package Component;

import org.json.JSONArray;
import org.json.JSONObject;
import Servisofts.SPGConect;
import Servisofts.SUtil;
import Server.SSSAbstract.SSSessionAbstract;

public class Camera {
    public static final String COMPONENT = "camera";

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
            String consulta = "select get_all('" + COMPONENT + "', 'key_empresa', '"+obj.getString("key_empresa")+"') as json";
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

    public static JSONObject getByKey(String key_empresa, String key_usuario) {
        try {
            String consulta = "select get_all('" + COMPONENT + "', 'key_empresa','"+key_empresa+"','key_usuario','" + key_usuario + "') as json";
            JSONObject all = SPGConect.ejecutarConsultaObject(consulta);
            if(JSONObject.getNames(all).length>0){
                return all.getJSONObject(JSONObject.getNames(all)[0]);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }


    public static void registro(JSONObject obj, SSSessionAbstract session) {
        try {

            JSONObject camera = getByKey(obj.getString("key_empresa"),obj.getString("key_usuario"));

            if(camera == null){
                JSONObject data = obj.getJSONObject("data");
                
                data.put("key", SUtil.uuid());
                data.put("estado", 1);
                data.put("fecha_on", SUtil.now());
                data.put("key_usuario", obj.getString("key_usuario"));
                data.put("key_empresa", obj.getString("key_empresa"));
                SPGConect.insertArray(COMPONENT, new JSONArray().put(data));
            }else{
                camera.put("descripcion", obj.getJSONObject("data").getString("descripcion"));
                camera.put("fecha_on", SUtil.now());
                camera.put("data", obj.getJSONObject("data").getJSONObject("data"));
                SPGConect.editObject(COMPONENT, camera);
            }
            
            obj.put("data", camera);
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
