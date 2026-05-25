#!/usr/bin/env bash
# ===========================================================
# ONTOS Rule Engine · 写 systemd unit 并启动
# 用法：bash deploy/server-systemd.sh
# 前置：/opt/ontos-rule/app.jar 已经 scp 上来
# ===========================================================
set -euo pipefail

JAR=/opt/ontos-rule/app.jar
if [ ! -f "$JAR" ]; then
  echo "❌ 找不到 $JAR — 请先 scp 上 jar"
  echo "   scp <jar> root@<server>:/opt/ontos-rule/app.jar"
  exit 1
fi

echo '═══════════ 写 systemd unit ═══════════'
cat > /etc/systemd/system/ontos-rule.service <<'EOF'
[Unit]
Description=ONTOS Rule Engine (Spring Boot + Vue)
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/ontos-rule
Environment=JAVA_HOME=/opt/jdk-21
ExecStart=/opt/jdk-21/bin/java -XX:MaxRAMPercentage=75 -XX:+UseG1GC -Dfile.encoding=UTF-8 -jar /opt/ontos-rule/app.jar --server.port=19091
Restart=on-failure
RestartSec=5
SuccessExitStatus=143
StandardOutput=append:/var/log/ontos-rule.log
StandardError=append:/var/log/ontos-rule.log

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable --now ontos-rule

echo
echo '═══════════ 等 25s 让 Spring Boot 起来 ═══════════'
sleep 25

echo
echo '═══════════ systemctl status ═══════════'
systemctl status ontos-rule --no-pager | head -12 || true

echo
echo '═══════════ 日志末尾 ═══════════'
tail -20 /var/log/ontos-rule.log

echo
echo '═══════════ HTTP 探活 ═══════════'
for path in / /playground /rules /api/rules; do
  code=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:19091${path}" || echo "ERR")
  printf 'GET %-15s → %s\n' "${path}" "${code}"
done

echo
echo '═══════════ ✅ 完毕 ═══════════'
echo '浏览器打开：'
echo '  http://10.10.104.98:19091/playground'
echo '  http://10.10.104.98:19091/rules'
echo
echo '日常运维：'
echo '  systemctl start|stop|restart|status ontos-rule'
echo '  tail -f /var/log/ontos-rule.log'
