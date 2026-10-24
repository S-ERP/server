package Tasks;

import java.time.LocalDateTime;

import org.json.JSONObject;

import Component.ZKTecoAccesos;
import Servisofts.SConfig;
import Servisofts.SConsole;

// Cada dia a la hora_sincronizacion (4 por defecto) vuelve a cargar los molinetes de las sucursales de SERP,
// para que las suscripciones vencidas dejen de abrir. Las sucursales salen de los puntos de venta zkteco con
// key_servicio = config.json zkteco.key_servicio; sin esa configuracion no hace nada.
public class ZKTecoTask extends Thread {

    private boolean isRun;

    public ZKTecoTask() {
        this.isRun = true;
        this.start();
    }

    @Override
    public void run() {
        String ultimo = "";
        while (isRun) {
            try {
                Thread.sleep(1000 * 60);
                JSONObject config = SConfig.getJSON().optJSONObject("zkteco");
                if (config == null || config.optString("key_servicio").isEmpty()) continue;
                LocalDateTime ahora = LocalDateTime.now();
                String hoy = ahora.toLocalDate().toString();
                if (ahora.getHour() != config.optInt("hora_sincronizacion", 4) || hoy.equals(ultimo)) continue;
                ultimo = hoy;
                for (String key_sucursal : ZKTecoAccesos.getSucursales(config.getString("key_servicio"))) {
                    try {
                        SConsole.succes("ZKTecoTask", key_sucursal, ZKTecoAccesos.sincronizar(key_sucursal).toString());
                    } catch (Exception e) {
                        SConsole.error("ZKTecoTask", key_sucursal, e.getMessage());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
