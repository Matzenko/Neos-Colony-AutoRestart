param(
 [Parameter(Mandatory=$true)][string]$LibrariesDirectory,
 [Parameter(Mandatory=$true)][string]$VersionsDirectory,
 [Parameter(Mandatory=$true)][string]$AutoModpackJar,
 [string]$GuiJar,
 [string]$JavaHome=$env:JAVA_HOME
)
$ErrorActionPreference='Stop'
Set-Location -LiteralPath $PSScriptRoot
if (!$JavaHome) { $JavaHome=Split-Path (Split-Path (Get-Command javac.exe -ErrorAction Stop).Source) }
$javac=Join-Path $JavaHome 'bin\javac.exe'
$java=Join-Path $JavaHome 'bin\java.exe'
$jar=Join-Path $JavaHome 'bin\jar.exe'
foreach($p in @($javac,$java,$jar,$AutoModpackJar)) {if (!(Test-Path -LiteralPath $p -PathType Leaf)) {throw "Missing input: $p"}}
$externalHashes=@{}
foreach($inputPath in @($AutoModpackJar,$GuiJar)){
 if($inputPath){
  $externalHashes[$inputPath]=(Get-FileHash -LiteralPath $inputPath -Algorithm SHA256).Hash
 }
}
function Write-Local([string]$path,[string]$text){
 [IO.File]::WriteAllText((Join-Path $PSScriptRoot $path),$text,[Text.UTF8Encoding]::new($false))
}
foreach($dir in @('out','dist','temp','test-results','reference')){[IO.Directory]::CreateDirectory((Join-Path $PSScriptRoot $dir))|Out-Null}
$run=Get-Date -Format 'yyyyMMdd-HHmmss-fff'
$classes=Join-Path $PSScriptRoot "out\$run\main"
$tests=Join-Path $PSScriptRoot "out\$run\test"
[IO.Directory]::CreateDirectory($classes)|Out-Null
[IO.Directory]::CreateDirectory($tests)|Out-Null
$neoJson=Get-Content -LiteralPath (Join-Path $VersionsDirectory 'neoforge-21.1.251\neoforge-21.1.251.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$mcJson=Get-Content -LiteralPath (Join-Path $VersionsDirectory '1.21.1\1.21.1.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$libs=[Collections.Generic.List[string]]::new()
foreach($relative in @('net\neoforged\neoforge\21.1.251\neoforge-21.1.251-client.jar','net\neoforged\neoforge\21.1.251\neoforge-21.1.251-universal.jar','net\minecraft\client\1.21.1-20240808.144430\client-1.21.1-20240808.144430-srg.jar')){
 $libs.Add((Join-Path $LibrariesDirectory $relative))
}
foreach($entry in @($neoJson.libraries)+@($mcJson.libraries)){
 if($entry.rules){
  $allowed=$false
  foreach($rule in $entry.rules){
   $matches=$true
   if($rule.os.name -and $rule.os.name -ne 'windows'){$matches=$false}
   if($rule.os.arch -and $rule.os.arch -notin @('amd64','x86_64')){$matches=$false}
   if($rule.features){$matches=$false}
   if($matches){$allowed=$rule.action -eq 'allow'}
  }
  if(!$allowed){continue}
 }
 $artifact=$entry.downloads.artifact
 if($artifact.path){
  $path=Join-Path $LibrariesDirectory $artifact.path
  if(!(Test-Path -LiteralPath $path -PathType Leaf)){throw "Missing declared runtime library: $($artifact.path)"}
  if($artifact.sha1 -and (Get-FileHash -LiteralPath $path -Algorithm SHA1).Hash.ToLowerInvariant() -ne $artifact.sha1){throw "Runtime library integrity mismatch: $($artifact.path)"}
  if(!$libs.Contains($path)){$libs.Add($path)}
 }
}
foreach($p in $libs){if(!(Test-Path -LiteralPath $p -PathType Leaf)){throw "Missing exact API JAR: $p"}}
$cp=$libs -join ';'
$inventory=foreach($p in $libs){[PSCustomObject]@{file=$p;sha256=(Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash}}
Write-Local 'test-results\compile-inputs.json' ($inventory|ConvertTo-Json)
# Extract the original nested UI JAR as a read-only test reference, never bundle it.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive=[IO.Compression.ZipFile]::OpenRead($AutoModpackJar)
try{
 $entry=$archive.GetEntry('META-INF/jarjar/automodpack-mod.jar')
 if(!$entry){throw 'Missing AutoModpack nested UI'}
 $input=$entry.Open()
 $output=[IO.File]::Create((Join-Path $PSScriptRoot 'reference\automodpack-mod-4.0.6.jar'))
 try{$input.CopyTo($output)}finally{$input.Dispose();$output.Dispose()}
}finally{$archive.Dispose()}
function Javac([string]$source,[string]$output,[string]$classpath,[string]$argname){
 $arguments=@('--release','21','-encoding','UTF-8','-proc:none','-classpath',('"'+$classpath.Replace('\','/')+'"'),'-d',('"'+$output.Replace('\','/')+'"'))
 $arguments+=Get-ChildItem -LiteralPath $source -Recurse -Filter *.java|ForEach-Object {'"'+$_.FullName.Replace('\','/')+'"'}
 Write-Local "temp\$argname" ($arguments -join [Environment]::NewLine)
 $previousPreference=$ErrorActionPreference
 try {$ErrorActionPreference="Continue"; & $javac "@temp\$argname"; $compilerExit=$LASTEXITCODE} finally {$ErrorActionPreference=$previousPreference}
 if($compilerExit -ne 0){throw 'javac failed'}
}
Javac 'src\main\java' $classes $cp 'main.args'
Copy-Item -Path 'src\main\resources\*' -Destination $classes -Recurse
$artifact=Join-Path $PSScriptRoot "dist\neos-colony-automodpack-autorestart-1.21.1-1.0.1-$run.jar"
& $jar --create --file $artifact -C $classes .
if($LASTEXITCODE -ne 0){throw 'jar failed'}
Javac 'src\test\java' $tests "$classes;$cp" 'tests.args'
& $java "-Djava.io.tmpdir=$PSScriptRoot\temp" "-Djna.tmpdir=$PSScriptRoot\temp" -cp "$tests;$artifact;$cp" dev.neoscolony.autorestart.ModTests $artifact $AutoModpackJar (Join-Path $PSScriptRoot 'reference\automodpack-mod-4.0.6.jar') $GuiJar 2>&1 | Tee-Object -FilePath 'test-results\latest-tests.txt'
if($LASTEXITCODE -ne 0){throw 'Tests failed'}
$services=Join-Path $tests 'META-INF\services'
[IO.Directory]::CreateDirectory($services)|Out-Null
[IO.File]::WriteAllText((Join-Path $services 'org.spongepowered.asm.service.IMixinService'),'dev.neoscolony.autorestart.OfflineMixinService',[Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllText((Join-Path $services 'org.spongepowered.asm.service.IGlobalPropertyService'),'dev.neoscolony.autorestart.OfflineProperties',[Text.UTF8Encoding]::new($false))
& $java "-Djava.io.tmpdir=$PSScriptRoot\temp" -cp "$tests;$artifact;$AutoModpackJar;$PSScriptRoot\reference\automodpack-mod-4.0.6.jar;$cp" dev.neoscolony.autorestart.OfflineMixinTest 2>&1 | Tee-Object -FilePath 'test-results\latest-mixins.txt'
if($LASTEXITCODE -ne 0){throw 'Offline Mixin tests failed'}
$stable=Join-Path $PSScriptRoot 'dist\neos-colony-automodpack-autorestart-1.21.1-1.0.1.jar'
Copy-Item -LiteralPath $artifact -Destination $stable
$artifact=$stable
Write-Local 'test-results\latest-artifact.txt' $artifact
Get-FileHash -LiteralPath $artifact -Algorithm SHA256
$summary=Get-Content -LiteralPath 'test-results\latest-tests.txt' -Raw
$m=[regex]::Match($summary,'ALL_PASSED=(\d+)')
if(!$m.Success){throw 'Missing completed test summary'}
$test3=Join-Path $PSScriptRoot 'dist\neos-colony-automodpack-autorestart-1.21.1-0.1.0-test.3.jar'
$previousPreserved=$false
if(Test-Path -LiteralPath $test3){
 if((Get-FileHash -LiteralPath $test3 -Algorithm SHA256).Hash -ne '986663F9E2892A1847083CEB83D54EA2B64FEF6817BB05FCE839A1D03BDED882'){throw 'Preserved test.3 changed'}
 $previousPreserved=$true
}
$coreHash=(Get-FileHash -LiteralPath 'src\main\java\dev\neoscolony\autorestart\restart\RestartEngine.java' -Algorithm SHA256).Hash
if($coreHash -ne '692059156A90C669D469DBDE2A58259EDD9B88926B8A1B064783F28B89334C02'){throw 'Restart core changed'}
foreach($inputPath in $externalHashes.Keys){
 if((Get-FileHash -LiteralPath $inputPath -Algorithm SHA256).Hash -ne $externalHashes[$inputPath]){throw 'External original input changed'}
}
$result=[ordered]@{
 version='1.0.1'
 status='RELEASE_BUILD_AND_AUTOMATED_TESTS_PASSED'
 assertions=[int]$m.Groups[1].Value
 offlineMixinTransformations=2
 artifact=$artifact
 sha256=(Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash
 bytes=(Get-Item -LiteralPath $artifact).Length
 previousTest3Preserved=$previousPreserved
 restartCoreUnchanged=$true
 autoModpackSha256=(Get-FileHash -LiteralPath $AutoModpackJar -Algorithm SHA256).Hash
 guiSha256=if($GuiJar){(Get-FileHash -LiteralPath $GuiJar -Algorithm SHA256).Hash}else{$null}
 inGameTest='RESTART_CORE_UNCHANGED; NEW_DIALOG_FLOW_REQUIRES_ISOLATED_CLIENT_CHECK'
}
Write-Local 'test-results\release-build.json' ($result|ConvertTo-Json)

$sha=(Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash.ToLowerInvariant()
Write-Local ('dist\'+[IO.Path]::GetFileName($artifact)+'.sha256') ($sha+'  '+[IO.Path]::GetFileName($artifact)+[Environment]::NewLine)
Write-Output "RELEASE_JAR=$artifact"
