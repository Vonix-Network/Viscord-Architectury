@echo off
setlocal enabledelayedexpansion

echo ==================================================
echo Building all Viscord versions...
echo ==================================================

set ROOT_DIR=%CD%
set RELEASES_DIR=%ROOT_DIR%\Releases
if not exist "%RELEASES_DIR%" mkdir "%RELEASES_DIR%"

for %%V in (1.18.2 1.19.2 1.20.1 1.21.1) do (
    echo.
    echo Building version %%V...
    echo.
    
    REM Find the template directory
    for /d %%D in (viscord-%%V-*) do (
        set VER_DIR=%%D
    )
    
    if defined VER_DIR (
        cd "%ROOT_DIR%\!VER_DIR!"
        call gradlew build
        if errorlevel 1 (
            echo Failed to build %%V!
        ) else (
            echo Build %%V successful. Copying jars...
            
            REM Copy Fabric jars
            if exist "fabric\build\libs\viscord-*-fabric.jar" (
                copy /y "fabric\build\libs\viscord-*-fabric.jar" "%RELEASES_DIR%\"
            )
            
            REM Copy Forge/NeoForge jars
            if exist "forge\build\libs\viscord-*-forge.jar" (
                copy /y "forge\build\libs\viscord-*-forge.jar" "%RELEASES_DIR%\"
            )
            if exist "neoforge\build\libs\viscord-*-neoforge.jar" (
                copy /y "neoforge\build\libs\viscord-*-neoforge.jar" "%RELEASES_DIR%\"
            )
        )
    ) else (
        echo Could not find directory for version %%V
    )
)

echo.
echo ==================================================
echo Build process complete.
echo ==================================================
cd "%ROOT_DIR%"
