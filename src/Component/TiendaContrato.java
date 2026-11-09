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
import SocketCliente.SocketCliente;

/**
 * Planes con débito automático de la tienda web (Fidelidad / Total): contrato de N meses.
 *
 * Al aprobarse el primer pago (link con guardar_tarjeta: Cybersource guarda la tarjeta):
 *   venta a crédito por N × cuota → contrato → N cuotas en compra_venta (una por mes, mismo día del primer pago)
 *   → cuota 1 pagada en caja con ese pago → suscripción del primer mes.
 * Cada paso deja registro y se puede repetir: si algo falla, el siguiente intento sigue donde quedó (nunca dos ventas).
 *
 * Cada mes (Tasks.TiendaCobrosTask) se cobra la cuota que vence con la tarjeta guardada (cobro automático MIT). Si se
 * aprueba: pago de la cuota en la caja automática + un mes más de suscripción. Si se rechaza y es reintentable
 * (categoryCode distinto de 01) se reintenta según la tienda (reintentos 0..3, cada reintento_horas: 24/48/72);
 * después la cuota queda en MORA y el contrato MOROSO.
 * El personal puede generar un link para que el cliente pague esa cuota (y con eso cambia la tarjeta guardada).
 *
 * Tablas: tienda_contrato, tienda_cobro (sql/tienda_recurrente.sql).
 */
public class TiendaContrato {
    static final int MAX_REINTENTOS = 3; // Linkser: máximo 3 reintentos de un cobro rechazado
    static final ZoneId BO = ZoneId.of("America/La_Paz");

    static LocalDate hoy() {
        return LocalDate.now(BO);
    }

    // ===================== Alta del contrato (desde Tienda.procesar) =====================

    /** Registra (o completa) el contrato de la orden pagada. Devuelve key_compra_venta cuando todo quedó listo. */
    static String registrar(JSONObject o, JSONObject link) throws Exception {
        JSONObject t = Tienda.consulta("select to_json(t) as json from tienda t where t.key = ?", o.getString("key_tienda"));
        String key_empresa = t.getString("key_empresa"), key_usuario = t.getString("key_usuario_venta");
        if (t.optString("key_empresa_tipo_pago_credito").isEmpty()) throw new Exception("La tienda no tiene configurado el tipo de pago a crédito");
        JSONObject p = Tienda.consulta("select to_json(p) as json from tienda_producto p where p.key = ?", o.getString("key_tienda_producto"));
        int n = Math.max(2, p.optInt("cuotas", 12));
        double cuota = o.getDouble("monto");
        JSONObject c = o.getJSONObject("cliente");
        JSONObject m = Tienda.modelo(o.getString("key_modelo"));
        if (m == null) throw new Exception("El producto de la orden ya no existe");

        String key_cliente = Tienda.clienteDeOrden(o, key_empresa, key_usuario);
        String key_caja = cajaAutomatica(t);

        // 1) Venta a crédito por el total del contrato, con la suscripción del primer mes.
        JSONObject k = Tienda.consulta("select to_json(k) as json from tienda_contrato k where k.key_orden = ?", o.getString("key"));
        String venta = o.optString("key_compra_venta", "");
        LocalDate primerCobro = hoy();
        String[] mes1 = null;
        if (venta.isEmpty()) {
            mes1 = proximoMes(key_empresa, key_usuario, key_cliente, m);
            JSONObject suscriptor = new JSONObject().put("key_cliente", key_cliente).put("fecha_inicio", mes1[0]).put("fecha_fin", mes1[1])
                    .put("key_producto", "").put("key_sucursal", o.getString("key_sucursal"));
            double total = Math.round(cuota * n * 100.0) / 100.0;
            JSONObject detalle = new JSONObject()
                    .put("cantidad", 1).put("precio_unitario", total).put("precio_unitario_base", total).put("detalle", "")
                    .put("descripcion", m.optString("descripcion") + " (" + n + " cuotas de " + monto(cuota) + ")")
                    .put("data", new JSONObject().put("tipo_producto", "servicio").put("cupos_disponibles", m.optInt("cantidad_suscriptores", 1)).put("cupos_suscritos", 1))
                    .put("key_modelo", m.getString("key")).put("costos", new JSONArray()).put("suscriptores", new JSONArray().put(suscriptor));
            String etpCredito = t.getString("key_empresa_tipo_pago_credito");
            JSONObject tipos_pago = new JSONObject().put(etpCredito, new JSONObject()
                    .put("monto_nacional", total).put("monto_extranjera", total).put("tipo_pago", tipoPagoDe(etpCredito, key_empresa))
                    .put("referencia", "Contrato tienda web: " + n + " cuotas mensuales con débito automático").put("descripcion", "Crédito"));
            JSONObject data = new JSONObject()
                    .put("descripcion", "").put("observacion", "Tienda web · " + o.optString("descripcion") + " · " + n + " cuotas · orden " + o.getString("key").substring(0, 8))
                    .put("facturar", false).put("factura", JSONObject.NULL).put("tipo_pago", "credito").put("facturar_luego", false)
                    .put("cliente", new JSONObject().put("key", key_cliente).put("nit", c.optString("ci"))
                            .put("razon_social", (c.optString("nombres") + " " + c.optString("apellidos")).trim()))
                    .put("descuentos", new JSONArray()).put("key_cliente", key_cliente).put("key_usuario", key_usuario)
                    .put("key_caja", key_caja).put("key_almacen", t.getString("key_almacen")).put("key_moneda", t.getString("key_moneda"))
                    .put("detalle", new JSONArray().put(detalle)).put("suscripciones", new JSONArray().put(new JSONObject(suscriptor.toString())))
                    .put("tipos_pago", tipos_pago);
            JSONObject r = SocketCliente.sendSinc("caja", new JSONObject().put("component", "caja_detalle").put("type", "venta").put("estado", "cargando").put("data", data), 60000);
            if (r == null) throw new Exception("Sin respuesta del servicio caja");
            if ("error".equals(r.optString("estado"))) throw new Exception("caja: " + r.optString("error", "error al registrar la venta"));
            venta = r.optJSONObject("data") == null ? "" : r.getJSONObject("data").optString("key_compra_venta", "");
            if (venta.isEmpty()) throw new Exception("La caja no devolvió la venta");
            Tienda.actualizar("update tienda_orden set key_compra_venta = ? where key = ?", venta, o.getString("key"));
        }

        // 2) Contrato.
        if (k.isEmpty()) {
            String key = SUtil.uuid();
            JSONObject sub = suscripcionDeVenta(key_empresa, key_usuario, key_cliente, m.getString("key"), mes1 == null ? null : mes1[0]);
            Tienda.actualizar("insert into tienda_contrato (key, key_usuario, key_tienda, key_empresa, key_orden, key_tienda_producto, key_modelo, key_producto, "
                    + "key_sucursal, key_cliente, cliente, key_compra_venta, descripcion, monto_cuota, cuotas, dia_cobro, fecha_inicio, key_tarjeta, tarjeta, tarjeta_vence) "
                    + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::json, ?, ?, ?::numeric, ?::integer, ?::integer, ?::date, ?, ?, ?)",
                    key, key_usuario, t.getString("key"), key_empresa, o.getString("key"), o.getString("key_tienda_producto"), m.getString("key"),
                    sub == null ? null : sub.optString("key_producto", null), o.getString("key_sucursal"), key_cliente, c.toString(), venta,
                    o.optString("descripcion"), monto(cuota), String.valueOf(n), String.valueOf(primerCobro.getDayOfMonth()), primerCobro.toString(),
                    link.getString("key"), link.optString("tarjeta", null), link.optString("tarjeta_vence", null));
            k = Tienda.consulta("select to_json(k) as json from tienda_contrato k where k.key = ?", key);
            // Cobros: el 1 es el de la orden; los demás, el mismo día de cada mes siguiente.
            LocalDate base = LocalDate.parse(k.getString("fecha_inicio").substring(0, 10));
            for (int i = 1; i <= n; i++)
                Tienda.actualizar("insert into tienda_cobro (key, key_contrato, key_empresa, numero, fecha_vencimiento, monto, key_suscripcion) values (?, ?, ?, ?::integer, ?::date, ?::numeric, ?)",
                        SUtil.uuid(), key, key_empresa, String.valueOf(i), base.plusMonths(i - 1).toString(), monto(cuota),
                        i == 1 && sub != null ? sub.optString("key", null) : null);
            Tienda.actualizar("update tienda_orden set key_contrato = ? where key = ?", key, o.getString("key"));
        }

        // 3) Cuotas en compra_venta (reemplaza la cuota única de la venta a crédito por las N mensuales).
        JSONArray cobros = cobrosDe(k.getString("key"));
        JSONArray cuotas = cuotasDe(venta, key_empresa, key_usuario);
        if (cuotas.length() != n) {
            if (cuotas.length() != 1) throw new Exception("La venta tiene " + cuotas.length() + " cuotas: revisarla a mano");
            JSONObject orig = cuotas.getJSONObject(0);
            double ratio = orig.optDouble("monto", 0) > 0 ? orig.optDouble("monto_base", orig.optDouble("monto")) / orig.getDouble("monto") : 1;
            JSONArray nuevas = new JSONArray();
            for (int i = 0; i < cobros.length(); i++) {
                JSONObject cb = cobros.getJSONObject(i);
                double mto = cb.getDouble("monto");
                nuevas.put(new JSONObject().put("codigo", String.valueOf(cb.getInt("numero"))).put("fecha", cb.getString("fecha_vencimiento").substring(0, 10))
                        .put("monto", mto).put("monto_base", Math.round(mto * ratio * 100.0) / 100.0).put("capital", mto).put("interes", 0)
                        .put("key_moneda", orig.optString("key_moneda")).put("key_empresa_tipo_pago", orig.optString("key_empresa_tipo_pago"))
                        .put("key_cuenta_contable", orig.optString("key_cuenta_contable"))
                        .put("descripcion", "Cuota " + cb.getInt("numero") + "/" + n + " · " + o.optString("descripcion")));
            }
            Tienda.bus("compra_venta", new JSONObject().put("component", "cuota").put("type", "registroAll").put("key_compra_venta", venta)
                    .put("tipo_pago", "credito").put("periodicidad_medida", "mes").put("periodicidad_valor", 1).put("porcentaje_interes", 0)
                    .put("key_usuario", key_usuario).put("key_empresa", key_empresa).put("data", nuevas), 60000);
            cuotas = cuotasDe(venta, key_empresa, key_usuario);
            if (cuotas.length() != n) throw new Exception("compra_venta no registró las " + n + " cuotas");
        }
        for (int i = 0; i < cobros.length(); i++) {
            JSONObject cb = cobros.getJSONObject(i);
            if (!cb.optString("key_cuota", "").isEmpty()) continue;
            JSONObject cu = cuotaNumero(cuotas, cb.getInt("numero"));
            if (cu != null) Tienda.actualizar("update tienda_cobro set key_cuota = ? where key = ?", cu.getString("key"), cb.getString("key"));
        }

        // 4) Cuota 1 pagada con el pago de la orden.
        JSONObject c1 = cobroNumero(cobrosDe(k.getString("key")), 1);
        if (!"PAGADO".equals(c1.optString("state"))) {
            Tienda.actualizar("update tienda_cobro set state = 'COBRADO', key_transaccion = ?, intentos = 1 where key = ? and state <> 'PAGADO'", link.getString("key"), c1.getString("key"));
            registrarPago(c1.getString("key"), link);
        }
        return venta;
    }

    // ===================== Cobro mensual =====================

    /** Cobros a procesar: vencidos y en orden (no se cobra una cuota si hay una anterior sin pagar), cobrados sin registrar, o con link enviado. */
    public static JSONArray pendientes() throws Exception {
        JSONArray out = new JSONArray();
        Connection con = SPGConect.pool.getConnection();
        try (PreparedStatement ps = con.prepareStatement("select c.key from tienda_cobro c join tienda_contrato k on k.key = c.key_contrato "
                + "where c.estado > 0 and k.estado > 0 and k.state in ('ACTIVO','MOROSO') and ("
                + "  c.state = 'COBRADO' "
                + "  or (c.key_link is not null and c.state in ('PENDIENTE','REINTENTO','MORA')) "
                + "  or (c.state in ('PENDIENTE','REINTENTO') and c.fecha_vencimiento <= (now() at time zone 'America/La_Paz')::date "
                + "      and (c.proximo_intento is null or c.proximo_intento <= now()) "
                + "      and not exists (select 1 from tienda_cobro a where a.key_contrato = c.key_contrato and a.estado > 0 and a.numero < c.numero "
                + "                      and a.state in ('PENDIENTE','REINTENTO','COBRADO','MORA','INCIERTO')))"
                + ") order by c.fecha_vencimiento, c.numero limit 200")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.put(rs.getString("key"));
            }
        } finally {
            SPGConect.pool.releaseConnection(con);
        }
        return out;
    }

    /** ¿El cobro solo necesita registrarse (ya cobrado o con link enviado), sin cobrar con la tarjeta? */
    public static boolean soloRegistrar(String key_cobro) throws Exception {
        JSONObject cb = cobro(key_cobro);
        return cb.optString("state").equals("COBRADO") || !cb.optString("key_link", "").isEmpty();
    }

    /**
     * Procesa un cobro: si el link manual ya se pagó o el cobro está COBRADO, lo registra; si vence (o manual = el
     * personal lo pide), cobra con la tarjeta guardada. Con lock por cobro: nunca dos cobros del mismo intento.
     */
    public static JSONObject procesar(String key_cobro, boolean manual) throws Exception {
        synchronized (("tienda_cobro:" + key_cobro).intern()) {
            JSONObject cb = cobro(key_cobro);
            String state = cb.optString("state");
            if (state.equals("PAGADO") || state.equals("ANULADO")) return cb;
            JSONObject k = contrato(cb.getString("key_contrato"));

            // Link manual (cuota en mora / cambio de tarjeta) pagado por el cliente.
            if (!cb.optString("key_link", "").isEmpty()) {
                JSONObject l = linkPago(cb.getString("key_link"), cb.getString("key_empresa"));
                if (l != null && "AUTHORIZED".equals(l.optString("state"))) {
                    Tienda.actualizar("update tienda_cobro set state = 'COBRADO', key_transaccion = ? where key = ? and state <> 'PAGADO'", l.getString("key"), key_cobro);
                    if (l.optBoolean("tarjeta_guardada"))
                        Tienda.actualizar("update tienda_contrato set key_tarjeta = ?, tarjeta = ?, tarjeta_vence = ? where key = ?",
                                l.getString("key"), l.optString("tarjeta", null), l.optString("tarjeta_vence", null), k.getString("key"));
                    return registrarPago(key_cobro, l);
                }
            }
            if (state.equals("COBRADO")) return registrarPago(key_cobro, linkOMit(cb));
            // Con un link enviado al cliente la tarea no cobra la tarjeta (evita cobrar dos veces); el personal sí puede.
            if (!manual && !cb.optString("key_link", "").isEmpty()) return cb;
            if (state.equals("INCIERTO")) throw new Exception("El último cobro quedó sin respuesta de Cybersource: revisarlo en el Business Center antes de volver a cobrar");
            if (!k.optString("state").equals("ACTIVO") && !k.optString("state").equals("MOROSO")) throw new Exception("El contrato está " + k.optString("state"));
            if (LocalDate.parse(cb.getString("fecha_vencimiento").substring(0, 10)).isAfter(hoy())) throw new Exception("La cuota todavía no vence");
            if (!manual && !(state.equals("PENDIENTE") || state.equals("REINTENTO"))) return cb;
            if (k.optString("key_tarjeta", "").isEmpty()) throw new Exception("El contrato no tiene tarjeta guardada");

            int intento = cb.optInt("intentos", 0) + 1;
            JSONObject r = CyberSource.llamar(new JSONObject().put("component", "link_pago").put("type", "cobrar_token")
                    .put("key_empresa", k.getString("key_empresa")).put("key_tarjeta", k.getString("key_tarjeta"))
                    .put("monto", cb.getDouble("monto")).put("key_solicitud", "cobro:" + key_cobro + ":" + intento)
                    .put("descripcion", k.optString("descripcion") + " · cuota " + cb.getInt("numero") + "/" + k.getInt("cuotas")));
            if (!"exito".equals(r.optString("estado"))) {
                // El servicio no llegó a cobrar (error antes de llamar a Cybersource): se reintenta en una hora sin contar intento.
                Tienda.actualizar("update tienda_cobro set error = ?, proximo_intento = now() + interval '1 hour' where key = ?",
                        Tienda.recortar(r.optString("error", "error del servicio de pagos"), 250), key_cobro);
                throw new Exception(r.optString("error", "No se pudo cobrar"));
            }
            JSONObject d = r.getJSONObject("data");
            String st = d.optString("state");
            if (st.equals("AUTHORIZED")) {
                Tienda.actualizar("update tienda_cobro set state = 'COBRADO', key_transaccion = ?, intentos = ?::integer, error = null, categoria = null where key = ?",
                        d.getString("key"), String.valueOf(intento), key_cobro);
                return registrarPago(key_cobro, d);
            }
            if (st.equals("INCIERTO")) {
                Tienda.actualizar("update tienda_cobro set state = 'INCIERTO', key_transaccion = ?, intentos = ?::integer, error = ? where key = ?",
                        d.getString("key"), String.valueOf(intento), Tienda.recortar(d.optString("mensaje"), 250), key_cobro);
                return cobro(key_cobro);
            }
            // Reintentos según la configuración de la tienda (cada 24/48/72 h; máx 3, regla de Linkser).
            JSONObject tc = Tienda.consulta("select to_json(t) as json from tienda t where t.key = ?", k.getString("key_tienda"));
            int horas = Math.max(24, Math.min(168, tc.optInt("reintento_horas", 24)));
            int reintentos = Math.max(0, Math.min(MAX_REINTENTOS, tc.optInt("reintentos", MAX_REINTENTOS)));
            boolean reintentar = d.optBoolean("reintentable") && intento <= reintentos;
            Tienda.actualizar("update tienda_cobro set state = ?, key_transaccion = ?, intentos = ?::integer, categoria = ?, error = ?, "
                    + "proximo_intento = case when ? = 'REINTENTO' then now() + (? || ' hours')::interval else null end where key = ?",
                    reintentar ? "REINTENTO" : "MORA", d.optString("key"), String.valueOf(intento), d.optString("categoria", null),
                    Tienda.recortar("Rechazado: " + d.optString("mensaje", d.optString("status")), 250), reintentar ? "REINTENTO" : "MORA", String.valueOf(horas), key_cobro);
            if (!reintentar) Tienda.actualizar("update tienda_contrato set state = 'MOROSO' where key = ? and state = 'ACTIVO'", k.getString("key"));
            return cobro(key_cobro);
        }
    }

    /**
     * Cobro aprobado → pago de la cuota en la caja automática + un mes de suscripción → PAGADO. Cada paso se salta si
     * ya se hizo (cuota ya amortizada, suscripción ya creada), así que repetirlo es seguro.
     */
    static JSONObject registrarPago(String key_cobro, JSONObject pago) throws Exception {
        JSONObject cb = cobro(key_cobro);
        if (cb.optString("state").equals("PAGADO")) return cb;
        JSONObject k = contrato(cb.getString("key_contrato"));
        JSONObject t = Tienda.consulta("select to_json(t) as json from tienda t where t.key = ?", k.getString("key_tienda"));
        String key_empresa = k.getString("key_empresa"), key_usuario = t.getString("key_usuario_venta");

        // 1) Pago de la cuota en caja (con verificación del cobro con tarjeta).
        JSONArray cuotas = cuotasDe(k.getString("key_compra_venta"), key_empresa, key_usuario);
        JSONObject cu = cuotaNumero(cuotas, cb.getInt("numero"));
        if (cu == null) throw new Exception("No se encontró la cuota " + cb.getInt("numero") + " en la venta");
        if (cu.optDouble("total_amortizado", 0) + 0.005 < cu.optDouble("monto", 0)) {
            double mto = cb.getDouble("monto");
            String ref = "Ref: Cybersource " + pago.optString("cybs_id") + (pago.optString("aprobacion").isEmpty() ? "" : " · Aut " + pago.optString("aprobacion"))
                    + (pago.optString("tarjeta").isEmpty() ? "" : " · " + pago.optString("tarjeta")) + " · cuota " + cb.getInt("numero") + "/" + k.getInt("cuotas");
            JSONObject req = new JSONObject().put("component", "caja_detalle").put("type", "amortizarCuotaCompra").put("estado", "cargando")
                    .put("key_usuario", key_usuario).put("key_empresa", key_empresa).put("key_caja", cajaAutomatica(t))
                    .put("data", new JSONObject().put("key_compra_venta", k.getString("key_compra_venta")).put("cuotas", new JSONArray().put(cu.getString("key")))
                            .put("tipos_pago", new JSONObject().put(t.getString("key_empresa_tipo_pago"), new JSONObject()
                                    .put("monto_nacional", mto).put("monto_extranjera", mto).put("referencia", ref)
                                    .put("pago_tarjeta", new JSONObject().put("key", pago.getString("key"))))));
            JSONObject r = Tienda.bus("caja", req, 60000);
            // caja devuelve el movimiento creado en data.tipos_pago.<tipo>.key_caja_detalle (sirve para anular el pago).
            JSONObject tp = r.optJSONObject("data") == null ? null : r.getJSONObject("data").optJSONObject("tipos_pago");
            JSONObject v = tp == null ? null : tp.optJSONObject(t.getString("key_empresa_tipo_pago"));
            if (v != null && !v.optString("key_caja_detalle", "").isEmpty())
                Tienda.actualizar("update tienda_cobro set key_caja_detalle = ? where key = ?", v.getString("key_caja_detalle"), key_cobro);
        }

        // 2) Un mes más de suscripción (el 1 lo crea la venta).
        if (cb.optString("key_suscripcion", "").isEmpty()) {
            JSONObject m = Tienda.modelo(k.getString("key_modelo"));
            String key_producto = k.optString("key_producto", "");
            if (key_producto.isEmpty()) {
                JSONObject s = suscripcionDeVenta(key_empresa, key_usuario, k.getString("key_cliente"), k.getString("key_modelo"), null);
                key_producto = s == null ? "" : s.optString("key_producto", "");
                if (!key_producto.isEmpty()) Tienda.actualizar("update tienda_contrato set key_producto = ? where key = ?", key_producto, k.getString("key"));
            }
            if (key_producto.isEmpty()) throw new Exception("No se encontró el producto de la suscripción del contrato");
            String[] mes = proximoMes(key_empresa, key_usuario, k.getString("key_cliente"), m);
            JSONObject r = Tienda.bus("inventario", new JSONObject().put("version", "1.0").put("component", "suscripcion").put("type", "registro").put("estado", "cargando")
                    .put("key_usuario", key_usuario).put("key_empresa", key_empresa)
                    .put("data", new JSONObject().put("key_cliente", k.getString("key_cliente")).put("key_producto", key_producto)
                            .put("fecha_inicio", mes[0]).put("fecha_fin", mes[1]).put("key_sucursal", k.getString("key_sucursal"))
                            .put("descripcion", "Cuota " + cb.getInt("numero") + "/" + k.getInt("cuotas") + " · " + k.optString("descripcion"))));
            String ks = r.optJSONObject("data") == null ? null : r.getJSONObject("data").optString("key", null);
            Tienda.actualizar("update tienda_cobro set key_suscripcion = ? where key = ?", ks, key_cobro);
        }

        // 3) Listo. Si había un link enviado que no se usó, se anula para que el cliente no pague dos veces.
        if (!cb.optString("key_link", "").isEmpty() && !cb.getString("key_link").equals(pago.optString("key"))) {
            try {
                CyberSource.llamar(new JSONObject().put("component", "link_pago").put("type", "anular").put("key", cb.getString("key_link")).put("key_empresa", key_empresa));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        Tienda.actualizar("update tienda_cobro set state = 'PAGADO', fecha_pago = coalesce(fecha_pago, now()), error = null, proximo_intento = null where key = ?", key_cobro);
        Tienda.actualizar("update tienda_contrato set state = case "
                + "when not exists (select 1 from tienda_cobro c where c.key_contrato = tienda_contrato.key and c.estado > 0 and c.state not in ('PAGADO','ANULADO')) then 'FINALIZADO' "
                + "when not exists (select 1 from tienda_cobro c where c.key_contrato = tienda_contrato.key and c.estado > 0 and c.state in ('MORA','INCIERTO')) then 'ACTIVO' "
                + "else state end where key = ? and state in ('ACTIVO','MOROSO')", k.getString("key"));
        return cobro(key_cobro);
    }

    /** Link para que el cliente pague una cuota (en mora o por adelantado); guarda la tarjeta nueva para los cobros siguientes. */
    static JSONObject generarLink(String key_cobro, String key_empresa) throws Exception {
        synchronized (("tienda_cobro:" + key_cobro).intern()) {
            JSONObject cb = cobro(key_cobro);
            if (!key_empresa.equals(cb.optString("key_empresa"))) throw new Exception("Cobro no encontrado");
            String st = cb.optString("state");
            if (!st.equals("PENDIENTE") && !st.equals("REINTENTO") && !st.equals("MORA")) throw new Exception("La cuota está " + st);
            if (!cb.optString("key_link", "").isEmpty()) {
                JSONObject l = linkPago(cb.getString("key_link"), key_empresa);
                if (l != null && !"ANULADA".equals(l.optString("state")) && !"REEMBOLSADA".equals(l.optString("state"))) return l;
            }
            JSONObject k = contrato(cb.getString("key_contrato"));
            JSONObject c = k.optJSONObject("cliente") == null ? new JSONObject() : k.getJSONObject("cliente");
            JSONObject t = Tienda.consulta("select to_json(t) as json from tienda t where t.key = ?", k.getString("key_tienda"));
            JSONObject r = CyberSource.llamar(new JSONObject().put("component", "link_pago").put("type", "registro")
                    .put("key_empresa", key_empresa).put("key_usuario", t.optString("key_usuario_venta"))
                    .put("data", new JSONObject().put("monto", cb.getDouble("monto")).put("guardar_tarjeta", true)
                            .put("descripcion", k.optString("descripcion") + " · cuota " + cb.getInt("numero") + "/" + k.getInt("cuotas"))
                            .put("key_solicitud", "cobro:" + key_cobro + ":link:" + (cb.optInt("intentos", 0) + 1) + ":" + System.currentTimeMillis())
                            .put("cliente", new JSONObject().put("nombre", c.optString("nombres")).put("apellido", c.optString("apellidos"))
                                    .put("email", c.optString("email")).put("telefono", c.optString("celular")).put("documento", c.optString("ci")))));
            if (!"exito".equals(r.optString("estado"))) throw new Exception(r.optString("error", "No se pudo crear el link de pago"));
            JSONObject l = r.getJSONObject("data");
            Tienda.actualizar("update tienda_cobro set key_link = ?, url_link = ? where key = ?", l.getString("key"), l.getString("url"), key_cobro);
            return l;
        }
    }

    /**
     * Cancelación del contrato (la decide el personal): deja de cobrar. Con anular_restantes las cuotas que faltan se
     * marcan ANULADO aquí (la deuda en compra_venta se ajusta desde el ERP); sin eso quedan pendientes y se pueden
     * cobrar con link (p. ej. penalidad).
     */
    static JSONObject cancelar(String key, String key_empresa, String key_usuario, boolean anular_restantes, String motivo) throws Exception {
        JSONObject k = contrato(key);
        if (!key_empresa.equals(k.optString("key_empresa"))) throw new Exception("Contrato no encontrado");
        if (k.optString("state").equals("CANCELADO") || k.optString("state").equals("FINALIZADO")) throw new Exception("El contrato ya está " + k.optString("state"));
        if (motivo == null || motivo.trim().length() < 3) throw new Exception("Indicá el motivo de la cancelación");
        Tienda.actualizar("update tienda_contrato set state = 'CANCELADO', fecha_cancelacion = now(), motivo_cancelacion = ?, key_usuario_cancelacion = ? where key = ?",
                Tienda.recortar(motivo.trim(), 250), key_usuario, key);
        if (anular_restantes)
            Tienda.actualizar("update tienda_cobro set state = 'ANULADO', proximo_intento = null where key_contrato = ? and state in ('PENDIENTE','REINTENTO','MORA')", key);
        return detalle(key, key_empresa);
    }

    // ===================== Anular una compra (devuelve el dinero) =====================

    /**
     * Anula una compra de la tienda web y devuelve el dinero a la tarjeta (sirve para pruebas y para devoluciones):
     *  - orden sin pagar: anula el link y la orden.
     *  - pago único: anula la venta (la caja devuelve el pago con tarjeta).
     *  - contrato en cuotas: cancela el contrato (cuotas restantes anuladas), anula las suscripciones de los meses
     *    cobrados aparte y anula la venta a crédito: caja devuelve cada cobro con tarjeta de la venta y revierte sus
     *    movimientos (los pagos de cuotas quedan ligados a la venta desde caja 2026-11-09).
     * Cada paso se salta si ya está hecho: si algo falla a mitad, repetir sigue donde quedó.
     */
    static JSONObject anularOrden(String key_orden, String key_empresa, String key_usuario, String motivo) throws Exception {
        if (motivo == null || motivo.trim().length() < 3) throw new Exception("Indicá el motivo de la anulación");
        synchronized (("tienda_orden:" + key_orden).intern()) {
            JSONObject o = Tienda.consulta("select to_json(o) as json from tienda_orden o where o.key = ?", key_orden);
            if (o.isEmpty() || !key_empresa.equals(o.optString("key_empresa"))) throw new Exception("Orden no encontrada");
            String st = o.optString("state");
            if (st.equals("ANULADA")) return o;
            JSONObject t = Tienda.consulta("select to_json(t) as json from tienda t where t.key = ?", o.getString("key_tienda"));

            if (!st.equals("VENTA")) {
                // Sin venta: si el link ya se pagó, primero hay que registrar la venta (orden PAGADA) o revisarla.
                JSONObject l = o.optString("key_link", "").isEmpty() ? null : linkPago(o.getString("key_link"), key_empresa);
                if (l != null && "AUTHORIZED".equals(l.optString("state")))
                    throw new Exception("La orden ya está pagada pero sin venta: reintentá registrarla y después anulala");
                if (l != null && !"ANULADA".equals(l.optString("state")))
                    CyberSource.llamar(new JSONObject().put("component", "link_pago").put("type", "anular").put("key", l.getString("key")).put("key_empresa", key_empresa));
                marcarAnulada(key_orden, key_usuario, motivo);
                return Tienda.consulta("select to_json(o) as json from tienda_orden o where o.key = ?", key_orden);
            }

            String venta = o.getString("key_compra_venta");
            // La reversión va a la caja abierta del punto de venta de la tienda (se abre sola si hace falta).
            cajaAutomatica(t);
            if (!o.optString("key_contrato", "").isEmpty()) {
                JSONObject k = contrato(o.getString("key_contrato"));
                if (k.optString("state").equals("ACTIVO") || k.optString("state").equals("MOROSO"))
                    cancelar(k.getString("key"), key_empresa, key_usuario, true, "Compra anulada: " + motivo.trim());
                else Tienda.actualizar("update tienda_cobro set state = 'ANULADO', proximo_intento = null where key_contrato = ? and state in ('PENDIENTE','REINTENTO','MORA')", k.getString("key"));
                // Suscripciones de los meses cobrados aparte (la del mes 1 la anula la venta).
                JSONArray cobros = cobrosDe(k.getString("key"));
                for (int i = 0; i < cobros.length(); i++) {
                    JSONObject cb = cobros.getJSONObject(i);
                    if (!cb.optString("state").equals("PAGADO") || cb.getInt("numero") <= 1 || cb.optString("key_suscripcion", "").isEmpty()) continue;
                    try {
                        Tienda.bus("inventario", new JSONObject().put("version", "1.0").put("component", "suscripcion").put("type", "editar").put("estado", "cargando")
                                .put("key_usuario", key_usuario).put("key_empresa", key_empresa)
                                .put("data", new JSONObject().put("key", cb.getString("key_suscripcion")).put("estado", 0)));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            // La venta: caja devuelve TODOS los cobros con tarjeta ligados a ella (pago único, o la cuota 1 y las cuotas
            // cobradas, que quedan ligadas a la venta) y revierte sus movimientos; compra_venta anula cuotas, pagos de
            // cuotas y asientos. (anularAmortizacion no sirve: solo deja anular el último pago del cliente, de cualquier venta.)
            JSONObject r = SocketCliente.sendSinc("caja", new JSONObject().put("component", "caja_detalle").put("type", "anularVenta").put("estado", "cargando")
                    .put("key_empresa", key_empresa).put("key_usuario", key_usuario).put("key_compra_venta", venta)
                    .put("datos_anulacion", new JSONObject().put("motivo", motivo.trim()).put("origen", "Tienda web")), 90000);
            if (r == null) throw new Exception("Sin respuesta del servicio caja al anular la venta");
            if ("error".equals(r.optString("estado")) && !String.valueOf(r.optString("error")).toLowerCase().contains("anulad"))
                throw new Exception("caja: " + r.optString("error", "no se pudo anular la venta"));
            if (!o.optString("key_contrato", "").isEmpty())
                Tienda.actualizar("update tienda_cobro set state = 'ANULADO', error = ? where key_contrato = ? and state = 'PAGADO'",
                        Tienda.recortar("Devuelto: " + motivo.trim(), 250), o.getString("key_contrato"));
            marcarAnulada(key_orden, key_usuario, motivo);
            return Tienda.consulta("select to_json(o) as json from tienda_orden o where o.key = ?", key_orden);
        }
    }

    private static void marcarAnulada(String key_orden, String key_usuario, String motivo) throws Exception {
        Tienda.actualizar("update tienda_orden set state = 'ANULADA', fecha_anulacion = now(), key_usuario_anulacion = ?, motivo_anulacion = ? where key = ?",
                key_usuario, Tienda.recortar(motivo.trim(), 250), key_orden);
    }

    // ===================== Consultas para los paneles =====================

    static JSONArray contratos(String key_empresa, String state) throws Exception {
        JSONArray out = new JSONArray();
        Connection con = SPGConect.pool.getConnection();
        try (PreparedStatement ps = con.prepareStatement("select json_build_object('contrato', to_json(k), "
                + "'pagadas', (select count(*) from tienda_cobro c where c.key_contrato = k.key and c.estado > 0 and c.state = 'PAGADO'), "
                + "'en_mora', (select count(*) from tienda_cobro c where c.key_contrato = k.key and c.estado > 0 and c.state in ('MORA','INCIERTO')), "
                + "'proximo', (select to_json(c) from tienda_cobro c where c.key_contrato = k.key and c.estado > 0 and c.state not in ('PAGADO','ANULADO') order by c.numero limit 1)"
                + ")::text as json from tienda_contrato k where k.key_empresa = ? and k.estado > 0 and (? = '' or k.state = ?) order by k.fecha_on desc limit 3000")) {
            ps.setString(1, key_empresa);
            ps.setString(2, state == null ? "" : state);
            ps.setString(3, state == null ? "" : state);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.put(new JSONObject(rs.getString("json")));
            }
        } finally {
            SPGConect.pool.releaseConnection(con);
        }
        return out;
    }

    static JSONObject detalle(String key, String key_empresa) throws Exception {
        JSONObject k = contrato(key);
        if (!key_empresa.equals(k.optString("key_empresa"))) throw new Exception("Contrato no encontrado");
        return new JSONObject().put("contrato", k).put("cobros", cobrosDe(key));
    }

    /** Cobros por fecha de vencimiento (y estado) con el cliente del contrato. */
    static JSONArray cobros(String key_empresa, String desde, String hasta, String state) throws Exception {
        if (!desde.isEmpty() && !desde.matches("\\d{4}-\\d{2}-\\d{2}")) throw new Exception("desde inválido");
        if (!hasta.isEmpty() && !hasta.matches("\\d{4}-\\d{2}-\\d{2}")) throw new Exception("hasta inválido");
        JSONArray out = new JSONArray();
        Connection con = SPGConect.pool.getConnection();
        try (PreparedStatement ps = con.prepareStatement("select (to_jsonb(c) || jsonb_build_object('cliente', k.cliente, 'contrato_descripcion', k.descripcion, "
                + "'cuotas', k.cuotas, 'contrato_state', k.state, 'tarjeta', k.tarjeta))::text as json from tienda_cobro c join tienda_contrato k on k.key = c.key_contrato "
                + "where c.key_empresa = ? and c.estado > 0 and (? = '' or c.fecha_vencimiento >= ?::date) and (? = '' or c.fecha_vencimiento <= ?::date) "
                + "and (? = '' or c.state = ?) order by c.fecha_vencimiento desc, c.numero limit 5000")) {
            ps.setString(1, key_empresa);
            ps.setString(2, desde); ps.setString(3, desde.isEmpty() ? "1900-01-01" : desde);
            ps.setString(4, hasta); ps.setString(5, hasta.isEmpty() ? "2999-12-31" : hasta);
            ps.setString(6, state == null ? "" : state); ps.setString(7, state == null ? "" : state);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.put(new JSONObject(rs.getString("json")));
            }
        } finally {
            SPGConect.pool.releaseConnection(con);
        }
        return out;
    }

    // ===================== internos =====================

    static JSONObject cobro(String key) throws Exception {
        JSONObject c = Tienda.consulta("select to_json(c) as json from tienda_cobro c where c.key = ? and c.estado > 0", key == null ? "" : key);
        if (c.isEmpty()) throw new Exception("Cobro no encontrado");
        return c;
    }

    static JSONObject contrato(String key) throws Exception {
        JSONObject k = Tienda.consulta("select to_json(k) as json from tienda_contrato k where k.key = ? and k.estado > 0", key == null ? "" : key);
        if (k.isEmpty()) throw new Exception("Contrato no encontrado");
        return k;
    }

    static JSONArray cobrosDe(String key_contrato) throws Exception {
        return arr("select coalesce(json_agg(c order by c.numero), '[]'::json)::text as json from tienda_cobro c where c.key_contrato = ? and c.estado > 0", key_contrato);
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

    private static JSONObject cobroNumero(JSONArray cobros, int numero) throws Exception {
        for (int i = 0; i < cobros.length(); i++) if (cobros.getJSONObject(i).getInt("numero") == numero) return cobros.getJSONObject(i);
        throw new Exception("Falta el cobro " + numero);
    }

    /** Cuotas activas de la venta en compra_venta, ordenadas por fecha. */
    static JSONArray cuotasDe(String key_compra_venta, String key_empresa, String key_usuario) throws Exception {
        JSONObject r = Tienda.bus("compra_venta", new JSONObject().put("component", "cuota").put("type", "getAll").put("estado", "cargando")
                .put("key_compra_venta", key_compra_venta).put("key_empresa", key_empresa).put("key_usuario", key_usuario));
        java.util.List<JSONObject> l = new java.util.ArrayList<>();
        JSONObject d = r.optJSONObject("data");
        if (d != null) for (String key : d.keySet()) {
            JSONObject c = d.optJSONObject(key);
            if (c != null && c.optInt("estado", 1) > 0) l.add(c);
        }
        l.sort((a, b) -> a.optString("fecha").compareTo(b.optString("fecha")) != 0 ? a.optString("fecha").compareTo(b.optString("fecha"))
                : a.optString("codigo").compareTo(b.optString("codigo")));
        return new JSONArray(l);
    }

    private static JSONObject cuotaNumero(JSONArray cuotas, int numero) {
        for (int i = 0; i < cuotas.length(); i++) if (String.valueOf(numero).equals(cuotas.getJSONObject(i).optString("codigo"))) return cuotas.getJSONObject(i);
        return null;
    }

    private static String cajaAutomatica(JSONObject t) throws Exception {
        JSONObject caja = Tienda.bus("caja", new JSONObject().put("component", "caja").put("type", "getAutomatica")
                .put("key_punto_venta", t.getString("key_punto_venta")).put("key_empresa", t.getString("key_empresa")).put("key_usuario", t.getString("key_usuario_venta")));
        return caja.getJSONObject("data").getString("key");
    }

    private static Object tipoPagoDe(String key_empresa_tipo_pago, String key_empresa) throws Exception {
        JSONObject etp = Tienda.bus("caja", new JSONObject().put("component", "empresa_tipo_pago").put("type", "getByKey")
                .put("key", key_empresa_tipo_pago).put("key_empresa", key_empresa)).optJSONObject("data");
        JSONObject tipos = Tienda.bus("caja", new JSONObject().put("component", "tipo_pago").put("type", "getAll")).optJSONObject("data");
        JSONObject tp = tipos == null || etp == null ? null : tipos.optJSONObject(etp.optString("key_tipo_pago"));
        return tp == null ? JSONObject.NULL : tp;
    }

    /** Un mes de suscripción: desde hoy, o desde el día siguiente al fin de la última suscripción vigente del mismo tipo. */
    static String[] proximoMes(String key_empresa, String key_usuario, String key_cliente, JSONObject m) throws Exception {
        String[] f = Tienda.fechasSuscripcion(key_empresa, key_usuario, key_cliente, m);
        LocalDate inicio = LocalDate.parse(f[0]);
        return new String[] { inicio.toString(), inicio.plusMonths(1).minusDays(1).toString() };
    }

    /** Suscripción que creó la venta (la de ese modelo; con inicio dado si se conoce, si no la más reciente). */
    private static JSONObject suscripcionDeVenta(String key_empresa, String key_usuario, String key_cliente, String key_modelo, String inicio) {
        try {
            JSONObject r = SocketCliente.sendSinc("inventario", new JSONObject().put("version", "1.0").put("component", "suscripcion")
                    .put("type", "getByKeyCliente").put("key_cliente", key_cliente).put("key_empresa", key_empresa).put("key_usuario", key_usuario), 20000);
            Object d = r == null ? null : r.opt("data");
            if (d instanceof String) d = ((String) d).trim().startsWith("[") ? new JSONArray((String) d) : new JSONObject((String) d);
            JSONArray subs = d instanceof JSONArray ? (JSONArray) d : new JSONArray();
            if (d instanceof JSONObject) for (String k : ((JSONObject) d).keySet()) subs.put(((JSONObject) d).get(k));
            JSONObject mejor = null;
            for (int i = 0; i < subs.length(); i++) {
                JSONObject s = subs.optJSONObject(i);
                if (s == null || s.optInt("estado", 1) <= 0) continue;
                JSONObject prod = s.optJSONObject("producto");
                if (prod == null || !key_modelo.equals(prod.optString("key_modelo"))) continue;
                if (inicio != null && !s.optString("fecha_inicio").startsWith(inicio)) continue;
                if (mejor == null || s.optString("fecha_on").compareTo(mejor.optString("fecha_on")) > 0) mejor = s;
            }
            return mejor;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static JSONObject linkPago(String key, String key_empresa) {
        try {
            JSONObject r = CyberSource.llamar(new JSONObject().put("component", "link_pago").put("type", "getByKey").put("key", key).put("key_empresa", key_empresa));
            return "exito".equals(r.optString("estado")) ? r.optJSONObject("data") : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Datos del cobro aprobado guardado en el cobro (link o cobro automático) para la referencia de caja. */
    private static JSONObject linkOMit(JSONObject cb) {
        JSONObject l = linkPago(cb.optString("key_transaccion"), cb.getString("key_empresa"));
        return l != null ? l : new JSONObject().put("key", cb.optString("key_transaccion"));
    }

    private static String monto(double v) {
        return String.format(java.util.Locale.US, "%.2f", v);
    }
}
