package Servisofts.PG;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class PGConnection {

    private PGConnectionProps conf;
    public Connection con;

    public PGConnection(PGConnectionProps conf) throws SQLException {
        this.conf = conf;
        this.con = DriverManager.getConnection("jdbc:postgresql://" + conf.ip + ":" + conf.puerto + "/" + conf.bd_name,
                conf.user,
                conf.pass);
    }
}
