# FTC Robot Controller Deployment Guide

This document describes how to deploy FTC TeamCode to the REV Control Hub using VS Code and Wi-Fi, without requiring Android Studio.

## Prerequisites

### Required Software (All Platforms)

1. **Java Development Kit (JDK) 17**
   - Required by Gradle 8.9 to build the project
   - This project uses Gradle 8.9 and compiles to Java 8 bytecode (compatible with Android)

2. **Android SDK Platform Tools**
   - Provides `adb` (Android Debug Bridge) for deploying over Wi-Fi
   - Minimum version: 30.0.0 or later

3. **Git** (for version control)

4. **VS Code** (or any text editor)

---

## Installation Instructions

### macOS

#### 1. Install JDK 17 via Homebrew

```bash
# Install Homebrew if not already installed
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# Install OpenJDK 17
brew install openjdk@17
```

The deploy script automatically detects this installation at `/opt/homebrew/opt/openjdk@17`.

#### 2. Install Android SDK Platform Tools

**Option A: Via Homebrew (Recommended)**
```bash
brew install --cask android-platform-tools
```

**Option B: Manual Download**
1. Download from: https://developer.android.com/tools/releases/platform-tools
2. Extract to a location like `~/Library/Android/sdk/platform-tools`
3. Add to PATH in `~/.zshrc`:
   ```bash
   export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
   ```

#### 3. Verify Installation

```bash
# Check Java
/opt/homebrew/opt/openjdk@17/bin/java -version
# Should show: openjdk version "17.0.x"

# Check adb
adb version
# Should show: Android Debug Bridge version 30.x or higher
```

---

### Windows

#### 1. Install JDK 17

**Option A: Eclipse Temurin (Recommended)**
1. Download installer from: https://adoptium.net/temurin/releases/?version=17
2. Run installer and select "Set JAVA_HOME" during installation
3. Install to default location: `C:\Program Files\Eclipse Adoptium\jdk-17`

**Option B: Microsoft Build of OpenJDK**
1. Download from: https://learn.microsoft.com/en-us/java/openjdk/download
2. Run MSI installer for JDK 17

#### 2. Install Android SDK Platform Tools

**Option A: Via Android Studio (if already installed)**
- Tools are located at: `%LOCALAPPDATA%\Android\Sdk\platform-tools`

**Option B: Standalone Download**
1. Download from: https://developer.android.com/tools/releases/platform-tools
2. Extract to: `C:\Android\sdk\platform-tools` (or your preferred location)
3. Add to System PATH:
   - Search "Environment Variables" in Windows Start menu
   - Edit "Path" under System Variables
   - Add: `C:\Android\sdk\platform-tools`

#### 3. Verify Installation

Open Command Prompt or PowerShell:
```cmd
# Check Java
java -version
REM Should show: openjdk version "17.0.x"

# Check adb
adb version
REM Should show: Android Debug Bridge version 30.x or higher
```

---

## Deployment Workflow

### 1. Connect to Control Hub Wi-Fi

Switch your computer's Wi-Fi network to the REV Control Hub network:
- Network name: Usually starts with `DIRECT-` or configured name
- Default IP: `192.168.43.1`
- Default Port: `5555`

### 2. Run Deployment Script

**macOS/Linux:**
```bash
cd /path/to/FtcRobotController
./scripts/deploy_teamcode_wifi.sh
```

**Windows:**
```cmd
cd C:\path\to\FtcRobotController
scripts\deploy_teamcode_wifi.bat
```

### 3. Script Options

Both scripts support the following options:

```bash
# Default deployment (192.168.43.1:5555)
./scripts/deploy_teamcode_wifi.sh

# Custom IP/port
./scripts/deploy_teamcode_wifi.sh 192.168.49.1:5555

# Clean build before deploy
./scripts/deploy_teamcode_wifi.sh --clean

# Restart Robot Controller app after deploy
./scripts/deploy_teamcode_wifi.sh --restart

# Combine options
./scripts/deploy_teamcode_wifi.sh --clean --restart
```

---

## Troubleshooting

### "Unable to locate a Java Runtime"

**macOS:**
- Ensure JDK 17 is installed: `brew list openjdk@17`
- The script should auto-detect at `/opt/homebrew/opt/openjdk@17`
- Manually set: `export JAVA_HOME=/opt/homebrew/opt/openjdk@17`

**Windows:**
- Verify `JAVA_HOME` environment variable is set
- Check: `echo %JAVA_HOME%` (should show JDK path)
- Verify `java -version` works from Command Prompt

### "adb not found"

**macOS:**
```bash
# Check installation
which adb
# If not found, install via Homebrew
brew install --cask android-platform-tools
```

**Windows:**
- Verify `adb.exe` is in your PATH
- Check: `where adb`
- Ensure Android SDK platform-tools directory is in System PATH

### "ADB does not list device"

1. Verify you're connected to Control Hub Wi-Fi network
2. Test connection manually:
   ```bash
   adb connect 192.168.43.1:5555
   adb devices
   ```
3. If device shows as "offline", try:
   ```bash
   adb disconnect
   adb connect 192.168.43.1:5555
   ```

### Build Errors

**"Could not resolve dependencies"**
- Ensure you have internet access (may need to deploy once on internet before going offline)
- Gradle downloads dependencies on first build

**"Gradle daemon disappeared unexpectedly"**
- May indicate insufficient memory or Java version mismatch
- Try: `./gradlew --stop` then retry deployment

---

## Important Notes from FTC Documentation

⚠️ **DO NOT Update Android Gradle Plugin (AGP)**

When importing this project, you may see prompts to update the Android Gradle Plugin. **Do NOT update** as newer versions may not be compatible with the FTC SDK.

Reference: https://ftc-docs.firstinspires.org/en/latest/programming_resources/tutorial_specific/android_studio/downloading_as_project_folder/Downloading-the-Android-Studio-Project-Folder.html

---

## Additional Resources

- **FTC SDK Documentation**: https://ftc-docs.firstinspires.org/
- **FTC SDK Repository**: https://github.com/FIRST-Tech-Challenge/FtcRobotController
- **REV Hardware Client**: https://docs.revrobotics.com/rev-hardware-client/
- **Android ADB Documentation**: https://developer.android.com/tools/adb

---

## For AI Assistants

### Quick Setup Checklist

When helping users with this project, ensure they have:

1. ✅ **JDK 17 installed**
   - macOS: `brew install openjdk@17`
   - Windows: Download from adoptium.net

2. ✅ **Android SDK platform-tools (adb)**
   - macOS: `brew install --cask android-platform-tools`
   - Windows: Download from developer.android.com

3. ✅ **Connected to Control Hub Wi-Fi**
   - Default IP: 192.168.43.1:5555

4. ✅ **Run deployment script**
   - macOS: `./scripts/deploy_teamcode_wifi.sh`
   - Windows: `scripts\deploy_teamcode_wifi.bat`

### Common Issues

- **No Java**: Install JDK 17 (not JRE)
- **No adb**: Install Android SDK platform-tools
- **Can't connect**: Verify Wi-Fi network and IP address
- **Build fails**: Check Gradle compatibility (uses 8.9) and Java version (needs 17)

### Project Structure

- `TeamCode/src/main/java/` - Team's OpModes and code
- `scripts/deploy_teamcode_wifi.sh` - macOS/Linux deployment
- `scripts/deploy_teamcode_wifi.bat` - Windows deployment
- `build.gradle` - Do NOT modify Gradle/AGP versions
- Uses Gradle 8.9, compiles to Java 8 bytecode
