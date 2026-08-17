package Component;

import java.sql.SQLException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import Servisofts.SPGConect;
import Servisofts.SUtil;
import SharedKernel.Empresa;
import Server.SSSAbstract.SSSessionAbstract;

public class EmpresaCierreProgramado  {
    public static final String COMPONENT = "empresa_cierre_programado";


    public static void onMessage(JSONObject obj, SSSessionAbstract session) {
        switch (obj.getString("type")) {
            case "getAll":
                getAll(obj, session);
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
            String key_empresa = obj.getString("key_empresa");
            String consulta = "select get_all('" + COMPONENT + "', 'key_empresa', '" + key_empresa + "') as json";
            JSONObject data = SPGConect.ejecutarConsultaObject(consulta);
            obj.put("data", data);
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
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
        } catch (Exception e) {
            obj.put("estado", "error");
            e.printStackTrace();
        }
    }

    public static void editar(JSONObject obj, SSSessionAbstract session) {
        try {
            JSONObject data = obj.getJSONObject("data");
            SPGConect.editObject(COMPONENT, data);
            if(data.getInt("estado") == 0) {
                JSONObject cierre_programado = getByKey(data.getString("key"));
                ejecutar_off(cierre_programado);
            }
            

            obj.put("data", data);
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            e.printStackTrace();
        }
    }

    public static JSONObject getByKey(String key) {
        try {
            String consulta = "select get_by_key('" + COMPONENT + "','" + key + "') as json";
            JSONObject data = SPGConect.ejecutarConsultaObject(consulta);
            return data.getJSONObject(key);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static void ejecutar_on(JSONObject obj) throws JSONException, SQLException {
        if(obj.has("fecha_ejecucion_on") && !obj.isNull("fecha_ejecucion_on")) {
            // si ya se cerro no se vuelve a ejecutar la accion
            return;
        }
        if((obj.has("fecha_ejecucion_off") && !obj.isNull("fecha_ejecucion_off"))) {
            // no se inicia si ya esta terminado
            return;
        }
        JSONObject empresa = Empresa.getByKey(obj.getString("key_empresa"));
        String habilitado;
        try {
            habilitado = empresa.getBoolean("habilitado") + "";
        } catch (Exception e) {
            habilitado = "null";
        }
        SPGConect.ejecutarUpdate(
            "UPDATE empresa habilitado = false, key = '" + obj.getString("key_empresa") + "'");

        SPGConect.ejecutarUpdate(
            "UPDATE empresa_cierre_programado SET habilitado_backup = " + habilitado + ", fecha_ejecucion_on = now() " + 
                " where key = '" + obj.getString("key") + "'");
    }

    public static void ejecutar_off(JSONObject obj) {

        if((!obj.has("fecha_ejecucion_on") || obj.isNull("fecha_ejecucion_on"))) {
            // no se revierte si no ha iniciado
            return;
        }

        if((obj.has("fecha_ejecucion_off") && !obj.isNull("fecha_ejecucion_off"))) {
            // no se revierte si ya esta terminado
            return;
        }
        String habilitado;
        try {
            habilitado = obj.getBoolean("habilitado_backup") + "";
        } catch (Exception e) {
            habilitado = "null";
        }
        SPGConect.ejecutarUpdate(
            "UPDATE empresa habilitado = " + habilitado + ", key = '" + obj.getString("key_empresa") + "'");

        SPGConect.ejecutarUpdate(
            "UPDATE empresa_cierre_programado SET  fecha_ejecucion_off = now() " +  
                "where key = '" + obj.getString("key") + "'");
    }

}
