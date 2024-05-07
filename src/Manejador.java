import Component.*;
import Component.RedSocial.Publicacion;
import Component.RedSocial.PublicacionComentario;
import Component.RedSocial.PublicacionLike;
import Servisofts.SConsole;
import org.json.JSONObject;
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
        }
    }

}
