#!/bin/bash
# 🚀 1-Click Interactive Web Demo Launcher for Client Ali's Text Chat App
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PORT=8089

echo "======================================================="
echo "📱 تشغيل المعاينة التفاعلية لتطبيق غرف الدردشة الجماعية"
echo "======================================================="
echo "📂 المجلد: $DIR/demo"
echo "🌐 الرابط: http://localhost:$PORT/index.html"
echo "👉 افتح الرابط في المتصفح لرؤية واجهة الشات والشريط الأزرق"
echo "======================================================="

python3 -m http.server $PORT --directory "$DIR/demo"
