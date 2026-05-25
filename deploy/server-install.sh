#!/usr/bin/env bash
# ===========================================================
# ONTOS Rule Engine · 服务器一次性安装
# 用法：bash deploy/server-install.sh
# 内容：装 JDK 21、配 PATH、放 19091 防火墙、建 /opt/ontos-rule/data
# 幂等：重复跑不会破坏已有环境
# ===========================================================
set -euo pipefail

echo '═══════════ Step 1/3: 装 JDK 21 ═══════════'
cd /opt
if [ ! -d /opt/jdk-21 ]; then
  echo '下载 Adoptium Temurin 21.0.5 ...'
  curl -fL --retry 3 -o /tmp/jdk21.tar.gz \
    'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.5%2B11/OpenJDK21U-jdk_x64_linux_hotspot_21.0.5_11.tar.gz'
  tar xzf /tmp/jdk21.tar.gz -C /opt
  mv /opt/jdk-21.0.5+11 /opt/jdk-21
  rm -f /tmp/jdk21.tar.gz
fi
cat > /etc/profile.d/jdk21.sh <<'EOF'
export JAVA_HOME=/opt/jdk-21
export PATH=$JAVA_HOME/bin:$PATH
EOF
chmod +x /etc/profile.d/jdk21.sh
export JAVA_HOME=/opt/jdk-21
export PATH=$JAVA_HOME/bin:$PATH
echo '✓ JDK 21:'
java -version

echo
echo '═══════════ Step 2/3: 防火墙放 19091 ═══════════'
if command -v firewall-cmd >/dev/null 2>&1; then
  firewall-cmd --permanent --add-port=19091/tcp >/dev/null || true
  firewall-cmd --reload >/dev/null
  echo '✓ firewalld 已放行 19091'
elif command -v ufw >/dev/null 2>&1; then
  ufw allow 19091/tcp
  echo '✓ ufw 已放行 19091'
else
  echo '⚠️ 未识别防火墙，请手动放行 19091/tcp'
fi

echo
echo '═══════════ Step 3/3: 建目录 ═══════════'
mkdir -p /opt/ontos-rule/data
echo '✓ /opt/ontos-rule 准备好'

echo
echo '═══════════ ✅ 安装阶段完成 ═══════════'
echo
echo '下一步：'
echo '  1. 你 Windows 本机跑：'
echo '       scp <local-jar-path> root@<server>:/opt/ontos-rule/app.jar'
echo '  2. 上传完成后回服务器跑：'
echo '       bash /opt/ontos-rule-src/deploy/server-systemd.sh'
echo '     （路径换成你 git clone 的目录）'
