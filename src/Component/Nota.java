package Component;

import org.json.JSONArray;
import org.json.JSONObject;

import Servisofts.SConsole;
import Servisofts.SPGConect;
import Servisofts.SUtil;
import Server.SSSAbstract.SSSessionAbstract;

public class Nota {
    public static final String COMPONENT = "nota";

    public static void onMessage(JSONObject obj, SSSessionAbstract session) {
        switch (obj.getString("type")) {
            case "getAll":
                getAll(obj, session);
                break;
            case "getHistorico":
                getHistorico(obj, session);
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
            case "quitarUsuario":
                quitarUsuario(obj, session);
                break;
        }
    }

    public static void getAll(JSONObject obj, SSSessionAbstract session) {
        try {
            
            String consulta = "select get_all_notas('" + obj.getString("key_usuario") + "', '" + obj.getString("key_empresa") + "') as json";
            
            JSONObject data = SPGConect.ejecutarConsultaObject(consulta);
            obj.put("data", data);
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            e.printStackTrace();
        }
    }

    public static void getHistorico(JSONObject obj, SSSessionAbstract session) {
        try {
            
            String consulta = "select get_all_notas_historico('" + obj.getString("key_nota") + "') as json";
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

    public static JSONObject getByKey(String key) {
        try {
            String consulta = "select get_by_key('" + COMPONENT + "', '" + key + "') as json";
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
            data.put("observacion", data.getString("observacion").replaceAll("'", "''"));
            data.put("fecha_on", SUtil.now());
            SPGConect.insertArray(COMPONENT, new JSONArray().put(data));

            JSONObject notaUsuario = new JSONObject();
            notaUsuario.put("key", SUtil.uuid());
            notaUsuario.put("estado", 1);
            notaUsuario.put("fecha_on", SUtil.now());
            notaUsuario.put("key_usuario", data.getString("key_usuario"));
            notaUsuario.put("key_nota", data.getString("key"));
            notaUsuario.put("is_admin", "admin");
            SPGConect.insertArray("nota_usuario", new JSONArray().put(notaUsuario));

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

            String key = obj.getJSONObject("data").getString("key");

            String consulta = "select get_by_key('" + COMPONENT + "', '" + key + "') as json";
            JSONObject nota = SPGConect.ejecutarConsultaObject(consulta);
            nota = nota.getJSONObject(JSONObject.getNames(nota)[0]);
            
            JSONObject data = obj.getJSONObject("data");


            nota.put("key", SUtil.uuid());
            nota.put("estado", 1);
            nota.put("key_nota", key);
            nota.put("observacion", (data.getString("observacion")+"").replaceAll("'", "''"));

            SPGConect.insertArray(COMPONENT, new JSONArray().put(nota));

            data.put("fecha_on", SUtil.now());
            data.put("key_usuario", obj.getString("key_usuario"));
            SPGConect.editObject(COMPONENT, data);
            obj.put("data", data);
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            e.printStackTrace();
        }
    }

    public static void quitarUsuario(JSONObject obj, SSSessionAbstract session) {
        try {
            
            JSONObject nota = Nota.getByKey(obj.getString("key_nota"));
            nota = nota.getJSONObject(JSONObject.getNames(nota)[0]);

            JSONObject notaUsuario = NotaUsuario.getNotaUsuario(obj.getString("key_nota"), obj.getString("key_usuario_nota"));
            if(notaUsuario.isEmpty()){
                obj.put("estado", "error");
                obj.put("error", "Nota ya eliminada");
                return;    
            }
            notaUsuario.put("estado", 0);


            SPGConect.editObject("nota_usuario", notaUsuario);

            if(!obj.getString("key_usuario_nota").equals(obj.getString("key_usuario"))){
                new Notification().send_urlType(
                    nota.getString("key_empresa"),
                    obj.getString("key_usuario"),
                    notaUsuario.getString("key_usuario"),
                    "nota_usuario_delete", 
                    nota);
            }


            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            e.printStackTrace();
        }
    }

}
