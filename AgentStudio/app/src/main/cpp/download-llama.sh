#!/bin/bash
# Download llama.cpp source for Android build

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LLAMA_DIR="$SCRIPT_DIR/llama.cpp"

echo "=== Downloading llama.cpp ==="

if [ -d "$LLAMA_DIR" ]; then
    echo "llama.cpp already exists at $LLAMA_DIR"
    read -p "Update? (y/n): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Keeping existing llama.cpp"
        exit 0
    fi
    rm -rf "$LLAMA_DIR"
fi

# Clone llama.cpp (shallow clone for faster download)
echo "Cloning llama.cpp..."
git clone --depth 1 https://github.com/ggerganov/llama.cpp.git "$LLAMA_DIR"

echo ""
echo "=== llama.cpp downloaded successfully ==="
echo "Location: $LLAMA_DIR"
echo ""
echo "Now you can build the native library with:"
echo "  cd $SCRIPT_DIR"
echo "  ./build-native.sh"
