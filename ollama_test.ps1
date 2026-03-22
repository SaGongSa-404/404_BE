$body = '{"model":"qwen3:4b","prompt":"Respond with {\"ok\":true}","stream":false}'
Invoke-RestMethod -Uri 'http://localhost:11434/api/generate' -Method Post -ContentType 'application/json' -Body $body | ConvertTo-Json -Depth 8
