package Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import Server.SSSAbstract.SSSessionAbstract;
import Servisofts.SPGConect;
import Servisofts.SUtil;
import SocketCliente.SocketCliente;

/**
 * Tokens de acceso al MCP de S-ERP (tabla mcp_token).
 *
 * registro  {key_usuario, key_empresa, data:{descripcion, alcance, fecha_vencimiento?}} -> data.token (solo esta vez)
 * getAll    {key_usuario, key_empresa}  -> tokens de la empresa, sin hash
 * anular    {key_usuario, key_empresa, key}
 * validar   {token}  -> {key, key_empresa, key_usuario, alcance, descripcion}  (lo usa el MCP)
 *
 * Gestionar tokens requiere el permiso PAGE/new (crear, anular) o PAGE/ver (listar) en roles_permisos.
 */
public class McpToken {
    public static final String COMPONENT = "mcp_token";
    public static final String PAGE = "/empresa/mcp";
    private static final String PREFIJO = "smcp_";
    private static final SecureRandom RANDOM = new SecureRandom();

    public static void onMessage(JSONObject obj, SSSessionAbstract session) {
        try {
            switch (obj.getString("type")) {
                case "registro": registro(obj); break;
                case "getAll": getAll(obj); break;
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
        String key_usuario = uuid(obj, "key_usuario");
        exigirPermiso(key_usuario, key_empresa, "new");

        JSONObject data = obj.optJSONObject("data");
        if (data == null) data = new JSONObject();
        String alcance = data.optString("alcance", "lectura");
        if (!alcance.equals("lectura") && !alcance.equals("escritura")) throw new Exception("alcance inválido");
        String descripcion = data.optString("descripcion", "").trim();
        if (descripcion.isEmpty()) throw new Exception("La descripción es requerida");

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = PREFIJO + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        JSONObject row = new JSONObject()
                .put("key", SUtil.uuid())
                .put("estado", 1)
                .put("fecha_on", SUtil.now())
                .put("key_usuario", key_usuario)
                .put("key_empresa", key_empresa)
                .put("descripcion", descripcion)
                .put("alcance", alcance)
                .put("token_hash", sha256(token))
                .put("token_prefijo", token.substring(0, PREFIJO.length() + 6));
        if (!data.optString("fecha_vencimiento", "").isEmpty()) row.put("fecha_vencimiento", data.getString("fecha_vencimiento"));
        SPGConect.insertArray2(COMPONENT, new JSONArray().put(row));

        row.remove("token_hash");
        row.put("token", token);
        obj.put("data", row);
    }

    static void getAll(JSONObject obj) throws Exception {
        String key_empresa = uuid(obj, "key_empresa");
        exigirPermiso(uuid(obj, "key_usuario"), key_empresa, "ver");
        obj.put("data", consulta(
                "select coalesce(json_object_agg(t.key, to_jsonb(t) - 'token_hash'), '{}') as json from mcp_token t where t.key_empresa = ?",
                key_empresa));
    }

    static void anular(JSONObject obj) throws Exception {
        String key_empresa = uuid(obj, "key_empresa");
        String key_usuario = uuid(obj, "key_usuario");
        exigirPermiso(key_usuario, key_empresa, "delete");
        int n = actualizar("update mcp_token set estado = 0, key_usuario_anulacion = ?, fecha_anulacion = now() "
                + "where key = ? and key_empresa = ? and estado > 0", key_usuario, uuid(obj, "key"), key_empresa);
        if (n == 0) throw new Exception("Token no encontrado");
    }

    static void validar(JSONObject obj) throws Exception {
        String token = obj.optString("token", "");
        if (!token.startsWith(PREFIJO)) throw new Exception("token inválido");
        JSONObject data = consulta(
                "select to_jsonb(t) - 'token_hash' as json from mcp_token t where t.token_hash = ? and t.estado > 0 "
                        + "and (t.fecha_vencimiento is null or t.fecha_vencimiento > now())",
                sha256(token));
        if (data.isEmpty()) throw new Exception("token inválido");
        actualizar("update mcp_token set fecha_ultimo_uso = now() where key = ?", data.getString("key"));
        obj.remove("token");
        obj.put("data", data);
    }

    // --- helpers ---

    private static void exigirPermiso(String key_usuario, String key_empresa, String permiso) throws Exception {
        JSONObject resp = SocketCliente.sendSinc("roles_permisos", new JSONObject()
                .put("version", "1.0")
                .put("component", "usuarioPage")
                .put("type", "getAll")
                .put("estado", "cargando")
                .put("key_usuario", key_usuario)
                .put("key_empresa", key_empresa), 20000);
        JSONObject page = resp == null ? null : resp.optJSONObject("data") == null ? null : resp.getJSONObject("data").optJSONObject(PAGE);
        JSONObject permisos = page == null ? null : page.optJSONObject("permisos");
        if (permisos == null || !permisos.has(permiso)) throw new Exception("Sin permiso " + PAGE + " " + permiso);
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
