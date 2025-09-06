package Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;

import org.json.JSONObject;

import Server.SSSAbstract.SSSessionAbstract;
import Servisofts.SConfig;
import Servisofts.SConsole;

public class Wtspp {

    public static final String COMPONENT = "whatsapp";
    public static JSONObject listener = new JSONObject();

    public static void onMessage(JSONObject obj, SSSessionAbstract session) {
        switch (obj.getString("type")) {
            case "addListener":
                addListener(obj, session);
                break;
            case "removeListener":
                removeListener(obj, session);
                break;
        }
    }

    public static void addListener(JSONObject obj, SSSessionAbstract session) {
        try {
            String key_usuario = obj.getString("key_usuario");
            String key_device = obj.getString("key_device");

            if (!listener.has(key_device)) {
                listener.put(key_device, new JSONObject());
            }
            if (!listener.getJSONObject(key_device).has(key_usuario)) {
                listener.getJSONObject(key_device).put(key_usuario, new Date().getTime());
            }

            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            e.printStackTrace();
        }
    }
    public static void removeListener(JSONObject obj, SSSessionAbstract session) {
        try {
            String key_usuario = obj.getString("key_usuario");
            String key_device = obj.getString("key_device");

            listener.getJSONObject(key_device).remove(key_usuario);

            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            e.printStackTrace();
        }
    }


    public static void sendMessage(String telefono_, String msg) {
        Thread hilo = new Thread(() -> {
            try {
                SConsole.log("Enviando mensaje por whatsapp al numero .", telefono_);
                String telefono = telefono_.replaceAll("\\+", "").replaceAll(" ", "");
                JSONObject send_ = new JSONObject();
                send_.put("key", SConfig
                        .getJSON("wtsp").getString("key"));
                send_.put("mensaje", msg);
                send_.put("numero", telefono);
                send_ = Wtspp.post(SConfig.getJSON("wtsp").getString("url") + "/send/", send_);
                System.out.println(send_);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        hilo.start();
    }

    public static JSONObject post(String url_, JSONObject data) throws Exception {

        URL url = new URL(url_);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");

        con.setRequestProperty("Content-Type", "application/json");

        con.setUseCaches(false);
        con.setDoOutput(true);

        String jsonInputString = data.toString();

        // Escribir el cuerpo de la petición
        try (OutputStream os = con.getOutputStream()) {
            byte[] input = jsonInputString.getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        JSONObject resp;

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(con.getInputStream(), "utf-8"))) {
            StringBuilder response = new StringBuilder();
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            resp = new JSONObject(response.toString());
        }

        return resp;

    }
}
