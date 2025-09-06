package _component;

import org.json.JSONObject;
import Server.SSSAbstract.SSSessionAbstract;
import Servisofts.SConsole;
import Servisofts.Servisofts;
import SocketCliente.SocketCliente;

public class _Manejador {

    public static JSONObject factory(JSONObject data, SSSessionAbstract sesion) {
        return new _Manejador(data, sesion).manejar();
    }

    JSONObject data;
    SSSessionAbstract sesion;

    public _Manejador(JSONObject data, SSSessionAbstract sesion) {
        this.data = data;
        this.sesion = sesion;
        // this.data = _Manejador.factory(this.data, this.sesion);
    }

    public JSONObject manejar() {
        try {
            if (!data.isNull("component")) {

                try {
                    switch (data.getString("component")) {
                        case "servicio":
                            new _servicio(data, sesion);
                            break;
                        case "usuario":
                            new _Usuario(data, sesion);
                            break;
                        case "test":
                            new _Test(data, sesion);
                            break;
                    }
                } catch (Exception e) {
                    SConsole.error("Error en el componente: " + data.getString("component"));
                }

                if (Servisofts.Manejador != null) {
                    Servisofts.Manejador.apply(data, sesion);
                }
                if (data.has("service")) {
                    String service = data.getString("service");
                    data.remove("service");
                    // Cambie el send por sendSinc 20/jul/2022 2
                    int timeout = 20000;
                    if (data.has("timeOut")) {
                        timeout = data.getInt("timeOut");
                    }
                    data = SocketCliente.sendSinc(service, data, timeout);
                    if (data.has("servicio")) {
                        data.remove("servicio");
                    }
                    if (data.has("id")) {
                        data.remove("id");
                    }
                    if (data.has("info")) {
                        data.remove("info");
                    }

                }
            } else {
                data.put("error", "No existe el componente");
            }
        } catch (Exception e) {
            SConsole.error("Error en el manejador de componentes: " + e.getMessage());
            e.printStackTrace();
        }
        return data;
    }

}