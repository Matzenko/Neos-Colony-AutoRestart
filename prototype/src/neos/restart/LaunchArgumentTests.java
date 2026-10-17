package neos.restart.v4;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.net.*;
import java.io.*;
import java.util.*;
import java.security.MessageDigest;
import java.lang.management.ManagementFactory;
import java.util.concurrent.TimeUnit;

public final class LaunchArgumentTests {
 static int passed;
 static Path root=Prototype.ROOT;
 static String javaExe=ProcessHandle.current().info().command().orElseThrow();
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);System.out.println("PASS "+message);passed++;}
 static boolean has(Path p,String text){try{return Files.readString(p).contains(text);}catch(Exception e){return false;}}
 static void waitFor(java.util.function.BooleanSupplier b)throws Exception{
  long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(25);
  while(!b.getAsBoolean()&&System.nanoTime()<end)Thread.sleep(50);
  if(!b.getAsBoolean())throw new AssertionError("Timeout in JLI fixture");
 }
 public static void main(String[] args)throws Exception{
  Path jna=Path.of(args[0]),dir=root.resolve("test-results").resolve("jli replay "+UUID.randomUUID());
  Files.createDirectories(dir);
  List<String> edge=List.of(javaExe,"","space value","a\"b","C:\\space path\\","slashes\\\\\"quote","\u00dc\u00df","\t");
  check(Prototype.windowsArgs(Prototype.windowsCommand(edge)).equals(edge),"exact argument boundary roundtrip including quotes empty strings Unicode trailing backslash");
  Prototype.Context c=new Prototype.Context();c.executable=javaExe;
  List<String> vector=List.of(javaExe,"Main","--gameDir",dir.toString(),"","space value");
  String javaCommand=String.join(" ",vector.subList(1,vector.size()));
  Prototype.applyLauncherArguments(c,vector,2,javaCommand);
  check(c.argumentSource.equals("JLI_EXPANDED_ARGV")&&c.applicationArgumentsVerified,"structured launcher arguments verified without parsing sun.java.command");
  check(Prototype.windowsArgs(c.command).equals(vector),"all launcher argument tokens preserved");
  try{Prototype.applyLauncherArguments(c,vector,2,"different");throw new AssertionError();}catch(IOException expected){check(true,"mismatched application source refused");}
  try{Prototype.applyLauncherArguments(c,vector,0,javaCommand);throw new AssertionError();}catch(IOException expected){check(true,"invalid application index refused");}
  List<String> at=List.of(javaExe,"Main","@literal");
  try{Prototype.applyLauncherArguments(c,at,2,"Main @literal");throw new AssertionError();}catch(IOException expected){check(true,"ambiguous literal at-argument refused");}
  check(c.argumentFiles.isEmpty(),"monitored library is not counted as an argument file");
  Files.writeString(dir.resolve("launch.args"),"-Dfixture.sample=\"value with spaces\"\n",StandardCharsets.US_ASCII);
  List<String> command=List.of(javaExe,"@"+dir.resolve("launch.args"),"-Djava.io.tmpdir="+root.resolve("temp"),"-Djna.tmpdir="+root.resolve("temp"),"-cp",Prototype.jar()+";"+jna,Fixture.class.getName(),"--gameDir",dir.toString(),"value with spaces","","trailing\\");
  ProcessBuilder pb=new ProcessBuilder(command).directory(dir.toFile());
  pb.environment().put("NEOS_JLI_FIXTURE","original environment \u00dc");
  pb.redirectError(dir.resolve("fixture.err").toFile());pb.redirectOutput(dir.resolve("fixture.out").toFile());
  Process original=pb.start();
  check(original.waitFor(20,TimeUnit.SECONDS),"JLI original fixture exits normally");
  check(original.exitValue()==0,"JLI original fixture succeeded");
  waitFor(()->Files.exists(dir.resolve("start-2")));
  check(Files.readString(dir.resolve("start-1")).equals(Files.readString(dir.resolve("start-2"))),"JLI-recovered launch preserves application args JVM flags cwd and complete environment");
  check(!Files.exists(dir.resolve("launch.args")),"original argument file removed before replay");
  check(has(dir.resolve("helper.log"),"ATTEMPT=1")&&has(dir.resolve("helper.log"),"CHILD_CREATED_PID="),"expanded in-memory arguments launch once without original argument file");
  check(Files.exists(dir.resolve("original-exited")),"recovered launch waits until original shutdown hook finished");
  Thread.sleep(500);
  check(!Files.exists(dir.resolve("start-3")),"no third process after recovered replay");
  System.out.println("LAUNCH_ARGUMENT_TESTS_PASSED="+passed);
 }
 public static final class Fixture{
  public static void main(String[] args)throws Exception{
   Class.forName("com.sun.jna.Native");
   Path dir=Path.of(args[1]);int generation=1;while(Files.exists(dir.resolve("start-"+generation)))generation++;
   Prototype.NativeWin win=new Prototype.NativeWin(Fixture.class.getClassLoader());
   Prototype.Context c=win.capture();
   String evidence="args="+Arrays.toString(args)+"\nvm="+ManagementFactory.getRuntimeMXBean().getInputArguments()+"\ncwd="+c.cwd+"\nenv="+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(c.environment));
   Files.writeString(dir.resolve("start-"+generation),evidence);
   if(generation>1)return;
   Runtime.getRuntime().addShutdownHook(new Thread(()->{try{Thread.sleep(200);Files.writeString(dir.resolve("original-exited"),"done");}catch(Exception ignored){}}));
   c.command="Minecraft synthetic unavailable command"; // Change only this local test copy.
   win.recoverLauncherArguments(c);
   Prototype.validateContext(c);
   if(!c.argumentFiles.isEmpty())throw new AssertionError("JLI-expanded args unexpectedly need source file");
   c.parentPid=ProcessHandle.current().pid();c.parentStart=ProcessHandle.current().info().startInstant().orElseThrow().toString();
   c.jnaJar=Path.of(Class.forName("com.sun.jna.Native").getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
   c.log=dir.resolve("helper.log").toString();c.cancel=dir.resolve("cancel").toString();
   Files.delete(dir.resolve("launch.args")); // Only our synthetic fixture file.
   Process helper=new ProcessBuilder(c.executable,"-Djava.io.tmpdir="+root.resolve("temp"),"-Djna.tmpdir="+root.resolve("temp"),"-cp",Prototype.jar().toString(),Prototype.class.getName(),"helper").redirectError(ProcessBuilder.Redirect.DISCARD).start();
   try(ObjectOutputStream out=new ObjectOutputStream(helper.getOutputStream())){out.writeObject(c);}
   waitFor(()->has(Path.of(c.log),"HELPER_READY"));
  }
 }
}
