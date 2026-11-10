package Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.ZoneId;

import org.json.JSONArray;
import org.json.JSONObject;

import Servisofts.SPGConect;
import Servisofts.SUtil;

/**
 * Liquidaciones de Linkser: conciliación de los depósitos de los cobros con tarjeta (pantalla Cybersource → Liquidaciones).
 *
 * Cada venta o cuota pagada con tarjeta deja el dinero en la cuenta por cobrar a Linkser (la del tipo de pago). Linkser
 * deposita neto, ya descontada su comisión. Al registrar un depósito se marcan en el servicio cybersource los cobros
 * (y refunds) que cubre y se genera el asiento:
 *   Debe banco (depositado) + Debe gasto comisión (bruto − depositado) / Haber cuenta por cobrar Linkser (bruto)
 * con bruto = cobros − refunds descontados en ese depósito. Anular una liquidación genera el asiento inverso y los
 * cobros vuelven a quedar pendientes.
 *
 * Los montos van en la moneda base de la empresa (los cobros son en BOB; igual que la tienda web, tipo de cambio 1).
 */
public class LiquidacionLinkser {

    static JSONObject config(String key_empresa) throws Exception {
        JSONObject c = Tienda.consulta("select to_json(c) as json from cybersource_liquidacion_config c where c.key_empresa = ?", key_empresa);
        return c.isEmpty() ? new JSONObject().put("configurada", false) : c.put("configurada", true);
    }

    /** {key_cuenta_linkser, key_cuenta_banco, key_cuenta_comision, porcentaje_comision, tolerancia, cuentas?} */
    static JSONObject guardarConfig(String key_empresa, String key_usuario, JSONObject d) throws Exception {
        String[][] cuentas = { { "key_cuenta_linkser", "la cuenta por cobrar a Linkser" }, { "key_cuenta_banco", "el banco" }, { "key_cuenta_comision", "la cuenta de comisión" } };
        for (String[] c : cuentas) if (d.optString(c[0]).isEmpty()) throw new Exception("Falta " + c[1]);
        if (d.getString("key_cuenta_linkser").equals(d.getString("key_cuenta_banco"))) throw new Exception("El banco no puede ser la misma cuenta por cobrar a Linkser");
        double pct = d.optDouble("porcentaje_comision", 0), tol = d.optDouble("tolerancia", 1);
        if (Double.isNaN(pct) || pct < 0 || pct > 20) throw new Exception("Porcentaje de comisión inválido (0 a 20)");
        if (Double.isNaN(tol) || tol < 0 || tol > 100000) throw new Exception("Tolerancia inválida");
        String desc = d.optJSONObject("cuentas") == null ? null : d.getJSONObject("cuentas").toString();
        Tienda.actualizar("insert into cybersource_liquidacion_config (key_empresa, key_cuenta_linkser, key_cuenta_banco, key_cuenta_comision, porcentaje_comision, tolerancia, cuentas, key_usuario, fecha_on) "
                + "values (?, ?, ?, ?, ?::numeric, ?::numeric, ?::json, ?, now()) on conflict (key_empresa) do update set key_cuenta_linkser = excluded.key_cuenta_linkser, "
                + "key_cuenta_banco = excluded.key_cuenta_banco, key_cuenta_comision = excluded.key_cuenta_comision, porcentaje_comision = excluded.porcentaje_comision, "
                + "tolerancia = excluded.tolerancia, cuentas = excluded.cuentas, key_usuario = excluded.key_usuario, fecha_on = now()",
                key_empresa, d.getString("key_cuenta_linkser"), d.getString("key_cuenta_banco"), d.getString("key_cuenta_comision"),
                String.valueOf(pct), String.valueOf(tol), desc, key_usuario);
        return config(key_empresa);
    }

    /** Cobros y refunds que Linkser todavía no liquidó (y los aprobados sin venta, informativos) con la configuración. */
    static JSONObject pendientes(String key_empresa) throws Exception {
        return servicio(new JSONObject().put("type", "liquidables").put("key_empresa", key_empresa)).put("config", config(key_empresa));
    }

    static JSONArray lista(String key_empresa) throws Exception {
        return arr("select coalesce(json_agg(l order by l.fecha_on desc), '[]'::json)::text as json from "
                + "(select * from cybersource_liquidacion where key_empresa = ? and estado > 0 and state <> 'PROCESANDO' order by fecha_on desc limit 300) l", key_empresa);
    }

    static JSONObject detalle(String key, String key_empresa) throws Exception {
        JSONObject l = buscar(key, key_empresa);
        return l.put("items", servicio(new JSONObject().put("type", "de_liquidacion").put("key_empresa", key_empresa).put("key_liquidacion", key)));
    }

    /**
     * Registra un depósito de Linkser: {fecha_deposito, monto_depositado, referencia?, observacion?, cobros:[key], reembolsos:[key], forzar?}.
     * Si la comisión (bruto − depositado) se aleja de la pactada más que la tolerancia, pide forzar (la pantalla lo confirma).
     */
    static JSONObject registrar(String key_empresa, String key_usuario, JSONObject d) throws Exception {
        JSONObject c = config(key_empresa);
        if (!c.optBoolean("configurada")) throw new Exception("Configure primero las cuentas de la liquidación");
        double depositado = redondear(d.optDouble("monto_depositado", 0));
        if (!(depositado > 0)) throw new Exception("Monto depositado inválido");
        String fecha = d.optString("fecha_deposito", "");
        if (!fecha.matches("\\d{4}-\\d{2}-\\d{2}")) throw new Exception("Fecha del depósito inválida");
        LocalDate hoy = LocalDate.now(ZoneId.of("America/La_Paz"));
        if (LocalDate.parse(fecha).isAfter(hoy)) throw new Exception("La fecha del depósito no puede ser futura");
        String referencia = Tienda.recortar(d.optString("referencia", "").trim(), 120), observacion = Tienda.recortar(d.optString("observacion", "").trim(), 300);

        String key = SUtil.uuid();
        Tienda.actualizar("insert into cybersource_liquidacion (key, key_empresa, key_usuario, state, fecha_deposito, monto_depositado, referencia, observacion, "
                + "key_cuenta_linkser, key_cuenta_banco, key_cuenta_comision) values (?, ?, ?, 'PROCESANDO', ?::date, ?::numeric, ?, ?, ?, ?, ?)",
                key, key_empresa, key_usuario, fecha, String.valueOf(depositado), referencia, observacion,
                c.getString("key_cuenta_linkser"), c.getString("key_cuenta_banco"), c.getString("key_cuenta_comision"));
        boolean marcado = false;
        JSONObject a = null;
        try {
            JSONObject m = servicio(new JSONObject().put("type", "liquidar").put("key_empresa", key_empresa).put("key_liquidacion", key)
                    .put("cobros", d.optJSONArray("cobros") == null ? new JSONArray() : d.getJSONArray("cobros"))
                    .put("reembolsos", d.optJSONArray("reembolsos") == null ? new JSONArray() : d.getJSONArray("reembolsos")));
            marcado = true;
            double bruto = m.getDouble("bruto"), cobrado = m.getDouble("cobrado");
            double comision = redondear(bruto - depositado);
            double esperada = redondear(cobrado * c.getDouble("porcentaje_comision") / 100.0);
            if (comision < 0) throw new Exception("El depósito (" + fmt(depositado) + ") es mayor que lo cobrado (" + fmt(bruto) + "): falta marcar cobros");
            if (Math.abs(comision - esperada) > c.getDouble("tolerancia") + 0.005 && !d.optBoolean("forzar"))
                throw new Exception("La comisión (" + fmt(comision) + ") no coincide con la pactada (" + fmt(esperada) + ")");

            String glosa = "Liquidación Linkser " + fecha + (referencia.isEmpty() ? "" : " · " + referencia) + " · " + m.getInt("cobros") + " cobros";
            JSONArray det = new JSONArray().put(linea(c.getString("key_cuenta_banco"), glosa, depositado, 0));
            if (comision > 0) det.put(linea(c.getString("key_cuenta_comision"), "Comisión Linkser " + fecha, comision, 0));
            det.put(linea(c.getString("key_cuenta_linkser"), glosa, 0, bruto));
            a = asiento(key_empresa, key_usuario, glosa, observacion.isEmpty() ? glosa : observacion, det);

            Tienda.actualizar("update cybersource_liquidacion set state = 'REGISTRADA', cantidad = ?::integer, cantidad_reembolsos = ?::integer, cobrado = ?::numeric, "
                    + "devuelto = ?::numeric, bruto = ?::numeric, comision = ?::numeric, comision_esperada = ?::numeric, key_asiento = ?, codigo_asiento = ? where key = ?",
                    String.valueOf(m.getInt("cobros")), String.valueOf(m.getInt("reembolsos")), String.valueOf(cobrado), String.valueOf(m.getDouble("devuelto")),
                    String.valueOf(bruto), String.valueOf(comision), String.valueOf(esperada), a.optString("key"), a.optString("codigo"), key);
            return buscar(key, key_empresa);
        } catch (Exception e) {
            // Con el asiento ya hecho no se deshace nada: queda en PROCESANDO con los cobros marcados para revisarla a mano.
            if (a != null) throw new Exception("Se generó el asiento " + a.optString("codigo") + " pero no se pudo guardar la liquidación: " + e.getMessage());
            if (marcado) {
                try { servicio(new JSONObject().put("type", "desliquidar").put("key_empresa", key_empresa).put("key_liquidacion", key)); } catch (Exception ignored) {}
            }
            Tienda.actualizar("delete from cybersource_liquidacion where key = ? and state = 'PROCESANDO'", key);
            throw e;
        }
    }

    /** Asiento inverso y los cobros vuelven a quedar pendientes: {key, motivo}. */
    static JSONObject anular(String key, String key_empresa, String key_usuario, String motivo) throws Exception {
        if (motivo == null || motivo.trim().isEmpty()) throw new Exception("Indique el motivo");
        JSONObject l = buscar(key, key_empresa);
        if (!"REGISTRADA".equals(l.optString("state"))) throw new Exception("La liquidación no está registrada");
        if (Tienda.actualizar("update cybersource_liquidacion set state = 'ANULANDO' where key = ? and state = 'REGISTRADA'", key) == 0)
            throw new Exception("La liquidación ya se está anulando");
        try {
            double depositado = l.getDouble("monto_depositado"), comision = l.optDouble("comision", 0), bruto = l.getDouble("bruto");
            String glosa = "Anulación liquidación Linkser " + l.optString("codigo_asiento") + ": " + Tienda.recortar(motivo.trim(), 200);
            JSONArray det = new JSONArray().put(linea(l.getString("key_cuenta_linkser"), glosa, bruto, 0));
            det.put(linea(l.getString("key_cuenta_banco"), glosa, 0, depositado));
            if (comision > 0) det.put(linea(l.getString("key_cuenta_comision"), glosa, 0, comision));
            JSONObject a = asiento(key_empresa, key_usuario, glosa, glosa, det);
            servicio(new JSONObject().put("type", "desliquidar").put("key_empresa", key_empresa).put("key_liquidacion", key));
            Tienda.actualizar("update cybersource_liquidacion set state = 'ANULADA', fecha_anulacion = now(), key_usuario_anulacion = ?, motivo_anulacion = ?, "
                    + "key_asiento_anulacion = ?, codigo_asiento_anulacion = ? where key = ?", key_usuario, Tienda.recortar(motivo.trim(), 300), a.optString("key"), a.optString("codigo"), key);
        } catch (Exception e) {
            Tienda.actualizar("update cybersource_liquidacion set state = 'REGISTRADA' where key = ? and state = 'ANULANDO'", key);
            throw e;
        }
        return buscar(key, key_empresa);
    }

    // ---------- auxiliares ----------

    private static JSONObject asiento(String key_empresa, String key_usuario, String descripcion, String observacion, JSONArray detalle) throws Exception {
        JSONObject data = new JSONObject().put("tipo", "traspaso").put("fecha", LocalDate.now(ZoneId.of("America/La_Paz")).toString())
                .put("descripcion", Tienda.recortar(descripcion, 250)).put("observacion", Tienda.recortar(observacion, 250)).put("detalle", detalle);
        JSONObject r = Tienda.bus("contabilidad", new JSONObject().put("component", "asiento_contable").put("type", "set")
                .put("key_usuario", key_usuario).put("key_empresa", key_empresa).put("data", data), 60000);
        JSONObject a = r.optJSONObject("data");
        if (a == null || a.optString("key").isEmpty()) throw new Exception("Contabilidad no devolvió el asiento");
        return a;
    }

    private static JSONObject linea(String key_cuenta, String glosa, double debe, double haber) {
        JSONObject l = new JSONObject().put("key_cuenta_contable", key_cuenta).put("glosa", Tienda.recortar(glosa, 250));
        if (debe > 0) l.put("debe", redondear(debe));
        if (haber > 0) l.put("haber", redondear(haber));
        return l;
    }

    private static JSONObject servicio(JSONObject req) throws Exception {
        JSONObject r = CyberSource.llamar(req.put("component", "link_pago"));
        if (!"exito".equals(r.optString("estado"))) throw new Exception(r.optString("error", "Error en el servicio de pagos"));
        return r.getJSONObject("data");
    }

    private static JSONObject buscar(String key, String key_empresa) throws Exception {
        JSONObject l = Tienda.consulta("select to_json(l) as json from cybersource_liquidacion l where l.key = ? and l.key_empresa = ?", key == null ? "" : key, key_empresa);
        if (l.isEmpty()) throw new Exception("Liquidación no encontrada");
        return l;
    }

    private static JSONArray arr(String sql, String... params) throws Exception {
        Connection con = SPGConect.pool.getConnection();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setString(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getString("json") != null ? new JSONArray(rs.getString("json")) : new JSONArray();
            }
        } finally {
            SPGConect.pool.releaseConnection(con);
        }
    }

    private static double redondear(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.US, "%.2f", v);
    }
}
