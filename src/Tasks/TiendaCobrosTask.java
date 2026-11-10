package Tasks;

import java.time.LocalTime;
import java.time.ZoneId;

import org.json.JSONArray;

import Component.TiendaContrato;

/**
 * Cobro de las cuotas de los contratos de la tienda web (planes con débito automático). Cada 10 minutos procesa los
 * cobros pendientes: registra los ya cobrados o pagados por link, y cobra con la tarjeta guardada las cuotas que
 * vencen (los cobros nuevos solo entre las 8:00 y las 20:00 de Bolivia, para que el cliente vea el débito de día).
 */
public class TiendaCobrosTask extends Thread {

    public TiendaCobrosTask() {
        setDaemon(true);
        setName("TiendaCobrosTask");
        start();
    }

    @Override
    public void run() {
        while (true) {
            try {
                Thread.sleep(10 * 60 * 1000);
                int hora = LocalTime.now(ZoneId.of("America/La_Paz")).getHour();
                boolean cobrarNuevos = hora >= 8 && hora < 20;
                JSONArray keys = TiendaContrato.pendientes();
                int procesados = 0, errores = 0;
                String ultimoError = null;
                for (int i = 0; i < keys.length(); i++) {
                    try {
                        if (cobrarNuevos || TiendaContrato.soloRegistrar(keys.getString(i))) {
                            TiendaContrato.procesar(keys.getString(i), false);
                            procesados++;
                        }
                    } catch (Exception e) {
                        errores++;
                        ultimoError = "Cobro " + keys.getString(i).substring(0, 8) + ": " + e.getMessage();
                        System.out.println("TiendaCobrosTask " + keys.getString(i) + ": " + e.getMessage());
                    }
                }
                Component.Tienda.registrarTarea("cobros", procesados, errores, ultimoError);
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                e.printStackTrace();
                Component.Tienda.registrarTarea("cobros", 0, 1, "La pasada falló: " + e.getMessage());
            }
        }
    }
}
