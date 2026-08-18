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
            case "crearBackupBackend":
                crearBackupBackend(obj, session);
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
            String rutaBackups = "/home/servisofts/servicios/serp/entornos/serp/servicios/serp";
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

            String rutaScript = "/home/servisofts/Documents/GitHub/alvaro/serp_alvaro/server/alvaro_backend_backup .sh";
            String rutaBackups = "/home/servisofts/servicios/serp/entornos/serp/servicios/serp";
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

    public static void verBackupBackend(JSONObject obj, SSSessionAbstract session) {
        try {
            System.out.println("[ALVARO] ========== LISTANDO BACKUPS DE BACKEND ==========");
            String sshHost = "servisofts@192.168.2.5";
            String remoteDir = "/home/servisofts/servicios/serp/entornos/serp/servicios/serp";

            String comando = "ssh \"" + sshHost + "\" \"ls -lh " + remoteDir + "/server*.jar 2>/dev/null | awk '{print \\$6, \\$7, \\$8, \\$9, \\\"(\\\" \\$5 \\\")\\\"}' \"";
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
                    System.out.println("[ALVARO] Linea: " + linea);

                    String[] partes = linea.split(" \\(");
                    if (partes.length == 2) {
                        String datosArchivo = partes[0].trim();
                        String tamaño = partes[1].replace(")", "").trim();

                        String[] dateAndFile = datosArchivo.split(" (?=[^ ]*\\.jar)");
                        String fecha = dateAndFile[0].trim();
                        String ruta = dateAndFile.length > 1 ? dateAndFile[1].trim() : "";
                        String nombre = new java.io.File(ruta).getName();

                        JSONObject backupInfo = new JSONObject();
                        backupInfo.put("nombre", nombre);
                        backupInfo.put("ruta", ruta);
                        backupInfo.put("tamaño", tamaño);
                        backupInfo.put("tipo", "backend");
                        backupInfo.put("fecha", fecha);

                        backupsArray.put(backupInfo);
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
            String sshHost = "servisofts@192.168.2.5";
            String remoteDir = "~/servicios/serp/entornos/serp";

            String comando = "ssh \"" + sshHost + "\" \"cd " + remoteDir + " && ls -lhd build* 2>/dev/null | awk '{print \\$6, \\$7, \\$8, \\$9, \\\"(\\\" \\$5 \\\")\\\"}' \"";
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
                    System.out.println("[ALVARO] Linea: " + linea);

                    String[] partes = linea.split(" \\(");
                    if (partes.length == 2) {
                        String datosArchivo = partes[0].trim();
                        String tamaño = partes[1].replace(")", "").trim();

                        String[] dateAndFile = datosArchivo.split(" (?=build)");
                        String fecha = dateAndFile[0].trim();
                        String nombre = dateAndFile.length > 1 ? dateAndFile[1].trim() : "";

                        JSONObject backupInfo = new JSONObject();
                        backupInfo.put("nombre", nombre);
                        backupInfo.put("ruta", remoteDir + "/" + nombre);
                        backupInfo.put("tamaño", tamaño);
                        backupInfo.put("tipo", "frontend");
                        backupInfo.put("fecha", fecha);

                        backupsArray.put(backupInfo);
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

    public static void listarBackups(JSONObject obj, SSSessionAbstract session) {
        try {
            System.out.println("[ALVARO] Listando backups...");
            String rutaBackups = "/home/servisofts/servicios/serp/entornos/serp/servicios/serp";

            java.io.File directorio = new java.io.File(rutaBackups);
            if (!directorio.exists() || !directorio.isDirectory()) {
                System.out.println("[ALVARO] Directorio no existe: " + rutaBackups);
                JSONArray backupsArray = new JSONArray();
                obj.put("data", backupsArray);
                obj.put("estado", "exito");
                obj.put("cantidad", 0);
                return;
            }

            java.io.File[] archivos = directorio.listFiles((dir, name) ->
                name.startsWith("server.jar_") || (name.startsWith("server_") && name.endsWith(".jar"))
            );

            JSONArray backupsArray = new JSONArray();

            if (archivos != null && archivos.length > 0) {
                java.util.Arrays.sort(archivos, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

                for (java.io.File archivo : archivos) {
                    JSONObject backupInfo = new JSONObject();
                    backupInfo.put("nombre", archivo.getName());
                    backupInfo.put("ruta", archivo.getAbsolutePath());
                    backupInfo.put("tamaño", formatearTamaño(archivo.length()));
                    backupInfo.put("tamaño_bytes", archivo.length());
                    backupInfo.put("fecha", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                        .format(new java.util.Date(archivo.lastModified())));
                    backupInfo.put("timestamp", archivo.lastModified());

                    backupsArray.put(backupInfo);
                }
                System.out.println("[ALVARO] Se encontraron " + archivos.length + " backups");
            } else {
                System.out.println("[ALVARO] No se encontraron backups");
            }

            obj.put("data", backupsArray);
            obj.put("estado", "exito");
            obj.put("cantidad", backupsArray.length());
            System.out.println("[ALVARO] Response: " + obj.toString());
        } catch (Exception e) {
            obj.put("estado", "error");
            obj.put("error", e.getMessage());
            System.out.println("[ALVARO] Error listando backups: " + e.getMessage());
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
