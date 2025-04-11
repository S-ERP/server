package Controllers;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import org.json.JSONArray;
import org.json.JSONObject;

import JWT.JWT;
import Server.SSSAbstract.SSSessionAbstract;
import Servisofts.SConsole;
import Servisofts.SPGConect;
import Servisofts.SUtil;
import Servisofts.http.Status;
import Servisofts.http.Exception.*;
import Servisofts.http.annotation.*;
import SharedKernel.Empresa;
import SocketCliente.SocketCliente;

@RestController
@RequestMapping("/sapi")
public class Sapi {

    public static final String COMPONENT = "sapi_token";

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

    public static void registro(JSONObject obj, SSSessionAbstract session) {
        try {
            int expirationTime = 3600000;

            JSONObject data = obj.getJSONObject("data");
            data.put("key", SUtil.uuid());
            data.put("estado", 1);
            data.put("fecha_on", SUtil.now());
            data.put("fecha_fin", SUtil.formatTimestamp(new Date(System.currentTimeMillis() + expirationTime)));
            data.put("key_usuario", obj.getString("key_usuario"));
            data.put("key_empresa", obj.getString("key_empresa"));
            String token = JWT.create(
                data.getString("key_empresa"),
                expirationTime
            );
            data.put("token", token); 

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


    @PostMapping("/qr")
    public String callback(@RequestBody String body, @RequestHeader(value = "Authorization", required = true) String auth) throws HttpException {
        try{

        
            SConsole.log("Entro al qr");

            JSONObject empresa =Empresa.getByKey(auth);

            if(empresa==null || empresa.isEmpty()){
                throw new HttpException(Status.NON_AUTHORITATIVE_INFORMATION, "{\"error\":\"Error not Authorization\"}");
            }

            JSONObject obj = new JSONObject(body);
            String nit = obj.getString("nit");
            String razon_social = obj.getString("razon_social");
            String correo = obj.getString("correo");
            String telefono = obj.getString("telefono");

            Calendar cal = new GregorianCalendar();
            cal.add(Calendar.DATE, 1);

            SimpleDateFormat formatoJson = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
            SimpleDateFormat formato = new SimpleDateFormat("ddMMyyyy");

            JSONObject solicitud_qr = new JSONObject();
            solicitud_qr.put("key", SUtil.uuid());
            solicitud_qr.put("fecha_on", SUtil.now());
            solicitud_qr.put("estado", 1);
            solicitud_qr.put("callback", obj.getString("callback"));
            //solicitud_qr.put("key_usuario", obj.getString("key_usuario"));
            if(obj.has("tipo")){
                solicitud_qr.put("tipo", obj.getString("tipo"));
            }
            solicitud_qr.put("key_empresa", empresa.getString("key"));
            solicitud_qr.put("nit", nit);
            solicitud_qr.put("razon_social", razon_social);
            if(obj.has("descripcion") && !obj.isNull("descripcion")){
                solicitud_qr.put("descripcion", obj.getString("descripcion"));
            }
            solicitud_qr.put("telefono", telefono);
            solicitud_qr.put("correos", new JSONArray().put(correo));
            solicitud_qr.put("fecha_inicio", SUtil.now());
            solicitud_qr.put("fecha_vencimiento", formatoJson.format(cal.getTime()));
            solicitud_qr.put("monto", obj.getDouble("monto"));

            SPGConect.insertObject("solicitud_qr", solicitud_qr);

            JSONObject send = new JSONObject();
            send.put("component", "bg_profile");
            send.put("type", "getBy");
            send.put("key", "key_empresa");
            send.put("value", auth);

            // buscamos el key_bg_profile"
            JSONObject pgprofile = SocketCliente.sendSinc("banco_ganadero", send, 2 * 60 * 1000);
            pgprofile=pgprofile.getJSONObject("data");


            send = new JSONObject();
            send.put("component", "bg_payment_order");
            send.put("type", "registro");
            send.put("key_bg_profile", pgprofile.getString("key"));

            send.put("monto", obj.getDouble("monto"));
            send.put("glosa", obj.getString("descripcion"));
            send.put("fecha_expiracion", formato.format(cal.getTime()));
            send.put("fecha_expiracion", formato.format(cal.getTime()));

            // Solicitamos un nuevo qr al banco ganadero
            send = SocketCliente.sendSinc("banco_ganadero", send, 2 * 60 * 1000);

            if (send.getString("estado").equals("exito")) {
                solicitud_qr.put("qrid", send.getJSONObject("data").getString("qrId"));
                solicitud_qr.put("qrImage", send.getJSONObject("data").getString("qrImage"));

                SPGConect.editObject("solicitud_qr", solicitud_qr);

                obj = solicitud_qr; 
            } else {
                obj.put("error", send.getString("error"));
                obj.put("estado", "error");
            }

            
            return obj.toString();
        }catch (Exception e){
            SConsole.error("Error al procesar su solicitud", e.getMessage());
            throw new HttpException(Status.BAD_REQUEST, e.getLocalizedMessage());
        }    

        // DOC-> Tengo que retornar el status 200;
        // return "exito";
    }

}
