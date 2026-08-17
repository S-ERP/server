import Component.*;
import Component.RedSocial.Publicacion;
import Component.RedSocial.PublicacionComentario;
import Component.RedSocial.PublicacionLike;
import Controllers.Sapi;
import Servisofts.SConsole;

import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;

import Server.SSSAbstract.SSServerAbstract;
import Server.SSSAbstract.SSSessionAbstract;

public class Manejador {
    public static void onMessage(JSONObject obj, SSSessionAbstract session) {
        if (session != null) {
            SConsole.log(session.getIdSession(), "\t|\t", obj.getString("component"), obj.getString("type"));
        } else {
            SConsole.log("http-server", "-->", obj.getString("component"), obj.getString("type"));
        }
        if (obj.isNull("component")) {
            return;
        }
        switch (obj.getString("component")) {
            case Publicacion.COMPONENT: Publicacion.onMessage(obj, session); break;
            case PublicacionComentario.COMPONENT: PublicacionComentario.onMessage(obj, session); break;
            case PublicacionLike.COMPONENT: PublicacionLike.onMessage(obj, session); break;
            case usuario.COMPONENT: usuario.onMessage(obj, session); break;
            case Dato.COMPONENT: Dato.onMessage(obj, session); break;
            case RolDato.COMPONENT: RolDato.onMessage(obj, session); break;
            case UsuarioDato.COMPONENT: UsuarioDato.onMessage(obj, session); break;
            case Banco.COMPONENT: Banco.onMessage(obj, session); break;
            case BancoCuenta.COMPONENT: BancoCuenta.onMessage(obj, session); break;
            case CuentaMovimiento.COMPONENT: CuentaMovimiento.onMessage(obj, session); break;
            case Reporte.COMPONENT: Reporte.onMessage(obj, session); break;
            case Tarea.COMPONENT: Tarea.onMessage(obj, session); break;
            case TareaUsuario.COMPONENT: TareaUsuario.onMessage(obj, session); break;
            case Label.COMPONENT: Label.onMessage(obj, session); break;
            case TareaLabel.COMPONENT: TareaLabel.onMessage(obj, session); break;
            case TareaComentario.COMPONENT: TareaComentario.onMessage(obj, session); break;
            case Nota.COMPONENT: Nota.onMessage(obj, session); break;
            case NotaUsuario.COMPONENT: NotaUsuario.onMessage(obj, session); break;
            case Invitacion.COMPONENT: Invitacion.onMessage(obj, session); break;
            case SolicitudQr.COMPONENT: SolicitudQr.onMessage(obj, session); break;
            case Sapi.COMPONENT: Sapi.onMessage(obj, session); break;
            case Billetera.COMPONENT: Billetera.onMessage(obj, session); break;
            case Mesh.COMPONENT: Mesh.onMessage(obj, session); break;
            case Camera.COMPONENT: Camera.onMessage(obj, session); break;
            case Scene.COMPONENT: Scene.onMessage(obj, session); break;
            case Avatar.COMPONENT: Avatar.onMessage(obj, session); break;
            case SceneMesh.COMPONENT: SceneMesh.onMessage(obj, session); break;
            case Terreno.COMPONENT: Terreno.onMessage(obj, session); break;
            case Wtspp.COMPONENT: Wtspp.onMessage(obj, session); break;
            case qr_reader.COMPONENT: qr_reader.onMessage(obj, session); break;
            case Pizarra.COMPONENT: Pizarra.onMessage(obj, session); break;
            // case Habilidad.COMPONENT: Habilidad.onMessage(obj, session); break;
            // case HabilidadUsuario.COMPONENT: HabilidadUsuario.onMessage(obj, session); break;
            case "listener": listener(obj, session); break;
        }

        try {
            applyListeners(obj, session);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static void listener(JSONObject obj, SSSessionAbstract session) {
        try {
            if (!obj.has("type")) {
                return;
            }
            if (obj.getString("type").equals("save")) {
                if (!obj.has("listeners")) {
                    return;
                }
                session.listeners = obj.getJSONArray("listeners");
                obj.put("estado", "exito");

                return;
            }
            // if (obj.getString("type").equals("remove")) {
            // session.listeners = new JSONObject();
            // return;
            // }
            // applyListeners(obj, session);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean compare(JSONObject a, JSONObject b) {
        return compare(a, b, 0);
    }

    public static boolean compare(JSONObject a, JSONObject b, int lvl) {
        // Comparo que el que todo lo que hay en el a exista en el b;
        String[] names = JSONObject.getNames(a);
        boolean match = true;
        for (String name : names) {
            if (!b.has(name)) {
                match = false;
                break;
            }
            if (name.equals("key") && lvl == 0) {
                continue;
            }

            if (b.get(name).getClass().getName().equals("org.json.JSONObject")) {
                if (!compare(a.optJSONObject(name), b.optJSONObject(name), lvl + 1)) {
                    return false;
                }
            } else {
                if (!b.optString(name).equals(a.optString(name))) {
                    return false;
                }
            }

        }
        return true;
    }

    public static void applyListeners(JSONObject obj, SSSessionAbstract session) throws Exception {

        HashMap<String, SSServerAbstract> SERVIDORES = SSServerAbstract.getServidores();

        JSONObject listen = new JSONObject();
        for (Map.Entry me2 : SERVIDORES.entrySet()) {
            SSServerAbstract server = (SSServerAbstract) me2.getValue();
            HashMap<String, SSSessionAbstract> sessiones = server.getSessiones();

            for (Map.Entry me : sessiones.entrySet()) {
                SSSessionAbstract session_ = (SSSessionAbstract) me.getValue();
                if (session_ == session) {
                    continue;
                }
                if (session == null) {
                    continue;
                }
                if (session_.listeners == null) {
                    continue;
                }
                for (int i = 0; i < session_.listeners.length(); i++) {
                    listen = session_.listeners.getJSONObject(i);
                    boolean match = compare(listen, obj);
                    // quiero recorrer todas las keys q no son component ni type

                    if (!match) {
                        continue;
                    }

                    session_.send(obj.toString());
                    break;
                    // System.out.println("Entro aca " + session_.listeners.getString(i));
                    // if (session_.listeners.getString(i).equals(obj.getString("component"))) {
                    // session_.send(obj);
                    // }
                }
            }
        }

        // session.listeners = obj.getJSONArray("listeners");
    }

}
