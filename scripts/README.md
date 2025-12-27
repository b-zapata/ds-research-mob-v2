# Development Scripts

## export_db.ps1

Exports the Mindful database from your connected Android device.

### Usage

```powershell
.\scripts\export_db.ps1
```

Or from the project root:

```powershell
.\scripts\export_db.ps1
```

### Parameters

- `-DeviceId` (optional): Device ID if multiple devices are connected. Default: `53061FDAP00258`

### Output

The script will:
1. Export the database from `/data/data/com.mindful.android.debug/app_flutter/Mindful.sqlite`
2. Save it to `db_exports/Mindful_exported_YYYY-MM-DD_HH-mm-ss.sqlite`
3. Display the file size

### Requirements

- ADB must be in your PATH
- Device must be connected via USB with USB debugging enabled
- App must be installed on the device

