package ServerHttp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.jboss.com.sun.net.httpserver.HttpExchange;
import org.jboss.com.sun.net.httpserver.HttpHandler;

import ServerHttp.Compressor.Compressor;
import Servisofts.SConfig;

public class Uploadv3 implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
            // algo
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "*");
            exchange.sendResponseHeaders(200, 0);
            return;
        }

        if ("POST".equals(exchange.getRequestMethod())) {
            handleRequest(exchange);
        } else {
            exchange.sendResponseHeaders(405, -1); // Method Not Allowed
        }
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        try {
            InputStream is = exchange.getRequestBody();

            // -----------------------------------
            File file;
            String ruta = "";
            String nombre = "";
            String[] paths = exchange.getRequestURI().getPath().split("/");
            for (int i = 0; i < paths.length; i++) {
                if (i > 1) {
                    if (i == paths.length - 1) {
                        nombre = paths[i];
                    } else
                        ruta += paths[i] + "/";
                }
            }
            ruta = ruta.substring(0, ruta.length() - 1);
            file = new File(SConfig.getJSON("files").getString("url") + ruta);
            if (!file.exists()) {
                file.mkdirs();
            }
            file = new File(SConfig.getJSON("files").getString("url") + ruta + "/" + nombre);
            OutputStream os = new FileOutputStream(file);
            // -----------------------------------

            byte[] buffer = new byte[1024];
            int bytesRead;
            long totalBytesRead = 0;
            long fileSize = exchange.getRequestHeaders().getFirst("Content-Length") != null
                    ? Long.parseLong(exchange.getRequestHeaders().getFirst("Content-Length"))
                    : -1;

            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
                if (fileSize != -1) {
                    double progress = (double) totalBytesRead / fileSize * 100;
                    // Simula un callback o log del progreso de carga en el servidor.
                    System.out.printf("Progress: %.2f%%%n", progress);
                }
            }
            os.close();
            is.close();

            Compressor.compress(file, "");

            String response = "File uploaded successfully.";
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            OutputStream osResponse = exchange.getResponseBody();
            osResponse.write(response.getBytes(StandardCharsets.UTF_8));
            osResponse.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
