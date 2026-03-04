package Controllers;

import org.json.JSONObject;

import Component.SolicitudQr;
import Servisofts.http.Exception.*;
import Servisofts.http.annotation.*;
import SocketCliente.SocketCliente;
import picocli.CommandLine.Model;

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

    @PostMapping("/validarAlmacen")
    public String validarAlmacen(@RequestBody String body) {
        JSONObject response = new JSONObject();

        try {
            System.out.println("Inicio de validación de almacén");

            if (body == null || body.trim().isEmpty()) {
                System.out.println("Body vacío");
                response.put("estado", "error");
                response.put("mensaje", "Body vacío");
                return response.toString();
            }
            System.out.println("Body recibido correctamente");

            JSONObject data = new JSONObject(body);

            if (!data.has("key_almacen_serp") || data.getString("key_almacen_serp").isEmpty()) {
                System.out.println("Falta key_almacen_serp");
                response.put("estado", "error");
                response.put("mensaje", "Falta key_almacen_serp");
                return response.toString();
            }

            String keyAlmacen = data.getString("key_almacen_serp");
            System.out.println("Key de almacén recibida: " + keyAlmacen);

            JSONObject obAlmacen = new JSONObject();
            obAlmacen.put("servicio", "inventario");
            obAlmacen.put("component", "almacen");
            obAlmacen.put("type", "getByKey");
            obAlmacen.put("estado", "cargando");
            obAlmacen.put("key", keyAlmacen);

            System.out.println("Enviando solicitud al socket");
            JSONObject send = SocketCliente.sendSinc("inventario", obAlmacen);

            // Validar si existe el almacén
            if (send != null && "exito".equalsIgnoreCase(send.optString("estado"))) {
                JSONObject dataAlmacen = send.optJSONObject("data");
                if (dataAlmacen != null && keyAlmacen.equals(dataAlmacen.optString("key"))) {
                    System.out.println("Almacén encontrado");
                    response.put("estado", "exito");
                    response.put("mensaje", "Almacén si existe en SERP");
                    return response.toString();
                }
            }

            System.out.println("Almacén no encontrado");
            response.put("estado", "error");
            response.put("mensaje", "Almacén no existe en SERP");
            return response.toString();

        } catch (Exception e) {
            System.out.println("Excepción capturada: " + e.getMessage());
            response.put("estado", "error");
            response.put("mensaje", "Error al validar el almacén");
            e.printStackTrace();
            return response.toString();
        }
    }

    @PostMapping("/validarModelo")
    public String validarModelo(@RequestBody String body) {
        JSONObject response = new JSONObject();

        try {
            System.out.println("Inicio de validación de modelo");

            if (body == null || body.trim().isEmpty()) {
                System.out.println("Body vacío");
                response.put("estado", "error");
                response.put("mensaje", "Body vacío");
                return response.toString();
            }

            System.out.println("Body recibido correctamente");

            JSONObject data = new JSONObject(body);

            if (!data.has("key_modelo_serp") || data.getString("key_modelo_serp").isEmpty()) {
                System.out.println("Falta key_modelo_serp");
                response.put("estado", "error");
                response.put("mensaje", "Falta key_modelo_serp");
                return response.toString();
            }

            String keyModelo = data.getString("key_modelo_serp");
            System.out.println("Key de modelo recibida: " + keyModelo);

            JSONObject obModelo = new JSONObject();
            obModelo.put("servicio", "inventario");
            obModelo.put("component", "modelo"); // 👈 cambiamos aquí
            obModelo.put("type", "getByKey");
            obModelo.put("estado", "cargando");
            obModelo.put("key", keyModelo);

            System.out.println("Enviando solicitud al socket");
            JSONObject send = SocketCliente.sendSinc("inventario", obModelo);

            // Validar si existe el modelo
            if (send != null && "exito".equalsIgnoreCase(send.optString("estado"))) {
                JSONObject dataModelo = send.optJSONObject("data");
                if (dataModelo != null && keyModelo.equals(dataModelo.optString("key"))) {
                    System.out.println("Modelo encontrado");
                    response.put("estado", "exito");
                    response.put("mensaje", "Modelo si existe en SERP");
                    return response.toString();
                }
            }

            System.out.println("Modelo no encontrado");
            response.put("estado", "error");
            response.put("mensaje", "Modelo no existe en SERP");
            return response.toString();

        } catch (Exception e) {
            System.out.println("Excepción capturada: " + e.getMessage());
            response.put("estado", "error");
            response.put("mensaje", "Error al validar el modelo");
            e.printStackTrace();
            return response.toString();
        }
    }

    @PostMapping("/registrarCliente")
    public String registrarCliente(@RequestBody String body) {

        JSONObject response = new JSONObject();

        try {

            System.out.println("Inicio de registro de cliente");

            if (body == null || body.trim().isEmpty()) {
                response.put("estado", "error");
                response.put("mensaje", "Body vacío");
                return response.toString();
            }

            JSONObject data = new JSONObject(body);

            if (!data.has("nit") || data.getString("nit").isEmpty()) {
                response.put("estado", "error");
                response.put("mensaje", "Falta NIT");
                return response.toString();
            }

            if (!data.has("razon_social") || data.getString("razon_social").isEmpty()) {
                response.put("estado", "error");
                response.put("mensaje", "Falta razón social");
                return response.toString();
            }

            // ========================= ARMAR REQUEST CRM =========================
            JSONObject request = new JSONObject();
            request.put("servicio", "crm");
            request.put("component", "cliente");
            request.put("type", "registro");
            request.put("data", data);
            request.put("key_usuario", "b2aa9d81-5f63-40ce-ae35-31fbb1417745");

            System.out.println("Enviando solicitud de registro a CRM");

            JSONObject send = SocketCliente.sendSinc("crm", request);

            // ========================= VALIDAR RESPUESTA =========================
            if (send != null && "exito".equalsIgnoreCase(send.optString("estado"))) {

                response.put("estado", "exito");
                response.put("mensaje", "Cliente registrado correctamente en SERP");
                response.put("data", send.optJSONObject("data"));
                return response.toString();
            }

            response.put("estado", "error");
            response.put("mensaje", send != null
                    ? send.optString("mensaje", "Error al registrar cliente")
                    : "No hubo respuesta del servidor CRM");

            return response.toString();

        } catch (Exception e) {

            response.put("estado", "error");
            response.put("mensaje", "Error interno al registrar cliente");
            response.put("detalle", e.getMessage());
            e.printStackTrace();
            return response.toString();
        }
    }

    @PostMapping("/validarNit")
    public String validarNit(@RequestBody String body) {

        JSONObject response = new JSONObject();
        System.out.println();
        try {

            System.out.println("Inicio de validación de NIT");

            if (body == null || body.trim().isEmpty()) {
                response.put("estado", "error");
                response.put("mensaje", "Body vacío");
                return response.toString();
            }

            JSONObject data = new JSONObject(body);

            if (!data.has("nit") || data.getString("nit").isEmpty()) {
                response.put("estado", "error");
                response.put("mensaje", "Falta NIT");
                return response.toString();
            }

            String nit = data.getString("nit");
            System.out.println("NIT recibido: " + nit);

            // ========================= ARMAR REQUEST A CRM =========================
            JSONObject requestNit = new JSONObject();
            requestNit.put("servicio", "crm");
            requestNit.put("component", "cliente");
            requestNit.put("type", "buscar_nit");
            requestNit.put("key_empresa", "f894ea35-5ad1-4b61-a2d0-9294965be169");
            requestNit.put("nit", nit);

            System.out.println("Enviando solicitud al socket CRM");

            JSONObject send = SocketCliente.sendSinc("crm", requestNit);

            // ========================= VALIDAR RESPUESTA =========================
            if (send != null && "exito".equalsIgnoreCase(send.optString("estado"))) {

                JSONObject dataCliente = send.optJSONObject("data");

                if (dataCliente != null && !dataCliente.isEmpty()) {

                    response.put("estado", "exito");
                    response.put("mensaje", "NIT existe en SERP");
                    response.put("data", dataCliente); // opcional: devolver datos del cliente
                    return response.toString();
                }
            }

            response.put("estado", "error");
            response.put("mensaje", "NIT no existe en SERP");
            return response.toString();

        } catch (Exception e) {

            response.put("estado", "error");
            response.put("mensaje", "Error al validar el NIT");
            e.printStackTrace();
            return response.toString();
        }
    }

}
