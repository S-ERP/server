package Component;

import org.json.JSONArray;
import org.json.JSONObject;
import Servisofts.SPGConect;
import Servisofts.SUtil;
import Server.SSSAbstract.SSSessionAbstract;

public class TareaLabel {
    public static final String COMPONENT = "tarea_label";

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
            String consulta = "select get_all('" + COMPONENT + "', 'key_tarea', '"+obj.getString("key_tarea")+"') as json";
            
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
            JSONObject data = new JSONObject();
            data.put("key", SUtil.uuid());
            data.put("estado", 1);
            data.put("fecha_on", SUtil.now());
            data.put("key_usuario", obj.getString("key_usuario"));
            data.put("key_label", obj.getString("key_label"));
            data.put("key_tarea", obj.getString("key_tarea"));

            SPGConect.insertArray(COMPONENT, new JSONArray().put(data));

            
            TareaComentario.registro("add_label", "agregó el label",
                    "", obj.getString("key_tarea"),
                    obj.getString("key_usuario"),
                    new JSONObject().put("key_label", obj.getString("key_label")));


            obj.put("data", data);
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            e.printStackTrace();
        }
    }

    public static void registro(String key_tarea, String key_usuario, String key_empresa, JSONObject response) {
        try {
            JSONObject data = new JSONObject();
            data.put("key", SUtil.uuid());
            data.put("estado", 1);
            data.put("fecha_on", SUtil.now());
            data.put("key_usuario", key_usuario);
            data.put("key_tarea", key_tarea);
            data.put("key_empresa", key_empresa);
            data.put("response", response);
            SPGConect.insertArray(COMPONENT, new JSONArray().put(data));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void marcarTarea(JSONObject obj) {
        try {
            if (obj.has("component") && obj.has("type")) {
                JSONObject tarea = SPGConect.ejecutarConsultaObject("select tarea_get_all('component','type') as json");
                if (tarea != null && !tarea.isEmpty()) {

                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void editar(JSONObject obj, SSSessionAbstract session) {
        try {
            JSONObject data = obj.getJSONObject("data");
            SPGConect.editObject(COMPONENT, data);
            if (data.has("estado") && data.getInt("estado") == 0) {
                TareaComentario.registro("delete_label", "eliminó el label",
                        "", data.getString("key_tarea"),
                        obj.getString("key_usuario"),
                        new JSONObject().put("key_label",
                                data.getString("key_label")));
            }
            obj.put("data", data);
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            e.printStackTrace();
        }
    }

}
