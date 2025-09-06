package ServerHttp;

import Servisofts.SConfig;
import Servisofts.SConsole;
import Servisofts.http.Rest;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.concurrent.Executors;

import org.jboss.com.sun.net.httpserver.HttpServer;
import org.jboss.com.sun.net.httpserver.HttpHandler;
import org.jboss.com.sun.net.httpserver.HttpContext;
import org.jboss.com.sun.net.httpserver.HttpExchange;

public class ServerHttp {

    public static HashMap<String, HttpHandler> context = new HashMap<String, HttpHandler>();

    public static void Start(int puerto) {
        HttpServer server;
        try {
            SConsole.warning("Initializing HttpServer on port ( " + puerto + " )");
            System.setProperty("org.eclipse.jetty.util.log.class", "org.eclipse.jetty.util.log.StdErrLog");
            System.setProperty("org.eclipse.jetty.LEVEL", "OFF");
            org.eclipse.jetty.util.log.Log.getProperties().setProperty("org.eclipse.jetty.util.log.announce", "false");
            org.eclipse.jetty.util.log.Log.getRootLogger().setDebugEnabled(false);
            server = HttpServer.create(new InetSocketAddress(puerto), 0);

            HttpContext apiContext = server.createContext("/api");
            apiContext.setHandler(Api::POST);

            HttpContext uploadContext = server.createContext("/upload");
            uploadContext.setHandler(Upload::handleRequest);

            // HttpContext uploadv2Context = server.createContext("/uploadv2");
            // uploadv2Context.setHandler(Uploadv2::handleRequest);

            Uploadv2 uploadHandler = new Uploadv2();
            server.createContext("/uploadv2", uploadHandler);
            server.setExecutor(Executors.newCachedThreadPool());

            // HttpContext uploadv3Context = server.createContext("/uploadv3");
            // uploadv3Context.setHandler(Uploadv3::handleRequest);

            HttpContext context = server.createContext("/rest/");
            context.setHandler(Rest::RestHandler);

            HttpContext downloadContext = server.createContext("/");
            downloadContext.setHandler(Download::handleRequest);

            for (String key : ServerHttp.context.keySet()) {
                HttpContext ctx = server.createContext(key);
                ctx.setHandler(ServerHttp.context.get(key));
            }

            server.start();
            SConsole.succes("HttpServer on port ( " + puerto + " ) is ready!");
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public static void addContext(String string, HttpHandler ctx) {
        context.put(string, ctx);   
    }

}