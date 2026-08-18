package Component;

import org.json.JSONArray;
import org.json.JSONObject;

import Servisofts.SConfig;
import Servisofts.SPGConect;
import Servisofts.SUtil;
import Server.SSSAbstract.SSSessionAbstract;

public class Alvaro {
    public static final String COMPONENT = "alvaro";

    // ========== CONFIGURACIÓN POR ENTORNO ==========
    // TRUE = Lee de config.json (RECOMENDADO PARA PRODUCCIÓN)
    // FALSE = Usa valores hardcodeados abajo (opcional)

    private static final boolean USE_CONFIG_FILE = false; // ← CAMBIAR AQUÍ: true o false

    // Valores hardcodeados (SOLO si USE_CONFIG_FILE = false)
    // private static final String PROD_SCRIPTS_DIR =
    // "/home/servisofts/servicios/serp/scripts";
    // private static final String PROD_BACKUPS_DIR =
    // "/home/servisofts/servicios/serp/backups";
    // private static final String PROD_SSH_HOST = "servisofts@192.168.2.5";
    // private static final String PROD_REMOTE_DIR =
    // "/home/servisofts/servicios/serp/entornos/serp/servicios/serp";

    private static JSONObject getAlvaroConfig() {
        JSONObject config = SConfig.getJSON();
        if (config != null && config.has("alvaro")) {
            return config.getJSONObject("alvaro");
        }
        return new JSONObject();
    }

    private static String getScriptPath(String scriptName) {
        String scriptsDir = getScriptsDir();
        return scriptsDir + "/" + scriptName;
    }

    private static String getScriptsDir() {
        if (!USE_CONFIG_FILE) {
            return "/home/servisofts/servicios/serp/scripts";
        }
        JSONObject alvaroConfig = getAlvaroConfig();
        if (alvaroConfig.has("scripts_dir")) {
            return alvaroConfig.getString("scripts_dir");
        }
        throw new RuntimeException("scripts_dir no configurado en alvaro config");
    }

    private static String getSSHHost() {
        if (!USE_CONFIG_FILE) {
            return "servisofts@192.168.2.5";
        }
        JSONObject alvaroConfig = getAlvaroConfig();
        if (!alvaroConfig.has("ssh_host")) {
            throw new RuntimeException("ssh_host no configurado en alvaro config");
        }
        return alvaroConfig.getString("ssh_host");
    }

    private static String getBackupsDir() {
        if (!USE_CONFIG_FILE) {
            return "/home/servisofts/servicios/serp/backups";
        }
        JSONObject alvaroConfig = getAlvaroConfig();
        if (!alvaroConfig.has("backups_dir")) {
            throw new RuntimeException("backups_dir no configurado en alvaro config");
        }
        return alvaroConfig.getString("backups_dir");
    }

    private static String getRemoteDir() {
        if (!USE_CONFIG_FILE) {
            return "/home/servisofts/servicios/serp/entornos/serp/servicios/serp";
        }
        JSONObject alvaroConfig = getAlvaroConfig();
        if (!alvaroConfig.has("remote_dir")) {
            throw new RuntimeException("remote_dir no configurado en alvaro config");
        }
        return alvaroConfig.getString("remote_dir");
    }

    public static void onMessage(JSONObject obj, SSSessionAbstract session) {
        switch (obj.getString("type")) {
            case "crearBackup":
                crearBackup(obj, session);
                break;
            case "crearBackupBackend":
                crearBackupBackend(obj, session);
                break;
            case "crearBackupFrontend":
                crearBackupFrontend(obj, session);
                break;
            case "backend_ver_backup":
                verBackupBackend(obj, session);
                break;
            case "frontend_ver_backup":
                verBackupFrontend(obj, session);
                break;
            case "eliminarBackupBackend":
                eliminarBackupBackend(obj, session);
                break;
            case "restaurarBackupBackend":
                restaurarBackupBackend(obj, session);
                break;
            case "eliminarBackupFrontend":
                eliminarBackupFrontend(obj, session);
                break;
            case "restaurarBackupFrontend":
                restaurarBackupFrontend(obj, session);
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

            String rutaScript = getScriptPath("alvaro_backend_backup.sh");
            String rutaBackups = getBackupsDir();
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

            String nombreBackup = "server.jar_"
                    + new java.text.SimpleDateFormat("yyyy-MM-dd_HHmmss").format(new java.util.Date());
            String rutaCompleta = rutaBackups + "/" + nombreBackup;

            JSONObject backup = new JSONObject();
            backup.put("key", SUtil.uuid());
            backup.put("estado", 1);
            backup.put("key_usuario", obj.optString("key_usuario", "sistema"));
            backup.put("key_empresa", obj.optString("key_empresa", "empresa"));
            backup.put("nombre", nombreBackup);
            backup.put("ruta", rutaCompleta);
            backup.put("descripcion", obj.optString("descripcion", "").replaceAll("'", "''"));
            backup.put("fecha_creacion", SUtil.now());
            backup.put("fecha_backup", SUtil.now());

            java.io.File archivoBackup = new java.io.File(rutaCompleta);
            if (archivoBackup.exists()) {
                long tamaño = archivoBackup.length();
                backup.put("tamaño", formatearTamaño(tamaño));
                System.out.println(
                        "[ALVARO] Archivo backup encontrado: " + nombreBackup + " (" + formatearTamaño(tamaño) + ")");
            } else {
                backup.put("tamaño", "0");
                System.out.println("[ALVARO] Advertencia: Archivo backup no encontrado en: " + rutaCompleta);
            }

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
            obj.put("mensaje", "Backup creado exitosamente: " + nombreBackup);
            System.out.println("[ALVARO] ========== crearBackup COMPLETADO ==========");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            System.out.println("[ALVARO] EXCEPCIÓN EN crearBackup: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void crearBackupBackend(JSONObject obj, SSSessionAbstract session) {
        try {
            System.out.println("[ALVARO] ========== INICIANDO crearBackupBackend ==========");
            System.out.println("[ALVARO] Datos recibidos: " + obj.toString());

            String rutaScript = getScriptPath("alvaro_backend_backup.sh");
            String rutaBackups = getBackupsDir();
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

    public static void crearBackupFrontend(JSONObject obj, SSSessionAbstract session) {
        try {
            System.out.println("[ALVARO] ========== INICIANDO crearBackupFrontend ==========");
            System.out.println("[ALVARO] Datos recibidos: " + obj.toString());

            String rutaScript = getScriptPath("alvaro_frontend_backup.sh");
            String remoteDir = getRemoteDir();
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
                obj.put("error", "Error al ejecutar script de backup frontend (código: " + exitCode + ")");
                System.out.println("[ALVARO] Error: Script retornó código " + exitCode);
                return;
            }

            System.out.println("[ALVARO] Script ejecutado exitosamente");

            String nombreBackup = "build_"
                    + new java.text.SimpleDateFormat("yyyy-MM-dd_HHmmss").format(new java.util.Date());
            String rutaCompleta = remoteDir + "/" + nombreBackup;

            JSONObject backup = new JSONObject();
            backup.put("key", SUtil.uuid());
            backup.put("estado", 1);
            backup.put("key_usuario", obj.optString("key_usuario", "sistema"));
            backup.put("key_empresa", obj.optString("key_empresa", "empresa"));
            backup.put("nombre", nombreBackup);
            backup.put("ruta", rutaCompleta);
            backup.put("descripcion", obj.optString("descripcion", "").replaceAll("'", "''"));
            backup.put("tipo", "frontend");
            backup.put("fecha_creacion", SUtil.now());
            backup.put("fecha_backup", SUtil.now());

            obj.put("data", backup);
            obj.put("estado", "exito");
            obj.put("mensaje", "Backup frontend creado exitosamente: " + nombreBackup);
            System.out.println("[ALVARO] ========== crearBackupFrontend COMPLETADO ==========");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            System.out.println("[ALVARO] EXCEPCIÓN EN crearBackupFrontend: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void verBackupBackend(JSONObject obj, SSSessionAbstract session) {
        try {
            System.out.println("[ALVARO] ========== LISTANDO BACKUPS DE BACKEND ==========");
            String backupsDir = getBackupsDir();
            System.out.println("[ALVARO] Directorio de backups: " + backupsDir);

            java.io.File dir = new java.io.File(backupsDir);

            if (!dir.exists()) {
                obj.put("estado", "error");
                obj.put("error", "Directorio de backups no existe: " + backupsDir);
                System.out.println("[ALVARO] Error: Directorio no existe: " + backupsDir);
                return;
            }

            if (!dir.isDirectory()) {
                obj.put("estado", "error");
                obj.put("error", "No es un directorio: " + backupsDir);
                System.out.println("[ALVARO] Error: No es un directorio: " + backupsDir);
                return;
            }

            java.io.File[] archivos = dir.listFiles((d, name) -> name.startsWith("server"));

            if (archivos == null) {
                archivos = new java.io.File[0];
            }

            JSONArray backupsArray = new JSONArray();

            for (java.io.File archivo : archivos) {
                if (archivo.isFile()) {
                    String nombre = archivo.getName();
                    String tamaño = formatearTamaño(archivo.length());
                    long timestamp = archivo.lastModified();
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    String fecha = sdf.format(new java.util.Date(timestamp));

                    JSONObject backupInfo = new JSONObject();
                    backupInfo.put("nombre", nombre);
                    backupInfo.put("ruta", archivo.getAbsolutePath());
                    backupInfo.put("tamaño", tamaño);
                    backupInfo.put("tipo", "backend");
                    backupInfo.put("fecha", fecha);
                    backupInfo.put("timestamp", timestamp);

                    backupsArray.put(backupInfo);
                    System.out.println("[ALVARO] ✓ Agregado: " + nombre + " | " + tamaño + " | " + fecha);
                }
            }

            obj.put("data", backupsArray);
            obj.put("estado", "exito");
            obj.put("cantidad", backupsArray.length());
            System.out.println("[ALVARO] Se encontraron " + backupsArray.length() + " backups de backend");
            System.out.println("[ALVARO] ========== COMPLETADO ==========");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            System.out.println("[ALVARO] Error listando backups de backend: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void verBackupFrontend(JSONObject obj, SSSessionAbstract session) {
        try {
            System.out.println("[ALVARO] ========== LISTANDO BACKUPS DE FRONTEND ==========");
            String remoteDir = getRemoteDir();
            System.out.println("[ALVARO] Directorio remoto: " + remoteDir);

            java.io.File dir = new java.io.File(remoteDir);

            if (!dir.exists()) {
                obj.put("estado", "error");
                obj.put("error", "Directorio remoto no existe: " + remoteDir);
                System.out.println("[ALVARO] Error: Directorio no existe: " + remoteDir);
                return;
            }

            if (!dir.isDirectory()) {
                obj.put("estado", "error");
                obj.put("error", "No es un directorio: " + remoteDir);
                System.out.println("[ALVARO] Error: No es un directorio: " + remoteDir);
                return;
            }

            java.io.File[] archivos = dir.listFiles((d, name) -> name.startsWith("build"));

            if (archivos == null) {
                archivos = new java.io.File[0];
            }

            JSONArray backupsArray = new JSONArray();

            for (java.io.File archivo : archivos) {
                if (archivo.isDirectory()) {
                    String nombre = archivo.getName();
                    long tamañoDir = obtenerTamañoDirectorio(archivo);
                    String tamaño = formatearTamaño(tamañoDir);
                    long timestamp = archivo.lastModified();
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    String fecha = sdf.format(new java.util.Date(timestamp));

                    JSONObject backupInfo = new JSONObject();
                    backupInfo.put("nombre", nombre);
                    backupInfo.put("ruta", archivo.getAbsolutePath());
                    backupInfo.put("tamaño", tamaño);
                    backupInfo.put("tipo", "frontend");
                    backupInfo.put("fecha", fecha);
                    backupInfo.put("timestamp", timestamp);

                    backupsArray.put(backupInfo);
                    System.out.println("[ALVARO] ✓ Agregado: " + nombre + " | " + tamaño + " | " + fecha);
                }
            }

            obj.put("data", backupsArray);
            obj.put("estado", "exito");
            obj.put("cantidad", backupsArray.length());
            System.out.println("[ALVARO] Se encontraron " + backupsArray.length() + " backups de frontend");
            System.out.println("[ALVARO] ========== COMPLETADO ==========");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            System.out.println("[ALVARO] Error listando backups de frontend: " + e.getMessage());
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

    private static long obtenerTamañoDirectorio(java.io.File dir) {
        long tamaño = 0;
        if (dir.isDirectory()) {
            java.io.File[] archivos = dir.listFiles();
            if (archivos != null) {
                for (java.io.File archivo : archivos) {
                    if (archivo.isDirectory()) {
                        tamaño += obtenerTamañoDirectorio(archivo);
                    } else {
                        tamaño += archivo.length();
                    }
                }
            }
        }
        return tamaño;
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

    public static void eliminarBackupBackend(JSONObject obj, SSSessionAbstract session) {
        try {
            System.out.println("[ALVARO] ========== ELIMINANDO BACKUP BACKEND ==========");
            String rutaBackup = obj.optString("ruta", "");

            if (rutaBackup.isEmpty()) {
                obj.put("estado", "error");
                obj.put("error", "Ruta del backup no especificada");
                return;
            }

            java.io.File archivo = new java.io.File(rutaBackup);

            if (!archivo.exists()) {
                obj.put("estado", "error");
                obj.put("error", "Archivo no existe: " + rutaBackup);
                System.out.println("[ALVARO] Error: Archivo no existe: " + rutaBackup);
                return;
            }

            if (archivo.delete()) {
                obj.put("estado", "exito");
                obj.put("mensaje", "Backup backend eliminado exitosamente");
                System.out.println("[ALVARO] Backup eliminado: " + rutaBackup);
            } else {
                obj.put("estado", "error");
                obj.put("error", "No se pudo eliminar el archivo");
                System.out.println("[ALVARO] Error: No se pudo eliminar: " + rutaBackup);
            }
            System.out.println("[ALVARO] ========== COMPLETADO ==========");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            System.out.println("[ALVARO] Error eliminando backup backend: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void restaurarBackupBackend(JSONObject obj, SSSessionAbstract session) {
        try {
            System.out.println("[ALVARO] ========== RESTAURANDO BACKUP BACKEND ==========");
            String rutaBackup = obj.optString("ruta", "");
            String remoteDir = getRemoteDir();
            String rutaDestino = remoteDir + "/server.jar";

            if (rutaBackup.isEmpty()) {
                obj.put("estado", "error");
                obj.put("error", "Ruta del backup no especificada");
                return;
            }

            java.io.File archivoBackup = new java.io.File(rutaBackup);

            if (!archivoBackup.exists()) {
                obj.put("estado", "error");
                obj.put("error", "Archivo de backup no existe: " + rutaBackup);
                System.out.println("[ALVARO] Error: Archivo no existe: " + rutaBackup);
                return;
            }

            java.io.File archivoDestino = new java.io.File(rutaDestino);

            try {
                copiarArchivo(archivoBackup, archivoDestino);
                obj.put("estado", "exito");
                obj.put("mensaje", "Backup backend restaurado exitosamente");
                System.out.println("[ALVARO] Backup restaurado: " + rutaBackup + " -> " + rutaDestino);
            } catch (Exception ex) {
                obj.put("estado", "error");
                obj.put("error", "Error al copiar archivo: " + ex.getMessage());
                System.out.println("[ALVARO] Error: " + ex.getMessage());
            }

            System.out.println("[ALVARO] ========== COMPLETADO ==========");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            System.out.println("[ALVARO] Error restaurando backup backend: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void copiarArchivo(java.io.File origen, java.io.File destino) throws Exception {
        java.io.FileInputStream fis = new java.io.FileInputStream(origen);
        java.io.FileOutputStream fos = new java.io.FileOutputStream(destino);

        byte[] buffer = new byte[1024];
        int longitud;

        while ((longitud = fis.read(buffer)) > 0) {
            fos.write(buffer, 0, longitud);
        }

        fis.close();
        fos.close();
    }

    public static void eliminarBackupFrontend(JSONObject obj, SSSessionAbstract session) {
        try {
            System.out.println("[ALVARO] ========== ELIMINANDO BACKUP FRONTEND ==========");
            String rutaBackup = obj.optString("ruta", "");

            if (rutaBackup.isEmpty()) {
                obj.put("estado", "error");
                obj.put("error", "Ruta del backup no especificada");
                return;
            }

            java.io.File archivo = new java.io.File(rutaBackup);

            if (!archivo.exists()) {
                obj.put("estado", "error");
                obj.put("error", "Directorio no existe: " + rutaBackup);
                System.out.println("[ALVARO] Error: Directorio no existe: " + rutaBackup);
                return;
            }

            if (eliminarDirectorio(archivo)) {
                obj.put("estado", "exito");
                obj.put("mensaje", "Backup frontend eliminado exitosamente");
                System.out.println("[ALVARO] Backup eliminado: " + rutaBackup);
            } else {
                obj.put("estado", "error");
                obj.put("error", "No se pudo eliminar el directorio");
                System.out.println("[ALVARO] Error: No se pudo eliminar: " + rutaBackup);
            }
            System.out.println("[ALVARO] ========== COMPLETADO ==========");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            System.out.println("[ALVARO] Error eliminando backup frontend: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static boolean eliminarDirectorio(java.io.File dir) {
        if (dir.isDirectory()) {
            java.io.File[] archivos = dir.listFiles();
            if (archivos != null) {
                for (java.io.File archivo : archivos) {
                    if (!eliminarDirectorio(archivo)) {
                        return false;
                    }
                }
            }
        }
        return dir.delete();
    }

    public static void restaurarBackupFrontend(JSONObject obj, SSSessionAbstract session) {
        try {
            System.out.println("[ALVARO] ========== RESTAURANDO BACKUP FRONTEND ==========");
            String rutaBackup = obj.optString("ruta", "");
            String remoteDir = getRemoteDir();
            String buildDir = remoteDir + "/build";

            if (rutaBackup.isEmpty()) {
                obj.put("estado", "error");
                obj.put("error", "Ruta del backup no especificada");
                return;
            }

            java.io.File backupDir = new java.io.File(rutaBackup);

            if (!backupDir.exists() || !backupDir.isDirectory()) {
                obj.put("estado", "error");
                obj.put("error", "Directorio de backup no existe: " + rutaBackup);
                System.out.println("[ALVARO] Error: Directorio no existe: " + rutaBackup);
                return;
            }

            java.io.File dirDestino = new java.io.File(buildDir);

            try {
                if (dirDestino.exists()) {
                    eliminarDirectorio(dirDestino);
                    System.out.println("[ALVARO] Directorio anterior eliminado: " + buildDir);
                }

                copiarDirectorio(backupDir, dirDestino);
                obj.put("estado", "exito");
                obj.put("mensaje", "Backup frontend restaurado exitosamente");
                System.out.println("[ALVARO] Backup restaurado: " + rutaBackup + " -> " + buildDir);
            } catch (Exception ex) {
                obj.put("estado", "error");
                obj.put("error", "Error al restaurar: " + ex.getMessage());
                System.out.println("[ALVARO] Error: " + ex.getMessage());
            }

            System.out.println("[ALVARO] ========== COMPLETADO ==========");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            System.out.println("[ALVARO] Error restaurando backup frontend: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void copiarDirectorio(java.io.File origen, java.io.File destino) throws Exception {
        if (!destino.exists()) {
            destino.mkdirs();
        }

        java.io.File[] archivos = origen.listFiles();
        if (archivos != null) {
            for (java.io.File archivo : archivos) {
                java.io.File nuevaRuta = new java.io.File(destino, archivo.getName());

                if (archivo.isDirectory()) {
                    copiarDirectorio(archivo, nuevaRuta);
                } else {
                    copiarArchivo(archivo, nuevaRuta);
                }
            }
        }
    }

}
