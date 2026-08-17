package Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;
import Server.SSSAbstract.SSSessionAbstract;
import Servisofts.SConfig;
import Servisofts.SPGConect;

public class Reporte {
    public static final String COMPONENT = "reporte";

    public static void onMessage(JSONObject obj, SSSessionAbstract session) {
        switch (obj.getString("type")) {
            case "execute_function":
                execute_function(obj, session);
                break;
        }
    }

    public static String getParamRecursive(JSONObject obj, String param) {
        String val = "";
        String[] points = param.split("\\.");
        if (points.length == 1) {
            return obj.get(points[0]).toString();
        }

        return getParamRecursive(obj.getJSONObject(points[0]), param.replaceAll(points[0] + ".", ""));
    }

    public static void execute_function(JSONObject obj, SSSessionAbstract session) {
        try {

            if (obj.has("service") && !obj.getString("service").equals(SConfig.getJSON().getString("nombre")))
                return;

            if (!obj.has("func"))
                throw new Exception("[func] Parameter not found.");
            if (obj.isNull("func"))
                throw new Exception("[func] Parameter required.");

            String params = "";
            if (obj.has("params") && !obj.isNull("params")) {
                JSONArray arr = obj.getJSONArray("params");
                for (int i = 0; i < arr.length(); i++) {
                    String valParam = arr.get(i).toString();
                    if (valParam.contains("$")) {
                        Pattern patron = Pattern.compile("\\$\\{(.+?)\\}");
                        Matcher matcher = patron.matcher(valParam);
                        if (matcher.find()) {
                            String resultado = matcher.group(1);
                            String p = getParamRecursive(obj, resultado);
                            String p2 = valParam.replaceAll("\\$\\{(.+?)\\}", p);
                            valParam = p2;
                        }
                    }
                    params += valParam;

                    if (i + 1 < arr.length()) {
                        params += ",";
                    }
                }
            }
            String func = obj.getString("func");
            obj.put("data", SPGConect.ejecutarConsultaArray("select " + func + "(" + params + ") as json"));
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("error", e.getLocalizedMessage());
            obj.put("estado", "error");
        }
    }
}
