#!/bin/bash

set -euo pipefail

curl -sSL https://get.docker.com/ | sh
docker --version
mkdir -p /etc/docker/
if [ -f /etc/docker/daemon.json ]; then
    cp /etc/docker/daemon.json /etc/docker/daemon.json.bak
fi
cat > /etc/docker/daemon.json <<EOF
{
    "registry-mirrors":[
        "https://docker.m.daocloud.io",
        "https://docker.1ms.run",
        "https://docker.xuanyuan.me"
    ],
    "insecure-registries": ["127.0.0.1/8"],
    "max-concurrent-downloads":10,
    "log-driver":"json-file",
    "log-level":"warn",
    "log-opts":{
        "max-size":"10m",
        "max-file":"3"
    },
    "data-root":"/var/lib/docker"
}
EOF
service docker restart

curl -L https://github.com/docker/compose/releases/download/v2.2.2/docker-compose-`uname -s`-`uname -m` > /usr/local/bin/docker-compose && chmod +x /usr/local/bin/docker-compose
docker-compose --version

