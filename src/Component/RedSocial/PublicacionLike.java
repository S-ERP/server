package Component.RedSocial;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONObject;

import Component.Notification;
import Component.usuario;
import Server.SSSAbstract.SSServerAbstract;
import Server.SSSAbstract.SSSessionAbstract;
import Servisofts.SConfig;
import Servisofts.SPGConect;
import SocketCliente.SocketCliente;

public class PublicacionLike {
    public static final String COMPONENT = "publicacion_like";

    public static void onMessage(JSONObject obj, SSSessionAbstract session) {
        switch (obj.getString("type")) {
            case "getAll":
                getAll(obj, session);
                break;
            case "getByKey":
                getByKey(obj, session);
                break;
            case "registro":
                registro(obj, session);
                break;
            case "editar":
                editar(obj, session);
                break;
            case "dislike":
                dislike(obj, session);
                break;
        }
    }

    public static void getAll(JSONObject obj, SSSessionAbstract session) {
        try {
            String consulta = "select get_all('" + COMPONENT + "','key_publicacion','" + obj.getString("key_publicacion") + "') as json";
            JSONObject data = SPGConect.ejecutarConsultaObject(consulta);
            obj.put("data", data);
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getLocalizedMessage());
            e.printStackTrace();
        }
    }

    public static void getByKey(JSONObject obj, SSSessionAbstract session) {
        try {
            String consulta = "select get_by_key('" + COMPONENT + "', '"+obj.getString("key")+"') as json";
            JSONObject data = SPGConect.ejecutarConsultaObject(consulta);
            obj.put("data", data);
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getLocalizedMessage());
            e.printStackTrace();
        }
    }

    public static JSONObject getLike(String key_publicacion, String key_usuario) throws Exception{
        String consulta = "select get_by('publicacion_like', 'key_usuario', '"+key_usuario+"', 'key_publicacion', '"+key_publicacion+"') as json";
        return SPGConect.ejecutarConsultaObject(consulta);
    }

    public static void registro(JSONObject obj, SSSessionAbstract session) {
        try {

            JSONObject like = getLike(obj.getString("key_publicacion"), obj.getString("key_usuario"));
            if(like != null && !like.isEmpty()){
                obj.put("estado", "error");
                obj.put("error", "Ya diste like");
                return;
            }

            DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS");
            String fecha_on = formatter.format(new Date());
            JSONObject data = new JSONObject();
            data.put("key", UUID.randomUUID().toString());
            data.put("estado", 1);
            data.put("fecha_on", fecha_on);
            data.put("key_usuario", obj.getString("key_usuario"));
            data.put("key_publicacion", obj.getString("key_publicacion"));
            SPGConect.insertArray(COMPONENT, new JSONArray().put(data));

            JSONObject publicacion = Publicacion.getByKey(obj.getString("key_publicacion"));
            publicacion = publicacion.getJSONObject(JSONObject.getNames(publicacion)[0]);

            JSONObject usuario_ = usuario.getUsuario(obj.getString("key_usuario"));

            new Notification().send("Serp", ""+usuario_.getString("Nombres")+" "+usuario_.getString("Apellidos")+" le dió un like a tu publicación.", "https://tllebo.servisofts.com/images/usuario/"+obj.getString("key_usuario"),SConfig.getJSON().getString("deeplink")+"/publicacion/post?pk="+publicacion.getString("key"),publicacion.getString("key_usuario"));

            obj.put("data", data);
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getLocalizedMessage());
            e.printStackTrace();
        }
    }

    public static void editar(JSONObject obj, SSSessionAbstract session) {
        try {
            JSONObject data = obj.getJSONObject("data");
            SPGConect.editObject(COMPONENT, data);
            obj.put("data", data);
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getLocalizedMessage());
            e.printStackTrace();
        }
    }

    public static void dislike(JSONObject obj, SSSessionAbstract session) {
        try {
            String consulta = "select dislike('"+obj.getString("key_publicacion")+"', '"+obj.getString("key_usuario")+"') as json";
            SPGConect.ejecutar(consulta);
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getLocalizedMessage());
            e.printStackTrace();
        }
    }


}
