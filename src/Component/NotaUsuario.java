package Component;

import org.json.JSONArray;
import org.json.JSONObject;

import Servisofts.SConfig;
import Servisofts.SPGConect;
import Servisofts.SUtil;
import SharedKernel.Empresa;
import SocketCliente.SocketCliente;
import Server.SSSAbstract.SSSessionAbstract;

public class NotaUsuario {
    public static final String COMPONENT = "nota_usuario";

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

    public static JSONObject getNotaUsuario(String keyNota, String keyUsuario) throws Exception{
        String consulta = "select get_nota_usuario('" + keyNota + "', '"+keyUsuario+"') as json";
        return SPGConect.ejecutarConsultaObject(consulta);
    }

    public static void getAll(JSONObject obj, SSSessionAbstract session) {
        try {
            String consulta = "select get_all('" + COMPONENT + "') as json";
            if(obj.has("key_usuario")){
                consulta = "select get_all('" + COMPONENT + "','key_usuario','"+obj.getString("key_usuario")+"') as json";
            }
            if(obj.has("key_nota")){
                consulta = "select get_all('" + COMPONENT + "','key_nota', '"+obj.getString("key_nota")+"') as json";
            }
            if(obj.has("key_usuario") && obj.has("key_empresa")){
                consulta = "select get_all('" + COMPONENT + "','key_usuario','"+obj.getString("key_usuario")+"','key_empresa', '"+obj.getString("key_empresa")+"') as json";
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

    public static void getTareasAgendadas(JSONObject obj, SSSessionAbstract session) {
        try {
            String consulta = "select get_tareas_agendadas('"+obj.getString("key_usuario")+"','"+obj.getString("key_empresa")+"') as json";
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

            String consulta = "select get_all('" + COMPONENT + "','key_usuario','"+data.getString("key_usuario")+"','key_nota', '"+data.getString("key_nota")+"') as json";
            JSONObject notaUsuario = SPGConect.ejecutarConsultaObject(consulta);
            if(!notaUsuario.isEmpty()){
                obj.put("estado", "error");
                obj.put("error", "El usuario ya se encuentra en la nota.");
                return;
            }
            System.out.println(notaUsuario);

            JSONObject nota = Nota.getByKey(data.getString("key_nota"));
            nota = nota.getJSONObject(JSONObject.getNames(nota)[0]);
            data.put("key", SUtil.uuid());
            data.put("estado", 1);
            data.put("fecha_on", SUtil.now());
            data.put("key_usuario", data.getString("key_usuario"));
            data.put("key_nota", data.getString("key_nota"));
            
            
            SPGConect.insertArray(COMPONENT, new JSONArray().put(data));

            JSONObject empresa = Empresa.getByKey(obj.getString("key_empresa"));
            

            new Notification().send_urlType(
                nota.getString("key_empresa"),
                obj.getString("key_usuario"),
                data.getString("key_usuario"),
                "nota_usuario_registro", 
                new JSONObject()
                .put("key", nota.getString("key"))
                .put("key_empresa", empresa.getString("key"))
                .put("razon_social", empresa.getString("razon_social"))
                .put("observacion", nota.getString("observacion"))
                );

            

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
        try{
            if(obj.has("component") && obj.has("type")){
                JSONObject tarea = SPGConect.ejecutarConsultaObject("select tarea_get_all('component','type') as json");
                if(tarea !=null && !tarea.isEmpty()){
                    
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

}
