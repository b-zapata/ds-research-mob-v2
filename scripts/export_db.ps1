# Quick script to export the database from device
# Usage: .\export_db.ps1

param(
    [string]$DeviceId = "53061FDAP00258"
)

$packageName = "com.mindful.android.debug"
$dbPath = "/data/data/$packageName/app_flutter/Mindful.sqlite"
$timestamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"

# Create db_exports directory if it doesn't exist
$exportDir = "db_exports"
if (-not (Test-Path $exportDir)) {
    New-Item -ItemType Directory -Path $exportDir | Out-Null
}

$outputFile = Join-Path $exportDir "Mindful_exported_$timestamp.sqlite"

$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")

Write-Host "Exporting database..." -ForegroundColor Cyan

# Stream database directly using exec-out (must preserve binary output)
$startInfo = New-Object System.Diagnostics.ProcessStartInfo
$startInfo.FileName = "adb"
$startInfo.Arguments = "-s $DeviceId exec-out `"run-as $packageName cat $dbPath`""
$startInfo.UseShellExecute = $false
$startInfo.RedirectStandardOutput = $true
$startInfo.RedirectStandardError = $true

$process = [System.Diagnostics.Process]::Start($startInfo)
$outputStream = $process.StandardOutput.BaseStream
$fileStream = [System.IO.File]::Create($outputFile)

try {
    $buffer = New-Object byte[] 8192
    while (($bytesRead = $outputStream.Read($buffer, 0, $buffer.Length)) -gt 0) {
        $fileStream.Write($buffer, 0, $bytesRead)
    }
    $process.WaitForExit()
} finally {
    $outputStream.Close()
    $fileStream.Close()
    $process.Dispose()
}

if (Test-Path $outputFile) {
    Write-Host "[SUCCESS] Database exported to: $outputFile" -ForegroundColor Green
    Write-Host "  Size: $([math]::Round((Get-Item $outputFile).Length / 1KB, 2)) KB" -ForegroundColor Gray
} else {
    Write-Host "[ERROR] Export failed" -ForegroundColor Red
}

