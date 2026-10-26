param([Parameter(Mandatory=$true)][string]$PackageDirectory,[Parameter(Mandatory=$true)][string]$AutoModpackJar)
$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot
$work=Join-Path $repo ('temp\server-package-tests-'+[Guid]::NewGuid().ToString('N'))
$server=Join-Path $work 'server'
[IO.Directory]::CreateDirectory((Join-Path $server 'automodpack\host-modpack\main\mods'))|Out-Null
[IO.Directory]::CreateDirectory((Join-Path $server 'mods'))|Out-Null
$installer=Join-Path $PackageDirectory 'Install-ServerPackage.ps1'
$old='neos-colony-automodpack-autorestart-1.21.1-1.0.0.jar'
$new='neos-colony-automodpack-autorestart-1.21.1-1.0.1.jar'
$trust='neoscolony-autorestart-trusted.properties'
$hostRoot=Join-Path $server 'automodpack\host-modpack\main'
Copy-Item -LiteralPath (Join-Path $repo ('dist\'+$old)) -Destination (Join-Path $hostRoot ('mods\'+$old))
Copy-Item -LiteralPath (Join-Path $repo ('dist\'+$old)) -Destination (Join-Path $server ('mods\'+$old))
Copy-Item -LiteralPath $AutoModpackJar -Destination (Join-Path $server 'mods\automodpack-4.0.6.jar')
$cfg=Join-Path $server 'automodpack\automodpack-server.json'
$settings=[ordered]@{DO_NOT_CHANGE_IT=2;modpackHost=$true;generateModpackOnStart=$true;syncedFiles=@('/mods/*.jar');allowEditsInFiles=@('/config/**');forceCopyFilesToStandardLocation=@('/options.txt');nonModpackFilesToDelete=[ordered]@{'/old-sentinel.txt'=('b'*40)};unrelated=[ordered]@{keep=@(1,'unchanged',$true)}}
[IO.File]::WriteAllText($cfg,($settings|ConvertTo-Json -Depth 20))
$sentinel=Join-Path $server 'unchanged.txt';[IO.File]::WriteAllText($sentinel,'KEEP')
function Snapshot{
 $items=@(Get-ChildItem -LiteralPath $server -File -Recurse | Sort-Object FullName | ForEach-Object {$_.FullName+'|'+(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash+'|'+$_.LastWriteTimeUtc.Ticks})
 return $items -join [Environment]::NewLine
}
$n=0
function Check([bool]$ok,[string]$name){if(!$ok){throw $name};$script:n++;Write-Output ('PASS_INSTALLER '+$name)}
$before=Snapshot
& $installer -ServerRoot $server | Out-Null
Check ((Snapshot) -eq $before) 'inspection changes no server files'
$refused=$false;try{& $installer -ServerRoot $server -Apply | Out-Null}catch{$refused=$true}
Check ($refused -and (Snapshot) -eq $before) 'apply without stopped-server confirmation refused before writes'
& $installer -ServerRoot $server -Apply -ServerStopped | Out-Null
Check (!(Test-Path -LiteralPath (Join-Path $hostRoot ('mods\'+$old))) -and !(Test-Path -LiteralPath (Join-Path $server ('mods\'+$old)))) 'old versions removed from both distribution sources'
Check ((Get-FileHash -LiteralPath (Join-Path $hostRoot ('mods\'+$new)) -Algorithm SHA256).Hash.ToLowerInvariant() -eq 'afe7944786348f93b57255f4512740afab5f504fd68aee9bffc57c8e15c9b2cd') 'new JAR matches published bytes'
Check ((Get-FileHash -LiteralPath (Join-Path $hostRoot ('config\'+$trust)) -Algorithm SHA256).Hash -eq (Get-FileHash -LiteralPath (Join-Path $PackageDirectory ('payload\'+$trust)) -Algorithm SHA256).Hash) 'private payload unchanged'
$actual=Get-Content -LiteralPath $cfg -Raw|ConvertFrom-Json
Check ($actual.forceCopyFilesToStandardLocation -contains ('/config/'+$trust) -and $actual.forceCopyFilesToStandardLocation -contains '/options.txt') 'exact copy rule added and original rules retained'
Check ($actual.nonModpackFilesToDelete.('/mods/'+$old) -eq 'd0f8f39848b77e6ea6034b6a85f8400b3267140b' -and $actual.nonModpackFilesToDelete.'/old-sentinel.txt' -eq ('b'*40)) 'exact old hash only and previous deletion entries retained'
Check ($actual.unrelated.keep[1] -eq 'unchanged' -and [IO.File]::ReadAllText($sentinel) -eq 'KEEP') 'unrelated settings and files unchanged'
$after=Snapshot
& $installer -ServerRoot $server -Apply -ServerStopped | Out-Null
Check ((Snapshot) -eq $after) 'second installation is idempotent including file timestamps'
$refused=$false;try{& $installer -ServerRoot $server -Verify | Out-Null}catch{$refused=$true}
Check $refused 'missing regenerated manifest is not reported as completed'
$index=Join-Path $server 'automodpack\host-modpack\automodpack-content.json'
$rows=@([ordered]@{file=('/mods/'+$new);size=(Get-Item (Join-Path $hostRoot ('mods\'+$new))).Length.ToString();sha1=(Get-FileHash (Join-Path $hostRoot ('mods\'+$new)) -Algorithm SHA1).Hash.ToLowerInvariant()},[ordered]@{file=('/config/'+$trust);forceCopy=$true;sha1=(Get-FileHash (Join-Path $hostRoot ('config\'+$trust)) -Algorithm SHA1).Hash.ToLowerInvariant()})
[IO.File]::WriteAllText($index,(@{list=$rows;nonModpackFilesToDelete=@(@{file=('/mods/'+$old);sha1='d0f8f39848b77e6ea6034b6a85f8400b3267140b';timestamp='fixture'})}|ConvertTo-Json -Depth 20))
& $installer -ServerRoot $server -Verify | Out-Null
Check $true 'verification accepts correct fixture manifest'
$rows+=,@{file=('/mods/'+$old)}
[IO.File]::WriteAllText($index,(@{list=$rows;nonModpackFilesToDelete=@(@{file=('/mods/'+$old);sha1='d0f8f39848b77e6ea6034b6a85f8400b3267140b';timestamp='fixture'})}|ConvertTo-Json -Depth 20))
$refused=$false;try{& $installer -ServerRoot $server -Verify | Out-Null}catch{$refused=$true}
Check $refused 'stale old entry rejected'
$renamed=Join-Path $server 'mods\renamed-autorestart.jar'
Copy-Item -LiteralPath (Join-Path $repo ('dist\'+$old)) -Destination $renamed
$before=Snapshot
$refused=$false;try{& $installer -ServerRoot $server -Apply -ServerStopped | Out-Null}catch{$refused=$true}
Check ($refused -and (Snapshot) -eq $before) 'renamed old mod ID rejected without mutation'
Write-Output ('INSTALLER_TESTS_PASSED='+$n)
