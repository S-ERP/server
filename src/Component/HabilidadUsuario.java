package Component;

import Servisofts.SUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import Servisofts.SPGConect;
import Server.SSSAbstract.SSSessionAbstract;

public class HabilidadUsuario {
    public static final String COMPONENT = "habilidad_usuario";

    public static void onMessage(JSONObject obj, SSSessionAbstract session) {
        switch (obj.getString("type")) {
            case "getAll":
                getAll(obj, session);
                break;
            case "getAllByUsuario":
                getAllByUsuario(obj, session);
                break;
            case "getAllWithUsuario":
                getAllWithUsuario(obj, session);
                break;                
            case "getByKey":
                getByKey(obj, session);
                break;
            case "registro":
                registro(obj, session);
                break;
            case "registro_all":
                registro_all(obj, session);
                break;
            case "editar":
                editar(obj, session);
                break;
        }
    }

    public static void getAll(JSONObject obj, SSSessionAbstract session) {
        try {
            String consulta = "select get_all('"+COMPONENT+"','key_empresa', '" + obj.getString("key_empresa") + "') as json";
            JSONObject data = SPGConect.ejecutarConsultaObject(consulta);
            obj.put("data", data);
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            e.printStackTrace();
        }
    }

    public static void getAllByUsuario(JSONObject obj, SSSessionAbstract session) {
        try {
            String consulta = "select get_all('"+COMPONENT+"','key_usuario', '" + obj.getString("key_usuario") + "') as json";
            JSONObject data = SPGConect.ejecutarConsultaObject(consulta);
            obj.put("data", data);
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            e.printStackTrace();
        }
    }

    public static void getAllWithUsuario(JSONObject obj, SSSessionAbstract session) {
        try {
            String consulta = "select get_all_habilidad_usuario('" + obj.getString("key_empresa") + "') as json";
            JSONArray data = SPGConect.ejecutarConsultaArray(consulta);
            obj.put("data", data);
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            e.printStackTrace();
        }
    }

    public static void getByKey(JSONObject obj, SSSessionAbstract session) {
        try {
            String consulta = "select get_by_key('" + COMPONENT + "', '" + obj.getString("key") + "') as json";
            JSONObject data = SPGConect.ejecutarConsultaObject(consulta);
            obj.put("data", data);
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            e.printStackTrace();
        }
    }

    public static JSONObject getByKey(String keyTarea) {
        try {
            String consulta = "select get_by_key('" + keyTarea + "') as json";
            return SPGConect.ejecutarConsultaObject(consulta);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void registro(JSONObject obj, SSSessionAbstract session) {
        try {
            JSONObject data = obj.getJSONObject("data");
            data.put("key", SUtil.uuid());
            data.put("estado", 1);
            data.put("fecha_on", SUtil.now());
            data.put("key_usuario", obj.getString("key_usuario"));
            data.put("key_empresa", obj.getString("key_empresa"));
            SPGConect.insertArray(COMPONENT, new JSONArray().put(data));
            obj.put("data", data);
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            e.printStackTrace();
        }
    }

    public static void registro_all(JSONObject obj, SSSessionAbstract session) {
        try {
          
            JSONArray habilidades = obj.getJSONArray("key_habilidades");
            // Obtener las habilidades existentes del usuario
            String consultaExistentes = "select get_all('" + COMPONENT + "','key_usuario', '" + obj.getString("key_usuario") + "') as json";
            JSONObject habilidadesExistentes = SPGConect.ejecutarConsultaObject(consultaExistentes);

            // Crear arrays para controlar las operaciones
            JSONArray habilidadesNuevos = new JSONArray();
            JSONArray keysNuevos = new JSONArray();

            // Recorrer las habilidades nuevas
            for (int i = 0; i < habilidades.length(); i++) {
                String keyHabilidad = habilidades.getString(i);
                keysNuevos.put(keyHabilidad);
                
                // Verificar si la habilidad ya existe
                boolean existe = false;
                if (habilidadesExistentes != null) {
                    for (String keyExistente : habilidadesExistentes.keySet()) {
                        JSONObject habExistente = habilidadesExistentes.getJSONObject(keyExistente);
                        if (habExistente.getString("key_habilidad").equals(keyHabilidad)) {
                            existe = true;
                            break;
                        }
                    }
                }
                
                // Si no existe, preparar para insertar
                if (!existe) {
                    JSONObject nuevaHabilidad = new JSONObject();
                    nuevaHabilidad.put("key", SUtil.uuid());
                    nuevaHabilidad.put("key_usuario", obj.getString("key_usuario"));
                    nuevaHabilidad.put("key_habilidad", keyHabilidad);
                    nuevaHabilidad.put("key_empresa", obj.getString("key_empresa"));
                    nuevaHabilidad.put("estado", 1);
                    nuevaHabilidad.put("fecha_on", SUtil.now());
                    nuevaHabilidad.put("key_usuario", obj.getString("key_usuario"));
                    habilidadesNuevos.put(nuevaHabilidad);
                }
            }

            // Insertar nuevas habilidades
            if (habilidadesNuevos.length() > 0) {
                SPGConect.insertArray(COMPONENT, habilidadesNuevos);
            }

            // Eliminar habilidades que ya no están en la lista
            if (habilidadesExistentes != null) {
                for (String keyExistente : habilidadesExistentes.keySet()) {
                    JSONObject habExistente = habilidadesExistentes.getJSONObject(keyExistente);
                    String keyHabilidadExistente = habExistente.getString("key_habilidad");
                    
                    boolean debeMantenerse = false;
                    for (int i = 0; i < keysNuevos.length(); i++) {
                        if (keysNuevos.getString(i).equals(keyHabilidadExistente)) {
                            debeMantenerse = true;
                            break;
                        }
                    }
                    
                    // Si no debe mantenerse, eliminar (cambiar estado a 0)
                    if (!debeMantenerse) {
                        habExistente.put("estado", 0);
                        SPGConect.editObject(COMPONENT, habExistente);
                    }
                }
            }

            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            e.printStackTrace();
        }
    }

    public static void editar(JSONObject obj, SSSessionAbstract session) {
        try {
            JSONObject data = obj.getJSONObject("data");
            SPGConect.editObject(COMPONENT, data);
            obj.put("data", data);
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            e.printStackTrace();
        }
    }
}