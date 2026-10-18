#!/usr/bin/env sh
# Writes a compressed, consistent PostgreSQL dump of the running stack to ./backups/.
#   scripts/backup.sh                -> backups/buildmybundle-YYYYmmdd-HHMMSS.sql.gz
# Restore with scripts/restore.sh <file>. Schedule it (cron, Task Scheduler) and copy backups off the host.
set -eu
cd "$(dirname "$0")/.."
mkdir -p backups
file="backups/buildmybundle-$(date +%Y%m%d-%H%M%S).sql.gz"
# pg_dump runs inside the container, so no PostgreSQL client is needed on the host
docker compose exec -T postgres sh -c 'pg_dump --clean --if-exists --no-owner -U "$POSTGRES_USER" "$POSTGRES_DB"' | gzip > "$file"
echo "Backup written to $file ($(wc -c < "$file") bytes)"
