import Servisofts.Servisofts;
import Tasks.TareaTask;

public class App {
    
    public static void main(String[] args) {
        try {
            Servisofts.DEBUG = false;
            Servisofts.ManejadorCliente = ManejadorCliente::onMessage;
            Servisofts.Manejador = Manejador::onMessage;
            new TareaTask();
            Servisofts.initialize();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}