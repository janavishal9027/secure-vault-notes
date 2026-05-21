#!/usr/bin/env bash
#
# Deploy the notes service to one of the secure-vault-* k3s clusters.
# Mirrors the Authentication / UI deploy structure: render locally, scp,
# run remote script.
#
# Required environment variables (set per environment in Bitbucket
# Deployment variables):
#   VPS_USER, VPS_HOST                SSH details for the LXD host
#   REMOTE_DIR                        Shared staging dir for this cluster
#                                     (e.g. /root/secure-vault-dev-a/manifests)
#   LXD_CONTAINER                     LXD container name
#   KUBE_NAMESPACE                    k8s namespace inside the container
#   APP_NAME                          e.g. notes
#   IMAGE_REPO                        Docker image repo (e.g. kittuvittu/secure-vault-notes)
#   IMAGE_TAG                         Image tag — exported by the build step (commit SHA)
#   INGRESS_HOST                      Public hostname routed by host nginx
#   LXD_BRIDGE_IP                     IP of the LXD container on lxdbr0
#   DB_URL                            JDBC URL, e.g.
#                                     jdbc:postgresql://10.86.216.10:5432/digital-notes?currentSchema=secure-vault
#   DB_PASSWORD                       Postgres password
#   JWT_SECRET                        Same value as Authentication / roles / ai-core-service
#   KAFKA_BOOTSTRAP_SERVERS           e.g. 10.86.216.10:9092
#   AUTHENTICATION_SERVICE_URL        Internal cluster URL for the Feign
#                                     auth client, e.g. http://authentication-service/authentication
#   AI_CORE_BASE_URL                  Internal URL for ai-core-service, e.g.
#                                     http://ai-core-service-service:8001
#   AI_WORKER_BASE_URL                Internal URL for ai-worker, e.g.
#                                     http://ai-worker-service:8000
# Optional:
#   DB_USERNAME                       Default "postgres"
#   REPLICAS                          Default 1
#   JWT_EXPIRATION                    Default 600000 (10 min, ms)
#   OPENAI_API_KEY                    Default empty (transcription disabled
#                                     until set)
#   SWAGGER_ENABLED                   Default "false"

set -euo pipefail

: "${VPS_USER:?}"
: "${VPS_HOST:?}"
: "${REMOTE_DIR:?}"
: "${LXD_CONTAINER:?}"
: "${KUBE_NAMESPACE:?}"
: "${APP_NAME:?}"
: "${IMAGE_REPO:?}"
: "${IMAGE_TAG:?}"
: "${INGRESS_HOST:?}"
: "${LXD_BRIDGE_IP:?}"
: "${DB_URL:?}"
: "${DB_PASSWORD:?}"
: "${JWT_SECRET:?}"
: "${KAFKA_BOOTSTRAP_SERVERS:?}"
: "${AUTHENTICATION_SERVICE_URL:?}"
: "${AI_CORE_BASE_URL:?}"
: "${AI_WORKER_BASE_URL:?}"

DB_USERNAME="${DB_USERNAME:-postgres}"
REPLICAS="${REPLICAS:-1}"
JWT_EXPIRATION="${JWT_EXPIRATION:-600000}"
OPENAI_API_KEY="${OPENAI_API_KEY:-}"
SWAGGER_ENABLED="${SWAGGER_ENABLED:-false}"

REMOTE_DIR="${REMOTE_DIR}/${APP_NAME}"
REMOTE_TARGET="${VPS_USER}@${VPS_HOST}"

SSH_OPTS=(
  -o StrictHostKeyChecking=no
  -o BatchMode=yes
  -o ConnectTimeout=15
  -o ServerAliveInterval=30
  -o ServerAliveCountMax=10
)

echo "==> Rendering manifests locally"
mkdir -p rendered
render_file() {
  local in="$1" out="$2"
  sed \
    -e "s|\${APP_NAME}|${APP_NAME}|g" \
    -e "s|\${KUBE_NAMESPACE}|${KUBE_NAMESPACE}|g" \
    -e "s|\${IMAGE_REPO}|${IMAGE_REPO}|g" \
    -e "s|\${IMAGE_TAG}|${IMAGE_TAG}|g" \
    -e "s|\${INGRESS_HOST}|${INGRESS_HOST}|g" \
    -e "s|\${REPLICAS}|${REPLICAS}|g" \
    -e "s|\${DB_URL}|${DB_URL}|g" \
    -e "s|\${DB_USERNAME}|${DB_USERNAME}|g" \
    -e "s|\${DB_PASSWORD}|${DB_PASSWORD}|g" \
    -e "s|\${JWT_SECRET}|${JWT_SECRET}|g" \
    -e "s|\${JWT_EXPIRATION}|${JWT_EXPIRATION}|g" \
    -e "s|\${KAFKA_BOOTSTRAP_SERVERS}|${KAFKA_BOOTSTRAP_SERVERS}|g" \
    -e "s|\${AUTHENTICATION_SERVICE_URL}|${AUTHENTICATION_SERVICE_URL}|g" \
    -e "s|\${AI_CORE_BASE_URL}|${AI_CORE_BASE_URL}|g" \
    -e "s|\${AI_WORKER_BASE_URL}|${AI_WORKER_BASE_URL}|g" \
    -e "s|\${OPENAI_API_KEY}|${OPENAI_API_KEY}|g" \
    -e "s|\${SWAGGER_ENABLED}|${SWAGGER_ENABLED}|g" \
    "$in" > "$out"
}
render_file deployment.yml rendered/deployment.yml
render_file service.yml    rendered/service.yml
render_file ingress.yml    rendered/ingress.yml

echo "==> Rendering nginx location snippet"
sed -e "s|\${LXD_BRIDGE_IP}|${LXD_BRIDGE_IP}|g" \
    ci/nginx/notes.location.conf > rendered/notes.location.conf

echo "=== Rendered manifests (secrets redacted) ==="
for f in rendered/*.yml; do
  echo "--- $f ---"
  sed \
    -e "s|${JWT_SECRET}|***JWT_SECRET***|g" \
    -e "s|${DB_PASSWORD}|***DB_PASSWORD***|g" \
    -e "s|${OPENAI_API_KEY:-__no_openai_key__}|***OPENAI_API_KEY***|g" \
    "$f"
done

echo "==> Preparing remote staging dir ${REMOTE_DIR} on ${VPS_HOST}"
ssh "${SSH_OPTS[@]}" "$REMOTE_TARGET" "mkdir -p '${REMOTE_DIR}'"

echo "==> Shipping manifests + deploy-remote.sh to ${VPS_HOST}"
scp "${SSH_OPTS[@]}" \
    rendered/deployment.yml \
    rendered/service.yml \
    rendered/ingress.yml \
    rendered/notes.location.conf \
    ci/deploy-remote.sh \
    "${REMOTE_TARGET}:${REMOTE_DIR}/"

echo "==> Executing deploy-remote.sh on ${VPS_HOST}"
ssh "${SSH_OPTS[@]}" "$REMOTE_TARGET" \
    "env \
      APP_NAME='${APP_NAME}' \
      KUBE_NAMESPACE='${KUBE_NAMESPACE}' \
      IMAGE_REPO='${IMAGE_REPO}' \
      IMAGE_TAG='${IMAGE_TAG}' \
      INGRESS_HOST='${INGRESS_HOST}' \
      REPLICAS='${REPLICAS}' \
      REMOTE_DIR='${REMOTE_DIR}' \
      LXD_CONTAINER='${LXD_CONTAINER}' \
      LXD_BRIDGE_IP='${LXD_BRIDGE_IP}' \
      bash '${REMOTE_DIR}/deploy-remote.sh'"
