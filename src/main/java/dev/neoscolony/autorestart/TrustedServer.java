package dev.neoscolony.autorestart;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;
/** Exact administrator-configured endpoint tuple AND already trusted certificate. Never learns trust. */
public final class TrustedServer {
 public static final String FILE="neoscolony-autorestart-trusted.properties";
 private TrustedServer(){}
 public static boolean matches(Properties p,InetSocketAddress server,InetSocketAddress host,Object fingerprint){
  try{
   String key=p.getProperty("fingerprint","").trim();
   return key.matches("[0-9a-f]{64}")&&key.equals(fingerprint)&&server!=null&&host!=null
    &&!p.getProperty("serverHost","").isBlank()&&!p.getProperty("modpackHost","").isBlank()
    &&p.getProperty("serverHost").trim().equalsIgnoreCase(server.getHostString())
    &&Integer.parseInt(p.getProperty("serverPort",""))==server.getPort()
    &&p.getProperty("modpackHost").trim().equalsIgnoreCase(host.getHostString())
    &&Integer.parseInt(p.getProperty("modpackPort",""))==host.getPort()
    &&server.getPort()>0&&host.getPort()>0;
  }catch(RuntimeException e){return false;}
 }
 public static boolean trusted(Path game,Object addresses){
  try{
   Path file=game.resolve("config").resolve(FILE);if(!Files.isRegularFile(file)||Files.size(file)>65536)return false;
   Properties p=new Properties();try(var in=Files.newInputStream(file)){p.load(in);}
   var server=(InetSocketAddress)addresses.getClass().getField("serverAddress").get(addresses);
   var host=(InetSocketAddress)addresses.getClass().getField("hostAddress").get(addresses);
   Object known=Class.forName("pl.skidam.automodpack_core.GlobalVariables").getField("knownHosts").get(null);
   Object hosts=known.getClass().getField("hosts").get(known);
   Object fingerprint=hosts instanceof Map<?,?> map&&host!=null?map.get(host.getHostString()):null;
   return matches(p,server,host,fingerprint);
  }catch(ReflectiveOperationException|java.io.IOException|RuntimeException|LinkageError e){return false;}
 }
}
