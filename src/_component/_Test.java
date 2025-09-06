package _component;

import org.json.JSONArray;
import org.json.JSONObject;
import Server.SSSAbstract.SSSessionAbstract;
import Servisofts.SConsole;
import Servisofts.SPGConect;
import SocketCliente.SocketCliente;

public class _Test {

    // DATA TABLE = usuario

    // key CV
    // user CV
    // pass CV
    // key_persona CV
    // telefono CV
    // correo CV
    // estado INT

    public _Test(JSONObject data, SSSessionAbstract session) {
        switch (data.getString("type")) {
            case "live":
                live(data, session);
            break;
            case "testServicioHabilitado":
                testServicioHabilitado(data, session);
            break;
            case "restartServicio":
                restartServicio(data, session);
            break;
            case "testBD":
                testBD(data, session);
            break;
            case "restartBD":
                restartBD(data, session);
            break;
            case "closeBD":
                closeBD(data, session);
            break;
            case "callServicio":
                callServicio(data, session);
            break;
            case "callServicioN":
                callServicioN(data, session);
            break;
        }
    }

    public static String messageLog = "[test]";
    public static int timeOut = 5000;

    public void live(JSONObject obj, SSSessionAbstract session) {
        obj.put("estado", "exito");
    }

    public void testServicioHabilitado(JSONObject obj, SSSessionAbstract session) {
        JSONObject send = new JSONObject();
        send.put("version", "2.0");
        send.put("component", "test");
        send.put("type", "live");
        send.put("estado", "cargando");
        long startTime = System.nanoTime();
        try {
            send = SocketCliente.sendSinc(obj.getString("nombre"), send, timeOut);
            obj.put("data", send);
        } catch (Exception e) {
            send.put("estado", "error");
            send.put("error", e.getMessage());
            obj.put("data", send);
        }
        long endTime = System.nanoTime();
        long durationInMillis = (endTime - startTime) / 1_000_000;
        obj.put("ping", durationInMillis);
        SConsole.info("[test] [ServicioHabilitado] [ping=", durationInMillis, "] servicio = ", obj.getString("nombre"), " status = ", obj.getString("estado"));
        // SConsole.info("[test] [ServicioHabilitado] [ping=", durationInMillis, "] Respuesta = ", obj.toString(), obj.getString("estado"));
    }

    public void restartServicio(JSONObject obj, SSSessionAbstract session) {
        long startTime = System.nanoTime();
        try {
            SConsole.warning("Reiniciando la conexion con el cliente por orden de _Test restartServicio");
            SocketCliente.reconect(obj.getString("nombre"));
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
        }
        long endTime = System.nanoTime();
        long durationInMillis = (endTime - startTime) / 1_000_000;
        obj.put("ping", durationInMillis);
        // SConsole.info("[test] [RestartServicio] [ping=", durationInMillis, "] Respuesta = ", obj.toString(), obj.getString("estado"));
    }

    public void testBD(JSONObject obj, SSSessionAbstract session) {
        long startTime = System.nanoTime();
        if(SPGConect.isLive()) {
            obj.put("estado", "exito");
        } else {
            obj.put("estado", "error"); 
            obj.put("error", "No Conectado al a Base de Datos"); 
        }
        long endTime = System.nanoTime();
        long durationInMillis = (endTime - startTime) / 1_000_000;
        obj.put("ping", durationInMillis);
        // SConsole.info("[test] [testBD] [ping=", durationInMillis, "] Respuesta = ", obj.toString(), obj.getString("estado"));
    }

    public void restartBD(JSONObject obj, SSSessionAbstract session) {
        long startTime = System.nanoTime();
        if(SPGConect.restartConexion(true)) {
            obj.put("estado", "exito");
        } else {
            obj.put("estado", "error"); 
            obj.put("error", "No Conectado al a Base de Datos"); 
        }
        long endTime = System.nanoTime();
        long durationInMillis = (endTime - startTime) / 1_000_000;
        obj.put("ping", durationInMillis);
        // SConsole.info("[test] [restartBD] [ping=", durationInMillis, "] Respuesta = ", obj.toString(), obj.getString("estado"));
    }

    public void closeBD(JSONObject obj, SSSessionAbstract session) {
        long startTime = System.nanoTime();
        SPGConect.desconectar();
        obj.put("estado", "exito");
        long endTime = System.nanoTime();
        long durationInMillis = (endTime - startTime) / 1_000_000;
        obj.put("ping", durationInMillis);
        SConsole.info("[test] [closeBD] [ping=", durationInMillis, "] Respuesta = ", obj.toString(), obj.getString("estado"));
    }

    public void callServicio(JSONObject obj, SSSessionAbstract session) {
        // SConsole.info("callServicio", obj.toString());
        JSONObject send = new JSONObject(obj.getJSONObject("data").toString());
        send.put("version", "2.0");
        // send.put("component", obj.get("component"));
        // send.put("type", obj.get("type"));
        send.put("estado", "cargando");
        long startTime = System.nanoTime();
        try {
            send = SocketCliente.sendSinc(obj.getString("nombre"), send, timeOut);
            obj.put("data", send);
        } catch (Exception e) {
            send.put("estado", "error");
            send.put("error", e.getMessage());
            obj.put("data", send);
        }
        long endTime = System.nanoTime();
        long durationInMillis = (endTime - startTime) / 1_000_000;
        obj.put("ping", durationInMillis);
        // SConsole.info("callServicio Respuesta", obj.toString());
    }

    public void callServicioN(JSONObject obj, SSSessionAbstract session) {
        // SConsole.info("callServicioN", obj.toString());
        int cantidad = obj.getInt("cantidad");
        // JSONArray resp = new JSONArray();
        for (int i = 0; i < cantidad; i++) {
            // SConsole.info("********************", "Intento ", i, "********************");
            JSONObject objNew = new JSONObject(obj.toString());
            callServicio(objNew, session);
            // resp.put(objNew);
            SConsole.info("Intento ", i, objNew.getJSONObject("data").getString("estado"));
            // SConsole.info("*************************************************************");
        }

        // obj.put("response", resp);
    }

}