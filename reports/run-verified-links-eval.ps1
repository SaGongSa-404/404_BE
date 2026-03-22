param(
  [string]$Port = "18099"
)
$ErrorActionPreference = "Stop"
$jar = Get-ChildItem "apps/api/build/libs/*.jar" | Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName
$proc = Start-Process -FilePath "java.exe" -ArgumentList @("-jar", $jar, "--server.port=$Port", "--app.ai.ollama.background-enhancement-enabled=false") -WorkingDirectory (Get-Location) -PassThru
try {
  $deadline = (Get-Date).AddSeconds(45)
  do {
    try {
      $r = Invoke-WebRequest -Uri "http://localhost:$Port/" -UseBasicParsing -TimeoutSec 2
      if ($r.StatusCode -eq 200) { break }
    } catch {}
    Start-Sleep -Milliseconds 800
  } while ((Get-Date) -lt $deadline)

  $lines = Get-Content -Encoding UTF8 'action_card_links_50_verified_2026-03-22.md'
  $rows = foreach ($line in $lines) {
    if ($line.StartsWith('|') -and -not $line.StartsWith('| #') -and -not $line.StartsWith('| ---')) {
      $parts = $line.Split('|') | ForEach-Object { $_.Trim() }
      if ($parts.Length -ge 8) {
        [pscustomobject]@{
          title = $parts[2]
          url = $parts[3]
          expected = $parts[5]
        }
      }
    }
  }

  $headers = @{ 'X-User-Id'='14141414-1414-1414-1414-141414141414' }
  $results = @()
  foreach ($row in $rows) {
    $body = @{ url = $row.url } | ConvertTo-Json -Compress
    try {
      $save = Invoke-RestMethod -Uri "http://localhost:$Port/api/v1/content-links" -Method Post -ContentType "application/json; charset=utf-8" -Headers $headers -Body $body -TimeoutSec 45
      $results += [pscustomobject]@{
        title = $row.title
        url = $row.url
        expected = $row.expected
        sourceTitle = $save.practiceCard.sourceTitle
        category = $save.practiceCard.categoryLabel
        actionTitle = $save.practiceCard.actionTitle
      }
    } catch {
      $results += [pscustomobject]@{
        title = $row.title
        url = $row.url
        expected = $row.expected
        sourceTitle = $null
        category = 'ERROR'
        actionTitle = $_.Exception.Message
      }
    }
  }
  $results | ConvertTo-Json -Depth 5
}
finally {
  if ($proc -and -not $proc.HasExited) { Stop-Process -Id $proc.Id -Force }
}
