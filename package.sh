#!/usr/bin/env bash
#
# Builds a self contained desktop app under target/dist using jpackage.
# Requires a JDK 17+ with jpackage on PATH (the bundled ./mvnw JDK is fine).
#
set -euo pipefail
cd "$(dirname "$0")"

APP_NAME="AttendanceBook"
MAIN_CLASS="com.attendance.app.Launcher"
JFX_VERSION="21.0.6"
MVN_REPO="${HOME}/.m2/repository"

echo "==> Compiling and packaging the jar"
./mvnw -q -B clean package

echo "==> Collecting runtime dependencies"
rm -rf target/app target/dist target/jfx
mkdir -p target/app
./mvnw -q -B dependency:copy-dependencies \
    -DincludeScope=runtime \
    -DoutputDirectory=target/app

cp target/StudentAttendanceApp-*.jar target/app/app.jar

echo "==> Adding JavaFX native libraries for this platform"
case "$(uname -s)" in
    Linux)  CLASSIFIER="linux" ;;
    Darwin) CLASSIFIER="mac" ;;
    *)      CLASSIFIER="win" ;;
esac
for module in base graphics controls; do
    jar="${MVN_REPO}/org/openjfx/javafx-${module}/${JFX_VERSION}/javafx-${module}-${JFX_VERSION}-${CLASSIFIER}.jar"
    if [ -f "$jar" ]; then
        cp "$jar" target/app/
    else
        echo "WARNING: missing $jar" >&2
    fi
done

echo "==> Running jpackage"
jpackage \
    --type app-image \
    --name "$APP_NAME" \
    --input target/app \
    --main-jar app.jar \
    --main-class "$MAIN_CLASS" \
    --dest target/dist \
    --java-options "-Xmx512m" \
    --java-options "-Dattendance.db=${HOME}/.local/share/${APP_NAME}/attendance.db"

APP_DIR="$(pwd)/target/dist/${APP_NAME}"
echo "==> Wrote ${APP_DIR}"
echo "    Launch it with: ${APP_DIR}/bin/${APP_NAME}"

echo "==> Writing desktop entry"
mkdir -p target/dist/desktop
cat > "target/dist/desktop/${APP_NAME}.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=Attendance Book
Comment=Student attendance tracker
Exec=${APP_DIR}/bin/${APP_NAME}
Terminal=false
Categories=Education;Office;
EOF
echo "    Desktop entry: target/dist/desktop/${APP_NAME}.desktop"
echo
echo "To install for the current user:"
echo "  cp target/dist/desktop/${APP_NAME}.desktop ~/.local/share/applications/"