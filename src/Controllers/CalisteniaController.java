package Controllers;

import org.json.JSONObject;

import Component.SolicitudQr;
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

    @PostMapping("/solicitarCaja")
    public String solicitarCaja(@RequestBody String body) throws HttpException {
        try {

            // Validar que el body no venga vacío
            if (body == null || body.isEmpty()) {
                JSONObject error = new JSONObject();
                error.put("estado", "error");
                error.put("mensaje", "Body vacío");
                return error.toString();
            }

            JSONObject data = new JSONObject(body);

            // Validar que venga la key
            if (!data.has("key_puntoventa_serp")) {
                JSONObject error = new JSONObject();
                error.put("estado", "error");
                error.put("mensaje", "Falta key_puntoventa_serp");
                return error.toString();
            }

            String key_punto = data.getString("key_puntoventa_serp");

            System.out.println("KEY PUNTO VENTA: " + key_punto);

            // Construir objeto para enviar al otro servidor
            JSONObject obT = new JSONObject();
            obT.put("servicio", "caja"); // 👈 IMPORTANTE (no service)
            obT.put("component", "caja");
            obT.put("type", "getAutomatica");
            obT.put("estado", "cargando");
            obT.put("key_punto_venta", key_punto);

            // Enviar al socket
            JSONObject send = SocketCliente.sendSinc("caja", obT);

            System.out.println("RESPUESTA SOCKET: " + send);

            if (send == null) {
                JSONObject error = new JSONObject();
                error.put("estado", "error");
                error.put("mensaje", "Sin respuesta del servidor caja");
                return error.toString();
            }

            return send.toString();

        } catch (Exception e) {
            JSONObject error = new JSONObject();
            error.put("estado", "error");
            error.put("mensaje", e.getMessage());
            e.printStackTrace();
            return error.toString();
        }
    }

}