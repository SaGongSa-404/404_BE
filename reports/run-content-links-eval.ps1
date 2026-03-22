param(
    [string]$Port = "18096",
    [string]$UserId = "99999999-9999-9999-9999-999999999999",
    [switch]$EnableEnhancement
)

$ErrorActionPreference = "Stop"

$jar = Get-ChildItem "apps/api/build/libs/*.jar" | Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName
$args = @("-jar", $jar, "--server.port=$Port")
if (-not $EnableEnhancement) {
    $args += "--app.ai.ollama.background-enhancement-enabled=false"
}

$proc = Start-Process -FilePath "java.exe" -ArgumentList $args -WorkingDirectory (Get-Location) -PassThru
try {
    $deadline = (Get-Date).AddSeconds(45)
    do {
        try {
            $r = Invoke-WebRequest -Uri "http://localhost:$Port/" -UseBasicParsing -TimeoutSec 2
            if ($r.StatusCode -eq 200) { break }
        } catch {}
        Start-Sleep -Milliseconds 800
    } while ((Get-Date) -lt $deadline)

    $content = Get-Content -Raw -Encoding UTF8 "content-platform-links.md"
    $urls = [regex]::Matches($content, 'https?://\S+') | ForEach-Object { $_.Value.TrimEnd(')', ']', ',') }
    $headers = @{ "X-User-Id" = $UserId }
    $results = @()

    foreach ($url in $urls) {
        $body = @{ url = $url } | ConvertTo-Json -Compress
        try {
            $save = Invoke-RestMethod -Uri "http://localhost:$Port/api/v1/content-links" -Method Post -ContentType "application/json; charset=utf-8" -Headers $headers -Body $body -TimeoutSec 45
            $results += [pscustomobject]@{
                url = $url
                sourceTitle = $save.practiceCard.sourceTitle
                category = $save.practiceCard.categoryLabel
                actionTitle = $save.practiceCard.actionTitle
            }
        } catch {
            $results += [pscustomobject]@{
                url = $url
                sourceTitle = $null
                category = "ERROR"
                actionTitle = $_.Exception.Message
            }
        }
    }

    $results | ConvertTo-Json -Depth 4
}
finally {
    if ($proc -and -not $proc.HasExited) {
        Stop-Process -Id $proc.Id -Force
    }
}
