#!/bin/sh
set -eu

MYSQL_HOST="${MYSQL_HOST:-mysql}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_DATABASE="${MYSQL_DATABASE:-english_nova}"
MYSQL_USERNAME="${MYSQL_USERNAME:-english_nova}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-english_nova}"

NACOS_BASE_URL="${NACOS_BASE_URL:-http://nacos:8848}"
NACOS_GROUP="${NACOS_GROUP:-DEFAULT_GROUP}"
NACOS_NAMESPACE="${NACOS_NAMESPACE:-public}"
NACOS_USERNAME="${NACOS_USERNAME:-}"
NACOS_PASSWORD="${NACOS_PASSWORD:-}"
NACOS_CONFIG_DIR="${NACOS_CONFIG_DIR:-/seed/nacos/configs}"

wait_for_mysql() {
  echo "Waiting for MySQL at ${MYSQL_HOST}:${MYSQL_PORT}..."
  for attempt in $(seq 1 60); do
    if mysqladmin ping \
      -h"${MYSQL_HOST}" \
      -P"${MYSQL_PORT}" \
      -u"${MYSQL_USERNAME}" \
      -p"${MYSQL_PASSWORD}" \
      --silent >/dev/null 2>&1; then
      echo "MySQL is ready."
      return 0
    fi

    echo "MySQL is not ready yet (${attempt}/60)."
    sleep 2
  done

  echo "MySQL did not become ready in time." >&2
  return 1
}

seed_mysql() {
  echo "Seeding MySQL schema and data..."
  mysql -h"${MYSQL_HOST}" -P"${MYSQL_PORT}" -u"${MYSQL_USERNAME}" -p"${MYSQL_PASSWORD}" "${MYSQL_DATABASE}" < /seed/mysql/init/001-schema.sql
  mysql -h"${MYSQL_HOST}" -P"${MYSQL_PORT}" -u"${MYSQL_USERNAME}" -p"${MYSQL_PASSWORD}" "${MYSQL_DATABASE}" < /seed/mysql/init/002-seed.sql
  echo "MySQL seed finished."
}

get_nacos_token() {
  if [ -z "${NACOS_USERNAME}" ] || [ -z "${NACOS_PASSWORD}" ]; then
    return 0
  fi

  login_response="$(curl -fsS \
    -X POST "${NACOS_BASE_URL}/nacos/v3/auth/user/login" \
    -d "username=${NACOS_USERNAME}" \
    -d "password=${NACOS_PASSWORD}" 2>/dev/null || true)"

  printf '%s' "${login_response}" | sed -n 's/.*"accessToken"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p'
}

publish_nacos_config() {
  config_file="$1"
  data_id="$(basename "${config_file}")"
  namespace_args=""

  if [ -n "${NACOS_NAMESPACE}" ] && [ "${NACOS_NAMESPACE}" != "public" ]; then
    namespace_args="--data-urlencode namespaceId=${NACOS_NAMESPACE}"
  fi

  token="${NACOS_ACCESS_TOKEN:-}"
  if [ -z "${token}" ]; then
    token="$(get_nacos_token)"
  fi

  token_args=""
  if [ -n "${token}" ]; then
    token_args="--data-urlencode accessToken=${token}"
  fi

  # shellcheck disable=SC2086
  curl -fsS \
    -X POST "${NACOS_BASE_URL}/nacos/v3/admin/cs/config" \
    --data-urlencode "dataId=${data_id}" \
    --data-urlencode "groupName=${NACOS_GROUP}" \
    --data-urlencode "type=yaml" \
    --data-urlencode "content@${config_file}" \
    ${namespace_args} \
    ${token_args}
}

wait_for_nacos_and_seed() {
  if [ ! -d "${NACOS_CONFIG_DIR}" ]; then
    echo "Nacos config directory does not exist: ${NACOS_CONFIG_DIR}" >&2
    return 1
  fi

  for config_file in "${NACOS_CONFIG_DIR}"/*.yaml; do
    if [ ! -f "${config_file}" ]; then
      echo "No Nacos config files found in ${NACOS_CONFIG_DIR}." >&2
      return 1
    fi
    break
  done

  echo "Seeding Nacos configs at ${NACOS_BASE_URL}..."
  for attempt in $(seq 1 60); do
    all_published=1

    for config_file in "${NACOS_CONFIG_DIR}"/*.yaml; do
      data_id="$(basename "${config_file}")"
      if publish_nacos_config "${config_file}" >/dev/null 2>&1; then
        echo "Published Nacos config: ${data_id}"
      else
        echo "Nacos is not ready for ${data_id} yet (${attempt}/60)."
        all_published=0
        break
      fi
    done

    if [ "${all_published}" -eq 1 ]; then
      echo "Nacos seed finished."
      return 0
    fi

    sleep 2
  done

  echo "Nacos did not accept config seeds in time." >&2
  return 1
}

wait_for_mysql
seed_mysql
wait_for_nacos_and_seed
