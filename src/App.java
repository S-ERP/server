import Controllers.BancoGanaderoController;
import Servisofts.Servisofts;
import Servisofts.http.Rest;
import Tasks.TareaTask;

public class App {
    
    public static void main(String[] args) {
        try {
            Servisofts.DEBUG = false;
            Servisofts.ManejadorCliente = ManejadorCliente::onMessage;
            Servisofts.Manejador = Manejador::onMessage;
            Rest.addController(BancoGanaderoController.class);
            new TareaTask();
            Servisofts.initialize();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}