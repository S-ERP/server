package Tasks;

import Component.SyncCalistenia;

/**
 * Cada minuto sincroniza paquetes y suscripciones con el sistema antiguo de Calistenia (solo las empresas con el sync
 * encendido en sync_cal_config). Ver Component.SyncCalistenia.
 */
public class SyncCalisteniaTask extends Thread {

    public SyncCalisteniaTask() {
        setDaemon(true);
        setName("SyncCalisteniaTask");
        start();
    }

    @Override
    public void run() {
        while (true) {
            try {
                Thread.sleep(60 * 1000);
                SyncCalistenia.pasadaGeneral();
            } catch (InterruptedException e) {
                return;
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }
}
