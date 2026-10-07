param(
    [string]$Task = ":app:assembleFossDebug",
    [switch]$NoStop
)

$exitCode = 0
try {
    Write-Host "Kitsugi derleme başlatılıyor: $Task" -ForegroundColor Cyan
    & .\gradlew.bat $Task
    $exitCode = $LASTEXITCODE
} catch {
    Write-Error $_
    $exitCode = 1
} finally {
    if (-not $NoStop) {
        Write-Host "Derleme tamamlandı ($exitCode). Arka planda kalan Gradle/JDK daemon işlemleri durduruluyor..." -ForegroundColor Yellow
        & .\gradlew.bat --stop
        Get-WmiObject Win32_Process -Filter "Name = 'java.exe'" -ErrorAction SilentlyContinue | Where-Object {
            $_.CommandLine -like "*GradleDaemon*" -or $_.CommandLine -like "*KotlinCompileDaemon*"
        } | ForEach-Object {
            Write-Host "Kalan daemon sonlandırılıyor (PID: $($_.ProcessId))" -ForegroundColor DarkGray
            Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
        }
        Write-Host "RAM temizlendi." -ForegroundColor Green
    }
}
exit $exitCode
