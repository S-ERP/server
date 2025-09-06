package Tasks;

import org.json.JSONArray;
import org.json.JSONObject;

import Component.EmpresaCierreProgramado;
import Servisofts.SPGConect;

public class EmpresaTask extends Thread {

    private boolean isRun;

    public EmpresaTask() {
        this.isRun = true;
        this.start();

    }

    @Override
    public void run() {
        while (isRun) {
            try {
                Thread.sleep(1000 * 60);
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


}
