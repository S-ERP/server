package Component;

import org.json.JSONObject;

import SocketCliente.SocketCliente;

public class Notification extends Thread {
    
    private JSONObject obj;

    public void run(){
        try {
            Thread.sleep(5000);
            SocketCliente.send("notification", obj.toString());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void send(String titulo, String desc, String key_usuario) throws Exception{
        JSONObject not = new JSONObject();
        not.put("descripcion", titulo);
        not.put("observacion", desc);
        JSONObject objNotificacion = new JSONObject();
        objNotificacion.put("component", "notification");
        objNotificacion.put("type", "send");
        objNotificacion.put("data", not);
        objNotificacion.put("key_usuario", key_usuario);
        this.obj = objNotificacion;
        this.start();
    }

    public void send(String titulo, String desc) throws Exception{
        JSONObject not = new JSONObject();
        not.put("descripcion", titulo);
        not.put("observacion", desc);
        JSONObject objNotificacion = new JSONObject();
        objNotificacion.put("component", "notification");
        objNotificacion.put("type", "send");
        objNotificacion.put("data", not);
        this.obj = objNotificacion;
        this.start();
    }

    public void send_url(String titulo, String desc, String url_image) throws Exception{
        JSONObject not = new JSONObject();
        not.put("descripcion", titulo);
        not.put("observacion", desc);
        not.put("url_image", url_image);
        JSONObject objNotificacion = new JSONObject();
        objNotificacion.put("component", "notification");
        objNotificacion.put("type", "send");
        objNotificacion.put("data", not);
        this.obj = objNotificacion;
        this.start();
    }

    public void send_url(String titulo, String desc, String url_image, String deepLink) throws Exception{
        JSONObject not = new JSONObject();
        not.put("descripcion", titulo);
        not.put("observacion", desc);

        JSONObject data = new JSONObject();
        data.put("deepLink", deepLink);
        data.put("url_image", url_image);

        not.put("data", data);


        JSONObject objNotificacion = new JSONObject();
        objNotificacion.put("component", "notification");
        objNotificacion.put("type", "send");
        objNotificacion.put("data", not);
        this.obj = objNotificacion;
        this.start();
    }

    public void send_urlV2(String key_empresa, String key_usuario_emisor, String key_usuario_receptor, String titulo, String desc, String url_image, String deepLink) throws Exception{
        JSONObject not = new JSONObject();
        not.put("descripcion", titulo);
        not.put("observacion", desc);
        not.put("url_image", url_image);

        JSONObject data = new JSONObject();
        data.put("deepLink", deepLink);
        data.put("key_empresa", key_empresa);
        
        not.put("data", data);


        JSONObject objNotificacion = new JSONObject();
        objNotificacion.put("component", "notification");
        objNotificacion.put("type", "sendV2");
        objNotificacion.put("key_usuario_emisor", key_usuario_emisor);
        objNotificacion.put("data", not);
        objNotificacion.put("tags", new JSONObject().put("key_usuario", key_usuario_receptor));
        this.obj = objNotificacion;
        this.start();
    }

    public void send_urlType(String key_empresa, String key_usuario_emisor, String key_usuario_receptor, String tipo, JSONObject data) throws Exception{
        
        JSONObject objNotificacion = new JSONObject();
        objNotificacion.put("component", "notification");
        objNotificacion.put("type", "sendType");
        objNotificacion.put("key_usuario_emisor", key_usuario_emisor);
        objNotificacion.put("key_empresa", key_empresa);
        objNotificacion.put("tipo", tipo);
        objNotificacion.put("data", data);
        objNotificacion.put("tags", new JSONObject().put("key_usuario", key_usuario_receptor));
        this.obj = objNotificacion;
        this.start();
    }

    public void send_urlTypeTags(String key_empresa, String key_usuario_emisor, JSONObject tags, String tipo, JSONObject data) throws Exception{
        
        JSONObject objNotificacion = new JSONObject();
        objNotificacion.put("component", "notification");
        objNotificacion.put("type", "sendType");
        objNotificacion.put("key_usuario_emisor", key_usuario_emisor);
        objNotificacion.put("key_empresa", key_empresa);
        objNotificacion.put("tipo", tipo);
        objNotificacion.put("data", data);
        objNotificacion.put("tags", tags);
        this.obj = objNotificacion;
        this.start();
    }

    public void send(String titulo, String desc, String url_image, String deepLink, String key_usuario) throws Exception{
        JSONObject not = new JSONObject();
        not.put("descripcion", titulo);
        not.put("observacion", desc);

        JSONObject data = new JSONObject();
        data.put("deepLink", deepLink);
        data.put("url_image", url_image);

        not.put("data", data);

        JSONObject objNotificacion = new JSONObject();
        objNotificacion.put("component", "notification");
        objNotificacion.put("type", "send");
        objNotificacion.put("data", not);
        objNotificacion.put("key_usuario", key_usuario);
        this.obj = objNotificacion;
        this.start();
    }

    public void sendTags(String titulo, String desc, String url_image, String deepLink, JSONObject tags) throws Exception{
        JSONObject not = new JSONObject();
        not.put("descripcion", titulo);
        not.put("observacion", desc);
        not.put("deepLink", deepLink);
        not.put("url_image", url_image);

        JSONObject objNotificacion = new JSONObject();
        objNotificacion.put("component", "notification");
        objNotificacion.put("type", "send");
        objNotificacion.put("data", not);
        objNotificacion.put("tags", tags);
        this.obj = objNotificacion;
        this.start();
    }
}
