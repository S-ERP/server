package ServerHttp;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.sql.rowset.serial.SerialBlob;

import org.jboss.com.sun.net.httpserver.HttpExchange;
import org.json.JSONObject;
import _component._Manejador;

public class Api {
    public static void POST(HttpExchange t) throws IOException {
        try {

            t.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            t.getResponseHeaders().add("Access-Control-Allow-Headers", "*");

            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(t.getRequestBody(), "UTF-8"));

            StringBuilder sb = new StringBuilder();
            // InputStream ios = t.getRequestBody();
            
            String i;
            while ((i = bufferedReader.readLine()) != null) {
                sb.append(i);
            }
            JSONObject request = new JSONObject(sb.toString());
            bufferedReader.close();
            request = _Manejador.factory(request, null);
            byte[] bs = request.toString().getBytes("UTF-8");
            t.sendResponseHeaders(200, bs.length);
            OutputStream os = t.getResponseBody();
            os.write(bs);
            os.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getBase64FromUrl(String imageUrl) {
        try {
            // Conexión a la URL y obtención de un InputStream
            URL url = new URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.connect();

            // Convertir InputStream a byte[]
            InputStream inputStream = connection.getInputStream();
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                byteArrayOutputStream.write(buffer, 0, bytesRead);
            }
            byte[] imageBytes = byteArrayOutputStream.toByteArray();

            // Convertir byte[] a Blob
            SerialBlob imageBlob = new SerialBlob(imageBytes);

            // Convertir Blob a String en base64
            String base64String = blobToBase64String(imageBlob);

            // Cerrar las conexiones
            inputStream.close();
            byteArrayOutputStream.close();
            connection.disconnect();

            return base64String;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String blobToBase64String(SerialBlob blob) throws Exception {
        byte[] blobBytes = blob.getBytes(1, (int) blob.length());
        return java.util.Base64.getEncoder().encodeToString(blobBytes);
    }
}
