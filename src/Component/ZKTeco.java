package Component;

import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONObject;

import Server.SSSAbstract.SSSessionAbstract;
import Servisofts.SConsole;

// Registro de huellas con el microservicio zkteco (mismo flujo que Calistenia).
// El microservicio responde los eventos del lector (lector_huella/onEvent) y el registro final
// (dispositivo/registro) a este servidor; aqui se reenvian solo a la sesion que hizo la solicitud.
public class ZKTeco {
    public static final String LECTOR_HUELLA = "lector_huella";
    public static final String DISPOSITIVO = "dispositivo";

    // key_sucursal -> sesion que pidio el registro de huella
    private static final ConcurrentHashMap<String, SSSessionAbstract> solicitudes = new ConcurrentHashMap<>();

    // Mensajes de los clientes (Manejador)
    public static void onMessage(JSONObject obj, SSSessionAbstract session) {
        try {
            if (session == null) return;
            if (!obj.optString("type").equals("solicitud_registro_huella")) return;
            JSONObject data = obj.optJSONObject("data");
            if (data == null || !data.has("key_sucursal")) return;
            solicitudes.put(data.getString("key_sucursal"), session);
        } catch (Exception e) {
            SConsole.error("ZKTeco.onMessage", e.getMessage());
        }
    }

    // Mensajes del microservicio (ManejadorCliente)
    public static void onMessageCliente(JSONObject obj) {
        try {
            // Las respuestas a sendSinc ya vuelven por su propio camino.
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                if (keys.next().startsWith("_sincrone_key_")) return;
            }

            String component = obj.optString("component");
            String type = obj.optString("type");
            String key_sucursal = null;
            boolean fin = false;
            if (component.equals(LECTOR_HUELLA) && type.equals("onEvent")) {
                key_sucursal = obj.optString("key_sucursal", null);
            } else if (component.equals(DISPOSITIVO) && type.equals("registro")) {
                JSONObject data = obj.optJSONObject("data");
                if (data != null) key_sucursal = data.optString("key_sucursal", null);
                fin = true;
            }
            if (key_sucursal == null) return;

            SSSessionAbstract session = fin ? solicitudes.remove(key_sucursal) : solicitudes.get(key_sucursal);
            if (session == null) return;

            JSONObject send = new JSONObject(obj.toString());
            send.remove("info");
            send.remove("servicio");
            send.remove("id");
            send.put("component", fin ? "usuario_huella" : LECTOR_HUELLA);
            if (send.optJSONObject("data") != null) send.getJSONObject("data").remove("huella");
            session.send(send.toString());
        } catch (Exception e) {
            SConsole.error("ZKTeco.onMessageCliente", e.getMessage());
        }
    }
}
