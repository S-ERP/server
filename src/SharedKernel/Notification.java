package SharedKernel;

import org.json.JSONObject;

import SocketCliente.SocketCliente;

public class Notification extends Thread {

    private String key_empresa;
    private String tipo;
    private JSONObject data;
    private String key_usuario_emisor;
    private JSONObject tag;


    public Notification() {
        this.data = new JSONObject();
        this.tag = new JSONObject();
    }

    public Notification setTipo(String tipo) {
        this.tipo = tipo;
        return this;
    }

    public Notification setKey_usuario_emisor(String key_usuario_emisor) {
        this.key_usuario_emisor = key_usuario_emisor;
        return this;
    }

    public Notification setData(JSONObject data) {
        this.data = data;
        return this;
    }

    public Notification setKey_empresa(String key_empresa) {
        this.key_empresa = key_empresa;
        return this;
    }

    public Notification setTag(String tag, Object a) {
        this.tag.put(tag, a);
        return this;
    }

    public JSONObject getJson() {
        JSONObject json = new JSONObject();
        json.put("component", "notification");
        json.put("type", "sendType");
        json.put("key_usuario_emisor", key_usuario_emisor);
        if (key_empresa != null && !key_empresa.isEmpty())
            json.put("key_empresa", key_empresa);

        json.put("tipo", tipo);
        json.put("data", data);
        json.put("tags", tag);
        return json;
    }

    public void send() {
        this.start();
    }

    public void run() {
        try {
            Thread.sleep(5000);
            SocketCliente.send("notification", getJson().toString());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}
