package Component;

import java.util.Date;

import org.json.JSONArray;
import org.json.JSONObject;
import Servisofts.SPGConect;
import Servisofts.SUtil;
import Server.SSSAbstract.SSServerAbstract;
import Server.SSSAbstract.SSSessionAbstract;

public class SceneMesh {
    public static final String COMPONENT = "scene_mesh";

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
        }
    }

    public static void getAll(JSONObject obj, SSSessionAbstract session) {
        try {
            String consulta = "select get_all_scene_mesh('" + obj.getString("key_scene") + "') as json";
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

            
            JSONObject objSend = new JSONObject();
            objSend.put("component", "scene_mesh");
            objSend.put("key_usuario", obj.getString("key_usuario"));
            objSend.put("type", "onmove");
            objSend.put("estado", "exito");
            objSend.put("time", new Date().getTime());
            objSend.put("data", data);

            JSONArray usuarios = Scene.getKeyUsuarios(data.getString("key_scene"));
            SSServerAbstract.sendUsers(objSend, usuarios);

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

            JSONObject objSend = new JSONObject();
            objSend.put("component", "scene_mesh");
            objSend.put("type", "onmove");
            objSend.put("estado", "exito");
            objSend.put("key_usuario", obj.getString("key_usuario"));
            objSend.put("time", new Date().getTime());
            objSend.put("data", data);

            JSONArray usuarios = Scene.getKeyUsuarios(data.getString("key_scene"));
            SSServerAbstract.sendUsers(objSend, usuarios);

        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            e.printStackTrace();
        }
    }

}
