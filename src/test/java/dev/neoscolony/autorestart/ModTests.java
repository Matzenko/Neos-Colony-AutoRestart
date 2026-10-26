package dev.neoscolony.autorestart;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
public final class ModTests{
    static int n;
    static void check(boolean b,String name){if(!b)throw new AssertionError(name);System.out.println("PASS "+(++n)+" "+name);}
    enum Type{FULL,UPDATE,SELECT,AUTOMODPACK}
    static List<UpdateGate.Frame> origin(String nearest){
        return List.of(new UpdateGate.Frame("pl.skidam.automodpack_loader_core.ReLauncher","handleClientRestart"),
            new UpdateGate.Frame("pl.skidam.automodpack_loader_core.client.ModpackUpdater",nearest),
            new UpdateGate.Frame("pl.skidam.automodpack_loader_core.client.ModpackUpdater","startUpdate"));
    }
    static ClassNode cls(Path jar,String name)throws Exception{
        try(JarFile j=new JarFile(jar.toFile())){
            ClassNode node=new ClassNode();new ClassReader(j.getInputStream(j.getJarEntry(name+".class"))).accept(node,0);return node;
        }
    }
    static MethodNode method(ClassNode c,String name,String desc){
        return c.methods.stream().filter(m->m.name.equals(name)&&(desc==null||m.desc.equals(desc))).findFirst().orElseThrow();
    }
    static boolean calls(MethodNode m,String owner,String name){
        for(var i:m.instructions)if(i instanceof MethodInsnNode call&&call.owner.equals(owner)&&call.name.equals(name))return true;
        return false;
    }
    static String field(String text,String key){
        var matcher=java.util.regex.Pattern.compile("(?m)^\\s*"+key+"\\s*=\\s*\"([^\"]*)\"").matcher(text);
        return matcher.find()?matcher.group(1):"";
    }

    public static void main(String[] args)throws Exception{
        Path own=Path.of(args[0]),outer=Path.of(args[1]),ui=Path.of(args[2]);

        String actualId,actualVersion;
        try(JarFile original=new JarFile(ui.toFile())){
            String toml=new String(original.getInputStream(original.getJarEntry("META-INF/neoforge.mods.toml")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            actualId=field(toml,"modId");actualVersion=field(toml,"version");
            check(actualId.equals("automodpack_mod")&&actualVersion.equals("4.0.6"),"actual nested NeoForge mod ID/version");
        }
        try(JarFile original=new JarFile(outer.toFile())){
            String toml=new String(original.getInputStream(original.getJarEntry("META-INF/neoforge.mods.toml")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            check(field(toml,"modId").equals("automodpack"),"outer loader ID differs from registered UI mod");
            check(original.getJarEntry("META-INF/services/net.neoforged.neoforgespi.locating.IDependencyLocator")!=null,"original custom dependency locator present");
        }
        check(calls(method(cls(outer,"pl/skidam/automodpack_loader_core/LazyModLocator"),"scanMods",null),
            "net/neoforged/fml/loading/moddiscovery/locators/JarInJarDependencyLocator","scanMods"),"lazy locator delegates nested mod discovery");
        ClassNode init=cls(ui,"pl/skidam/automodpack/init/NeoForgeInit");
        check(init.visibleAnnotations.stream().anyMatch(annotation->annotation.desc.equals("Lnet/neoforged/fml/common/Mod;")&&annotation.values.contains(actualId)),"NeoForge entrypoint annotation matches nested ID");
        try(JarFile built=new JarFile(own.toFile())){
            String toml=new String(built.getInputStream(built.getJarEntry("META-INF/neoforge.mods.toml")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            String dependency=Arrays.stream(toml.split("\\[\\[")).filter(block->block.startsWith("dependencies.")&&field(block,"modId").equals(actualId)).findFirst().orElseThrow();
            check(field(dependency,"type").equals("required")&&field(dependency,"versionRange").equals("["+actualVersion+"]"),"actual runtime mod dependency remains required and exact");
            check(!toml.contains("modId=\"automodpack\""),"no outer-loader dependency");
            MethodNode ctor=method(cls(own,"dev/neoscolony/autorestart/AutoRestartClient"),"<init>",null);
            List<Object> constants=new ArrayList<>();
            for(var instruction:ctor.instructions)if(instruction instanceof LdcInsnNode ldc)constants.add(ldc.cst);
            check(constants.contains(actualId)&&constants.contains(actualVersion)&&!constants.contains("automodpack"),"runtime compatibility gate checks registered ID/version");
        }

        Path dir=Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath();
        Object changes=new Object();
        for(String bad:List.of("CheckAndLoadModpack","prepareUpdate","start")){
            UpdateGate gate=new UpdateGate();
            check(!gate.observe(new Object[]{dir,Type.UPDATE,changes},origin(bad)),"reject origin "+bad);
        }
        for(Type bad:List.of(Type.SELECT,Type.AUTOMODPACK))
            check(!new UpdateGate().observe(new Object[]{dir,bad,changes},origin("startUpdate")),"reject type "+bad);
        check(!new UpdateGate().observe(new Object[]{dir,Type.UPDATE,changes},List.of()),"ordinary exit no ticket");
        for(Type good:List.of(Type.UPDATE,Type.FULL)){
            UpdateGate gate=new UpdateGate();
            check(gate.observe(new Object[]{dir,good,changes},origin("startUpdate")),"success "+good);
            check(gate.associate(dir,good,new Object())==null,"reject wrong changes identity");
            check(gate.associate(dir.resolve("other"),good,changes)==null,"reject wrong path");
            check(gate.associate(dir,good,changes)!=null,"associate exact success screen");
            check(gate.associate(dir,good,changes)==null,"screen once");
            check(!gate.observe(new Object[]{dir,good,changes},origin("startUpdate")),"duplicate success once");
        }
        String a="{\"list\":[{\"file\":\"/a\",\"sha1\":\"1\",\"size\":\"2\",\"type\":\"mod\"},{\"file\":\"/b\",\"sha1\":\"2\",\"size\":\"3\",\"type\":\"config\"}]}";
        String b="{\"list\":[{\"type\":\"config\",\"size\":\"3\",\"sha1\":\"2\",\"file\":\"/b\"},{\"size\":\"2\",\"sha1\":\"1\",\"type\":\"mod\",\"file\":\"/a\"}]}";
        String hash=UpdateFingerprint.json(a);
        check(hash.equals(UpdateFingerprint.json(b)),"fingerprint ignores JSON and set ordering");
        check(!UpdateFingerprint.allowed(hash,hash),"same update blocked across restart");
        check(UpdateFingerprint.allowed(UpdateFingerprint.json(a.replace("\"1\"","\"3\"")),hash),"new update allowed");
        try{UpdateFingerprint.json("{\"list\":[]}");throw new AssertionError();}catch(IllegalStateException expected){check(true,"empty metadata refused");}

        Path verifyDir=Files.createTempDirectory(dir,"metadata-");
        Path payload=verifyDir.resolve("test.dat");Files.writeString(payload,"content");
        String sha=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(payload)));
        String valid="{\"mcVersion\":\"1.21.1\",\"loader\":\"neoforge\",\"loaderVersion\":\"21.1.251\",\"list\":[{\"file\":\"/test.dat\",\"sha1\":\""+sha+"\",\"size\":\"7\",\"type\":\"config\"}]}";
        Path metadata=verifyDir.resolve("automodpack-content.json");
        Files.writeString(metadata,valid);
        check(UpdateFingerprint.from(verifyDir).matches("[a-f0-9]{64}"),"complete update file integrity accepted");
        Files.writeString(payload,"changed");
        try{UpdateFingerprint.from(verifyDir);throw new AssertionError();}catch(IllegalStateException expected){check(true,"changed update content refused");}
        Files.delete(payload);
        try{UpdateFingerprint.from(verifyDir);throw new AssertionError();}catch(IllegalStateException expected){check(true,"missing update content refused");}
        Files.writeString(payload,"content");Files.writeString(metadata,valid.replace("21.1.251","21.1.252"));
        try{UpdateFingerprint.from(verifyDir);throw new AssertionError();}catch(IllegalStateException expected){check(true,"loader update requires manual launcher");}
        Files.writeString(metadata,valid.replace("/test.dat","/../outside.dat"));
        try{UpdateFingerprint.from(verifyDir);throw new AssertionError();}catch(IllegalStateException expected){check(true,"metadata path traversal refused");}

        ClassNode screen=cls(ui,"pl/skidam/automodpack/client/ScreenImpl");
        check(method(screen,"restart","([Ljava/lang/Object;)V")!=null,"original ScreenImpl hook descriptor");
        ClassNode restart=cls(ui,"pl/skidam/automodpack/client/ui/RestartScreen");
        check(method(restart,"<init>","(Ljava/nio/file/Path;Lpl/skidam/automodpack_loader_core/utils/UpdateType;Lpl/skidam/automodpack_loader_core/client/Changelogs;)V")!=null,"original RestartScreen constructor descriptor");
        check(method(restart,"init","()V")!=null,"original RestartScreen init descriptor");
        ClassNode updater=cls(outer,"pl/skidam/automodpack_loader_core/client/ModpackUpdater");
        MethodNode update=method(updater,"startUpdate",null);
        boolean join=false,error=false,restartCall=false;
        for(var i:update.instructions)if(i instanceof MethodInsnNode call){
            join|=call.name.equals("joinAll");error|=call.name.equals("error");
            restartCall|=call.owner.endsWith("/ReLauncher")&&call.name.equals("restart");
        }
        check(join&&error&&restartCall,"installed updater has join/error/restart branches");
        check(updater.methods.stream().anyMatch(m->m.name.equals("CheckAndLoadModpack")),"second startup restart origin exists");
        try(JarFile j=new JarFile(own.toFile())){
            check(j.getEntry("META-INF/neoforge.mods.toml")!=null,"NeoForge metadata packaged");
            check(j.getEntry("neoscolony_autorestart.mixins.json")!=null,"Mixin config packaged");
            check(j.stream().noneMatch(e->e.getName().startsWith("pl/")||e.getName().startsWith("com/sun/")||e.getName().startsWith("net/")||e.getName().contains("ModTests")||e.getName().contains("Prototype")),"no foreign classes/prototype/tests bundled");
        }
        ClassNode client=cls(own,"dev/neoscolony/autorestart/AutoRestartClient");
        check(calls(method(client,"tick",null),"dev/neoscolony/autorestart/UpdateGate","take"),"client uses confirmed final opening screen");
        boolean commitBeforeStop=false,abortOnFailure=false;
        for(MethodNode m:client.methods){
            int commit=-1,stop=-1,index=0;
            for(var instruction:m.instructions){
                if(instruction instanceof MethodInsnNode call){
                    if(call.owner.endsWith("RestartEngine$Prepared")&&call.name.equals("commit"))commit=index;
                    if(call.owner.equals("net/minecraft/client/Minecraft")&&call.name.equals("stop"))stop=index;
                    if(call.owner.endsWith("RestartEngine$Prepared")&&call.name.equals("abort"))abortOnFailure=true;
                }
                index++;
            }
            if(stop>=0)commitBeforeStop=commit>=0&&commit<stop;
        }
        check(commitBeforeStop&&abortOnFailure,"Minecraft stop remains after successful helper commit with abort on failure");

        n+=TrustedServerTests.run();
        n+=ReleaseEquivalenceTests.run(own);
        n+=ServerSideTests.run(own);
        n+=DistributionTests.run(own,outer);
        n+=ScreenFlowTests.run(dir);
        if(args.length>3&&!args[3].isBlank())n+=GuiCompatibilityTests.run(Path.of(args[3]));
        n+=dev.neoscolony.autorestart.restart.EngineTests.run(own);
        System.out.println("ALL_PASSED="+n);
    }
}
