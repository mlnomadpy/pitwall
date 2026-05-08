#!/data/data/com.termux/files/usr/bin/bash

echo "========================================="
echo "🏎️  PITWALL TERMUX SETUP SCRIPT 🏎️"
echo "========================================="

# 1. Update and install required packages
echo "[1/4] Updating packages and installing dependencies..."
pkg update -y
pkg upgrade -y
pkg install -y git python python-pip binutils make clang pkg-config openssl libffi

# 2. Allow External Apps to send Intents
echo "[2/4] Enabling external intents..."
mkdir -p ~/.termux
echo "allow-external-apps = true" > ~/.termux/termux.properties
# Reload settings
termux-reload-settings

# 3. Clone Repository
echo "[3/4] Cloning Pitwall Repository (Branch: ${BRANCH:-main})..."
cd ~
if [ -d "pitwall" ]; then
    echo "Repository already exists, fetching latest..."
    cd pitwall
    git fetch
    git checkout ${BRANCH:-main}
    git pull origin ${BRANCH:-main}
else
    git clone -b ${BRANCH:-main} https://github.com/mlnomadpy/pitwall.git
    cd pitwall
fi

# 4. Setup Python Environment
echo "[4/4] Setting up Python Environment..."
# Install uv for fast package management
pip install uv
uv venv .venv
source .venv/bin/activate
uv pip install -e .[all]

echo "========================================="
echo "✅ Setup Complete!"
echo "Your Python backend is ready to be launched by the Pitwall Android app."
echo "========================================="
