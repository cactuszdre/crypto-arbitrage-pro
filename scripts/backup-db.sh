#!/bin/bash
# ==============================================
# Sauvegarde automatique PostgreSQL
# Crontab : 0 3 * * * /root/crypto-arbitrage/scripts/backup-db.sh
# ==============================================

BACKUP_DIR="/root/crypto-arbitrage/backups"
DATE=$(date +%Y-%m-%d_%H-%M)
CONTAINER="crypto-postgres"

# Charger les variables d'env
source /root/crypto-arbitrage/.env.prod

mkdir -p $BACKUP_DIR

# Dump de la base
docker exec $CONTAINER pg_dump -U $DB_USERNAME $DB_NAME | gzip > "$BACKUP_DIR/backup_$DATE.sql.gz"

# Garder seulement les 7 derniers backups
ls -tp $BACKUP_DIR/backup_*.sql.gz 2>/dev/null | tail -n +8 | xargs -I {} rm -- {}

echo "[$(date)] Backup OK: backup_$DATE.sql.gz"
