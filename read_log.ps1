$path = 'C:\Users\Administrator\AppData\Roaming\.minecraft\versions\eski sunucu 1.21.1\logs\debug.log'
if (!(Test-Path $path)) {
    Write-Host "File not found"
    exit
}
try {
    $stream = [System.IO.File]::Open($path, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
    $reader = New-Object System.IO.StreamReader($stream)
    $warnings = New-Object System.Collections.Generic.List[string]
    $errors = New-Object System.Collections.Generic.List[string]
    while (($line = $reader.ReadLine()) -ne $null) {
        if ($line -match "WARN") {
            $warnings.Add($line)
        } elseif ($line -match "ERROR") {
            $errors.Add($line)
        }
    }
    $reader.Close()
    $stream.Close()
    
    Write-Host "Total Warnings: $($warnings.Count)"
    Write-Host "Total Errors: $($errors.Count)"
    
    Write-Host "`n--- Sample Warnings (Last 20) ---"
    $warnStart = [Math]::Max(0, $warnings.Count - 20)
    for ($i = $warnStart; $i -lt $warnings.Count; $i++) {
        Write-Host $warnings[$i]
    }
    
    Write-Host "`n--- Sample Errors (Last 20) ---"
    $errStart = [Math]::Max(0, $errors.Count - 20)
    for ($i = $errStart; $i -lt $errors.Count; $i++) {
        Write-Host $errors[$i]
    }
} catch {
    Write-Host "Error: $_"
}
