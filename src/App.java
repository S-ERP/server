import Controllers.BancoGanaderoController;
import Controllers.Sapi;
import Servisofts.Servisofts;
import Servisofts.http.Rest;
import Tasks.EmpresaTask;
import Tasks.TareaTask;

public class App {
    
    public static void main(String[] args) {
        try {
            
            Servisofts.DEBUG = false;
            Servisofts.ManejadorCliente = ManejadorCliente::onMessage;
            Servisofts.Manejador = Manejador::onMessage;
            Rest.addController(BancoGanaderoController.class);
            Rest.addController(Sapi.class);
            
            new TareaTask();
            new EmpresaTask();
            Servisofts.initialize();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}