#!/usr/bin/env sh
# Restores a dump made by scripts/backup.sh into the running stack's database.
#   scripts/restore.sh backups/buildmybundle-20261018-120000.sql.gz [backups/buildmybundle-20261018-120000-media.tar.gz]
# The backend is stopped during the restore and started again afterwards.
set -eu
cd "$(dirname "$0")/.."
file="${1:?Usage: scripts/restore.sh <backup.sql.gz> [media.tar.gz]}"
media="${2:-}"
[ -f "$file" ] || { echo "No such file: $file" >&2; exit 1; }
printf 'This replaces ALL data in the database with %s. Type "restore" to continue: ' "$file"
read -r answer
[ "$answer" = "restore" ] || { echo "Cancelled."; exit 1; }
docker compose stop backend
gunzip -c "$file" | docker compose exec -T postgres sh -c 'psql -v ON_ERROR_STOP=1 -q -U "$POSTGRES_USER" "$POSTGRES_DB"'
docker compose start backend
if [ -n "$media" ]; then
  [ -f "$media" ] || { echo "No such file: $media" >&2; exit 1; }
  docker compose exec -T backend sh -c 'find /app/media -mindepth 1 -delete && tar -C /app/media -xzf -' < "$media"
  echo "Restored uploaded images from $media"
fi
echo "Restored $file"
