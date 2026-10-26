package dev.neoscolony.autorestart;
import java.net.*;import java.util.*;
public final class TrustedServerTests {
 public static int run(){
  int n=0;Properties p=new Properties();p.setProperty("serverHost","private.example");p.setProperty("serverPort","25565");p.setProperty("modpackHost","pack.example");p.setProperty("modpackPort","30037");p.setProperty("fingerprint","a".repeat(64));
  var server=InetSocketAddress.createUnresolved("private.example",25565);var host=InetSocketAddress.createUnresolved("pack.example",30037);
  if(!TrustedServer.matches(p,server,host,"a".repeat(64)))throw new AssertionError("exact pin");n++;
  for(String key:List.of("serverHost","serverPort","modpackHost","modpackPort","fingerprint")){
   Properties bad=new Properties();bad.putAll(p);bad.setProperty(key,key.endsWith("Port")?"25566":"different");if(TrustedServer.matches(bad,server,host,"a".repeat(64)))throw new AssertionError("foreign "+key);n++;
   bad.remove(key);if(TrustedServer.matches(bad,server,host,"a".repeat(64)))throw new AssertionError("missing "+key);n++;
  }
  if(TrustedServer.matches(p,server,host,null)||TrustedServer.matches(p,server,host,"b".repeat(64))||TrustedServer.matches(p,null,host,"a".repeat(64))||TrustedServer.matches(new Properties(),server,host,"a".repeat(64)))throw new AssertionError("absent/rotated/disabled");n+=4;
  var upper=InetSocketAddress.createUnresolved("PRIVATE.EXAMPLE",25565);if(!TrustedServer.matches(p,upper,host,"a".repeat(64)))throw new AssertionError("hostname case");n++;
  System.out.println("PASS_TRUSTED_SERVER="+n);return n;
 }
}
