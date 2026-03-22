param(
    [string]$Port = "18101"
)

$ErrorActionPreference = "Stop"
$jar = Get-ChildItem "apps/api/build/libs/*.jar" | Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName
$proc = Start-Process -FilePath "java.exe" -ArgumentList @("-jar", $jar, "--server.port=$Port") -WorkingDirectory (Get-Location) -PassThru
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

    try {
        Invoke-RestMethod -Uri "http://localhost:$Port/api/v1/content-links" -Method Post -ContentType "application/json; charset=utf-8" -Headers $headers -Body (@{ url = "https://linktr.ee/test" } | ConvertTo-Json -Compress) -TimeoutSec 15
    } catch {
        "UNSUPPORTED=" + $_.ErrorDetails.Message
    }

    $save = Invoke-RestMethod -Uri "http://localhost:$Port/api/v1/content-links" -Method Post -ContentType "application/json; charset=utf-8" -Headers $headers -Body (@{ url = "https://blog.naver.com/PostView.naver?blogId=leevely1112&logNo=222693673604" } | ConvertTo-Json -Compress) -TimeoutSec 20
    "CARD=" + $save.practiceCard.id
    Invoke-RestMethod -Uri "http://localhost:$Port/api/v1/monitoring/ai-enhancements" -Headers $headers -TimeoutSec 10 | ConvertTo-Json -Depth 6
    Invoke-RestMethod -Uri ("http://localhost:$Port/api/v1/practice-cards/{0}/report-issue" -f $save.practiceCard.id) -Method Post -ContentType "application/json; charset=utf-8" -Headers $headers -Body (@{ reason = "테스트 신고" } | ConvertTo-Json -Compress) -TimeoutSec 10 | ConvertTo-Json -Depth 6
    Invoke-RestMethod -Uri ("http://localhost:$Port/api/v1/practice-cards/{0}/regenerate" -f $save.practiceCard.id) -Method Post -Headers $headers -TimeoutSec 10 | ConvertTo-Json -Depth 6
}
finally {
    if ($proc -and -not $proc.HasExited) {
        Stop-Process -Id $proc.Id -Force
    }
}
