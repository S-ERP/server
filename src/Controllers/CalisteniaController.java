package Controllers;

import org.json.JSONObject;
import Servisofts.http.Exception.*;
import Servisofts.http.annotation.*;
import SocketCliente.SocketCliente;

@RestController
@RequestMapping("/venta")
public class CalisteniaController {

    @GetMapping("/status")
    public String status() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("controller", "Alvaro ventaController");
            obj.put("metodo", "GET");
            obj.put("estado", "exito ✅");
            obj.put("mensaje", "Servidor activo");
            return obj.toString();
        } catch (Exception e) {
            JSONObject error = new JSONObject();
            error.put("estado", "error ❌");
            error.put("mensaje", e.getMessage());
            return error.toString();
        }
    }

    @PostMapping("/status")
    public String statusPost() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("controller", "alvaro");
            obj.put("metodo", "POST");
            obj.put("estado", "exito ✅");
            obj.put("mensaje", "Servidor recibe POST correctamente");
            return obj.toString();
        } catch (Exception e) {
            JSONObject error = new JSONObject();
            error.put("estado", "error ❌");
            error.put("mensaje", e.getMessage());
            return error.toString();
        }
    }

    @PostMapping("/registrar")
    public String registrar(@RequestBody String body) throws HttpException {
        try {
            JSONObject data = new JSONObject(body);
            JSONObject obj = new JSONObject();
            obj.put("data", data);
            obj.put("key_empresa", "1234564787987213");
            obj.put("key_usuario", "noseestaenviandokey");
            JSONObject obT = new JSONObject();
            obT.put("service", "caja");
            obT.put("component", "caja_detalle");
            obT.put("type", "venta");
            obT.put("estado", "cargando");
            obT.put("data", data);
            JSONObject send = SocketCliente.sendSinc("caja", obT);
            obj.put("status", "Exito ✅");
            System.out.println(obT);
            System.out.println("---------------------");
            return send.get("data").toString();
        } catch (Exception e) {
            JSONObject error = new JSONObject();
            error.put("estado", "error");
            error.put("mensaje", e.getMessage());
            return error.toString();
        }
    }

}