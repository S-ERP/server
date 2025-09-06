package Servisofts.Contabilidad;

import org.json.JSONObject;

public class AsientoContableDetalle {

    public static AsientoContableDetalle fromJSON(JSONObject json) {
        AsientoContableDetalle detalle = new AsientoContableDetalle(json.optString("key_cuenta_contable"),
                json.optString("glosa"));
        detalle.setDebe(json.optDouble("debe", 0));
        detalle.setHaber(json.optDouble("haber", 0));
        return detalle;
    }

    public String key_cuenta_contable;
    public String glosa;
    public double debe;
    public double haber;

    public AsientoContableDetalle(String key_cuenta_contable, String glosa) {
        this.glosa = glosa;
        this.key_cuenta_contable = key_cuenta_contable;
    }

    public AsientoContableDetalle setDebe(double debe) {
        this.debe = debe;
        return this;
    }

    public AsientoContableDetalle setHaber(double haber) {
        this.haber = haber;
        return this;
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("key_cuenta_contable", key_cuenta_contable);
        json.put("glosa", glosa);
        json.put("debe", debe);
        json.put("haber", haber);
        return json;
    }

}
