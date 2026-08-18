package Component;

import org.json.JSONArray;
import org.json.JSONObject;

import Servisofts.SConfig;
import Servisofts.SPGConect;
import Servisofts.SUtil;
import Server.SSSAbstract.SSSessionAbstract;

public class Alvaro {
    public static final String COMPONENT = "alvaro";

    private static JSONObject getAlvaroConfig() {
        JSONObject config = SConfig.getJSON();
        if (config != null && config.has("alvaro")) {
            return config.getJSONObject("alvaro");
        }
        return new JSONObject();
    }

    private static String getScriptPath(String scriptName) {
        JSONObject alvaroConfig = getAlvaroConfig();
        if (alvaroConfig.has("scripts_dir")) {
            return alvaroConfig.getString("scripts_dir") + "/" + scriptName;
        }
        return scriptName;
    }

    private static String getSSHHost() {
        JSONObject alvaroConfig = getAlvaroConfig();
        return alvaroConfig.optString("ssh_host", "servisofts@192.168.2.5");
    }

    private static String getBackupsDir() {
        JSONObject alvaroConfig = getAlvaroConfig();
        return alvaroConfig.optString("backups_dir", "/home/servisofts/servicios/serp/entornos/serp/servicios/serp");
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

            String nombreBackup = "server.jar_" + new java.text.SimpleDateFormat("yyyy-MM-dd_HHmmss").format(new java.util.Date());
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
                System.out.println("[ALVARO] Archivo backup encontrado: " + nombreBackup + " (" + formatearTamaño(tamaño) + ")");
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

            String nombreBackup = "server_backend_" + new java.text.SimpleDateFormat("yyyy-MM-dd_HHmmss").format(new java.util.Date());
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
                System.out.println("[ALVARO] Archivo backup backend encontrado: " + nombreBackup + " (" + formatearTamaño(tamaño) + ")");
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
            JSONObject alvaroConfig = getAlvaroConfig();
            String remoteDir = alvaroConfig.optString("remote_dir", "~/servicios/serp/entornos/serp");
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

            String nombreBackup = "build_" + new java.text.SimpleDateFormat("yyyy-MM-dd_HHmmss").format(new java.util.Date());
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
            String sshHost = getSSHHost();
            String remoteDir = getBackupsDir();

            String comando = "ssh \"" + sshHost + "\" \"ls -lh " + remoteDir + "/server* 2>/dev/null\"";
            System.out.println("[ALVARO] Ejecutando: " + comando);

            ProcessBuilder pb = new ProcessBuilder("bash", "-c", comando);
            pb.redirectErrorStream(true);
            Process proceso = pb.start();

            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(proceso.getInputStream()));
            JSONArray backupsArray = new JSONArray();
            String linea;

            while ((linea = reader.readLine()) != null) {
                linea = linea.trim();
                if (!linea.isEmpty()) {
                    System.out.println("[ALVARO] Linea raw: " + linea);

                    String[] partes = linea.split("\\s+");
                    System.out.println("[ALVARO] Partes encontradas: " + partes.length);

                    if (partes.length >= 9) {
                        String tamaño = partes[4];
                        String mes = partes[5];
                        String dia = partes[6];
                        String hora = partes[7];
                        String rutaArchivo = partes[8];
                        String nombre = new java.io.File(rutaArchivo).getName();
                        String fecha = mes + " " + dia + " " + hora;

                        JSONObject backupInfo = new JSONObject();
                        backupInfo.put("nombre", nombre);
                        backupInfo.put("ruta", rutaArchivo);
                        backupInfo.put("tamaño", tamaño);
                        backupInfo.put("tipo", "backend");
                        backupInfo.put("fecha", fecha);

                        backupsArray.put(backupInfo);
                        System.out.println("[ALVARO] ✓ Agregado: " + nombre + " | " + tamaño + " | " + fecha);
                    } else {
                        System.out.println("[ALVARO] ✗ Línea ignorada (partes insuficientes): " + partes.length);
                    }
                }
            }

            int exitCode = proceso.waitFor();
            System.out.println("[ALVARO] Exit code: " + exitCode);

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
            String sshHost = getSSHHost();
            JSONObject alvaroConfig = getAlvaroConfig();
            String remoteDir = alvaroConfig.optString("remote_dir", "~/servicios/serp/entornos/serp");

            String comando = "ssh \"" + sshHost + "\" \"ls -lhd " + remoteDir + "/build* 2>/dev/null\"";
            System.out.println("[ALVARO] Ejecutando: " + comando);

            ProcessBuilder pb = new ProcessBuilder("bash", "-c", comando);
            pb.redirectErrorStream(true);
            Process proceso = pb.start();

            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(proceso.getInputStream()));
            JSONArray backupsArray = new JSONArray();
            String linea;

            while ((linea = reader.readLine()) != null) {
                linea = linea.trim();
                if (!linea.isEmpty()) {
                    System.out.println("[ALVARO] Linea raw: " + linea);

                    String[] partes = linea.split("\\s+");
                    System.out.println("[ALVARO] Partes encontradas: " + partes.length);

                    if (partes.length >= 9) {
                        String tamaño = partes[4];
                        String mes = partes[5];
                        String dia = partes[6];
                        String hora = partes[7];
                        String rutaArchivo = partes[8];
                        String nombre = new java.io.File(rutaArchivo).getName();
                        String fecha = mes + " " + dia + " " + hora;

                        JSONObject backupInfo = new JSONObject();
                        backupInfo.put("nombre", nombre);
                        backupInfo.put("ruta", rutaArchivo);
                        backupInfo.put("tamaño", tamaño);
                        backupInfo.put("tipo", "frontend");
                        backupInfo.put("fecha", fecha);

                        backupsArray.put(backupInfo);
                        System.out.println("[ALVARO] ✓ Agregado: " + nombre + " | " + tamaño + " | " + fecha);
                    } else {
                        System.out.println("[ALVARO] ✗ Línea ignorada (partes insuficientes): " + partes.length);
                    }
                }
            }

            int exitCode = proceso.waitFor();
            System.out.println("[ALVARO] Exit code: " + exitCode);

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
        if (bytes <= 0) return "0 B";
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

    public static void eliminarBackupBackend(JSONObject obj, SSSessionAbstract session) {
        try {
            System.out.println("[ALVARO] ========== ELIMINANDO BACKUP BACKEND ==========");
            String sshHost = getSSHHost();
            String rutaBackup = obj.optString("ruta", "");

            if (rutaBackup.isEmpty()) {
                obj.put("estado", "error");
                obj.put("error", "Ruta del backup no especificada");
                return;
            }

            String comando = "ssh \"" + sshHost + "\" \"rm -f '" + rutaBackup + "'\"";
            System.out.println("[ALVARO] Ejecutando: " + comando);

            ProcessBuilder pb = new ProcessBuilder("bash", "-c", comando);
            pb.redirectErrorStream(true);
            Process proceso = pb.start();
            int exitCode = proceso.waitFor();

            if (exitCode == 0) {
                obj.put("estado", "exito");
                obj.put("mensaje", "Backup backend eliminado exitosamente");
                System.out.println("[ALVARO] Backup eliminado: " + rutaBackup);
            } else {
                obj.put("estado", "error");
                obj.put("error", "Error al eliminar el backup (código: " + exitCode + ")");
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
            String sshHost = getSSHHost();
            String rutaBackup = obj.optString("ruta", "");
            String remoteDir = getBackupsDir();

            if (rutaBackup.isEmpty()) {
                obj.put("estado", "error");
                obj.put("error", "Ruta del backup no especificada");
                return;
            }

            String comando = "ssh \"" + sshHost + "\" \"cp '" + rutaBackup + "' '" + remoteDir + "/server.jar'\"";
            System.out.println("[ALVARO] Ejecutando: " + comando);

            ProcessBuilder pb = new ProcessBuilder("bash", "-c", comando);
            pb.redirectErrorStream(true);
            Process proceso = pb.start();
            int exitCode = proceso.waitFor();

            if (exitCode == 0) {
                obj.put("estado", "exito");
                obj.put("mensaje", "Backup backend restaurado exitosamente");
                System.out.println("[ALVARO] Backup restaurado: " + rutaBackup);
            } else {
                obj.put("estado", "error");
                obj.put("error", "Error al restaurar el backup (código: " + exitCode + ")");
            }
            System.out.println("[ALVARO] ========== COMPLETADO ==========");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            System.out.println("[ALVARO] Error restaurando backup backend: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void eliminarBackupFrontend(JSONObject obj, SSSessionAbstract session) {
        try {
            System.out.println("[ALVARO] ========== ELIMINANDO BACKUP FRONTEND ==========");
            String sshHost = getSSHHost();
            String rutaBackup = obj.optString("ruta", "");

            if (rutaBackup.isEmpty()) {
                obj.put("estado", "error");
                obj.put("error", "Ruta del backup no especificada");
                return;
            }

            String rutaAbsoluta = rutaBackup.replace("~/", "/home/servisofts/");
            String comando = "ssh \"" + sshHost + "\" \"rm -rf '" + rutaAbsoluta + "'\"";
            System.out.println("[ALVARO] Ejecutando: " + comando);

            ProcessBuilder pb = new ProcessBuilder("bash", "-c", comando);
            pb.redirectErrorStream(true);
            Process proceso = pb.start();
            int exitCode = proceso.waitFor();

            if (exitCode == 0) {
                obj.put("estado", "exito");
                obj.put("mensaje", "Backup frontend eliminado exitosamente");
                System.out.println("[ALVARO] Backup eliminado: " + rutaAbsoluta);
            } else {
                obj.put("estado", "error");
                obj.put("error", "Error al eliminar el backup (código: " + exitCode + ")");
            }
            System.out.println("[ALVARO] ========== COMPLETADO ==========");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            System.out.println("[ALVARO] Error eliminando backup frontend: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void restaurarBackupFrontend(JSONObject obj, SSSessionAbstract session) {
        try {
            System.out.println("[ALVARO] ========== RESTAURANDO BACKUP FRONTEND ==========");
            String sshHost = getSSHHost();
            String rutaBackup = obj.optString("ruta", "");
            JSONObject alvaroConfig = getAlvaroConfig();
            String remoteDir = alvaroConfig.optString("remote_dir", "~/servicios/serp/entornos/serp");
            String buildDir = remoteDir + "/build";

            if (rutaBackup.isEmpty()) {
                obj.put("estado", "error");
                obj.put("error", "Ruta del backup no especificada");
                return;
            }

            String comando = "ssh \"" + sshHost + "\" \"rm -rf '" + buildDir + "' && cp -r '" + rutaBackup + "' '" + buildDir + "'\"";
            System.out.println("[ALVARO] Ejecutando: " + comando);

            ProcessBuilder pb = new ProcessBuilder("bash", "-c", comando);
            pb.redirectErrorStream(true);
            Process proceso = pb.start();
            int exitCode = proceso.waitFor();

            if (exitCode == 0) {
                obj.put("estado", "exito");
                obj.put("mensaje", "Backup frontend restaurado exitosamente");
                System.out.println("[ALVARO] Backup restaurado: " + rutaBackup);
            } else {
                obj.put("estado", "error");
                obj.put("error", "Error al restaurar el backup (código: " + exitCode + ")");
            }
            System.out.println("[ALVARO] ========== COMPLETADO ==========");
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            System.out.println("[ALVARO] Error restaurando backup frontend: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
