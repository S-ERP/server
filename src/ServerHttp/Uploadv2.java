package ServerHttp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.jboss.com.sun.net.httpserver.HttpExchange;
import org.jboss.com.sun.net.httpserver.HttpHandler;
import org.json.JSONException;

import ServerHttp.Compressor.Compressor;
import ServerHttp.Compressor.VideoCompressor;
import Servisofts.SConfig;

public class Uploadv2 implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "*");
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return;
        }

        if ("POST".equals(exchange.getRequestMethod())) {
            handleRequest(exchange);
        } else {
            exchange.sendResponseHeaders(405, -1); // Method Not Allowed
            exchange.close();
        }
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        try {
            InputStream is = exchange.getRequestBody();

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
            System.out.println("ruta" + ruta);
            String query = exchange.getRequestURI().getQuery();
            String[] queryParams = query.split("&");
            int chunkNumber = -1;
            int totalChunks = -1;
            long lastModified = -1;

            for (String param : queryParams) {
                String[] keyValue = param.split("=");
                switch (keyValue[0]) {
                    case "chunkNumber":
                        chunkNumber = Integer.parseInt(keyValue[1]);
                        break;
                    case "totalChunks":
                        totalChunks = Integer.parseInt(keyValue[1]);
                        break;
                    case "lastModified":
                        lastModified = Long.parseLong(keyValue[1]); // Capturar la fecha de modificación
                        break;
                }
            }

            if (chunkNumber == -1 || totalChunks == -1) {
                exchange.sendResponseHeaders(400, -1); // Bad Request
                return;
            }

            File dir = new File(SConfig.getJSON("files").getString("url") + ruta);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            dir = new File(SConfig.getJSON("files").getString("url") + "/.tmp/" + ruta);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            File file = new File(dir, nombre + ".part" + chunkNumber);

            OutputStream os = new FileOutputStream(file);

            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            os.close();
            is.close();

            if (lastModified > 0) {
                file.setLastModified(lastModified);
            }

            if (chunkNumber == totalChunks - 1) {
                combineChunks(ruta, nombre, totalChunks, lastModified);
            }

            String response = "Chunk " + chunkNumber + " uploaded successfully.";
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            OutputStream osResponse = exchange.getResponseBody();
            osResponse.write(response.getBytes(StandardCharsets.UTF_8));
            osResponse.close();
        } catch (Exception e) {
            e.printStackTrace();
            exchange.sendResponseHeaders(500, -1); // Internal Server Error
        }
    }

    public static String getMimeType(java.io.File file) throws IOException {
        Path path = Paths.get(file.getAbsolutePath());
        return Files.probeContentType(path);
    }

    private void combineChunks(String ruta, String fileName, int totalChunks, long lastModified) throws IOException {
        File outputFile = new File(SConfig.getJSON("files").getString("url") + ruta + "/" + fileName);
        if (outputFile.exists()) {
            outputFile.delete();
        }

        outputFile = new File(SConfig.getJSON("files").getString("url") + ruta + "/" + fileName);
        // if (outputFile.exists())
        // return;

        OutputStream os = new FileOutputStream(outputFile, true);

        for (int i = 0; i < totalChunks; i++) {
            File chunkFile = new File(
                    SConfig.getJSON("files").getString("url") + "/.tmp/" + ruta + "/" + fileName + ".part" + i);
            InputStream is = new FileInputStream(chunkFile);
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            chunkFile.delete(); // Eliminar el fragmento después de combinarlo
        }
        os.close();

        if (lastModified > 0) {
            outputFile.setLastModified(lastModified);
        }
        Compressor.compress(outputFile, "");
        /*
         * String mime = getMimeType(outputFile);
         * if (mime!=null && mime.indexOf("video") > -1) {
         * try {
         * VideoCompressor.createMiniature(outputFile.getPath());
         * // VideoCompressor.convertMOVtoMP4(SConfig.getJSON("files").getString("url")
         * +
         * // ruta + "/" + fileName, SConfig.getJSON("files").getString("url") + ruta +
         * "/"
         * // + fileName+".mp4");
         * } catch (Exception e) {
         * e.printStackTrace();
         * }
         * // // VideoConverter.convertToMp4(SConfig.getJSON("files").getString("url") +
         * // ruta + "/" + fileName, SConfig.getJSON("files").getString("url") + ruta +
         * // "/__" + fileName);
         * }
         * // File input = new
         * // File(SConfig.getJSON("files").getString("url")+ruta+"/"+fileName);
         * // if(ImageCompressor.isImageFile(input)){
         * // File output120 = new
         * // File(SConfig.getJSON("files").getString("url")+ruta+"/120_"+fileName);
         * // ImageCompressor.resizeAndCompressImage(input, output120, 120, 120, 0.8f);
         * 
         * // File output480 = new
         * // File(SConfig.getJSON("files").getString("url")+ruta+"/480_"+fileName);
         * // ImageCompressor.resizeAndCompressImage(input, output480, 480, 480, 0.8f);
         * // }
         * 
         */
    }

}
