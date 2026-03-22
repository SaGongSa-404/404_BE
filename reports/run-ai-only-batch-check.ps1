param(
    [string]$InputMarkdown = "action_card_links_50_verified_2026-03-22.md",
    [string]$Port = "18103",
    [int]$WaitSecondsPerCard = 12,
    [string]$OutputJson = ""
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

    $lines = Get-Content -Encoding UTF8 $InputMarkdown
    $rows = foreach ($line in $lines) {
        if ($line.StartsWith('|') -and -not $line.StartsWith('| #') -and -not $line.StartsWith('| ---')) {
            $escaped = $line -replace '\\\|', '[PIPE]'
            $parts = $escaped.Split('|') | ForEach-Object { $_.Trim().Replace('[PIPE]', '|') }
            if ($parts.Length -ge 8) {
                [pscustomobject]@{
                    title = $parts[2]
                    url = $parts[3]
                    expected = $parts[5]
                }
            }
        }
    }

    $mapping = @{
        fitness = @("운동")
        tutorial = @("학습")
        "tutorial/docs" = @("학습")
        study = @("학습", "집중")
        event = @("행사·전시")
        travel = @("행사·전시", "학습")
        exhibition = @("행사·전시")
        "travel phrase" = @("학습")
        cooking = @("요리")
        recipe = @("요리")
        dessert = @("요리")
        breakfast = @("요리")
        lunch = @("요리")
        dinner = @("요리")
        main = @("요리")
        appetizer = @("요리")
        salad = @("요리")
        soup = @("요리")
        "recipe hub" = @("요리")
        "recipe roundup" = @("요리")
        "social design tool" = @("학습")
        "design tutorial" = @("학습")
        "social design tutorial" = @("학습")
        "instagram marketing guide" = @("집중", "학습")
        "size guide" = @("학습")
        "template library" = @("학습")
        "content ideas" = @("학습", "실천")
        "social listening tool" = @("집중", "학습")
        "instagram planning tool" = @("학습", "집중")
        "case study" = @("집중", "학습")
        "email marketing guide" = @("집중")
        "newsletter guide" = @("집중")
        "photography/social tips" = @("학습", "실천")
        "productivity article" = @("집중")
        explainer = @("학습")
        "product review" = @("실천", "학습")
        "travel guide" = @("행사·전시", "학습")
        "web design article" = @("학습")
    }

    $headers = @{ "X-User-Id" = "18181818-1818-1818-1818-181818181818" }
    $results = @()

    foreach ($row in $rows) {
        $body = @{ url = $row.url } | ConvertTo-Json -Compress
        try {
            $save = Invoke-RestMethod -Uri "http://localhost:$Port/api/v1/content-links" -Method Post -ContentType "application/json; charset=utf-8" -Headers $headers -Body $body -TimeoutSec 20
            $cardId = $save.practiceCard.id
            $final = $save.practiceCard
            $status = $final.enhancementStatus
            $deadlineCard = (Get-Date).AddSeconds($WaitSecondsPerCard)
            do {
                if ($status -ne "PENDING") { break }
                Start-Sleep -Milliseconds 700
                try {
                    $final = Invoke-RestMethod -Uri ("http://localhost:$Port/api/v1/practice-cards/{0}" -f $cardId) -Method Get -Headers $headers -TimeoutSec 10
                    $status = $final.enhancementStatus
                } catch {}
            } while ((Get-Date) -lt $deadlineCard)

            $expectedLabels = $mapping[$row.expected]
            $matched = ($status -eq "ENHANCED") -and ($expectedLabels -contains $final.categoryLabel)
            $results += [pscustomobject]@{
                title = $row.title
                url = $row.url
                expected = $row.expected
                expectedLabel = ($expectedLabels -join ", ")
                enhancementStatus = $status
                actualLabel = $final.categoryLabel
                matched = $matched
                actionTitle = $final.actionTitle
            }
        } catch {
            $results += [pscustomobject]@{
                title = $row.title
                url = $row.url
                expected = $row.expected
                expectedLabel = (($mapping[$row.expected]) -join ", ")
                enhancementStatus = "ERROR"
                actualLabel = "ERROR"
                matched = $false
                actionTitle = $_.Exception.Message
            }
        }
    }

    $summary = $results | Group-Object enhancementStatus | ForEach-Object {
        [pscustomobject]@{
            status = $_.Name
            count = $_.Count
        }
    }

    $payload = [pscustomobject]@{
        total = $results.Count
        enhancedAndMatched = ($results | Where-Object matched).Count
        aiOnlyAccuracy = [math]::Round((($results | Where-Object matched).Count / [math]::Max($results.Count, 1)) * 100, 2)
        byEnhancementStatus = $summary
        results = $results
    } | ConvertTo-Json -Depth 6

    if ($OutputJson) {
        Set-Content -Encoding UTF8 $OutputJson $payload
    }

    $payload
}
finally {
    if ($proc -and -not $proc.HasExited) {
        Stop-Process -Id $proc.Id -Force
    }
}
