param(
  [string]$BaseUrl = "http://localhost:8080",
  [long]$RoomTypeId = 1,
  [int]$Stock = 3,
  [int]$Concurrency = 50,
  [int]$RoomsPerOrder = 1,
  [string]$CheckIn = "",
  [string]$MysqlPassword = "142857"
)

$ErrorActionPreference = "Continue"
if (-not $CheckIn) {
  $CheckIn = (Get-Date).AddDays(60).ToString("yyyy-MM-dd")
}
$CheckOut = ([datetime]::Parse($CheckIn)).AddDays(1).ToString("yyyy-MM-dd")
$outDir = Join-Path $PSScriptRoot "..\docs"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$resultFile = Join-Path $outDir ("loadtest-oversell-" + (Get-Date -Format "yyyyMMdd-HHmmss") + ".json")

function Login([string]$user, [string]$pass) {
  $body = "{`"username`":`"$user`",`"password`":`"$pass`"}"
  $r = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post -ContentType "application/json" -Body $body
  if ($r.code -ne 0) { throw "login failed: $($r | ConvertTo-Json -Compress)" }
  return $r.data.token
}

function Mysql([string]$sql) {
  $tmp = Join-Path $env:TEMP ("hr-lt-" + [guid]::NewGuid().ToString("N") + ".sql")
  Set-Content -Path $tmp -Value $sql -Encoding ASCII
  $psi = New-Object System.Diagnostics.ProcessStartInfo
  $psi.FileName = "mysql"
  $psi.Arguments = "-uroot -p$MysqlPassword --default-character-set=utf8mb4 -N -e `"source $tmp`""
  $psi.RedirectStandardOutput = $true
  $psi.RedirectStandardError = $true
  $psi.UseShellExecute = $false
  $p = [System.Diagnostics.Process]::Start($psi)
  $stdout = $p.StandardOutput.ReadToEnd()
  $stderr = $p.StandardError.ReadToEnd()
  $p.WaitForExit()
  Remove-Item $tmp -Force
  if ($p.ExitCode -ne 0 -and $stderr -notmatch "Warning") {
    throw "mysql failed: $stderr"
  }
  return $stdout.Trim()
}

Write-Host "checkIn=$CheckIn checkOut=$CheckOut roomTypeId=$RoomTypeId stock=$Stock concurrency=$Concurrency"

# Reset MySQL inventory for the night (create row if missing using total from room_type)
$resetSql = @"
UPDATE hotel_reservation.room_inventory
SET available = $Stock, total_rooms = $Stock
WHERE room_type_id = $RoomTypeId AND stay_date = '$CheckIn';
INSERT INTO hotel_reservation.room_inventory (room_type_id, stay_date, available, total_rooms, price, version, deleted)
SELECT $RoomTypeId, '$CheckIn', $Stock, $Stock, rt.base_price
FROM hotel_reservation.room_type rt
WHERE rt.id = $RoomTypeId
  AND NOT EXISTS (
    SELECT 1 FROM hotel_reservation.room_inventory ri
    WHERE ri.room_type_id = $RoomTypeId AND ri.stay_date = '$CheckIn'
  );
SELECT available, total_rooms FROM hotel_reservation.room_inventory
WHERE room_type_id = $RoomTypeId AND stay_date = '$CheckIn';
"@
$before = Mysql $resetSql
Write-Host "mysql after reset: $before"

$adminToken = Login "admin" "admin123"
$demoToken = Login "demo" "demo123"
$adminHeaders = @{ Authorization = "Bearer $adminToken" }
$demoHeaders = @{ Authorization = "Bearer $demoToken"; "Content-Type" = "application/json" }

# Reload redis from MySQL for this room type
Invoke-RestMethod -Uri "$BaseUrl/api/admin/inventory/reload/$RoomTypeId" -Method Post -Headers $adminHeaders | Out-Null

# Peek redis via wsl
$redisKey = "inv:${RoomTypeId}:" + ([datetime]::Parse($CheckIn)).ToString("yyyyMMdd")
$redisBefore = (wsl -e redis-cli GET $redisKey 2>$null)
Write-Host "redis $redisKey before=$redisBefore"

$sw = [System.Diagnostics.Stopwatch]::StartNew()
$bag = [System.Collections.Concurrent.ConcurrentBag[object]]::new()
$runspacePool = [runspacefactory]::CreateRunspacePool(1, [Math]::Min($Concurrency, 32))
$runspacePool.Open()
$jobs = @()

1..$Concurrency | ForEach-Object {
  $i = $_
  $ps = [powershell]::Create().AddScript({
    param($BaseUrl, $Headers, $RoomTypeId, $CheckIn, $CheckOut, $RoomsPerOrder, $i, $Bag)
    try {
      $reqId = "lt-" + [guid]::NewGuid().ToString("N")
      $body = @{
        roomTypeId = $RoomTypeId
        checkIn = $CheckIn
        checkOut = $CheckOut
        rooms = $RoomsPerOrder
        requestId = $reqId
      } | ConvertTo-Json
      $resp = Invoke-WebRequest -Uri "$BaseUrl/api/orders" -Method Post -Headers $Headers -Body $body -UseBasicParsing
      $json = $resp.Content | ConvertFrom-Json
      $Bag.Add([pscustomobject]@{
        idx = $i
        statusCode = [int]$resp.StatusCode
        code = $json.code
        message = $json.message
        orderNo = $(if ($json.data) { $json.data.orderNo } else { $null })
        ok = ($json.code -eq 0)
      }) | Out-Null
    } catch {
      $ex = $_.Exception
      $msg = $ex.Message
      $code = -1
      $status = 0
      try {
        $resp2 = $_.ErrorDetails.Message | ConvertFrom-Json
        $code = $resp2.code
        $msg = $resp2.message
        $status = 200
      } catch {}
      $Bag.Add([pscustomobject]@{
        idx = $i
        statusCode = $status
        code = $code
        message = $msg
        orderNo = $null
        ok = $false
      }) | Out-Null
    }
  }).AddArgument($BaseUrl).AddArgument($demoHeaders).AddArgument($RoomTypeId).AddArgument($CheckIn).AddArgument($CheckOut).AddArgument($RoomsPerOrder).AddArgument($i).AddArgument($bag)
  $ps.RunspacePool = $runspacePool
  $jobs += [pscustomobject]@{ ps = $ps; handle = $ps.BeginInvoke() }
}

foreach ($j in $jobs) {
  $j.ps.EndInvoke($j.handle) | Out-Null
  $j.ps.Dispose()
}
$runspacePool.Close()
$runspacePool.Dispose()
$sw.Stop()

$results = $bag.ToArray()
$success = @($results | Where-Object { $_.ok }).Count
$soldOut = @($results | Where-Object { -not $_.ok -and ($_.code -eq 410 -or $_.message -match "库存|SOLD|不足") }).Count
$otherFail = $Concurrency - $success - $soldOut

$mysqlAfter = Mysql "SELECT available, total_rooms FROM hotel_reservation.room_inventory WHERE room_type_id=$RoomTypeId AND stay_date='$CheckIn';"
$redisAfter = (wsl -e redis-cli GET $redisKey 2>$null)
$orderCount = Mysql "SELECT COUNT(*) FROM hotel_reservation.booking_order WHERE room_type_id=$RoomTypeId AND check_in='$CheckIn' AND status IN ('PENDING_PAY','CONFIRMED');"

# Parse available
$avail = 0
if ($mysqlAfter -match "(\d+)") { $avail = [int]$Matches[1] }
$redisAvail = 0
if ($redisAfter -match "^-?\d+$") { $redisAvail = [int]$redisAfter }

$oversell = ($success * $RoomsPerOrder -gt $Stock) -or ($avail -lt 0) -or ($redisAvail -lt 0)
$pass = ($success -eq $Stock) -and (-not $oversell) -and ($avail -eq 0) -and ($redisAvail -eq 0)

$summary = [ordered]@{
  at = (Get-Date).ToString("s")
  baseUrl = $BaseUrl
  roomTypeId = $RoomTypeId
  roomName = "豪华大床房 (万达嘉华)"
  checkIn = $CheckIn
  checkOut = $CheckOut
  stock = $Stock
  concurrency = $Concurrency
  roomsPerOrder = $RoomsPerOrder
  elapsedMs = $sw.ElapsedMilliseconds
  successOrders = $success
  soldOutResponses = $soldOut
  otherFailures = $otherFail
  mysqlAvailableAfter = $avail
  redisKey = $redisKey
  redisAvailableAfter = $redisAfter
  bookingOrdersOnDate = $orderCount.Trim()
  oversellDetected = $oversell
  pass = $pass
}
$summary | ConvertTo-Json | Set-Content -Path $resultFile -Encoding UTF8

Write-Host "---- RESULT ----"
$summary.GetEnumerator() | ForEach-Object { Write-Host ("{0}={1}" -f $_.Key, $_.Value) }
Write-Host "saved=$resultFile"
if (-not $pass) { exit 2 }

