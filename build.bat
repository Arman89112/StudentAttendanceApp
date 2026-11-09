@echo off
setlocal
cd /d "%~dp0"
echo ==> Building Student Attendance App...
call mvnw.cmd clean package
echo Build finished!
pause
