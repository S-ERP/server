package Component;

import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONObject;
import Servisofts.SPGConect;
import Servisofts.SUtil;
import Server.SSSAbstract.SSSessionAbstract;

public class Billetera {
    public static final String COMPONENT = "billetera";

    public static final String TIPO_PAGO_QR = "QR";

    public static void onMessage(JSONObject obj, SSSessionAbstract session) {
        switch (obj.getString("type")) {
            case "getAll":
                getAll(obj, session);
                break;
            case "getByKey":
                getByKey(obj, session);
                break;
            case "registro":
                registro(obj, session);
                break;
            case "editar":
                editar(obj, session);
                break;
            case "saldoBilletera":
                saldoBilletera(obj, session);
                break;
        }
    }

    public static void getAll(JSONObject obj, SSSessionAbstract session) {
        try {
            String consulta = "select get_all('" + COMPONENT + "', 'key_usuario', '"+obj.getString("key_usuario")+"') as json";
            JSONObject data = SPGConect.ejecutarConsultaObject(consulta);
            obj.put("data", data);
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            e.printStackTrace();
        }
    }

    public static void getByKey(JSONObject obj, SSSessionAbstract session) {
        try {
            String consulta = "select get_by_key('" + COMPONENT + "', '" + obj.getString("key") + "') as json";
            JSONObject data = SPGConect.ejecutarConsultaObject(consulta);
            obj.put("data", data);
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            e.printStackTrace();
        }
    }

    public static void registro(JSONObject obj, SSSessionAbstract session) {
        try {
            JSONObject data = obj.getJSONObject("data");
            data.put("key", SUtil.uuid());
            data.put("estado", 1);
            data.put("fecha_on", SUtil.now());
            data.put("key_usuario", obj.getString("key_usuario"));
            SPGConect.insertArray(COMPONENT, new JSONArray().put(data));
            obj.put("data", data);
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            e.printStackTrace();
        }
    }

    public static void editar(JSONObject obj, SSSessionAbstract session) {
        try {
            JSONObject data = obj.getJSONObject("data");
            SPGConect.editObject(COMPONENT, data);
            obj.put("data", data);
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            e.printStackTrace();
        }
    }

    public static void saldoBilletera(JSONObject obj, SSSessionAbstract session) {
        try {
            String key_usuario = obj.getString("key_usuario");
            String consulta = "SELECT to_json(sq.*)::json as json \n" +
                            "FROM (\n" +
                            "   SELECT SUM(billetera.monto) AS monto\n" +
                            "   FROM billetera\n" +
                            "   WHERE billetera.key_usuario = '" + key_usuario + "'\n" +
                            "     AND billetera.estado > 0\n" +
                            ") sq\n" +
                            "";
            JSONObject data = SPGConect.ejecutarConsultaObject(consulta);
            obj.put("data", data);
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            e.printStackTrace();
        }
    }

    public static void registroBilleteraPagoQr(JSONObject solicitudQr) {
        try {
            JSONObject billetera = new JSONObject();
            billetera.put("key", UUID.randomUUID());
            billetera.put("key_usuario", solicitudQr.getString("key_usuario"));
            billetera.put("fecha_on", SUtil.now());
            billetera.put("estado", 1);
            billetera.put("monto", solicitudQr.getDouble("monto"));
            billetera.put("tipo_pago", TIPO_PAGO_QR);
            billetera.put("detalle", "Abono a billetera.");
            billetera.put("transaction_id", solicitudQr.getString("qrid"));
            billetera.put("key_empresa", solicitudQr.getString("key_empresa"));
            SPGConect.insertArray(COMPONENT, new JSONArray().put(billetera));
        } catch (Exception e) {
            // todo hacer un proceso que revise si todos los pagos por QR tipo billetera que esten pagados tambien este registrado en la billetera
        }
        
    }


}
