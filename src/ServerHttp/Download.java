package ServerHttp;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Paths;

import org.jboss.com.sun.net.httpserver.Headers;
import org.jboss.com.sun.net.httpserver.HttpExchange;

import ServerHttp.Compressor.Compressor;
import ServerHttp.Compressor.ZIP;
import Servisofts.SConfig;
import Servisofts.SConsole;

public class Download {
    private static final int BUFFER_SIZE = 8192; // 8KB buffer size

    public static void handleRequest(HttpExchange exchange) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.add("Access-Control-Allow-Origin", "*");
        headers.add("Access-Control-Allow-Headers", "*");

        String ruta = SConfig.getJSON("files").getString("url");
        URI requestURI = exchange.getRequestURI();

        String[] paths = requestURI.getPath().split("/");
        for (int i = 1; i < paths.length; i++) {
            ruta += paths[i] + "/";
        }
        ruta = ruta.substring(0, ruta.length() - 1);
        File file = new File(ruta);
        if (!file.exists()) {
            file = new File("./default.png");
        }

        String compressParam = getQueryParam(exchange.getRequestURI(), "compress");

        boolean changeName = false;
        if (file.isDirectory()) {
            if (!"zip".equals(compressParam)) {
                exchange.sendResponseHeaders(502, -1); // Internal Server Error
                return;
            }
            file = toZip(file, true);
            changeName = true;
        } else {
            if ("zip".equals(compressParam)) {
                file = toZip(file, false);
                changeName = true;
            }
        }

        String rangeHeader = exchange.getRequestHeaders().getFirst("Range");
        // SConsole.info("rangeHeader", rangeHeader);

        if (rangeHeader == null) {
            byte[] bytearray = new byte[(int) file.length()];
            FileInputStream fis = new FileInputStream(file);
            BufferedInputStream bis = new BufferedInputStream(fis);
            bis.read(bytearray, 0, bytearray.length);
            exchange.sendResponseHeaders(200, file.length());
            OutputStream os = exchange.getResponseBody();
            os.write(bytearray, 0, bytearray.length);
            os.close();
            return;
        }
        // SConsole.info("ruta=", ruta);
        // SConsole.info("file=", file.getAbsolutePath());

        long fileLength = file.length();
        long start = 0, end = fileLength - 1;

        if (rangeHeader != null) {
            String[] ranges = rangeHeader.substring("bytes=".length()).split("-");
            try {
                start = Long.parseLong(ranges[0]);
                if (ranges.length > 1 && !ranges[1].isEmpty()) {
                    end = Long.parseLong(ranges[1]);
                }
            } catch (NumberFormatException e) {
                exchange.sendResponseHeaders(416, -1); // 416 for Range Not Satisfiable
                return;
            }
        }

        if (start >= fileLength || end >= fileLength) {
            exchange.sendResponseHeaders(416, -1); // 416 for Range Not Satisfiable
            return;
        }

        long contentLength = end - start + 1;
        String contentType = getContentType(file.getName());
        if(contentType == null) {
            contentType =  Compressor.getMimeType(file);
        }
        if (contentType == null) {
            contentType = "application/octet-stream"; // Default content type if not recognized
        }

        if (changeName) {
            headers.set("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");
        }
        headers.set("Content-Type", contentType);
        headers.set("Content-Length", Long.toString(contentLength));
        headers.set("Accept-Ranges", "bytes");
        if (rangeHeader != null) {
            headers.set("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
            exchange.sendResponseHeaders(206, contentLength); // 206 for partial content
        } else {
            exchange.sendResponseHeaders(200, fileLength);
        }

        try (OutputStream os = exchange.getResponseBody();
                FileInputStream fis = new FileInputStream(file);
                BufferedInputStream bis = new BufferedInputStream(fis)) {

            bis.skip(start);
            byte[] buffer = new byte[BUFFER_SIZE];
            long bytesToRead = contentLength;
            int bytesRead;
            while ((bytesRead = bis.read(buffer, 0, (int) Math.min(buffer.length, bytesToRead))) != -1) {
                os.write(buffer, 0, bytesRead);
                bytesToRead -= bytesRead;
                if (bytesToRead <= 0)
                    break;
            }
        }
    }

    private static String getContentType(String fileName) {
        if (fileName.endsWith(".mp4")) {
            return "video/mp4";
        } else if (fileName.endsWith(".mov")) {
            return "video/quicktime";
        } else {
            return "application/octet-stream";
        }
        
    }

    private static File toZip(File source, boolean isFolder) {

        String targetPath = SConfig.getJSON("files").getString("url") + "/tmp";
        File targetFolder = new File(targetPath);
        if (!targetFolder.exists()) {
            targetFolder.mkdirs();
        }

        File outputZip = new File(targetPath + "/" + source.getName() + ".zip");
        if (outputZip.exists()) {
            outputZip.delete();
        }

        String outputPath = targetPath + "/" + source.getName() + ".zip";
        outputZip = new File(outputPath);
        System.out.println(outputZip.getPath());

        try {
            if (isFolder) {
                ZIP.zipFolder(Paths.get(source.getPath()), Paths.get(outputZip.getPath()));
            } else {
                ZIP.zipFile(Paths.get(source.getPath()), Paths.get(outputZip.getPath()));
            }

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        outputZip = new File(outputPath);
        return outputZip;
    }

    private static String getQueryParam(URI uri, String key) {
        String query = uri.getQuery();
        if (query == null)
            return null;

        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=");
            if (keyValue.length > 1 && keyValue[0].equalsIgnoreCase(key)) {
                return keyValue[1];
            }
        }
        return null;
    }
}
