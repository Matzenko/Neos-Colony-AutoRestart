param(
 [Parameter(Mandatory=$true)][string]$ServerRoot,
 [switch]$Apply,
 [switch]$ServerStopped,
 [switch]$Verify
)
$ErrorActionPreference='Stop'
$utf8=[Text.UTF8Encoding]::new($false)
$oldName='neos-colony-automodpack-autorestart-1.21.1-1.0.0.jar'
$newName='neos-colony-automodpack-autorestart-1.21.1-1.0.1.jar'
$trustName='neoscolony-autorestart-trusted.properties'
$newHash='afe7944786348f93b57255f4512740afab5f504fd68aee9bffc57c8e15c9b2cd'
$oldHash='f19483e231d46d2769a043492c9b192aa27a0d454f5fb09f65683a86fb539ca1'
$oldSha1='d0f8f39848b77e6ea6034b6a85f8400b3267140b'
$clientPath='/config/'+$trustName
function Hash([string]$p){(Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash.ToLowerInvariant()}
function Assert([bool]$ok,[string]$message){if(!$ok){throw $message}}
function Under([string]$p,[string]$base){$p.StartsWith($base.TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase)}
Assert (Test-Path -LiteralPath $ServerRoot -PathType Container) 'Server folder does not exist. Nothing changed.'
$root=(Get-Item -LiteralPath $ServerRoot).FullName.TrimEnd('\')
$package=(Get-Item -LiteralPath $PSScriptRoot).FullName.TrimEnd('\')
Assert (!(Under $package $root) -and $package -ne $root) 'Extract package OUTSIDE the server folder (backups must not be distributed).'
# Reject junctions/symlinks along every involved path before any mutation.
function Safe([string]$p){
 $absolute=[IO.Path]::GetFullPath($p)
 Assert (Under $absolute $root) 'Target outside server root.'
 $at=$absolute
 while($at -and $at.Length -ge $root.Length){
  if(Test-Path -LiteralPath $at){Assert (((Get-Item -LiteralPath $at -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) 'Reparse points need administrator review.'}
  $at=Split-Path $at
 }
 return $absolute
}
$cfg=Safe (Join-Path $root 'automodpack\automodpack-server.json')
Assert (Test-Path -LiteralPath $cfg -PathType Leaf) 'Existing AutoModpack configuration missing.'
$originalConfig=[IO.File]::ReadAllText($cfg)
$config=$originalConfig | ConvertFrom-Json
Assert ($config.modpackHost -eq $true -and $config.generateModpackOnStart -eq $true) 'Expected existing modpackHost=true and generateModpackOnStart=true. No automatic broad configuration changes.'
$payload=Join-Path $package 'payload'
$manifest=Get-Content -LiteralPath (Join-Path $package 'package.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$jarSource=Join-Path $payload $newName
$trustSource=Join-Path $payload $trustName
Assert ((Hash $jarSource) -eq $newHash) 'Published JAR integrity mismatch.'
Assert ((Hash $trustSource) -eq $manifest.trustedSha256) 'Private configuration integrity mismatch.'
$pin=@{}
foreach($line in [IO.File]::ReadAllLines($trustSource)){
 if($line.Trim() -eq '' -or $line.TrimStart().StartsWith('#')){continue}
 $kv=@($line -split '=',2);Assert ($kv.Count -eq 2) 'Invalid private configuration.'
 Assert (!$pin.ContainsKey($kv[0].Trim())) 'Duplicate private setting.'
 $pin[$kv[0].Trim()]=$kv[1].Trim()
}
Assert ($pin.Count -eq 5) 'Exactly five private settings required.'
foreach($key in @('serverHost','serverPort','modpackHost','modpackPort','fingerprint')){Assert ($pin.ContainsKey($key) -and ![string]::IsNullOrWhiteSpace($pin[$key])) 'Incomplete private configuration.'}
Assert ($pin.fingerprint -cmatch '^[0-9a-f]{64}$') 'Invalid certificate pin.'
foreach($key in @('serverPort','modpackPort')){Assert ($pin[$key] -match '^\d+$' -and [int]$pin[$key] -ge 1 -and [int]$pin[$key] -le 65535) 'Invalid port.'}
$hostRoot=Safe (Join-Path $root 'automodpack\host-modpack\main')
$jarTarget=Safe (Join-Path $hostRoot ('mods\'+$newName))
$trustTarget=Safe (Join-Path $hostRoot ('config\'+$trustName))
$searchRoots=@((Join-Path $root 'mods'),$hostRoot)
$oldFiles=@()
$autoVersionFound=$false
Add-Type -AssemblyName System.IO.Compression.FileSystem
foreach($search in $searchRoots){
 if(!(Test-Path -LiteralPath $search)){continue}
 $queue=[Collections.Generic.Queue[string]]::new();$queue.Enqueue((Safe $search))
 while($queue.Count){
  $dir=$queue.Dequeue()
  foreach($item in Get-ChildItem -LiteralPath $dir -Force){
   $checked=Safe $item.FullName
   if($item.PSIsContainer){if($search -eq $hostRoot){$queue.Enqueue($checked)};continue}
   if($item.Extension -ne '.jar'){continue}
   $isOwn=$item.Name -in @($oldName,$newName)
   $zip=$null
   try{
    $zip=[IO.Compression.ZipFile]::OpenRead($checked);$entry=$zip.GetEntry('META-INF/neoforge.mods.toml')
    if($entry){$reader=[IO.StreamReader]::new($entry.Open());try{$toml=$reader.ReadToEnd()}finally{$reader.Dispose()};if($search -eq (Join-Path $root 'mods') -and $toml -match 'modId\s*=\s*"automodpack"'){Assert ($toml -match '(?m)^\s*version\s*=\s*"4\.0\.6"') 'Expected existing AutoModpack 4.0.6.';$autoVersionFound=$true};if($toml -match 'modId\s*=\s*"neoscolony_autorestart"'){$isOwn=$true}}
   }finally{if($zip){$zip.Dispose()}}
   if(!$isOwn){continue}
   if($item.Name -eq $oldName){Assert ((Hash $checked) -eq $oldHash) 'Unexpected old AutoRestart contents; refusing replacement.';$oldFiles+=,$checked}
   else{Assert ($item.Name -eq $newName -and $checked -eq $jarTarget -and (Hash $checked) -eq $newHash) 'Unexpected/renamed/duplicate AutoRestart JAR; review before installing.'}
  }
 }
}
Assert $autoVersionFound 'Existing AutoModpack 4.0.6 server JAR not found.'
$force=@($config.forceCopyFilesToStandardLocation | Where-Object {$_ -ne $null})
$changedConfig=$false
if($force -notcontains $clientPath){$force+=,$clientPath;$config | Add-Member -Force NoteProperty forceCopyFilesToStandardLocation $force;$changedConfig=$true}
$deletions=$config.nonModpackFilesToDelete
if($null -eq $deletions){$deletions=[pscustomobject]@{};$config | Add-Member -Force NoteProperty nonModpackFilesToDelete $deletions}
$oldClient='/mods/'+$oldName
$previous=$deletions.PSObject.Properties[$oldClient]
if($null -ne $previous){Assert ($previous.Value -eq $oldSha1) 'Conflicting old-JAR deletion rule; review required.'}
else{$deletions | Add-Member NoteProperty $oldClient $oldSha1;$changedConfig=$true}
# Prove that no settings outside the two specifically authorized distribution keys changed.
$before=$originalConfig | ConvertFrom-Json
$after=($config | ConvertTo-Json -Depth 100) | ConvertFrom-Json
foreach($key in @('forceCopyFilesToStandardLocation','nonModpackFilesToDelete')){$before.PSObject.Properties.Remove($key);$after.PSObject.Properties.Remove($key)}
Assert (($before | ConvertTo-Json -Depth 100 -Compress) -eq ($after | ConvertTo-Json -Depth 100 -Compress)) 'Unrelated configuration changed.'
if($Verify){
 Assert ($oldFiles.Count -eq 0 -and !$changedConfig) 'Old JAR or required distribution rules still missing.'
 Assert ((Test-Path -LiteralPath $jarTarget) -and (Hash $jarTarget) -eq $newHash) 'Published server payload missing.'
 Assert ((Test-Path -LiteralPath $trustTarget) -and (Hash $trustTarget) -eq $manifest.trustedSha256) 'Private host payload missing.'
 $index=Safe (Join-Path $root 'automodpack\host-modpack\automodpack-content.json')
 $content=Get-Content -LiteralPath $index -Raw -Encoding UTF8 | ConvertFrom-Json
 $jars=@($content.list | Where-Object {$_.file -like '*neos-colony-automodpack-autorestart*.jar'})
 Assert ($jars.Count -eq 1 -and $jars[0].file -eq ('/mods/'+$newName)) 'Manifest contains old/duplicate/missing AutoRestart.'
 Assert ($jars[0].sha1 -eq (Get-FileHash -LiteralPath $jarSource -Algorithm SHA1).Hash.ToLowerInvariant() -and [long]$jars[0].size -eq (Get-Item $jarSource).Length) 'Wrong JAR hash/size in manifest.'
 $rows=@($content.list | Where-Object {$_.file -eq $clientPath})
 Assert ($rows.Count -eq 1 -and $rows[0].forceCopy -eq $true) 'Configuration not forced to standard client/config location.'
 Assert ($rows[0].sha1 -eq (Get-FileHash -LiteralPath $trustSource -Algorithm SHA1).Hash.ToLowerInvariant()) 'Wrong configuration hash in manifest.'
 $removal=@($content.nonModpackFilesToDelete | Where-Object {$_.file -eq $oldClient -and $_.sha1 -eq $oldSha1})
 Assert ($removal.Count -eq 1) 'Generated old-JAR migration rule missing. Regenerate through AutoModpack; do not edit manifest manually.'
 Write-Output 'VERIFIED: only 1.0.1 and exact private configuration in generated distribution. Real player join remains to be checked.'
 return
}
Write-Output ('Old AutoRestart copies to back up: '+$oldFiles.Count)
Write-Output ('JAR target: '+$jarTarget)
Write-Output ('Private host payload target: '+$trustTarget)
Write-Output ('Distribution configuration changes needed: '+$changedConfig)
if(!$Apply){Write-Output 'CHECK_ONLY: no files changed. Apply only with stopped server.';return}
Assert $ServerStopped 'Confirm server stopped with -ServerStopped. Script never stops/restarts servers.'
$lock=Join-Path $root 'world\session.lock'
if(Test-Path -LiteralPath $lock){try{$handle=[IO.File]::Open($lock,[IO.FileMode]::Open,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None);$handle.Dispose()}catch{throw 'World lock cannot be opened exclusively; server may still run.'}}
$backup=Join-Path $package ('backups\'+[Guid]::NewGuid().ToString('N'))
[IO.Directory]::CreateDirectory($backup)|Out-Null
$targets=@($cfg,$jarTarget,$trustTarget)+$oldFiles
$targets=@($targets | Select-Object -Unique)
$records=@();$n=0
foreach($target in $targets){
 $record=[ordered]@{target=$target;existed=(Test-Path -LiteralPath $target -PathType Leaf);backup=('file-'+$n)}
 if($record.existed){Copy-Item -LiteralPath $target -Destination (Join-Path $backup $record.backup)}
 $records+=[pscustomobject]$record;$n++
}
[IO.File]::WriteAllText((Join-Path $backup 'restore-map.json'),($records | ConvertTo-Json -Depth 10),$utf8)
try{
 foreach($old in $oldFiles){Move-Item -LiteralPath $old -Destination (Join-Path $backup ('removed-'+[Guid]::NewGuid().ToString('N')+'.jar'))}
 foreach($target in @($jarTarget,$trustTarget)){[IO.Directory]::CreateDirectory((Split-Path $target))|Out-Null}
 if(!(Test-Path -LiteralPath $jarTarget) -or (Hash $jarTarget) -ne $newHash){[IO.File]::WriteAllBytes($jarTarget,[IO.File]::ReadAllBytes($jarSource))}
 if(!(Test-Path -LiteralPath $trustTarget) -or (Hash $trustTarget) -ne $manifest.trustedSha256){[IO.File]::WriteAllBytes($trustTarget,[IO.File]::ReadAllBytes($trustSource))}
 if($changedConfig){[IO.File]::WriteAllText($cfg,($config | ConvertTo-Json -Depth 100),$utf8)}
 Assert ((Hash $jarTarget) -eq $newHash -and (Hash $trustTarget) -eq $manifest.trustedSha256) 'Installed payload hash mismatch.'
}catch{
 foreach($record in $records){
  if($record.existed){Copy-Item -LiteralPath (Join-Path $backup $record.backup) -Destination $record.target -Force}
  elseif(Test-Path -LiteralPath $record.target -PathType Leaf){Move-Item -LiteralPath $record.target -Destination (Join-Path $backup ('failed-'+[Guid]::NewGuid().ToString('N')))}
 }
 throw
}
Write-Output ('INSTALLED_FILES_ONLY. Backup: '+$backup)
Write-Output 'No generated manifest or player trust entries were edited. Start server normally, then rerun with -Verify.'
