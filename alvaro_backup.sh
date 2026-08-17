#!/usr/bin/env bash

SSH_HOST="servisofts@192.168.2.5"
REMOTE_BUILD_DIR="~/servicios/serp/entornos/serp"
BACKUP_DATE=$(date +%Y-%m-%d_%H%M%S)

echo "Respaldando build remoto..."
ssh "$SSH_HOST" "cd $REMOTE_BUILD_DIR && cp -r build build_$BACKUP_DATE"

if [ $? -eq 0 ]; then
    echo "Backup creado: $REMOTE_BUILD_DIR/build_$BACKUP_DATE"
else
    echo "Error al crear el backup"
    exit 1
fi
