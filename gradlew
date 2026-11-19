#!/usr/bin/env sh
# Lightweight Gradle bootstrapper for Linux/macOS, matching BreatheEasy's wrapper approach.
set -eu
GRADLE_VERSION="8.11.1"
CACHE_ROOT="${GRADLE_USER_HOME:-${HOME}/.gradle}/tasbeeh"
GRADLE_HOME_DIR="${CACHE_ROOT}/gradle-${GRADLE_VERSION}"
GRADLE_BIN="${GRADLE_HOME_DIR}/bin/gradle"
if [ ! -x "${GRADLE_BIN}" ]; then
    mkdir -p "${CACHE_ROOT}"
    TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/tasbeeh-gradle.XXXXXX")"
    trap 'rm -rf "${TMP_DIR}"' EXIT HUP INT TERM
    ARCHIVE="${TMP_DIR}/gradle-${GRADLE_VERSION}-bin.zip"
    URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
    if command -v curl >/dev/null 2>&1; then
        curl --fail --location --retry 3 --connect-timeout 20 "${URL}" -o "${ARCHIVE}"
    elif command -v wget >/dev/null 2>&1; then
        wget --tries=3 --timeout=20 "${URL}" -O "${ARCHIVE}"
    else
        echo "Tasbeeh needs curl or wget to download Gradle ${GRADLE_VERSION}." >&2
        exit 1
    fi
    if ! command -v unzip >/dev/null 2>&1; then
        echo "Tasbeeh needs unzip to unpack Gradle ${GRADLE_VERSION}." >&2
        exit 1
    fi
    unzip -q "${ARCHIVE}" -d "${TMP_DIR}"
    if [ ! -x "${GRADLE_BIN}" ]; then
        rm -rf "${GRADLE_HOME_DIR}"
        mv "${TMP_DIR}/gradle-${GRADLE_VERSION}" "${GRADLE_HOME_DIR}"
    fi
fi
exec "${GRADLE_BIN}" "$@"
