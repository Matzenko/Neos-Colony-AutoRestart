package dev.neoscolony.autorestart;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
/** Runs only AutoModpack core in a temporary working directory, never a real server. */
public final class DistributionHarness {
    static int checks;
    static void check(boolean b,String text){if(!b)throw new AssertionError(text);checks++;System.out.println("PASS_DISTRIBUTION "+text);}
    static final class CoreLoader extends URLClassLoader {
        int forbiddenAttempts;
        CoreLoader(URL[] urls){super(urls,ClassLoader.getPlatformClassLoader());}
        @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
            if(name.startsWith("net.minecraft.")||name.startsWith("dev.neoscolony.autorestart.")||name.startsWith("de.neoscolony.gui.")){
                forbiddenAttempts++;throw new ClassNotFoundException("Game/mod entrypoints forbidden in distribution fixture");
            }
            return super.loadClass(name,resolve);
        }
    }
    static void set(Class<?> globals,String field,Object value)throws Exception{globals.getField(field).set(null,value);}
    public static void main(String[] args)throws Exception{
        Path own=Path.of(args[0]),auto=Path.of(args[1]),cwd=Path.of("").toAbsolutePath();
        List<URL> urls=new ArrayList<>();urls.add(auto.toUri().toURL());
        for(String path:System.getProperty("java.class.path").split(java.io.File.pathSeparator))urls.add(Path.of(path).toUri().toURL());
        try(CoreLoader loader=new CoreLoader(urls.toArray(URL[]::new))){
            Class<?> globals=Class.forName("pl.skidam.automodpack_core.GlobalVariables",true,loader);
            set(globals,"LOADER","neoforge");set(globals,"LOADER_VERSION","21.1.251");
            set(globals,"AM_VERSION","4.0.6");set(globals,"MC_VERSION","1.21.1");
            Class<?> config=Class.forName("pl.skidam.automodpack_core.config.Jsons$ServerConfigFieldsV2",true,loader);
            Object settings=config.getConstructor().newInstance();
            config.getField("modpackName").set(settings,"distribution-fixture");
            config.getField("modpackHost").setBoolean(settings,true);
            config.getField("generateModpackOnStart").setBoolean(settings,true);
            config.getField("syncedFiles").set(settings,Set.of("/mods/*.jar"));
            set(globals,"serverConfig",settings);
            Path content=(Path)globals.getField("hostContentModpackDir").get(null);
            check(content.normalize().equals(Path.of("automodpack","host-modpack","main")),"original 4.0.6 distribution root confirmed");
            Path delivered=content.resolve("mods").resolve(own.getFileName());
            Files.createDirectories(delivered.getParent());Files.copy(own,delivered);
            Files.createDirectories(cwd.resolve("mods"));
            check(Files.list(cwd.resolve("mods")).findAny().isEmpty(),"server mods directory remains empty");
            Class<?> inspect=Class.forName("pl.skidam.automodpack_core.utils.FileInspection",true,loader);
            check((Boolean)inspect.getMethod("isMod",Path.class).invoke(null,delivered),"original inspection recognizes release JAR as mod");
            check("neoscolony_autorestart".equals(inspect.getMethod("getModID",Path.class).invoke(null,delivered)),"original inspection reads correct mod ID");
            Class<?> executorType=Class.forName("pl.skidam.automodpack_core.modpack.ModpackExecutor",true,loader);
            Object executor=executorType.getConstructor().newInstance();
            try{
                check((Boolean)executorType.getMethod("generateNew").invoke(executor),"original modpack generation succeeds from separate host folder");
                Path manifest=(Path)globals.getField("hostModpackContentFile").get(null);
                String json=Files.readString(manifest);
                check(json.contains("/mods/"+own.getFileName()),"generated client manifest contains release destination /mods");
                check(!json.contains("host-modpack/main/mods"),"host source directory does not leak into client destination");
                check(Files.list(cwd.resolve("mods")).findAny().isEmpty(),"generation did not install release as server mod");
                check(loader.forbiddenAttempts==0,"no Minecraft, AutoRestart or GUI entrypoint loaded by distribution core");
            }finally{
                executorType.getMethod("stop").invoke(executor);
                ThreadPoolExecutor pool=(ThreadPoolExecutor)executorType.getMethod("getExecutor").invoke(executor);
                check(pool.awaitTermination(10,TimeUnit.SECONDS),"isolated generator threads stopped");
            }
        }
        System.out.println("DISTRIBUTION_ASSERTIONS_PASSED="+checks);
    }
}
