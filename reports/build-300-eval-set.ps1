param(
    [string]$OutputPath = "reports/eval-set-300.md"
)

$ErrorActionPreference = "Stop"
$files = Get-ChildItem "reports/link-batches" -File | Sort-Object Name
$rows = @()

foreach ($file in $files) {
    $contentRows = Get-Content -Encoding UTF8 $file.FullName | Where-Object { $_ -match '^\| ' -and $_ -notmatch '^\| #|^\| ---' }
    foreach ($line in $contentRows) {
        $escaped = $line -replace '\\\|', '[PIPE]'
        $parts = $escaped.Split('|') | ForEach-Object { $_.Trim().Replace('[PIPE]', '|') }
        if ($parts.Length -ge 8) {
            $rows += [pscustomobject]@{
                Title = $parts[2]
                URL = $parts[3]
                Platform = $parts[4]
                Category = $parts[5]
                Intent = $parts[6]
                Why = $parts[7]
            }
        }
    }
}

$uniqueRows = $rows | Group-Object URL | ForEach-Object { $_.Group[0] } | Select-Object -First 300

$lines = @(
    "# Eval Set 300",
    "",
    "- 생성일: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')",
    "- 출처: reports/link-batches/*",
    "- 총 링크 수: $($uniqueRows.Count)",
    "",
    "| # | Title | URL | Platform | Category | 추론된 의도(액션카드용) | 왜 바로 의도가 보이는가 |",
    "| --- | --- | --- | --- | --- | --- | --- |"
)

$index = 1
foreach ($row in $uniqueRows) {
    $title = $row.Title.Replace('|', '\|')
    $url = $row.URL
    $platform = $row.Platform.Replace('|', '\|')
    $category = $row.Category.Replace('|', '\|')
    $intent = $row.Intent.Replace('|', '\|')
    $why = $row.Why.Replace('|', '\|')
    $lines += "| $index | $title | $url | $platform | $category | $intent | $why |"
    $index++
}

Set-Content -Encoding UTF8 $OutputPath $lines
Write-Output $OutputPath
