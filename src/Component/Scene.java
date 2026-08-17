package Component;

import java.util.Date;

import org.json.JSONArray;
import org.json.JSONObject;
import Servisofts.SPGConect;
import Servisofts.SUtil;
import Server.SSSAbstract.SSServerAbstract;
import Server.SSSAbstract.SSSessionAbstract;

public class Scene {
    public static final String COMPONENT = "scene";

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
            case "notify":
                notify(obj, session);
                break;
        }
    }

    public static void getAll(JSONObject obj, SSSessionAbstract session) {
        try {
            String consulta = "select get_all('" + COMPONENT + "', 'key_empresa', '" + obj.getString("key_empresa")
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

    public static void registro(JSONObject obj, SSSessionAbstract session) {
        try {
            JSONObject data = obj.getJSONObject("data");
            data.put("key", SUtil.uuid());
            data.put("estado", 1);
            data.put("fecha_on", SUtil.now());
            data.put("key_usuario", obj.getString("key_usuario"));
            data.put("key_empresa", obj.getString("key_empresa"));
            SPGConect.insertArray(COMPONENT, new JSONArray().put(data));
            obj.put("data", data);
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            e.printStackTrace();
        }
    }

    public static JSONArray getKeyUsuarios(String key_scene) {
        try {
            String consulta = "select get_all('avatar', 'key_scene', '" + key_scene + "') as json";
            JSONObject data = SPGConect.ejecutarConsultaObject(consulta);

            JSONArray keys = new JSONArray();
            for (int i = 0; i < JSONObject.getNames(data).length; i++) {
                JSONObject avatar = data.getJSONObject(JSONObject.getNames(data)[i]);
                keys.put(avatar.getString("key_usuario"));
            }
            return keys;
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public static void editar(JSONObject obj, SSSessionAbstract session) {
        try {
            JSONObject data = obj.getJSONObject("data");
            SPGConect.editObject(COMPONENT, data);

            JSONObject objSend = new JSONObject();
            objSend.put("component", "scene");
            objSend.put("key_usuario", obj.getString("key_usuario"));
            objSend.put("type", "onmove");
            objSend.put("estado", "exito");
            objSend.put("time", new Date().getTime());
            objSend.put("data", data);

            JSONArray usuarios = Scene.getKeyUsuarios(data.getString("key"));
            SSServerAbstract.sendUsers(objSend, usuarios);

            obj.put("data", data);
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            e.printStackTrace();
        }
    }
    public static void notify(JSONObject obj, SSSessionAbstract session) {
        try {
            // JSONObject data = obj.getJSONObject("data");
            // SPGConect.editObject(COMPONENT, data);

            // JSONObject objSend = new JSONObject();
            // objSend.put("component", "scene");
            // objSend.put("key_usuario", obj.getString("key_usuario"));
            // objSend.put("type", "onmove");
            // objSend.put("estado", "exito");
            // objSend.put("time", new Date().getTime());
            // objSend.put("data", data);
            JSONArray usuarios = Scene.getKeyUsuarios(obj.getString("key_scene"));
            SSServerAbstract.sendUsers(obj, usuarios);
            obj.put("estado", "exito");
            obj.put("noSend", true);
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            e.printStackTrace();
        }
    }

}
