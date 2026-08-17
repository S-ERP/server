package _component;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;

import org.bouncycastle.util.encoders.Base64;
import org.json.JSONArray;
import org.json.JSONObject;

import Server.SSSAbstract.SSServerAbstract;
import Server.SSSAbstract.SSSessionAbstract;
import Server.ServerSocket.ServerSocket;
import Server.ServerSocketWeb.ServerSocketWeb;
import ServerHttp.ImageCompressor;
import ServerHttp.ServerHttp;
import Servisofts.Regex;
import Servisofts.SConfig;
import Servisofts.SConsole;
import Servisofts.SLog;
import Servisofts.SMyIp;
import Servisofts.SSL;
import Servisofts.Servisofts;
import SocketCliente.SocketCliente;
// import SocketServer.SocketServer;

public class _servicio {

    public static JSONObject ServiciosConocidos = new JSONObject();
    // HashMap<>();

    public _servicio(JSONObject obj, SSSessionAbstract sesion) {
        if (!obj.isNull("type")) {
            // if (sesion == null) {
            // SConsole.warning("Socket Client,",
            // " ( OU= " + obj.getJSONObject("info").getJSONObject("cert").getString("OU"),
            // ") servicio,",
            // obj.getString("type"));
            // } else {
            // SConsole.warning("Socket Server,", "servicio,", obj.getString("type"));
            // }
            switch (obj.getString("type")) {
                case "init":
                    init(obj, sesion);
                    break;
                case "initServer":
                    initServer(obj, sesion);
                    break;
                case "initClient":
                    initClient(obj, sesion);
                    break;
                case "getServicioHabilitado":
                    getServicioHabilitado(obj, sesion);
                    break;
                case "getEstadoServicio":
                    getEstadoServicio(obj, sesion);
                    break;
                case "getPem":
                    getPem(obj);
                    break;
                case "comprimir":
                    comprimir(obj);
                    break;

            }
        }
    }

    private void getEstadoServicio(JSONObject obj, SSSessionAbstract sesion) {
        if (obj.getString("estado").equals("error")) {
            SConsole.error("ERROR EN EL ESTADO DEL SERVICIO ", obj.getString("error"));
            // sesion.onClose(obj);
            return;
        }
        JSONObject servicio = obj.getJSONObject("data");
        // SConsole.warning("Servicio conocido insertado", servicio);
        ServiciosConocidos.put(servicio.getString("nombre"), servicio);
        SSServerAbstract.getSession(obj.getString("id_session")).setServicio(servicio);
        obj.put("component", "servicio");
        obj.put("socket", "usuario");
        obj.put("type", "initClient");
        obj.put("estado", "exito");
        obj.put("data", servicio);
        SSServerAbstract.getSession(obj.getString("id_session")).send(obj.toString());

        // services_sessions.put(obj.getString("key"), sesion);
        SConsole.succes("SERVICIO Identificado", "\t|\t", obj.getString("id_session"), "\t|\t",
                servicio.getString("nombre"));
    }

    private void getServicioHabilitado(JSONObject obj, SSSessionAbstract sesion) {
        JSONArray data = obj.getJSONArray("data");
        SConsole.info("----SERVICIO HABILITADOS---------------------------");
        JSONObject serviciosH = new JSONObject();
        String nombre;
        String host;
        for (int i = 0; i < data.length(); i++) {
            JSONObject serhabilitado = data.getJSONObject(i);
            nombre = serhabilitado.getJSONObject("servicio").getString("nombre");
            try {
                host = serhabilitado.getJSONObject("servicio").getString("ip") + ":"
                        + serhabilitado.getJSONObject("servicio").getInt("puerto");
                SocketCliente.servicios_habilitados.put(nombre,
                        serhabilitado.getJSONObject("servicio"));

                SConsole.info(host + "\t" + nombre);
                if (!serhabilitado.getJSONObject("servicio").getString("nombre").equals("servicio")) {
                    // SLog.put("Servicios." + nombre + ".host", host);
                    SLog.put("Servicios." + nombre, false);
                    SocketCliente.StartServicio(serhabilitado.getJSONObject("servicio").getString("nombre"));
                }
            } catch (Exception e) {
                SConsole.error("Error conectando Servicio", nombre, ": ", e.getMessage());
            }
        }
        SConsole.info("---------------------------------------------------");
    }

    private void initServer(JSONObject obj, SSSessionAbstract sesion) {

        if (obj.getString("estado").equals("exito")) {
            if (!Servisofts.isInitServer()) {
                new ServerSocket(obj.getJSONObject("data").getInt("puerto"));
                new ServerSocketWeb(obj.getJSONObject("data").getInt("puerto_ws"));
                ServerHttp.Start(obj.getJSONObject("data").getInt("puerto_http"));
                Servisofts.setInitServer(true);
            }
            JSONObject objSend = new JSONObject();
            objSend.put("component", "servicio");
            objSend.put("type", "getServicioHabilitado");
            objSend.put("key", obj.getJSONObject("data").getString("key"));
            objSend.put("estado", "cargando");
            SocketCliente.send("servicio", objSend.toString());

        } else {
            SConsole.error("Socket Client,", "servicio,", obj.getString("type"), "\t|\t", obj.getString("error"));
            System.exit(0);
        }
    }

    private void initClient(JSONObject obj, SSSessionAbstract sesion) {
        if (sesion == null) {
            String name = obj.getJSONObject("info").getJSONObject("cert").getString("OU");
            SConsole.succes("SERVER INICIADO:\t\t" + name);
            // SLog.put("Servicios." + name + ".status", "exito");
            SLog.put("Servicios." + name, true);
            return;
        }
        SConsole.succes("Indentificado como: " + obj.getString("id") + " - " + obj.getJSONObject("data").toString());
        JSONObject data = obj.getJSONObject("data");
        String OU = data.getJSONObject("cert").getString("OU");

        JSONObject objSend = new JSONObject();
        objSend.put("component", "servicio");
        objSend.put("type", "getEstadoServicio");
        objSend.put("data", data);
        objSend.put("id_session", sesion.getIdSession());
        objSend.put("estado", "cargando");
        if(obj.has("_sincrone_key_" + OU)){
            objSend.put("_sincrone_key_" + OU, obj.optString("_sincrone_key_" + OU));
        }

        if (ServiciosConocidos.has(OU)) {
            SConsole.warning(
                    "Se inicio un servicio conocido para evitar ir a servicio intentando resolver el error de desconeccion",
                    OU);
            objSend.put("data", ServiciosConocidos.getJSONObject(OU));
            objSend.put("estado", "exito");
      
            getEstadoServicio(objSend, sesion);
            obj.put("noSend", true);
            return;

        }
        SocketCliente.send("servicio", objSend, sesion);
        obj.put("noSend", true);
    }

    private void init(JSONObject obj, SSSessionAbstract sesion) {
        JSONObject data = new JSONObject();
        data.put("cert", SConfig.getJSON("ssl").getJSONObject("cert"));
        data.put("ip", SMyIp.getLocalIp());
        data.put("ip_public","servisofts.com");
        String fingerp = SSL.getFingerPrint(SConfig.getJSON("ssl").getJSONObject("cert").getString("OU"));
        data.put("fingerp", fingerp);
        JSONObject objSend = new JSONObject();
        if (obj.getString("socket").equals("servicio")) {
            // Solo cuando me conecto con servicio
            objSend.put("component", "servicio");
            objSend.put("type", "initServer");
            objSend.put("data", data);
            objSend.put("estado", "cargando");
            SocketCliente.send("servicio", objSend.toString());
        } else {
            objSend.put("component", "servicio");
            objSend.put("type", "initClient");
            objSend.put("data", data);
            objSend.put("estado", "cargando");
            SocketCliente.send(obj.getString("socket"), objSend.toString());
        }

    }

    private void getPem(JSONObject obj) {
        // System.out.println(obj.toString());

        try {
            String pem = obj.getString("data");
            byte[] prvBlob = Base64.decode(pem);
            X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(prvBlob));
            String nombre = Regex.findOU(cert.getSubjectX500Principal().getName());
            SSL.registerPem(nombre, cert);
            SocketCliente.servicios_habilitados.getJSONObject(nombre).put("pem", nombre + ".pem");
        } catch (CertificateException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void comprimir(JSONObject obj) {
        // System.out.println(obj.toString());

        try {
            String url = obj.getString("url");
            // String url = "/u01/servisoftsFiles/tapeke";
            // String url = "/u01/servisoftsFiles/tapeke_test/restaurant";
            String[] skipFolders = { "gpx" };
            SConsole.info("Comprimiendo a 128");
            ImageCompressor.compress(url, true, false, true, 128, skipFolders);
            SConsole.info("Comprimiendo a 512");
            ImageCompressor.compress(url, true, false, true, 512, skipFolders);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void comprimir2(JSONObject obj) {
        // System.out.println(obj.toString());

        try {
            String url = obj.getString("url");
            // String url = "/u01/servisoftsFiles/tapeke";
            // String url = "/u01/servisoftsFiles/tapeke_test/restaurant";
            String[] skipFolders = { "gpx" };
            SConsole.info("Comprimiendo a 128");
            ImageCompressor.compress(url, true, false, true, 128, skipFolders);
            SConsole.info("Comprimiendo a 512");
            ImageCompressor.compress(url, true, false, true, 512, skipFolders);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

}
