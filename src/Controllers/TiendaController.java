package Controllers;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.com.sun.net.httpserver.HttpExchange;
import org.json.JSONObject;

import Component.Tienda;
import Servisofts.http.annotation.GetMapping;
import Servisofts.http.annotation.PathVariable;
import Servisofts.http.annotation.PostMapping;
import Servisofts.http.annotation.RequestBody;
import Servisofts.http.annotation.RequestMapping;
import Servisofts.http.annotation.RestController;

/**
 * API pública de la tienda web (https://serp.servisofts.com/http/rest/tienda/...). Ver Component.Tienda.
 *   GET  /tienda/catalogo/{key_tienda}   planes publicados con precio y sucursales
 *   POST /tienda/orden                   {key_tienda, key_producto, key_sucursal, cliente} -> {key, url_pago, monto}
 *   GET  /tienda/orden/{key}             estado (registra la venta si el pago ya está aprobado)
 *   GET  /tienda/solo_web/{key_empresa}  productos "solo web" (los usa el servicio caja)
 * Responde siempre JSON; los errores van como {error}.
 */
@RestController
@RequestMapping("/tienda")
public class TiendaController {

    private static final int MAX_ORDENES = 15, VENTANA_MS = 10 * 60 * 1000;
    private static final Map<String, ArrayDeque<Long>> ordenesPorIp = new ConcurrentHashMap<>();

    @GetMapping("/catalogo/{key_tienda}")
    public String catalogo(@PathVariable("key_tienda") String key_tienda) {
        return json(() -> Tienda.catalogo(key_tienda));
    }

    @PostMapping("/orden")
    public String orden(@RequestBody String body, HttpExchange ex) {
        String ip = ip(ex);
        return json(() -> {
            limitar(ip);
            return Tienda.crearOrden(new JSONObject(body), ip, ex.getRequestHeaders().getFirst("Origin"));
        });
    }

    @GetMapping("/orden/{key}")
    public String estado(@PathVariable("key") String key) {
        return json(() -> Tienda.estadoOrden(key));
    }

    @GetMapping("/solo_web/{key_empresa}")
    public String soloWeb(@PathVariable("key_empresa") String key_empresa) {
        return json(() -> Tienda.soloWeb(key_empresa));
    }

    private static void limitar(String ip) throws Exception {
        long ahora = System.currentTimeMillis();
        ArrayDeque<Long> q = ordenesPorIp.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst() < ahora - VENTANA_MS) q.pollFirst();
            if (q.size() >= MAX_ORDENES) throw new Exception("Demasiados intentos. Esperá unos minutos y volvé a probar.");
            q.addLast(ahora);
        }
    }

    private static String ip(HttpExchange ex) {
        String f = ex.getRequestHeaders().getFirst("X-Forwarded-For");
        if (f == null) f = ex.getRequestHeaders().getFirst("X-Real-IP");
        if (f == null) f = ex.getRemoteAddress().getAddress().getHostAddress();
        return f.split(",")[0].trim();
    }

    interface Accion { JSONObject run() throws Exception; }

    private static String json(Accion a) {
        try {
            return a.run().toString();
        } catch (Exception e) {
            String m = e.getMessage();
            if (m == null || m.isEmpty() || e instanceof org.json.JSONException) {
                e.printStackTrace();
                m = "No se pudo procesar la solicitud";
            }
            return new JSONObject().put("error", m).toString();
        }
    }
}
