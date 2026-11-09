#!/bin/bash
# 用法：sudo bash deploy/up.sh
# Alibaba Cloud Linux 4 容器优化版走这条。脚本会在缺少引擎时安装 moby 和 docker compose。
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"
env_file="$root/deploy/.env"
compose_file="$root/deploy/docker-compose.yml"

install_pkg() {
  if command -v dnf >/dev/null 2>&1; then
    dnf install -y "$@"
  else
    yum install -y "$@"
  fi
}

if [[ -r /etc/os-release ]] && grep -q 'Alibaba Cloud Linux' /etc/os-release; then
  if [[ "$(id -u)" -ne 0 ]]; then
    echo "请用 root 运行：sudo bash deploy/up.sh"
    exit 1
  fi
  if ! command -v docker >/dev/null 2>&1; then
    install_pkg moby
  fi
  systemctl enable --now docker
  if ! docker compose version >/dev/null 2>&1; then
    install_pkg docker-compose-plugin
  fi
  if ! command -v curl >/dev/null 2>&1; then
    install_pkg curl
  fi
fi

if ! command -v docker >/dev/null 2>&1 || ! docker info >/dev/null 2>&1; then
  echo "Docker 没有就绪。Alibaba Cloud Linux 4 请用 root 运行本脚本，它会安装 moby。"
  exit 1
fi

if docker compose version >/dev/null 2>&1; then
  compose() { docker compose -p cti -f "$compose_file" --env-file "$env_file" "$@"; }
elif command -v docker-compose >/dev/null 2>&1; then
  compose() { docker-compose -p cti -f "$compose_file" --env-file "$env_file" "$@"; }
else
  echo "没有 docker compose。Alibaba Cloud Linux 4 上执行：sudo dnf install -y docker-compose-plugin"
  exit 1
fi

if [[ ! -f "$env_file" ]]; then
  root_password="$(openssl rand -hex 12)Aa1"
  app_password="$(openssl rand -hex 12)Aa1"
  cat > "$env_file" <<EOF
MYSQL_ROOT_PASSWORD=${root_password}
MYSQL_PASSWORD=${app_password}
CTI_HTTP_BIND=127.0.0.1:8081
EOF
  chmod 600 "$env_file"
  echo "已生成 $env_file。数据库密码写在这个文件里。"
fi

echo "开始构建并启动 MySQL、Redis、Python 和 Java。第一次构建会下载镜像并编译，需要几分钟。"
compose up -d --build

echo "等待 Java 健康检查。"
ready=0
for _ in $(seq 1 60); do
  if curl -fsS "http://127.0.0.1:8081/api/v1/admin/system-health" >/dev/null 2>&1; then
    ready=1
    break
  fi
  sleep 5
done

if [[ "$ready" -ne 1 ]]; then
  echo "Java 还没有就绪。查看日志：docker compose -p cti -f deploy/docker-compose.yml logs api"
  exit 1
fi

if [[ "$(id -u)" -eq 0 ]]; then
  hour=0
  minute=20
  if timedatectl 2>/dev/null | grep -q 'Asia/Shanghai'; then
    hour=8
  fi
  cat > /etc/cron.d/cti <<EOF
${minute} ${hour} * * * root CTI_API_URL=http://127.0.0.1:8081 ${root}/deploy/daily.sh >> /var/log/cti-daily.log 2>&1
EOF
  chmod 644 /etc/cron.d/cti
  echo "每日任务已写入 /etc/cron.d/cti。"
else
  echo "当前不是 root，没有写定时任务。需要时用 root 再运行一次本脚本，或手工把 deploy/daily.sh 放进 cron。"
fi

echo "页面：http://127.0.0.1:8081/"
echo "健康检查里的 XXL-Job 可以是 DEGRADED。每日行情由 cron 调用本机接口完成。"
echo "第一次补历史数据可以现在执行：sudo CTI_API_URL=http://127.0.0.1:8081 bash deploy/daily.sh"
