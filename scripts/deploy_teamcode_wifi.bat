@echo off
REM Wi-Fi deploy helper for FTC TeamCode on REV Control Hub (Windows)
REM
REM Usage examples:
REM   scripts\deploy_teamcode_wifi.bat
REM   scripts\deploy_teamcode_wifi.bat 192.168.43.1:5555
REM   scripts\deploy_teamcode_wifi.bat --clean
REM   scripts\deploy_teamcode_wifi.bat --restart
REM
REM Notes:
REM - Requires Android SDK platform-tools (adb) and working Gradle wrapper.
REM - Installs the :TeamCode debug build onto the connected Control Hub.

setlocal EnableDelayedExpansion

set DEFAULT_TARGET=192.168.43.1:5555
set APP_PKG=com.qualcomm.ftcrobotcontroller
set DO_CLEAN=0
set DO_RESTART=0
set TARGET=%DEFAULT_TARGET%

REM Parse arguments
:parse_args
if "%~1"=="" goto args_done
if /I "%~1"=="--clean" (
    set DO_CLEAN=1
    shift
    goto parse_args
)
if /I "%~1"=="--restart" (
    set DO_RESTART=1
    shift
    goto parse_args
)
if /I "%~1"=="--pkg" (
    set APP_PKG=%~2
    shift
    shift
    goto parse_args
)
if /I "%~1"=="-h" goto show_help
if /I "%~1"=="--help" goto show_help
REM If contains colon, treat as target
echo %~1 | findstr /C:":" >nul
if !errorlevel!==0 (
    set TARGET=%~1
    shift
    goto parse_args
)
echo [deploy][error] Unrecognized argument: %~1
goto show_help

:show_help
echo Usage: %~nx0 [^<ip:port^>] [--clean] [--restart] [--pkg ^<package^>]
echo.
echo Options:
echo   ^<ip:port^>     Target ADB endpoint (default: %DEFAULT_TARGET%)
echo   --clean       Run 'gradlew clean' before install
echo   --restart     Attempt to (re)launch the Robot Controller app after install
echo   --pkg ^<name^>  Package to launch when --restart is set (default: %APP_PKG%)
exit /b 2

:args_done

REM Resolve repo root (assume script lives in scripts\ under the repo)
set SCRIPT_DIR=%~dp0
set REPO_ROOT=%SCRIPT_DIR%..
cd /d "%REPO_ROOT%"

echo [deploy] Repository root: %CD%

REM Locate adb.exe
set ADB=
if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
    set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
) else if exist "%USERPROFILE%\AppData\Local\Android\Sdk\platform-tools\adb.exe" (
    set ADB=%USERPROFILE%\AppData\Local\Android\Sdk\platform-tools\adb.exe
) else if exist "%ANDROID_HOME%\platform-tools\adb.exe" (
    set ADB=%ANDROID_HOME%\platform-tools\adb.exe
) else if exist "%ANDROID_SDK_ROOT%\platform-tools\adb.exe" (
    set ADB=%ANDROID_SDK_ROOT%\platform-tools\adb.exe
) else (
    where adb.exe >nul 2>&1
    if !errorlevel!==0 (
        for /f "delims=" %%i in ('where adb.exe') do set ADB=%%i
    )
)

if "%ADB%"=="" (
    echo [deploy][error] adb.exe not found. Install Android SDK platform-tools.
    exit /b 1
)

echo [deploy] Using adb: %ADB%

REM Ensure gradlew.bat exists
if not exist "%REPO_ROOT%\gradlew.bat" (
    echo [deploy][error] gradlew.bat not found at %REPO_ROOT%\gradlew.bat
    exit /b 1
)

REM Set JAVA_HOME if not set (prefer JDK 17)
if "%JAVA_HOME%"=="" (
    REM Common JDK install locations on Windows
    if exist "C:\Program Files\Eclipse Adoptium\jdk-17" (
        set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17
        echo [deploy] JAVA_HOME set to Eclipse Temurin JDK 17
    ) else if exist "C:\Program Files\Java\jdk-17" (
        set JAVA_HOME=C:\Program Files\Java\jdk-17
        echo [deploy] JAVA_HOME set to JDK 17
    ) else if exist "C:\Program Files\Microsoft\jdk-17" (
        set JAVA_HOME=C:\Program Files\Microsoft\jdk-17
        echo [deploy] JAVA_HOME set to Microsoft JDK 17
    ) else if exist "C:\Program Files\OpenJDK\jdk-17" (
        set JAVA_HOME=C:\Program Files\OpenJDK\jdk-17
        echo [deploy] JAVA_HOME set to OpenJDK 17
    ) else (
        echo [deploy][warning] JAVA_HOME not set. Gradle will try to use system Java.
    )
)

REM Connect ADB
echo [deploy] Connecting ADB to %TARGET% ...
"%ADB%" connect %TARGET% >nul 2>&1

REM Verify connection
"%ADB%" devices | findstr /C:"%TARGET%" >nul
if !errorlevel! neq 0 (
    echo [deploy][error] ADB does not list %TARGET%. Ensure Control Hub is reachable over Wi-Fi.
    "%ADB%" devices
    exit /b 1
)
echo [deploy] ADB connected to %TARGET%.

REM Optional clean
if %DO_CLEAN%==1 (
    echo [deploy] Running clean ...
    call gradlew.bat clean
    if !errorlevel! neq 0 (
        echo [deploy][error] Clean failed
        exit /b 1
    )
)

REM Install
echo [deploy] Installing :TeamCode:installDebug ... (this may take a bit)
call gradlew.bat :TeamCode:installDebug
if !errorlevel! neq 0 (
    echo [deploy][error] Install failed
    exit /b 1
)
echo [deploy] Install complete.

REM Optional restart
if %DO_RESTART%==1 (
    echo [deploy] Attempting to (re)launch %APP_PKG% via monkey ...
    "%ADB%" -s %TARGET% shell monkey -p %APP_PKG% -c android.intent.category.LAUNCHER 1 >nul 2>&1
    echo [deploy] Launch command sent.
)

echo [deploy] Done. You can now disconnect from this network if needed.
exit /b 0
