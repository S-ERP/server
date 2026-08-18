#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

# --- Colores para los mensajes de consola ---
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

echo -e "${CYAN}Backups disponibles en: $REMOTE_DIR${NC}"
echo "=========================================="
ssh "$SSH_HOST" "ls -lh $REMOTE_DIR/server.jar_* 2>/dev/null | awk '{print \$9, \"(\" \$5 \")\"}'"
echo "=========================================="
ssh "$SSH_HOST" "ls -1 $REMOTE_DIR/server.jar_* 2>/dev/null | wc -l" | xargs echo "Total de backups:"
