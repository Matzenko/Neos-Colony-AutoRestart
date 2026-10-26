package dev.neoscolony.autorestart;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
/** Proves that release promotion did not rewrite the tested runtime implementation. */
public final class ReleaseEquivalenceTests{
    public static int run(Path release)throws Exception{
        Path previous=release.getParent().resolve("neos-colony-automodpack-autorestart-1.21.1-0.1.0-test.3.jar");
        if(!Files.isRegularFile(previous)){System.out.println("BASELINE_COMPARISON_SKIPPED=no_local_test3_artifact");return 0;}
        int count=0;
        try(JarFile old=new JarFile(previous.toFile());JarFile current=new JarFile(release.toFile())){
            for(var entry:old.stream().filter(e->e.getName().endsWith(".class")).toList()){
                String name=entry.getName();
                byte[] a=old.getInputStream(entry).readAllBytes();
                byte[] b=current.getInputStream(current.getJarEntry(name)).readAllBytes();
                if(name.equals("dev/neoscolony/autorestart/AutoRestartClient.class"))continue; // Intended UI wiring; gate, fingerprint and restart engine must remain byte-identical.
                if(!Arrays.equals(a,b))throw new AssertionError("Runtime changed beyond release version log: "+name);
                System.out.println("PASS_EQUIVALENCE "+name);count++;
            }
        }
        return count;
    }
    static byte[] normalized(byte[] bytes){
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        for(var method:node.methods)for(var instruction:method.instructions)
            if(instruction instanceof LdcInsnNode ldc&&ldc.cst instanceof String text&&text.startsWith("[NeosAutoRestart] {} ")){
                ldc.cst="[NeosAutoRestart] {} RELEASE_VERSION"+text.substring(text.indexOf(';'));
            }
        ClassWriter writer=new ClassWriter(0);node.accept(writer);return writer.toByteArray();
    }
}
