package Component.RedSocial;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONObject;

import Component.Notification;
import Component.usuario;
import Server.SSSAbstract.SSSessionAbstract;
import Servisofts.SConfig;
import Servisofts.SPGConect;

public class Publicacion {
    public static final String COMPONENT = "publicacion";

    public static void onMessage(JSONObject obj, SSSessionAbstract session) {
        switch (obj.getString("type")) {
            case "getAll":
                getAll(obj, session);
                break;
            case "getAllInicio":
                getAllInicio(obj, session);
                break;
            case "getByKey":
                getByKey(obj, session);
                break;
            case "registro":
                registro(obj, session);
                break;
            case "publicar":
                publicar(obj, session);
                break;
            case "editar":
                editar(obj, session);
                break;
            case "topLikes":
                topLikes(obj, session);
                break;
        }
    }

    public static void getAll(JSONObject obj, SSSessionAbstract session) {
        try {
            String consulta = "select get_publicaciones() as json";
            if (obj.has("key_usuario") && !obj.isNull("key_usuario")) {
                consulta = "select get_publicaciones('" + obj.getString("key_usuario") + "') as json";
            }
            if (obj.has("key_usuario") && !obj.isNull("key_usuario") && obj.has("key_perfil")
                    && !obj.isNull("key_perfil")) {
                consulta = "select get_publicaciones('" + obj.getString("key_usuario") + "', '"
                        + obj.getString("key_perfil") + "') as json";
            }
            if (obj.has("pagina") && !obj.isNull("pagina")) {
                consulta = "select get_publicaciones_('" + obj.getInt("pagina") + "') as json";
            }
            if (obj.has("pagina") && !obj.isNull("pagina") && obj.has("limit") && !obj.isNull("limit")) {
                consulta = "select get_publicaciones_(" + obj.getInt("pagina") * obj.getInt("limit") + ", "
                        + obj.getInt("limit") + ") as json";
            }
            if (obj.has("pagina") && !obj.isNull("pagina") && obj.has("limit") && !obj.isNull("limit")
                    && obj.has("key_usuario") && !obj.isNull("key_usuario")) {
                consulta = "select get_publicaciones_inicio(" + obj.getInt("pagina") * obj.getInt("limit") + ", "
                        + obj.getInt("limit") + ", '" + obj.getString("key_usuario") + "') as json";
            }

            if (obj.has("pagina") && !obj.isNull("pagina") && obj.has("limit") && !obj.isNull("limit")
                    && obj.has("key_usuario") && !obj.isNull("key_usuario") && obj.has("key_empresa") && !obj.isNull("key_empresa")) {
                consulta = "select get_publicaciones_inicio(" + obj.getInt("pagina") * obj.getInt("limit") + ", "
                        + obj.getInt("limit") + ", '" + obj.getString("key_usuario") + "', '"+obj.getString("key_empresa")+"') as json";
            }

            JSONObject data = SPGConect.ejecutarConsultaObject(consulta);
            obj.put("data", data);
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getLocalizedMessage());
            e.printStackTrace();
        }
    }

    public static void getAllInicio(JSONObject obj, SSSessionAbstract session) {
        try {
            String key_usuario = obj.getString("key_usuario");
            int limit = obj.getInt("limit");
            int pagina = obj.getInt("pagina");

            String consulta = "select get_publicaciones_inicio(" + limit * pagina + ", " + limit + ",'" + key_usuario
                    + "') as json";

            JSONObject data = SPGConect.ejecutarConsultaObject(consulta);
            obj.put("data", data);
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getLocalizedMessage());
            e.printStackTrace();
        }
    }

    public static void topLikes(JSONObject obj, SSSessionAbstract session) {
        try {
            String consulta = "select top_likes(" + obj.getInt("top") + ") as json";
            if (obj.has("fecha_inicio") && obj.has("fecha_fin")) {
                consulta = "select top_likes(" + obj.getInt("top") + ", '" + obj.getString("fecha_inicio") + "', '"
                        + obj.getString("fecha_fin") + "') as json";
            }
            JSONArray data = SPGConect.ejecutarConsultaArray(consulta);
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
            String consulta = "select get_publicacion('" + obj.getString("key") + "') as json";
            if (obj.has("key_usuario") && !obj.isNull("key_usuario")) {
                consulta = "select get_publicacion('" + obj.getString("key") + "','" + obj.getString("key_usuario")
                        + "') as json";
            }
            JSONObject data = SPGConect.ejecutarConsultaObject(consulta);
            obj.put("data", data);
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getLocalizedMessage());
            e.printStackTrace();
        }
    }

    public static JSONObject getByKey(String key_publicacion) {
        try {
            String consulta = "select get_by_key('" + COMPONENT + "', '" + key_publicacion + "') as json";
            return SPGConect.ejecutarConsultaObject(consulta);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void registro(JSONObject obj, SSSessionAbstract session) {
        try {
            DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS");
            String fecha_on = formatter.format(new Date());
            JSONObject publicacion = obj.getJSONObject("data");

            publicacion.put("key", UUID.randomUUID().toString());
            publicacion.put("estado", 1);
            publicacion.put("fecha_on", fecha_on);
            publicacion.put("key_usuario", obj.getString("key_usuario"));
            publicacion.put("key_empresa", obj.getString("key_empresa"));
            SPGConect.insertArray("publicacion", new JSONArray().put(publicacion));

            JSONObject notificationData = new JSONObject();
            if(obj.has("notification_data")){
                notificationData = obj.getJSONObject("notification_data");
            }

            new Notification().send_urlTypeTags(
                publicacion.getString("key_empresa"),
                publicacion.getString("key_usuario"),
                new JSONObject().put("key_empresa", obj.getString("key_empresa")),
            "publicacion_registro", 
                notificationData.put("publicacion", publicacion));

            obj.put("data", publicacion);
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getLocalizedMessage());
            e.printStackTrace();
        }
    }

    public static void publicar(JSONObject obj, SSSessionAbstract session) {
        try {
            DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS");
            String fecha_on = formatter.format(new Date());
            JSONObject publicacion = obj.getJSONObject("publicacion");

            publicacion.put("key", UUID.randomUUID().toString());
            publicacion.put("estado", 1);
            publicacion.put("fecha_on", fecha_on);
            publicacion.put("key_usuario", obj.getString("key_usuario"));
            SPGConect.insertArray("publicacion", new JSONArray().put(publicacion));

            obj.put("data", publicacion);
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

}
