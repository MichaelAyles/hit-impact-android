# BLESensorViewer

BLESensorViewer is an Android application designed to interact with Bluetooth Low Energy (BLE) devices. It allows users to scan for BLE devices, connect to them, and view sensor data in real-time.

## Features

- Scan for nearby BLE devices
- Connect to selected BLE devices
- View and analyze sensor data
- User-friendly interface

## Prerequisites

- Android Studio
- Android device with BLE support
- Minimum Android SDK version: 21

## Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/BLESensorViewer.git
   ```
2. Open the project in Android Studio.
3. Build the project and run it on an Android device or emulator with BLE support.

## Usage

1. Launch the BLESensorViewer app on your Android device.
2. Use the scan feature to discover nearby BLE devices.
3. Select a device to connect and view its sensor data.

## Project Structure

- `app/src/main/java/com/example/blesensorviewer/`: Contains the main application logic.
  - `MainActivity.kt`: The main entry point of the application.
  - `ScanActivity.kt`: Handles the scanning of BLE devices.
  - `ImpactDataActivity.kt`: Displays impact data from connected devices.
  - `RawDataActivity.kt`: Shows raw sensor data.
  - `Bluetooth.kt`: Manages Bluetooth connections and data handling.

- `app/src/main/res/layout/`: Contains XML layout files for the app's UI.
  - `activity_main.xml`: Layout for the main activity.
  - `activity_scan.xml`: Layout for the scan activity.
  - `activity_impact_data.xml`: Layout for the impact data activity.
  - `activity_raw_data.xml`: Layout for the raw data activity.

- `app/src/main/res/values/`: Contains resource values such as strings, colors, and themes.

## Contributing

Contributions are welcome! Please fork the repository and submit a pull request for any improvements or bug fixes.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Contact

For any inquiries or support, please contact [your-email@example.com].
