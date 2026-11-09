# Linux 部署

Alibaba Cloud Linux 4 容器优化版在仓库目录执行 `sudo bash deploy/up.sh`。脚本会安装系统源里的 moby 和 docker compose，再启动 MySQL、Redis、Python 和 Java。

这是研究系统，只拉币安公开行情，只做模拟。服务器不需要交易 API Key。

一台机器上有四个进程：MySQL、Redis、Python 计算服务、Java 页面和任务。XXL-Job Admin 只负责定时调用，不负责计算。系统没有登录。接口在本机用手动触发就能跑完全部任务。

不要把 3306、6379、8090、9999、8088 暴露到公网。页面端口 8081 也没有鉴权，能访问它的人就能触发任务和模拟下单。对外只开放 22，以及反向代理的 80 或 443。8081 只给本机或固定 IP。

Java 和 Python 都读环境变量 `CTI_MYSQL_URL`，格式不同，必须分成两个文件：

- Java：`jdbc:mysql://127.0.0.1:3306/cti?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8&serverTimezone=UTC&connectionTimeZone=UTC`
- Python：`mysql+pymysql://cti:密码@127.0.0.1:3306/cti`

业务日期用 UTC。日线在 UTC 0 点收盘，定时任务要排在这之后，例如北京时间 08:20。

## 安装

需要 Java 17、Python 3.11 或更高（开发机是 3.13）、MySQL 8.0、Redis。Maven 3.9 只在打包时需要，可以在开发机上打包，把 jar 拷走。

```bash
sudo apt update
sudo apt install -y openjdk-17-jre-headless python3 python3-venv python3-pip mysql-server redis-server
java -version
python3 --version
```

建立目录和用户：

```bash
sudo useradd --system --create-home --home-dir /opt/cti --shell /usr/sbin/nologin cti
sudo mkdir -p /opt/cti/cti-server /opt/cti/cti-quant /opt/cti/bin /var/lib/cti /var/log/cti
sudo chown -R cti:cti /opt/cti /var/lib/cti /var/log/cti
```

把 `cti-quant` 整个目录，以及打好的 `cti-api-1.0.0-SNAPSHOT.jar` 放到对应位置。Python 代码不在 jar 里，必须一起带上。

开发机上打包：

```bash
cd cti-server
mvn -pl cti-api -am package -DskipTests
```

产物是 `cti-server/cti-api/target/cti-api-1.0.0-SNAPSHOT.jar`。

## 数据库和 Redis

MySQL 使用 `utf8mb4`，默认时区 `+00:00`。密码不要继续用开发库的 `cti`。

```bash
sudo mysql <<'SQL'
CREATE DATABASE cti CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'cti'@'127.0.0.1' IDENTIFIED BY '换成服务器密码';
GRANT ALL ON cti.* TO 'cti'@'127.0.0.1';
FLUSH PRIVILEGES;
SQL
```

在 MySQL 配置里加上 `default-time-zone = '+00:00'`，然后重启 MySQL。Redis 只监听 `127.0.0.1:6379`。

表不用手工建。Java 第一次启动时，Flyway 会执行 V1 到 V17。其中 V2 和 V6 写在 jar 里，不在 SQL 文件中。空库启动后会有表和策略种子，没有开发机上的行情、评分和模拟账户。

要连同现有研究数据一起搬，就在开发机上导出，再导入服务器：

```bash
mysqldump --default-character-set=utf8mb4 -ucti -p cti > cti.sql
mysql --default-character-set=utf8mb4 -ucti -p cti < cti.sql
```

导入已有库之后，再启动 Java。Flyway 会校验这 17 个迁移，显示 schema 已是最新，不会重建表。

仓库里的 `docker-compose.yml` 可以代替本机 MySQL、Redis 和 XXL-Job Admin。它把 3306、6379、8088 发布到所有网卡，数据库密码是 `cti` / `root`。公网机器不要原样执行。要用的话，把端口绑到 `127.0.0.1`，并先改密码。Admin 的种子任务只有健康探测，而且调度类型是 `NONE`，不会每天拉行情。

## 环境文件

`/etc/cti/java.env`：

```bash
JAVA_TOOL_OPTIONS=-Duser.timezone=UTC
CTI_MYSQL_URL=jdbc:mysql://127.0.0.1:3306/cti?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8&serverTimezone=UTC&connectionTimeZone=UTC
CTI_MYSQL_USER=cti
CTI_MYSQL_PASSWORD=换成服务器密码
CTI_REDIS_HOST=127.0.0.1
CTI_REDIS_PORT=6379
CTI_QUANT_URL=http://127.0.0.1:8090
CTI_DATA_DIR=/var/lib/cti
CTI_XXL_ADMIN=http://127.0.0.1:8088/xxl-job-admin
```

`/etc/cti/python.env`：

```bash
CTI_MYSQL_URL=mysql+pymysql://cti:换成服务器密码@127.0.0.1:3306/cti
CTI_REDIS_HOST=127.0.0.1
CTI_REDIS_PORT=6379
```

```bash
sudo mkdir -p /etc/cti
sudo chown root:cti /etc/cti/*.env
sudo chmod 640 /etc/cti/*.env
```

Python 虚拟环境：

```bash
sudo -u cti python3 -m venv /opt/cti/cti-quant/.venv
sudo -u cti /opt/cti/cti-quant/.venv/bin/pip install -r /opt/cti/cti-quant/requirements.txt
```

## systemd

`/etc/systemd/system/cti-quant.service`：

```ini
[Unit]
Description=CTI Python calculations
After=network.target mysql.service redis-server.service

[Service]
User=cti
Group=cti
WorkingDirectory=/opt/cti/cti-quant
EnvironmentFile=/etc/cti/python.env
ExecStart=/opt/cti/cti-quant/.venv/bin/uvicorn app.main:app --host 127.0.0.1 --port 8090
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

`/etc/systemd/system/cti-api.service`：

```ini
[Unit]
Description=CTI Java API
After=cti-quant.service mysql.service redis-server.service

[Service]
User=cti
Group=cti
WorkingDirectory=/opt/cti/cti-server
EnvironmentFile=/etc/cti/java.env
ExecStart=/usr/bin/java -jar /opt/cti/cti-server/cti-api-1.0.0-SNAPSHOT.jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now redis-server mysql cti-quant cti-api
sudo systemctl status cti-quant cti-api
```

启动顺序是 MySQL 和 Redis，然后 Python，最后 Java。Java 启动后才会连 Python。

## 让日报和情报每天更新

进程内没有定时器。要更新，就按这个顺序调用本机接口。后一步依赖前一步的已收盘数据：

1. `cti_sync_kline_daily_job`
2. `cti_sync_kline_4h_job`
3. `cti_data_quality_job`
4. `cti_sync_derivatives_job`
5. `cti_sync_market_overview_job`
6. `cti_sync_news_job`
7. `cti_sync_onchain_job`
8. `cti_build_daily_feature_job`
9. `cti_build_market_regime_job`
10. `cti_build_trend_score_job`
11. `cti_generate_signal_job`
12. `cti_portfolio_risk_job`
13. `cti_build_intelligence_score_job`
14. `cti_build_intelligence_alert_job`
15. `cti_intelligence_report_job`
16. `cti_daily_report_job`

第一次日线同步会补大约 800 天，可能要几分钟到几十分钟。任务锁是 20 分钟，第一次不要并行再点一次。链上任务会成功返回“未接入”，表保持空。Dominance、TOTAL、TOTAL3 也没有来源，不会出现数字。

`/opt/cti/bin/daily.sh`：

```bash
#!/bin/bash
set -euo pipefail
jobs=(
  cti_sync_kline_daily_job
  cti_sync_kline_4h_job
  cti_data_quality_job
  cti_sync_derivatives_job
  cti_sync_market_overview_job
  cti_sync_news_job
  cti_sync_onchain_job
  cti_build_daily_feature_job
  cti_build_market_regime_job
  cti_build_trend_score_job
  cti_generate_signal_job
  cti_portfolio_risk_job
  cti_build_intelligence_score_job
  cti_build_intelligence_alert_job
  cti_intelligence_report_job
  cti_daily_report_job
)
for job in "${jobs[@]}"; do
  echo "run $job"
  curl -fsS --max-time 3600 -X POST "http://127.0.0.1:8081/api/v1/admin/jobs/${job}/trigger"
  echo
done
```

```bash
sudo chmod 755 /opt/cti/bin/daily.sh
sudo crontab -u cti -e
```

crontab 里加：

```cron
20 0 * * * /opt/cti/bin/daily.sh >> /var/log/cti/daily.log 2>&1
```

这是 UTC 00:20。系统时区如果是上海，就改成 `20 8 * * *`。

回测任务 `cti_run_backtest_job` 不放进每天的链。样本内结果不能标成策略通过，现有历史也完成不了 24/3/3 的样本外走步。

XXL-Job Admin 可以后加。镜像是 `xuxueli/xxl-job-admin:2.4.0`，本机端口 8088，访问令牌必须和 Java 的 `default_token` 一致，执行器名是 `cti-executor`，执行器端口 9999。Admin 起来之后，还要在界面里把上面的任务配成 cron。现在的种子不会做这件事。Admin 没开时，健康检查的总状态是 `DEGRADED`，MySQL 和 Redis 仍是 `UP`，上面的脚本照样能跑。

助手没有定时任务。页面只在有人调用 `POST /api/v1/research/assistant/query` 时增加会话，回答引用当时库里的数据。没有模型服务，解释固定是“未能生成解释”。

## 对外访问

用 Nginx 把域名转到 `127.0.0.1:8081`。不要代理 8090。服务器防火墙放行 22、80、443，拒绝来自外网的 3306、6379、8081、8088、8090、9999。

## 核对

```bash
curl -sS http://127.0.0.1:8090/health
curl -sS http://127.0.0.1:8081/api/v1/admin/system-health
curl -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8081/
curl -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8081/intelligence
curl -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8081/reports
curl -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8081/assistant
```

Python 健康检查应返回 200。系统健康里 MySQL、Redis 为 `UP`。四个页面都是 200。第一次日线任务成功后，再跑特征、评分和两份日报，页面日期才会离开空库或旧的交易日。

## 备份和回滚

每天备份 MySQL。Flyway 没有向下脚本。回滚时停 `cti-api` 和 `cti-quant`，用迁移前的备份恢复 `cti`，换回上一版 jar 和 Python 目录。不要删除 `flyway_schema_history` 里的记录。
