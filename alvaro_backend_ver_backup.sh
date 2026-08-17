#!/usr/bin/env bash

SSH_HOST="servisofts@192.168.2.5"
REMOTE_BUILD_DIR="/home/servisofts/servicios/serp/entornos/serp/servicios/serp"

echo "Backups disponibles de server.jar en: $REMOTE_BUILD_DIR"
echo "=========================================="
ssh "$SSH_HOST" "ls -lh $REMOTE_BUILD_DIR/server.jar_* 2>/dev/null"
echo "=========================================="
ssh "$SSH_HOST" "ls -1 $REMOTE_BUILD_DIR/server.jar_* 2>/dev/null | wc -l" | xargs echo "Total de backups:"
