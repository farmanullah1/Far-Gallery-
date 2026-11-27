# Phase 1: Full System & Bottleneck Audit (Read-Only)
$ErrorActionPreference = 'Stop'
$OutputEncoding = [Console]::OutputEncoding

$out = [System.Text.StringBuilder]::new()
function Add([string]$s) { $null = $out.Append($s) }
function AddLine() { $null = $out.AppendLine() }

AddLine '======================================'
AddLine '  PHASE 1: SYSTEM & BOTTLENECK AUDIT'
AddLine '======================================'
AddLine ''

# ---------- 1. HARDWARE SPECS ----------
AddLine '--- 1. HARDWARE SPECS ---'
AddLine ''

$cpu = Get-CimInstance Win32_Processor
Add 'CPU Model(s):'
foreach ($proc in $cpu) {
    Add "  $($proc.Name)"
    Add "    Logical Cores : $($proc.NumberOfLogicalProcessors)"
    Add "    Physical Cores: $($proc.NumberOfCores)"
    Add "    Max Clock     : $($proc.MaxClockSpeed) MHz ($([math]::Round($proc.MaxClockSpeed/1000,1)) GHz)"
    Add "    Current Clock : $($proc.CurrentClockSpeed) MHz ($([math]::Round($proc.CurrentClockSpeed/1000,1)) GHz)"
}
AddLine ''

$ramTotal = [math]::Round((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1GB, 2)
$ramAvail = [math]::Round((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory / 1MB, 2)
Add "Total RAM: $ramTotal GB"
Add "Available RAM (currently free): $ramAvail GB"
AddLine ''

Add 'RAM Modules (slots):'
$ramModules = Get-CimInstance Win32_PhysicalMemory
if ($ramModules) {
    $ramTable = $ramModules | Format-Table Capacity, Speed, Manufacturer, PartNumber, ConfiguredClockSpeed -AutoSize | Out-String
    Add $ramTable
    Add "Slots used: $($ramModules.Count)"
} else {
    Add '  (Unable to enumerate RAM modules)'
}
AddLine ''

Add 'Battery Health:'
$battery = Get-CimInstance Win32_Battery
if ($battery) {
    foreach ($bat in $battery) {
        $designCap = $bat.DesignCapacity
        $fullCap = $bat.FullChargeCapacity
        if ($designCap -and $designCap -gt 0) {
            $healthPct = [math]::Round($fullCap / $designCap * 100, 1)
        } else {
            $healthPct = 'N/A'
        }
        if ($bat.EstimatedRunTime -gt 0) {
            $runTime = "$($bat.EstimatedRunTime) minutes"
        } else {
            $runTime = 'Plugged in / N/A'
        }
        Add "  Name          : $($bat.Name)"
        Add "  Design Capacity   : $designCap mAh"
        Add "  Full Charge Capacity: $fullCap mAh"
        Add "  Health          : $healthPct%"
        Add "  Estimated Runtime: $runTime"
        Add "  Chemistry       : $($bat.Chemistry)"
        Add "  Status          : $($bat.Status)"
    }
} else {
    Add '  (No battery detected - desktop or AC-only system)'
}
AddLine ''

Add 'Storage Drives:'
AddLine ''
$disks = Get-PhysicalDisk | Select-Object FriendlyName, MediaType, Size, @{N='FreeGB';E={[math]::Round($_.SizeRemaining/1GB,2)}}, Status, OperationalStatus, BusType
foreach ($disk in $disks) {
    switch ($disk.MediaType) {
        'SSD' { $mediaLabel = 'SSD' }
        'HDD' { $mediaLabel = 'HDD' }
        'Unknown' { $mediaLabel = 'Unknown' }
        default { $mediaLabel = $disk.MediaType }
    }
    switch ($disk.BusType) {
        'NVMe' { $busLabel = 'NVMe' }
        'SATA' { $busLabel = 'SATA' }
        default { $busLabel = $disk.BusType }
    }
    Add "  Friendly Name : $($disk.FriendlyName)"
    Add "  Type          : $mediaLabel ($busLabel)"
    Add "  Size          : $([math]::Round($disk.Size/1GB,2)) GB"
    Add "  Free          : $($disk.FreeGB) GB"
    Add "  Status        : $($disk.Status) | Operational: $($disk.OperationalStatus)"
    AddLine ''
}
Add 'Volume usage:'
AddLine ''
$volumes = Get-Volume | Where-Object { $_.DriveType -eq 'Fixed' }
foreach ($vol in $volumes) {
    $usedGB = [math]::Round(($vol.Size - $vol.SizeRemaining) / 1GB, 2)
    $freeGB = [math]::Round($vol.SizeRemaining / 1GB, 2)
    $totalGB = [math]::Round($vol.Size / 1GB, 2)
    $freePct = [math]::Round($vol.SizeRemaining / $vol.Size * 100, 1)
    if ($vol.FileSystemLabel) {
        $label = " ($($vol.FileSystemLabel))"
    } else {
        $label = ''
    }
    Add "  $($vol.DriveLetter):$label  Total: $totalGB GB  Used: $usedGB GB  Free: $freeGB GB ($freePct pct free)"
}
AddLine ''

Add 'GPU:'
$gpu = Get-CimInstance Win32_VideoController
foreach ($g in $gpu) {
    Add "  Name          : $($g.Name)"
    if ($g.AdapterRAM) {
        Add "  VRAM          : $([math]::Round($g.AdapterRAM / 1GB, 2)) GB"
    } else {
        Add '  VRAM          : (not reported)'
    }
    Add "  Driver Version: $($g.DriverVersion)"
    Add "  Video Processor: $($g.VideoProcessor)"
    Add "  Resolution    : $($g.CurrentHorizontalResolution)x$($g.CurrentVerticalResolution)"
    AddLine ''
}
AddLine ''

# ---------- 2. OS & BOOT ENVIRONMENT ----------
AddLine '--- 2. OS & BOOT ENVIRONMENT ---'
AddLine ''

$os = Get-CimInstance Win32_OperatingSystem
Add "Windows Version : $($os.Caption)"
Add "Build Number    : $($os.BuildNumber)"
Add "OS Version      : $($os.Version)"
AddLine ''
$bootTime = $os.LastBootUpTime
$uptime = (Get-Date) - $bootTime
Add "Last Boot Time  : $($bootTime)"
Add "Uptime         : $($uptime.Days)d $($uptime.Hours)h $($uptime.Minutes)m $($uptime.Seconds)s"
AddLine ''

Add 'Boot Configuration (bcdedit):'
try {
    $bcdOutput = bcdedit /enum 2>&1
    Add $bcdOutput
} catch {
    Add '  (bcdedit not accessible - likely non-admin or missing boot manager)'
}
AddLine ''
Add '  NOTE: Presence of multiple bootmgr / hyper-v / boot loader entries indicates multi-boot. Check before modifying Fast Startup / Hibernation.'
AddLine ''

Add 'Power Plans (powercfg /list):'
try {
    Add (powercfg /list)
} catch {
    Add '  (powercfg not accessible)'
}
AddLine ''
Add 'Active Power Plan:'
try {
    Add (powercfg /getactivescheme)
} catch {
    Add '  (could not determine active plan)'
}
AddLine ''

# ---------- 3. HIDDEN & BOTTLENECK SETTINGS ----------
AddLine '--- 3. HIDDEN & BOTTLENECK SETTINGS ---'
AddLine ''

AddLine '--- 3a. Ultimate Performance Power Plan ---'
$ultimateGuid = 'e9a42b02-d5df-448d-aa00-03f14749eb61'
try {
    $ultimateInfo = powercfg /q $ultimateGuid '*' 2>&1
    if ($ultimateInfo -match 'Power Scheme GUID:') {
        Add "Status: UNLOCKED / EXISTS (GUID $ultimateGuid is visible)"
        Add 'Details:'
        Add $ultimateInfo
    } else {
        Add "Status: NOT VISIBLE / LOCKED (requires unlocking via powercfg -duplicatescheme $ultimateGuid)"
    }
} catch {
    Add 'Status: Unknown - exception querying the scheme'
}
AddLine ''

AddLine '--- 3b. Hardware-Accelerated GPU Scheduling (HAGS) ---'
$hagsPath = 'HKLM:\SYSTEM\CurrentControlSet\Control\GraphicsDrivers'
$hags = Get-ItemProperty -Path $hagsPath -Name 'HwSchMode' -ErrorAction SilentlyContinue
if ($hags) {
    $val = $hags.HwSchMode
    switch ($val) {
        0 { $meaning = 'Disabled' }
        1 { $meaning = 'Reserved' }
        2 { $meaning = 'Enabled' }
        default { $meaning = "Unknown value: $val" }
    }
    Add "Registry: HwSchMode = $val"
    Add "Meaning : $meaning (2 = enabled, 0 = disabled)"
} else {
    Add 'HwSchMode registry value not present (HAGS not configured)'
}
AddLine ''
Add 'HAGS UI availability check (Windows 10 1809+ / Win11):'
Add '  (HAGS can also be enabled via Settings -> System -> Display -> Graphics -> Change default graphics settings)'
AddLine ''

AddLine '--- 3c. Game Mode ---'
$gmPath = 'HKLM:\SOFTWARE\Microsoft\PolicyManager\default\software Policies\Microsoft\Windows\GameDVR'
if (Test-Path $gmPath) {
    $gm = Get-ItemProperty -Path $gmPath -Name 'AllowGameMode' -ErrorAction SilentlyContinue
    if ($gm) {
        $gval = $gm.AllowGameMode
        switch ($gval) {
            0 { $gmMeaning = 'Disabled' }
            2 { $gmMeaning = 'Enabled' }
            default { $gmMeaning = "Unknown ($gval)" }
        }
        Add "Registry: AllowGameMode = $gval"
        Add "Meaning : $gmMeaning (2 = enabled)"
    } else {
        Add 'AllowGameMode value not present (Game Mode not configured via registry)'
    }
} else {
    Add 'GameDVR policy path not present'
}
AddLine ''

AddLine '--- 3d. Visual Effects Setting ---'
$visPath = 'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Explorer'
$vis = Get-ItemProperty -Path $visPath -Name 'VisualFXSetting' -ErrorAction SilentlyContinue
if ($vis) {
    $vval = $vis.VisualFXSetting
    switch ($vval) {
        0 { $vMeaning = 'Best Appearance (let Windows choose appearance)' }
        1 { $vMeaning = 'Best Performance (adjust for best performance)' }
        2 { $vMeaning = 'Let Windows decide' }
        default { $vMeaning = "Unknown ($vval)" }
    }
    Add "Registry: VisualFXSetting = $vval"
    Add "Meaning : $vMeaning"
} else {
    Add 'VisualFXSetting not present (defaults to Let Windows decide in UI)'
}
AddLine ''

AddLine '--- 3e. Memory Management: Pagefile, SysMain, Memory Compression ---'
$cs = Get-CimInstance Win32_ComputerSystem
Add "Pagefile Auto-Management : $($cs.AutomaticManagedPagefile) (True = managed automatically)"
Add "Auto Reset on Boot      : $($cs.AutomaticResetBoot)"
Add "Auto Reset on Shutdown  : $($cs.AutomaticResetShutdown)"
AddLine ''
Add 'Pagefile configuration (from registry):'
try {
    $pfPath = 'HKLM:\SYSTEM\CurrentControlSet\Control\Session Manager\Memory Management'
    $pf = Get-ItemProperty -Path $pfPath -Name 'PagingFiles' -ErrorAction SilentlyContinue
    if ($pf) {
        Add "  PagingFiles = $($pf.PagingFiles -join ', ')"
    } else {
        Add '  No PagingFiles value found'
    }
    $clearPagefile = Get-ItemProperty -Path $pfPath -Name 'ClearPageFileAtShutdown' -ErrorAction SilentlyContinue
    if ($clearPagefile.ClearPageFileAtShutdown -ne $null) {
        $clearVal = $clearPagefile.ClearPageFileAtShutdown
    } else {
        $clearVal = 'not set'
    }
    Add "  ClearPageFileAtShutdown = $clearVal (1 = slow shutdown with pagefile wipe)"
} catch {
    Add '  (could not read pagefile registry)'
}
AddLine ''

Add 'SysMain (formerly SuperFetch / prefetch):'
try {
    $sysMain = Get-Service -Name 'SysMain'
    Add "  Name    : $($sysMain.Name)"
    Add "  Status  : $($sysMain.Status)"
    Add "  Start   : $($sysMain.StartType)"
} catch {
    Add '  (SysMain service not found)'
}
AddLine ''

Add 'Memory Compression (Get-MMAgent):'
try {
    $mm = Get-MMAgent
    if ($mm.PSSession -ne $null) { $pss = $mm.PSSession } else { $pss = 'n/a' }
    if ($mm.SMB1 -ne $null) { $smb = $mm.SMB1 } else { $smb = 'n/a' }
    if ($mm.Status -ne $null) { $mmStatus = $mm.Status } else { $mmStatus = 'n/a' }
    Add "  PSSession                 : $pss"
    Add "  SMB1                      : $smb"
    Add "  Status (compression etc) : $mmStatus"
    Add '  (True compression status is best inspected via Get-MMAgent -MemoryCompression if available)'
} catch {
    Add '  (Get-MMAgent failed - older Windows version?)'
}
AddLine ''

AddLine '--- 3f. Background Load: Startup Apps ---'
AddLine ''
Add 'High-impact startup entries:'
$startup = Get-CimInstance Win32_StartupCommand
if ($startup) {
    $sTable = $startup | Select-Object Name, Command, Location, User | Format-Table -AutoSize | Out-String
    Add $sTable
    Add "Count: $($startup.Count) startup entries"
} else {
    Add '  (No startup entries enumerated)'
}
AddLine ''

AddLine '--- 3g. Non-Microsoft Background Services (Automatic + Running) ---'
AddLine ''
$nonMS = Get-Service | Where-Object {
    $_.StartType -eq 'Automatic' -and $_.Status -eq 'Running' -and $_.DisplayName -notmatch '^Microsoft|^Windows'
}
if ($nonMS) {
    $sTable = $nonMS | Select-Object Name, DisplayName, Status, StartType | Format-Table -AutoSize | Out-String
    Add $sTable
    Add "Count: $($nonMS.Count) services"
} else {
    Add '  (No non-Microsoft auto-running services detected)'
}
AddLine ''
Add 'All Automatic-start services (non-Microsoft only, for context):'
$autoAll = Get-Service | Where-Object {
    $_.StartType -eq 'Automatic' -and $_.DisplayName -notmatch '^Microsoft|^Windows|^Connected User Experiences and Telemetry'
} | Select-Object Name, DisplayName, StartType | Sort-Object DisplayName | Format-Table -AutoSize | Out-String
Add $autoAll
AddLine ''

AddLine '--- 3h. Telemetry (DiagTrack / Connected User Experiences) ---'
AddLine ''
try {
    $diag = Get-Service -Name 'DiagTrack'
    Add "  Service    : $($diag.Name)"
    Add "  Status     : $($diag.Status)"
    Add "  Start Type : $($diag.StartType)"
} catch {
    Add '  (DiagTrack service not found)'
}
AddLine ''
Add 'Note: On newer Windows, telemetry is controlled via Settings -> Privacy -> Diagnostics and feedback'
AddLine ''

AddLine '--- 3i. Delivery Optimization (DoSvc P2P Updates) ---'
AddLine ''
try {
    $doSvc = Get-Service -Name 'DoSvc'
    Add "  Service    : $($doSvc.Name)"
    Add "  Status     : $($doSvc.Status)"
    Add "  Start Type : $($doSvc.StartType)"
} catch {
    Add '  (DoSvc service not found)'
}
AddLine ''
try {
    $doRegPath = 'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\DeliveryOptimization'
    if (Test-Path $doRegPath) {
        $doReg = Get-ItemProperty -Path $doRegPath
        if ($doReg.DownloadMode) { $dlMode = $doReg.DownloadMode } else { $dlMode = 'n/a' }
        switch ($dlMode) {
            0 { $dlModeMeaning = "OptOut (do not download from peers)" }
            1 { $dlModeMeaning = 'OptIn' }
            2 { $dlModeMeaning = 'All' }
            3 { $dlModeMeaning = 'FQD (certs only)' }
            default { $dlModeMeaning = "Unknown ($dlMode)" }
        }
        Add "  DownloadMode                : $dlMode ($dlModeMeaning)"
        if ($doReg.DODownloadFromPeers) { $peers = $doReg.DODownloadFromPeers } else { $peers = 'not set' }
        Add "  DODownloadFromPeers         : $peers"
        if ($doReg.DODownloadOverMeteredNetworkLimited) { $metaLim = $doReg.DODownloadOverMeteredNetworkLimited } else { $metaLim = 'not set' }
        Add "  DODownloadOverMeteredNetworkLimited : $metaLim"
        if ($doReg.DODownloadOverMeteredNetworkOption) { $metaOpt = $doReg.DODownloadOverMeteredNetworkOption } else { $metaOpt = 'not set' }
        Add "  DODownloadOverMeteredNetworkOption  : $metaOpt"
    } else {
        Add '  (DeliveryOptimization registry key not present)'
    }
} catch {
    Add '  (Could not read DeliveryOptimization settings)'
}
AddLine ''

AddLine '--- 3j. Background UWP / Store Apps ---'
AddLine ''
try {
    $uwp = Get-AppxPackage | Where-Object {
        $_.IsFramework -eq $false -and $_.Name -notmatch '^Microsoft\.(Windows|VisualElements|ApplicationInsights)'
    }
    if ($uwp) {
        $uwpTable = $uwp | Select-Object Name, Publisher, Status, PackageFullName | Format-Table -AutoSize | Out-String
        Add $uwpTable
        Add "Count: $($uwp.Count) user Appx packages"
    } else {
        Add '  (No non-framework Appx packages found)'
    }
} catch {
    Add '  (Could not enumerate Appx packages)'
}
AddLine ''

AddLine '--- 3k. Storage: TRIM and Cache Bloat ---'
AddLine ''
$trimOut = fsutil behavior query DisableDeleteNotify 2>&1
if ($LASTEXITCODE -eq 0) {
    $trimVal = ($trimOut -split ' ')[-1]
    switch ($trimVal) {
        '0' { $trimMeaning = 'TRIM ENABLED (good)' }
        '1' { $trimMeaning = 'TRIM DISABLED (bad for SSD)' }
        default { $trimMeaning = "Unexpected: $trimVal" }
    }
    Add "DisableDeleteNotify = $trimVal"
    Add "Meaning             : $trimMeaning"
} else {
    Add "DisableDeleteNotify : unable to query ($trimOut)"
}
AddLine ''
try {
    $tempSize = (Get-ChildItem $env:TEMP -Force -ErrorAction SilentlyContinue | Measure-Object -Property Length -Sum -ErrorAction SilentlyContinue).Sum
    $tempMB = [math]::Round($tempSize / 1MB, 2)
    Add "Temp folder ($env:TEMP) size: $tempMB MB"
} catch {
    Add '  (Could not measure Temp folder size)'
}
try {
    $localAppData = $env:LOCALAPPDATA
    $lacSize = (Get-ChildItem $localAppData -Force -ErrorAction SilentlyContinue | Measure-Object -Property Length -Sum -ErrorAction SilentlyContinue).Sum
    $lacMB = [math]::Round($lacSize / 1MB, 2)
    Add "LocalAppData ($localAppData) size: $lacMB MB"
} catch {
    Add '  (Could not measure LocalAppData size)'
}
try {
    $winINetCache = Join-Path $env:LOCALAPPDATA 'Microsoft\Windows\INetCache'
    if (Test-Path $winINetCache) {
        $cacheSize = (Get-ChildItem $winINetCache -Force -ErrorAction SilentlyContinue -Recurse | Measure-Object -Property Length -Sum -ErrorAction SilentlyContinue).Sum
        $cacheMB = [math]::Round($cacheSize / 1MB, 2)
        Add "INetCache size: $cacheMB MB"
    }
} catch {}
AddLine ''

AddLine '--- 3l. Wi-Fi Adapter Power-Saving ---'
AddLine ''
try {
    $wifiAdapters = Get-NetAdapter | Where-Object {
        $_.InterfaceDescription -match 'wireless|wi-fi|wlan|802.11' -or $_.PhysicalMediaType -match 'native'
    }
    if ($wifiAdapters) {
        foreach ($w in $wifiAdapters) {
            $pm = Get-NetAdapterPowerManagement -Name $w.Name -ErrorAction SilentlyContinue
            if ($pm) {
                Add "  Adapter       : $($w.Name) ($($w.InterfaceDescription))"
                Add "  WakeOnMagicPacket : $($pm.WakeOnMagicPacket)"
                Add "  WakeOnPattern     : $($pm.WakeOnPattern)"
                Add "  SavePowerLevel    : $($pm.SavePowerLevel)"
                AddLine ''
            } else {
                Add "  Adapter       : $($w.Name) ($($w.InterfaceDescription)) - power-management data unavailable"
            }
        }
    } else {
        Add '  (No Wi-Fi adapters detected via Get-NetAdapter)'
    }
} catch {
    Add '  (Could not query network adapter power management)'
}
AddLine '======================================'
AddLine '  PHASE 1 AUDIT COMPLETE'
AddLine '======================================'

Write-Output $out.ToString()
