package Component;

import org.json.JSONArray;
import org.json.JSONObject;

import Servisofts.SConfig;
import Servisofts.SPGConect;
import Servisofts.SUtil;
import SharedKernel.Empresa;
import Server.SSSAbstract.SSSessionAbstract;

public class TareaUsuario {
    public static final String COMPONENT = "tarea_usuario";

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
            case "getTareasAgendadas":
                getTareasAgendadas(obj, session);
                break;
        }
    }

    public static void getAll(JSONObject obj, SSSessionAbstract session) {
        try {
            String consulta = "select get_all('" + COMPONENT + "') as json";
            if (obj.has("key_usuario") && obj.has("key_empresa")) {
                consulta = "select get_all_tareas('" + obj.getString("key_usuario") + "', '"
                        + obj.getString("key_empresa") + "') as json";
            }
            if (obj.has("key_tarea") && obj.has("key_empresa")) {
                consulta = "select get_all_usuarios_tareas('" + obj.getString("key_tarea") + "', '"
                        + obj.getString("key_empresa") + "') as json";
            }
            JSONObject data = SPGConect.ejecutarConsultaObject(consulta);
            obj.put("data", data);
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            e.printStackTrace();
        }
    }

    public static JSONObject getAllUsuarios(String key_tarea) {
        try {
            String consulta = "select get_all('" + COMPONENT + "', 'key_tarea', '"+key_tarea+"') as json";
            return SPGConect.ejecutarConsultaObject(consulta);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void getTareasAgendadas(JSONObject obj, SSSessionAbstract session) {
        try {
            String consulta = "select get_tareas_agendadas('" + obj.getString("key_usuario") + "','"
                    + obj.getString("key_empresa") + "') as json";
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
            data.put("key_usuario", obj.getJSONObject("data").getString("key_usuario"));
            data.put("key_usuario_creador", obj.getString("key_usuario"));
            data.put("key_empresa", obj.getString("key_empresa"));
            data.put("key_tarea", obj.getString("key_tarea"));

            SPGConect.insertArray(COMPONENT, new JSONArray().put(data));

            JSONObject tarea = Tarea.getByKey(obj.getString("key_tarea"));

            // Aqui me quede
            // TareaComentario.registro("nuevo_usuario",obj.getString("key_tarea"),obj.getJSONObject("data").getString("key_usuario"),
            // obj.getString("key_empresa"), null);
            

            TareaComentario.registro("add_user", "agrego a",
                    "", obj.getString("key_tarea"),
                    obj.getString("key_usuario"),
                    new JSONObject().put("key_usuario_participante", obj.getJSONObject("data").getString("key_usuario")));

          /*  new Notification().send_url("Nueva tarea", tarea.getString("descripcion"),
                    "https://serp.servisofts.com/images/tarea/" + obj.getString("key_tarea"),
                    SConfig.getJSON().getString("deeplink")+"/tarea/post?pk=" + obj.getString("key_tarea"));*/

            
            JSONObject empresa = Empresa.getByKey(tarea.getString("key_empresa"));
            new Notification().send_urlType(
                tarea.getString("key_empresa"),
                obj.getString("key_usuario"),
                data.getString("key_usuario"),
                "tarea_usuario_registro", 
                tarea.put("razon_social", empresa.getString("razon_social")));

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

                TareaComentario.registro("delete_user", "elimino a",
                        "", data.getString("key_tarea"),
                        obj.getString("key_usuario"),
                        new JSONObject().put("key_usuario_participante",
                                data.getString("key_usuario")));
                
                JSONObject tarea = Tarea.getByKey(data.getString("key_tarea"));
                JSONObject empresa = Empresa.getByKey(tarea.getString("key_empresa"));
                new Notification().send_urlType(
                        tarea.getString("key_empresa"),
                        obj.getString("key_usuario"),
                        data.getString("key_usuario"),
                        "tarea_usuario_delete", 
                        tarea.put("razon_social", empresa.getString("razon_social")));
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
