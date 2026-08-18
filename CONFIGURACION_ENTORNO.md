# ⚙️ Configuración por Entorno - Alvaro

El código ahora soporta **dos modos de configuración**:

## 🟢 MODO 1: Config.json (Recomendado)

Lee configuraciones del archivo `config.json`.

**Ventajas:**
- ✅ Cambiar configuración sin recompilar
- ✅ Diferentes rutas para local y producción
- ✅ Más fácil de mantener

**Estado actual:** ✅ ACTIVADO

### Cómo usar:

```java
// En src/Component/Alvaro.java, línea ~19:
private static final boolean USE_CONFIG_FILE = true;  // ✅ ACTIVADO
```

El código automáticamente leerá de `config.json`:

```json
{
  "alvaro": {
    "scripts_dir": "/ruta/scripts",
    "backups_dir": "/ruta/backups",
    "ssh_host": "usuario@ip",
    "remote_dir": "/ruta/remota"
  }
}
```

---

## 🔴 MODO 2: Valores Hardcodeados (Para Producción)

Usa valores fijos en el código sin necesidad de `config.json`.

**Ventajas:**
- ✅ No depende de config.json
- ✅ Más seguro si usas secretos

**Cómo activar:**

### Paso 1: Editar `src/Component/Alvaro.java`

Busca las líneas 19-28 y cambia:

```java
// LOCAL - Lee de config.json
// private static final boolean USE_CONFIG_FILE = true;

// PRODUCCIÓN - Usar valores hardcodeados
private static final boolean USE_CONFIG_FILE = false;
private static final String PROD_SCRIPTS_DIR = "/home/servisofts/servicios/serp/scripts";
private static final String PROD_BACKUPS_DIR = "/home/servisofts/servicios/serp/backups";
private static final String PROD_SSH_HOST = "servisofts@192.168.2.5";
private static final String PROD_REMOTE_DIR = "/home/servisofts/servicios/serp/entornos/serp/servicios/serp";
```

### Paso 2: Recompilar

```bash
./compile.sh
```

### Paso 3: Deploy

```bash
scp ./server.jar usuario@ip:/ruta/
```

---

## 📊 Comparación

| Aspecto | Config.json | Hardcodeado |
|--------|------------|------------|
| Cambiar config | ✅ Sin recompilar | ❌ Recompila |
| Múltiples entornos | ✅ Fácil | ❌ Difícil |
| Seguridad | ⚠️ En archivo | ✅ En código |
| Complejidad | ✅ Simple | ✅ Simple |

---

## 🎯 Recomendación

**LOCAL (Desarrollo):** `USE_CONFIG_FILE = true`
- Edita `config.json` con tus rutas locales
- No necesitas recompilar cada vez

**PRODUCCIÓN (Servidor):** Elige uno:
- **Opción A (Recomendado):** `USE_CONFIG_FILE = true`
  - Sube `config.json` con rutas de producción
  - Más flexible
  
- **Opción B (Alternativa):** `USE_CONFIG_FILE = false`
  - Hardcodea valores en el código
  - Más seguro si usas secretos

---

## 📋 Valores por Defecto

Si `USE_CONFIG_FILE = false`, estos son los valores:

```java
PROD_SCRIPTS_DIR = "/home/servisofts/servicios/serp/scripts"
PROD_BACKUPS_DIR = "/home/servisofts/servicios/serp/backups"
PROD_SSH_HOST = "servisofts@192.168.2.5"
PROD_REMOTE_DIR = "/home/servisofts/servicios/serp/entornos/serp/servicios/serp"
```

Puedes cambiarlos directamente en el código.

---

## ✅ Checklist

- [ ] Local: `USE_CONFIG_FILE = true` y `config.json` con rutas locales
- [ ] Producción: Elegir opción A o B
- [ ] Recompilar: `./compile.sh`
- [ ] Subir archivos correspondientes
- [ ] Verificar logs: `[ALVARO]` sin errores
