package Servisofts.PG;

import org.json.JSONObject;

public class PGConnectionProps {

    public static PGConnectionProps buildFromJson(JSONObject obj) {
        return new PGConnectionProps(obj.getString("ip"), obj.getInt("puerto"), obj.getString("bd_name"),
                obj.getString("user"), obj.getString("pass"));
    }

    public String ip;
    public int puerto;
    public String bd_name;
    public String user;
    public String pass;

    public PGConnectionProps(String ip, int puerto, String bd_name, String user, String pass) {
        this.ip = ip;
        this.puerto = puerto;
        this.bd_name = bd_name;
        this.user = user;
        this.pass = pass;
    }

}
