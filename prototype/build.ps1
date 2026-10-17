param([string]$JnaJar, [string]$UnionJar)
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$env:TEMP = Join-Path $projectRoot 'temp'
$env:TMP = $env:TEMP
New-Item -ItemType Directory -Path $env:TEMP, (Join-Path $projectRoot 'out-v4'), (Join-Path $projectRoot 'dist') -Force | Out-Null
$javaCommand = (Get-Command javac.exe -ErrorAction Stop).Source
$jdkBin = Split-Path -Parent $javaCommand
$sources = @('Prototype.java','PrototypeTests.java','JnaAudit.java','JnaResolutionTests.java','LaunchArgumentTests.java') | ForEach-Object { Join-Path $projectRoot "src\neos\restart\$_" }
& $javaCommand --release 21 --add-modules jdk.attach -encoding UTF-8 -d (Join-Path $projectRoot 'out-v4') @sources
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }
$manifest = @('Manifest-Version: 1.0','Main-Class: neos.restart.v4.Prototype','Agent-Class: neos.restart.v4.Prototype','Can-Redefine-Classes: false','Can-Retransform-Classes: false','','') -join [Environment]::NewLine
[IO.File]::WriteAllText((Join-Path $projectRoot 'manifest-v4.mf'), $manifest, [Text.UTF8Encoding]::new($false))
$artifact = Join-Path $projectRoot 'dist\autorestart-prototype-v4.jar'
& (Join-Path $jdkBin 'jar.exe') --create --file $artifact --manifest (Join-Path $projectRoot 'manifest-v4.mf') -C (Join-Path $projectRoot 'out-v4') neos/restart/v4
if ($LASTEXITCODE -ne 0) { throw 'jar failed; a loaded agent JAR can be locked until its target JVM exits' }
if ($JnaJar) {
 & (Join-Path $jdkBin 'java.exe') "-Djava.io.tmpdir=$env:TEMP" "-Djna.tmpdir=$env:TEMP" --add-modules jdk.attach -cp $artifact neos.restart.v4.PrototypeTests $JnaJar
 if ($LASTEXITCODE -ne 0) { throw 'Prototype tests failed' }
 $auditArgs = @("-Djava.io.tmpdir=$env:TEMP",'--add-opens=java.base/java.lang.invoke=ALL-UNNAMED','-cp',$artifact,'neos.restart.v4.JnaResolutionTests',$JnaJar)
 if ($UnionJar) { $auditArgs += $UnionJar }
 & (Join-Path $jdkBin 'java.exe') @auditArgs
 if ($LASTEXITCODE -ne 0) { throw 'JNA resolution tests failed' }
 & (Join-Path $jdkBin 'java.exe') "-Djava.io.tmpdir=$env:TEMP" -cp $artifact neos.restart.v4.JnaAudit $JnaJar
 if ($LASTEXITCODE -ne 0) { throw 'JNA audit failed' }
 & (Join-Path $jdkBin 'java.exe') "-Djava.io.tmpdir=$env:TEMP" "-Djna.tmpdir=$env:TEMP" -cp $artifact neos.restart.v4.LaunchArgumentTests $JnaJar
 if ($LASTEXITCODE -ne 0) { throw 'Launcher argument tests failed' }
}
