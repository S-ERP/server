#!/usr/bin/env bash

SSH_HOST="servisofts@192.168.2.5"
REMOTE_BUILD_DIR="~/servicios/serp/entornos/serp"

echo "Builds disponibles en: $REMOTE_BUILD_DIR"
echo "=========================================="
ssh "$SSH_HOST" "cd $REMOTE_BUILD_DIR && ls -lh | grep build | awk '{print \$9, \"(\" \$5 \")\"}'"
echo "=========================================="
ssh "$SSH_HOST" "cd $REMOTE_BUILD_DIR && ls -1d build* | wc -l" | xargs echo "Total de builds:"
