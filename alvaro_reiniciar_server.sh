#!/bin/bash

# ✅ Listo. Creé alvaro_reiniciar_server.sh con las siguientes características:
# Uso:

# ./alvaro_reiniciar_server.sh <nombre_servidor>
# Ejemplos:

# ./alvaro_reiniciar_server.sh serp
# ./alvaro_reiniciar_server.sh facturacion
# ./alvaro_reiniciar_server.sh empresa

export LC_NUMERIC=C

# Colores
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

# Configuración SSH
SSH_USER="${SSH_USER:-root}"
SUDO_PASS="${SUDO_PASS:-servisofts}"

declare -A servidores=(
  ["empresa"]="192.168.5.29"
  ["roles"]="192.168.5.16"
  ["servicios"]="192.168.5.1"
  ["usuario"]="192.168.5.2"
  ["facturacion"]="192.168.5.28"
  ["spdf"]="192.168.5.46"
  ["drive"]="192.168.5.17"
  ["notification"]="192.168.5.33"
  ["chat"]="192.168.5.9"
  ["calistenia"]="192.168.5.18"
  ["geolocation"]="192.168.5.5"
  ["proyecto"]="192.168.5.14"
  ["serp"]="192.168.5.48"
  ["caja"]="192.168.5.45"
  ["compra-venta"]="192.168.5.41"
  ["crm"]="192.168.5.51"
  ["inventario"]="192.168.5.39"
  ["contabilidad"]="192.168.5.11"
  ["sqr"]="192.168.5.34"
  ["zkteco"]="192.168.5.32"
  ["nginx"]="192.168.2.3"
  ["wireguard"]="192.168.2.4"
)

LINEA="────────────────────────────────────────────────────────────"

# Función para detener un servidor
detener_servidor() {
  local nombre=$1
  local ip=$2

  echo -ne "${YELLOW}[DETENIENDO] $nombre ($ip)...${NC}"

  SSH_HOST="${SSH_USER}@${ip}"
  printf '%s\n1\n%s\n' "$SUDO_PASS" "$SUDO_PASS" | ssh -tt "$SSH_HOST" "cd ~/servicios/$nombre && ./servisofts.sh down" 2>/dev/null

  if [ $? -eq 0 ]; then
    echo -e " ${GREEN}✓${NC}"
    return 0
  else
    echo -e " ${RED}✗${NC}"
    return 1
  fi
}

# Función para iniciar un servidor
iniciar_servidor() {
  local nombre=$1
  local ip=$2

  echo -ne "${CYAN}[INICIANDO] $nombre ($ip)...${NC}"

  SSH_HOST="${SSH_USER}@${ip}"
  printf '%s\n1\n%s\n' "$SUDO_PASS" "$SUDO_PASS" | ssh -tt "$SSH_HOST" "cd ~/servicios/$nombre && ./servisofts.sh up -d" 2>/dev/null

  if [ $? -eq 0 ]; then
    echo -e " ${GREEN}✓${NC}"
    return 0
  else
    echo -e " ${RED}✗${NC}"
    return 1
  fi
}

# Función para verificar si servidor está online
verificar_online() {
  local ip=$1
  local max_intentos=30
  local intento=0

  while [ $intento -lt $max_intentos ]; do
    if ping -c 1 -W 1 "$ip" &>/dev/null; then
      return 0
    fi
    intento=$((intento + 1))
    sleep 1
  done
  return 1
}

# Verificar que se pasó un nombre de servidor
if [ -z "$1" ]; then
  echo -e "${RED}Error: Debe especificar un nombre de servidor${NC}"
  echo ""
  echo "Uso: $0 <nombre_servidor>"
  echo ""
  echo "Servidores disponibles:"
  for nombre in "${!servidores[@]}"; do
    printf "  %-20s %s\n" "$nombre" "${servidores[$nombre]}"
  done | sort
  exit 1
fi

SERVIDOR=$1
IP="${servidores[$SERVIDOR]}"

# Verificar que el servidor existe
if [ -z "$IP" ]; then
  echo -e "${RED}Error: Servidor '$SERVIDOR' no encontrado${NC}"
  echo ""
  echo "Servidores disponibles:"
  for nombre in "${!servidores[@]}"; do
    printf "  %-20s %s\n" "$nombre" "${servidores[$nombre]}"
  done | sort
  exit 1
fi

echo "╔════════════════════════════════════════════════════════════╗"
echo "║      REINICIO DE SERVIDOR: $SERVIDOR"
echo "║      IP: $IP"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""

# FASE 1: APAGAR
echo -e "${YELLOW}FASE 1: APAGANDO SERVIDOR${NC}"
echo "$LINEA"
detener_servidor "$SERVIDOR" "$IP"

echo ""
echo -e "${CYAN}Esperando a que el servidor se apague...${NC}"
sleep 5

# FASE 2: ENCENDER
echo ""
echo -e "${CYAN}FASE 2: ENCENDIENDO SERVIDOR${NC}"
echo "$LINEA"
iniciar_servidor "$SERVIDOR" "$IP"

echo -ne "${CYAN}Esperando que $SERVIDOR esté online...${NC}"
if verificar_online "$IP"; then
  echo -e " ${GREEN}✓${NC}"
else
  echo -e " ${RED}✗ No respondió después de 30s${NC}"
fi

# FASE 3: VERIFICACIÓN FINAL
echo ""
echo "$LINEA"
echo -e "${GREEN}REINICIO COMPLETADO${NC}"
echo "╔════════════════════════════════════════════════════════════╗"
echo "║              VERIFICACIÓN FINAL DE ESTADO                  ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""
printf " %-22s %-15s %-14s\n" "SERVICIO" "IP" "ESTADO"
echo "$LINEA"

if ping -c 1 -W 1 "$IP" &>/dev/null; then
  estado="🟢 ONLINE"
else
  estado="🔴 OFFLINE"
fi

printf " %-22s %-15s %-14s\n" "$SERVIDOR" "$IP" "$estado"

echo ""
echo "════════════════════════════════════════════════════════════"
printf " ⏱  Finalizado en      : %s\n" "$(date '+%Y-%m-%d %H:%M:%S')"
echo "════════════════════════════════════════════════════════════"
