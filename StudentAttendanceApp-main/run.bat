@echo off
setlocal
cd /d "%~dp0"
echo ==> Running Student Attendance App...
call mvnw.cmd javafx:run
pause
