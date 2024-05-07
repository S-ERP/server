package Component;

import org.json.JSONArray;
import org.json.JSONObject;
import Servisofts.SPGConect;
import Servisofts.SUtil;
import SharedKernel.Empresa;
import Server.SSSAbstract.SSSessionAbstract;

public class TareaComentario {
    public static final String COMPONENT = "tarea_comentario";

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
            String consulta = "select get_all('" + COMPONENT + "','key_tarea','" + obj.getString("key_tarea")
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
            JSONObject tarea = Tarea.getByKey(obj.getString("key_tarea"));

            JSONObject data = obj.getJSONObject("data");
            data.put("key", SUtil.uuid());
            data.put("estado", 1);
            data.put("fecha_on", SUtil.now());
            data.put("key_usuario", obj.getString("key_usuario"));
            data.put("key_empresa", obj.getString("key_empresa"));
            data.put("key_tarea", tarea.getString("key"));
            data.put("descripcion", (data.getString("descripcion")+"").replaceAll("'", "''"));
            SPGConect.insertArray(COMPONENT, new JSONArray().put(data));

            JSONObject tareaUsuarios = TareaUsuario.getAllUsuarios(tarea.getString("key"));
            JSONObject tareaUsuario;

            JSONObject notificationData = new JSONObject();
            if(obj.has("notification_data")){
                notificationData = obj.getJSONObject("notification_data");
            }

            for (int i = 0; i < JSONObject.getNames(tareaUsuarios).length; i++) {
                tareaUsuario = tareaUsuarios.getJSONObject(JSONObject.getNames(tareaUsuarios)[i]);
                
                if(tareaUsuario.getString("key_usuario").equals(obj.getString("key_usuario"))) continue;

                JSONObject empresa = Empresa.getByKey(tarea.getString("key_empresa"));

                new Notification().send_urlType(
                    tarea.getString("key_empresa"),
                    obj.getString("key_usuario"),
                    tareaUsuario.getString("key_usuario"),
                    "tarea_comentario_registro", 
                    notificationData.put("tarea", tarea).put("tarea_comentario", data).put("razon_social", empresa.getString("razon_social")));

            }

            obj.put("data", data);
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            e.printStackTrace();
        }
    }

    public static void registro(String tipo, String desc, String obs, String key_tarea, String key_usuario,
          JSONObject data) {
        try {

            

            JSONObject d = new JSONObject();
            d.put("key", SUtil.uuid());
            d.put("estado", 1);
            d.put("descripcion", desc);
            d.put("observacion", obs);
            d.put("fecha_on", SUtil.now());
            d.put("key_usuario", key_usuario);
            d.put("key_tarea", key_tarea);
            d.put("data", data);
            d.put("tipo", tipo);
            SPGConect.insertArray(COMPONENT, new JSONArray().put(d));



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
            obj.put("data", data);
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            e.printStackTrace();
        }
    }

}
