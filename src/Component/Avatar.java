package Component;

import java.util.Date;

import org.json.JSONArray;
import org.json.JSONObject;
import Servisofts.SPGConect;
import Servisofts.SUtil;
import SocketCliente.SocketCliente;
import Server.SSSAbstract.SSServerAbstract;
import Server.SSSAbstract.SSSessionAbstract;

public class Avatar {
    public static final String COMPONENT = "avatar";

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
            case "exit":
                exit(obj, session);
                break;
            case "editar":
                editar(obj, session);
                break;
            case "getByKeyUsuario":
                getByKeyUsuario(obj, session);
                break;
        }
    }

    public static void getAll(JSONObject obj, SSSessionAbstract session) {
        try {
            String consulta = "select get_all('" + COMPONENT + "', 'key_scene', '" + obj.getString("key_scene")
                    + "') as json";
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
    public static void getByKeyUsuario(JSONObject obj, SSSessionAbstract session) {
        try {
            JSONObject data = Avatar._getByKeyUsuario(obj.getString("key_usuario"));
            obj.put("data", data);
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            e.printStackTrace();
        }
    }


    public static JSONObject _getByKeyUsuario(String key_usuario) {
        try {
            String consulta = "select get_by('" + COMPONENT + "', 'key_usuario', '" + key_usuario + "') as json";
            return SPGConect.ejecutarConsultaObject(consulta);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void registro(JSONObject obj, SSSessionAbstract session) {
        try {
            if (obj.has("key_usuario")) {
                JSONObject data = Avatar._getByKeyUsuario(obj.getString("key_usuario"));
                if (data != null && !data.isEmpty()) {
                    data.put("data", obj.getJSONObject("data"));
                    data.put("key_scene", obj.getString("key_scene"));
                    data.put("fecha_edit", SUtil.now());
                    SPGConect.editObject(COMPONENT, data);
                } else {
                    data = new JSONObject();
                    data.put("key", SUtil.uuid());
                    data.put("estado", 1);
                    data.put("fecha_on", SUtil.now());
                    data.put("key_usuario", obj.getString("key_usuario"));
                    SPGConect.insertArray(COMPONENT, new JSONArray().put(data));

                }

                JSONObject objSend = new JSONObject();
                objSend.put("component", "avatar");
                objSend.put("type", "onmove");
                objSend.put("estado", "exito");
                objSend.put("time", new Date().getTime());
                objSend.put("data", data);

                JSONArray usuarios = Scene.getKeyUsuarios(data.getString("key_scene"));
                SSServerAbstract.sendUsers(objSend, usuarios);

                obj.put("data", data);

            }

            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            e.printStackTrace();
        }
    }

    public static void exit(JSONObject obj, SSSessionAbstract session) {
        try {
            obj.put("estado", "exito");
            obj.put("time", new Date().getTime());
            JSONArray usuarios = Scene.getKeyUsuarios(obj.getString("key_scene"));
            SSServerAbstract.sendUsers(obj, usuarios);

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

}
