package Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

import Servisofts.SConsole;

// Asistencia de los clientes que marcan en los molinetes zkteco de una sucursal.
// Cuando un molinete entrega sus marcaciones, zkteco las guarda en dispositivo_historico y avisa con
// dispositivo/sincronizarLog (key_sucursal). Aqui se piden las pendientes desde la ultima procesada y, por cada
// cliente con suscripcion vigente en la sucursal, se registra una asistencia en inventario (una por hora).
// El personal entra pero no genera asistencia. La ultima marcacion procesada de cada sucursal se guarda en ARCHIVO.
public class ZKTecoAsistencia {
    private static final Path ARCHIVO = Paths.get("zkteco_asistencia.json");
    private static final Object lock = new Object();

    // Mensajes del microservicio zkteco (ManejadorCliente)
    public static void onMessageCliente(JSONObject obj) {
        if (!ZKTeco.DISPOSITIVO.equals(obj.optString("component")) || !"sincronizarLog".equals(obj.optString("type"))
                || !"exito".equals(obj.optString("estado"))) return;
        for (String k : obj.keySet()) {
            if (k.startsWith("_sincrone_key_")) return;
        }
        String key_sucursal = obj.optString("key_sucursal");
        if (key_sucursal.isEmpty() || key_sucursal.equals("null")) return;
        try {
            sincronizar(key_sucursal);
        } catch (Exception e) {
            SConsole.error("ZKTecoAsistencia", key_sucursal, e.getMessage());
        }
    }

    public static int sincronizar(String key_sucursal) throws Exception {
        synchronized (lock) {
            JSONObject estado = leerEstado();
            String desde = estado.optString(key_sucursal, LocalDate.now() + " 00:00:00");
            JSONObject resp = ZKTecoAccesos.request("zkteco", new JSONObject()
                    .put("component", "dispositivo_historico").put("type", "getAsistenciasPendientes")
                    .put("key_sucursal", key_sucursal).put("fecha_min", desde));
            List<JSONObject> marcaciones = new ArrayList<>();
            for (Object o : ZKTecoAccesos.valores(resp.opt("data"))) marcaciones.add((JSONObject) o);
            if (marcaciones.isEmpty()) return 0;
            marcaciones.sort((a, b) -> a.optString("fecha_on").compareTo(b.optString("fecha_on")));

            JSONObject accesos = ZKTecoAccesos.getAccesos(key_sucursal);
            String key_empresa = accesos.getString("key_empresa");
            Map<String, String> suscripciones = new HashMap<>();
            JSONArray clientes = accesos.getJSONArray("clientes");
            for (int i = 0; i < clientes.length(); i++) {
                JSONObject c = clientes.getJSONObject(i);
                suscripciones.put(c.getString("key"), c.optString("key_suscripcion"));
            }
            Set<String> registradas = registradas(key_empresa, key_sucursal);

            int total = 0;
            for (JSONObject m : marcaciones) {
                JSONObject data = m.optJSONObject("data");
                String key_cliente = data == null ? "" : data.optString("key_usuario");
                if (suscripciones.containsKey(key_cliente) && registradas.add(key_cliente + "|" + hora(m))) {
                    ZKTecoAccesos.request("inventario", new JSONObject()
                            .put("component", "asistencia").put("type", "registro").put("key_usuario", key_cliente)
                            .put("data", new JSONObject()
                                    .put("key_cliente", key_cliente)
                                    .put("key_suscripcion", suscripciones.get(key_cliente))
                                    .put("key_sucursal", key_sucursal)
                                    .put("key_empresa", key_empresa)));
                    total++;
                }
                // Se guarda en cada marcacion: si algo falla, la proxima vez sigue desde aca.
                estado.put(key_sucursal, m.getString("fecha_on"));
                guardarEstado(estado);
            }
            SConsole.succes("ZKTecoAsistencia", key_sucursal, "marcaciones=" + marcaciones.size(), "asistencias=" + total);
            return total;
        }
    }

    // Asistencias ya registradas en la sucursal, como cliente|yyyy-MM-ddTHH, para no repetir en la misma hora.
    private static Set<String> registradas(String key_empresa, String key_sucursal) {
        Set<String> set = new HashSet<>();
        try {
            JSONObject resp = ZKTecoAccesos.request("inventario", new JSONObject()
                    .put("component", "asistencia").put("type", "getAll").put("key_empresa", key_empresa));
            for (Object o : ZKTecoAccesos.valores(resp.opt("data"))) {
                JSONObject a = (JSONObject) o;
                String f = a.optString("fecha_on");
                if (key_sucursal.equals(a.optString("key_sucursal")) && f.length() >= 13)
                    set.add(a.optString("key_cliente") + "|" + f.substring(0, 13));
            }
        } catch (Exception e) {
            // Sin asistencias en la empresa get_asistencias devuelve null e inventario responde error.
            SConsole.warning("ZKTecoAsistencia", "asistencias registradas", e.getMessage());
        }
        return set;
    }

    // Hora de la marcacion (yyyy-MM-ddTHH). Time_second viene como lo codifican los controladores ZKTeco.
    private static String hora(JSONObject m) {
        JSONObject data = m.optJSONObject("data");
        try {
            int ts = Integer.parseInt(data.getString("Time_second"));
            return String.format("%04d-%02d-%02dT%02d", ts / 32140800 + 2000, ts / 2678400 % 12 + 1,
                    ts / 86400 % 31 + 1, ts / 3600 % 24);
        } catch (Exception e) {
            String f = m.optString("fecha_on").replace(' ', 'T');
            return f.length() >= 13 ? f.substring(0, 13) : f;
        }
    }

    private static JSONObject leerEstado() {
        try {
            if (Files.exists(ARCHIVO)) return new JSONObject(new String(Files.readAllBytes(ARCHIVO), StandardCharsets.UTF_8));
        } catch (Exception e) {
            SConsole.error("ZKTecoAsistencia", ARCHIVO.toString(), e.getMessage());
        }
        return new JSONObject();
    }

    private static void guardarEstado(JSONObject estado) throws Exception {
        Path tmp = Paths.get(ARCHIVO + ".tmp");
        Files.write(tmp, estado.toString(2).getBytes(StandardCharsets.UTF_8));
        Files.move(tmp, ARCHIVO, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
}
