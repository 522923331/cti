# Crypto Trend Intelligence

现货、只做多的加密货币趋势研究系统。规格见原文档，缺口和取舍见 `docs/spec-gaps.md`。

当前完成到 Phase 0：Java / Python 工程、MySQL 迁移、Redis、XXL-Job 执行器、Docker Compose、健康检查。还没有行情采集和评分。

## 本阶段可以调用的接口

Java，端口 8081。本机 8080 上已经有别的服务，所以第一版改到 8081：

- `GET /api/v1/admin/system-health`：MySQL、Redis、XXL-Job 是否可用
- `GET /api/v1/admin/jobs`：已注册任务和最近 20 条执行记录
- `POST /api/v1/admin/jobs/cti_health_probe_job/trigger`：手动跑探测任务。它会写 `ct_job_run`，并把时间戳写到 Redis 键 `cti:health:probe`

Python，端口 8090：

- `GET /health`

没有 XXL-Job Admin 时，健康检查里的 `xxlJob` 会是 `DEGRADED`，整个状态也是 `DEGRADED`。MySQL 或 Redis 失败时状态是 `DOWN`，HTTP 503。

## 本地启动

本机如果没有 Docker，用下面的方式。有 Docker 时在仓库根目录执行 `docker compose up -d`，MySQL 用户 `cti` / `cti`，XXL-Job Admin 是 `http://127.0.0.1:8088/xxl-job-admin`，账号 `admin` / `123456`。

```bash
redis-server --daemonize yes --port 6379

# MySQL 8 建库后：
# CREATE DATABASE cti CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
# CREATE USER 'cti'@'localhost' IDENTIFIED BY 'cti';
# GRANT ALL ON cti.* TO 'cti'@'localhost';

cd cti-server
export JAVA_HOME="/Users/k02/Library/Java/JavaVirtualMachines/ms-17.0.15/Contents/Home"
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/maven/bin:$PATH"
mvn -pl cti-api -am package -DskipTests
java -jar cti-api/target/cti-api-1.0.0-SNAPSHOT.jar

cd cti-quant
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
.venv/bin/uvicorn app.main:app --host 127.0.0.1 --port 8090
```

## 验收

```bash
curl -sS http://127.0.0.1:8081/api/v1/admin/system-health
curl -sS -X POST http://127.0.0.1:8081/api/v1/admin/jobs/cti_health_probe_job/trigger
curl -sS http://127.0.0.1:8081/api/v1/admin/jobs
redis-cli GET cti:health:probe
curl -sS http://127.0.0.1:8090/health
```

探测任务再调用一次，`attempt` 会变成 2，不会改掉上一条记录。
