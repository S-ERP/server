package Server.ServerSocket;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.security.cert.X509Certificate;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import org.json.JSONObject;
import Server.MensajeSocket;
import Server.SSSAbstract.SSSessionAbstract;
import Servisofts.SConsole;
import Servisofts.SUtil;

public class SessionSocket extends SSSessionAbstract {
    private final BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private javax.net.ssl.SSLSocket miSession;
    private PrintWriter outpw = null;
    private X509Certificate cer;

    public SessionSocket(Object session, ServerSocket server) {
        super(session, ((javax.net.ssl.SSLSocket) session).getRemoteSocketAddress().toString(), server);
        this.miSession = (javax.net.ssl.SSLSocket) session;
        this.miSession.addHandshakeCompletedListener(new HandshakeCompletedListener() {
            @Override
            public void handshakeCompleted(HandshakeCompletedEvent event) {
                X509Certificate cerx = (X509Certificate) event.getLocalCertificates()[0];
                cer = cerx;
                printLog("Certificado: " + cer.getSubjectX500Principal().getName());
            }
        });
        try {
            outpw = new PrintWriter(miSession.getOutputStream(), true);
            Start();
            executor.submit(this::processQueue);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onMessage(String mensaje) {
        try {
            SConsole.log("Recivio mensaje", mensaje.length());
            JSONObject data = new JSONObject(mensaje);
            new Thread() {
                public void run() {
                    onMenssage(data);
                };
            }.start();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onClose(JSONObject obj) {
        try {
            executor.shutdownNow();
            miSession.close();
            super.onClose(obj);
            // printLog("Conexion cerrada: ip = " + getIdSession() + " )");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onError(JSONObject obj) {
        printLog("Error: " + obj.getString("error"));
    }

    @Override
    public void send(String mensaje) {
        // MensajeSocket mensajeSocket = new MensajeSocket(mensaje, this);
        messageQueue.offer(mensaje + "---SSkey---" + SUtil.uuid() + "---SSofts---");

    }

    // @Override
    // public void send(String mensaje, MensajeSocket mensajeSocket) {
    // mensajeSocket.setEnvio();
    // outpw.write(mensaje + "---SSkey---" + SUtil.uuid() + "---SSofts--\n");
    // outpw.flush();
    // }

    public void Start() {
        try {
            Thread t = new Thread() {
                @Override
                public void run() {
                    try {
                        // printLog("Nueva session desde " + getIdSession());
                        InputStream inp = null;
                        BufferedReader brinp = null;
                        inp = miSession.getInputStream();
                        brinp = new BufferedReader(new InputStreamReader(inp));
                        onOpen();
                        String line;
                        boolean isRun = true;
                        while (isRun) {
                            try {
                                line = brinp.readLine();
                                if ((line == null) || line.equalsIgnoreCase("QUIT")) {
                                    JSONObject obj = new JSONObject();
                                    obj.put("estado", "close");
                                    onClose(obj);
                                    return;
                                } else {
                                    if (line.length() > 0) {
                                        onMessage(line);
                                    }
                                }
                            } catch (Exception e) {
                                Pattern p = Pattern.compile(".*?onnection.has.closed.*");
                                if (e.getMessage() != null) {
                                    Matcher m = p.matcher(e.getMessage());
                                    boolean b = m.matches();
                                    if (b) {
                                        isRun = false;
                                    }
                                    JSONObject obj = new JSONObject();
                                    obj.put("estado", "error");
                                    obj.put("error", e.getLocalizedMessage());
                                    onError(obj);
                                    SConsole.error("Error en onMessage if e.getMessage() != null ");
                                    // e.printStackTrace();
                                } else {
                                    SConsole.error("Error en onMessage else ");
                                    isRun = false;
                                    e.printStackTrace();
                                }
                            }
                        }
                    } catch (Exception e) {
                        JSONObject obj = new JSONObject();
                        obj.put("estado", "error");
                        obj.put("error", e.getLocalizedMessage());
                        onError(obj);
                    }
                }
            };
            t.start();
        } catch (Exception e) {
            JSONObject obj = new JSONObject();
            obj.put("estado", "error");
            obj.put("error", e.getLocalizedMessage());
            onError(obj);
        }
    }

    @Override
    public void printLog(String mensaje) {
        SConsole.info(getIdSession() + ": " + mensaje);

    }

    @Override
    public boolean isOpen() {
        if (miSession == null) {
            return false;
        }
        if (miSession.isClosed()) {
            return false;
        }
        return true;
    }

    private void processQueue() {
        while (true) {
            try {
                String mensaje = messageQueue.take();
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        // this.miSession.getRemote().sendString(mensaje);
                        outpw.write(mensaje + "---SSkey---" + SUtil.uuid() + "---SSofts---\n");
                        outpw.flush();
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                });

                future.whenComplete((result, ex) -> {
                    if (ex != null) {
                        System.err.println("Failed to send message: " + ex.getMessage());
                        // ex.printStackTrace();
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
