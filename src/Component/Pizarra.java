package Component;

import java.util.Date;

import org.json.JSONArray;
import org.json.JSONObject;
import Servisofts.SPGConect;
import Servisofts.SUtil;
import SocketCliente.SocketCliente;
import Server.SSSAbstract.SSServerAbstract;
import Server.SSSAbstract.SSSessionAbstract;

public class Pizarra {
    public static final String COMPONENT = "pizarra";

    public static void onMessage(JSONObject obj, SSSessionAbstract session) {
        switch (obj.getString("type")) {
            case "save":
                save(obj, session);
                break;
            case "saveNodo":
                saveNodo(obj, session);
                break;
            case "save_pizarra_usuario":
                save_pizarra_usuario(obj, session);
                break;
            case "get":
                get(obj, session);
                break;

        }
    }

    public static void save(JSONObject obj, SSSessionAbstract session) {
        try {
            JSONObject data = obj.getJSONObject("data");

            JSONArray arr = SPGConect.array_to_json("""
                    SELECT
                        *
                    FROM pizarra
                    WHERE pizarra.estado > 0
                    AND pizarra.key_empresa = '%s'
                    AND pizarra.id = '%s'
                    """.formatted(data.getString("key_empresa"), data.getString("id")));

            if (arr.length() == 0) {
                data.put("key", SUtil.uuid());
                data.put("estado", 1);
                data.put("fecha_on", SUtil.now());
                data.put("fecha_edit", SUtil.now());
                SPGConect.insertArray(COMPONENT, new JSONArray().put(data));
            } else {
                data.put("key", arr.getJSONObject(0).getString("key"));
                data.put("fecha_edit", SUtil.now());
                SPGConect.editObject(COMPONENT, data);
            }
            // SSServerAbstract.sendAllServer(obj.toString());
            // String consulta = "select get_all('" + COMPONENT + "', 'key_empresa', '" +
            // obj.getString("key_empresa")
            // + "') as json";
            // JSONObject data = SPGConect.ejecutarConsultaObject(consulta);
            // obj.put("data", data);
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            e.printStackTrace();
        }
    }

    public static void saveNodo(JSONObject obj, SSSessionAbstract session) {
        try {
            JSONObject data = obj.getJSONObject("data");
            JSONArray arr = SPGConect.array_to_json("""
                    SELECT
                        *
                    FROM pizarra
                    WHERE pizarra.estado > 0
                    AND pizarra.key_empresa = '%s'
                    AND pizarra.id = '%s'
                    """.formatted(data.getString("key_empresa"), data.getString("id")));

            if (arr.length() == 0) {
                data.put("key", SUtil.uuid());
                data.put("estado", 1);
                data.put("fecha_on", SUtil.now());
                data.put("fecha_edit", SUtil.now());
                SPGConect.insertArray(COMPONENT, new JSONArray().put(data));
            } else {
                data.put("key", arr.getJSONObject(0).getString("key"));
                data.put("fecha_edit", SUtil.now());
                JSONArray nodesToEdit = data.getJSONArray("nodes");
                // JSONObject nodoToEdit = data.getJSONObject("node");
                JSONArray nodos = arr.getJSONObject(0).getJSONArray("nodes");
                JSONArray nodos_edit = new JSONArray();
                boolean encontrado = false;
                for (int i = 0; i < nodos.length(); i++) {
                    JSONObject nodo = nodos.getJSONObject(i);
                    for (int j = 0; j < nodesToEdit.length(); j++) {
                        JSONObject nodoToEdit = nodesToEdit.getJSONObject(j);
                        if (nodo.getString("id").equals(nodoToEdit.getString("id"))) {
                            nodo = nodoToEdit;
                            nodesToEdit.remove(j);
                            break;
                        }
                    }
                    nodos_edit.put(nodo);
                }
                if (nodesToEdit.length() > 0) {
                    for (int j = 0; j < nodesToEdit.length(); j++) {
                        JSONObject nodoToEdit = nodesToEdit.getJSONObject(j);
                        nodos_edit.put(nodoToEdit);
                    }
                }
                // if (!encontrado) {
                // nodos_edit.put(nodoToEdit);
                // }
                data.put("nodes", nodos_edit);
                // nodo.put("data", data.getJSONObject("data"));
                // nodo.put("fecha_edit", SUtil.now());

                SPGConect.editObject(COMPONENT, data);
                data.put("nodes", nodesToEdit);

            }
            // SSServerAbstract.sendAllServer(obj.toString());
            // String consulta = "select get_all('" + COMPONENT + "', 'key_empresa', '" +
            // obj.getString("key_empresa")
            // + "') as json";
            // JSONObject data = SPGConect.ejecutarConsultaObject(consulta);
            // obj.put("data", data);
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            e.printStackTrace();
        }
    }

    public static void save_pizarra_usuario(JSONObject obj, SSSessionAbstract session) {
        try {
            JSONObject data = obj.getJSONObject("data");

            JSONArray arr = SPGConect.array_to_json("""
                    SELECT
                        pizarra.*,
                        (
                            SELECT array_to_json(array_agg(row_to_json(t)))
                            FROM (
                                SELECT *
                                FROM pizarra_usuario
                                WHERE pizarra_usuario.estado > 0
                                AND pizarra_usuario.key_pizarra = pizarra.key
                                AND pizarra_usuario.key_usuario = '%s'
                            ) t
                        ) as pizarra_usuario
                    FROM pizarra
                    WHERE pizarra.estado > 0
                    AND pizarra.key_empresa = '%s'
                    AND pizarra.id = '%s'
                    """.formatted(obj.getString("key_usuario"), obj.getString("key_empresa"),
                    data.getString("id_pizarra")));

            if (arr.length() == 0) {
                throw new Exception("La pizarra no existe");
            }

            JSONObject pizarra = arr.getJSONObject(0);

            if (pizarra.isNull("pizarra_usuario") || pizarra.getJSONArray("pizarra_usuario").length() == 0) {

                data.put("key", SUtil.uuid());
                data.put("key_pizarra", pizarra.getString("key"));
                data.put("key_usuario", obj.getString("key_usuario"));
                data.put("estado", 1);
                data.put("fecha_on", SUtil.now());
                data.put("fecha_edit", SUtil.now());
                SPGConect.insertObject("pizarra_usuario", data);
            } else {
                JSONObject pu = pizarra.getJSONArray("pizarra_usuario").getJSONObject(0);
                data.put("key", pu.getString("key"));
                data.put("fecha_edit", SUtil.now());
                SPGConect.editObject("pizarra_usuario", data);
            }

            // obj.put("data", )
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            e.printStackTrace();
        }
    }

    public static void get(JSONObject obj, SSSessionAbstract session) {
        try {

            JSONArray arr = SPGConect.array_to_json("""
                    SELECT
                        pizarra.*,
                        (
                            select to_json(pizarra_usuario.*)
                            FROM pizarra_usuario
                            WHERE pizarra_usuario.key_pizarra = pizarra.key
                            AND pizarra_usuario.estado > 0
                            AND key_usuario = '%s'
                        ) as pizarra_usuario
                    FROM pizarra
                    WHERE pizarra.estado > 0
                    AND pizarra.key_empresa = '%s'
                    AND pizarra.id = '%s'
                    """.formatted(obj.optString("key_usuario", ""), obj.getString("key_empresa"),
                    obj.getString("id_pizarra")));

            obj.put("data", arr.optJSONObject(0));
            // String consulta = "select get_all('" + COMPONENT + "', 'key_empresa', '" +
            // obj.getString("key_empresa")
            // + "') as json";
            // JSONObject data = SPGConect.ejecutarConsultaObject(consulta);
            // obj.put("data", data);
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            e.printStackTrace();
        }
    }

}
