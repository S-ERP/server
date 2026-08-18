#!/bin/bash

SSH_HOST="servisofts@192.168.2.5"
SUDO_PASS="servisofts"

CYAN='\033[0;36m'
GREEN='\033[0;32m'
NC='\033[0m'

scp ./server.jar servisofts@192.168.2.5:/home/servisofts/servicios/serp/entornos/serp/servicios/serp/

# --- 4) Reiniciar el servicio ---
# Detiene y luego inicia el servicio con el nuevo jar
echo -e "${CYAN}[4/4] Deteniendo servicio...${NC}"

printf '%s\n1\n%s\n' "$SUDO_PASS" "$SUDO_PASS" | ssh -tt "$SSH_HOST" "cd ~/servicios/serp && ./servisofts.sh down"

echo -e "${CYAN}Iniciando servicio con el nuevo jar...${NC}"

printf '%s\n1\n%s\n' "$SUDO_PASS" "$SUDO_PASS" | ssh -tt "$SSH_HOST" "cd ~/servicios/serp && ./servisofts.sh up -d"

echo -e "${GREEN}✓ Deploy completado. Servicio reiniciado con el jar nuevo.${NC}"