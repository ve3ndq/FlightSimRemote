# FlightSimRemote

Android app and Python server for sending remote commands to flight simulators via SimConnect.

## Components

### Android App
- Material 3 Jetpack Compose UI
- Configurable hotkey buttons
- Socket communication to Python server

### Python Server
- Listens for commands from Android app
- Bridges to SimConnect for flight simulator integration
- Press Ctrl+C to quit

## Setup

### Android App
1. Open project in Android Studio
2. Build and install: `./gradlew installDebug`
3. Configure server IP in the app

### Python Server
1. Navigate to `python_server/`
2. Run: `python server.py`
3. Default port: 5555

## Usage

1. Start the Python server on your PC
2. Launch the Android app
3. Enter server IP and port
4. Add custom commands with labels
5. Tap buttons to send commands

## Requirements

- Android SDK 24+
- Python 3.x
- SimConnect SDK (for flight simulator integration)
