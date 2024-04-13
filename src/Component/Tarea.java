package Component;

import org.json.JSONArray;
import org.json.JSONObject;

import Servisofts.SConsole;
import Servisofts.SPGConect;
import Servisofts.SUtil;
import Server.SSSAbstract.SSSessionAbstract;

public class Tarea {
    public static final String COMPONENT = "tarea";

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
            case "cerrar":
                cerrar(obj, session);
                break;
            case "abrir":
                abrir(obj, session);
                break;
            case "cantidad":
                cantidad(obj, session);
                break;
        }
    }

    public static void getAll(JSONObject obj, SSSessionAbstract session) {
        try {
            String consulta = "select get_all('"+COMPONENT+"','key_empresa', '" + obj.getString("key_empresa") + "') as json";

            if(obj.has("key_usuario")){
                consulta = "select get_all_tareas('" + obj.getString("key_usuario") + "', '" + obj.getString("key_empresa") + "') as json";
            }
            if(obj.has("fecha_inicio") && obj.has("fecha_fin")){
                consulta = "select get_all_tareas('" + obj.getString("key_usuario") + "', '" + obj.getString("key_empresa") + "', '"+obj.getString("fecha_inicio")+"', '"+obj.getString("fecha_fin")+"') as json";
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

    public static void cantidad(JSONObject obj, SSSessionAbstract session) {
        try {
            
            String consulta = "select get_all_tareas_cantidad('" + obj.getString("key_usuario") + "', '" + obj.getString("key_empresa") + "', '"+obj.getString("fecha_inicio")+"', '"+obj.getString("fecha_fin")+"') as json";
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

    public static JSONObject getByKey(String keyTarea) {
        try {
            String consulta = "select get_by_key('"+COMPONENT+"','" + keyTarea + "') as json";
            JSONObject tarea = SPGConect.ejecutarConsultaObject(consulta);
            tarea = tarea.getJSONObject(JSONObject.getNames(tarea)[0]);
            return tarea;
            
        } catch (Exception e) {
            e.printStackTrace();
            return null;
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

            JSONObject num = SPGConect.ejecutarConsultaObject("select get_numero_tarea('"+obj.getString("key_empresa")+"') as json");

            data.put("numero", num.getInt("numero"));
            

            SPGConect.insertArray(COMPONENT, new JSONArray().put(data));

            JSONObject tareaUsuario = new JSONObject();
            tareaUsuario.put("key", SUtil.uuid());
            tareaUsuario.put("estado", 1);
            tareaUsuario.put("fecha_on", SUtil.now());
            tareaUsuario.put("key_usuario", obj.getString("key_usuario"));
            tareaUsuario.put("key_tarea", data.getString("key"));
            tareaUsuario.put("is_admin", "admin");
            SPGConect.insertArray("tarea_usuario", new JSONArray().put(tareaUsuario));

            obj.put("data", data);
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            e.printStackTrace();
        }
    }

    public static void marcarTarea(JSONObject obj) {
        try{
            if(obj.has("component") && obj.has("type")){
                JSONObject tarea = SPGConect.ejecutarConsultaObject("select tarea_get_all('"+obj.getString("component")+"','"+obj.getString("type")+"') as json");
                if(tarea !=null && !tarea.isEmpty()){
                    if(obj.has("key_usuario")){
                        JSONObject resp = new JSONObject();
                        if(obj.has("estado")){
                            if(obj.getString("estado").equals("error")){
                                resp.put("estado", "error");
                                resp.put("error", obj.getString("error"));
                                TareaUsuario.registro(JSONObject.getNames(tarea)[0], obj.getString("key_usuario"), obj.getString("key_empresa"), resp);
                            }
                            if(obj.getString("estado").equals("exito")){
                                resp.put("estado", "exito");
                                TareaUsuario.registro(JSONObject.getNames(tarea)[0], obj.getString("key_usuario"), obj.getString("key_empresa"), resp);
                            }
                            
                        }
                        
                    }else{
                        SConsole.error("La petición no tiene key_usuario");
                    }
                }
            }
        }catch(Exception e){ 
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

    public static void cerrar(JSONObject obj, SSSessionAbstract session) {
        try {
            JSONObject tarea = Tarea.getByKey(obj.getString("key_tarea"));
            tarea.put("key", tarea.getString("key"));
            tarea.put("estado", 2);
            SPGConect.editObject(COMPONENT, tarea);

            TareaComentario.registro("cerrar", "cerró la tarea",
            "", obj.getString("key_tarea"),
            obj.getString("key_usuario"),
            new JSONObject());

            JSONObject tareaUsuarios = TareaUsuario.getAllUsuarios(tarea.getString("key"));
            JSONObject tareaUsuario;

            JSONObject notificationData = new JSONObject();
            if(obj.has("notification_data")){
                notificationData = obj.getJSONObject("notification_data");
            }

            for (int i = 0; i < JSONObject.getNames(tareaUsuarios).length; i++) {
                tareaUsuario = tareaUsuarios.getJSONObject(JSONObject.getNames(tareaUsuarios)[i]);
                
                if(tareaUsuario.getString("key_usuario").equals(obj.getString("key_usuario"))) continue;

                new Notification().send_urlType(
                    tarea.getString("key_empresa"),
                    obj.getString("key_usuario"),
                    tareaUsuario.getString("key_usuario"),
                    "tarea_cerrar", 
                    notificationData.put("tarea", tarea));

            }

            obj.put("data", tarea);
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            e.printStackTrace();
        }
    }

    public static void abrir(JSONObject obj, SSSessionAbstract session) {
        try {
            JSONObject tarea = Tarea.getByKey(obj.getString("key_tarea"));
            tarea.put("estado", 1);
            SPGConect.editObject(COMPONENT, tarea);

            TareaComentario.registro("abrir", "reabrió la tarea",
                    "", tarea.getString("key"),
                    obj.getString("key_usuario"),
                    new JSONObject());


            JSONObject tareaUsuarios = TareaUsuario.getAllUsuarios(tarea.getString("key"));
            JSONObject tareaUsuario;

            JSONObject notificationData = new JSONObject();
            if(obj.has("notification_data")){
                notificationData = obj.getJSONObject("notification_data");
            }

            for (int i = 0; i < JSONObject.getNames(tareaUsuarios).length; i++) {
                tareaUsuario = tareaUsuarios.getJSONObject(JSONObject.getNames(tareaUsuarios)[i]);
                
                if(tareaUsuario.getString("key_usuario").equals(obj.getString("key_usuario"))) continue;

                new Notification().send_urlType(
                    tarea.getString("key_empresa"),
                    obj.getString("key_usuario"),
                    tareaUsuario.getString("key_usuario"),
                    "tarea_abrir", 
                    notificationData.put("tarea", tarea));

            }

            obj.put("data", tarea);
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            e.printStackTrace();
        }
    }
}
