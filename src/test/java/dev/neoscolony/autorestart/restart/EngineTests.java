package dev.neoscolony.autorestart.restart;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
public final class EngineTests{
    static int n;
    static void check(boolean b,String s){if(!b)throw new AssertionError(s);n++;System.out.println("PASS_ENGINE "+s);}
    public static int run(Path own)throws Exception{
        n=0;
        List<String> difficult=List.of("java.exe","","hello world","a\\","x\"y","ä ö","\\\\server\\share\\");
        check(RestartEngine.windowsArgs(RestartEngine.windowsCommand(difficult)).equals(difficult),"Windows argv roundtrip");
        String hash="a".repeat(64);
        List<String> marked=RestartEngine.windowsArgs(RestartEngine.withMarker(RestartEngine.windowsCommand(difficult),hash));
        check(marked.get(1).equals("-D"+RestartEngine.MARKER+"="+hash),"loop marker inserted as JVM option");
        marked.remove(1);check(marked.equals(difficult),"all original arguments retained");
        String twice=RestartEngine.withMarker(RestartEngine.withMarker(RestartEngine.windowsCommand(difficult),hash),"b".repeat(64));
        check(RestartEngine.windowsArgs(twice).stream().filter(a->a.startsWith("-D"+RestartEngine.MARKER+"=")).count()==1,"marker replaced once");
        Class<?> nativeClass=Class.forName("com.sun.jna.Native");
        RestartEngine.NativeWin win=new RestartEngine.NativeWin(nativeClass.getClassLoader());
        RestartEngine.Context context=win.capture();
        win.recoverLauncherArguments(context);
        check(context.applicationArgumentsVerified,"actual JLI capture/application match in test JVM");
        check(Files.isRegularFile(RestartEngine.resolveJnaJar(nativeClass)),"physical JNA integrity");
        Path root=Files.createTempDirectory(Path.of(System.getProperty("java.io.tmpdir")),"engine-");
        Path jna=RestartEngine.resolveJnaJar(nativeClass);
        Path testClasses=Path.of(EngineTests.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        for(String mode:List.of("cancel","commit","unarmed","abort","eof","changed","argfile")){
            Path work=Files.createDirectory(root.resolve(mode+" ä"));
            Path watched=work.resolve("watched.jar");
            try(var zip=new java.util.jar.JarOutputStream(Files.newOutputStream(watched))){}
            String cp=testClasses+";"+own+";"+jna+";"+watched;
            ProcessBuilder pb=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),
                "-XX:-UsePerfData","-Djava.io.tmpdir="+root,"-Djna.tmpdir="+root,
                "-cp",cp,SyntheticGame.class.getName(),mode,work.toString(),"--gameDir",work.toString());
            if(mode.equals("argfile")){
                Path argfile=work.resolve("launch.args");
                Files.writeString(argfile,"-Dfixture.option=\"value with spaces\"\n",java.nio.charset.StandardCharsets.US_ASCII);
                pb.command().add(1,"@"+argfile);
            }
            pb.directory(work.toFile());pb.environment().put("NEOS_SYNTHETIC","Unicode ä test");
            pb.redirectError(work.resolve("stderr.txt").toFile());
            Process p=pb.start();
            BufferedReader reader=p.inputReader();
            var lineTask=new FutureTask<String>(reader::readLine);new Thread(lineTask).start();
            check("PARENT_READY".equals(lineTask.get(30,TimeUnit.SECONDS)),mode+" ready");
            check(!Files.exists(work.resolve("child.txt")),mode+" no start while parent alive");
            p.getOutputStream().write(1);p.getOutputStream().flush();
            check(p.waitFor(30,TimeUnit.SECONDS)&&p.exitValue()==0,mode+" parent normal exit");
            Path report=work.resolve("child.txt");
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos((mode.equals("commit")||mode.equals("argfile"))?20:2);
            while(System.nanoTime()<deadline&&!Files.exists(report))Thread.sleep(50);
            check(Files.exists(report)==(mode.equals("commit")||mode.equals("argfile")),mode+" expected child count");
            if(mode.equals("commit")||mode.equals("argfile")){
                String result=Files.readString(report);
                check(result.equals("OK"),"restart cwd/env/argv/marker preserved and original fully exited");
                Thread.sleep(1000);
                check(!Files.exists(work.resolve("third.txt")),"no third process");
            }
        }
        // Real helper startup failure: a valid empty test JAR cannot provide the helper main class.
        Path brokenHelper=Files.createTempFile(root,"missing-helper-main-",".jar");
        try(var zip=new java.util.jar.JarOutputStream(Files.newOutputStream(brokenHelper))){}
        RestartEngine.Context failedContext=win.capture();
        failedContext.jnaJar=jna.toString();
        boolean failed=false;
        try{
            RestartEngine.startHelper(failedContext,brokenHelper,Path.of(System.getProperty("jnidispatch.path")));
        }catch(Exception expected){failed=true;}
        check(failed,"helper without main class fails before READY");
        check(failedContext.command==null&&java.util.Arrays.equals(failedContext.environment,new byte[failedContext.environment.length]),"failed helper discards in-memory start context");

        return n;
    }
}
