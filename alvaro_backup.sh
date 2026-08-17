#!/usr/bin/env bash

SSH_HOST="servisofts@192.168.2.5"
REMOTE_BUILD_DIR="~/servicios/serp/entornos/serp"

# Función para hacer backup
hacer_backup() {
    BACKUP_DATE=$(date +%Y-%m-%d_%H%M%S)
    echo "Respaldando build remoto..."
    ssh "$SSH_HOST" "cd $REMOTE_BUILD_DIR && cp -r build build_$BACKUP_DATE"

    if [ $? -eq 0 ]; then
        echo "Backup creado: $REMOTE_BUILD_DIR/build_$BACKUP_DATE"
        return 0
    else
        echo "Error al crear el backup"
        return 1
    fi
}

# Función para ver backups
ver_backups() {
    echo "Backups disponibles en: $REMOTE_BUILD_DIR"
    echo "=========================================="
    ssh "$SSH_HOST" "cd $REMOTE_BUILD_DIR && ls -lh | grep build | awk '{print \$9, \"(\" \$5 \")\"}'"
    echo "=========================================="
    ssh "$SSH_HOST" "cd $REMOTE_BUILD_DIR && ls -1d build* | wc -l" | xargs echo "Total de builds:"
}

# Procesar argumentos
case "${1:-}" in
    "backup")
        hacer_backup
        ;;
    "ver")
        ver_backups
        ;;
    *)
        echo "Uso: $0 {backup|ver}"
        echo "  backup - Hacer backup del build actual"
        echo "  ver    - Ver lista de backups"
        exit 1
        ;;
esac
