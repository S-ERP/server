#!/usr/bin/env bash

SSH_HOST="servisofts@192.168.2.5"
REMOTE_BUILD_DIR="/home/servisofts/servicios/serp/entornos/serp/servicios/serp"

echo "Backups disponibles de server.jar en: $REMOTE_BUILD_DIR"
echo "=========================================="
ssh "$SSH_HOST" "ls -lh $REMOTE_BUILD_DIR/server.jar_* 2>/dev/null"
echo "=========================================="
ssh "$SSH_HOST" "ls -1 $REMOTE_BUILD_DIR/server.jar_* 2>/dev/null | wc -l" | xargs echo "Total de backups:"

echo ""
read -p "¿Deseas restaurar un backup? (s/n): " RESPUESTA

if [[ "$RESPUESTA" == "s" || "$RESPUESTA" == "S" ]]; then
    echo ""
    read -p "Ingresa el timestamp del backup a restaurar (ej: 2026-08-17_143025): " BACKUP_TIMESTAMP

    BACKUP_FILE="server.jar_$BACKUP_TIMESTAMP"

    echo "Verificando backup..."
    ssh "$SSH_HOST" "[ -f $REMOTE_BUILD_DIR/$BACKUP_FILE ] && echo 'Backup encontrado' || (echo 'Backup no encontrado' && exit 1)"

    if [ $? -eq 0 ]; then
        echo "Restaurando $BACKUP_FILE..."
        ssh "$SSH_HOST" "cd $REMOTE_BUILD_DIR && cp $BACKUP_FILE server.jar"

        if [ $? -eq 0 ]; then
            echo "✓ Restauración completada: server.jar"
        else
            echo "✗ Error al restaurar el backup"
            exit 1
        fi
    else
        echo "✗ Error: Backup no encontrado"
        exit 1
    fi
else
    echo "Operación cancelada"
fi
