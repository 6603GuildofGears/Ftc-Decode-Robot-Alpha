# FTC VS Code Deployment Tools

Deploy FTC Robot Controller code to REV Control Hub using VS Code instead of Android Studio.

## Quick Start

### Prerequisites
- JDK 17
- Android SDK platform-tools (adb)
- Wi-Fi connection to REV Control Hub

### Deploy Your Code

**macOS/Linux:**
```bash
./scripts/deploy_teamcode_wifi.sh
```

**Windows:**
```cmd
scripts\deploy_teamcode_wifi.bat
```

## Features

- ✅ Deploy TeamCode over Wi-Fi without Android Studio
- ✅ Automatic Java environment detection
- ✅ Cross-platform support (macOS, Linux, Windows)
- ✅ Optional clean builds and app restart
- ✅ Works offline once dependencies are cached

## Documentation

See [DEPLOYMENT.md](DEPLOYMENT.md) for:
- Complete installation instructions
- Troubleshooting guide
- Platform-specific setup
- AI assistant integration notes

## Scripts

- `scripts/deploy_teamcode_wifi.sh` - Bash script for macOS/Linux
- `scripts/deploy_teamcode_wifi.bat` - Batch script for Windows

## Usage

```bash
# Basic deploy
./scripts/deploy_teamcode_wifi.sh

# Custom IP address
./scripts/deploy_teamcode_wifi.sh 192.168.49.1:5555

# Clean build
./scripts/deploy_teamcode_wifi.sh --clean

# Auto-restart app
./scripts/deploy_teamcode_wifi.sh --restart
```

## Integration with Main FTC Repository

These scripts are designed to work with the official [FtcRobotController](https://github.com/FIRST-Tech-Challenge/FtcRobotController) repository.

To add these scripts to your FTC project:

1. Copy `scripts/` folder to your FtcRobotController directory
2. Copy `DEPLOYMENT.md` to your project root
3. Make the bash script executable: `chmod +x scripts/deploy_teamcode_wifi.sh`
4. Follow setup instructions in DEPLOYMENT.md

## Team

**6603 Guild of Gears**
- FTC Team competing in the current season
- Using Limelight vision and AprilTag tracking

## License

These deployment scripts are MIT licensed. The FTC SDK itself follows the FIRST BSD license.

## Contributing

Improvements welcome! This is designed to help teams develop more efficiently with their preferred tools.
