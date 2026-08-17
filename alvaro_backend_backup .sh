#!/usr/bin/env bash

SSH_HOST="servisofts@192.168.2.5"
REMOTE_BUILD_DIR="/home/servisofts/servicios/serp/entornos/serp/servicios/serp"
BACKUP_DATE=$(date +%Y-%m-%d_%H%M%S)

echo "Respaldando server.jar remoto..."
ssh "$SSH_HOST" "cd $REMOTE_BUILD_DIR && cp server.jar server.jar_$BACKUP_DATE"

if [ $? -eq 0 ]; then
    echo "Backup creado: $REMOTE_BUILD_DIR/server.jar_$BACKUP_DATE"
else
    echo "Error al crear el backup"
    exit 1
fi
