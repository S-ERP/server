package Tasks;

public class TareaTask extends Thread {

    private boolean isRun;

    public TareaTask() {
        this.isRun = true;
        this.start();
    }

    @Override
    public void run() {
        while (isRun) {
            try {
                Thread.sleep(1000 * 5);
               
                sync_tareas();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    

    private void sync_tareas() {
        try {

            System.out.println("sync_tareas...>");
            

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
