@echo off
chcp 65001 >nul

echo.
echo ================================================================
echo                  FRewards v2.0 - BUILD + PROGUARD
echo ================================================================
echo.

cd /d "%~dp0"

REM --- CONFIGURAR JDK ---

if exist "%USERPROFILE%\.jdks\ms-21.0.9" (
    set "JAVA_HOME=%USERPROFILE%\.jdks\ms-21.0.9"
    echo [OK] Usando JDK: %JAVA_HOME%
) else if exist "%USERPROFILE%\.jdks\openjdk-21" (
    set "JAVA_HOME=%USERPROFILE%\.jdks\openjdk-21"
    echo [OK] Usando JDK: %JAVA_HOME%
) else if exist "%USERPROFILE%\.jdks\openjdk-17" (
    set "JAVA_HOME=%USERPROFILE%\.jdks\openjdk-17"
    echo [OK] Usando JDK: %JAVA_HOME%
) else if exist "C:\Program Files\Java\jdk-21" (
    set "JAVA_HOME=C:\Program Files\Java\jdk-21"
    echo [OK] Usando JDK: %JAVA_HOME%
) else if exist "C:\Program Files\Java\jdk-17" (
    set "JAVA_HOME=C:\Program Files\Java\jdk-17"
    echo [OK] Usando JDK: %JAVA_HOME%
) else if exist "C:\Program Files\Eclipse Adoptium\jdk-17" (
    set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17"
    echo [OK] Usando JDK: %JAVA_HOME%
)

"%JAVA_HOME%\bin\java.exe" -version 2>nul
if %ERRORLEVEL% NEQ 0 (
    java -version 2>nul
    if %ERRORLEVEL% NEQ 0 (
        echo [ERROR] Java no encontrado. Instala JDK 17 o superior.
        pause
        exit /b 1
    )
)
echo.

REM --- BUSCAR MAVEN ---

set "MVN="

if exist "..\FBoxCore\apache-maven-3.9.6\bin\mvn.cmd" (
    set "MVN=..\FBoxCore\apache-maven-3.9.6\bin\mvn.cmd"
    echo [OK] Maven encontrado en FBoxCore
) else if exist "apache-maven-3.9.6\bin\mvn.cmd" (
    set "MVN=apache-maven-3.9.6\bin\mvn.cmd"
    echo [OK] Usando Maven portable local
) else if exist "..\apache-maven-3.9.6\bin\mvn.cmd" (
    set "MVN=..\apache-maven-3.9.6\bin\mvn.cmd"
    echo [OK] Usando Maven: ..\apache-maven-3.9.6
) else if exist "%USERPROFILE%\apache-maven-3.9.6\bin\mvn.cmd" (
    set "MVN=%USERPROFILE%\apache-maven-3.9.6\bin\mvn.cmd"
    echo [OK] Usando Maven: %USERPROFILE%\apache-maven-3.9.6
) else (
    mvn -version >nul 2>&1
    if %ERRORLEVEL% EQU 0 (
        set "MVN=mvn"
        echo [OK] Usando Maven del PATH del sistema
    ) else (
        echo [ERROR] Maven no encontrado.
        echo.
        echo Copia la carpeta apache-maven-3.9.6 de FBoxCore aqui o instalalo en el PATH.
        pause
        exit /b 1
    )
)

echo.

REM --- BUILD ---

echo [1/3] Limpiando proyecto...
call %MVN% clean -q
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Error limpiando proyecto
    pause
    exit /b 1
)

echo [2/3] Compilando con ProGuard...
echo.
call %MVN% package -DskipTests
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Error durante la compilacion / ProGuard
    echo Revisa los logs de Maven arriba para mas detalles.
    pause
    exit /b 1
)

echo.
echo [3/3] Copiando output...
if not exist "builds" mkdir builds
if exist "target\FRewards-2.0-pg.jar" (
    copy /y "target\FRewards-2.0-pg.jar" "builds\FRewards-2.0.jar" >nul
    echo [OK] Copiado a: builds\FRewards-2.0.jar  (ofuscado con ProGuard)
) else if exist "target\FRewards-2.0.jar" (
    copy /y "target\FRewards-2.0.jar" "builds\FRewards-2.0.jar" >nul
    echo [OK] Copiado a: builds\FRewards-2.0.jar
)

echo.
echo [OK] Build completado!
echo.
echo ================================================================
echo   ARCHIVOS GENERADOS:
echo ================================================================
echo.

for %%f in (target\FRewards-*.jar) do (
    echo   [JAR] %%~nxf  ^|  %%~zf bytes
)

echo.
echo ================================================================
echo   Distribuir: builds\FRewards-2.0.jar  (ofuscado con ProGuard)
echo ================================================================
echo.

pause
