package Component;

import org.json.JSONObject;

import Server.SSSAbstract.SSServerAbstract;
import Server.SSSAbstract.SSSessionAbstract;

public class qr_reader {
    public static final String COMPONENT = "qr_reader";

    public static void onMessage(JSONObject obj, SSSessionAbstract session) {
        switch (obj.getString("type")) {
            case "read":
                read(obj, session);
                break;
            case "take_picture":
                take_picture(obj, session);
                break;

        }
    }

    public static void read(JSONObject obj, SSSessionAbstract session) {
        try {
            String key_usuario = obj.getString("key_usuario");
            obj.put("estado", "exito");
            SSServerAbstract.sendUserNoMySession(obj, key_usuario, session);
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            e.printStackTrace();
        }
    }

    public static void take_picture(JSONObject obj, SSSessionAbstract session) {
        try {
            String key_usuario = obj.getString("key_usuario");
            obj.put("estado", "exito");

            SSServerAbstract.sendUserNoMySession(obj, key_usuario, session);
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            e.printStackTrace();
        }
    }

}
