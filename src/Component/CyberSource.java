package Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.json.JSONObject;

import Server.SSSAbstract.SSSessionAbstract;
import Servisofts.SConfig;
import SocketCliente.SocketCliente;

/**
 * Cobro con tarjeta (Cybersource / Linkser) de la empresa: reporte de pagos, links de pago y configuración.
 *
 * serp no habilita el microservicio cybersource en el bus a propósito: el gateway público no autentica y ese servicio
 * maneja las llaves de los comercios. Aquí se revisan los permisos del usuario en la empresa y se le pasa al
 * servicio (por la red interna, config "cybersource_api") solo la operación permitida y siempre con la key_empresa.
 *
 * Tipos (todos con key_usuario y key_empresa):
 *   estado          ver         -> {configurado, activo, ambiente, descripcion}
 *   pagos           ver         {desde?, hasta?, state?} -> links de pago de la empresa
 *   link_registro   new         {data:{monto, descripcion, cliente?, key_solicitud?}} -> link con url
 *   link_anular     delete      {key}
 *   config_get      configurar  -> configuración sin el Shared Secret
 *   config_guardar  configurar  {data:{descripcion, merchant_id, ambiente, cybs_key, cybs_secret?, razon_social, rubro, activo}}
 *   config_probar   configurar  -> prueba las credenciales contra Cybersource
 *   caja_cobrar     (miembro)   {data:{monto, descripcion, cliente?, key_solicitud?}} -> link con url y qr para cobrar una venta
 *   caja_estado     (miembro)   {key} -> estado del link (solo de la propia empresa)
 *   caja_anular     (miembro)   {key} -> anula un link no pagado (el cliente no llegó a pagar)
 *
 * Permisos en roles_permisos: PAGE ver, new, delete, configurar.
 */
public class CyberSource {
    public static final String COMPONENT = "cybersource";
    public static final String PAGE = "/cybersource";

    public static void onMessage(JSONObject obj, SSSessionAbstract session) {
        try {
            String key_usuario = obj.getString("key_usuario");
            String key_empresa = obj.getString("key_empresa");
            if (!activoEnEmpresa(key_usuario, key_empresa)) throw new Exception("El usuario no está activo en la empresa");
            JSONObject permisos = permisos(key_usuario, key_empresa);
            JSONObject req = new JSONObject().put("key_empresa", key_empresa).put("key_usuario", key_usuario);
            JSONObject resp;
            switch (obj.getString("type")) {
                case "estado":
                    exigir(permisos, "ver");
                    resp = llamar(req.put("component", "comercio").put("type", "get"));
                    JSONObject c = resp.optJSONObject("data") == null ? new JSONObject() : resp.getJSONObject("data");
                    resp.put("data", new JSONObject().put("configurado", c.optBoolean("configurado")).put("activo", c.optBoolean("activo"))
                            .put("ambiente", c.optString("ambiente")).put("descripcion", c.optString("descripcion")));
                    break;
                case "pagos":
                    exigir(permisos, "ver");
                    resp = llamar(req.put("component", "link_pago").put("type", "getAll")
                            .put("desde", obj.optString("desde", "")).put("hasta", obj.optString("hasta", "")).put("state", obj.optString("state", "")));
                    break;
                case "link_registro":
                    exigir(permisos, "new");
                    resp = llamar(req.put("component", "link_pago").put("type", "registro").put("data", obj.getJSONObject("data")));
                    break;
                case "link_anular":
                    exigir(permisos, "delete");
                    resp = llamar(req.put("component", "link_pago").put("type", "anular").put("key", obj.getString("key")));
                    break;
                // Cobro de una venta desde caja: cualquier usuario activo de la empresa (el cajero), sin permisos de /cybersource.
                case "caja_cobrar": {
                    JSONObject d = obj.getJSONObject("data");
                    JSONObject data = new JSONObject().put("monto", d.getDouble("monto"))
                            .put("descripcion", d.optString("descripcion", "Venta").trim().isEmpty() ? "Venta" : d.getString("descripcion").trim())
                            .put("cliente", d.optJSONObject("cliente") == null ? new JSONObject() : d.getJSONObject("cliente"));
                    if (!d.optString("key_solicitud").isEmpty()) data.put("key_solicitud", d.getString("key_solicitud"));
                    resp = llamar(req.put("component", "link_pago").put("type", "registro").put("data", data));
                    break;
                }
                case "caja_estado":
                    resp = llamar(req.put("component", "link_pago").put("type", "getByKey").put("key", obj.getString("key")));
                    break;
                case "caja_anular":
                    resp = llamar(req.put("component", "link_pago").put("type", "anular").put("key", obj.getString("key")));
                    break;
                case "config_get":
                    exigir(permisos, "configurar");
                    resp = llamar(req.put("component", "comercio").put("type", "get"));
                    break;
                case "config_guardar":
                    exigir(permisos, "configurar");
                    resp = llamar(req.put("component", "comercio").put("type", "guardar").put("data", obj.getJSONObject("data")));
                    break;
                case "config_probar":
                    exigir(permisos, "configurar");
                    resp = llamar(req.put("component", "comercio").put("type", "probar"));
                    break;
                default:
                    throw new Exception("type no soportado: " + obj.getString("type"));
            }
            if (!"exito".equals(resp.optString("estado"))) throw new Exception(resp.optString("error", "Error en el servicio de pagos"));
            obj.remove("data");
            obj.put("data", resp.opt("data"));
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
        }
    }

    static JSONObject llamar(JSONObject req) throws Exception {
        String api = SConfig.getJSON().optString("cybersource_api", "http://192.168.5.56:30056/api");
        HttpURLConnection con = (HttpURLConnection) new URL(api).openConnection();
        con.setRequestMethod("POST");
        con.setConnectTimeout(10000);
        con.setReadTimeout(60000);
        con.setDoOutput(true);
        con.setRequestProperty("Content-Type", "application/json");
        try (OutputStream out = con.getOutputStream()) {
            out.write(req.toString().getBytes(StandardCharsets.UTF_8));
        }
        int http = con.getResponseCode();
        InputStream in = http >= 400 ? con.getErrorStream() : con.getInputStream();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        if (in != null) in.transferTo(buf);
        con.disconnect();
        try {
            return new JSONObject(buf.toString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new Exception("El servicio de pagos no respondió (HTTP " + http + ")");
        }
    }

    /** Permisos de la página /cybersource del usuario en la empresa (los usa también Tienda). */
    static JSONObject permisosDe(String key_usuario, String key_empresa) throws Exception {
        if (!activoEnEmpresa(key_usuario, key_empresa)) throw new Exception("El usuario no está activo en la empresa");
        return permisos(key_usuario, key_empresa);
    }

    static void exigirPermiso(JSONObject permisos, String permiso) throws Exception {
        exigir(permisos, permiso);
    }

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
                .put("component", "empresa_usuario").put("type", "getAll").put("estado", "cargando")
                .put("key_empresa", key_empresa), 20000);
        JSONObject data = resp == null ? null : resp.optJSONObject("data");
        if (data == null) return false;
        for (String k : data.keySet()) {
            JSONObject eu = data.optJSONObject(k);
            if (eu != null && key_usuario.equals(eu.optString("key_usuario")) && eu.optInt("estado", 1) > 0) return true;
        }
        return false;
    }
}
