package Component;

import java.net.Socket;
import java.util.Date;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONObject;

import Servisofts.SConsole;
import Servisofts.SPGConect;
import Servisofts.SUtil;
import SocketCliente.SocketCliente;
import Server.SSSAbstract.SSServerAbstract;
import Server.SSSAbstract.SSSessionAbstract;
import Server.ServerSocket.ServerSocket;

public class Invitacion {
    public static final String COMPONENT = "invitacion";

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
            case "aceptar":
                aceptar(obj, session);
                break;
        }
    }

    public static void getAll(JSONObject obj, SSSessionAbstract session) {
        try {
            String consulta = "select get_all_tareas('" + obj.getString("key_usuario") + "', '" + obj.getString("key_empresa") + "') as json";
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
            String consulta = "select get_by_key('" + keyTarea + "') as json";
            return SPGConect.ejecutarConsultaObject(consulta);
            
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

            SPGConect.insertArray(COMPONENT, new JSONArray().put(data));

            obj.put("data", data);
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
    public static void aceptar(JSONObject obj, SSSessionAbstract session) {
        try {
            if(!obj.has("key_usuario") || obj.isNull("key_usuario") || obj.getString("key_usuario").length()==0){
                throw new Exception("No tienes usuario");
            }

            if(!obj.has("key") || obj.isNull("key") || obj.getString("key").length()==0){
                throw new Exception("No tienes invitación");
            }

            JSONObject invitacion = SPGConect.ejecutarConsultaObject("select get_by_key('"+COMPONENT+"', '"+obj.getString("key")+"') as json");

            invitacion = invitacion.getJSONObject(JSONObject.getNames(invitacion)[0]);
            if(invitacion==null || invitacion.isEmpty() || !invitacion.has("key")){
                throw new Exception("No tienes invitación");
            }

            if(invitacion.has("key_usuario_invitado") && !invitacion.isNull("key_usuario_invitado") && invitacion.getString("key_usuario_invitado").length()>0){

                invitacion.remove("key_usuario_invitado");
                invitacion.put("key_invitation", invitacion.getString("key"));
                invitacion.put("key", SUtil.uuid());
                invitacion.put("fecha_on", SUtil.now());
                invitacion.put("key_invitation", SUtil.now());
                SPGConect.insertObject("invitacion",invitacion);
            }

            if(new Date().getTime() > SUtil.parseTimestamp(invitacion.getString("fecha_fin")).getTime()){
                throw new Exception("Esta invitación se encuentra vencida ");
            }

            JSONObject send = new JSONObject();
            send.put("component", "empresa_usuario");
            send.put("type", "getByKeys");
            send.put("key_empresa", invitacion.getString("key_empresa"));
            send.put("key_usuario", obj.getString("key_usuario"));
            
            JSONObject empresa_usuario = SocketCliente.sendSinc("empresa", send);
            empresa_usuario = empresa_usuario.getJSONObject("data");

            if(empresa_usuario.isEmpty()){

                send = new JSONObject();
                send.put("component", "empresa_usuario");
                send.put("type", "registro");
                send.put("data", 
                new JSONObject()
                .put("key_empresa", invitacion.getString("key_empresa"))
                .put("key_usuario", obj.getString("key_usuario"))
                .put("alias", obj.getString("alias"))
                );
                
                empresa_usuario = SocketCliente.sendSinc("empresa", send);

                if(empresa_usuario.getString("estado").equals("error")){
                    throw new Exception(empresa_usuario.getString("error"));
                }

                empresa_usuario = empresa_usuario.getJSONObject("data");

            }

            

            invitacion.put("key_usuario_invitado", obj.getString("key_usuario"));

            SPGConect.editObject(COMPONENT, invitacion);
            

            obj.put("data", invitacion);
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            e.printStackTrace();
        }
    }
}
