#!/bin/bash

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

grupos_nombres=("CORE SERVICES" "PLATAFORMA SERVICES" "CALISTENIA BOLIVIA" "SISTEMA EMPRESARIAL")
grupos_servidores=(
  "nginx wireguard"
  "empresa servicios roles usuario"
  "facturacion spdf drive notification chat"
  "calistenia geolocation proyecto sqr zkteco"
  "serp caja compra-venta crm inventario contabilidad"
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

echo "╔════════════════════════════════════════════════════════════╗"
echo "║         REINICIO DE TODOS LOS SERVIDORES                  ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""

# FASE 1: APAGAR TODOS LOS SERVIDORES
echo -e "${YELLOW}FASE 1: APAGANDO SERVIDORES${NC}"
echo "$LINEA"

for i in "${!grupos_nombres[@]}"; do
  echo ""
  echo " ${grupos_nombres[$i]}"

  for nombre in ${grupos_servidores[$i]}; do
    ip="${servidores[$nombre]}"
    detener_servidor "$nombre" "$ip"
  done
done

echo ""
echo "$LINEA"
echo -e "${CYAN}Esperando a que los servidores se apaguen...${NC}"
sleep 10

# FASE 2: ENCENDER TODOS LOS SERVIDORES
echo ""
echo -e "${CYAN}FASE 2: ENCENDIENDO SERVIDORES${NC}"
echo "$LINEA"

for i in "${!grupos_nombres[@]}"; do
  echo ""
  echo " ${grupos_nombres[$i]}"

  for nombre in ${grupos_servidores[$i]}; do
    ip="${servidores[$nombre]}"
    iniciar_servidor "$nombre" "$ip"

    echo -ne "${CYAN}  Esperando que $nombre esté online...${NC}"
    if verificar_online "$ip"; then
      echo -e " ${GREEN}✓${NC}"
    else
      echo -e " ${RED}✗ No respondió después de 30s${NC}"
    fi
  done
done

# FASE 3: RESUMEN FINAL
echo ""
echo "$LINEA"
echo -e "${GREEN}REINICIO COMPLETADO${NC}"
echo "╔════════════════════════════════════════════════════════════╗"
echo "║              VERIFICACIÓN FINAL DE ESTADO                  ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""
printf " %-22s %-15s %-14s\n" "SERVICIO" "IP" "ESTADO"
echo "$LINEA"

online=0
offline=0

for i in "${!grupos_nombres[@]}"; do
  echo ""
  echo " ${grupos_nombres[$i]}"

  for nombre in ${grupos_servidores[$i]}; do
    ip="${servidores[$nombre]}"

    if ping -c 1 -W 1 "$ip" &>/dev/null; then
      estado="🟢 ONLINE"
      online=$((online + 1))
    else
      estado="🔴 OFFLINE"
      offline=$((offline + 1))
    fi

    printf " %-22s %-15s %-14s\n" "$nombre" "$ip" "$estado"
  done
done

echo ""
echo "════════════════════════════════════════════════════════════"
total=$((online + offline))
disponibilidad=$(awk -v o="$online" -v t="$total" 'BEGIN { printf "%.1f", (t > 0 ? o / t * 100 : 0) }')

printf " 🟢 SERVICIOS ACTIVOS  : %d\n" "$online"
printf " 🔴 SERVICIOS CAÍDOS   : %d\n" "$offline"
printf " 📊 DISPONIBILIDAD     : %s%%\n" "$disponibilidad"
printf " ⏱  Finalizado en      : %s\n" "$(date '+%Y-%m-%d %H:%M:%S')"
echo "════════════════════════════════════════════════════════════"
