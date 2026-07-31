#!/usr/bin/env bash
# Production deploy on the droplet (also invoked by GitHub Actions via SSH).
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

LOCK_FILE="${DEPLOY_LOCK_FILE:-/var/lock/crypto-arbitrage-deploy.lock}"
mkdir -p "$(dirname "$LOCK_FILE")"
exec 9>"$LOCK_FILE"
if ! flock -w "${DEPLOY_LOCK_WAIT_SEC:-2700}" 9; then
  echo "ERROR: could not acquire deploy lock ($LOCK_FILE) within ${DEPLOY_LOCK_WAIT_SEC:-2700}s"
  exit 1
fi

echo "=== deploy lock acquired ==="
echo "=== HEAD ==="
git rev-parse HEAD
git log -1 --oneline

# Keep production HTTPS config in sync with the tracked template.
if [ -f nginx/conf.d/default.conf.template ]; then
  cp nginx/conf.d/default.conf.template nginx/conf.d/default.conf
fi
# SSL bootstrap file must not load alongside default.conf (port 80 clash).
rm -f nginx/conf.d/init.conf

compose() {
  docker compose -f docker-compose.prod.yml --env-file .env.prod "$@"
}

# Drop leftover rename containers from interrupted / overlapping compose runs
# (names like "a1b2c3d4e5f6_crypto-backend").
cleanup_compose_leftovers() {
  local names
  names="$(docker ps -a --format '{{.Names}}' | grep -E '^[a-f0-9]+_crypto-' || true)"
  if [ -n "$names" ]; then
    echo "=== removing compose rename leftovers ==="
    # shellcheck disable=SC2086
    echo "$names" | xargs -r docker rm -f
  fi
}

cleanup_compose_leftovers

# Serial builds on the 1-vCPU droplet avoid OOM thrash and shorten wall time vs parallel Maven+Nuxt.
export COMPOSE_PARALLEL_LIMIT="${COMPOSE_PARALLEL_LIMIT:-1}"

echo "=== building images (serial) ==="
compose build

echo "=== recreating containers ==="
if ! compose up -d --remove-orphans; then
  echo "=== compose up failed — cleaning leftovers and retrying ==="
  cleanup_compose_leftovers
  compose up -d --remove-orphans
fi

# Reload nginx so conf + upstream DNS refresh after recreates.
if ! docker exec crypto-nginx nginx -t || ! docker exec crypto-nginx nginx -s reload; then
  echo "=== nginx reload failed — force recreating nginx ==="
  compose up -d --force-recreate --no-deps nginx
fi

docker image prune -f
echo "=== containers ==="
compose ps
echo "✅ Déploiement terminé !"
