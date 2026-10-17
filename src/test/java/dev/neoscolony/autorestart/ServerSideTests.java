package dev.neoscolony.autorestart;
import com.google.gson.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.lang.annotation.ElementType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
/** Exercises the installed FML side predicate; no Minecraft/server instance is launched. */
public final class ServerSideTests {
    static int n;
    static void check(boolean b,String text){if(!b)throw new AssertionError(text);n++;System.out.println("PASS_SIDE "+text);}
    public static int run(Path own)throws Exception{
        n=0;
        ClassNode client=ModTests.cls(own,"dev/neoscolony/autorestart/AutoRestartClient");
        AnnotationNode mod=client.visibleAnnotations.stream().filter(a->a.desc.equals("Lnet/neoforged/fml/common/Mod;")).findFirst().orElseThrow();
        Map<String,Object> data=new HashMap<>();
        for(int i=0;i<mod.values.size();i+=2)data.put((String)mod.values.get(i),mod.values.get(i+1));
        List<?> dist=(List<?>)data.get("dist");
        Class<?> holder=Class.forName("net.neoforged.fml.loading.modscan.ModAnnotation$EnumHolder");
        var constructor=holder.getDeclaredConstructor(String.class,String.class);
        List<Object> converted=new ArrayList<>();
        for(Object e:dist){String[] entry=(String[])e;converted.add(constructor.newInstance(entry[0],entry[1]));}
        data.put("dist",converted);
        Class<?> annotation=Class.forName("net.neoforged.neoforgespi.language.ModFileScanData$AnnotationData");
        Object scan=annotation.getConstructor(Type.class,ElementType.class,Type.class,String.class,Map.class)
            .newInstance(Type.getType(mod.desc),ElementType.TYPE,Type.getObjectType(client.name),"",data);
        Class<?> loader=Class.forName("net.neoforged.fml.loading.FMLLoader");
        Field side=loader.getDeclaredField("dist");side.setAccessible(true);
        Object previous=side.get(null);
        Class<?> sides=Class.forName("net.neoforged.api.distmarker.Dist");
        Method predicate=Class.forName("net.neoforged.fml.javafmlmod.FMLJavaModLanguageProvider")
            .getDeclaredMethod("lambda$loadMod$1",annotation);predicate.setAccessible(true);
        try{
            Object server=Arrays.stream(sides.getEnumConstants()).filter(e->e.toString().equals("DEDICATED_SERVER")).findFirst().orElseThrow();
            side.set(null,server);
            check(!(Boolean)predicate.invoke(null,scan),"actual FML rejects our client entrypoint on dedicated server");
            Object physicalClient=Arrays.stream(sides.getEnumConstants()).filter(e->e.toString().equals("CLIENT")).findFirst().orElseThrow();
            side.set(null,physicalClient);
            check((Boolean)predicate.invoke(null,scan),"actual FML accepts same entrypoint on client");
        }finally{side.set(null,previous);}
        try(JarFile jar=new JarFile(own.toFile())){
            JsonObject config=JsonParser.parseString(new String(jar.getInputStream(jar.getJarEntry("neoscolony_autorestart.mixins.json")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            check(config.getAsJsonArray("client").size()==2&&!config.has("mixins")&&!config.has("server"),"both mixins declared exclusively in client list");
            check(jar.stream().noneMatch(e->e.getName().startsWith("META-INF/services/")),"no server discovery or startup services in release");
        }
        return n;
    }
}
