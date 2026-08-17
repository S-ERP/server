#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

# --- Colores para los mensajes de consola ---
BLUE='\033[1;34m'
CYAN='\033[1;36m'
GREEN='\033[1;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # sin color

PKG_JSON="package.json"

# --- 1) Subir version (patch +1) ---
current_version=$(node -p "require('./package.json').version")
IFS='.' read -r major minor patch <<< "$current_version"
new_patch=$((patch + 1))
new_version="$major.$minor.$new_patch"

echo -e "${BLUE}[1/5] Subiendo version: $current_version -> $new_version${NC}"

sed -i "s/\"version\": \"$current_version\"/\"version\": \"$new_version\"/" "$PKG_JSON"

echo -e "${GREEN}Version actualizada a $new_version${NC}"

# --- 2) Compilar el build ---
echo -e "${BLUE}[2/5] Compilando frontend...${NC}"
npm run build

echo -e "${GREEN}Build completado.${NC}"

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

SUDO_PASS="${SUDO_PASS:-servisofts}"
BACKUP_DATE=$(date +%Y-%m-%d_%H%M%S)

# --- 3) Crear una copia del build actual en el servidor (respaldo con fecha) ---
echo -e "${BLUE}[3/5] Respaldando build remoto (fecha: $BACKUP_DATE)...${NC}"
ssh "$SSH_HOST" "cp -r $REMOTE_FRONTEND_DIR/build $REMOTE_FRONTEND_DIR/build_$BACKUP_DATE"

echo -e "${GREEN}Backup creado: build_$BACKUP_DATE${NC}"

# --- 4) Subir el build nuevo al servidor ---
# rsync --delete deja el remoto identico al build local (borra archivos viejos que ya no existen)
echo -e "${CYAN}[4/5] Subiendo build nuevo...${NC}"
rsync -avz --delete build/ "$SSH_HOST:$REMOTE_FRONTEND_DIR/build/"

echo -e "${GREEN}build subido.${NC}"

# --- 5) Reiniciar el servicio ---
echo -e "${CYAN}[5/5] Deteniendo servicio...${NC}"

printf '%s\n1\n%s\n' "$SUDO_PASS" "$SUDO_PASS" | ssh -tt "$SSH_HOST" "cd ~/servicios/serp && ./servisofts.sh down"

echo -e "${CYAN}Iniciando servicio con el nuevo build...${NC}"

printf '%s\n1\n%s\n' "$SUDO_PASS" "$SUDO_PASS" | ssh -tt "$SSH_HOST" "cd ~/servicios/serp && ./servisofts.sh up -d"

echo -e "${GREEN}✓ Deploy completado. Version $new_version subida y servicio reiniciado.${NC}"
