package neos.restart.v4;
import java.nio.file.*;
import java.net.*;
import java.util.*;
public final class JnaResolutionTests {
 static int passed;
 static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);System.out.println("PASS "+name);passed++;}

 static java.lang.instrument.Instrumentation emptyInstrumentation() {
  return (java.lang.instrument.Instrumentation)java.lang.reflect.Proxy.newProxyInstance(
    JnaResolutionTests.class.getClassLoader(), new Class<?>[]{java.lang.instrument.Instrumentation.class},
    (proxy, method, args) -> {
      if(method.getName().equals("getAllLoadedClasses"))return new Class<?>[0];
      throw new UnsupportedOperationException();
    });
 }

 public static void main(String[] args)throws Exception{
  Path jna=Path.of(args[0]),dir=Prototype.ROOT.resolve("test-results/jna-resolution");
  Files.createDirectories(dir);
  check(Prototype.auditJna(jna)>0,"all JNA archive entries size and CRC valid");
  check(Prototype.physicalJnaPath(jna).equals(jna.toRealPath()),"ordinary file code source retained");
  try(URLClassLoader loader=new URLClassLoader(new URL[]{jna.toUri().toURL()},ClassLoader.getPlatformClassLoader())){
   check(Prototype.resolveJnaJar(Class.forName("com.sun.jna.Native",false,loader)).equals(jna.toRealPath()),"resolved JAR contains the loaded Native class resource");
  }
  Path html=dir.resolve("not-a-library.jar");Files.writeString(html,"<html>failed download</html>");
  try{Prototype.auditJna(html);throw new AssertionError();}catch(java.io.IOException expected){check(true,"HTML disguised as JAR rejected");}
  Path truncated=dir.resolve("truncated.jar");byte[] bytes=Files.readAllBytes(jna);Files.write(truncated,Arrays.copyOf(bytes,bytes.length/2));
  try{Prototype.auditJna(truncated);throw new AssertionError();}catch(java.io.IOException expected){check(true,"truncated JAR rejected");}
  try{Prototype.physicalJnaPath(dir);throw new AssertionError();}catch(java.io.IOException expected){check(true,"ordinary directory rejected");}

  Prototype.Context incomplete=new Prototype.Context();incomplete.executable=PrototypeTests.java;
  incomplete.cwd=dir.toString();incomplete.environment="\0\0".getBytes(java.nio.charset.StandardCharsets.UTF_16LE);
  incomplete.command="javaw Minecraft";
  check(Prototype.inspectionReadiness(incomplete,emptyInstrumentation()).equals("NO_GAME_DIR"),"inspection reports missing original args without inventing them");

  if(args.length>1){
   try(URLClassLoader loader=new URLClassLoader(new URL[]{Path.of(args[1]).toUri().toURL()},ClassLoader.getPlatformClassLoader())){
    Class<?> providerClass=Class.forName("cpw.mods.niofs.union.UnionFileSystemProvider",true,loader);
    Class<?> filterClass=Class.forName("cpw.mods.niofs.union.UnionPathFilter",true,loader);
    Object provider=providerClass.getConstructor().newInstance();
    try(var fs=(FileSystem)providerClass.getMethod("newFileSystem",filterClass,Path[].class).invoke(provider,null,(Object)new Path[]{jna})){
     Path virtualRoot=fs.getRootDirectories().iterator().next();
     check(Files.isDirectory(virtualRoot)&&!Files.isRegularFile(virtualRoot),"real NeoForge union root reproduces old refusal");
     check(Prototype.physicalJnaPath(virtualRoot).equals(jna.toRealPath()),"real NeoForge union root resolves to physical JAR");
     check(Prototype.auditJna(Prototype.physicalJnaPath(virtualRoot))>0,"resolved union JAR passes full integrity check");
    }
   }
  }
  System.out.println("JNA_RESOLUTION_PASSED="+passed);
 }
}
