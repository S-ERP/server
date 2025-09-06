package Server.ServerSocketWeb;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.JSONObject;

import Server.MensajeSocket;
import Server.SSSAbstract.SSServerAbstract;
import Server.SSSAbstract.SSSessionAbstract;
import Servisofts.SUtil;

public class SessionSocketWeb extends SSSessionAbstract {
    private final BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private org.eclipse.jetty.websocket.api.Session miSession;

    public SessionSocketWeb(Object session) {
        super(session, ((org.eclipse.jetty.websocket.api.Session) session).getRemoteAddress().toString(),
                SSServerAbstract.getServer(SSServerAbstract.TIPO_SOCKET_WEB));
        this.miSession = (org.eclipse.jetty.websocket.api.Session) session;
        this.miSession.setIdleTimeout(1000 * 60 * 60);
        executor.submit(this::processQueue);
        onOpen();

    }

    @Override
    public void onMessage(String mensaje) {
        onMenssage(new JSONObject(mensaje));
    }

    @Override
    public void onClose(JSONObject obj) {
        executor.shutdownNow();
        miSession.close();
        super.onClose(obj);

    }

    @Override
    public void onError(JSONObject obj) {
        // TODO Auto-generated method stub
        System.out.println("Error en la session socket Web ");
        miSession.close();
        super.onClose(obj);
    }

    @Override
    public void send(String mensaje) {
        messageQueue.offer(mensaje + "---SSkey---" + SUtil.uuid() + "---SSofts---");
        // try {
        // // MensajeSocket mensajeSocket = new MensajeSocket(mensaje, this);
        // // Future<Void> fut;
        // // fut = this.miSession.getRemote()
        // // .sendStringByFuture(mensaje + "---SSkey---" + SUtil.uuid() +
        // "---SSofts---");
        // // fut.get(5, TimeUnit.SECONDS); // wait for send to complete.
        // this.miSession.getRemote().sendString(mensaje + "---SSkey---" + SUtil.uuid()
        // + "---SSofts---");
        // } catch (Exception e) {
        // e.printStackTrace();
        // }
    }

    private void processQueue() {
        while (true) {
            try {
                String mensaje = messageQueue.take();
                sendMessageWithRetry(mensaje, 3, 1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void sendMessageWithRetry(String mensaje, int maxRetries, long initialDelay) {
        AtomicInteger attempt = new AtomicInteger(0);
        sendMessage(mensaje, attempt, maxRetries, initialDelay);
    }

    private void sendMessage(String mensaje, AtomicInteger attempt, int maxRetries, long delay) {
        CompletableFuture.runAsync(() -> {
            try {
                this.miSession.getRemote().sendString(mensaje);
            } catch (Exception e) {
                if (attempt.incrementAndGet() < maxRetries) {
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    // Retry with exponential backoff
                    sendMessage(mensaje, attempt, maxRetries, delay * 2);
                } else {
                    throw new CompletionException(e);
                }
            }
        }).whenComplete((result, ex) -> {
            if (ex != null) {
                System.err.println("Failed to send message after retries: " + ex.getMessage());
                ex.printStackTrace();
            }
        });
    }

    @Override
    public void printLog(String mensaje) {
        System.out.println(getIdSession() + ": " + mensaje);

    }

    // @Override
    // public void send(String mensaje, MensajeSocket mensajeSocket) {
    // // TODO Auto-generated method stub

    // }

    @Override
    public boolean isOpen() {
        if (miSession == null) {
            return false;
        }
        return miSession.isOpen();
    }

}
