package Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONObject;

import Server.SSSAbstract.SSSessionAbstract;
import Servisofts.SConfig;
import Servisofts.SPGConect;
import SocketCliente.SocketCliente;

/**
 * Sincronización de paquetes/suscripciones entre el sistema antiguo de Calistenia (base servisofts.calistenia) y
 * S-ERP, para las empresas de sync_cal_config (ver sql/sync_calistenia.sql y sql/sync_calistenia_legacy.sql).
 *
 * Calistenia -> S-ERP: los triggers de la base antigua anotan los cambios en sync_outbox; cada pasada los convierte en
 * eventos (sync_cal_evento) y registra la venta completa en S-ERP (cliente por CI, caja automática del punto de venta
 * "Calistenia (sistema antiguo)" de la sucursal, suscripciones con las mismas fechas) o la anula.
 * S-ERP -> Calistenia: cada pasada busca ventas nuevas de planes enlazados y escribe en la base antigua el
 * paquete_venta, un acceso por suscripción y los movimientos de la caja virtual "S-ERP" de la sucursal (con su
 * traspaso a bancos), igual que la "Caja qr", para que cuadren sus reportes. Lo que escribe el sync va con
 * sync.origen = 'serp' para que no vuelva (anti-eco).
 * Accesos (sync_cal_acceso): se comparan los dos lados contra el último estado acordado; cambia el que se movió.
 * Catálogo (sync_cal_paquete): el sistema donde nació el plan es el dueño de sus datos; los nuevos se crean en el otro.
 *
 * Bus (component "sync_calistenia", permisos de la página /sync_calistenia): ver | configurar.
 */
public class SyncCalistenia {
    public static final String COMPONENT = "sync_calistenia";
    static final String PAGE = "/sync_calistenia";
    static final ZoneId BO = ZoneId.of("America/La_Paz");
    static final int MAX_INTENTOS = 20;
    static final String ORIGEN_CAL = "calistenia", ORIGEN_SERP = "serp";

    /** Error que necesita a una persona (no se reintenta solo). */
    static class Revisar extends Exception {
        Revisar(String m) { super(m); }
    }

    /** No corresponde sincronizar (sucursal apagada, venta anulada antes de copiarse...). */
    static class Omitir extends Exception {
        Omitir(String m) { super(m); }
    }

    // ===================== Bus =====================

    public static void onMessage(JSONObject obj, SSSessionAbstract session) {
        try {
            String key_usuario = obj.getString("key_usuario");
            String key_empresa = obj.getString("key_empresa");
            JSONObject permisos = permisosDe(key_usuario, key_empresa);
            switch (obj.getString("type")) {
                case "estado":
                    exigir(permisos, "ver");
                    obj.put("data", estado(key_empresa));
                    break;
                case "config_guardar":
                    exigir(permisos, "configurar");
                    obj.put("data", guardarConfig(key_empresa, obj.getJSONObject("data")));
                    break;
                case "eventos":
                    exigir(permisos, "ver");
                    obj.put("data", lista("select to_json(e) as json from sync_cal_evento e where e.key_empresa = ? and e.estado > 0 "
                            + "and (? = '' or e.state = ?) order by e.fecha_on desc limit 3000", key_empresa, obj.optString("state", ""), obj.optString("state", "")));
                    break;
                case "evento_reprocesar":
                    exigir(permisos, "configurar");
                    actualizar("update sync_cal_evento set state = 'PENDIENTE', intentos = 0, proximo_intento = now(), error = null "
                            + "where key = ? and key_empresa = ? and state <> 'HECHO'", obj.getString("key"), key_empresa);
                    obj.put("data", procesarEvento(config(key_empresa), obj.getString("key")));
                    break;
                case "evento_omitir":
                    exigir(permisos, "configurar");
                    actualizar("update sync_cal_evento set state = 'OMITIDO', error = ? where key = ? and key_empresa = ? and state <> 'HECHO'",
                            recortar("Omitido a mano: " + obj.optString("motivo", ""), 250), obj.getString("key"), key_empresa);
                    break;
                case "sucursales":
                    exigir(permisos, "ver");
                    obj.put("data", sucursales(key_empresa));
                    break;
                case "sucursal_guardar":
                    exigir(permisos, "configurar");
                    obj.put("data", guardarSucursal(key_empresa, obj.getJSONObject("data")));
                    break;
                case "paquetes":
                    exigir(permisos, "ver");
                    obj.put("data", paquetes(key_empresa));
                    break;
                case "tipos_pago":
                    exigir(permisos, "ver");
                    obj.put("data", lista("select to_json(t) as json from sync_cal_tipo_pago t where t.key_empresa = ? and t.estado > 0 "
                            + "order by t.key_sucursal_cal nulls first, t.key_tipo_pago_cal", key_empresa));
                    break;
                case "clientes":
                    exigir(permisos, "ver");
                    obj.put("data", lista("select to_json(c) as json from sync_cal_cliente c where c.key_empresa = ? and c.estado > 0 "
                            + "order by c.fecha_on desc limit 5000", key_empresa));
                    break;
                case "conciliacion":
                    exigir(permisos, "ver");
                    obj.put("data", conciliacion(config(key_empresa), obj.optString("fecha", LocalDate.now(BO).toString())));
                    break;
                case "historico_revisar":
                    exigir(permisos, "ver");
                    obj.put("data", historicoRevisar(config(key_empresa), obj.getString("mes")));
                    break;
                case "historico_cargar":
                    exigir(permisos, "configurar");
                    obj.put("data", historicoCargar(config(key_empresa), obj.getString("mes"), key_usuario));
                    break;
                case "pasada":
                    exigir(permisos, "configurar");
                    obj.put("data", pasada(config(key_empresa), true));
                    break;
                default:
                    throw new Exception("type no soportado: " + obj.getString("type"));
            }
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
        }
    }

    // ===================== Tarea =====================

    /** Una pasada por cada empresa con el sync encendido (Tasks.SyncCalisteniaTask, cada minuto). */
    public static void pasadaGeneral() {
        List<JSONObject> cfgs;
        try {
            cfgs = listaObj("select to_json(c) as json from sync_cal_config c where c.estado > 0 and c.activo");
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        for (JSONObject cfg : cfgs) {
            try {
                pasada(cfg, false);
            } catch (Exception e) {
                e.printStackTrace();
                registrarTarea(cfg.getString("key_empresa"), 0, 1, "La pasada falló: " + e.getMessage(), null);
            }
        }
    }

    static JSONObject pasada(JSONObject cfg, boolean manual) throws Exception {
        String key_empresa = cfg.getString("key_empresa");
        synchronized (("sync_cal:" + key_empresa).intern()) {
            String inicio = consultaTexto("select now()::text as json");
            if (!cfg.optBoolean("activo")) return new JSONObject().put("activo", false).put("error", "El sync está apagado");
            int procesados = 0, errores = 0;
            String ultimoError = null;
            try {
                leerOutbox(cfg);
            } catch (Exception e) {
                errores++;
                ultimoError = "Outbox de Calistenia: " + e.getMessage();
                e.printStackTrace();
            }
            try {
                detectarVentasSerp(cfg);
            } catch (Exception e) {
                errores++;
                ultimoError = "Ventas de S-ERP: " + e.getMessage();
                e.printStackTrace();
            }
            // Catálogo cada 5 minutos (o en la pasada manual).
            try {
                if (manual || consultaTexto("select (coalesce(fecha_catalogo, '1900-01-01') < now() - interval '5 minutes')::text as json from sync_cal_config where key = ?",
                        cfg.getString("key")).equals("true")) {
                    catalogo(cfg);
                    actualizar("update sync_cal_config set fecha_catalogo = now() where key = ?", cfg.getString("key"));
                }
            } catch (Exception e) {
                errores++;
                ultimoError = "Catálogo: " + e.getMessage();
                e.printStackTrace();
            }
            for (JSONObject ev : listaObj("select to_json(e) as json from sync_cal_evento e where e.key_empresa = ? and e.estado > 0 "
                    + "and e.state in ('PENDIENTE','ERROR') and e.proximo_intento <= now() order by case e.tipo when 'paquete' then 0 when 'venta' then 1 "
                    + "when 'acceso' then 2 else 3 end, e.fecha_on limit 200", key_empresa)) {
                JSONObject r = procesarEvento(cfg, ev.getString("key"));
                procesados++;
                if ("ERROR".equals(r.optString("state")) || "REVISAR".equals(r.optString("state"))) {
                    errores++;
                    ultimoError = r.optString("tipo") + " " + r.optString("key_origen").substring(0, Math.min(8, r.optString("key_origen").length())) + ": " + r.optString("error");
                }
            }
            try {
                procesados += conciliarAccesos(cfg);
                procesados += pagosNuevosSerp(cfg);
            } catch (Exception e) {
                errores++;
                ultimoError = "Accesos/pagos: " + e.getMessage();
                e.printStackTrace();
            }
            registrarTarea(key_empresa, procesados, errores, ultimoError, inicio);
            return new JSONObject().put("procesados", procesados).put("errores", errores).put("ultimo_error", ultimoError == null ? JSONObject.NULL : ultimoError);
        }
    }

    // ===================== Detección =====================

    /** Lee sync_outbox de Calistenia desde el último id y crea/rearma los eventos. */
    static void leerOutbox(JSONObject cfg) throws Exception {
        String key_empresa = cfg.getString("key_empresa");
        long ultimo = cfg.optLong("ultimo_outbox", 0);
        try (Connection cal = conCal(cfg)) {
            List<JSONObject> filas = q(cal, "select id, tabla, operacion, key, cambios::text as cambios, origen from sync_outbox where id > ? order by id limit 5000", ultimo);
            for (JSONObject f : filas) {
                ultimo = Math.max(ultimo, f.getLong("id"));
                if (!f.optString("origen").isEmpty()) continue; // lo escribió el sync
                String tabla = f.getString("tabla"), key = f.getString("key"), cambios = f.optString("cambios");
                boolean insert = "I".equals(f.getString("operacion"));
                switch (tabla) {
                    case "paquete_venta":
                        if (insert) evento(key_empresa, ORIGEN_CAL, "venta", key, null, 30, false);
                        else if (cambios.contains("estado")) {
                            JSONObject pv = uno(cal, "select estado from paquete_venta where key = ?", key);
                            if (pv != null && pv.optInt("estado", 1) <= 0) evento(key_empresa, ORIGEN_CAL, "anulacion", key, null, 5, true);
                        }
                        break;
                    case "paquete_venta_usuario":
                        evento(key_empresa, ORIGEN_CAL, "acceso", key, null, 30, true);
                        break;
                    case "prorroga_paquete_venta_usuario": {
                        JSONObject p = uno(cal, "select key_paquete_venta_usuario from prorroga_paquete_venta_usuario where key = ?", key);
                        if (p != null) evento(key_empresa, ORIGEN_CAL, "acceso", p.getString("key_paquete_venta_usuario"), null, 5, true);
                        break;
                    }
                    case "paquete":
                        evento(key_empresa, ORIGEN_CAL, "paquete", key, null, 0, true);
                        break;
                }
            }
        }
        actualizar("update sync_cal_config set ultimo_outbox = ?::bigint where key = ?", String.valueOf(ultimo), cfg.getString("key"));
    }

    /** Ventas nuevas de S-ERP con planes enlazados (desde el último cursor, con 10 min de solape). */
    static void detectarVentasSerp(JSONObject cfg) throws Exception {
        String key_empresa = cfg.getString("key_empresa");
        String desde = fechaTexto(cfg.optString("ultimo_serp", ""));
        if (desde.isEmpty()) {
            actualizar("update sync_cal_config set ultimo_serp = now() where key = ?", cfg.getString("key"));
            return;
        }
        String[] modelos = columna("select key_modelo as json from sync_cal_paquete where key_empresa = ? and estado > 0", key_empresa);
        if (modelos.length == 0) return;
        String[] pvSync = columna("select key_punto_venta as json from sync_cal_sucursal where key_empresa = ? and estado > 0 and key_punto_venta is not null", key_empresa);
        String max = desde;
        try (Connection cv = conServicio("compra_venta"); Connection caja = conServicio("caja")) {
            List<JSONObject> ventas = q(cv, "select cv.key, cv.fecha_on::text as fecha_on, cv.key_caja, coalesce(cv.observacion, '') as observacion from compra_venta cv "
                    + "where cv.key_empresa = ? and cv.tipo = 'venta' and cv.fecha_on > ?::timestamp - interval '10 minutes' "
                    + "and exists (select 1 from compra_venta_detalle d where d.key_compra_venta = cv.key and d.estado > 0 and d.key_modelo = any(?)) order by cv.fecha_on",
                    key_empresa, desde, cv.createArrayOf("varchar", modelos));
            for (JSONObject v : ventas) {
                if (v.getString("fecha_on").compareTo(max) > 0) max = v.getString("fecha_on");
                if (v.getString("observacion").startsWith("Sistema antiguo Calistenia")) continue; // la creó el sync
                if (!consultaTexto("select count(*)::text as json from sync_cal_evento where key_empresa = ? and origen = 'calistenia' and key_destino = ?",
                        key_empresa, v.getString("key")).equals("0")) continue;
                JSONObject c = uno(caja, "select key_punto_venta from caja where key = ?", v.optString("key_caja"));
                if (c != null && java.util.Arrays.asList(pvSync).contains(c.optString("key_punto_venta"))) continue;
                evento(key_empresa, ORIGEN_SERP, "venta", v.getString("key"), null, 20, false);
            }
        }
        actualizar("update sync_cal_config set ultimo_serp = ?::timestamp where key = ?", max, cfg.getString("key"));
    }

    // ===================== Procesamiento de eventos =====================

    static JSONObject procesarEvento(JSONObject cfg, String key) throws Exception {
        synchronized (("sync_cal_evento:" + key).intern()) {
            JSONObject ev = consulta("select to_json(e) as json from sync_cal_evento e where e.key = ?", key);
            if (ev.isEmpty()) throw new Exception("Evento no encontrado");
            if (!"PENDIENTE".equals(ev.optString("state")) && !"ERROR".equals(ev.optString("state"))) return ev;
            try {
                String destino;
                String o = ev.getString("origen"), t = ev.getString("tipo");
                if (o.equals(ORIGEN_CAL) && t.equals("venta")) destino = ventaCal(cfg, ev);
                else if (o.equals(ORIGEN_CAL) && t.equals("anulacion")) destino = anulacionCal(cfg, ev);
                else if (o.equals(ORIGEN_CAL) && t.equals("acceso")) destino = accesoCal(cfg, ev);
                else if (o.equals(ORIGEN_CAL) && t.equals("paquete")) {
                    catalogo(cfg);
                    destino = null;
                } else if (o.equals(ORIGEN_SERP) && t.equals("venta")) destino = ventaSerp(cfg, ev);
                else if (o.equals(ORIGEN_SERP) && t.equals("anulacion")) destino = anulacionSerp(cfg, ev);
                else throw new Revisar("Evento desconocido " + o + "/" + t);
                actualizar("update sync_cal_evento set state = 'HECHO', key_destino = coalesce(?, key_destino), error = null, fecha_proceso = now() where key = ?", destino, key);
            } catch (Omitir e) {
                actualizar("update sync_cal_evento set state = 'OMITIDO', error = ?, fecha_proceso = now() where key = ?", recortar(e.getMessage(), 250), key);
            } catch (Revisar e) {
                actualizar("update sync_cal_evento set state = 'REVISAR', error = ?, intentos = intentos + 1, fecha_proceso = now() where key = ?", recortar(e.getMessage(), 250), key);
            } catch (Exception e) {
                e.printStackTrace();
                actualizar("update sync_cal_evento set intentos = intentos + 1, error = ?, fecha_proceso = now(), "
                        + "state = case when intentos + 1 >= " + MAX_INTENTOS + " then 'REVISAR' else 'ERROR' end, "
                        + "proximo_intento = now() + (least(60, power(2, intentos)) || ' minutes')::interval where key = ?",
                        recortar(e.getMessage() == null ? e.toString() : e.getMessage(), 250), key);
            }
            return consulta("select to_json(e) as json from sync_cal_evento e where e.key = ?", key);
        }
    }

    /** Venta de Calistenia -> venta completa en S-ERP. Devuelve la key de compra_venta. */
    static String ventaCal(JSONObject cfg, JSONObject ev) throws Exception {
        return ventaCal(cfg, ev, null);
    }

    /** fechaContable != null: carga histórica (sin el límite de activación; venta y comprobante con esa fecha). */
    static String ventaCal(JSONObject cfg, JSONObject ev, String fechaContable) throws Exception {
        String key_empresa = cfg.getString("key_empresa");
        try (Connection cal = conCal(cfg)) {
            JSONObject pv = uno(cal, "select key, key_paquete, monto, estado, nit, razon_social, coalesce(is_server_serp, false) as is_server_serp, "
                    + "fecha_on::text as fecha_on, (fecha_on > now() - interval '5 minutes') as reciente from paquete_venta where key = ?", ev.getString("key_origen"));
            if (pv == null) throw new Omitir("La venta ya no existe en Calistenia");
            if (pv.optInt("estado", 1) <= 0) throw new Omitir("Anulada en Calistenia antes de copiarse");
            if (pv.optBoolean("is_server_serp")) throw new Omitir("Ya se había enviado a S-ERP con el botón \"POST SERP\"");
            List<JSONObject> pvus = q(cal, "select key, key_usuario, fecha_inicio::text as fecha_inicio, fecha_fin::text as fecha_fin, key_caja from paquete_venta_usuario "
                    + "where key_paquete_venta = ? and estado > 0 order by fecha_on", pv.getString("key"));
            if (pvus.isEmpty()) {
                if (pv.optBoolean("reciente")) throw new Exception("Esperando que Calistenia termine de guardar la venta");
                throw new Revisar("La venta no tiene clientes");
            }
            JSONObject caja = uno(cal, "select key, key_sucursal, coalesce(tipo, '') as tipo from caja where key = ?", pvus.get(0).optString("key_caja"));
            if (caja == null) throw new Revisar("La venta no tiene caja");
            if ("serp".equals(caja.getString("tipo"))) throw new Omitir("Venta de la caja S-ERP (vino de S-ERP)");
            JSONObject suc = sucursalCal(key_empresa, caja.getString("key_sucursal"));
            if (suc == null || !suc.optBoolean("activo")) throw new Omitir("Sucursal no activa en el sync");
            if (fechaContable == null && !suc.optString("fecha_desde").isEmpty() && fechaTexto(pv.getString("fecha_on")).compareTo(fechaTexto(suc.getString("fecha_desde"))) < 0)
                throw new Omitir("Venta anterior a la activación de la sucursal (se pasa con la carga histórica)");

            List<String> keysPvu = new ArrayList<>();
            for (JSONObject u : pvus) keysPvu.add(u.getString("key"));
            List<JSONObject> movs = q(cal, "select key_tipo_pago, monto, data::text as data from caja_movimiento where data->>'key_paquete_venta_usuario' = any(?) "
                    + "and key_caja_tipo_movimiento = '3' and monto > 0 and estado = 1 and descripcion like 'Venta de servicio%'",
                    cal.createArrayOf("varchar", keysPvu.toArray()));
            double total = 0;
            Map<String, Double> porTipo = new LinkedHashMap<>();
            Map<String, String> referencia = new HashMap<>();
            for (JSONObject m : movs) {
                total += m.getDouble("monto");
                porTipo.merge(m.getString("key_tipo_pago"), m.getDouble("monto"), Double::sum);
                JSONObject d = new JSONObject(m.optString("data", "{}"));
                String ref = (d.optString("Banco") + " " + d.optString("Código")).trim();
                if (!ref.isEmpty()) referencia.put(m.getString("key_tipo_pago"), ref);
            }
            double monto = round(pv.getDouble("monto"));
            if (Math.abs(round(total) - monto) > 0.01) {
                if (pv.optBoolean("reciente")) throw new Exception("Esperando que Calistenia termine de guardar los pagos");
                throw new Revisar("Los pagos (" + round(total) + ") no suman el monto de la venta (" + monto + ")");
            }
            if (monto <= 0) throw new Revisar("Venta de Bs 0 (beca o cortesía): no hay dinero que registrar como venta");
            String key_modelo = modeloDePaquete(cfg, pv.getString("key_paquete"), fechaContable != null);
            JSONObject m = Tienda.modelo(key_modelo);
            if (m == null) throw new Exception("No se pudo leer el plan " + key_modelo);

            // Clientes y suscripciones (mismas fechas).
            JSONArray suscriptores = new JSONArray();
            String comprador = null;
            for (JSONObject u : pvus) {
                String kc = clienteSerp(cfg, u.getString("key_usuario"));
                if (comprador == null) comprador = kc;
                suscriptores.put(new JSONObject().put("key_cliente", kc).put("fecha_inicio", u.getString("fecha_inicio").substring(0, 10))
                        .put("fecha_fin", u.getString("fecha_fin").substring(0, 10)).put("key_producto", "").put("key_sucursal", suc.getString("key_sucursal")));
            }
            JSONObject tipos_pago = new JSONObject();
            JSONObject tiposCaja = Tienda.bus("caja", new JSONObject().put("component", "tipo_pago").put("type", "getAll")).optJSONObject("data");
            for (Map.Entry<String, Double> e : porTipo.entrySet()) {
                String etp = tipoPagoSerp(key_empresa, caja.getString("key_sucursal"), e.getKey());
                if (etp == null) throw new Revisar("El tipo de pago " + e.getKey() + " de Calistenia no tiene equivalente en S-ERP");
                JSONObject etpObj = Tienda.bus("caja", new JSONObject().put("component", "empresa_tipo_pago").put("type", "getByKey").put("key", etp).put("key_empresa", key_empresa)).optJSONObject("data");
                JSONObject tp = tiposCaja == null || etpObj == null ? null : tiposCaja.optJSONObject(etpObj.optString("key_tipo_pago"));
                JSONObject prev = tipos_pago.optJSONObject(etp);
                double v = round(e.getValue() + (prev == null ? 0 : prev.getDouble("monto_nacional")));
                tipos_pago.put(etp, new JSONObject().put("monto_nacional", v).put("monto_extranjera", v).put("tipo_pago", tp == null ? JSONObject.NULL : tp)
                        .put("referencia", "Sistema antiguo" + (referencia.containsKey(e.getKey()) ? " · " + referencia.get(e.getKey()) : ""))
                        .put("descripcion", etpObj == null ? "" : etpObj.optString("descripcion")));
            }
            JSONObject cajaSerp = Tienda.bus("caja", new JSONObject().put("component", "caja").put("type", "getAutomatica")
                    .put("key_punto_venta", suc.getString("key_punto_venta")).put("key_empresa", key_empresa).put("key_usuario", cfg.getString("key_usuario_serp")));
            JSONObject detalle = new JSONObject()
                    .put("cantidad", 1).put("precio_unitario", monto).put("precio_unitario_base", monto).put("detalle", "")
                    .put("descripcion", m.optString("descripcion"))
                    .put("data", new JSONObject().put("tipo_producto", "servicio").put("cupos_disponibles", Math.max(pvus.size(), m.optInt("cantidad_suscriptores", 1)))
                            .put("cupos_suscritos", pvus.size()))
                    .put("key_modelo", key_modelo).put("costos", new JSONArray()).put("suscriptores", suscriptores);
            JSONObject cli = clienteCrm(comprador, key_empresa);
            String nit = pv.optString("nit").isEmpty() || pv.optString("nit").equals("0") ? cli.optString("ci", cli.optString("nit")) : pv.optString("nit");
            String razon = pv.optString("razon_social").isEmpty() ? (cli.optString("nombres") + " " + cli.optString("apellidos")).trim() : pv.optString("razon_social");
            JSONObject data = new JSONObject()
                    .put("descripcion", "").put("observacion", "Sistema antiguo Calistenia · venta " + pv.getString("key").substring(0, 8).toUpperCase())
                    .put("facturar", false).put("factura", JSONObject.NULL).put("tipo_pago", "contado").put("facturar_luego", false)
                    .put("cliente", new JSONObject().put("key", comprador).put("nit", nit).put("razon_social", razon))
                    .put("descuentos", new JSONArray()).put("key_cliente", comprador).put("key_usuario", cfg.getString("key_usuario_serp"))
                    .put("key_caja", cajaSerp.getJSONObject("data").getString("key")).put("key_almacen", suc.getString("key_almacen")).put("key_moneda", cfg.getString("key_moneda"))
                    .put("detalle", new JSONArray().put(detalle)).put("suscripciones", new JSONArray(suscriptores.toString()))
                    .put("tipos_pago", tipos_pago);
            if (fechaContable != null) data.put("fecha_contable", fechaContable)
                    .put("observacion", "Sistema antiguo Calistenia · venta " + pv.getString("key").substring(0, 8).toUpperCase() + " · carga histórica del " + pv.getString("fecha_on").substring(0, 10));
            JSONObject r = SocketCliente.sendSinc("caja", new JSONObject().put("component", "caja_detalle").put("type", "venta").put("estado", "cargando").put("data", data), 60000);
            if (r == null) throw new Exception("Sin respuesta del servicio caja");
            if ("error".equals(r.optString("estado"))) throw new Exception("caja: " + r.optString("error", "error al registrar la venta"));
            String venta = r.optJSONObject("data") == null ? "" : r.getJSONObject("data").optString("key_compra_venta", "");
            if (venta.isEmpty()) throw new Exception("La caja no devolvió la venta");
            actualizar("update sync_cal_evento set key_destino = ? where key = ?", venta, ev.getString("key"));

            // Enlazar cada acceso con su suscripción (cliente + fecha de inicio).
            try (Connection inv = conServicio("inventario"); Connection cv = conServicio("compra_venta")) {
                String[] dets = columnaDe(cv, "select key as json from compra_venta_detalle where key_compra_venta = ? and estado > 0", venta);
                List<JSONObject> subs = q(inv, "select s.key, s.key_cliente, s.key_producto, s.fecha_inicio::date::text as fecha_inicio from suscripcion s join producto p on p.key = s.key_producto "
                        + "where p.key_compra_venta_detalle = any(?) and s.estado > 0", inv.createArrayOf("varchar", dets));
                for (int i = 0; i < pvus.size(); i++) {
                    JSONObject u = pvus.get(i), s = suscriptores.getJSONObject(i);
                    for (JSONObject sub : subs) {
                        if (sub.getString("key_cliente").equals(s.getString("key_cliente")) && sub.getString("fecha_inicio").equals(s.getString("fecha_inicio"))) {
                            guardarAcceso(key_empresa, ORIGEN_CAL, venta, pv.getString("key"), u.getString("key"), sub.getString("key"), sub.getString("key_producto"),
                                    s.getString("fecha_inicio"), s.getString("fecha_fin"), u.getString("key_usuario"), s.getString("key_cliente"), true);
                            subs.remove(sub);
                            break;
                        }
                    }
                }
            }
            return venta;
        }
    }

    /** Venta anulada en Calistenia: se anula su copia en S-ERP. */
    static String anulacionCal(JSONObject cfg, JSONObject ev) throws Exception {
        String key_empresa = cfg.getString("key_empresa"), key_pv = ev.getString("key_origen");
        JSONObject motivo;
        try (Connection cal = conCal(cfg)) {
            motivo = uno(cal, "select estado, coalesce(motivo_anulacion, '') as motivo from paquete_venta where key = ?", key_pv);
        }
        if (motivo == null || motivo.optInt("estado", 1) > 0) throw new Omitir("La venta ya no está anulada en Calistenia");
        JSONObject venta = consulta("select to_json(e) as json from sync_cal_evento e where e.key_empresa = ? and e.origen = 'calistenia' and e.tipo = 'venta' and e.key_origen = ? and e.estado > 0", key_empresa, key_pv);
        if (venta.isEmpty()) {
            JSONObject espejo = consulta("select to_json(e) as json from sync_cal_evento e where e.key_empresa = ? and e.origen = 'serp' and e.tipo = 'venta' and e.estado > 0 "
                    + "and e.data::text like ?", key_empresa, "%" + key_pv + "%");
            if (!espejo.isEmpty())
                throw new Revisar("Anularon en Calistenia una venta que vino de S-ERP (" + espejo.getString("key_origen").substring(0, 8).toUpperCase()
                        + "): si corresponde, anulala también en S-ERP");
            throw new Omitir("La venta no se había sincronizado");
        }
        if (!"HECHO".equals(venta.optString("state"))) {
            actualizar("update sync_cal_evento set state = 'OMITIDO', error = 'Anulada en Calistenia antes de copiarse' where key = ? and state <> 'HECHO'", venta.getString("key"));
            throw new Omitir("La venta no llegó a copiarse a S-ERP");
        }
        String key_cv = venta.getString("key_destino");
        JSONObject r = SocketCliente.sendSinc("caja", new JSONObject().put("component", "caja_detalle").put("type", "anularVenta").put("estado", "cargando")
                .put("key_empresa", key_empresa).put("key_usuario", cfg.getString("key_usuario_serp")).put("key_compra_venta", key_cv)
                .put("datos_anulacion", new JSONObject().put("motivo", "Anulada en el sistema antiguo: " + motivo.optString("motivo")).put("origen", "Sync Calistenia")), 90000);
        if (r == null) throw new Exception("Sin respuesta del servicio caja al anular la venta");
        if ("error".equals(r.optString("estado")) && !String.valueOf(r.optString("error")).toLowerCase().contains("anulad"))
            throw new Exception("caja: " + r.optString("error", "no se pudo anular la venta"));
        actualizar("update sync_cal_acceso set activo = false, fecha_sync = now() where key_empresa = ? and key_paquete_venta = ?", key_empresa, key_pv);
        return key_cv;
    }

    /** Acceso nuevo o cambiado en Calistenia (prórroga, cambio de cliente, participante agregado). */
    static String accesoCal(JSONObject cfg, JSONObject ev) throws Exception {
        String key_empresa = cfg.getString("key_empresa"), key_pvu = ev.getString("key_origen");
        JSONObject a = consulta("select to_json(a) as json from sync_cal_acceso a where a.key_empresa = ? and a.key_pvu = ? and a.estado > 0", key_empresa, key_pvu);
        if (!a.isEmpty()) {
            conciliarAcceso(cfg, a);
            return a.getString("key_suscripcion");
        }
        try (Connection cal = conCal(cfg)) {
            JSONObject u = uno(cal, "select key, key_paquete_venta, key_usuario, fecha_inicio::text as fecha_inicio, fecha_fin::text as fecha_fin, estado from paquete_venta_usuario where key = ?", key_pvu);
            if (u == null) throw new Omitir("El acceso ya no existe");
            JSONObject venta = consulta("select to_json(e) as json from sync_cal_evento e where e.key_empresa = ? and e.origen = 'calistenia' and e.tipo = 'venta' and e.key_origen = ? and e.estado > 0",
                    key_empresa, u.getString("key_paquete_venta"));
            if (!venta.isEmpty() && !"HECHO".equals(venta.optString("state"))) {
                if ("OMITIDO".equals(venta.optString("state"))) throw new Omitir("La venta no se sincroniza: " + venta.optString("error"));
                throw new Exception("Esperando que se copie la venta");
            }
            if (venta.isEmpty() || u.optInt("estado", 1) <= 0) throw new Omitir("Acceso de una venta que no se sincroniza");
            // La venta ya está en S-ERP: si es un participante nuevo, se le crea la suscripción con el mismo producto.
            JSONObject hermano = consulta("select to_json(a) as json from sync_cal_acceso a where a.key_empresa = ? and a.key_paquete_venta = ? and a.estado > 0 limit 1",
                    key_empresa, u.getString("key_paquete_venta"));
            if (hermano.isEmpty()) throw new Omitir("La venta en S-ERP no tiene suscripciones enlazadas");
            JSONObject suc = sucursalSerpDeAcceso(hermano);
            String kc = clienteSerp(cfg, u.getString("key_usuario"));
            String fi = u.getString("fecha_inicio").substring(0, 10), ff = u.getString("fecha_fin").substring(0, 10);
            JSONObject r = Tienda.bus("inventario", new JSONObject().put("version", "1.0").put("component", "suscripcion").put("type", "registro").put("estado", "cargando")
                    .put("key_usuario", cfg.getString("key_usuario_serp")).put("key_empresa", key_empresa)
                    .put("data", new JSONObject().put("key_producto", hermano.getString("key_producto")).put("key_cliente", kc).put("fecha_inicio", fi).put("fecha_fin", ff)
                            .put("key_sucursal", suc == null ? JSONObject.NULL : suc.optString("key_sucursal"))));
            String ks = r.getJSONObject("data").getString("key");
            guardarAcceso(key_empresa, ORIGEN_CAL, hermano.optString("key_compra_venta"), u.getString("key_paquete_venta"), key_pvu, ks, hermano.getString("key_producto"),
                    fi, ff, u.getString("key_usuario"), kc, true);
            return ks;
        }
    }

    /** Venta de S-ERP -> paquete_venta + accesos + movimientos de la caja virtual "S-ERP" en Calistenia. */
    static String ventaSerp(JSONObject cfg, JSONObject ev) throws Exception {
        String key_empresa = cfg.getString("key_empresa"), key_cv = ev.getString("key_origen");
        try (Connection cvc = conServicio("compra_venta"); Connection inv = conServicio("inventario"); Connection cajac = conServicio("caja")) {
            JSONObject cv = uno(cvc, "select key, estado, fecha_on::text as fecha_on, key_sucursal, cliente::text as cliente, key_cliente from compra_venta where key = ?", key_cv);
            if (cv == null) throw new Omitir("La venta ya no existe");
            if (cv.optInt("estado", 1) <= 0) throw new Omitir("Anulada en S-ERP antes de copiarse");
            Map<String, String> paqueteDeModelo = new HashMap<>();
            for (JSONObject p : listaObj("select json_build_object('m', key_modelo, 'p', key_paquete_cal)::text as json from sync_cal_paquete where key_empresa = ? and estado > 0", key_empresa))
                paqueteDeModelo.put(p.getString("m"), p.getString("p"));
            List<JSONObject> dets = new ArrayList<>();
            for (JSONObject d : q(cvc, "select key, key_modelo, cantidad, precio_unitario, coalesce(descuento, 0) as descuento from compra_venta_detalle where key_compra_venta = ? and estado > 0 order by fecha_on", key_cv))
                if (paqueteDeModelo.containsKey(d.optString("key_modelo"))) dets.add(d);
            if (dets.isEmpty()) throw new Omitir("La venta no tiene planes enlazados");
            List<String> keysDet = new ArrayList<>();
            for (JSONObject d : dets) keysDet.add(d.getString("key"));
            List<JSONObject> subs = q(inv, "select s.key, s.key_cliente, s.key_producto, s.key_sucursal, s.fecha_inicio::date::text as fecha_inicio, s.fecha_fin::date::text as fecha_fin, "
                    + "p.key_compra_venta_detalle from suscripcion s join producto p on p.key = s.key_producto where p.key_compra_venta_detalle = any(?) and s.estado > 0 order by s.fecha_on",
                    inv.createArrayOf("varchar", keysDet.toArray()));
            if (subs.isEmpty()) {
                if (cv.getString("fecha_on").compareTo(consultaTexto("select (now() - interval '5 minutes')::text as json")) > 0)
                    throw new Exception("Esperando las suscripciones de la venta");
                throw new Revisar("La venta no tiene suscripciones");
            }
            String key_sucursal = subs.get(0).optString("key_sucursal", cv.optString("key_sucursal"));
            if (key_sucursal.isEmpty()) key_sucursal = cv.optString("key_sucursal");
            JSONObject suc = consulta("select to_json(s) as json from sync_cal_sucursal s where s.key_empresa = ? and s.key_sucursal = ? and s.estado > 0", key_empresa, key_sucursal);
            if (suc.isEmpty() || !suc.optBoolean("activo")) throw new Omitir("Sucursal no activa en el sync");
            if (!suc.optString("fecha_desde").isEmpty() && fechaTexto(cv.getString("fecha_on")).compareTo(fechaTexto(suc.getString("fecha_desde"))) < 0)
                throw new Omitir("Venta anterior a la activación de la sucursal");
            String sucCal = suc.getString("key_sucursal_cal");
            List<JSONObject> pagos = pagosSerp(cajac, key_cv);

            // Usuarios de Calistenia de cada suscriptor (antes de escribir nada).
            Map<String, String> usuarios = new HashMap<>();
            for (JSONObject s : subs) if (!usuarios.containsKey(s.getString("key_cliente"))) usuarios.put(s.getString("key_cliente"), usuarioCal(cfg, s.getString("key_cliente")));
            for (JSONObject p : pagos) tipoPagoCal(key_empresa, p);

            JSONObject cliente = new JSONObject(cv.optString("cliente", "{}").isEmpty() ? "{}" : cv.optString("cliente", "{}"));
            JSONArray paquetesVenta = new JSONArray();
            try (Connection cal = conCal(cfg)) {
                cal.setAutoCommit(false);
                try {
                    marcarOrigen(cal);
                    String fecha = cv.getString("fecha_on");
                    String key_caja = cajaVirtual(cfg, cal, sucCal, fecha);
                    List<double[]> faltante = new ArrayList<>();
                    List<String> pvPorDetalle = new ArrayList<>(), pvuPorDetalle = new ArrayList<>();
                    for (JSONObject d : dets) {
                        String kpv = UUID.randomUUID().toString();
                        double monto = round(d.getDouble("cantidad") * d.getDouble("precio_unitario") - d.optDouble("descuento", 0));
                        ejecutar(cal, "insert into paquete_venta (key, descripcion, key_paquete, fecha_on, estado, monto, observacion, nit, razon_social, correos, is_factura, "
                                + "is_server_serp, key_compra_venta_serp) values (?, '', ?, ?::timestamp, 1, ?, ?, ?, ?, '[]'::json, false, true, ?)",
                                kpv, paqueteDeModelo.get(d.getString("key_modelo")), fecha, monto, "S-ERP venta " + key_cv.substring(0, 8).toUpperCase(),
                                cliente.optString("nit"), cliente.optString("razon_social"), key_cv);
                        String primerPvu = null;
                        for (JSONObject s : subs) {
                            if (!s.getString("key_compra_venta_detalle").equals(d.getString("key"))) continue;
                            String kpvu = UUID.randomUUID().toString();
                            if (primerPvu == null) primerPvu = kpvu;
                            ejecutar(cal, "insert into paquete_venta_usuario (key, key_paquete_venta, key_usuario, fecha_on, estado, descripcion, fecha_inicio, fecha_fin, key_caja) "
                                    + "values (?, ?, ?, ?::timestamp, 1, 'S-ERP', ?::date, ?::date, ?)", kpvu, kpv, usuarios.get(s.getString("key_cliente")), fecha,
                                    s.getString("fecha_inicio"), s.getString("fecha_fin"), key_caja);
                            guardarAcceso(key_empresa, ORIGEN_SERP, key_cv, kpv, kpvu, s.getString("key"), s.getString("key_producto"), s.getString("fecha_inicio"),
                                    s.getString("fecha_fin"), usuarios.get(s.getString("key_cliente")), s.getString("key_cliente"), true);
                        }
                        if (primerPvu == null) primerPvu = "";
                        pvPorDetalle.add(kpv);
                        pvuPorDetalle.add(primerPvu);
                        faltante.add(new double[] { monto });
                        paquetesVenta.put(kpv);
                    }
                    // Pagos al contado de la venta: se reparten entre los paquetes en orden (el resto, p. ej. snacks del mismo carrito, no va).
                    for (JSONObject p : pagos) {
                        double resto = p.getDouble("monto");
                        for (int i = 0; i < pvPorDetalle.size() && resto > 0.001; i++) {
                            double usar = Math.min(resto, faltante.get(i)[0]);
                            if (usar <= 0.001) continue;
                            movimientoVenta(cfg, cal, key_empresa, key_cv, p, round(usar), key_caja, sucCal, pvPorDetalle.get(i), pvuPorDetalle.get(i),
                                    usuarios.get(subs.get(0).getString("key_cliente")), fecha);
                            faltante.get(i)[0] -= usar;
                            resto -= usar;
                        }
                    }
                    cal.commit();
                } catch (Exception e) {
                    cal.rollback();
                    actualizar("update sync_cal_acceso set estado = 0 where key_empresa = ? and key_compra_venta = ? and origen = 'serp'", key_empresa, key_cv);
                    actualizar("update sync_cal_pago set estado = 0 where key_empresa = ? and key_compra_venta = ?", key_empresa, key_cv);
                    throw e;
                }
            }
            actualizar("update sync_cal_evento set data = ?::json, key_sucursal_cal = ? where key = ?",
                    new JSONObject().put("paquetes_venta", paquetesVenta).toString(), sucCal, ev.getString("key"));
            return paquetesVenta.getString(0);
        }
    }

    /** Venta de S-ERP anulada: se anulan sus paquetes en Calistenia como lo hace el sistema antiguo. */
    static String anulacionSerp(JSONObject cfg, JSONObject ev) throws Exception {
        String key_empresa = cfg.getString("key_empresa"), key_cv = ev.getString("key_origen");
        JSONObject venta = consulta("select to_json(e) as json from sync_cal_evento e where e.key_empresa = ? and e.origen = 'serp' and e.tipo = 'venta' and e.key_origen = ? and e.estado > 0", key_empresa, key_cv);
        if (venta.isEmpty() || !"HECHO".equals(venta.optString("state"))) {
            if (!venta.isEmpty()) actualizar("update sync_cal_evento set state = 'OMITIDO', error = 'Anulada en S-ERP antes de copiarse' where key = ? and state <> 'HECHO'", venta.getString("key"));
            throw new Omitir("La venta no se había copiado a Calistenia");
        }
        JSONArray pvs = venta.optJSONObject("data") == null ? new JSONArray() : venta.getJSONObject("data").optJSONArray("paquetes_venta");
        if (pvs == null || pvs.length() == 0) throw new Revisar("No se sabe qué paquetes de Calistenia corresponden a la venta");
        try (Connection cal = conCal(cfg)) {
            cal.setAutoCommit(false);
            try {
                marcarOrigen(cal);
                for (int i = 0; i < pvs.length(); i++) anularPaqueteVentaCal(cfg, cal, pvs.getString(i), "Anulada en S-ERP");
                cal.commit();
            } catch (Exception e) {
                cal.rollback();
                throw e;
            }
        }
        actualizar("update sync_cal_acceso set activo = false, fecha_sync = now() where key_empresa = ? and key_compra_venta = ?", key_empresa, key_cv);
        return pvs.getString(0);
    }

    static void anularPaqueteVentaCal(JSONObject cfg, Connection cal, String key_pv, String motivo) throws Exception {
        String ahora = consultaTexto("select to_char(now(), 'YYYY-MM-DD\"T\"HH24:MI:SS.US') as json");
        List<JSONObject> movs = q(cal, "select m.key, m.key_tipo_pago, m.monto from caja_movimiento m join paquete_venta_usuario u on u.key = m.data->>'key_paquete_venta_usuario' "
                + "where u.key_paquete_venta = ? and m.key_caja_tipo_movimiento = '3' and m.estado = 1", key_pv);
        for (JSONObject m : movs) {
            ejecutar(cal, "update caja_movimiento set estado = 3, data = (data::jsonb || jsonb_build_object('key_usuario_anulacion', ?::text, 'fecha_anulacion', ?::text))::json where key = ?",
                    cfg.optString("key_usuario_cal"), ahora, m.getString("key"));
            if (m.getDouble("monto") > 0 && "235".contains(m.getString("key_tipo_pago"))) {
                JSONObject b = uno(cal, "select key_cuenta_banco, monto, key_sucursal, key_tipo_gasto, tipo_movimiento, key_tipo_pago from cuenta_banco_movimiento "
                        + "where data->>'key_caja_movimiento' = ? and estado > 0 limit 1", m.getString("key"));
                if (b != null)
                    ejecutar(cal, "insert into cuenta_banco_movimiento (key, key_cuenta_banco, key_usuario, monto, fecha_on, estado, data, descripcion, key_sucursal, key_tipo_gasto, "
                            + "tipo_movimiento, key_tipo_pago) values (?, ?, ?, ?, now(), 1, ?::json, 'Anulacion de venta de servicio.', ?, ?, ?, ?)",
                            UUID.randomUUID().toString(), b.optString("key_cuenta_banco"), cfg.optString("key_usuario_cal"), -b.getDouble("monto"),
                            new JSONObject().put("key_caja_movimiento", m.getString("key")).toString(), b.optString("key_sucursal"), b.optString("key_tipo_gasto", null),
                            b.optString("tipo_movimiento"), b.optString("key_tipo_pago"));
            }
        }
        ejecutar(cal, "update paquete_venta_usuario set estado = 0 where key_paquete_venta = ?", key_pv);
        ejecutar(cal, "update paquete_venta set estado = 0, motivo_anulacion = ? where key = ?", motivo, key_pv);
    }

    // ===================== Accesos y pagos posteriores =====================

    /**
     * Compara los accesos enlazados con el último estado acordado y copia el lado que cambió. Incluye las
     * suscripciones nuevas de ventas de S-ERP ya copiadas (p. ej. cada mes de un plan con débito automático) y las
     * anulaciones de ventas de S-ERP.
     */
    static int conciliarAccesos(JSONObject cfg) throws Exception {
        String key_empresa = cfg.getString("key_empresa");
        int n = 0;
        // Ventas de S-ERP copiadas: anuladas y suscripciones nuevas.
        List<JSONObject> ventas = listaObj("select json_build_object('key', e.key, 'cv', e.key_origen, 'data', e.data)::text as json from sync_cal_evento e "
                + "where e.key_empresa = ? and e.origen = 'serp' and e.tipo = 'venta' and e.state = 'HECHO' and e.estado > 0 and e.fecha_on > now() - interval '400 days'", key_empresa);
        if (!ventas.isEmpty()) {
            List<String> keys = new ArrayList<>();
            for (JSONObject v : ventas) keys.add(v.getString("cv"));
            try (Connection cvc = conServicio("compra_venta"); Connection inv = conServicio("inventario")) {
                for (JSONObject a : q(cvc, "select key from compra_venta where key = any(?) and estado <= 0", cvc.createArrayOf("varchar", keys.toArray()))) {
                    evento(key_empresa, ORIGEN_SERP, "anulacion", a.getString("key"), null, 0, false);
                    n++;
                }
                List<JSONObject> nuevas = q(inv, "select s.key, s.key_cliente, s.key_producto, s.fecha_inicio::date::text as fecha_inicio, s.fecha_fin::date::text as fecha_fin from suscripcion s "
                        + "where s.estado > 0 and s.key_producto in (select key_producto from unnest(?::varchar[]) as key_producto) and s.fecha_on > now() - interval '400 days'",
                        inv.createArrayOf("varchar", productosDeAccesos(key_empresa, ORIGEN_SERP)));
                for (JSONObject s : nuevas) {
                    if (!consulta("select to_json(a) as json from sync_cal_acceso a where a.key_empresa = ? and a.key_suscripcion = ? and a.estado > 0", key_empresa, s.getString("key")).isEmpty()) continue;
                    try {
                        accesoNuevoSerp(cfg, s);
                        n++;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        // Accesos vigentes o recientes: comparar con lo acordado.
        for (JSONObject a : listaObj("select to_json(a) as json from sync_cal_acceso a where a.key_empresa = ? and a.estado > 0 and a.activo "
                + "and a.fecha_fin >= current_date - 30", key_empresa)) {
            try {
                if (conciliarAcceso(cfg, a)) n++;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return n;
    }

    /** Suscripción nueva de una venta de S-ERP ya copiada: nuevo acceso en el mismo paquete_venta de Calistenia. */
    static void accesoNuevoSerp(JSONObject cfg, JSONObject s) throws Exception {
        String key_empresa = cfg.getString("key_empresa");
        JSONObject hermano = consulta("select to_json(a) as json from sync_cal_acceso a where a.key_empresa = ? and a.key_producto = ? and a.estado > 0 order by a.fecha_on limit 1",
                key_empresa, s.getString("key_producto"));
        if (hermano.isEmpty()) return;
        String kuc = usuarioCal(cfg, s.getString("key_cliente"));
        try (Connection cal = conCal(cfg)) {
            cal.setAutoCommit(false);
            try {
                marcarOrigen(cal);
                JSONObject u = uno(cal, "select key_caja from paquete_venta_usuario where key = ?", hermano.getString("key_pvu"));
                String kpvu = UUID.randomUUID().toString();
                ejecutar(cal, "insert into paquete_venta_usuario (key, key_paquete_venta, key_usuario, fecha_on, estado, descripcion, fecha_inicio, fecha_fin, key_caja) "
                        + "values (?, ?, ?, now(), 1, 'S-ERP', ?::date, ?::date, ?)", kpvu, hermano.getString("key_paquete_venta"), kuc, s.getString("fecha_inicio"),
                        s.getString("fecha_fin"), u == null ? null : u.optString("key_caja"));
                guardarAcceso(key_empresa, ORIGEN_SERP, hermano.optString("key_compra_venta"), hermano.getString("key_paquete_venta"), kpvu, s.getString("key"),
                        s.getString("key_producto"), s.getString("fecha_inicio"), s.getString("fecha_fin"), kuc, s.getString("key_cliente"), true);
                cal.commit();
            } catch (Exception e) {
                cal.rollback();
                throw e;
            }
        }
    }

    /** Copia el lado que cambió respecto de lo acordado. Devuelve true si escribió algo. */
    static boolean conciliarAcceso(JSONObject cfg, JSONObject a) throws Exception {
        String key_empresa = cfg.getString("key_empresa");
        JSONObject cal, serp;
        try (Connection c = conCal(cfg)) {
            cal = uno(c, "select key_usuario, fecha_inicio::text as fecha_inicio, fecha_fin::text as fecha_fin, (estado > 0) as activo from paquete_venta_usuario where key = ?", a.getString("key_pvu"));
        }
        try (Connection i = conServicio("inventario")) {
            serp = uno(i, "select key_cliente, fecha_inicio::date::text as fecha_inicio, fecha_fin::date::text as fecha_fin, (estado > 0) as activo from suscripcion where key = ?", a.getString("key_suscripcion"));
        }
        if (cal == null || serp == null) return false;
        String acFi = a.optString("fecha_inicio"), acFf = a.optString("fecha_fin");
        boolean acAct = a.optBoolean("activo", true);
        boolean cambioCal = !cal.getString("fecha_inicio").substring(0, 10).equals(acFi) || !cal.getString("fecha_fin").substring(0, 10).equals(acFf)
                || cal.optBoolean("activo") != acAct || !cal.optString("key_usuario").equals(a.optString("key_usuario_cal"));
        boolean cambioSerp = !serp.getString("fecha_inicio").equals(acFi) || !serp.getString("fecha_fin").equals(acFf)
                || serp.optBoolean("activo") != acAct || !serp.optString("key_cliente").equals(a.optString("key_cliente"));
        if (!cambioCal && !cambioSerp) return false;
        boolean ganaCal = cambioCal && (!cambioSerp || ORIGEN_CAL.equals(a.getString("origen")));
        if (ganaCal) {
            String kc = cal.optString("key_usuario").equals(a.optString("key_usuario_cal")) ? a.optString("key_cliente") : clienteSerp(cfg, cal.optString("key_usuario"));
            String fi = cal.getString("fecha_inicio").substring(0, 10), ff = cal.getString("fecha_fin").substring(0, 10);
            Tienda.bus("inventario", new JSONObject().put("version", "1.0").put("component", "suscripcion").put("type", "editar").put("estado", "cargando")
                    .put("key_usuario", cfg.getString("key_usuario_serp")).put("key_empresa", key_empresa)
                    .put("data", new JSONObject().put("key", a.getString("key_suscripcion")).put("key_cliente", kc).put("fecha_inicio", fi).put("fecha_fin", ff)
                            .put("estado", cal.optBoolean("activo") ? 1 : 0)));
            actualizar("update sync_cal_acceso set fecha_inicio = ?::date, fecha_fin = ?::date, activo = ?::boolean, key_usuario_cal = ?, key_cliente = ?, fecha_sync = now() where key = ?",
                    fi, ff, String.valueOf(cal.optBoolean("activo")), cal.optString("key_usuario"), kc, a.getString("key"));
        } else {
            String ku = serp.optString("key_cliente").equals(a.optString("key_cliente")) ? a.optString("key_usuario_cal") : usuarioCal(cfg, serp.optString("key_cliente"));
            try (Connection c = conCal(cfg)) {
                c.setAutoCommit(false);
                marcarOrigen(c);
                ejecutar(c, "update paquete_venta_usuario set key_usuario = ?, fecha_inicio = ?::date, fecha_fin = ?::date, estado = ? where key = ?", ku,
                        serp.getString("fecha_inicio"), serp.getString("fecha_fin"), serp.optBoolean("activo") ? 1 : 0, a.getString("key_pvu"));
                c.commit();
            }
            actualizar("update sync_cal_acceso set fecha_inicio = ?::date, fecha_fin = ?::date, activo = ?::boolean, key_usuario_cal = ?, key_cliente = ?, fecha_sync = now() where key = ?",
                    serp.getString("fecha_inicio"), serp.getString("fecha_fin"), String.valueOf(serp.optBoolean("activo")), ku, serp.optString("key_cliente"), a.getString("key"));
        }
        return true;
    }

    /** Pagos nuevos (cuotas cobradas) de ventas de S-ERP ya copiadas -> movimientos en la caja "S-ERP". */
    static int pagosNuevosSerp(JSONObject cfg) throws Exception {
        String key_empresa = cfg.getString("key_empresa");
        List<JSONObject> ventas = listaObj("select json_build_object('cv', e.key_origen, 'suc', e.key_sucursal_cal, 'data', e.data)::text as json from sync_cal_evento e "
                + "where e.key_empresa = ? and e.origen = 'serp' and e.tipo = 'venta' and e.state = 'HECHO' and e.estado > 0 and e.fecha_on > now() - interval '400 days'", key_empresa);
        if (ventas.isEmpty()) return 0;
        int n = 0;
        try (Connection cajac = conServicio("caja")) {
            for (JSONObject v : ventas) {
                List<JSONObject> pagos = pagosSerp(cajac, v.getString("cv"));
                JSONArray pvs = v.optJSONObject("data") == null ? null : v.getJSONObject("data").optJSONArray("paquetes_venta");
                if (pvs == null || pvs.length() == 0) continue;
                for (JSONObject p : pagos) {
                    if (!consulta("select to_json(x) as json from sync_cal_pago x where x.key_empresa = ? and x.key_caja_detalle = ? and x.estado > 0", key_empresa, p.getString("key")).isEmpty()) continue;
                    try {
                        tipoPagoCal(key_empresa, p);
                        try (Connection cal = conCal(cfg)) {
                            cal.setAutoCommit(false);
                            try {
                                marcarOrigen(cal);
                                JSONObject pvu = uno(cal, "select key, key_usuario from paquete_venta_usuario where key_paquete_venta = ? order by fecha_on desc limit 1", pvs.getString(0));
                                String fecha = p.getString("fecha_on");
                                String key_caja = cajaVirtual(cfg, cal, v.getString("suc"), fecha);
                                movimientoVenta(cfg, cal, key_empresa, v.getString("cv"), p, round(p.getDouble("monto")), key_caja, v.getString("suc"), pvs.getString(0),
                                        pvu == null ? "" : pvu.getString("key"), pvu == null ? "" : pvu.optString("key_usuario"), fecha);
                                cal.commit();
                                n++;
                            } catch (Exception e) {
                                cal.rollback();
                                actualizar("update sync_cal_pago set estado = 0 where key_empresa = ? and key_caja_detalle = ?", key_empresa, p.getString("key"));
                                throw e;
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return n;
    }

    /** Pagos que cuentan como ingreso de la venta (contado y cuotas cobradas; el crédito no es dinero). */
    static List<JSONObject> pagosSerp(Connection cajac, String key_cv) throws Exception {
        return q(cajac, "select cd.key, cd.monto, cd.key_empresa_tipo_pago, cd.key_tipo_pago, cd.tipo, cd.fecha_on::text as fecha_on, coalesce(etp.descripcion, '') as descripcion "
                + "from caja_detalle cd left join empresa_tipo_pago etp on etp.key = cd.key_empresa_tipo_pago where cd.key_compra_venta = ? and cd.estado > 0 "
                + "and cd.tipo in ('venta', 'amortizacion_compra') and cd.monto > 0 and coalesce(cd.key_tipo_pago, '') <> 'credito' order by cd.fecha_on", key_cv);
    }

    /** Movimiento "Venta de servicio" (y para tarjeta/transferencia/QR su traspaso a bancos), como el sistema antiguo. */
    static void movimientoVenta(JSONObject cfg, Connection cal, String key_empresa, String key_cv, JSONObject pago, double monto, String key_caja, String sucCal,
            String key_pv, String key_pvu, String key_usuario_cliente, String fecha) throws Exception {
        String tp = pago.getString("tipo_cal");
        String kmov = UUID.randomUUID().toString();
        JSONObject data = new JSONObject().put("key_tipo_pago", tp).put("monto", String.format(java.util.Locale.US, "%.2f", monto))
                .put("key_usuario", key_usuario_cliente).put("key_paquete_venta_usuario", key_pvu).put("origen", "serp")
                .put("key_caja_detalle", pago.getString("key")).put("referencia", pago.optString("descripcion"));
        // El reporte general cuenta el QR solo con la descripción "Venta de servicios" (como las compras por QR de la app).
        ejecutar(cal, "insert into caja_movimiento (key, key_caja, key_caja_tipo_movimiento, descripcion, monto, fecha_on, estado, key_tipo_pago, data) "
                + "values (?, ?, '3', ?, ?, ?::timestamp, 1, ?, ?::json)", kmov, key_caja, tp.equals("5") ? "Venta de servicios" : "Venta de servicio", monto, fecha, tp, data.toString());
        if (tp.equals("2") || tp.equals("3") || tp.equals("5")) {
            ejecutar(cal, "insert into caja_movimiento (key, key_caja, key_caja_tipo_movimiento, descripcion, monto, fecha_on, estado, key_tipo_pago, data) "
                    + "values (?, ?, '3', 'Traspaso a bancos', ?, ?::timestamp, 1, ?, ?::json)", UUID.randomUUID().toString(), key_caja, -monto, fecha, tp,
                    new JSONObject(data.toString()).put("key_tipo_baco", tp).toString());
            JSONObject cb = uno(cal, "select key_cuenta_banco from sucursal_tipo_pago_cuenta_banco where key_sucursal = ? and key_tipo_pago = ? and estado > 0 limit 1", sucCal, tp);
            if (cb != null)
                ejecutar(cal, "insert into cuenta_banco_movimiento (key, key_cuenta_banco, key_usuario, monto, fecha_on, estado, data, descripcion, key_sucursal, tipo_movimiento, key_tipo_pago) "
                        + "values (?, ?, ?, ?, ?::timestamp, 1, ?::json, 'Ingreso por venta de servicio.', ?, 'ingreso', ?)", UUID.randomUUID().toString(), cb.getString("key_cuenta_banco"),
                        cfg.optString("key_usuario_cal"), monto, fecha, new JSONObject().put("key_caja_movimiento", kmov).toString(), sucCal, tp);
        }
        actualizar("insert into sync_cal_pago (key, key_empresa, key_compra_venta, key_caja_detalle, key_caja_movimiento, key_paquete_venta, key_tipo_pago_cal, monto) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?::numeric) on conflict do nothing", UUID.randomUUID().toString(), key_empresa, key_cv, pago.getString("key"), kmov, key_pv, tp,
                String.valueOf(monto));
    }

    /**
     * Caja virtual "S-ERP" de la sucursal para la fecha (una por día, como la "Caja qr"): se reutiliza la de ese día;
     * las de días anteriores que sigan abiertas se cierran (sin traspasos: el efectivo está en la caja de S-ERP).
     */
    static String cajaVirtual(JSONObject cfg, Connection cal, String sucCal, String fecha) throws Exception {
        String dia = fecha.substring(0, 10);
        ejecutar(cal, "update caja set fecha_off = now() where key_sucursal = ? and tipo = 'serp' and fecha_off is null and estado > 0 and fecha_on::date < ?::date", sucCal, dia);
        JSONObject c = uno(cal, "select key from caja where key_sucursal = ? and tipo = 'serp' and estado > 0 and fecha_on::date = ?::date order by fecha_on limit 1", sucCal, dia);
        if (c != null) return c.getString("key");
        String key = UUID.randomUUID().toString();
        boolean hoy = dia.equals(LocalDate.now(BO).toString());
        ejecutar(cal, "insert into caja (key, key_sucursal, key_usuario, descripcion, fecha_on, fecha_off, estado, tipo) values (?, ?, ?, 'Caja S-ERP', ?::timestamp, "
                + (hoy ? "null" : "?::timestamp") + ", 1, 'serp')", hoy ? new Object[] { key, sucCal, cfg.optString("key_usuario_cal"), fecha }
                        : new Object[] { key, sucCal, cfg.optString("key_usuario_cal"), fecha, fecha });
        ejecutar(cal, "insert into caja_movimiento (key, key_caja, key_caja_tipo_movimiento, descripcion, monto, fecha_on, estado, key_tipo_pago, data) "
                + "values (?, ?, '1', 'apertura', 0, ?::timestamp, 1, '1', '{\"key_tipo_pago\":\"1\"}'::json)", UUID.randomUUID().toString(), key, fecha);
        return key;
    }

    // ===================== Catálogo =====================

    /** El dueño de cada plan (donde nació) manda; los planes nuevos de un lado se crean en el otro. */
    static void catalogo(JSONObject cfg) throws Exception {
        String key_empresa = cfg.getString("key_empresa");
        if (cfg.optString("key_tipo_producto").isEmpty()) throw new Revisar("Falta el tipo de producto de los planes en la configuración");
        try (Connection cal = conCal(cfg); Connection inv = conServicio("inventario")) {
            Map<String, JSONObject> paquetes = new HashMap<>(), modelos = new HashMap<>();
            for (JSONObject p : q(cal, "select key, trim(descripcion) as descripcion, precio, dias, participantes, estado, coalesce(estado_caja, false) as estado_caja, "
                    + "coalesce(estado_app, false) as estado_app from paquete where coalesce(is_molinete, false) and dias > 0")) paquetes.put(p.getString("key"), p);
            for (JSONObject m : q(inv, "select key, trim(descripcion) as descripcion, precio_venta, duracion, duracion_medida, cantidad_suscriptores, coalesce(venta_caja, true) as venta_caja, estado "
                    + "from modelo where key_tipo_producto = ?", cfg.getString("key_tipo_producto"))) modelos.put(m.getString("key"), m);
            java.util.Set<String> pCal = new java.util.HashSet<>(), pSerp = new java.util.HashSet<>();
            for (JSONObject par : listaObj("select to_json(p) as json from sync_cal_paquete p where p.key_empresa = ? and p.estado > 0", key_empresa)) {
                pCal.add(par.getString("key_paquete_cal"));
                pSerp.add(par.getString("key_modelo"));
                JSONObject p = paquetes.get(par.getString("key_paquete_cal")), m = modelos.get(par.getString("key_modelo"));
                if (p == null || m == null) continue;
                if (ORIGEN_SERP.equals(par.getString("origen"))) {
                    int dias = diasDe(m);
                    boolean activo = m.optInt("estado", 1) > 0;
                    if (dias <= 0) continue;
                    if (!p.getString("descripcion").equals(m.getString("descripcion")) || Math.abs(p.getDouble("precio") - m.optDouble("precio_venta", 0)) > 0.001
                            || p.getInt("dias") != dias || p.optInt("participantes", 1) != Math.max(1, m.optInt("cantidad_suscriptores", 1))
                            || p.optBoolean("estado_caja") != m.optBoolean("venta_caja") || (p.optInt("estado", 1) > 0) != activo) {
                        cal.setAutoCommit(false);
                        marcarOrigen(cal);
                        ejecutar(cal, "update paquete set descripcion = ?, precio = ?, dias = ?, participantes = ?, estado_caja = ?, estado = ? where key = ?", m.getString("descripcion"),
                                m.optDouble("precio_venta", 0), dias, Math.max(1, m.optInt("cantidad_suscriptores", 1)), m.optBoolean("venta_caja"), activo ? 1 : 0, p.getString("key"));
                        cal.commit();
                        cal.setAutoCommit(true);
                        actualizar("update sync_cal_paquete set fecha_sync = now() where key = ?", par.getString("key"));
                    }
                } else {
                    boolean activo = p.optInt("estado", 1) > 0;
                    if (!m.getString("descripcion").equals(p.getString("descripcion")) || Math.abs(m.optDouble("precio_venta", 0) - p.getDouble("precio")) > 0.001
                            || m.optInt("duracion", 0) != p.getInt("dias") || !"dias".equals(m.optString("duracion_medida"))
                            || m.optInt("cantidad_suscriptores", 1) != Math.max(1, p.optInt("participantes", 1)) || m.optBoolean("venta_caja") != p.optBoolean("estado_caja")
                            || (m.optInt("estado", 1) > 0) != activo) {
                        ejecutar(inv, "update modelo set descripcion = ?, precio_venta = ?, duracion = ?, duracion_medida = 'dias', cantidad_suscriptores = ?, venta_caja = ?, estado = ?, "
                                + "fecha_edit = now() where key = ?", p.getString("descripcion"), p.getDouble("precio"), p.getInt("dias"), Math.max(1, p.optInt("participantes", 1)),
                                p.optBoolean("estado_caja"), activo ? 1 : 0, m.getString("key"));
                        actualizar("update sync_cal_paquete set fecha_sync = now() where key = ?", par.getString("key"));
                    }
                }
            }
            // Nuevos en Calistenia (vendibles) -> S-ERP.
            for (JSONObject p : paquetes.values()) {
                if (pCal.contains(p.getString("key")) || p.optInt("estado", 1) <= 0 || !(p.optBoolean("estado_caja") || p.optBoolean("estado_app"))) continue;
                String km = UUID.randomUUID().toString().replace("-", "");
                ejecutar(inv, "insert into modelo (key, key_usuario, fecha_on, estado, descripcion, key_marca, key_tipo_producto, precio_compra, precio_venta, duracion, duracion_medida, "
                        + "cantidad_suscriptores, precio_venta_moneda, precio_compra_moneda, venta_caja) values (?, ?, now(), 1, ?, ?, ?, 0, ?, ?, 'dias', ?, ?, ?, ?)", km,
                        cfg.optString("key_usuario_serp"), p.getString("descripcion"), cfg.optString("key_marca", null), cfg.getString("key_tipo_producto"), p.getDouble("precio"),
                        p.getInt("dias"), Math.max(1, p.optInt("participantes", 1)), cfg.optString("key_moneda"), cfg.optString("key_moneda"), p.optBoolean("estado_caja"));
                guardarPar(key_empresa, p.getString("key"), km, ORIGEN_CAL);
                try (Connection c2 = conCal(cfg)) {
                    c2.setAutoCommit(false);
                    marcarOrigen(c2);
                    ejecutar(c2, "update paquete set key_erp = ? where key = ?", km, p.getString("key"));
                    c2.commit();
                }
            }
            // Nuevos en S-ERP (con duración) -> Calistenia, con los servicios y sucursales del paquete plantilla.
            for (JSONObject m : modelos.values()) {
                if (pSerp.contains(m.getString("key")) || m.optInt("estado", 1) <= 0) continue;
                int dias = diasDe(m);
                if (dias <= 0) continue;
                String kp = UUID.randomUUID().toString();
                cal.setAutoCommit(false);
                try {
                    marcarOrigen(cal);
                    ejecutar(cal, "insert into paquete (key, descripcion, precio, dias, participantes, fecha_on, estado, observacion, requiere_motivo, is_molinete, estado_app, estado_caja, key_erp) "
                            + "values (?, ?, ?, ?, ?, now(), 1, 'Creado desde S-ERP', false, true, false, ?, ?)", kp, m.getString("descripcion"), m.optDouble("precio_venta", 0), dias,
                            Math.max(1, m.optInt("cantidad_suscriptores", 1)), m.optBoolean("venta_caja"), m.getString("key"));
                    String plantilla = cfg.optString("key_paquete_plantilla");
                    if (!plantilla.isEmpty()) {
                        ejecutar(cal, "insert into paquete_servicio (key, key_paquete, key_servicio, estado) select gen_random_uuid()::varchar, ?, key_servicio, 1 "
                                + "from paquete_servicio where key_paquete = ? and estado > 0 group by key_servicio", kp, plantilla);
                        ejecutar(cal, "insert into sucursal_paquete (key, key_sucursal, key_paquete, estado) select gen_random_uuid()::varchar, key_sucursal, ?, 1 "
                                + "from sucursal_paquete where key_paquete = ? and estado > 0", kp, plantilla);
                    }
                    cal.commit();
                } catch (Exception e) {
                    cal.rollback();
                    throw e;
                } finally {
                    cal.setAutoCommit(true);
                }
                guardarPar(key_empresa, kp, m.getString("key"), ORIGEN_SERP);
            }
        }
    }

    /** Plan de S-ERP para un paquete de Calistenia que ya no se vende (carga histórica): se crea dado de baja, como allá. */
    static String parHistorico(JSONObject cfg, String key_paquete) throws Exception {
        String key_empresa = cfg.getString("key_empresa");
        synchronized (("sync_cal_par:" + key_empresa + key_paquete).intern()) {
            String km = consultaTexto("select key_modelo as json from sync_cal_paquete where key_empresa = ? and key_paquete_cal = ? and estado > 0", key_empresa, key_paquete);
            if (!km.isEmpty()) return km;
            JSONObject p;
            try (Connection cal = conCal(cfg)) {
                p = uno(cal, "select key, trim(descripcion) as descripcion, precio, dias, participantes, estado, coalesce(estado_caja, false) as estado_caja from paquete "
                        + "where key = ? and coalesce(is_molinete, false) and dias > 0", key_paquete);
            }
            if (p == null) throw new Revisar("El paquete de Calistenia no es un plan sincronizable (sin molinete o sin días)");
            km = UUID.randomUUID().toString().replace("-", "");
            try (Connection inv = conServicio("inventario")) {
                ejecutar(inv, "insert into modelo (key, key_usuario, fecha_on, estado, descripcion, key_marca, key_tipo_producto, precio_compra, precio_venta, duracion, duracion_medida, "
                        + "cantidad_suscriptores, precio_venta_moneda, precio_compra_moneda, venta_caja) values (?, ?, now(), ?, ?, ?, ?, 0, ?, ?, 'dias', ?, ?, ?, ?)", km,
                        cfg.optString("key_usuario_serp"), p.optInt("estado", 1) > 0 ? 1 : 0, p.getString("descripcion"), cfg.optString("key_marca", null), cfg.getString("key_tipo_producto"),
                        p.getDouble("precio"), p.getInt("dias"), Math.max(1, p.optInt("participantes", 1)), cfg.optString("key_moneda"), cfg.optString("key_moneda"), p.optBoolean("estado_caja"));
            }
            guardarPar(key_empresa, key_paquete, km, ORIGEN_CAL);
            return km;
        }
    }

    static int diasDe(JSONObject m) {
        int d = m.optInt("duracion", 0);
        String med = m.optString("duracion_medida", "");
        if (med.equals("dias")) return d;
        if (med.equals("semanas")) return d * 7;
        if (med.equals("meses")) return d * 30;
        if (med.equals("anos") || med.equals("años")) return d * 360;
        return 0;
    }

    static void guardarPar(String key_empresa, String kp, String km, String origen) throws Exception {
        actualizar("insert into sync_cal_paquete (key, key_empresa, key_paquete_cal, key_modelo, origen, fecha_sync) values (?, ?, ?, ?, ?, now()) on conflict do nothing",
                UUID.randomUUID().toString(), key_empresa, kp, km, origen);
    }

    static String modeloDePaquete(JSONObject cfg, String key_paquete) throws Exception {
        return modeloDePaquete(cfg, key_paquete, false);
    }

    /** historico: los paquetes dados de baja (promos viejas) también se enlazan, creando el plan dado de baja en S-ERP. */
    static String modeloDePaquete(JSONObject cfg, String key_paquete, boolean historico) throws Exception {
        String km = consultaTexto("select key_modelo as json from sync_cal_paquete where key_empresa = ? and key_paquete_cal = ? and estado > 0", cfg.getString("key_empresa"), key_paquete);
        if (!km.isEmpty()) return km;
        if (historico) return parHistorico(cfg, key_paquete);
        catalogo(cfg);
        km = consultaTexto("select key_modelo as json from sync_cal_paquete where key_empresa = ? and key_paquete_cal = ? and estado > 0", cfg.getString("key_empresa"), key_paquete);
        if (km.isEmpty()) throw new Revisar("El paquete de Calistenia no es un plan sincronizable (sin molinete o sin días)");
        return km;
    }

    // ===================== Clientes =====================

    /** Cliente de S-ERP para un usuario de Calistenia: enlace guardado, o por CI en el CRM (si no existe se crea). */
    static String clienteSerp(JSONObject cfg, String key_usuario_cal) throws Exception {
        String key_empresa = cfg.getString("key_empresa");
        String kc = consultaTexto("select key_cliente as json from sync_cal_cliente where key_empresa = ? and key_usuario_cal = ? and estado > 0", key_empresa, key_usuario_cal);
        if (!kc.isEmpty()) return kc;
        JSONObject u = usuario.getUsuario(key_usuario_cal);
        if (u == null) throw new Exception("El servicio usuario no devolvió el cliente " + key_usuario_cal);
        String ci = u.optString("CI", "").trim().toUpperCase();
        if (ci.isEmpty() || ci.matches("0+")) throw new Revisar("El cliente " + (u.optString("Nombres") + " " + u.optString("Apellidos")).trim() + " no tiene CI en Calistenia");
        String metodo = "ci";
        kc = Tienda.buscarCliente(key_empresa, cfg.getString("key_usuario_serp"), ci);
        if (kc == null) {
            String nac = u.optString("Fecha nacimiento", "");
            kc = Tienda.crearCliente(key_empresa, cfg.getString("key_usuario_serp"), new JSONObject().put("nombres", u.optString("Nombres")).put("apellidos", u.optString("Apellidos"))
                    .put("ci", ci).put("celular", u.optString("Telefono")).put("email", u.optString("Correo")).put("nacimiento", nac.matches("\\d{4}-\\d{2}-\\d{2}.*") ? nac.substring(0, 10) : ""));
            metodo = "creado_en_serp";
        }
        actualizar("insert into sync_cal_cliente (key, key_empresa, key_usuario_cal, key_cliente, metodo, ci) values (?, ?, ?, ?, ?, ?) on conflict do nothing",
                UUID.randomUUID().toString(), key_empresa, key_usuario_cal, kc, metodo, ci);
        return consultaTexto("select key_cliente as json from sync_cal_cliente where key_empresa = ? and key_usuario_cal = ? and estado > 0", key_empresa, key_usuario_cal);
    }

    /** Usuario de Calistenia para un cliente de S-ERP: enlace guardado, o por CI en el servicio usuario (si no existe se crea). */
    static String usuarioCal(JSONObject cfg, String key_cliente) throws Exception {
        String key_empresa = cfg.getString("key_empresa");
        String ku = consultaTexto("select key_usuario_cal as json from sync_cal_cliente where key_empresa = ? and key_cliente = ? and estado > 0", key_empresa, key_cliente);
        if (!ku.isEmpty()) return ku;
        JSONObject c = clienteCrm(key_cliente, key_empresa);
        String ci = c.optString("ci", "").trim().toUpperCase();
        if (ci.isEmpty()) ci = c.optString("nit", "").trim().toUpperCase();
        String nombre = (c.optString("nombres") + " " + c.optString("apellidos")).trim();
        if (ci.isEmpty() || ci.matches("0+")) throw new Revisar("El cliente " + nombre + " no tiene CI en S-ERP");
        String metodo = "ci";
        JSONObject r = SocketCliente.sendSinc("usuario", new JSONObject().put("component", "usuario").put("version", "2.0").put("type", "getAllCis").put("estado", "cargando")
                .put("cis", new JSONArray().put(ci)), 20000);
        if (r == null || !"exito".equals(r.optString("estado"))) throw new Exception("El servicio usuario no respondió");
        JSONObject d = r.optJSONObject("data");
        ku = null;
        List<String> iguales = new ArrayList<>();
        if (d != null) for (String k : d.keySet()) {
            JSONObject fila = d.optJSONObject(k);
            JSONObject us = fila == null ? null : fila.optJSONObject("usuario");
            if (us != null && ci.equalsIgnoreCase(us.optString("CI").trim()) && !"0".equals(us.optString("estado", "1"))) {
                ku = us.optString("key", k);
                iguales.add((us.optString("Nombres") + " " + us.optString("Apellidos")).trim() + " (" + ku.substring(0, 8) + ")");
            }
        }
        // Varios usuarios con el mismo CI en Calistenia: no se elige al azar, lo decide una persona.
        if (iguales.size() > 1) throw new Revisar("En Calistenia hay " + iguales.size() + " clientes con CI " + ci + ": " + String.join(", ", iguales));
        if (ku == null) {
            JSONObject data = new JSONObject().put("Nombres", c.optString("nombres")).put("Apellidos", c.optString("apellidos")).put("CI", ci)
                    .put("Telefono", c.optString("telefono")).put("Correo", c.optString("correo"));
            if (c.optString("fecha_nacimiento").length() >= 10) data.put("Fecha nacimiento", c.optString("fecha_nacimiento").substring(0, 10));
            JSONObject rr = SocketCliente.sendSinc("usuario", new JSONObject().put("component", "usuario").put("version", "2.0").put("type", "registro").put("estado", "cargando")
                    .put("cabecera", "registro_administrador").put("key_usuario", cfg.optString("key_usuario_cal")).put("key_rol", "d16d800e-5b8d-48ae-8fcb-99392abdf61f")
                    .put("data", data), 30000);
            ku = rr == null || rr.optJSONObject("data") == null ? "" : rr.getJSONObject("data").optString("key");
            if (ku.isEmpty()) throw new Exception("No se pudo crear el cliente " + nombre + " en Calistenia: " + (rr == null ? "sin respuesta" : rr.optString("error", rr.optString("estado"))));
            metodo = "creado_en_cal";
        }
        actualizar("insert into sync_cal_cliente (key, key_empresa, key_usuario_cal, key_cliente, metodo, ci) values (?, ?, ?, ?, ?, ?) on conflict do nothing",
                UUID.randomUUID().toString(), key_empresa, ku, key_cliente, metodo, ci);
        String guardado = consultaTexto("select key_usuario_cal as json from sync_cal_cliente where key_empresa = ? and key_cliente = ? and estado > 0", key_empresa, key_cliente);
        if (guardado.isEmpty()) throw new Revisar("El usuario de Calistenia con CI " + ci + " ya está enlazado con otro cliente de S-ERP");
        return guardado;
    }

    static JSONObject clienteCrm(String key_cliente, String key_empresa) throws Exception {
        try (Connection crm = conServicio("crm")) {
            JSONObject c = uno(crm, "select key, nombres, apellidos, ci, nit, telefono, correo, fecha_nacimiento::text as fecha_nacimiento from cliente where key = ?", key_cliente);
            if (c == null) throw new Exception("Cliente " + key_cliente + " no encontrado en el CRM");
            return c;
        }
    }

    // ===================== Mapeos =====================

    static JSONObject sucursalCal(String key_empresa, String key_sucursal_cal) throws Exception {
        JSONObject s = consulta("select to_json(s) as json from sync_cal_sucursal s where s.key_empresa = ? and s.key_sucursal_cal = ? and s.estado > 0", key_empresa, key_sucursal_cal);
        return s.isEmpty() ? null : s;
    }

    static JSONObject sucursalSerpDeAcceso(JSONObject acceso) throws Exception {
        try (Connection inv = conServicio("inventario")) {
            return uno(inv, "select key_sucursal from suscripcion where key = ?", acceso.getString("key_suscripcion"));
        }
    }

    /** Tipo de pago de S-ERP para el de Calistenia: primero el de la sucursal, después el general. */
    static String tipoPagoSerp(String key_empresa, String sucCal, String tpCal) throws Exception {
        String k = consultaTexto("select key_empresa_tipo_pago as json from sync_cal_tipo_pago where key_empresa = ? and estado > 0 and sentido in ('ambos','cal_a_serp') "
                + "and key_tipo_pago_cal = ? and key_sucursal_cal = ? limit 1", key_empresa, tpCal, sucCal);
        if (!k.isEmpty()) return k;
        k = consultaTexto("select key_empresa_tipo_pago as json from sync_cal_tipo_pago where key_empresa = ? and estado > 0 and sentido in ('ambos','cal_a_serp') "
                + "and key_tipo_pago_cal = ? and key_sucursal_cal is null limit 1", key_empresa, tpCal);
        return k.isEmpty() ? null : k;
    }

    /** Pone en el pago su tipo de Calistenia (tipo_cal). Efectivo de cualquier cuenta de caja = "1". */
    static void tipoPagoCal(String key_empresa, JSONObject pago) throws Exception {
        String t = consultaTexto("select key_tipo_pago_cal as json from sync_cal_tipo_pago where key_empresa = ? and estado > 0 and sentido in ('ambos','serp_a_cal') "
                + "and key_empresa_tipo_pago = ? limit 1", key_empresa, pago.optString("key_empresa_tipo_pago"));
        if (t.isEmpty() && "caja".equals(pago.optString("key_tipo_pago"))) t = "1";
        if (t.isEmpty()) throw new Revisar("El tipo de pago \"" + pago.optString("descripcion") + "\" de S-ERP no tiene equivalente en Calistenia");
        pago.put("tipo_cal", t);
    }

    static void guardarAcceso(String key_empresa, String origen, String key_cv, String key_pv, String key_pvu, String key_sus, String key_producto, String fi, String ff,
            String key_usuario_cal, String key_cliente, boolean activo) throws Exception {
        actualizar("insert into sync_cal_acceso (key, key_empresa, origen, key_compra_venta, key_paquete_venta, key_pvu, key_suscripcion, key_producto, fecha_inicio, fecha_fin, "
                + "key_usuario_cal, key_cliente, activo) values (?, ?, ?, ?, ?, ?, ?, ?, ?::date, ?::date, ?, ?, ?::boolean) on conflict do nothing", UUID.randomUUID().toString(),
                key_empresa, origen, key_cv, key_pv, key_pvu, key_sus, key_producto, fi, ff, key_usuario_cal, key_cliente, String.valueOf(activo));
    }

    static String[] productosDeAccesos(String key_empresa, String origen) throws Exception {
        return columna("select distinct key_producto as json from sync_cal_acceso where key_empresa = ? and origen = ? and estado > 0 and key_producto is not null", key_empresa, origen);
    }

    // ===================== Panel =====================

    static JSONObject config(String key_empresa) throws Exception {
        JSONObject c = consulta("select to_json(c) as json from sync_cal_config c where c.key_empresa = ? and c.estado > 0", key_empresa);
        if (c.isEmpty()) throw new Exception("La sincronización con Calistenia no está configurada para esta empresa");
        return c;
    }

    static JSONObject estado(String key_empresa) throws Exception {
        JSONObject c = consulta("select to_json(c) as json from sync_cal_config c where c.key_empresa = ? and c.estado > 0", key_empresa);
        if (c.isEmpty()) return new JSONObject().put("configurada", false);
        JSONObject out = new JSONObject().put("configurada", true)
                .put("config", new JSONObject().put("activo", c.optBoolean("activo")).put("db_name", c.optString("db_name")).put("db_host", c.optString("db_host"))
                        .put("observacion", c.optString("observacion")).put("ultimo_serp", c.opt("ultimo_serp")).put("ultimo_outbox", c.opt("ultimo_outbox")));
        out.put("tarea", consulta("select json_build_object('fecha_inicio', fecha_inicio, 'fecha_fin', fecha_fin, 'procesados', procesados, 'errores', errores, 'error', error, "
                + "'atrasada', fecha_fin < now() - interval '10 minutes')::text as json from sync_cal_tarea where key_empresa = ?", key_empresa));
        out.put("eventos", consulta("select coalesce(json_object_agg(state, n), '{}')::text as json from (select state, count(*) n from sync_cal_evento where key_empresa = ? and estado > 0 group by state) x", key_empresa));
        out.put("totales", consulta("select json_build_object('sucursales', (select count(*) from sync_cal_sucursal where key_empresa = ? and estado > 0), "
                + "'sucursales_activas', (select count(*) from sync_cal_sucursal where key_empresa = ? and estado > 0 and activo), "
                + "'paquetes', (select count(*) from sync_cal_paquete where key_empresa = ? and estado > 0), "
                + "'clientes', (select count(*) from sync_cal_cliente where key_empresa = ? and estado > 0), "
                + "'accesos', (select count(*) from sync_cal_acceso where key_empresa = ? and estado > 0))::text as json", key_empresa, key_empresa, key_empresa, key_empresa, key_empresa));
        return out;
    }

    static JSONObject guardarConfig(String key_empresa, JSONObject d) throws Exception {
        JSONObject c = config(key_empresa);
        if (d.has("activo")) {
            boolean activo = d.getBoolean("activo");
            // Al encender, las ventas de S-ERP se buscan desde ese momento (lo anterior es carga inicial aparte).
            actualizar("update sync_cal_config set activo = ?::boolean, ultimo_serp = case when ?::boolean and not activo then now() else ultimo_serp end where key = ?",
                    String.valueOf(activo), String.valueOf(activo), c.getString("key"));
        }
        return estado(key_empresa);
    }

    static JSONArray sucursales(String key_empresa) throws Exception {
        JSONArray out = new JSONArray();
        JSONObject c = config(key_empresa);
        Map<String, String> nombresCal = new HashMap<>();
        try (Connection cal = conCal(c)) {
            for (JSONObject s : q(cal, "select key, trim(descripcion) as descripcion from sucursal")) nombresCal.put(s.getString("key"), s.getString("descripcion"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        Map<String, String> nombresSerp = new HashMap<>(), pvs = new HashMap<>();
        for (Object o : Tienda.sucursales(key_empresa)) nombresSerp.put(((JSONObject) o).getString("key"), ((JSONObject) o).optString("nombre"));
        for (JSONObject s : listaObj("select to_json(s) as json from sync_cal_sucursal s where s.key_empresa = ? and s.estado > 0", key_empresa)) {
            s.put("sucursal_cal", nombresCal.getOrDefault(s.getString("key_sucursal_cal"), s.getString("key_sucursal_cal")));
            s.put("sucursal", nombresSerp.getOrDefault(s.getString("key_sucursal"), s.getString("key_sucursal")));
            out.put(s);
        }
        return out;
    }

    static JSONArray guardarSucursal(String key_empresa, JSONObject d) throws Exception {
        String key = d.getString("key");
        if (d.has("activo")) actualizar("update sync_cal_sucursal set activo = ?::boolean, fecha_desde = case when ?::boolean and not activo then now() else fecha_desde end "
                + "where key = ? and key_empresa = ?", String.valueOf(d.getBoolean("activo")), String.valueOf(d.getBoolean("activo")), key, key_empresa);
        if (d.has("molinete")) {
            String m = d.getString("molinete");
            if (!m.equals("calistenia") && !m.equals("serp")) throw new Exception("molinete inválido");
            actualizar("update sync_cal_sucursal set molinete = ? where key = ? and key_empresa = ?", m, key, key_empresa);
        }
        if (d.has("key_punto_venta")) actualizar("update sync_cal_sucursal set key_punto_venta = ? where key = ? and key_empresa = ?", d.getString("key_punto_venta"), key, key_empresa);
        if (d.has("key_almacen")) actualizar("update sync_cal_sucursal set key_almacen = ? where key = ? and key_empresa = ?", d.getString("key_almacen"), key, key_empresa);
        return sucursales(key_empresa);
    }

    static JSONArray paquetes(String key_empresa) throws Exception {
        JSONObject c = config(key_empresa);
        Map<String, JSONObject> pcal = new HashMap<>(), mserp = new HashMap<>();
        try (Connection cal = conCal(c)) {
            for (JSONObject p : q(cal, "select key, trim(descripcion) as descripcion, precio, dias, participantes, estado, estado_caja, estado_app from paquete")) pcal.put(p.getString("key"), p);
        }
        try (Connection inv = conServicio("inventario")) {
            for (JSONObject m : q(inv, "select key, trim(descripcion) as descripcion, precio_venta, duracion, duracion_medida, venta_caja, estado from modelo where key_tipo_producto = ?",
                    c.optString("key_tipo_producto"))) mserp.put(m.getString("key"), m);
        }
        JSONArray out = new JSONArray();
        for (JSONObject par : listaObj("select to_json(p) as json from sync_cal_paquete p where p.key_empresa = ? and p.estado > 0", key_empresa))
            out.put(par.put("cal", pcal.getOrDefault(par.getString("key_paquete_cal"), new JSONObject())).put("serp", mserp.getOrDefault(par.getString("key_modelo"), new JSONObject())));
        return out;
    }

    /**
     * Conciliación del día por sucursal: lo vendido en las cajas propias de cada sistema tiene que coincidir con lo que
     * entró al otro por el sync (Calistenia propias = S-ERP en el punto de venta "sistema antiguo"; S-ERP = caja "S-ERP").
     */
    static JSONArray conciliacion(JSONObject cfg, String fecha) throws Exception {
        if (!fecha.matches("\\d{4}-\\d{2}-\\d{2}")) throw new Exception("fecha inválida");
        String key_empresa = cfg.getString("key_empresa");
        Map<String, JSONObject> filas = new LinkedHashMap<>();
        for (JSONObject s : listaObj("select to_json(s) as json from sync_cal_sucursal s where s.key_empresa = ? and s.estado > 0", key_empresa))
            filas.put(s.getString("key_sucursal_cal"), new JSONObject().put("key_sucursal_cal", s.getString("key_sucursal_cal")).put("key_sucursal", s.getString("key_sucursal"))
                    .put("activo", s.optBoolean("activo")).put("desde", fechaTexto(s.optString("fecha_desde"))).put("pv", s.optString("key_punto_venta")).put("cal_propias", 0.0).put("cal_desde_serp", 0.0).put("serp_desde_cal", 0.0).put("serp_propias", 0.0));
        try (Connection cal = conCal(cfg)) {
            for (JSONObject r : q(cal, "select c.key_sucursal, coalesce(c.tipo, '') = 'serp' as espejo, m.monto, pv.fecha_on::text as fecha_on, trim(s.descripcion) as nombre from caja_movimiento m "
                    + "join caja c on c.key = m.key_caja join sucursal s on s.key = c.key_sucursal join paquete_venta_usuario u on u.key = m.data->>'key_paquete_venta_usuario' "
                    + "join paquete_venta pv on pv.key = u.key_paquete_venta where m.key_caja_tipo_movimiento = '3' and m.monto > 0 and m.estado = 1 and pv.estado > 0 "
                    + "and m.descripcion like 'Venta de servicio%' and m.fecha_on::date = ?::date", fecha)) {
                JSONObject f = filas.get(r.getString("key_sucursal"));
                if (f == null) continue;
                f.put("sucursal", r.optString("nombre"));
                // Solo lo vendido con la sucursal activa (lo anterior no se sincroniza).
                if (!f.optBoolean("activo") || fechaTexto(r.getString("fecha_on")).compareTo(f.optString("desde")) < 0) continue;
                String campo = r.optBoolean("espejo") ? "cal_desde_serp" : "cal_propias";
                f.put(campo, round(f.getDouble(campo) + r.optDouble("monto", 0)));
            }
        }
        String[] modelos = columna("select key_modelo as json from sync_cal_paquete where key_empresa = ? and estado > 0", key_empresa);
        try (Connection cvc = conServicio("compra_venta"); Connection cajac = conServicio("caja"); Connection inv = conServicio("inventario")) {
            Map<String, String> pvDeCaja = new HashMap<>();
            List<JSONObject> dets = q(cvc, "select cv.key_caja, d.key, cv.key_sucursal, cv.fecha_on::text as fecha_on, d.cantidad * d.precio_unitario - coalesce(d.descuento, 0) as total from compra_venta cv "
                    + "join compra_venta_detalle d on d.key_compra_venta = cv.key where cv.key_empresa = ? and cv.tipo = 'venta' and cv.estado > 0 and d.estado > 0 "
                    + "and d.key_modelo = any(?) and cv.fecha_on::date = ?::date", key_empresa, cvc.createArrayOf("varchar", modelos), fecha);
            for (JSONObject d : dets) {
                String kc = d.optString("key_caja");
                if (!pvDeCaja.containsKey(kc)) {
                    JSONObject c = uno(cajac, "select key_punto_venta from caja where key = ?", kc);
                    pvDeCaja.put(kc, c == null ? "" : c.optString("key_punto_venta"));
                }
                JSONObject sub = uno(inv, "select s.key_sucursal from suscripcion s join producto p on p.key = s.key_producto where p.key_compra_venta_detalle = ? limit 1", d.getString("key"));
                String ks = sub == null || sub.optString("key_sucursal").isEmpty() ? d.optString("key_sucursal") : sub.getString("key_sucursal");
                for (JSONObject f : filas.values()) {
                    if (!f.optBoolean("activo") || fechaTexto(d.getString("fecha_on")).compareTo(f.optString("desde")) < 0) continue;
                    if (pvDeCaja.get(kc).equals(f.optString("pv"))) {
                        f.put("serp_desde_cal", round(f.getDouble("serp_desde_cal") + d.optDouble("total", 0)));
                        break;
                    }
                    if (ks.equals(f.getString("key_sucursal"))) {
                        f.put("serp_propias", round(f.getDouble("serp_propias") + d.optDouble("total", 0)));
                        break;
                    }
                }
            }
        }
        JSONArray out = new JSONArray();
        for (JSONObject f : filas.values()) out.put(f);
        return out;
    }

    // ===================== Carga histórica =====================

    /** Ventas de Calistenia del mes (sucursales activas) que todavía no están en S-ERP, y el estado de las gestiones. */
    static JSONObject historicoRevisar(JSONObject cfg, String mes) throws Exception {
        if (!mes.matches("\\d{4}-\\d{2}")) throw new Exception("mes inválido (AAAA-MM)");
        String key_empresa = cfg.getString("key_empresa");
        JSONObject out = new JSONObject().put("mes", mes);
        Map<String, JSONObject> porSucursal = new LinkedHashMap<>();
        for (JSONObject v : ventasHistoricas(cfg, mes)) {
            JSONObject f = porSucursal.computeIfAbsent(v.getString("key_sucursal"), k -> new JSONObject().put("sucursal", v.optString("sucursal")).put("ventas", 0).put("monto", 0.0)
                    .put("ya_copiadas", 0));
            if (v.optBoolean("copiada")) f.put("ya_copiadas", f.getInt("ya_copiadas") + 1);
            else f.put("ventas", f.getInt("ventas") + 1).put("monto", round(f.getDouble("monto") + v.optDouble("monto", 0)));
        }
        out.put("sucursales", new JSONArray(porSucursal.values()));
        try (Connection conta = conServicio("contabilidad")) {
            out.put("gestion_mes", uno(conta, "select key, estado, fecha::text as fecha, (select max(fecha)::text from asiento_contable a where a.key_gestion = g.key and a.estado > 0) as ultimo_comprobante "
                    + "from gestion g where key_empresa = ? and to_char(fecha, 'YYYY-MM') = ? and estado > 0 order by fecha_on limit 1", key_empresa, mes));
            out.put("gestion_abierta", uno(conta, "select key, fecha::text as fecha from gestion where key_empresa = ? and estado = 2 limit 1", key_empresa));
        }
        out.put("fecha_contable", ultimoDia(mes) + " 23:59:59");
        return out;
    }

    /**
     * Carga un mes: abre su gestión (la crea si no existe), registra cada venta con fecha contable del último día del
     * mes y SIEMPRE vuelve a abrir la gestión que estaba abierta. Mientras dura, S-ERP no puede vender (una sola gestión).
     */
    static JSONObject historicoCargar(JSONObject cfg, String mes, String key_usuario) throws Exception {
        if (!mes.matches("\\d{4}-\\d{2}")) throw new Exception("mes inválido (AAAA-MM)");
        if (mes.compareTo(LocalDate.now(BO).toString().substring(0, 7)) >= 0) throw new Exception("Solo meses anteriores al actual");
        String key_empresa = cfg.getString("key_empresa");
        synchronized (("sync_cal:" + key_empresa).intern()) {
            List<JSONObject> ventas = new ArrayList<>();
            for (JSONObject v : ventasHistoricas(cfg, mes)) if (!v.optBoolean("copiada")) ventas.add(v);
            if (ventas.isEmpty()) return new JSONObject().put("cargadas", 0).put("errores", 0).put("mensaje", "No hay ventas pendientes en ese mes");
            String fechaContable = ultimoDia(mes) + " 23:59:59";
            String abierta, delMes;
            try (Connection conta = conServicio("contabilidad")) {
                JSONObject a = uno(conta, "select key from gestion where key_empresa = ? and estado = 2 limit 1", key_empresa);
                if (a == null) throw new Exception("La empresa no tiene una gestión abierta");
                abierta = a.getString("key");
                JSONObject g = uno(conta, "select key from gestion where key_empresa = ? and to_char(fecha, 'YYYY-MM') = ? and estado > 0 order by fecha_on limit 1", key_empresa, mes);
                if (g == null) {
                    delMes = UUID.randomUUID().toString();
                    ejecutar(conta, "insert into gestion (key, key_usuario, fecha_on, estado, fecha, key_empresa, descripcion) values (?, ?, now(), 1, ?::timestamp, ?, 'Creada por la carga histórica')",
                            delMes, key_usuario, mes + "-01", key_empresa);
                } else delMes = g.getString("key");
                // Abrir la del mes (cierra la actual mientras dura la carga).
                ejecutar(conta, "update gestion set estado = 1 where key_empresa = ? and estado = 2", key_empresa);
                ejecutar(conta, "update gestion set estado = 2 where key = ?", delMes);
            }
            int ok = 0, errores = 0;
            String ultimoError = null;
            // Planes dados de baja (promos viejas): se enlazan y se activan solo mientras dura la carga, sin venta en caja.
            Map<String, JSONObject> reactivados = new HashMap<>();
            try {
                java.util.Set<String> paquetes = new java.util.LinkedHashSet<>();
                for (JSONObject v : ventas) paquetes.add(v.getString("key_paquete"));
                try (Connection inv = conServicio("inventario")) {
                    for (String kp : paquetes) {
                        String km;
                        try {
                            km = modeloDePaquete(cfg, kp, true);
                        } catch (Exception e) {
                            continue; // la venta queda en REVISAR con el motivo
                        }
                        JSONObject m = uno(inv, "select key, estado, coalesce(venta_caja, true) as venta_caja from modelo where key = ?", km);
                        if (m != null && m.optInt("estado", 1) <= 0) {
                            reactivados.put(km, m);
                            ejecutar(inv, "update modelo set estado = 1, venta_caja = false where key = ?", km);
                        }
                    }
                }
                for (JSONObject v : ventas) {
                    evento(key_empresa, ORIGEN_CAL, "venta", v.getString("key"), new JSONObject().put("historico", mes), 0, false);
                    JSONObject ev = consulta("select to_json(e) as json from sync_cal_evento e where e.key_empresa = ? and e.origen = 'calistenia' and e.tipo = 'venta' and e.key_origen = ? and e.estado > 0",
                            key_empresa, v.getString("key"));
                    if ("HECHO".equals(ev.optString("state"))) continue;
                    try {
                        String destino = ventaCal(cfg, ev, fechaContable);
                        actualizar("update sync_cal_evento set state = 'HECHO', key_destino = ?, error = null, fecha_proceso = now(), data = ?::json where key = ?", destino,
                                new JSONObject().put("historico", mes).toString(), ev.getString("key"));
                        ok++;
                    } catch (Omitir e) {
                        actualizar("update sync_cal_evento set state = 'OMITIDO', error = ?, fecha_proceso = now() where key = ?", recortar(e.getMessage(), 250), ev.getString("key"));
                    } catch (Exception e) {
                        errores++;
                        ultimoError = v.getString("key").substring(0, 8) + ": " + e.getMessage();
                        actualizar("update sync_cal_evento set state = 'REVISAR', intentos = intentos + 1, error = ?, fecha_proceso = now() where key = ?",
                                recortar("Carga histórica " + mes + ": " + e.getMessage(), 250), ev.getString("key"));
                    }
                }
            } finally {
                try (Connection inv = conServicio("inventario")) {
                    for (JSONObject m : reactivados.values())
                        ejecutar(inv, "update modelo set estado = ?, venta_caja = ? where key = ?", m.optInt("estado", 0), m.optBoolean("venta_caja"), m.getString("key"));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                // Volver a abrir la gestión que estaba abierta, pase lo que pase.
                try (Connection conta = conServicio("contabilidad")) {
                    ejecutar(conta, "update gestion set estado = 1 where key_empresa = ? and estado = 2", key_empresa);
                    ejecutar(conta, "update gestion set estado = 2 where key = ?", abierta);
                }
            }
            return new JSONObject().put("cargadas", ok).put("errores", errores).put("ultimo_error", ultimoError == null ? JSONObject.NULL : ultimoError)
                    .put("fecha_contable", fechaContable);
        }
    }

    /** Ventas de Calistenia del mes en las sucursales activas del sync (cajas propias, no anuladas). */
    static List<JSONObject> ventasHistoricas(JSONObject cfg, String mes) throws Exception {
        String key_empresa = cfg.getString("key_empresa");
        String[] sucs = columna("select key_sucursal_cal as json from sync_cal_sucursal where key_empresa = ? and estado > 0 and activo", key_empresa);
        java.util.Set<String> copiadas = new java.util.HashSet<>(java.util.Arrays.asList(columna(
                "select key_origen as json from sync_cal_evento where key_empresa = ? and origen = 'calistenia' and tipo = 'venta' and state = 'HECHO' and estado > 0", key_empresa)));
        List<JSONObject> out = new ArrayList<>();
        if (sucs.length == 0) return out;
        try (Connection cal = conCal(cfg)) {
            for (JSONObject v : q(cal, "select distinct on (pv.key) pv.key, pv.key_paquete, pv.monto, pv.fecha_on::text as fecha_on, c.key_sucursal, trim(s.descripcion) as sucursal "
                    + "from paquete_venta pv join paquete_venta_usuario u on u.key_paquete_venta = pv.key and u.estado > 0 join caja c on c.key = u.key_caja "
                    + "join sucursal s on s.key = c.key_sucursal where pv.estado > 0 and coalesce(pv.is_server_serp, false) = false and coalesce(c.tipo, '') <> 'serp' "
                    + "and to_char(pv.fecha_on, 'YYYY-MM') = ? and c.key_sucursal = any(?) order by pv.key, u.fecha_on", mes, cal.createArrayOf("varchar", sucs))) {
                v.put("copiada", copiadas.contains(v.getString("key")));
                out.add(v);
            }
        }
        out.sort((a, b) -> a.getString("fecha_on").compareTo(b.getString("fecha_on")));
        return out;
    }

    static String ultimoDia(String mes) {
        return LocalDate.parse(mes + "-01").plusMonths(1).minusDays(1).toString();
    }

    // ===================== Infra =====================

    static void evento(String key_empresa, String origen, String tipo, String key_origen, JSONObject data, int segundos, boolean rearmar) throws Exception {
        if (rearmar)
            actualizar("insert into sync_cal_evento (key, key_empresa, origen, tipo, key_origen, data, proximo_intento) values (?, ?, ?, ?, ?, ?::json, now() + (? || ' seconds')::interval) "
                    + "on conflict (key_empresa, origen, tipo, key_origen) where estado > 0 do update set state = 'PENDIENTE', intentos = 0, error = null, "
                    + "proximo_intento = excluded.proximo_intento where sync_cal_evento.state <> 'PENDIENTE'", UUID.randomUUID().toString(), key_empresa, origen, tipo, key_origen,
                    data == null ? null : data.toString(), String.valueOf(segundos));
        else
            actualizar("insert into sync_cal_evento (key, key_empresa, origen, tipo, key_origen, data, proximo_intento) values (?, ?, ?, ?, ?, ?::json, now() + (? || ' seconds')::interval) "
                    + "on conflict (key_empresa, origen, tipo, key_origen) where estado > 0 do nothing", UUID.randomUUID().toString(), key_empresa, origen, tipo, key_origen,
                    data == null ? null : data.toString(), String.valueOf(segundos));
    }

    static void registrarTarea(String key_empresa, int procesados, int errores, String error, String inicio) {
        try {
            actualizar("insert into sync_cal_tarea (key_empresa, fecha_inicio, fecha_fin, procesados, errores, error) values (?, coalesce(?::timestamp, now()), now(), ?::integer, ?::integer, ?) "
                    + "on conflict (key_empresa) do update set fecha_inicio = excluded.fecha_inicio, fecha_fin = now(), procesados = excluded.procesados, errores = excluded.errores, "
                    + "error = case when excluded.errores > 0 then excluded.error else sync_cal_tarea.error end", key_empresa, inicio, String.valueOf(procesados), String.valueOf(errores),
                    recortar(error, 250));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void marcarOrigen(Connection cal) throws Exception {
        try (Statement st = cal.createStatement()) {
            st.execute("set local sync.origen = 'serp'");
        }
    }

    static Connection conCal(JSONObject cfg) throws Exception {
        JSONObject db = SConfig.getJSON("data_base");
        return DriverManager.getConnection("jdbc:postgresql://" + cfg.getString("db_host") + ":" + cfg.optInt("db_port", 5432) + "/" + cfg.getString("db_name"),
                db.getString("user"), db.getString("pass"));
    }

    /** Bases de los demás servicios de S-ERP (red interna). Se puede cambiar con "sync_calistenia_dbs" en config.json. */
    static Connection conServicio(String servicio) throws Exception {
        JSONObject def = new JSONObject().put("caja", "192.168.5.45").put("compra_venta", "192.168.5.41").put("inventario", "192.168.5.39").put("crm", "192.168.5.51")
                .put("contabilidad", "192.168.5.11");
        JSONObject cfg = null;
        try {
            cfg = SConfig.getJSON().optJSONObject("sync_calistenia_dbs");
        } catch (Exception e) {
        }
        String host = cfg != null && cfg.has(servicio) ? cfg.getString(servicio) : def.getString(servicio);
        JSONObject db = SConfig.getJSON("data_base");
        return DriverManager.getConnection("jdbc:postgresql://" + host + ":5432/servisofts." + servicio, db.getString("user"), db.getString("pass"));
    }

    static List<JSONObject> q(Connection c, String sql, Object... params) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                List<JSONObject> out = new ArrayList<>();
                ResultSetMetaData md = rs.getMetaData();
                while (rs.next()) {
                    JSONObject o = new JSONObject();
                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        Object v = rs.getObject(i);
                        if (v == null) continue;
                        if (v instanceof Number || v instanceof Boolean) o.put(md.getColumnLabel(i), v);
                        else o.put(md.getColumnLabel(i), rs.getString(i));
                    }
                    out.add(o);
                }
                return out;
            }
        }
    }

    static JSONObject uno(Connection c, String sql, Object... params) throws Exception {
        List<JSONObject> l = q(c, sql, params);
        return l.isEmpty() ? null : l.get(0);
    }

    static String[] columnaDe(Connection c, String sql, Object... params) throws Exception {
        List<String> out = new ArrayList<>();
        for (JSONObject o : q(c, sql, params)) out.add(o.optString("json"));
        return out.toArray(new String[0]);
    }

    static void ejecutar(Connection c, String sql, Object... params) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            ps.executeUpdate();
        }
    }

    // serp (pool del framework)
    static JSONObject permisosDe(String key_usuario, String key_empresa) throws Exception {
        JSONObject resp = SocketCliente.sendSinc("roles_permisos", new JSONObject().put("version", "1.0").put("component", "usuarioPage").put("type", "getAll")
                .put("estado", "cargando").put("key_usuario", key_usuario).put("key_empresa", key_empresa), 20000);
        JSONObject page = resp == null || resp.optJSONObject("data") == null ? null : resp.getJSONObject("data").optJSONObject(PAGE);
        JSONObject permisos = page == null ? null : page.optJSONObject("permisos");
        return permisos == null ? new JSONObject() : permisos;
    }

    static void exigir(JSONObject permisos, String permiso) throws Exception {
        if (!permisos.has(permiso)) throw new Exception("Sin permiso " + PAGE + " " + permiso);
    }

    static JSONObject consulta(String sql, String... params) throws Exception {
        return Tienda.consulta(sql, params);
    }

    static int actualizar(String sql, String... params) throws Exception {
        return Tienda.actualizar(sql, params);
    }

    static String consultaTexto(String sql, String... params) throws Exception {
        Connection con = SPGConect.pool.getConnection();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setString(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getString("json") != null ? rs.getString("json") : "";
            }
        } finally {
            SPGConect.pool.releaseConnection(con);
        }
    }

    static String[] columna(String sql, String... params) throws Exception {
        List<String> out = new ArrayList<>();
        Connection con = SPGConect.pool.getConnection();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setString(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) if (rs.getString("json") != null) out.add(rs.getString("json"));
            }
        } finally {
            SPGConect.pool.releaseConnection(con);
        }
        return out.toArray(new String[0]);
    }

    static List<JSONObject> listaObj(String sql, String... params) throws Exception {
        List<JSONObject> out = new ArrayList<>();
        Connection con = SPGConect.pool.getConnection();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setString(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) if (rs.getString("json") != null) out.add(new JSONObject(rs.getString("json")));
            }
        } finally {
            SPGConect.pool.releaseConnection(con);
        }
        return out;
    }

    static JSONArray lista(String sql, String... params) throws Exception {
        JSONArray out = new JSONArray();
        for (JSONObject o : listaObj(sql, params)) out.put(o);
        return out;
    }

    /** Timestamps de to_json vienen con "T"; los de ::text con espacio. Para comparar como texto. */
    static String fechaTexto(String f) {
        return f == null ? "" : f.replace('T', ' ');
    }

    static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    static String recortar(String s, int max) {
        return Tienda.recortar(s, max);
    }
}
