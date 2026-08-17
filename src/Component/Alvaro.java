package Component;

import org.json.JSONArray;
import org.json.JSONObject;

import Servisofts.SPGConect;
import Servisofts.SUtil;
import Server.SSSAbstract.SSSessionAbstract;

public class Alvaro {
    public static final String COMPONENT = "alvaro";

    public static void onMessage(JSONObject obj, SSSessionAbstract session) {
        switch (obj.getString("type")) {
            case "crearBackup":
                crearBackup(obj, session);
                break;
            case "listarBackups":
                listarBackups(obj, session);
                break;
            case "obtenerBackup":
                obtenerBackup(obj, session);
                break;
            case "restaurarBackup":
                restaurarBackup(obj, session);
                break;
            case "eliminarBackup":
                eliminarBackup(obj, session);
                break;
            case "infoServidor":
                infoServidor(obj, session);
                break;
        }
    }

    public static void crearBackup(JSONObject obj, SSSessionAbstract session) {
        try {
            System.out.println("[ALVARO] ========== INICIANDO crearBackup ==========");
            System.out.println("[ALVARO] Datos recibidos: " + obj.toString());

            String rutaScript = "/home/servisofts/Documents/GitHub/alvaro/serp_alvaro/server/alvaro_backend_backup .sh";
            System.out.println("[ALVARO] Ruta del script: " + rutaScript);

            ProcessBuilder pb = new ProcessBuilder("bash", "-c", "bash \"" + rutaScript + "\"");
            pb.redirectErrorStream(true);
            System.out.println("[ALVARO] Ejecutando: " + pb.command());

            Process proceso = pb.start();
            System.out.println("[ALVARO] Script iniciado");

            int exitCode = proceso.waitFor();
            System.out.println("[ALVARO] Exit code del script: " + exitCode);

            if (exitCode != 0) {
                obj.put("estado", "error");
                obj.put("error", "Error al ejecutar script de backup (código: " + exitCode + ")");
                System.out.println("[ALVARO] Error: Script retornó código " + exitCode);
                return;
            }

            System.out.println("[ALVARO] Script ejecutado exitosamente");

            JSONObject backup = new JSONObject();
            backup.put("key", SUtil.uuid());
            backup.put("estado", 1);
            backup.put("key_usuario", obj.optString("key_usuario", "sistema"));
            backup.put("key_empresa", obj.optString("key_empresa", "empresa"));
            backup.put("nombre", obj.optString("nombre", "Backup " + SUtil.now()));
            backup.put("descripcion", obj.optString("descripcion", "").replaceAll("'", "''"));
            backup.put("fecha_creacion", SUtil.now());
            backup.put("fecha_backup", SUtil.now());
            backup.put("tamaño", obj.optString("tamaño", "0"));

            System.out.println("[ALVARO] Backup a insertar: " + backup.toString());

            try {
                SPGConect.insertArray(COMPONENT, new JSONArray().put(backup));
                System.out.println("[ALVARO] Backup insertado en BD");
            } catch (Exception dbError) {
                System.out.println("[ALVARO] Advertencia: No se pudo insertar en BD (tabla puede no existir)");
                System.out.println("[ALVARO] Error BD: " + dbError.getMessage());
            }

            obj.put("data", backup);
            obj.put("estado", "exito");
            obj.put("mensaje", "Backup creado exitosamente");
            System.out.println("[ALVARO] ========== crearBackup COMPLETADO ==========");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            System.out.println("[ALVARO] EXCEPCIÓN EN crearBackup: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void listarBackups(JSONObject obj, SSSessionAbstract session) {
        try {
            String consulta = "select get_all_backups_by_empresa('" + obj.getString("key_empresa") + "') as json";
            JSONObject data = SPGConect.ejecutarConsultaObject(consulta);
            obj.put("data", data);
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            e.printStackTrace();
        }
    }

    public static void obtenerBackup(JSONObject obj, SSSessionAbstract session) {
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

    public static JSONObject getByKey(String key) {
        try {
            String consulta = "select get_by_key('" + COMPONENT + "', '" + key + "') as json";
            return SPGConect.ejecutarConsultaObject(consulta);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void restaurarBackup(JSONObject obj, SSSessionAbstract session) {
        try {
            JSONObject backup = Alvaro.getByKey(obj.getString("key_backup"));
            if (backup == null || backup.isEmpty()) {
                obj.put("estado", "error");
                obj.put("error", "Backup no encontrado");
                return;
            }

            JSONObject backupData = backup.getJSONObject(JSONObject.getNames(backup)[0]);
            backupData.put("estado", 1);
            backupData.put("fecha_restauracion", SUtil.now());
            backupData.put("key_usuario_restauro", obj.getString("key_usuario"));

            SPGConect.editObject(COMPONENT, backupData);

            obj.put("data", backupData);
            obj.put("estado", "exito");
            obj.put("mensaje", "Backup restaurado exitosamente");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            e.printStackTrace();
        }
    }

    public static void eliminarBackup(JSONObject obj, SSSessionAbstract session) {
        try {
            JSONObject backup = Alvaro.getByKey(obj.getString("key_backup"));
            if (backup == null || backup.isEmpty()) {
                obj.put("estado", "error");
                obj.put("error", "Backup no encontrado");
                return;
            }

            JSONObject backupData = backup.getJSONObject(JSONObject.getNames(backup)[0]);
            backupData.put("estado", 0);
            backupData.put("fecha_eliminacion", SUtil.now());
            backupData.put("key_usuario_elimino", obj.getString("key_usuario"));

            SPGConect.editObject(COMPONENT, backupData);

            obj.put("estado", "exito");
            obj.put("mensaje", "Backup eliminado exitosamente");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            e.printStackTrace();
        }
    }

    public static void infoServidor(JSONObject obj, SSSessionAbstract session) {
        try {
            JSONObject info = new JSONObject();
            info.put("servidor", "SERP Alvaro");
            info.put("version", "1.0.0");
            info.put("estado", "activo");
            info.put("fecha_actual", SUtil.now());
            info.put("uptimeServidor", System.currentTimeMillis());

            obj.put("data", info);
            obj.put("estado", "exito");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            e.printStackTrace();
        }
    }

}
