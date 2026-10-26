param([string]$OutputDirectory)
$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot
if(!$OutputDirectory){$OutputDirectory=Join-Path $repo ('private\Neos-Colony-AutoRestart-1.0.1-Serverpaket-'+(Get-Date -Format 'yyyyMMdd-HHmmss'))}
$output=[IO.Path]::GetFullPath($OutputDirectory)
$privateRoot=[IO.Path]::GetFullPath((Join-Path $repo 'private')).TrimEnd('\')+'\'
if(!$output.StartsWith($privateRoot,[StringComparison]::OrdinalIgnoreCase)){throw 'Private package must remain under ignored private/.'}
if(Test-Path -LiteralPath $output){throw 'Output already exists; will not overwrite.'}
$new='neos-colony-automodpack-autorestart-1.21.1-1.0.1.jar'
$jar=Join-Path $repo ('downloads\1.0.1\'+$new)
$pin=Join-Path $repo 'private\neoscolony-autorestart-trusted.properties'
if((Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant() -ne 'afe7944786348f93b57255f4512740afab5f504fd68aee9bffc57c8e15c9b2cd'){throw 'Published artifact changed.'}
[IO.Directory]::CreateDirectory((Join-Path $output 'payload'))|Out-Null
Copy-Item -LiteralPath $jar -Destination (Join-Path $output ('payload\'+$new))
Copy-Item -LiteralPath $pin -Destination (Join-Path $output 'payload\neoscolony-autorestart-trusted.properties')
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Install-ServerPackage.ps1') -Destination $output
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'SERVERPAKET.md') -Destination (Join-Path $output 'ANLEITUNG.md')
$manifest=[ordered]@{version='1.0.1';trustedSha256=(Get-FileHash -LiteralPath $pin -Algorithm SHA256).Hash.ToLowerInvariant();jarSha256=(Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant()}
[IO.File]::WriteAllText((Join-Path $output 'package.json'),($manifest|ConvertTo-Json),[Text.UTF8Encoding]::new($false))
Compress-Archive -LiteralPath (Join-Path $output 'payload'),(Join-Path $output 'Install-ServerPackage.ps1'),(Join-Path $output 'ANLEITUNG.md'),(Join-Path $output 'package.json') -DestinationPath ($output+'.zip')
$sha=(Get-FileHash -LiteralPath ($output+'.zip') -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText(($output+'.zip.sha256'),$sha+'  '+[IO.Path]::GetFileName($output+'.zip')+[Environment]::NewLine,[Text.UTF8Encoding]::new($false))
Write-Output ('PRIVATE_PACKAGE_DIRECTORY='+$output)
Write-Output ('PRIVATE_PACKAGE_ZIP='+$output+'.zip')
Write-Output ('PRIVATE_PACKAGE_SHA256='+$sha)
