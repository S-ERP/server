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
PUERTO="${PUERTO:-10041}"

# Contraseña de sudo (siempre servisofts)
SUDO_PASS="servisofts"

# --- 1) Compilar el .jar ---
echo -e "${BLUE}[1/4] Compilando server.jar...${NC}"
./compile.sh

echo -e "${GREEN}Compilacion completada.${NC}"

# --- 2) Backup del server.jar remoto (con fecha) ---
# Copia el jar que ya esta corriendo en el servidor a un archivo con fecha, antes de subir el nuevo
BACKUP_DATE=$(date +%Y-%m-%d_%H%M%S)

echo -e "${CYAN}[2/4] Respaldando server.jar remoto (fecha: $BACKUP_DATE)...${NC}"
ssh "$SSH_HOST" "cp $REMOTE_DIR/server.jar $REMOTE_DIR/server_$BACKUP_DATE.jar"

echo -e "${GREEN}Backup creado: server_$BACKUP_DATE.jar${NC}"

# --- 3) Subir el jar nuevo ---
echo -e "${CYAN}[3/4] Subiendo server.jar nuevo...${NC}"
scp ./server.jar "$SSH_HOST:$REMOTE_DIR/"

echo -e "${GREEN}server.jar subido.${NC}"

# --- 4) Reiniciar el servicio ---
# Detiene y luego inicia el servicio con el nuevo jar
echo -e "${CYAN}[4/4] Deteniendo servicio...${NC}"

printf '%s\n' "$SUDO_PASS" | ssh -tt "$SSH_HOST" "cd $REMOTE_DIR && ./servisofts.sh down"

echo -e "${CYAN}Iniciando servicio con el nuevo jar...${NC}"

printf '%s\n' "$SUDO_PASS" | ssh -tt "$SSH_HOST" "cd $REMOTE_DIR && ./servisofts.sh up -d"

echo -e "${GREEN}Listo. Servicio reiniciado con el jar nuevo.${NC}"
