package Servisofts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import org.json.JSONArray;
import org.json.JSONObject;

public class SPG {

    public static JSONArray all_array(String table, String... wheres) throws SQLException {
        String consulta = String.join("\n", "",
                "SELECT ",
                "array_to_json(array_agg(sq1.*)) as json",
                "FROM (",
                "SELECT * FROM " + table,
                buildWheres(wheres),
                ") sq1",
                "LIMIT 1",
                "");
        return SPGConect.ejecutarConsultaArray(consulta);
    }

    public static JSONObject all_object(String table, String identifier, String... wheres) throws SQLException {
        String consulta = String.join("\n", "",
                "SELECT ",
                "jsonb_object_agg(sq1." + identifier + ",to_json(sq1.*)) as json",
                "FROM (",
                "SELECT * FROM " + table,
                buildWheres(wheres),
                ") sq1",
                "LIMIT 1",
                "");
        return SPGConect.ejecutarConsultaObject(consulta);
    }

    public static JSONObject single_object(String table, String... wheres) throws SQLException {
        String consulta = String.join("\n", "",
                "SELECT ",
                "to_json(sq1.*) as json",
                "FROM (",
                "SELECT * FROM " + table,
                buildWheres(wheres),
                ") sq1",
                "LIMIT 1",
                "");
        return SPGConect.ejecutarConsultaObject(consulta);
    }

    private static String buildWheres(String[] wheres) {
        if (wheres.length <= 0)
            return "";
        String where = "";
        for (int i = 0; i < wheres.length; i++) {
            String whe = wheres[i];
            if (i == 0) {
                where = where + " WHERE ";
            } else {
                where = where + " AND ";
            }
            where = where + " " + whe;
        }
        return where;
    }

    public static JSONArray executeFile(String path, String... params) throws Exception {
        String content = new String(Files.readAllBytes(Paths.get(path)));
        for (int i = 0; i < params.length; i++) {
            content= content.replaceAll("\\$" + (i + 1), params[i]);
        }
        // SConsole.info("executeFile", content);

        PreparedStatement ps = SPGConect.preparedStatement(content);
        ResultSet rs = ps.executeQuery();

        ResultSetMetaData rsmd = rs.getMetaData();
        int columnCount = rsmd.getColumnCount();

        JSONArray arr = new JSONArray();
        String resp = "";
        while (rs.next()) {
            if (columnCount == 1) {
                arr.put(rs.getObject(1));
            } else {
                JSONObject obj = new JSONObject();
                for (int i = 1; i <= columnCount; i++) {
                    String name = rsmd.getColumnName(i);
                    Object o = rs.getObject(name);

                    if (o != null && o.getClass().toString().indexOf("PGobject") > -1) {
                        // System.out.println(.getClass().toString());
                        if (o.toString().startsWith("{")) {
                            obj.put(name, new JSONObject(o.toString()));
                            continue;
                        }
                        if (o.toString().startsWith("[")) {
                            obj.put(name, new JSONArray(o.toString()));
                            continue;
                        }
                    }
                    obj.put(name, rs.getObject(name));
                }
                arr.put(obj);
            }
        }
        rs.close();
        ps.close();
        return arr;
    }
    // public static void main(String[] args) {
    // try {
    // Servisofts.initSPGConect();
    // System.out.println(SPG.all_array("tipo_viaje", "estado = 1"));
    // System.out.println(SPG.all_object("tipo_viaje", "key", "estado = 1"));
    // System.out.println(SPG.single_object(
    // "tipo_viaje",
    // "estado = 1",
    // "key = 'mensajeria-sobre'"));
    // } catch (Exception e) {
    // e.printStackTrace();
    // }
    // }
}
