param(
    [string]$Port = "18102"
)

$ErrorActionPreference = "Stop"
$jar = Get-ChildItem "apps/api/build/libs/*.jar" | Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName
$out = "reports/debug-monitoring.out.log"
$err = "reports/debug-monitoring.err.log"
if (Test-Path $out) { Remove-Item $out -Force }
if (Test-Path $err) { Remove-Item $err -Force }
$proc = Start-Process -FilePath "java.exe" -ArgumentList @("-jar", $jar, "--server.port=$Port") -WorkingDirectory (Get-Location) -RedirectStandardOutput $out -RedirectStandardError $err -PassThru
try {
    $deadline = (Get-Date).AddSeconds(45)
    do {
        try {
            $r = Invoke-WebRequest -Uri "http://localhost:$Port/" -UseBasicParsing -TimeoutSec 2
            if ($r.StatusCode -eq 200) { break }
        } catch {}
        Start-Sleep -Milliseconds 800
    } while ((Get-Date) -lt $deadline)

    $headers = @{ "X-User-Id" = "17171717-1717-1717-1717-171717171717" }
    $save = Invoke-RestMethod -Uri "http://localhost:$Port/api/v1/content-links" -Method Post -ContentType "application/json; charset=utf-8" -Headers $headers -Body (@{ url = "https://blog.naver.com/PostView.naver?blogId=leevely1112&logNo=222693673604" } | ConvertTo-Json -Compress) -TimeoutSec 20
    try {
        Invoke-RestMethod -Uri "http://localhost:$Port/api/v1/monitoring/ai-enhancements" -Headers $headers -TimeoutSec 10 | ConvertTo-Json -Depth 6
    } catch {
        "ERROR_BEGIN"
        $_ | Out-String
        "ERROR_END"
        "LOG_BEGIN"
        Get-Content $out
        if (Test-Path $err) { Get-Content $err }
        "LOG_END"
    }
}
finally {
    if ($proc -and -not $proc.HasExited) {
        Stop-Process -Id $proc.Id -Force
    }
}
