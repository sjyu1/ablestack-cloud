#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR=$(cd "$(dirname "$0")/../.." && pwd -P)
PACK=${PACK:-oss}
SIMULATOR=${SIMULATOR:-default}
DISTRO=${DISTRO:-rocky9}
RELEASE=${RELEASE:-}
TEMPLATES=${TEMPLATES:-}
NODE_VERSION=${NODE_VERSION:-14.21.3}

if ! command -v dnf >/dev/null 2>&1; then
    echo "This helper must run inside a Rocky/RHEL-compatible environment with dnf."
    exit 1
fi

if [ "$DISTRO" != "rocky9" ] && [ "$DISTRO" != "centos8" ]; then
    echo "Unsupported DISTRO=$DISTRO. Expected rocky9 or centos8."
    exit 1
fi

echo "== Rocky 9.7 RPM build helper =="
echo "ROOT_DIR=$ROOT_DIR"
echo "DISTRO=$DISTRO"
echo "PACK=$PACK"
echo "SIMULATOR=$SIMULATOR"
echo "RELEASE=${RELEASE:-<default>}"
echo "TEMPLATES=${TEMPLATES:-<none>}"
echo "NODE_VERSION=$NODE_VERSION"

DNF=(dnf --releasever=9.7 -y)

"${DNF[@]}" install dnf-plugins-core
dnf --releasever=9.7 config-manager --set-enabled crb || true
"${DNF[@]}" install epel-release || true
"${DNF[@]}" install \
    bash \
    bzip2 \
    ca-certificates \
    curl \
    findutils \
    gcc \
    genisoimage \
    git \
    glibc-devel \
    gzip \
    java-11-openjdk-devel \
    java-17-openjdk-devel \
    jq \
    maven \
    nodejs \
    openssl-devel \
    python3-devel \
    python3-pip \
    python3-setuptools \
    rpm-build \
    shadow-utils \
    tar \
    unzip \
    wget \
    which \
    xz

if [ ! -x "/opt/node-v${NODE_VERSION}-linux-x64/bin/node" ]; then
    curl -fsSL "https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-x64.tar.xz" \
        | tar -xJf - -C /opt
fi

JAVA17_HOME=$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")
if [ -d /usr/lib/jvm/java-17-openjdk ]; then
    JAVA17_HOME=/usr/lib/jvm/java-17-openjdk
fi

export JAVA_HOME="$JAVA17_HOME"
export PATH="/opt/node-v${NODE_VERSION}-linux-x64/bin:$JAVA_HOME/bin:$PATH"

git config --global --add safe.directory "$ROOT_DIR"

mkdir -p "$ROOT_DIR/dist/rocky97-build"
{
    echo "date=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "os_release=$(tr '\n' ' ' </etc/os-release)"
    echo "java_version=$("$JAVA_HOME/bin/java" -version 2>&1 | tr '\n' ' ' )"
    echo "maven_version=$(mvn -v | head -n 1)"
    echo "node_version=$(node -v)"
    echo "npm_version=$(npm -v)"
    echo "pack=$PACK"
    echo "simulator=$SIMULATOR"
    echo "distro=$DISTRO"
    echo "release=${RELEASE:-<default>}"
    echo "templates=${TEMPLATES:-<none>}"
} >"$ROOT_DIR/dist/rocky97-build/environment.txt"

build_args=(
    --distribution "$DISTRO"
    --pack "$PACK"
)

if [ "$SIMULATOR" != "default" ]; then
    build_args+=(--simulator "$SIMULATOR")
fi

if [ -n "$RELEASE" ]; then
    build_args+=(--release "$RELEASE")
fi

if [ -n "$TEMPLATES" ]; then
    build_args+=(--templates "$TEMPLATES")
fi

cd "$ROOT_DIR/packaging"
./package.sh "${build_args[@]}"

cd "$ROOT_DIR"
find dist/rpmbuild -type f \( -name '*.rpm' -o -name '*.src.rpm' \) | sort \
    > dist/rocky97-build/artifacts.txt
