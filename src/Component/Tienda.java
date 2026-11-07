package Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.ZoneId;

import org.json.JSONArray;
import org.json.JSONObject;

import Server.SSSAbstract.SSSessionAbstract;
import Servisofts.SPGConect;
import Servisofts.SUtil;
import SocketCliente.SocketCliente;

/**
 * Tienda web: una empresa publica productos (planes) que se compran desde una página pública y se pagan con
 * tarjeta (Cybersource). Al aprobarse el pago, serp registra la venta sola en la caja automática del punto de
 * venta de la tienda, con el cliente (CRM) y la suscripción en la sucursal elegida.
 *
 * Tablas (servisofts.serp): tienda (una por empresa), tienda_producto (producto de inventario publicado; el precio
 * sale siempre del producto, nunca de la página), tienda_orden (cada compra).
 * Estados de la orden: PENDIENTE_PAGO -> PAGADA -> VENTA (o ERROR tras muchos intentos; ANULADA si se descarta).
 *
 * La venta se registra desde orden_estado (la página consulta) y desde TiendaTask (cada minuto), siempre con
 * lock por orden: un pago genera una sola venta aunque se llame varias veces.
 *
 * Bus (component "tienda", con permisos /cybersource): config_get, config_guardar (ver/configurar), ordenes (ver).
 */
public class Tienda {
    public static final String COMPONENT = "tienda";
    static final int MAX_INTENTOS = 30;

    public static void onMessage(JSONObject obj, SSSessionAbstract session) {
        try {
            String key_usuario = obj.getString("key_usuario");
            String key_empresa = obj.getString("key_empresa");
            JSONObject permisos = CyberSource.permisosDe(key_usuario, key_empresa);
            switch (obj.getString("type")) {
                case "config_get":
                    CyberSource.exigirPermiso(permisos, "ver");
                    obj.put("data", configCompleta(key_empresa));
                    break;
                case "config_guardar":
                    CyberSource.exigirPermiso(permisos, "configurar");
                    obj.put("data", guardarConfig(key_empresa, key_usuario, obj.getJSONObject("data")));
                    break;
                case "config_opciones":
                    CyberSource.exigirPermiso(permisos, "configurar");
                    obj.put("data", opciones(key_empresa, key_usuario));
                    break;
                case "ordenes":
                    CyberSource.exigirPermiso(permisos, "ver");
                    obj.put("data", ordenes(key_empresa, obj.optString("desde", ""), obj.optString("hasta", "")));
                    break;
                case "orden_reprocesar":
                    CyberSource.exigirPermiso(permisos, "configurar");
                    JSONObject o = buscarOrden(obj.getString("key"));
                    if (!key_empresa.equals(o.optString("key_empresa"))) throw new Exception("Orden no encontrada");
                    actualizar("update tienda_orden set intentos = 0, state = case when state = 'ERROR' then 'PAGADA' else state end where key = ?", o.getString("key"));
                    obj.put("data", publicaOrden(procesar(o.getString("key"))));
                    break;
                // ---- Contratos en cuotas (débito automático) ----
                case "contratos":
                    CyberSource.exigirPermiso(permisos, "ver");
                    obj.put("data", TiendaContrato.contratos(key_empresa, obj.optString("state", "")));
                    break;
                case "contrato":
                    CyberSource.exigirPermiso(permisos, "ver");
                    obj.put("data", TiendaContrato.detalle(obj.getString("key"), key_empresa));
                    break;
                case "cobros":
                    CyberSource.exigirPermiso(permisos, "ver");
                    obj.put("data", TiendaContrato.cobros(key_empresa, obj.optString("desde", ""), obj.optString("hasta", ""), obj.optString("state", "")));
                    break;
                case "cobro_cobrar": {
                    CyberSource.exigirPermiso(permisos, "configurar");
                    JSONObject cb = TiendaContrato.cobro(obj.getString("key"));
                    if (!key_empresa.equals(cb.optString("key_empresa"))) throw new Exception("Cobro no encontrado");
                    obj.put("data", TiendaContrato.procesar(cb.getString("key"), true));
                    break;
                }
                case "cobro_link":
                    CyberSource.exigirPermiso(permisos, "configurar");
                    obj.put("data", TiendaContrato.generarLink(obj.getString("key"), key_empresa));
                    break;
                case "contrato_cancelar":
                    CyberSource.exigirPermiso(permisos, "configurar");
                    obj.put("data", TiendaContrato.cancelar(obj.getString("key"), key_empresa, key_usuario, obj.optBoolean("anular_restantes"), obj.optString("motivo")));
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

    // ===================== API pública (TiendaController) =====================

    /** Productos publicados con su precio actual (del inventario) y las sucursales donde se venden. */
    public static JSONObject catalogo(String key_tienda) throws Exception {
        JSONObject t = tiendaActiva(key_tienda);
        JSONArray sucursalesEmpresa = sucursales(t.getString("key_empresa"));
        JSONArray productos = new JSONArray();
        for (JSONObject p : productosDe(t.getString("key"))) {
            JSONObject m = modelo(p.getString("key_modelo"));
            if (m == null || m.optInt("estado", 1) <= 0) continue;
            productos.put(new JSONObject()
                    .put("key", p.getString("key"))
                    .put("nombre", p.optString("nombre").isEmpty() ? m.optString("descripcion") : p.optString("nombre"))
                    .put("descripcion", p.optString("descripcion", ""))
                    .put("precio", m.optDouble("precio_venta", 0))
                    .put("modalidad", p.optString("modalidad", "unico"))
                    .put("cuotas", "cuotas".equals(p.optString("modalidad")) ? Math.max(2, p.optInt("cuotas", 12)) : 1)
                    .put("duracion", m.optInt("duracion", 0))
                    .put("duracion_medida", m.optString("duracion_medida", "dias"))
                    .put("sucursales", filtrarSucursales(sucursalesEmpresa, p.optJSONArray("sucursales"))));
        }
        return new JSONObject().put("tienda", new JSONObject().put("key", t.getString("key")).put("nombre", t.optString("nombre")))
                .put("moneda", "BOB").put("productos", productos);
    }

    /**
     * {key_tienda, key_producto, key_sucursal, cliente:{nombres, apellidos, ci, nacimiento, celular, email}}
     * -> crea la orden con el precio del inventario y el link de pago con tarjeta.
     */
    public static JSONObject crearOrden(JSONObject b, String ip, String origen) throws Exception {
        JSONObject t = tiendaActiva(b.getString("key_tienda"));
        JSONObject p = consulta("select to_json(p) as json from tienda_producto p where p.key = ? and p.key_tienda = ? and p.estado > 0",
                b.optString("key_producto"), t.getString("key"));
        if (p.isEmpty()) throw new Exception("El plan elegido no está disponible");
        JSONObject m = modelo(p.getString("key_modelo"));
        if (m == null || m.optInt("estado", 1) <= 0) throw new Exception("El plan elegido no está disponible");
        double monto = Math.round(m.optDouble("precio_venta", 0) * 100.0) / 100.0;
        if (!(monto > 0)) throw new Exception("El plan elegido no tiene precio");

        String key_sucursal = b.optString("key_sucursal", "");
        boolean sucursalValida = false;
        for (Object s : filtrarSucursales(sucursales(t.getString("key_empresa")), p.optJSONArray("sucursales")))
            if (key_sucursal.equals(((JSONObject) s).optString("key"))) sucursalValida = true;
        if (!sucursalValida) throw new Exception("Elegí una sucursal válida para este plan");

        JSONObject c = b.optJSONObject("cliente");
        if (c == null) throw new Exception("Faltan tus datos");
        JSONObject cliente = new JSONObject()
                .put("nombres", texto(c, "nombres", 60, true)).put("apellidos", texto(c, "apellidos", 60, true))
                .put("ci", texto(c, "ci", 20, true).toUpperCase()).put("nacimiento", texto(c, "nacimiento", 10, false))
                .put("celular", texto(c, "celular", 20, true)).put("email", texto(c, "email", 120, true).toLowerCase());
        if (!cliente.getString("email").matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new Exception("Revisá tu email");
        if (!cliente.getString("nacimiento").isEmpty() && !cliente.getString("nacimiento").matches("\\d{4}-\\d{2}-\\d{2}")) throw new Exception("Revisá tu fecha de nacimiento");

        String key = SUtil.uuid();
        String nombre = p.optString("nombre").isEmpty() ? m.optString("descripcion") : p.optString("nombre");
        actualizar("insert into tienda_orden (key, key_tienda, key_empresa, key_tienda_producto, key_modelo, descripcion, monto, key_sucursal, cliente, state, origen, ip) "
                + "values (?, ?, ?, ?, ?, ?, ?::numeric, ?, ?::json, 'PENDIENTE_PAGO', ?, ?)",
                key, t.getString("key"), t.getString("key_empresa"), p.getString("key"), p.getString("key_modelo"), nombre,
                String.valueOf(monto), key_sucursal, cliente.toString(), recortar(origen, 60), recortar(ip, 60));

        // Link de pago (servicio cybersource). key_solicitud = la orden: reintentar crea/devuelve el mismo link.
        JSONObject datosLink = new JSONObject().put("monto", monto).put("descripcion", nombre).put("key_solicitud", "tienda:" + key);
        // Plan en cuotas (Fidelidad / Total): el primer pago es la cuota 1 y deja la tarjeta guardada para las siguientes.
        boolean enCuotas = "cuotas".equals(p.optString("modalidad"));
        if (enCuotas) {
            if (t.optString("key_empresa_tipo_pago_credito").isEmpty()) throw new Exception("Este plan todavía no está disponible");
            datosLink.put("guardar_tarjeta", true).put("descripcion", nombre + " · cuota 1/" + Math.max(2, p.optInt("cuotas", 12)) + " (débito automático)");
            actualizar("update tienda_orden set modalidad = 'cuotas' where key = ?", key);
        }
        String volver = urlRetorno(b.optString("volver", ""), origen, key);
        if (volver != null) datosLink.put("url_retorno", volver);
        // Página de pago en un iframe de la tienda: el servicio cybersource declara este origen a Cybersource
        // (Microform). Solo https o localhost; si no, la tienda usa el modo redirección.
        if (b.optBoolean("iframe") && origen != null && origen.trim().matches("^(https://[A-Za-z0-9.-]+|http://localhost)(:\\d{1,5})?$"))
            datosLink.put("origen_iframe", origen.trim());
        JSONObject r = CyberSource.llamar(new JSONObject().put("component", "link_pago").put("type", "registro")
                .put("key_empresa", t.getString("key_empresa")).put("key_usuario", t.optString("key_usuario_venta"))
                .put("data", datosLink
                        .put("cliente", new JSONObject().put("nombre", cliente.getString("nombres")).put("apellido", cliente.getString("apellidos"))
                                .put("email", cliente.getString("email")).put("telefono", cliente.getString("celular"))
                                .put("documento", cliente.getString("ci")))));
        if (!"exito".equals(r.optString("estado"))) {
            actualizar("update tienda_orden set state = 'ANULADA', error = ? where key = ?", recortar(r.optString("error"), 250), key);
            throw new Exception("No se pudo iniciar el pago: " + r.optString("error", ""));
        }
        JSONObject link = r.getJSONObject("data");
        actualizar("update tienda_orden set key_link = ?, url_pago = ? where key = ?", link.getString("key"), link.getString("url"), key);
        return new JSONObject().put("key", key).put("monto", monto).put("descripcion", nombre).put("url_pago", link.getString("url"));
    }

    /**
     * URL a la que la página de pago devuelve al cliente. Solo se acepta si es del mismo sitio que creó la orden
     * (cabecera Origin del navegador), para que el link no sirva para mandar a otro sitio. "{orden}" se reemplaza
     * por la key de la orden.
     */
    static String urlRetorno(String volver, String origen, String key) {
        try {
            if (volver == null || volver.isEmpty() || volver.length() > 400 || origen == null || origen.isEmpty()) return null;
            java.net.URI u = new java.net.URI(volver.replace("{orden}", "ORDEN"));
            if (!"http".equals(u.getScheme()) && !"https".equals(u.getScheme())) return null;
            String o = u.getScheme() + "://" + u.getRawAuthority();
            if (!o.equalsIgnoreCase(origen.trim())) return null;
            return volver.replace("{orden}", key);
        } catch (Exception e) {
            return null;
        }
    }

    /** Estado de la orden para la página. Si el pago ya se aprobó y falta la venta, la registra en el acto. */
    public static JSONObject estadoOrden(String key) throws Exception {
        JSONObject o = buscarOrden(key);
        if (o.optString("state").equals("PENDIENTE_PAGO") || o.optString("state").equals("PAGADA")) o = procesar(key);
        return publicaOrden(o);
    }

    // ===================== Registro de la venta =====================

    /** Revisa el pago de la orden y, si está aprobado, registra la venta. Idempotente y con lock por orden. */
    static JSONObject procesar(String key) throws Exception {
        synchronized (("tienda_orden:" + key).intern()) {
            JSONObject o = buscarOrden(key);
            String state = o.optString("state");
            if (!state.equals("PENDIENTE_PAGO") && !state.equals("PAGADA")) return o;
            if (o.optString("key_link").isEmpty()) return o;

            JSONObject link = linkDe(o);
            if (link == null) return o;
            if (state.equals("PENDIENTE_PAGO")) {
                if (!"AUTHORIZED".equals(link.optString("state"))) return o;
                actualizar("update tienda_orden set state = 'PAGADA', fecha_pago = now() where key = ? and state = 'PENDIENTE_PAGO'", key);
                o = buscarOrden(key);
            }
            if ("cuotas".equals(o.optString("modalidad"))) {
                // Contrato en cuotas: TiendaContrato completa los pasos que falten (venta, contrato, cuotas, pago de la 1).
                try {
                    String venta = TiendaContrato.registrar(o, link);
                    actualizar("update tienda_orden set state = 'VENTA', key_compra_venta = ?, fecha_venta = coalesce(fecha_venta, now()), error = null where key = ?", venta, key);
                } catch (Exception e) {
                    e.printStackTrace();
                    actualizar("update tienda_orden set intentos = intentos + 1, error = ?, state = case when intentos + 1 >= " + MAX_INTENTOS
                            + " then 'ERROR' else state end where key = ?", recortar(e.getMessage(), 250), key);
                }
                return buscarOrden(key);
            }
            if (!link.optString("consumido_por", "").isEmpty()) {
                // Ya hay una venta con este pago (p. ej. se registró y falló justo al guardar la orden).
                actualizar("update tienda_orden set state = 'VENTA', key_compra_venta = ?, fecha_venta = coalesce(fecha_venta, now()), error = null where key = ?",
                        link.getString("consumido_por"), key);
                return buscarOrden(key);
            }
            try {
                String venta = registrarVenta(o, link);
                actualizar("update tienda_orden set state = 'VENTA', key_compra_venta = ?, fecha_venta = now(), error = null where key = ?", venta, key);
            } catch (Exception e) {
                e.printStackTrace();
                actualizar("update tienda_orden set intentos = intentos + 1, error = ?, state = case when intentos + 1 >= " + MAX_INTENTOS
                        + " then 'ERROR' else state end where key = ?", recortar(e.getMessage(), 250), key);
            }
            return buscarOrden(key);
        }
    }

    private static String registrarVenta(JSONObject o, JSONObject link) throws Exception {
        JSONObject t = consulta("select to_json(t) as json from tienda t where t.key = ?", o.getString("key_tienda"));
        String key_empresa = t.getString("key_empresa");
        String key_usuario = t.getString("key_usuario_venta");
        JSONObject c = o.getJSONObject("cliente");

        // 1) Cliente: por CI; si no existe se crea.
        String key_cliente = clienteDeOrden(o, key_empresa, key_usuario);

        // 2) Caja automática del punto de venta de la tienda.
        JSONObject caja = bus("caja", new JSONObject().put("component", "caja").put("type", "getAutomatica")
                .put("key_punto_venta", t.getString("key_punto_venta")).put("key_empresa", key_empresa).put("key_usuario", key_usuario));
        String key_caja = caja.getJSONObject("data").getString("key");

        // 3) Producto y fechas de la suscripción (mismas reglas que la caja).
        JSONObject m = modelo(o.getString("key_modelo"));
        if (m == null) throw new Exception("El producto de la orden ya no existe");
        String[] fechas = fechasSuscripcion(key_empresa, key_usuario, key_cliente, m);
        JSONObject suscriptor = new JSONObject().put("key_cliente", key_cliente).put("fecha_inicio", fechas[0]).put("fecha_fin", fechas[1])
                .put("key_producto", "").put("key_sucursal", o.getString("key_sucursal"));
        double monto = o.getDouble("monto");
        JSONObject detalle = new JSONObject()
                .put("cantidad", 1).put("precio_unitario", monto).put("precio_unitario_base", monto).put("detalle", "")
                .put("descripcion", m.optString("descripcion"))
                .put("data", new JSONObject().put("tipo_producto", "servicio").put("cupos_disponibles", m.optInt("cantidad_suscriptores", 1)).put("cupos_suscritos", 1))
                .put("key_modelo", m.getString("key")).put("costos", new JSONArray()).put("suscriptores", new JSONArray().put(suscriptor));

        // 4) Pago con tarjeta: el servicio caja lo verifica y lo marca como usado por esta venta.
        JSONObject etp = bus("caja", new JSONObject().put("component", "empresa_tipo_pago").put("type", "getByKey")
                .put("key", t.getString("key_empresa_tipo_pago")).put("key_empresa", key_empresa)).optJSONObject("data");
        JSONObject tipoPago = bus("caja", new JSONObject().put("component", "tipo_pago").put("type", "getAll")).optJSONObject("data");
        JSONObject tp = tipoPago == null || etp == null ? null : tipoPago.optJSONObject(etp.optString("key_tipo_pago"));
        String referencia = "Ref: Cybersource " + link.optString("cybs_id") + (link.optString("aprobacion").isEmpty() ? "" : " · Aut " + link.optString("aprobacion"))
                + (link.optString("tarjeta").isEmpty() ? "" : " · " + link.optString("tarjeta"));
        JSONObject tipos_pago = new JSONObject().put(t.getString("key_empresa_tipo_pago"), new JSONObject()
                .put("monto_nacional", monto).put("monto_extranjera", monto)
                .put("tipo_pago", tp == null ? JSONObject.NULL : tp)
                .put("referencia", referencia).put("descripcion", etp == null ? "Tarjeta" : etp.optString("descripcion"))
                .put("pago_tarjeta", new JSONObject().put("key", link.getString("key"))));

        JSONObject data = new JSONObject()
                .put("descripcion", "").put("observacion", "Tienda web · " + o.optString("descripcion") + " · orden " + o.getString("key").substring(0, 8))
                .put("facturar", false).put("factura", JSONObject.NULL).put("tipo_pago", "contado").put("facturar_luego", false)
                .put("cliente", new JSONObject().put("key", key_cliente).put("nit", c.optString("ci"))
                        .put("razon_social", (c.optString("nombres") + " " + c.optString("apellidos")).trim()))
                .put("descuentos", new JSONArray()).put("key_cliente", key_cliente).put("key_usuario", key_usuario)
                .put("key_caja", key_caja).put("key_almacen", t.getString("key_almacen")).put("key_moneda", t.getString("key_moneda"))
                .put("detalle", new JSONArray().put(detalle)).put("suscripciones", new JSONArray().put(new JSONObject(suscriptor.toString())))
                .put("tipos_pago", tipos_pago);

        // caja_detalle/venta no pone estado "exito" al terminar bien (lo deja en "cargando"): éxito = trae key_compra_venta.
        JSONObject r = SocketCliente.sendSinc("caja", new JSONObject().put("component", "caja_detalle").put("type", "venta").put("estado", "cargando").put("data", data), 60000);
        if (r == null) throw new Exception("Sin respuesta del servicio caja");
        if ("error".equals(r.optString("estado"))) throw new Exception("caja: " + r.optString("error", "error al registrar la venta"));
        String venta = r.optJSONObject("data") == null ? "" : r.getJSONObject("data").optString("key_compra_venta", "");
        if (venta.isEmpty()) throw new Exception("La caja no devolvió la venta");
        return venta;
    }

    /** Cliente de la orden: el ya resuelto, o por CI en el CRM (si no existe se crea). Se guarda en la orden. */
    static String clienteDeOrden(JSONObject o, String key_empresa, String key_usuario) throws Exception {
        String key_cliente = o.optString("key_cliente", "");
        if (!key_cliente.isEmpty()) return key_cliente;
        JSONObject c = o.getJSONObject("cliente");
        key_cliente = buscarCliente(key_empresa, key_usuario, c.getString("ci"));
        if (key_cliente == null) key_cliente = crearCliente(key_empresa, key_usuario, c);
        actualizar("update tienda_orden set key_cliente = ? where key = ?", key_cliente, o.getString("key"));
        o.put("key_cliente", key_cliente);
        return key_cliente;
    }

    /** Cliente de la empresa por CI exacto (crm cliente buscar_ci). null si no existe; si el CRM falla, error (no se crea otro). */
    static String buscarCliente(String key_empresa, String key_usuario, String ci) throws Exception {
        JSONObject r = SocketCliente.sendSinc("crm", new JSONObject().put("component", "cliente").put("type", "buscar_ci").put("estado", "cargando")
                .put("ci", ci).put("key_empresa", key_empresa).put("key_usuario", key_usuario), 20000);
        if (r == null) throw new Exception("El CRM no respondió");
        if ("exito".equals(r.optString("estado"))) {
            JSONObject cli = r.optJSONObject("data");
            return cli == null || cli.optString("key").isEmpty() ? null : cli.getString("key");
        }
        if ("noexiste".equals(r.optString("error"))) return null;
        throw new Exception("CRM: " + r.optString("error", "error buscando el cliente"));
    }

    static String crearCliente(String key_empresa, String key_usuario, JSONObject c) throws Exception {
        JSONObject data = new JSONObject()
                .put("nombres", c.optString("nombres")).put("apellidos", c.optString("apellidos"))
                .put("nit", c.optString("ci")).put("razon_social", (c.optString("nombres") + " " + c.optString("apellidos")).trim())
                .put("telefono", c.optString("celular")).put("correo", c.optString("email"))
                .put("key_empresa", key_empresa); // crm registro toma la empresa de data, no del mensaje
        if (!c.optString("nacimiento").isEmpty()) data.put("fecha_nacimiento", c.optString("nacimiento"));
        JSONObject r = bus("crm", new JSONObject().put("component", "cliente").put("type", "registro")
                .put("data", data).put("key_empresa", key_empresa).put("key_usuario", key_usuario));
        String key = r.optJSONObject("data") == null ? "" : r.getJSONObject("data").optString("key");
        if (key.isEmpty()) throw new Exception("El CRM no devolvió el cliente");
        return key;
    }

    /** Inicio: hoy, o el día siguiente al fin de una suscripción vigente del mismo tipo. Fin: inicio + duración - 1. */
    static String[] fechasSuscripcion(String key_empresa, String key_usuario, String key_cliente, JSONObject m) throws Exception {
        LocalDate hoy = LocalDate.now(ZoneId.of("America/La_Paz"));
        LocalDate inicio = hoy;
        try {
            JSONObject r = SocketCliente.sendSinc("inventario", new JSONObject().put("version", "1.0").put("component", "suscripcion")
                    .put("type", "getByKeyCliente").put("key_cliente", key_cliente).put("key_empresa", key_empresa).put("key_usuario", key_usuario), 20000);
            Object d = r == null ? null : r.opt("data");
            JSONArray subs = d instanceof JSONArray ? (JSONArray) d : new JSONArray();
            if (d instanceof JSONObject) for (String k : ((JSONObject) d).keySet()) subs.put(((JSONObject) d).get(k));
            LocalDate ancla = null;
            for (int i = 0; i < subs.length(); i++) {
                JSONObject s = subs.optJSONObject(i);
                if (s == null || s.optInt("estado", 1) <= 0) continue;
                JSONObject prod = s.optJSONObject("producto");
                String km = prod == null ? "" : prod.optString("key_modelo");
                if (!km.equals(m.getString("key"))) {
                    JSONObject otro = km.isEmpty() ? null : modelo(km);
                    if (otro == null || !otro.optString("key_tipo_producto").equals(m.optString("key_tipo_producto"))) continue;
                }
                String f = s.optString("fecha_fin", "");
                if (f.length() < 10) continue;
                LocalDate fin = LocalDate.parse(f.substring(0, 10));
                if (!fin.isBefore(hoy) && (ancla == null || fin.isAfter(ancla))) ancla = fin;
            }
            if (ancla != null) inicio = ancla.plusDays(1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        int cantidad = Math.max(1, m.optInt("duracion", 30));
        String medida = m.optString("duracion_medida", "dias");
        LocalDate fin;
        if (medida.equals("meses")) fin = inicio.plusMonths(cantidad);
        else if (medida.equals("anos") || medida.equals("años")) fin = inicio.plusYears(cantidad);
        else if (medida.equals("semanas")) fin = inicio.plusDays(cantidad * 7L - 1);
        else fin = inicio.plusDays(cantidad - 1L);
        return new String[] { inicio.toString(), fin.toString() };
    }

    // ===================== Configuración (pantalla Cybersource → Tienda web) =====================

    static JSONObject configCompleta(String key_empresa) throws Exception {
        JSONObject t = consulta("select to_json(t) as json from tienda t where t.key_empresa = ? and t.estado > 0", key_empresa);
        if (t.isEmpty()) return new JSONObject().put("configurada", false);
        JSONArray productos = new JSONArray();
        for (JSONObject p : productosDe(t.getString("key"))) productos.put(p);
        return t.put("configurada", true).put("productos", productos);
    }

    /** {nombre, activa, key_punto_venta, key_almacen, key_empresa_tipo_pago, key_usuario_venta, key_moneda, origenes[], productos:[{key?, key_modelo, nombre, descripcion, orden, solo_web, sucursales[]|null}]} */
    static JSONObject guardarConfig(String key_empresa, String key_usuario, JSONObject d) throws Exception {
        for (String k : new String[] { "key_punto_venta", "key_almacen", "key_empresa_tipo_pago", "key_usuario_venta", "key_moneda" })
            if (d.optString(k).isEmpty()) throw new Exception("Falta " + k);
        JSONObject t = consulta("select to_json(t) as json from tienda t where t.key_empresa = ? and t.estado > 0", key_empresa);
        String key = t.isEmpty() ? SUtil.uuid() : t.getString("key");
        String origenes = d.optJSONArray("origenes") == null ? "[]" : d.getJSONArray("origenes").toString();
        String credito = d.optString("key_empresa_tipo_pago_credito", "");
        if (t.isEmpty())
            actualizar("insert into tienda (key, key_usuario, key_empresa, nombre, activa, key_punto_venta, key_almacen, key_empresa_tipo_pago, key_usuario_venta, key_moneda, origenes) "
                    + "values (?, ?, ?, ?, ?::boolean, ?, ?, ?, ?, ?, ?::json)", key, key_usuario, key_empresa, d.optString("nombre", "Tienda web"),
                    String.valueOf(d.optBoolean("activa", true)), d.getString("key_punto_venta"), d.getString("key_almacen"), d.getString("key_empresa_tipo_pago"),
                    d.getString("key_usuario_venta"), d.getString("key_moneda"), origenes);
        else
            actualizar("update tienda set nombre = ?, activa = ?::boolean, key_punto_venta = ?, key_almacen = ?, key_empresa_tipo_pago = ?, key_usuario_venta = ?, "
                    + "key_moneda = ?, origenes = ?::json where key = ?", d.optString("nombre", "Tienda web"), String.valueOf(d.optBoolean("activa", true)),
                    d.getString("key_punto_venta"), d.getString("key_almacen"), d.getString("key_empresa_tipo_pago"), d.getString("key_usuario_venta"),
                    d.getString("key_moneda"), origenes, key);
        actualizar("update tienda set key_empresa_tipo_pago_credito = nullif(?, ''), reintento_horas = ?::integer, reintentos = ?::integer where key = ?", credito,
                String.valueOf(java.util.Arrays.asList(24, 48, 72).contains(d.optInt("reintento_horas", 24)) ? d.optInt("reintento_horas", 24) : 24),
                String.valueOf(Math.max(0, Math.min(3, d.optInt("reintentos", 3)))), key);
        JSONArray productos = d.optJSONArray("productos");
        if (productos != null) {
            actualizar("update tienda_producto set estado = 0 where key_tienda = ?", key);
            for (int i = 0; i < productos.length(); i++) {
                JSONObject p = productos.getJSONObject(i);
                if (p.optString("key_modelo").isEmpty()) continue;
                String suc = p.optJSONArray("sucursales") == null ? null : p.getJSONArray("sucursales").toString();
                String kp = p.optString("key", "");
                String modalidad = "cuotas".equals(p.optString("modalidad")) ? "cuotas" : "unico";
                String cuotas = String.valueOf(modalidad.equals("cuotas") ? Math.max(2, Math.min(36, p.optInt("cuotas", 12))) : 1);
                if (modalidad.equals("cuotas") && credito.isEmpty()) throw new Exception("Para publicar planes en cuotas falta el tipo de pago a crédito");
                int n = kp.isEmpty() ? 0 : actualizar("update tienda_producto set estado = 1, key_modelo = ?, nombre = ?, descripcion = ?, orden = ?::integer, solo_web = ?::boolean, "
                        + "sucursales = ?::json, modalidad = ?, cuotas = ?::integer where key = ? and key_tienda = ?", p.getString("key_modelo"), p.optString("nombre"), p.optString("descripcion"),
                        String.valueOf(p.optInt("orden", i)), String.valueOf(p.optBoolean("solo_web", true)), suc, modalidad, cuotas, kp, key);
                if (n == 0)
                    actualizar("insert into tienda_producto (key, key_usuario, key_tienda, key_modelo, nombre, descripcion, orden, solo_web, sucursales, modalidad, cuotas) "
                            + "values (?, ?, ?, ?, ?, ?, ?::integer, ?::boolean, ?::json, ?, ?::integer)", SUtil.uuid(), key_usuario, key, p.getString("key_modelo"), p.optString("nombre"),
                            p.optString("descripcion"), String.valueOf(p.optInt("orden", i)), String.valueOf(p.optBoolean("solo_web", true)), suc, modalidad, cuotas);
            }
        }
        return configCompleta(key_empresa);
    }

    /** Listas para la pantalla de configuración (cada una por separado: si un servicio falla, las demás igual llegan). */
    static JSONObject opciones(String key_empresa, String key_usuario) {
        JSONObject out = new JSONObject();
        JSONArray sucs = sucursales(key_empresa);
        out.put("sucursales", sucs);
        // punto_venta getAll del servicio empresa es por sucursal.
        JSONArray pvs = new JSONArray();
        for (int i = 0; i < sucs.length(); i++) {
            JSONObject s = sucs.getJSONObject(i);
            JSONArray l = lista("empresa", new JSONObject().put("component", "punto_venta").put("type", "getAll").put("key_sucursal", s.getString("key")), "key", "descripcion", "key_sucursal");
            for (int j = 0; j < l.length(); j++) pvs.put(l.getJSONObject(j).put("sucursal", s.optString("nombre")));
        }
        out.put("puntos_venta", pvs);
        out.put("almacenes", lista("inventario", new JSONObject().put("version", "1.0").put("component", "almacen").put("type", "getAll").put("key_empresa", key_empresa).put("key_usuario", key_usuario), "key", "descripcion", "key_sucursal"));
        out.put("tipos_pago", lista("caja", new JSONObject().put("component", "empresa_tipo_pago").put("type", "getAll").put("key_empresa", key_empresa), "key", "descripcion", "key_tipo_pago", "key_moneda", "key_pasarela_empresa"));
        out.put("monedas", lista("empresa", new JSONObject().put("component", "empresa_moneda").put("type", "getAll").put("key_empresa", key_empresa), "key", "descripcion", "codigo", "tipo"));
        out.put("usuarios", lista("empresa", new JSONObject().put("component", "empresa_usuario").put("type", "getAll").put("key_empresa", key_empresa), "key_usuario", "alias", "key_rol"));
        out.put("modelos", lista("inventario", new JSONObject().put("version", "1.0").put("component", "modelo").put("type", "getAll").put("key_empresa", key_empresa), "key", "descripcion", "precio_venta", "duracion", "duracion_medida", "venta_caja"));
        return out;
    }

    private static JSONArray lista(String servicio, JSONObject req, String... campos) {
        JSONArray out = new JSONArray();
        try {
            JSONObject r = SocketCliente.sendSinc(servicio, req.put("estado", "cargando"), 30000);
            Object d = r == null ? null : r.opt("data");
            java.util.List<Object> filas = new java.util.ArrayList<>();
            if (d instanceof JSONObject) for (String k : ((JSONObject) d).keySet()) filas.add(((JSONObject) d).get(k));
            if (d instanceof JSONArray) for (int i = 0; i < ((JSONArray) d).length(); i++) filas.add(((JSONArray) d).get(i));
            for (Object o : filas) {
                if (!(o instanceof JSONObject) || ((JSONObject) o).optInt("estado", 1) <= 0) continue;
                JSONObject x = new JSONObject();
                for (String c : campos) x.put(c, ((JSONObject) o).opt(c));
                out.put(x);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return out;
    }

    static JSONArray ordenes(String key_empresa, String desde, String hasta) throws Exception {
        if (!desde.isEmpty() && !desde.matches("\\d{4}-\\d{2}-\\d{2}")) throw new Exception("desde inválido");
        if (!hasta.isEmpty() && !hasta.matches("\\d{4}-\\d{2}-\\d{2}")) throw new Exception("hasta inválido");
        JSONArray out = new JSONArray();
        Connection con = SPGConect.pool.getConnection();
        try (PreparedStatement ps = con.prepareStatement("select to_json(o) as json from tienda_orden o where o.key_empresa = ? "
                + "and (? = '' or o.fecha_on >= ?::date) and (? = '' or o.fecha_on < ?::date + 1) order by o.fecha_on desc limit 2000")) {
            ps.setString(1, key_empresa);
            ps.setString(2, desde); ps.setString(3, desde.isEmpty() ? "1900-01-01" : desde);
            ps.setString(4, hasta); ps.setString(5, hasta.isEmpty() ? "2999-12-31" : hasta);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.put(new JSONObject(rs.getString("json")));
            }
        } finally {
            SPGConect.pool.releaseConnection(con);
        }
        return out;
    }

    /**
     * Productos con "Vender en caja" desmarcado (inventario modelo.venta_caja = false) y el punto de venta de la
     * tienda web, el único donde sí se pueden vender. Lo usa el servicio caja para rechazarlos en las demás cajas.
     */
    public static JSONObject soloWeb(String key_empresa) throws Exception {
        JSONObject t = consulta("select to_json(t) as json from tienda t where t.key_empresa = ? and t.estado > 0", key_empresa == null ? "" : key_empresa);
        JSONObject r = SocketCliente.sendSinc("inventario", new JSONObject().put("version", "1.0").put("component", "modelo").put("type", "getAll")
                .put("estado", "cargando").put("key_empresa", key_empresa), 30000);
        if (r == null || !"exito".equals(r.optString("estado"))) throw new Exception("No se pudo leer el inventario");
        JSONArray modelos = new JSONArray();
        JSONObject d = r.optJSONObject("data");
        if (d != null) for (String k : d.keySet()) {
            JSONObject m = d.optJSONObject(k);
            if (m != null && m.has("venta_caja") && !m.isNull("venta_caja") && !m.optBoolean("venta_caja", true)) modelos.put(m.optString("key"));
        }
        return new JSONObject().put("key_punto_venta", t.optString("key_punto_venta", "")).put("modelos", modelos);
    }

    // ===================== internos =====================

    /** Para Tasks.TiendaTask. */
    public static JSONArray pendientesPublico() throws Exception { return pendientes(); }
    public static JSONObject procesarPublico(String key) throws Exception { return procesar(key); }

    static JSONArray pendientes() throws Exception {
        JSONArray out = new JSONArray();
        Connection con = SPGConect.pool.getConnection();
        try (PreparedStatement ps = con.prepareStatement("select key from tienda_orden where estado > 0 and key_link is not null and "
                + "(state = 'PAGADA' or (state = 'PENDIENTE_PAGO' and fecha_on > now() - interval '2 days')) order by fecha_on limit 200")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.put(rs.getString("key"));
            }
        } finally {
            SPGConect.pool.releaseConnection(con);
        }
        return out;
    }

    static JSONObject linkDe(JSONObject o) {
        try {
            JSONObject r = CyberSource.llamar(new JSONObject().put("component", "link_pago").put("type", "getByKey")
                    .put("key", o.getString("key_link")).put("key_empresa", o.getString("key_empresa")));
            return "exito".equals(r.optString("estado")) ? r.optJSONObject("data") : null;
        } catch (Exception e) {
            return null;
        }
    }

    static JSONObject publicaOrden(JSONObject o) {
        JSONObject c = o.optJSONObject("cliente");
        return new JSONObject().put("key", o.optString("key")).put("state", o.optString("state")).put("descripcion", o.optString("descripcion"))
                .put("monto", o.opt("monto")).put("url_pago", o.optString("url_pago")).put("key_sucursal", o.optString("key_sucursal"))
                .put("numero", o.optString("key_compra_venta").isEmpty() ? "" : o.getString("key_compra_venta").substring(0, 8).toUpperCase())
                .put("nombre", c == null ? "" : c.optString("nombres"))
                .put("error", o.optString("state").equals("ERROR") ? "No pudimos registrar tu inscripción automáticamente: te contactamos para completarla." : JSONObject.NULL);
    }

    static JSONObject tiendaActiva(String key_tienda) throws Exception {
        JSONObject t = consulta("select to_json(t) as json from tienda t where t.key = ? and t.estado > 0 and t.activa", key_tienda == null ? "" : key_tienda);
        if (t.isEmpty()) throw new Exception("Tienda no disponible");
        return t;
    }

    private static java.util.List<JSONObject> productosDe(String key_tienda) throws Exception {
        java.util.List<JSONObject> out = new java.util.ArrayList<>();
        Connection con = SPGConect.pool.getConnection();
        try (PreparedStatement ps = con.prepareStatement("select to_json(p) as json from tienda_producto p where p.key_tienda = ? and p.estado > 0 order by p.orden, p.fecha_on")) {
            ps.setString(1, key_tienda);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(new JSONObject(rs.getString("json")));
            }
        } finally {
            SPGConect.pool.releaseConnection(con);
        }
        return out;
    }

    static JSONObject buscarOrden(String key) throws Exception {
        JSONObject o = consulta("select to_json(o) as json from tienda_orden o where o.key = ?", key == null ? "" : key);
        if (o.isEmpty()) throw new Exception("Orden no encontrada");
        return o;
    }

    static JSONObject modelo(String key_modelo) {
        JSONObject r = SocketCliente.sendSinc("inventario", new JSONObject().put("version", "1.0").put("component", "modelo").put("type", "getByKey")
                .put("estado", "cargando").put("key", key_modelo), 20000);
        return r == null || !"exito".equals(r.optString("estado")) ? null : r.optJSONObject("data");
    }

    static JSONArray sucursales(String key_empresa) {
        JSONObject r = SocketCliente.sendSinc("empresa", new JSONObject().put("component", "sucursal").put("type", "getAll")
                .put("estado", "cargando").put("key_empresa", key_empresa), 20000);
        JSONArray out = new JSONArray();
        JSONObject d = r == null ? null : r.optJSONObject("data");
        if (d != null) for (String k : d.keySet()) {
            JSONObject s = d.optJSONObject(k);
            if (s == null || s.optInt("estado", 1) <= 0) continue;
            out.put(new JSONObject().put("key", s.optString("key")).put("nombre", s.optString("descripcion"))
                    .put("direccion", s.optString("direccion", "")).put("lat", s.opt("latitude") == null ? s.opt("lat") : s.opt("latitude"))
                    .put("lng", s.opt("longitude") == null ? s.opt("lng") : s.opt("longitude")));
        }
        return out;
    }

    private static JSONArray filtrarSucursales(JSONArray todas, JSONArray permitidas) {
        if (permitidas == null || permitidas.length() == 0) return todas;
        java.util.Set<String> ok = new java.util.HashSet<>();
        for (int i = 0; i < permitidas.length(); i++) ok.add(permitidas.optString(i));
        JSONArray out = new JSONArray();
        for (int i = 0; i < todas.length(); i++) if (ok.contains(todas.getJSONObject(i).optString("key"))) out.put(todas.get(i));
        return out;
    }

    static JSONObject bus(String servicio, JSONObject req) throws Exception {
        return bus(servicio, req, 20000);
    }

    static JSONObject bus(String servicio, JSONObject req, int timeout) throws Exception {
        JSONObject r = SocketCliente.sendSinc(servicio, req, timeout);
        if (r == null) throw new Exception("Sin respuesta del servicio " + servicio);
        if (!"exito".equals(r.optString("estado"))) throw new Exception(servicio + ": " + r.optString("error", r.optString("mensaje", "error")));
        return r;
    }

    private static String texto(JSONObject c, String campo, int max, boolean requerido) throws Exception {
        String v = c.optString(campo, "").trim().replaceAll("\\s+", " ");
        if (requerido && v.isEmpty()) throw new Exception("Falta " + campo);
        if (v.length() > max) throw new Exception("El campo " + campo + " es demasiado largo");
        return v;
    }

    static String recortar(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    static JSONObject consulta(String sql, String... params) throws Exception {
        Connection con = SPGConect.pool.getConnection();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setString(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getString("json") != null) return new JSONObject(rs.getString("json"));
                return new JSONObject();
            }
        } finally {
            SPGConect.pool.releaseConnection(con);
        }
    }

    static int actualizar(String sql, String... params) throws Exception {
        Connection con = SPGConect.pool.getConnection();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setString(i + 1, params[i]);
            return ps.executeUpdate();
        } finally {
            SPGConect.pool.releaseConnection(con);
        }
    }
}
