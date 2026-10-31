package Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.json.JSONArray;
import org.json.JSONObject;

import Server.SSSAbstract.SSSessionAbstract;
import Servisofts.SConfig;
import Servisofts.SPGConect;
import Servisofts.SUtil;
import SocketCliente.SocketCliente;

/**
 * Tokens de acceso al MCP de S-ERP (tabla mcp_token).
 *
 * Cada token es de UN usuario (key_usuario): el MCP actúa como ese usuario, con sus permisos, en una empresa.
 * key_usuario_creador es quien lo generó (puede ser otro usuario, con el permiso PAGE/otro_usuario).
 *
 * registro  {key_usuario, key_empresa, data:{descripcion, alcance, fecha_vencimiento?, key_usuario_token?}} -> data.token
 * getAll    {key_usuario, key_empresa}       -> tokens de la empresa, sin hash ni token
 * ver       {key_usuario, key_empresa, key}  -> data.token (dueño, creador o PAGE/otro_usuario)
 * anular    {key_usuario, key_empresa, key}
 * validar   {token}  -> {key, key_empresa, key_usuario, alcance, descripcion}  (lo usa el MCP)
 *
 * Permisos en roles_permisos: PAGE ver (listar y ver los propios), new (crear), delete (anular),
 * otro_usuario (crear y ver tokens de otros usuarios).
 * El token se guarda como SHA-256 (para validar) y cifrado con AES-GCM (para poder mostrarlo); la clave está en
 * config.json -> mcp.token_secret o en el archivo mcp_token.secret (base64 de 32 bytes).
 */
public class McpToken {
    public static final String COMPONENT = "mcp_token";
    public static final String PAGE = "/empresa/mcp";
    private static final String PREFIJO = "smcp_";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String OCULTOS = " - 'token_hash' - 'token_cifrado'";

    public static void onMessage(JSONObject obj, SSSessionAbstract session) {
        try {
            switch (obj.getString("type")) {
                case "registro": registro(obj); break;
                case "getAll": getAll(obj); break;
                case "ver": ver(obj); break;
                case "anular": anular(obj); break;
                case "validar": validar(obj); break;
                default: return;
            }
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
        }
    }

    static void registro(JSONObject obj) throws Exception {
        String key_empresa = uuid(obj, "key_empresa");
        String creador = uuid(obj, "key_usuario");
        JSONObject permisos = permisos(creador, key_empresa);
        exigir(permisos, "new");

        JSONObject data = obj.optJSONObject("data");
        if (data == null) data = new JSONObject();
        String alcance = data.optString("alcance", "lectura");
        if (!alcance.equals("lectura") && !alcance.equals("escritura")) throw new Exception("alcance inválido");
        String descripcion = data.optString("descripcion", "").trim();
        if (descripcion.isEmpty()) throw new Exception("La descripción es requerida");

        String dueno = data.optString("key_usuario_token", "").isEmpty() ? creador : uuid(data, "key_usuario_token");
        if (!dueno.equals(creador)) {
            exigir(permisos, "otro_usuario");
            if (!activoEnEmpresa(dueno, key_empresa)) throw new Exception("El usuario no está activo en la empresa");
        }

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = PREFIJO + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        JSONObject row = new JSONObject()
                .put("key", SUtil.uuid())
                .put("estado", 1)
                .put("fecha_on", SUtil.now())
                .put("key_usuario", dueno)
                .put("key_usuario_creador", creador)
                .put("key_empresa", key_empresa)
                .put("descripcion", descripcion)
                .put("alcance", alcance)
                .put("token_hash", sha256(token))
                .put("token_cifrado", cifrar(token))
                .put("token_prefijo", token.substring(0, PREFIJO.length() + 6));
        if (!data.optString("fecha_vencimiento", "").isEmpty()) row.put("fecha_vencimiento", data.getString("fecha_vencimiento"));
        SPGConect.insertArray2(COMPONENT, new JSONArray().put(row));

        row.remove("token_hash");
        row.remove("token_cifrado");
        row.put("token", token);
        obj.put("data", row);
    }

    static void getAll(JSONObject obj) throws Exception {
        String key_empresa = uuid(obj, "key_empresa");
        exigir(permisos(uuid(obj, "key_usuario"), key_empresa), "ver");
        obj.put("data", consulta(
                "select coalesce(json_object_agg(t.key, to_jsonb(t)" + OCULTOS
                        + " || jsonb_build_object('visible', t.token_cifrado is not null)), '{}') as json from mcp_token t where t.key_empresa = ?",
                key_empresa));
    }

    static void ver(JSONObject obj) throws Exception {
        String key_empresa = uuid(obj, "key_empresa");
        String key_usuario = uuid(obj, "key_usuario");
        JSONObject permisos = permisos(key_usuario, key_empresa);
        exigir(permisos, "ver");
        JSONObject t = consulta("select to_jsonb(t) as json from mcp_token t where t.key = ?", uuid(obj, "key"));
        if (t.isEmpty() || !key_empresa.equals(t.optString("key_empresa"))) throw new Exception("Token no encontrado");
        boolean propio = key_usuario.equals(t.optString("key_usuario")) || key_usuario.equals(t.optString("key_usuario_creador"));
        if (!propio) exigir(permisos, "otro_usuario");
        if (t.optString("token_cifrado", "").isEmpty()) throw new Exception("Este token se creó antes de poder mostrarse: anúlelo y cree otro");
        obj.put("data", new JSONObject().put("key", t.getString("key")).put("token", descifrar(t.getString("token_cifrado"))));
    }

    static void anular(JSONObject obj) throws Exception {
        String key_empresa = uuid(obj, "key_empresa");
        String key_usuario = uuid(obj, "key_usuario");
        exigir(permisos(key_usuario, key_empresa), "delete");
        int n = actualizar("update mcp_token set estado = 0, key_usuario_anulacion = ?, fecha_anulacion = now() "
                + "where key = ? and key_empresa = ? and estado > 0", key_usuario, uuid(obj, "key"), key_empresa);
        if (n == 0) throw new Exception("Token no encontrado");
    }

    static void validar(JSONObject obj) throws Exception {
        String token = obj.optString("token", "");
        if (!token.startsWith(PREFIJO)) throw new Exception("token inválido");
        JSONObject data = consulta(
                "select to_jsonb(t)" + OCULTOS + " as json from mcp_token t where t.token_hash = ? and t.estado > 0 "
                        + "and (t.fecha_vencimiento is null or t.fecha_vencimiento > now())",
                sha256(token));
        if (data.isEmpty()) throw new Exception("token inválido");
        actualizar("update mcp_token set fecha_ultimo_uso = now() where key = ?", data.getString("key"));
        obj.remove("token");
        obj.put("data", data);
    }

    // --- helpers ---

    private static JSONObject permisos(String key_usuario, String key_empresa) throws Exception {
        JSONObject resp = SocketCliente.sendSinc("roles_permisos", new JSONObject()
                .put("version", "1.0")
                .put("component", "usuarioPage")
                .put("type", "getAll")
                .put("estado", "cargando")
                .put("key_usuario", key_usuario)
                .put("key_empresa", key_empresa), 20000);
        JSONObject page = resp == null ? null : resp.optJSONObject("data") == null ? null : resp.getJSONObject("data").optJSONObject(PAGE);
        JSONObject permisos = page == null ? null : page.optJSONObject("permisos");
        return permisos == null ? new JSONObject() : permisos;
    }

    private static void exigir(JSONObject permisos, String permiso) throws Exception {
        if (!permisos.has(permiso)) throw new Exception("Sin permiso " + PAGE + " " + permiso);
    }

    private static boolean activoEnEmpresa(String key_usuario, String key_empresa) throws Exception {
        JSONObject resp = SocketCliente.sendSinc("empresa", new JSONObject()
                .put("component", "empresa_usuario")
                .put("type", "getAll")
                .put("estado", "cargando")
                .put("key_empresa", key_empresa), 20000);
        JSONObject data = resp == null ? null : resp.optJSONObject("data");
        if (data == null) return false;
        for (String k : data.keySet()) {
            JSONObject eu = data.optJSONObject(k);
            if (eu != null && key_usuario.equals(eu.optString("key_usuario")) && eu.optInt("estado", 1) > 0) return true;
        }
        return false;
    }

    // Clave de cifrado: config.json -> mcp.token_secret o, si no está, el archivo mcp_token.secret (fuera de git; así
    // desarrollo usa la misma clave que producción sin subirla al repo). Base64 de 32 bytes.
    private static final String ARCHIVO_CLAVE = "mcp_token.secret";

    // AES-256-GCM: base64(iv(12) || cifrado+tag)
    private static SecretKeySpec clave() throws Exception {
        JSONObject mcp = SConfig.getJSON().optJSONObject("mcp");
        String secret = mcp == null ? "" : mcp.optString("token_secret", "");
        if (secret.isEmpty() && Files.exists(Paths.get(ARCHIVO_CLAVE)))
            secret = new String(Files.readAllBytes(Paths.get(ARCHIVO_CLAVE)), StandardCharsets.UTF_8).trim();
        byte[] k = Base64.getDecoder().decode(secret);
        if (k.length != 32) throw new Exception("Falta la clave de tokens: config mcp.token_secret o " + ARCHIVO_CLAVE + " (32 bytes en base64)");
        return new SecretKeySpec(k, "AES");
    }

    private static String cifrar(String texto) throws Exception {
        byte[] iv = new byte[12];
        RANDOM.nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, clave(), new GCMParameterSpec(128, iv));
        byte[] ct = c.doFinal(texto.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array());
    }

    private static String descifrar(String b64) throws Exception {
        byte[] all = Base64.getDecoder().decode(b64);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, clave(), new GCMParameterSpec(128, all, 0, 12));
        return new String(c.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
    }

    private static JSONObject consulta(String sql, String param) throws Exception {
        Connection con = SPGConect.pool.getConnection();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getString("json") != null) return new JSONObject(rs.getString("json"));
                return new JSONObject();
            }
        } finally {
            SPGConect.pool.releaseConnection(con);
        }
    }

    // La conexión es del pool: se devuelve con releaseConnection, no se cierra.
    private static int actualizar(String sql, String... params) throws Exception {
        Connection con = SPGConect.pool.getConnection();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setString(i + 1, params[i]);
            return ps.executeUpdate();
        } finally {
            SPGConect.pool.releaseConnection(con);
        }
    }

    private static String uuid(JSONObject obj, String campo) throws Exception {
        String v = obj.optString(campo, "");
        if (!v.matches("[0-9a-fA-F-]{32,36}")) throw new Exception(campo + " inválido");
        return v;
    }

    static String sha256(String s) throws Exception {
        byte[] h = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : h) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
