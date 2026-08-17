package Servisofts.Contabilidad;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

import Servisofts.SUtil;

public class AsientoContable {

    public static AsientoContable fromJSON(JSONObject json) {
        AsientoContable asiento = new AsientoContable(AsientoContableTipo.valueOf(json.getString("tipo")));
        asiento.key = json.optString("key");
        asiento.fecha = json.optString("fecha");
        asiento.codigo = json.optString("codigo");
        asiento.descripcion = json.optString("descripcion");
        asiento.observacion = json.optString("observacion");
        asiento.key_gestion = json.optString("key_gestion");
        asiento.key_empresa = json.optString("key_empresa");
        asiento.key_usuario = json.optString("key_usuario");

        JSONArray detallesJson = json.optJSONArray("detalle");
        for (int i = 0; i < detallesJson.length(); i++) {
            JSONObject detalleJson = detallesJson.getJSONObject(i);
            AsientoContableDetalle detalle = AsientoContableDetalle.fromJSON(detalleJson);
            asiento.setDetalle(detalle);
        }

        return asiento;
    }

    public AsientoContableTipo tipo;
    public String key;
    public String fecha;
    public String codigo;
    public String descripcion;
    public String observacion;
    public String key_gestion;
    public String key_empresa;
    public String key_usuario;

    ArrayList<AsientoContableDetalle> detalle;

    public AsientoContable(AsientoContableTipo tipo) {
        this.tipo = tipo;
        this.fecha = SUtil.now();
        this.detalle = new ArrayList<>();
    }

    public void setDetalle(AsientoContableDetalle detalle) {
        this.detalle.add(detalle);
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("key", key);
        json.put("tipo", tipo.toString());
        json.put("fecha", fecha);
        json.put("codigo", codigo);
        json.put("descripcion", descripcion);
        json.put("observacion", observacion);
        json.put("key_gestion", key_gestion);
        json.put("key_empresa", key_empresa);
        json.put("key_usuario", key_usuario);

        JSONArray detallesJson = new JSONArray();
        for (AsientoContableDetalle det : detalle) {
            detallesJson.put(det.toJSON());
        }
        json.put("detalle", detallesJson);

        return json;
    }

    public JSONObject enviar() throws Exception {
        // TODO Validaciones

        JSONObject request = new JSONObject();
        request.put("component", "asiento_contable");
        request.put("type", "set");
        request.put("key_empresa", this.key_empresa);
        request.put("key_usuario", this.key_usuario);
        request.put("data", this.toJSON());
        JSONObject response = SocketCliente.SocketCliente.sendSinc("contabilidad", request);
        if (!response.optString("estado").equals("exito")) {
            throw new Exception(response.optString("error"));
        }
        this.key = response.getJSONObject("data").optString("key");
        this.codigo = response.getJSONObject("data").optString("codigo");
        return response;
    }

}
