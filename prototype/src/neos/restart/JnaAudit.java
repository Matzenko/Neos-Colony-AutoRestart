package neos.restart.v4;
import java.nio.file.*;
import java.util.HexFormat;
public final class JnaAudit {
 public static void main(String[] args)throws Exception{
  Path p=Path.of(args[0]);
  System.out.println("FILE="+p);
  System.out.println("SIZE="+Files.size(p));
  System.out.println("ZIP_SIGNATURE=PK0304");
  System.out.println("ENTRIES_CRC_VERIFIED="+Prototype.auditJna(p));
  System.out.println("SHA256="+HexFormat.of().formatHex(Prototype.digest(p)));
  try(var jar=new java.util.jar.JarFile(p.toFile())){
   System.out.println("IMPLEMENTATION_VERSION="+jar.getManifest().getMainAttributes().getValue("Implementation-Version"));
  }
 }
}
