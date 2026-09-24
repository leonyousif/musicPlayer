#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

VLC_DIR=""

# Check standard library paths for libvlc
STANDARD_PATHS=(
    "/usr/lib/x86_64-linux-gnu"
    "/usr/lib"
    "/usr/lib64"
    "/usr/local/lib"
    "/Applications/VLC.app/Contents/MacOS/lib"
    "/Applications/VLC.app/Contents/MacOS"
    "/Applications/VLC.app/Contents/Frameworks"
)

for dir in "${STANDARD_PATHS[@]}"; do
    if [ -f "$dir/libvlc.so" ] || [ -f "$dir/libvlc.so.5" ] || [ -f "$dir/libvlc.dylib" ]; then
        VLC_DIR="$dir"
        break
    fi
done

# If not found in standard paths, interactively prompt the user
while [ -z "$VLC_DIR" ]; do
    read -r -p "Enter path to VLC installation folder: " USER_VLC
    USER_VLC="${USER_VLC%\"}"
    USER_VLC="${USER_VLC#\"}"
    USER_VLC="${USER_VLC/#\~/$HOME}"

    if [ -f "$USER_VLC/libvlc.so" ] || [ -f "$USER_VLC/libvlc.so.5" ] || [ -f "$USER_VLC/libvlc.dylib" ]; then
        VLC_DIR="$USER_VLC"
    elif [ -f "$USER_VLC/lib/libvlc.so" ] || [ -f "$USER_VLC/lib/libvlc.so.5" ] || [ -f "$USER_VLC/lib/libvlc.dylib" ]; then
        VLC_DIR="$USER_VLC/lib"
    elif [ -f "$USER_VLC/Contents/MacOS/lib/libvlc.dylib" ]; then
        VLC_DIR="$USER_VLC/Contents/MacOS/lib"
    else
        echo "Error: libvlc not found in '$USER_VLC'. Please try again."
    fi
done

if [ -n "$VLC_DIR" ]; then
    export LD_LIBRARY_PATH="$VLC_DIR${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
    export DYLD_LIBRARY_PATH="$VLC_DIR${DYLD_LIBRARY_PATH:+:$DYLD_LIBRARY_PATH}"
    if [ -d "$VLC_DIR/plugins" ]; then
        export VLC_PLUGIN_PATH="$VLC_DIR/plugins"
    elif [ -d "$VLC_DIR/../plugins" ]; then
        export VLC_PLUGIN_PATH="$VLC_DIR/../plugins"
    fi
fi

java -jar vlc-music-player-1.0-SNAPSHOT.jar "$@"
