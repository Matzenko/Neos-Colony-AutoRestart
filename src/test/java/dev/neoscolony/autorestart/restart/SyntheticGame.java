package dev.neoscolony.autorestart.restart;
import java.nio.file.*;
import java.util.*;
public final class SyntheticGame{
    public static void main(String[] args)throws Exception{
        String mode=args[0];Path dir=Path.of(args[1]);
        Class<?> jna=Class.forName("com.sun.jna.Native");
        RestartEngine.NativeWin win=new RestartEngine.NativeWin(jna.getClassLoader());
        RestartEngine.Context captured=win.capture();
        win.recoverLauncherArguments(captured);
        List<String> effective=new ArrayList<>(RestartEngine.windowsArgs(captured.command));
        effective.removeIf(a->a.startsWith("-D"+RestartEngine.MARKER+"="));
        String evidence=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(captured.environment))
            +":"+HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(RestartEngine.windowsCommand(effective).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        if(!System.getProperty(RestartEngine.MARKER,"").isEmpty()){
            boolean ok=System.getProperty(RestartEngine.MARKER).equals("a".repeat(64))
                &&"Unicode ä test".equals(System.getenv("NEOS_SYNTHETIC"))
                &&Path.of("").toAbsolutePath().equals(dir)
                &&args.length==4&&args[2].equals("--gameDir")&&args[3].equals(dir.toString())
                                &&Files.exists(dir.resolve("shutdown.txt"))
                &&Files.readString(dir.resolve("evidence.txt")).equals(evidence)
                &&(!mode.equals("argfile")||("value with spaces".equals(System.getProperty("fixture.option"))&&!Files.exists(dir.resolve("launch.args"))));
            Path report=dir.resolve(Files.exists(dir.resolve("child.txt"))?"third.txt":"child.txt");
            Files.writeString(report,ok?"OK":"FAILED");return;
        }
        Files.writeString(dir.resolve("evidence.txt"),evidence);
        Runtime.getRuntime().addShutdownHook(new Thread(()->{
            try{Thread.sleep(250);Files.writeString(dir.resolve("shutdown.txt"),"complete");}catch(Exception e){throw new RuntimeException(e);}
        }));
        RestartEngine.Prepared prepared=mode.equals("unarmed")?null:RestartEngine.prepare("a".repeat(64),dir);
        if(mode.equals("argfile"))Files.delete(dir.resolve("launch.args"));
        System.out.println("PARENT_READY");System.out.flush();
        System.in.read();
        if(prepared!=null){
            if(mode.equals("eof"))System.exit(0);
            if(mode.equals("changed"))Files.writeString(dir.resolve("watched.jar"),"changed",StandardOpenOption.APPEND);
            if(mode.equals("commit")||mode.equals("changed")||mode.equals("abort")||mode.equals("argfile"))prepared.commit();
            if(mode.equals("abort"))prepared.abort();
            prepared.close();
        }
    }
}
