import Controllers.CalisteniaController;
import Controllers.BancoGanaderoController;
import Controllers.CalisteniaController;
import Controllers.Sapi;
import Controllers.TiendaController;
import Servisofts.Servisofts;
import Servisofts.http.Rest;
import Tasks.EmpresaTask;
import Tasks.TareaTask;
import Tasks.TiendaTask;
import Tasks.TiendaCobrosTask;
import Tasks.ZKTecoTask;

public class App {

    public static void main(String[] args) {
        try {
            System.out.println("Versión de Java: " + System.getProperty("java.version"));
            Servisofts.DEBUG = false;
            Servisofts.ManejadorCliente = ManejadorCliente::onMessage;
            Servisofts.Manejador = Manejador::onMessage;
            Rest.addController(BancoGanaderoController.class);
            Rest.addController(Sapi.class);
            Rest.addController(CalisteniaController.class);
            Rest.addController(TiendaController.class);

            new TareaTask();
            new EmpresaTask();
            new ZKTecoTask();
            new TiendaTask();
            new TiendaCobrosTask();
            Servisofts.initialize();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}