#!/usr/bin/env bash
# MediaX Client Installer
# Run once: bash install_mediax.sh
# After that, launch with: mediax  (or from your app launcher)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLIENT_SRC="$SCRIPT_DIR/mediax_client.py"
INSTALL_DIR="$HOME/.local/share/mediax"
BIN_DIR="$HOME/.local/bin"
DESKTOP_DIR="$HOME/.local/share/applications"

echo "── MediaX Installer ─────────────────────────────"

# 1. Check dependencies
echo "Checking Python dependencies..."
python3 -c "import requests" 2>/dev/null || pip3 install requests --break-system-packages -q
python3 -c "from PIL import Image" 2>/dev/null || pip3 install pillow --break-system-packages -q

if ! command -v mpv &>/dev/null; then
    echo ""
    echo "⚠  mpv not found. Install it with:"
    echo "   sudo apt install mpv       # Debian/Ubuntu"
    echo "   sudo pacman -S mpv         # Arch"
    echo "   sudo dnf install mpv       # Fedora"
    echo ""
fi

# 2. Copy client script
mkdir -p "$INSTALL_DIR"
cp "$CLIENT_SRC" "$INSTALL_DIR/mediax_client.py"
echo "Installed client to $INSTALL_DIR/"

# 3. Create launcher in ~/.local/bin
mkdir -p "$BIN_DIR"
cat > "$BIN_DIR/mediax" << 'EOF'
#!/usr/bin/env bash
exec python3 "$HOME/.local/share/mediax/mediax_client.py" "$@"
EOF
chmod +x "$BIN_DIR/mediax"
echo "Created launcher: $BIN_DIR/mediax"

# 4. Create .desktop entry (shows in app launcher / rofi / wofi)
mkdir -p "$DESKTOP_DIR"
cat > "$DESKTOP_DIR/mediax.desktop" << EOF
[Desktop Entry]
Name=MediaX
Comment=Stream music from your Android phone
Exec=$BIN_DIR/mediax
Icon=audio-headphones
Terminal=false
Type=Application
Categories=Audio;Music;
Keywords=music;stream;android;
EOF
echo "Created desktop entry: $DESKTOP_DIR/mediax.desktop"

# 5. Make sure ~/.local/bin is on PATH
if [[ ":$PATH:" != *":$HOME/.local/bin:"* ]]; then
    echo ""
    echo "⚠  $HOME/.local/bin is not in your PATH."
    echo "   Add this to your ~/.bashrc or ~/.zshrc:"
    echo '   export PATH="$HOME/.local/bin:$PATH"'
    echo "   Then run: source ~/.bashrc"
fi

echo ""
echo "✓  Done! You can now:"
echo "   • Run from terminal:  mediax"
echo "   • Run with known IP:  mediax --host 192.168.x.x"
echo "   • Open from your app launcher (search 'MediaX')"