@echo off
REM Compila el mod. Necesitas JDK 25 instalado (JAVA_HOME apuntando a el).
call gradlew.bat build
if errorlevel 1 (
  echo.
  echo *** La compilacion fallo. Copia el error y pasaselo a Claude. ***
  pause
  exit /b 1
)
echo.
echo Listo: tu .jar esta en build\libs\ruleta-desafios-1.0.0.jar
start "" build\libs
pause
