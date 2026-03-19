@echo off
chcp 949 >nul 2>&1
echo Fixing file encoding...
copy 실행.bat 실행.bat.backup
type 실행.bat > 실행_temp.bat
move /y 실행_temp.bat 실행.bat
echo Done! Please try running 실행.bat again.
pause
