package Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONArray;
import org.json.JSONObject;

import Server.SSSAbstract.SSServerAbstract;
import Server.SSSAbstract.SSSessionAbstract;
import Servisofts.SConsole;

// Pantalla de ingresos (/molinete/pantalla): avisa en vivo quien acaba de pasar por un molinete.
// La pantalla se suscribe con zkteco/suscribirIngresos (key_sucursal). Cuando ZKTecoAsistencia recibe las
// marcaciones de la sucursal, aqui se manda zkteco/ingreso a las sesiones suscritas que siguen abiertas.
public class ZKTecoIngresos {
    // key_sucursal -> id de las sesiones suscritas
    private static final ConcurrentHashMap<String, Set<String>> suscritos = new ConcurrentHashMap<>();

    public static void suscribir(JSONObject obj, SSSessionAbstract session) {
        String key_sucursal = obj.optString("key_sucursal");
        if (session == null || key_sucursal.isEmpty()) {
            obj.put("estado", "error");
            obj.put("error", "Falta key_sucursal");
            return;
        }
        for (Set<String> ids : suscritos.values()) ids.remove(session.getIdSession());
        suscritos.computeIfAbsent(key_sucursal, k -> ConcurrentHashMap.newKeySet()).add(session.getIdSession());
        obj.put("estado", "exito");
    }

    public static boolean haySuscritos(String key_sucursal) {
        Set<String> ids = suscritos.get(key_sucursal);
        return ids != null && !ids.isEmpty();
    }

    // accesos: resultado de ZKTecoAccesos.getAccesos, para decir si es cliente (con su suscripcion) o personal.
    public static void avisar(String key_sucursal, Iterable<JSONObject> marcaciones, JSONObject accesos) {
        try {
            Set<String> ids = suscritos.get(key_sucursal);
            if (ids == null || ids.isEmpty()) return;

            Map<String, JSONObject> clientes = indexar(accesos.optJSONArray("clientes"));
            Map<String, JSONObject> personal = indexar(accesos.optJSONArray("personal"));
            JSONArray ingresos = new JSONArray();
            for (JSONObject m : marcaciones) {
                JSONObject data = m.optJSONObject("data");
                if (data == null) continue;
                String key_usuario = data.optString("key_usuario");
                JSONObject ingreso = new JSONObject()
                        .put("key", m.optString("key"))
                        .put("key_usuario", key_usuario)
                        .put("key_dispositivo", m.optString("key_dispositivo"))
                        .put("pin", data.optString("Pin"))
                        .put("puerta", data.optString("DoorID"))
                        .put("fecha", data.optString("Fecha", m.optString("fecha_on")));
                if (clientes.containsKey(key_usuario)) {
                    ingreso.put("tipo", "cliente").put("suscripcion", clientes.get(key_usuario));
                } else if (personal.containsKey(key_usuario)) {
                    ingreso.put("tipo", "personal").put("rol", personal.get(key_usuario).optString("rol"));
                } else {
                    ingreso.put("tipo", "sin_acceso");
                }
                ingresos.put(ingreso);
            }
            if (ingresos.isEmpty()) return;

            String send = new JSONObject()
                    .put("component", "zkteco").put("type", "ingreso").put("estado", "exito")
                    .put("key_sucursal", key_sucursal).put("data", ingresos).toString();
            Set<String> abiertas = new HashSet<>();
            for (SSServerAbstract server : SSServerAbstract.getServidores().values()) {
                HashMap<String, SSSessionAbstract> sesiones = server.getSessiones();
                for (SSSessionAbstract s : sesiones.values()) {
                    if (s == null || !ids.contains(s.getIdSession())) continue;
                    abiertas.add(s.getIdSession());
                    s.send(send);
                }
            }
            ids.retainAll(abiertas);
        } catch (Exception e) {
            SConsole.error("ZKTecoIngresos", key_sucursal, e.getMessage());
        }
    }

    private static Map<String, JSONObject> indexar(JSONArray arr) {
        Map<String, JSONObject> map = new HashMap<>();
        if (arr == null) return map;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null) map.put(o.optString("key"), o);
        }
        return map;
    }
}
