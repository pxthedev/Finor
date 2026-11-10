param()
$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$mobile = Join-Path $workspace 'finor-mobile'
$listeners = @(Get-NetTCPConnection -LocalPort 8081 -State Listen -ErrorAction SilentlyContinue)
$manifestRoot = $null
if ($listeners.Count -gt 0) {
    try {
        $manifest = Invoke-RestMethod 'http://127.0.0.1:8081/' -Headers @{
            Accept = 'application/expo+json'; 'Expo-Platform' = 'android'; 'Expo-Protocol-Version' = '0'
        } -TimeoutSec 15
        $manifestRoot = $manifest.extra.expoClient._internal.projectRoot
    } catch { }
}
$targets = @()
foreach ($listener in $listeners | Sort-Object OwningProcess -Unique) {
    $info = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
    $absoluteProject = $info.CommandLine -match [regex]::Escape($mobile)
    $relativeProject = $manifestRoot -eq $mobile -and $listener.LocalAddress -eq '127.0.0.1'
    if ($info.Name -ne 'node.exe' -or $info.CommandLine -notmatch 'expo.*(start|run:android)' -or -not ($absoluteProject -or $relativeProject)) {
        throw 'A porta 8081 pertence a outro processo. Nenhum servidor foi encerrado.'
    }
    $targets += $listener.OwningProcess
}
foreach ($targetProcessId in $targets) { Stop-Process -Id $targetProcessId }
$env:EXPO_PUBLIC_API_URL = 'http://127.0.0.1:8080/api/v1'
$env:EXPO_PUBLIC_FIREBASE_AUTH_EMULATOR_URL = 'http://127.0.0.1:9099'
$env:EXPO_PUBLIC_FIREBASE_API_KEY = 'demo-key'
Remove-Item Env:CI -ErrorAction SilentlyContinue
$cli = Join-Path $mobile 'node_modules/expo/bin/cli'
$process = Start-Process -FilePath node -ArgumentList @($cli, 'start', '--localhost', '--port', '8081', '--max-workers', '2') -WorkingDirectory $mobile -WindowStyle Hidden -RedirectStandardOutput (Join-Path $workspace 'metro.out.log') -RedirectStandardError (Join-Path $workspace 'metro.err.log') -PassThru
"Metro local iniciado em segundo plano: PID $($process.Id)."
