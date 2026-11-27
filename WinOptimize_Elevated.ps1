param([string]$Mode='Full')
$ErrorActionPreference='Stop'
$global:Log="$env:USERPROFILE\Desktop\WinOptimize_Backup\phase34_log.txt"
function L($m){$ts=Get-Date -Format 'yyyy-MM-dd HH:mm:ss.fff';$ln="[$ts] $m";Add-Content -Path $global:Log -Value $ln -Encoding UTF8;Write-Output $ln}
function Run($sb,$desc){L "START: $desc";try{$r=& $sb 2>&1;if($r){foreach($x in $r){L "  -> $x"}};L "OK: $desc";return $true}catch{L "FAIL: $desc | $($_.Exception.Message)";return $false}}
function Save($n,$d){Add-Content -Path "$env:USERPROFILE\Desktop\WinOptimize_Backup\$n" -Value $d -Encoding UTF8}
if(!(Test-Path "$env:USERPROFILE\Desktop\WinOptimize_Backup")){New-Item -ItemType Directory -Path "$env:USERPROFILE\Desktop\WinOptimize_Backup" -Force|Out-Null}
L "=== WINOPTIMIZE PHASE 3+4 START (Mode=$Mode) ==="

L "RE-AUDIT (BEFORE STATE)"
$b=@()
foreach($p in Get-CimInstance Win32_Processor){$b+="CPU: $($p.Name) | Logical:$($p.NumberOfLogicalProcessors) Physical:$($p.NumberOfCores) Max:$($p.MaxClockSpeed)MHz $([math]::Round($p.MaxClockSpeed/1000,1))GHz Cur:$($p.CurrentClockSpeed)MHz"}
$ramT=[math]::Round((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory/1GB,2)
$ramF=[math]::Round((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory/1MB,2)
$b+="RAM Total:$ramT GB Free:$ramF GB"
$b+=(Get-CimInstance Win32_PhysicalMemory|Format-Table Capacity,Speed,Manufacturer,PartNumber -AutoSize|Out-String)
foreach($g in Get-CimInstance Win32_VideoController){$b+="GPU:$($g.Name) VRAM:$([math]::Round($g.AdapterRAM/1GB,2))GB Driver:$($g.DriverVersion)"}
$os=Get-CimInstance Win32_OperatingSystem
$up=(Get-Date)-$os.LastBootUpTime
$b+="OS:$($os.Caption) Build $($os.BuildNumber) Uptime:$($up.Days)d $($up.Hours)h $($up.Minutes)m"
$b+="Active Plan: (powercfg /getactivescheme)"
$b+="Processes:$((Get-Process).Count) RAM in use:$([math]::Round((Get-Process|Measure-Object WorkingSet64 -Sum).Sum/1GB,3))GB Free(OS):$ramF GB"
$trim=fsutil behavior query DisableDeleteNotify 2>&1
$b+="TRIM(DisableDeleteNotify): $trim"
$wa=Get-NetAdapter|Where-Object{$_.InterfaceDescription -match 'wireless|wi-fi|wlan|802.11'}
$b+="Wi-Fi adapters:$($wa.Count)"
foreach($w in $wa){$b+="  $($w.Name) ($($w.InterfaceDescription)) Status:$($w.Status) Link:$($w.LinkSpeed)";try{$pm=Get-NetAdapterPowerManagement -Name $w.Name;!$pm| Out-Null;$b+="    PM:WakeMagic=$($pm.WakeOnMagicPacket) WakePat=$($pm.WakeOnPattern) SaveLvl=$($pm.SavePowerLevel)}catch{$b+="    PM:unavailable"}}
Save "BEFORE_STATE.txt" ($b -join "`n")
L "BEFORE snapshot saved."
L "TARGETED BEFORE-READS"
$rb=@();$rb+="Path,Name,Type,Value"
$regs=@(('HKLM:\SYSTEM\CurrentControlSet\Control\GraphicsDrivers','HwSchMode'),('HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Explorer','VisualFXSetting'),('HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion\Windows','FontSmoothing'),('HKLM:\SYSTEM\CurrentControlSet\Control\Session Manager\Memory Management','PagingFiles'),('HKLM:\SYSTEM\CurrentControlSet\Control\Session Manager\Memory Management','ClearPageFileAtShutdown'))
foreach($r in $regs){try{$ip=Get-ItemProperty -Path $r[0] -ErrorAction Stop;$v=$ip.$r[1];$t=if($v -is [int]){'DWord'}else{'String'};$rb+="$($r[0]),$($r[1]),$t,$v}catch{$rb+="$($r[0]),$($r[1]),(unavailable),$( $_.Exception.Message )"}}
Save "RegBackup_before.csv" ($rb -join "`n")
$sv=Get-Service|Where-Object{$_.StartType -eq 'Automatic'-and $_.DisplayName -notmatch '^Microsoft|^Windows'}|Select-Object Name,DisplayName,Status,StartType|Sort-Object Name
Save "Services_AutoNonMS_before.txt" (($sv|Format-Table -AutoSize|Out-String) -join "`n")
$do='HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\DeliveryOptimization';$db=@()
if(Test-Path $do){$d=Get-ItemProperty -Path $do;foreach($k in $d.PSObject.Properties){if($k.Name -notin 'PSPath','PSParentPath','PSChildName','PSDrive','PSProvider'){$db+="  $($k.Name) = $($k.Value)"}}}else{$db+="DeliveryOptimization key not present"}
Save "DeliveryOptimization_before.txt" ($db -join "`n")
L "Targeted before-reads saved."
L "=== PHASE 3: SAFETY BACKUP ==="
# System Restore
Run {Enable-ComputerRestore -Drive $env:SystemDrive -ErrorAction Stop; L "SR enable sent"} "Enable-ComputerRestore"
Run {Checkpoint-Computer -Description 'Pre-Antigravity-Optimization' -RestorePointType MODIFY_SETTINGS; L "Restore point OK"} "Create SR point Pre-Antigravity-Optimization"
# Power scheme export
$psex="$env:USERPROFILE\Desktop\WinOptimize_Backup\power-schemes.xml"
Run {powercfg /export $psex; L "Exported $( (Get-Item $psex).Length ) bytes"} "powercfg /export"
Save "Services_before.txt" ((Get-Service|Select-Object Name,DisplayName,Status,StartType|Sort-Object Name|Format-Table -AutoSize|Out-String) -join "`n")
L "Phase 3 backup complete."

