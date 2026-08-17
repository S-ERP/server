package Servisofts.PG;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

import Servisofts.SConsole;

public class PGPool {

    private int min = 10;
    private PGConnectionProps conf;

    private final List<Connection> connections = new ArrayList<>();
    private final List<Connection> usedConnections = new ArrayList<>();

    public PGPool(PGConnectionProps conf) throws SQLException {
        SConsole.warning("Try to instance PGPool to db ",conf.bd_name, conf.ip);
        this.conf = conf;
        for (int i = 0; i < min; i++) {
            createConnection();
        }
    }

    private Connection createConnection() throws SQLException {
        Connection con = DriverManager.getConnection(
                "jdbc:postgresql://" + conf.ip + ":" + conf.puerto + "/" + conf.bd_name,
                conf.user,
                conf.pass);
        connections.add(con);
        return con;
    }

    public Connection getConnection() throws SQLException {
        for (int i = 0; i < 10 * 60; i++) {
            if (!connections.isEmpty()) {
                Connection con = connections.get(connections.size() - 1);
                if (con != null && !con.isClosed()) {
                    connections.remove(con);
                    usedConnections.add(con);
                    // SConsole.log("DB Pool = " + connections.size() + "/" + (this.min));
                    return con;
                }

            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        throw new SQLException("No connections available");
    }

    public void releaseConnection(Connection con) {
        connections.add(con);
        usedConnections.remove(con);
        // SConsole.log("DB Pool = " + connections.size() + "/" + (this.min));
        // SConsole.log("DB Pool = " + connections.size() + "/" + (this.min));
        // SConsole.log("Conexion desocupada: ", connections.size());
    }

    public synchronized void shutdown() throws SQLException {
        for (Connection connection : connections) {
            connection.close();
        }
        for (Connection connection : usedConnections) {
            connection.close();
        }
        connections.clear();
        usedConnections.clear();
    }

    
    public JSONObject getStats() {
        JSONObject obj = new JSONObject();
        obj.put("connections", connections.size()+usedConnections.size());
        obj.put("available_connections", connections.size());
        obj.put("used_connections", usedConnections.size());
        return obj;
    }

}
