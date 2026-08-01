# Compare Hyperliquid / Ondo settled prints vs live predicted (phase 0 measurement)
# Usage: pwsh ./scripts/compare-funding-settled.ps1

$ErrorActionPreference = "Stop"

Write-Host "=== Hyperliquid fundingHistory (settled) vs metaAndAssetCtxs (predicted) ==="
$start = [DateTimeOffset]::UtcNow.AddHours(-6).ToUnixTimeMilliseconds()
$histBody = @{ type = "fundingHistory"; coin = "BTC"; startTime = $start } | ConvertTo-Json
$hist = Invoke-RestMethod -Method Post -Uri "https://api.hyperliquid.xyz/info" -ContentType "application/json" -Body $histBody
$liveBody = @{ type = "metaAndAssetCtxs" } | ConvertTo-Json
$live = Invoke-RestMethod -Method Post -Uri "https://api.hyperliquid.xyz/info" -ContentType "application/json" -Body $liveBody

$btcIdx = 0
for ($i = 0; $i -lt $live[0].universe.Count; $i++) {
  if ($live[0].universe[$i].name -eq "BTC") { $btcIdx = $i; break }
}
$predicted = [decimal]$live[1][$btcIdx].funding
$lastSettled = [decimal]($hist | Select-Object -Last 1).fundingRate
Write-Host ("HL predicted (accruing now): {0}" -f $predicted)
Write-Host ("HL last settled print:      {0}" -f $lastSettled)
if ($lastSettled -ne 0) {
  $rel = [math]::Abs(($predicted - $lastSettled) / $lastSettled * 100)
  Write-Host ("HL relative gap predicted vs last settled: {0:N2}%" -f $rel)
}

Write-Host ""
Write-Host "=== Ondo funding_rate_history (settled) vs funding_rates (estimate) ==="
$ondoHist = Invoke-RestMethod -Uri "https://api.ondoperps.xyz/v1/perps/funding_rate_history?market=BTC-USD.P&limit=5"
$ondoLive = Invoke-RestMethod -Uri "https://api.ondoperps.xyz/v1/perps/funding_rates?market=BTC-USD.P"
$ondoPred = [decimal]$ondoLive.result.rate
$ondoSettled = [decimal]$ondoHist.result[0].fundingRate
Write-Host ("Ondo predicted/estimate: {0}" -f $ondoPred)
Write-Host ("Ondo last settled:      {0} @ {1}" -f $ondoSettled, $ondoHist.result[0].time)
Write-Host ("Ondo next intervalEnds: {0}" -f $ondoLive.result.intervalEnds)

Write-Host ""
Write-Host "Note: AVG of mid-interval predicted samples != settled print."
Write-Host "Use funding_settlement table for history; last_funding for live opportunities."
