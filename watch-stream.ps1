# Watches the live order stream through the gateway.
#
# Run this in one terminal and place orders in another: each saga transition arrives as
# its own push, with no polling and no refresh.
#
#   .\watch-stream.ps1                          # restaurant A (Alice)
#   .\watch-stream.ps1 -Email bob@orqentra.test -Password bob-pass
#   .\watch-stream.ps1 -Email admin@orqentra.test -Password admin-pass   # every restaurant

param(
    [string]$Gateway = "http://localhost:8080",
    [string]$Email = "alice@orqentra.test",
    [string]$Password = "alice-pass"
)

$ErrorActionPreference = "Stop"

Write-Host "Logging in as $Email ..." -ForegroundColor Cyan

$body = @{ email = $Email; password = $Password } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri "$Gateway/api/auth/login" `
    -ContentType 'application/json' -Body $body

if (-not $login.token) {
    throw "No token returned; check the credentials and that the gateway is up."
}

Write-Host "Opening stream at $Gateway/api/notifications/stream" -ForegroundColor Cyan
Write-Host "Press Ctrl+C to stop.`n" -ForegroundColor DarkGray

# -N disables curl's output buffering. Without it nothing is printed until the connection
# closes, and an SSE connection never does, so the terminal would just sit there empty.
# --no-buffer is the same switch spelled out.
curl.exe -N --no-buffer `
    -H "Authorization: Bearer $($login.token)" `
    -H "Accept: text/event-stream" `
    "$Gateway/api/notifications/stream"
