#!/bin/sh
set -eu

MAVEN_VERSION="3.9.16"
DIST_URL="https://dlcdn.apache.org/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.tar.gz"
EXPECTED_SHA512="831a8591fe20c8243b1dbe7d71e3244f31d1665b0804b2e825e38cbbe5ce0cafb8338851f90780735568773e0a6cd07bbec107cda0b896b008b861075358b6f6"
WRAPPER_HOME="${HOME}/.m2/wrapper/dists/kho-nha-minh"
INSTALL_ROOT="${WRAPPER_HOME}/apache-maven-${MAVEN_VERSION}"
MVN_BIN="${INSTALL_ROOT}/bin/mvn"

if [ ! -x "$MVN_BIN" ]; then
  mkdir -p "$WRAPPER_HOME"
  ARCHIVE="$WRAPPER_HOME/apache-maven-${MAVEN_VERSION}-bin.tar.gz"
  TEMP_DIR="$WRAPPER_HOME/extract-${MAVEN_VERSION}"
  echo "Maven Wrapper: downloading Maven ${MAVEN_VERSION} for first use..."
  if command -v curl >/dev/null 2>&1; then
    curl -fL "$DIST_URL" -o "$ARCHIVE"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$ARCHIVE" "$DIST_URL"
  else
    echo "Maven Wrapper requires curl or wget on first use." >&2
    exit 1
  fi
  if command -v sha512sum >/dev/null 2>&1; then
    ACTUAL_SHA512=$(sha512sum "$ARCHIVE" | awk '{print $1}')
  elif command -v shasum >/dev/null 2>&1; then
    ACTUAL_SHA512=$(shasum -a 512 "$ARCHIVE" | awk '{print $1}')
  else
    echo "Maven Wrapper requires sha512sum or shasum to verify Maven." >&2
    exit 1
  fi
  if [ "$ACTUAL_SHA512" != "$EXPECTED_SHA512" ]; then
    rm -f "$ARCHIVE"
    echo "Maven Wrapper checksum mismatch. Download removed." >&2
    exit 1
  fi
  rm -rf "$TEMP_DIR"
  mkdir -p "$TEMP_DIR"
  tar -xzf "$ARCHIVE" -C "$TEMP_DIR"
  rm -rf "$INSTALL_ROOT"
  mv "$TEMP_DIR/apache-maven-${MAVEN_VERSION}" "$INSTALL_ROOT"
  rm -rf "$TEMP_DIR" "$ARCHIVE"
fi

exec "$MVN_BIN" "$@"
