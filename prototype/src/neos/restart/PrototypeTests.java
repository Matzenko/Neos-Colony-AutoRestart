package neos.restart.v4;
import com.sun.tools.attach.VirtualMachine;
import java.nio.file.*; import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
public final class PrototypeTests {
 static Path root=Prototype.ROOT,jna;
 static String java=ProcessHandle.current().info().command().orElseThrow();
 static int passed;
 static void check(boolean condition,String name) { if(!condition)throw new AssertionError(name); System.out.println("PASS "+name);passed++; }
 static void await(java.util.function.BooleanSupplier condition,String name)throws Exception{
  long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(25);
  while(!condition.getAsBoolean()&&System.nanoTime()<end)Thread.sleep(50);
  if(!condition.getAsBoolean())throw new AssertionError("TIMEOUT "+name);
 }
 static boolean has(Path p,String s){try{return Files.readString(p).contains(s);}catch(Exception e){return false;}}
 static void attach(long pid,String mode,String id)throws Exception{
  VirtualMachine vm=VirtualMachine.attach(""+pid);
  try{vm.loadAgent(Prototype.jar().toString(),mode+"|"+id);}finally{vm.detach();}
 }
 static Process fixture(Path dir,boolean withJna,boolean argfile)throws Exception{
  Files.createDirectories(dir);
  List<String> cmd=new ArrayList<>(List.of(java,"-Djava.io.tmpdir="+root.resolve("temp"),"-Djna.tmpdir="+root.resolve("temp")));
  if(argfile){Files.writeString(dir.resolve("launch.args"),"-Dprototype.sample=\"synthetic sample\"\n",StandardCharsets.US_ASCII);cmd.add("@launch.args");}
  cmd.addAll(List.of("-cp",Prototype.jar()+(withJna?";"+jna:""),Fixture.class.getName(),"--gameDir",dir.toString(),"space value","","plain-value","C:\\trailing\\","\u00dc\u00df\u03bb"));
  ProcessBuilder pb=new ProcessBuilder(cmd).directory(dir.toFile());
  pb.environment().put("NEOS_FIXTURE_VALUE","spaces \u00dc\u00df\u03bb ; = literal");
  pb.redirectOutput(dir.resolve("fixture.out").toFile());pb.redirectError(dir.resolve("fixture.err").toFile());
  return pb.start();
 }
 static void stop(Process p,Path dir)throws Exception{
  Files.writeString(dir.resolve("stop"),"stop");
  if(!p.waitFor(10,TimeUnit.SECONDS))throw new AssertionError("fixture did not stop");
 }
 static void scenario(String name,boolean cancel,boolean remove,boolean change)throws Exception{
  Path dir=root.resolve("test-results").resolve(name+" "+UUID.randomUUID());
  Process p=fixture(dir,true,true);
  String id=UUID.randomUUID().toString();Path log=root.resolve("runs").resolve(id+".log");
  try{
   await(()->Files.exists(dir.resolve("start-1")),"fixture ready");
   attach(p.pid(),"arm",id);
   check(has(log,"ARMED_CLOSE"),name+" armed");
   check(!Files.exists(dir.resolve("start-2")),name+" waits while parent lives");
   String dupe=UUID.randomUUID().toString();attach(p.pid(),"arm",dupe);
   check(has(root.resolve("runs").resolve(dupe+".log"),"REFUSED_ALREADY_ARMED"),name+" duplicate arm rejected");
   if(cancel)Files.writeString(root.resolve("runs").resolve(id+".cancel"),"cancel");
   if(remove)Files.delete(dir.resolve("launch.args"));
   if(change)Files.writeString(dir.resolve("launch.args"),"-Dprototype.sample=changed\n");
   stop(p,dir);
   if(cancel||remove||change){
    await(()->has(log,"CANCELLED")||has(log,"STOPPED_NO_RETRY"),name+" refusal");
    check(!has(log,"ATTEMPT=1"),name+" no launch attempt");
    check(!Files.exists(dir.resolve("start-2")),name+" no child");
   }else{
    await(()->Files.exists(dir.resolve("start-2")),name+" child start");
    check(has(log,"ORIGINAL_PROCESS_EXITED"),name+" parent exit recorded");
    check(Files.exists(dir.resolve("original-exited")),name+" original shutdown hooks finished");
    check(Files.readString(dir.resolve("start-1")).equals(Files.readString(dir.resolve("start-2"))),name+" args cwd env JVM property identical");
    Thread.sleep(1000);
    check(!Files.exists(dir.resolve("start-3")),name+" no third process");
    check(Files.readString(log).lines().filter(s->s.endsWith("ATTEMPT=1")).count()==1,name+" exactly one attempt");
   }
   check(!Files.readString(log).contains("synthetic sample"),name+" values not logged");
  }finally{if(p.isAlive()){Files.writeString(dir.resolve("stop"),"stop");p.waitFor(5,TimeUnit.SECONDS);}}
 }
 public static void main(String[] args)throws Exception{
  jna=Path.of(args[0]);Files.createDirectories(root.resolve("temp"));Files.createDirectories(root.resolve("runs"));
  check(Prototype.windowsArgs("\"java.exe\" \"\" \"a b\" C:\\tail\\").equals(List.of("java.exe","","a b","C:\\tail\\")),"Windows empty spaces backslash parsing");
  check(Prototype.windowsArgs("java \"a\\\"b\" \"a\"\"b\"").equals(List.of("java","a\"b","a\"b")),"Windows escaped and doubled quotes");
  check(Prototype.argFile("# comment\n-Da=\"a b\"\n''\n-Dp='c:\\\\x'\n").equals(List.of("-Da=a b","","-Dp=c:\\x")),"Java argfile quotes comments escapes");
  try{Prototype.windowsArgs("java \"unterminated");throw new AssertionError();}catch(java.io.IOException e){check(true,"malformed command refused");}
  validationGuards(); inspectScenario(); nativeFailureScenario();
  scenario("normal unicode",false,false,false);scenario("cancel",true,false,false);scenario("missing argfile",false,true,false);scenario("changed argfile",false,false,true);
  Path dir=root.resolve("test-results").resolve("no-jna-"+UUID.randomUUID());Process p=fixture(dir,false,false);
  try{await(()->Files.exists(dir.resolve("start-1")),"no JNA fixture");String id=UUID.randomUUID().toString();attach(p.pid(),"arm",id);
   check(has(root.resolve("runs").resolve(id+".log"),"REFUSED_JNA_NOT_LOADED"),"missing JNA refuses without installation");
  }finally{stop(p,dir);}
  System.out.println("ALL_PASSED="+passed);
 }
 static void validationGuards() throws Exception {
  Path dir=root.resolve("test-results").resolve("guards-"+UUID.randomUUID());Files.createDirectories(dir);
  for(String suffix:List.of("--server localhost","--quickPlayMultiplayer localhost","-javaagent:unknown.jar","--gameDir duplicate")){
   Prototype.Context c=new Prototype.Context();c.executable=java;c.cwd=dir.toString();c.environment="\0\0".getBytes(StandardCharsets.UTF_16LE);
   c.command="\""+java+"\" Main --gameDir \""+dir+"\" "+suffix;
   try{Prototype.validateContext(c);throw new AssertionError("guard did not refuse");}catch(java.io.IOException expected){check(true,"unsafe or ambiguous startup rejected");}
  }
  Prototype.Context c=new Prototype.Context();c.executable=java;c.cwd=dir.toString();c.command="\""+java+"\" Main --gameDir \""+dir+"\"";
  c.environment="JDK_JAVA_OPTIONS=-Dexample=1\0\0".getBytes(StandardCharsets.UTF_16LE);
  try{Prototype.validateContext(c);throw new AssertionError("environment injection accepted");}catch(java.io.IOException expected){check(true,"unexamined environment JVM injection rejected");}
  Path arg=dir.resolve("nonascii.args");Files.writeString(arg,"-Dname=\u00dc\n");
  c.environment="\0\0".getBytes(StandardCharsets.UTF_16LE);c.command="\""+java+"\" @nonascii.args Main --gameDir \""+dir+"\"";
  try{Prototype.validateContext(c);throw new AssertionError("non-ASCII argfile accepted");}catch(java.io.IOException expected){check(true,"unsupported argfile encoding rejected");}
 }
 
static void inspectScenario() throws Exception {
  Path dir=root.resolve("test-results").resolve("inspect-"+UUID.randomUUID());Process p=fixture(dir,true,true);
  try{
   await(()->Files.exists(dir.resolve("start-1")),"inspect fixture");
   String id=UUID.randomUUID().toString();attach(p.pid(),"inspect",id);Path log=root.resolve("runs").resolve(id+".log");
   check(has(log,"INSPECT_ONLY_NO_RESTART"),"inspect captures without arming");
   check(!has(log,"HELPER_READY"),"inspect creates no helper");
   stop(p,dir);Thread.sleep(200);
   check(!Files.exists(dir.resolve("start-2")),"inspect does not restart");
  }finally{if(p.isAlive())stop(p,dir);}
 }
 static void nativeFailureScenario() throws Exception {
  Path dir=root.resolve("test-results").resolve("invalid-exe-"+UUID.randomUUID());Process p=fixture(dir,false,false);
  try(java.net.URLClassLoader loader=new java.net.URLClassLoader(new java.net.URL[]{jna.toUri().toURL()},ClassLoader.getPlatformClassLoader())){
   await(()->Files.exists(dir.resolve("start-1")),"failure fixture");
   Prototype.Context c=new Prototype.NativeWin(loader).capture();
   Path fake=dir.resolve("invalid.exe");Files.writeString(fake,"not a Windows executable");
   c.executable=fake.toString();c.cwd=dir.toString();c.jnaJar=jna.toString();
   c.files.put(fake.toString(),Prototype.digest(fake));c.parentPid=p.pid();c.parentStart=p.info().startInstant().orElseThrow().toString();
   Path log=dir.resolve("failure.log");c.log=log.toString();c.cancel=dir.resolve("cancel").toString();
   Process helper=new ProcessBuilder(java,"-Djava.io.tmpdir="+root.resolve("temp"),"-Djna.tmpdir="+root.resolve("temp"),"-cp",Prototype.jar().toString(),Prototype.class.getName(),"helper").redirectError(ProcessBuilder.Redirect.DISCARD).start();
   try(java.io.ObjectOutputStream out=new java.io.ObjectOutputStream(helper.getOutputStream())){out.writeObject(c);}
   await(()->has(log,"HELPER_READY"),"failure helper ready");stop(p,dir);
   check(helper.waitFor(10,TimeUnit.SECONDS),"failed CreateProcess helper exits");
   check(has(log,"ATTEMPT=1")&&has(log,"STOPPED_NO_RETRY"),"failed CreateProcess attempted once with no retry");
   check(!has(log,"CHILD_CREATED"),"failed CreateProcess reports no child");
  }finally{if(p.isAlive())stop(p,dir);}
 }
 public static final class Fixture {
  public static void main(String[] args)throws Exception{
   try{Class.forName("com.sun.jna.Native");}catch(ClassNotFoundException ignored){}
   Path dir=Path.of(args[1]);int generation=1; while(Files.exists(dir.resolve("start-"+generation))) generation++;
   String content="cwd="+System.getProperty("user.dir")+"\nenv="+System.getenv("NEOS_FIXTURE_VALUE")+"\njvm="+System.getProperty("prototype.sample")+"\nargs="+Arrays.toString(args)+"\n";
   try {
    Prototype.Context nativeContext=new Prototype.NativeWin(Fixture.class.getClassLoader()).capture();
    content+="nativeCwd="+nativeContext.cwd+"\nnativeCommand="+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(nativeContext.command.getBytes(StandardCharsets.UTF_16LE)))+"\nnativeEnv="+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(nativeContext.environment))+"\n";
   }catch(ClassNotFoundException ignored){}
   Files.writeString(dir.resolve("start-"+generation),content);
   if(generation==1){
    Runtime.getRuntime().addShutdownHook(new Thread(()->{try{Thread.sleep(300);Files.writeString(dir.resolve("original-exited"),"done");}catch(Exception ignored){}}));
    long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(90);
    while(!Files.exists(dir.resolve("stop"))&&System.nanoTime()<end)Thread.sleep(50);
   }
  }
 }
}
