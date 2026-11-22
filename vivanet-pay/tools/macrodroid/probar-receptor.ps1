<#
.SYNOPSIS
  Envía un evento de PRUEBA ("test": true) al receptor Android de Vivanet Pay. No crea pagos.

.DESCRIPTION
  Sirve para comprobar, desde el propio PC u otro equipo de la red, que el receptor responde y que el secreto es
  correcto. El secreto se pide de forma oculta (no queda en el historial ni en la línea de comandos).

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File probar-receptor.ps1 -Url http://192.168.1.20:47390/v1/events -Modo token
#>
param(
  [Parameter(Mandatory = $true)][string]$Url,
  [ValidateSet('token', 'hmac')][string]$Modo = 'token'
)
$ErrorActionPreference = 'Stop'

$seguro = Read-Host -AsSecureString 'Pegue el secreto del receptor (no se mostrará)'
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($seguro)
try { $secreto = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr) }
finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }

$ahora = [DateTimeOffset]::UtcNow
$cuerpo = '{"schemaVersion":1,"eventId":"prueba-ps-' + [Guid]::NewGuid().ToString('N') +
  '","source":"ANDROID_NEQUI","app":"Nequi","title":"Prueba Vivanet Pay","text":"Prueba desde PowerShell",' +
  '"capturedAt":"' + $ahora.ToString('yyyy-MM-ddTHH:mm:ssZ') + '","test":true}'
$bytes = [Text.Encoding]::UTF8.GetBytes($cuerpo)
$cabeceras = @{}

if ($Modo -eq 'token') {
  $cabeceras['Authorization'] = 'Bearer ' + $secreto
} else {
  $ts = [string]$ahora.ToUnixTimeSeconds()
  $nonce = [Guid]::NewGuid().ToString('N')
  $hmac = New-Object System.Security.Cryptography.HMACSHA256 (,[Text.Encoding]::UTF8.GetBytes($secreto))
  $pre = [Text.Encoding]::UTF8.GetBytes("v1`n$ts`n$nonce`n")
  $todo = New-Object byte[] ($pre.Length + $bytes.Length)
  [Array]::Copy($pre, 0, $todo, 0, $pre.Length)
  [Array]::Copy($bytes, 0, $todo, $pre.Length, $bytes.Length)
  $firma = ($hmac.ComputeHash($todo) | ForEach-Object { $_.ToString('x2') }) -join ''
  $cabeceras['X-Vivanet-Timestamp'] = $ts
  $cabeceras['X-Vivanet-Nonce'] = $nonce
  $cabeceras['X-Vivanet-Signature'] = 'v1=' + $firma
}
$secreto = $null

try {
  $r = Invoke-WebRequest -Uri $Url -Method Post -Body $bytes -ContentType 'application/json' -Headers $cabeceras -UseBasicParsing
  Write-Host ("Respuesta {0}: {1}" -f [int]$r.StatusCode, $r.Content)
  if ($r.Content -match 'test_ok') { Write-Host 'OK: el receptor recibió la prueba. No se registró ningún pago.' -ForegroundColor Green }
} catch {
  $code = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
  Write-Host ("Falló la prueba (código {0}). 401 = secreto incorrecto; 0 = sin conexión (IP/puerto/firewall)." -f $code) -ForegroundColor Red
}
