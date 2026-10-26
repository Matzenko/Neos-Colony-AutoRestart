param([Parameter(Mandatory=$true)][string]$AutoModpackJar,[string]$JavaHome)
$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot
if(!$JavaHome){$JavaHome=Split-Path (Split-Path (Get-Command javac.exe).Source)}
$work=Join-Path $repo ('temp\native-rollout-'+[Guid]::NewGuid().ToString('N'))
[IO.Directory]::CreateDirectory($work)|Out-Null
$classes=Join-Path $work 'classes'
[IO.Directory]::CreateDirectory($classes)|Out-Null
$jar=Join-Path $repo 'downloads\1.0.1\neos-colony-automodpack-autorestart-1.21.1-1.0.1.jar'
$old=Join-Path $repo 'dist\neos-colony-automodpack-autorestart-1.21.1-1.0.0.jar'
$cp=@($classes,$jar,$AutoModpackJar)+@((Get-Content (Join-Path $repo 'test-results\compile-inputs.json') -Raw|ConvertFrom-Json)|ForEach-Object file)
$classpath=$cp -join ';'
$source=Join-Path $repo 'src\test\java\dev\neoscolony\autorestart\FullRolloutHarness.java'
$compilerArgs=@('--release','21','-proc:none','-encoding','UTF-8','-cp',('"'+$classpath.Replace('\','/')+'"'),'-d',('"'+$classes.Replace('\','/')+'"'),('"'+$source.Replace('\','/')+'"'))
$argsFile=Join-Path $work 'compile.args'
[IO.File]::WriteAllLines($argsFile,$compilerArgs)
$previous=$ErrorActionPreference;try{$ErrorActionPreference='Continue';& (Join-Path $JavaHome 'bin\javac.exe') ('@'+$argsFile);$code=$LASTEXITCODE}finally{$ErrorActionPreference=$previous}
if($code -ne 0){throw 'Test compilation failed.'}
$server=Join-Path $work 'server';[IO.Directory]::CreateDirectory($server)|Out-Null
function Run([string]$cwd,[string[]]$arguments,[string]$log){
 $psi=[Diagnostics.ProcessStartInfo]::new();$psi.FileName=Join-Path $JavaHome 'bin\java.exe';$psi.WorkingDirectory=$cwd;$psi.UseShellExecute=$false;$psi.CreateNoWindow=$true
 $all=@(('-Djava.io.tmpdir='+$work),'-cp',$classpath,'dev.neoscolony.autorestart.FullRolloutHarness')+$arguments
 # Argument paths are controlled local values, never original launcher arguments.
 $psi.Arguments=($all|ForEach-Object {'"'+$_.Replace('"','\"')+'"'}) -join ' '
 $psi.RedirectStandardOutput=$true;$psi.RedirectStandardError=$true
 $p=[Diagnostics.Process]::Start($psi);$stdout=$p.StandardOutput.ReadToEndAsync();$stderr=$p.StandardError.ReadToEndAsync()
 if(!$p.WaitForExit(45000)){$p.Kill();throw 'Native rollout fixture timeout.'}
 [IO.File]::WriteAllText($log,$stdout.Result+$stderr.Result)
 if($p.ExitCode -ne 0){Get-Content $log -Tail 22;throw 'Native rollout fixture failed.'}
}
Run $server @('server',$jar,$old) (Join-Path $work 'server.txt')
for($i=1;$i -le 5;$i++){$client=Join-Path $work ('client-'+$i);[IO.Directory]::CreateDirectory($client)|Out-Null;Run $client @('client',$server,$old,[string]$i) (Join-Path $work ('client-'+$i+'.txt'))}
Write-Output ('NATIVE_ROLLOUT_TEST_DIRECTORY='+$work)
Get-ChildItem $work -Filter *.txt|ForEach-Object {Get-Content $_.FullName|Where-Object {$_ -match '^(CLIENT_ROLLOUT_OK|ROLLOUT_ASSERTIONS_PASSED)='}}
