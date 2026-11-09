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

if [[ -z "${CTI_DOCKER_MIRROR:-}" && -f "$env_file" ]]; then
  CTI_DOCKER_MIRROR="$(grep -E '^CTI_DOCKER_MIRROR=' "$env_file" | tail -1 | cut -d= -f2- | tr -d '"' | tr -d "'" || true)"
fi

if [[ -n "${CTI_DOCKER_MIRROR:-}" ]]; then
  if [[ "$(id -u)" -ne 0 ]]; then
    echo "配置镜像加速需要 root：sudo CTI_DOCKER_MIRROR=${CTI_DOCKER_MIRROR} bash deploy/up.sh"
    exit 1
  fi
  python3 - "$CTI_DOCKER_MIRROR" <<'PY'
import json, os, sys
mirror = sys.argv[1].rstrip("/")
path = "/etc/docker/daemon.json"
os.makedirs("/etc/docker", exist_ok=True)
data = {}
if os.path.exists(path) and os.path.getsize(path) > 0:
    with open(path) as handle:
        data = json.load(handle)
mirrors = [item for item in data.get("registry-mirrors", []) if item.rstrip("/") != mirror]
data["registry-mirrors"] = [mirror] + mirrors
with open(path, "w") as handle:
    json.dump(data, handle, indent=2)
    handle.write("\n")
PY
  systemctl restart docker
  echo "已使用镜像加速：${CTI_DOCKER_MIRROR}"
fi

echo "开始构建并启动 MySQL、Redis、Python 和 Java。第一次构建会下载镜像并编译，需要几分钟。"
if ! compose up -d --build; then
  echo
  echo "拉取 Docker Hub 超时。阿里云 ECS 需要账号自己的镜像加速地址。"
  echo "打开 https://cr.console.aliyun.com/ 左侧「镜像工具」→「镜像加速器」，复制加速器地址，然后执行："
  echo "sudo CTI_DOCKER_MIRROR=https://你的ID.mirror.aliyuncs.com bash deploy/up.sh"
  exit 1
fi

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
