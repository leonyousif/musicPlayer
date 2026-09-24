@echo off
setlocal enabledelayedexpansion

cd /d "%~dp0"

set "VLC_DIR="

rem Check standard installation paths for 64-bit VLC
if exist "%ProgramFiles%\VideoLAN\VLC\libvlc.dll" (
    set "VLC_DIR=%ProgramFiles%\VideoLAN\VLC"
) else if exist "C:\Program Files\VideoLAN\VLC\libvlc.dll" (
    set "VLC_DIR=C:\Program Files\VideoLAN\VLC"
) else (
    rem Check if libvlc.dll is available in PATH
    where libvlc.dll >nul 2>&1
    if !errorlevel! equ 0 (
        for /f "delims=" %%I in ('where libvlc.dll 2^>nul') do (
            if not defined VLC_DIR (
                set "VLC_DIR=%%~dpI"
                set "VLC_DIR=!VLC_DIR:~0,-1!"
            )
        )
    )
)

rem If not found in standard paths and not in PATH, interactively prompt user
if not defined VLC_DIR (
    :PROMPT_VLC
    set /p "USER_VLC=Enter path to VLC installation folder: "
    if defined USER_VLC (
        set "USER_VLC=!USER_VLC:"=!"
        if exist "!USER_VLC!\libvlc.dll" (
            set "VLC_DIR=!USER_VLC!"
        )
    )
    if not defined VLC_DIR (
        echo Error: libvlc.dll not found in "!USER_VLC!". Please ensure you enter the path to the VLC installation directory.
        goto PROMPT_VLC
    )
)

rem Ensure VLC directory is in PATH for native DLL loading
set "PATH=%VLC_DIR%;%PATH%"
if exist "%VLC_DIR%\plugins" (
    set "VLC_PLUGIN_PATH=%VLC_DIR%\plugins"
)

java -jar vlc-music-player-1.0-SNAPSHOT.jar %*
