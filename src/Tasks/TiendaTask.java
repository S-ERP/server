package Tasks;

import org.json.JSONArray;

import Component.Tienda;

/**
 * Cada minuto revisa las órdenes de la tienda web con pago pendiente o pagadas sin venta y registra las ventas que
 * falten. Cubre el caso en que el cliente paga y cierra la página antes de que esta consulte el estado.
 */
public class TiendaTask extends Thread {

    public TiendaTask() {
        setDaemon(true);
        setName("TiendaTask");
        start();
    }

    @Override
    public void run() {
        while (true) {
            try {
                Thread.sleep(60 * 1000);
                JSONArray keys = Tienda.pendientesPublico();
                int errores = 0;
                String ultimoError = null;
                for (int i = 0; i < keys.length(); i++) {
                    try {
                        Tienda.procesarPublico(keys.getString(i));
                    } catch (Exception e) {
                        errores++;
                        ultimoError = "Orden " + keys.getString(i).substring(0, 8) + ": " + e.getMessage();
                        e.printStackTrace();
                    }
                }
                Tienda.registrarTarea("ordenes", keys.length(), errores, ultimoError);
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                e.printStackTrace();
                Tienda.registrarTarea("ordenes", 0, 1, "La pasada falló: " + e.getMessage());
            }
        }
    }
}
