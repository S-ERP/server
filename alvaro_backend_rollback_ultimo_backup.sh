#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

# --- Colores para los mensajes de consola ---
BLUE='\033[1;34m'
CYAN='\033[1;36m'
GREEN='\033[1;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # sin color

# --- Datos del servidor ---
# Se leen de alvaro.env
ENV_FILE="alvaro.env"
if [ -f "$ENV_FILE" ]; then
    # shellcheck disable=SC1090
    source "$ENV_FILE"
else
    echo -e "${YELLOW}No se encontro $ENV_FILE con los datos del servidor.${NC}"
    exit 1
fi

# El puerto en el que escucha el servicio (usado por sbin/kill.sh para reiniciarlo)
PUERTO="${PUERTO:-10040}"

# Contraseña de sudo (siempre servisofts)
SUDO_PASS="${SUDO_PASS:-servisofts}"

# Se puede pasar el nombre de un backup puntual como argumento (ej: server_2026-08-10_184300.jar)
# Si no se pasa nada, se usa el backup mas reciente que haya en el servidor.
BACKUP_NAME="${1:-}"

# --- 1) Elegir el backup a restaurar ---
echo -e "${BLUE}[1/3] Buscando backup para revertir...${NC}"

if [ -z "$BACKUP_NAME" ]; then
    BACKUP_NAME=$(ssh "$SSH_HOST" "cd $REMOTE_DIR && ls -1 server_*.jar 2>/dev/null | sort | tail -n 1")
fi

if [ -z "$BACKUP_NAME" ]; then
    echo -e "${YELLOW}No se encontro ningun backup (server_*.jar) en $REMOTE_DIR.${NC}"
    exit 1
fi

echo -e "${GREEN}Se va a restaurar: $BACKUP_NAME${NC}"

# --- 2) Quitar lo subido y restaurar el jar anterior ---
# El server.jar actual (lo ultimo subido) se guarda aparte por si hace falta revisarlo despues
UNDO_DATE=$(date +%Y-%m-%d_%H%M%S)

echo -e "${CYAN}[2/3] Restaurando $BACKUP_NAME como server.jar...${NC}"
ssh "$SSH_HOST" "cd $REMOTE_DIR && mv server.jar server_revertido_$UNDO_DATE.jar && cp $BACKUP_NAME server.jar"

echo -e "${GREEN}server.jar restaurado desde $BACKUP_NAME (el que estaba se guardo como server_revertido_$UNDO_DATE.jar).${NC}"

# --- 3) Reiniciar el servicio ---
echo -e "${CYAN}[3/3] Deteniendo servicio...${NC}"

printf '%s\n1\n%s\n' "$SUDO_PASS" "$SUDO_PASS" | ssh -tt "$SSH_HOST" "cd ~/servicios/serp && ./servisofts.sh down"

echo -e "${CYAN}Iniciando servicio con $BACKUP_NAME...${NC}"

printf '%s\n1\n%s\n' "$SUDO_PASS" "$SUDO_PASS" | ssh -tt "$SSH_HOST" "cd ~/servicios/serp && ./servisofts.sh up -d"

echo -e "${GREEN}✓ Rollback completado. Servicio reiniciado con $BACKUP_NAME.${NC}"
