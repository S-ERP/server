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
            case "crearBackupBackend":
                crearBackupBackend(obj, session);
                break;
            case "infoServidor":
                infoServidor(obj, session);
                break;
        }
    }

    public static void crearBackupBackend(JSONObject obj, SSSessionAbstract session) {
        try {
            System.out.println("[ALVARO] ========== INICIANDO crearBackupBackend ==========");
            System.out.println("[ALVARO] Datos recibidos: " + obj.toString());

            String rutaScript = "/u01/servicios/serp/alvaro_backend_backup.sh";
            String rutaBackups = "/u01/servicios/serp";
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
                obj.put("error", "Error al ejecutar script de backup backend (código: " + exitCode + ")");
                System.out.println("[ALVARO] Error: Script retornó código " + exitCode);
                return;
            }

            System.out.println("[ALVARO] Script ejecutado exitosamente");

            String nombreBackup = "server_backend_"
                    + new java.text.SimpleDateFormat("yyyy-MM-dd_HHmmss").format(new java.util.Date());
            String rutaCompleta = rutaBackups + "/" + nombreBackup + ".jar";

            JSONObject backup = new JSONObject();
            backup.put("key", SUtil.uuid());
            backup.put("estado", 1);
            backup.put("key_usuario", obj.optString("key_usuario", "sistema"));
            backup.put("key_empresa", obj.optString("key_empresa", "empresa"));
            backup.put("nombre", nombreBackup);
            backup.put("ruta", rutaCompleta);
            backup.put("descripcion", obj.optString("descripcion", "").replaceAll("'", "''"));
            backup.put("tipo", "backend");
            backup.put("fecha_creacion", SUtil.now());
            backup.put("fecha_backup", SUtil.now());

            java.io.File archivoBackup = new java.io.File(rutaCompleta);
            if (archivoBackup.exists()) {
                long tamaño = archivoBackup.length();
                backup.put("tamaño", formatearTamaño(tamaño));
                System.out.println("[ALVARO] Archivo backup backend encontrado: " + nombreBackup + " ("
                        + formatearTamaño(tamaño) + ")");
            } else {
                backup.put("tamaño", "0");
                System.out.println("[ALVARO] Advertencia: Archivo backup backend no encontrado en: " + rutaCompleta);
            }

            System.out.println("[ALVARO] Backup backend a insertar: " + backup.toString());

            try {
                SPGConect.insertArray(COMPONENT, new JSONArray().put(backup));
                System.out.println("[ALVARO] Backup backend insertado en BD");
            } catch (Exception dbError) {
                System.out.println("[ALVARO] Advertencia: No se pudo insertar en BD (tabla puede no existir)");
                System.out.println("[ALVARO] Error BD: " + dbError.getMessage());
            }

            obj.put("data", backup);
            obj.put("estado", "exito");
            obj.put("mensaje", "Backup backend creado exitosamente: " + nombreBackup);
            System.out.println("[ALVARO] ========== crearBackupBackend COMPLETADO ==========");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            System.out.println("[ALVARO] EXCEPCIÓN EN crearBackupBackend: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String formatearTamaño(long bytes) {
        if (bytes <= 0)
            return "0 B";
        final String[] unidades = { "B", "KB", "MB", "GB" };
        int indice = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format("%.2f %s", bytes / Math.pow(1024, indice), unidades[indice]);
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
