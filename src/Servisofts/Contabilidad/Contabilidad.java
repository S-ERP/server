package Servisofts.Contabilidad;

import org.json.JSONObject;

import SocketCliente.SocketCliente;

public class Contabilidad {
    public static JSONObject getAjusteEmpresa(String key_empresa, String key_ajuste) {
        JSONObject send = new JSONObject();
        send.put("component", "ajuste_empresa");
        send.put("type", "getByKeyAjuste");
        send.put("key_empresa", key_empresa);
        send.put("key_ajuste", key_ajuste);
        JSONObject resp = SocketCliente.sendSinc("contabilidad", send);
        return resp.getJSONObject("data");
    }

    public static JSONObject getEnviroment(String key_empresa, String enviroment) {
        JSONObject send = new JSONObject();
        send.put("component", "enviroment");
        send.put("type", "getEnviroment");
        send.put("key_empresa", key_empresa);
        send.put("enviroment", enviroment);
        JSONObject resp = SocketCliente.sendSinc("contabilidad", send);
        return resp.getJSONObject("data");
    }
}
