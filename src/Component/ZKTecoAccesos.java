package Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

import Server.SSSAbstract.SSSessionAbstract;
import SocketCliente.SocketCliente;

// Quienes tienen acceso a los molinetes de una sucursal:
//  - Clientes con una suscripcion vigente en esa sucursal (estado 1, fecha_inicio <= hoy <= fecha_fin, el dia de fecha_fin cuenta).
//  - Personal con el permiso /molinete -> acceso por algun rol de la empresa, y activo en la empresa.
// La lista "keys" es lo que se carga en el molinete (zkteco guarda la huella con key_usuario = key del cliente o usuario).
// zkteco/sincronizarMolinete (key_sucursal) la carga en los molinetes de la sucursal; ZKTecoTask lo repite cada dia.
public class ZKTecoAccesos {
    public static final String COMPONENT = "zkteco";
    public static final String URL_PERMISO = "/molinete";
    public static final String TYPE_PERMISO = "acceso";

    public static void onMessage(JSONObject obj, SSSessionAbstract session) {
        switch (obj.getString("type")) {
            case "accesos":
                accesos(obj);
                break;
            case "sincronizarMolinete":
                sincronizarMolinete(obj);
                break;
            case "testConnection":
                testConnection(obj);
                break;
        }
    }

    public static void accesos(JSONObject obj) {
        try {
            obj.put("data", getAccesos(obj.getString("key_sucursal")));
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            e.printStackTrace();
        }
    }

    // zkteco responde dispositivo/testConnection con noSend:true y este servidor descartaria la respuesta
    // al reenviarla al cliente; aqui se consulta de forma sincrona y se devuelve siempre el resultado.
    public static void testConnection(JSONObject obj) {
        try {
            JSONObject resp = SocketCliente.sendSinc("zkteco", new JSONObject()
                    .put("component", "dispositivo").put("type", "testConnection").put("estado", "cargando")
                    .put("key_dispositivo", obj.getString("key_dispositivo"))
                    .put("key_punto_venta", obj.optString("key_punto_venta")), 20000);
            obj.put("estado", resp == null ? "error" : resp.optString("estado", "error"));
            if (resp == null) obj.put("error", "El punto de venta no respondio");
            else if (resp.has("error")) obj.put("error", resp.optString("error"));
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
        }
        obj.put("noSend", false);
    }

    public static void sincronizarMolinete(JSONObject obj) {
        try {
            obj.put("data", sincronizar(obj.getString("key_sucursal")));
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            e.printStackTrace();
        }
    }

    // Carga la lista de accesos en cada molinete de la sucursal: zkteco actualiza usuario_dispositivo
    // (delete_all saca a los que ya no tienen acceso) y se la manda al lector.
    public static JSONArray sincronizar(String key_sucursal) throws Exception {
        JSONArray keys = getAccesos(key_sucursal).getJSONArray("keys");
        JSONArray resultado = new JSONArray();
        for (JSONObject d : getDispositivos(key_sucursal)) {
            JSONObject r = new JSONObject().put("key_dispositivo", d.getString("key"))
                    .put("descripcion", d.optString("descripcion")).put("usuarios", keys.length());
            try {
                request("zkteco", new JSONObject().put("component", "dispositivo").put("type", "sincronizarMolinete")
                        .put("key_dispositivo", d.getString("key")).put("data", keys).put("delete_all", true), 600000);
                r.put("estado", "exito");
            } catch (Exception e) {
                r.put("estado", "error").put("error", e.getMessage());
            }
            resultado.put(r);
        }
        return resultado;
    }

    // Dispositivos activos de los puntos de venta de la sucursal.
    // No se usa punto_venta/getMolinetesByKeySucursal: get_dispositivos_key_sucursal tiene una sucursal fija.
    public static List<JSONObject> getDispositivos(String key_sucursal) throws Exception {
        List<JSONObject> dispositivos = new ArrayList<>();
        for (Object o : valores(request("zkteco", new JSONObject()
                .put("component", "punto_venta").put("type", "getAll")).opt("data"))) {
            JSONObject pv = (JSONObject) o;
            if (!key_sucursal.equals(pv.optString("key_sucursal")) || pv.optInt("estado") <= 0) continue;
            for (Object d : valores(request("zkteco", new JSONObject().put("component", "dispositivo")
                    .put("type", "getAll").put("key_punto_venta", pv.getString("key"))).opt("data"))) {
                if (((JSONObject) d).optInt("estado") > 0) dispositivos.add((JSONObject) d);
            }
        }
        return dispositivos;
    }

    // Sucursales de los puntos de venta zkteco que reportan a este servicio (punto_venta.key_servicio).
    public static Set<String> getSucursales(String key_servicio) throws Exception {
        Set<String> sucursales = new HashSet<>();
        for (Object o : valores(request("zkteco", new JSONObject()
                .put("component", "punto_venta").put("type", "getAll")).opt("data"))) {
            JSONObject pv = (JSONObject) o;
            if (key_servicio.equals(pv.optString("key_servicio")) && pv.optInt("estado") > 0
                    && !pv.optString("key_sucursal").isEmpty())
                sucursales.add(pv.getString("key_sucursal"));
        }
        return sucursales;
    }

    public static JSONObject getAccesos(String key_sucursal) throws Exception {
        JSONObject sucursal = request("empresa", new JSONObject()
                .put("component", "sucursal").put("type", "getByKey").put("key", key_sucursal))
                .optJSONObject("data");
        if (sucursal == null || !sucursal.has("key_empresa")) throw new Exception("No existe la sucursal");
        String key_empresa = sucursal.getString("key_empresa");
        String hoy = LocalDate.now().toString();

        JSONArray clientes = getClientes(key_empresa, key_sucursal, hoy);
        JSONArray personal = getPersonal(key_empresa);

        Set<String> keys = new HashSet<>();
        for (int i = 0; i < clientes.length(); i++) keys.add(clientes.getJSONObject(i).getString("key"));
        for (int i = 0; i < personal.length(); i++) keys.add(personal.getJSONObject(i).getString("key"));

        return new JSONObject()
                .put("key_sucursal", key_sucursal)
                .put("key_empresa", key_empresa)
                .put("fecha", hoy)
                .put("clientes", clientes)
                .put("personal", personal)
                .put("keys", new JSONArray(keys));
    }

    // Misma fuente que la tabla /suscriptores.
    private static JSONArray getClientes(String key_empresa, String key_sucursal, String hoy) throws Exception {
        JSONObject resp = request("inventario", new JSONObject()
                .put("component", "reporte").put("type", "execute_function")
                .put("func", "_reporte_suscritos")
                .put("params", new JSONArray().put("'" + key_empresa + "'")), 60000);
        Map<String, JSONObject> clientes = new LinkedHashMap<>();
        for (Object o : valores(resp.opt("data"))) {
            JSONObject s = (JSONObject) o;
            if (!key_sucursal.equals(s.optString("key_sucursal"))) continue;
            if (s.optInt("estado") != 1 || s.optString("key_cliente").isEmpty()) continue;
            String ini = fecha(s.optString("fecha_inicio")), fin = fecha(s.optString("fecha_fin"));
            if (ini.isEmpty() || fin.isEmpty() || ini.compareTo(hoy) > 0 || fin.compareTo(hoy) < 0) continue;
            JSONObject prev = clientes.get(s.getString("key_cliente"));
            if (prev != null && prev.getString("fecha_fin").compareTo(fin) >= 0) continue;
            clientes.put(s.getString("key_cliente"), new JSONObject()
                    .put("key", s.getString("key_cliente"))
                    .put("key_suscripcion", s.optString("key"))
                    .put("fecha_inicio", ini)
                    .put("fecha_fin", fin));
        }
        return new JSONArray(clientes.values());
    }

    // permiso/getAllUsuarios devuelve un solo rol por usuario y usuario_rol no tiene key_empresa,
    // por eso se va por los roles de la empresa que tienen el permiso.
    private static JSONArray getPersonal(String key_empresa) throws Exception {
        JSONObject page = buscar(request("roles_permisos", new JSONObject()
                .put("component", "page").put("type", "getAll")).opt("data"), "url", URL_PERMISO);
        if (page == null) return new JSONArray();
        JSONObject permiso = buscar(request("roles_permisos", new JSONObject()
                .put("component", "permiso").put("type", "getAll").put("key_page", page.getString("key"))).opt("data"),
                "type", TYPE_PERMISO);
        if (permiso == null) return new JSONArray();

        Set<String> rolesConPermiso = new HashSet<>();
        for (Object o : valores(request("roles_permisos", new JSONObject()
                .put("component", "rolPermiso").put("type", "getAll").put("key_permiso", permiso.getString("key"))).opt("data"))) {
            JSONObject rp = (JSONObject) o;
            if (rp.optInt("estado") > 0) rolesConPermiso.add(rp.optString("key_rol"));
        }

        Set<String> enEmpresa = new HashSet<>();
        for (Object o : valores(request("empresa", new JSONObject()
                .put("component", "empresa_usuario").put("type", "getAll").put("key_empresa", key_empresa)).opt("data"))) {
            JSONObject eu = (JSONObject) o;
            if (eu.optInt("estado", 1) != 0) enEmpresa.add(eu.optString("key_usuario"));
        }

        Map<String, JSONObject> personal = new HashMap<>();
        for (Object o : valores(request("roles_permisos", new JSONObject()
                .put("component", "rol").put("type", "getAll").put("key_empresa", key_empresa)).opt("data"))) {
            JSONObject rol = (JSONObject) o;
            if (rol.optInt("estado") <= 0 || !rolesConPermiso.contains(rol.optString("key"))) continue;
            for (Object u : valores(request("roles_permisos", new JSONObject()
                    .put("component", "usuarioRol").put("type", "getAll").put("key_rol", rol.getString("key"))).opt("data"))) {
                JSONObject ur = (JSONObject) u;
                String key_usuario = ur.optString("key_usuario");
                if (ur.optInt("estado") <= 0 || !enEmpresa.contains(key_usuario)) continue;
                personal.put(key_usuario, new JSONObject()
                        .put("key", key_usuario)
                        .put("key_rol", rol.getString("key"))
                        .put("rol", rol.optString("descripcion")));
            }
        }
        return new JSONArray(personal.values());
    }

    static JSONObject request(String service, JSONObject send) throws Exception {
        return request(service, send, 20000);
    }

    static JSONObject request(String service, JSONObject send, int timeOut) throws Exception {
        send.put("estado", "cargando");
        JSONObject resp = SocketCliente.sendSinc(service, send, timeOut);
        if (resp == null || !"exito".equals(resp.optString("estado")))
            throw new Exception(service + " " + send.optString("component") + "/" + send.optString("type") + ": "
                    + (resp == null ? "sin respuesta" : resp.optString("error", resp.optString("estado"))));
        return resp;
    }

    private static JSONObject buscar(Object data, String campo, String valor) {
        for (Object o : valores(data)) {
            JSONObject x = (JSONObject) o;
            if (valor.equals(x.optString(campo)) && x.optInt("estado") > 0) return x;
        }
        return null;
    }

    // data puede venir como objeto {key: fila} o como arreglo de filas.
    static JSONArray valores(Object data) {
        JSONArray arr = new JSONArray();
        if (data instanceof JSONArray) return (JSONArray) data;
        if (data instanceof JSONObject) {
            JSONObject o = (JSONObject) data;
            for (String k : o.keySet()) if (o.get(k) instanceof JSONObject) arr.put(o.get(k));
        }
        return arr;
    }

    private static String fecha(String f) {
        return f == null || f.length() < 10 ? "" : f.substring(0, 10);
    }
}
