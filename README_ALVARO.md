# 🔒 Alvaro - Sistema de Backups para SERP

## 📋 Resumen

Alvaro es un sistema que permite crear, gestionar y restaurar backups del backend y frontend de SERP.

## ✨ Cambios Principales (Versión Producción)

✅ **Configuración Flexible:** El código ahora soporta múltiples entornos sin cambiar el código fuente
✅ **Dos Modos:** Config.json (recomendado) o Hardcodeado
✅ **Fácil Deploy:** Cambiar configuración sin recompilar
✅ **Seguro:** Validación automática de configuraciones

## 🎯 Modos de Configuración

### LOCAL (Desarrollo)
```
USE_CONFIG_FILE = true
↓
Lee de config.json local
↓
Puedes cambiar config sin recompilar
```

### PRODUCCIÓN (Opción A - Recomendada)
```
USE_CONFIG_FILE = true
↓
Sube config.json con rutas de producción
↓
El servidor lee rutas correctas automáticamente
```

### PRODUCCIÓN (Opción B - Alternativa)
```
USE_CONFIG_FILE = false
↓
Hardcodea valores en el código
↓
Recompila y sube solo server.jar
```

## 📂 Archivos de Configuración

```
server/
├── config.json                      ← Configuración principal
├── alvaro.env                       ← Variables para deploy
├── src/Component/Alvaro.java        ← Código principal
├── CONFIGURACION_ENTORNO.md         ← Guía de modos
├── PRODUCTION_SETUP.md              ← Setup completo
├── CAMBIOS_PRODUCCION.md            ← Qué cambió
├── DEPLOY_QUICK.md                  ← Deploy rápido
└── alvaro_backend_deploy.sh         ← Script de deploy
```

## 🚀 Quick Start

### LOCAL
```bash
# Ya funciona, config.json tiene rutas locales
./compile.sh
./run.sh
```

### PRODUCCIÓN (Opción A - Recomendada)
```bash
# 1. Verificar alvaro.env
cat alvaro.env

# 2. Deploy automático
./alvaro_backend_deploy.sh

# 3. Listo ✅
```

### PRODUCCIÓN (Opción B)
```bash
# 1. Editar src/Component/Alvaro.java
#    Cambiar USE_CONFIG_FILE = false
#    Actualizar rutas

# 2. Recompilar
./compile.sh

# 3. Subir
scp ./server.jar usuario@ip:/ruta/

# 4. Reiniciar en servidor
ssh usuario@ip "./servisofts.sh down && ./servisofts.sh up -d"
```

## 📋 Opciones en src/Component/Alvaro.java

```java
// LÍNEA 24: Modo de configuración
private static final boolean USE_CONFIG_FILE = true;  // ← Cambiar esto

// LINEAS 26-28: Valores de producción (si USE_CONFIG_FILE = false)
// private static final String PROD_SCRIPTS_DIR = "...";
// private static final String PROD_BACKUPS_DIR = "...";
// private static final String PROD_SSH_HOST = "...";
// private static final String PROD_REMOTE_DIR = "...";
```

## 📊 Variables de Configuración

| Variable | Descripción | Ejemplo |
|----------|-------------|---------|
| `scripts_dir` | Directorio de scripts de backup | `/home/servisofts/servicios/serp/scripts` |
| `backups_dir` | Directorio de backups | `/home/servisofts/servicios/serp/backups` |
| `ssh_host` | Usuario@IP del servidor | `servisofts@192.168.2.5` |
| `remote_dir` | Ruta remota del servidor | `/home/servisofts/servicios/serp/...` |

## ✅ Funcionalidades

- 💾 Crear backup de backend (server.jar)
- 💾 Crear backup de frontend (carpeta build)
- 📋 Listar backups disponibles
- ↻ Restaurar backups
- 🗑️ Eliminar backups
- ℹ️ Info del servidor

## 🔧 Métodos Principales

```java
// Helpers para obtener configuración
getScriptPath(String scriptName)        // Ruta completa del script
getScriptsDir()                         // Directorio de scripts
getSSHHost()                            // Host SSH
getBackupsDir()                         // Directorio de backups
getRemoteDir()                          // Directorio remoto
```

## 📝 Logs

Los logs de Alvaro tienen prefijo `[ALVARO]`:

```
[ALVARO] ========== INICIANDO crearBackupBackend ==========
[ALVARO] Ruta del script: /home/servisofts/.../script.sh
[ALVARO] Script ejecutado exitosamente
[ALVARO] ========== COMPLETADO ==========
```

## 🛠️ Troubleshooting

### Error: "scripts_dir no configurado"
✅ Solución: Verificar que `config.json` tenga sección `alvaro` con `scripts_dir`

### Error: "ssh_host no configurado"
✅ Solución: Verificar que `config.json` o `USE_CONFIG_FILE = false` tenga SSH host

### Scripts no ejecutan
✅ Solución: Verificar permisos: `chmod +x script.sh`

## 📖 Documentación

- **CONFIGURACION_ENTORNO.md** - Modos de configuración detallados
- **PRODUCTION_SETUP.md** - Setup completo para producción
- **DEPLOY_QUICK.md** - Deploy rápido
- **CAMBIOS_PRODUCCION.md** - Resumen de cambios

## 🎯 Recomendación Final

**Para Producción:** Usa **OPCIÓN A**
- Edita `alvaro.env` con rutas de producción
- Run: `./alvaro_backend_deploy.sh`
- Todo automático ✨

¿Preguntas? Ver: `CONFIGURACION_ENTORNO.md`
