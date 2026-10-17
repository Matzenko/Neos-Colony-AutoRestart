package dev.neoscolony.autorestart;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.transformer.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
public final class OfflineMixinTest{
    public static void main(String[] args)throws Exception{
        MixinBootstrap.init();
        var env=MixinEnvironment.getDefaultEnvironment();
        env.setSide(MixinEnvironment.Side.CLIENT);
        Mixins.addConfiguration("neoscolony_autorestart.mixins.json");
        Class<?> type=Class.forName("org.spongepowered.asm.mixin.transformer.MixinTransformer");
        var ctor=type.getDeclaredConstructor();ctor.setAccessible(true);
        IMixinTransformer transformer=(IMixinTransformer)ctor.newInstance();
        for(String name:new String[]{"pl.skidam.automodpack.client.ScreenImpl","pl.skidam.automodpack.client.ui.RestartScreen"}){
            byte[] original=OfflineMixinTest.class.getClassLoader().getResourceAsStream(name.replace('.','/')+".class").readAllBytes();
            byte[] transformed=transformer.transformClass(env,name,original);
            ClassNode node=new ClassNode();new ClassReader(transformed).accept(node,0);
            long count=node.methods.stream().filter(m->m.name.contains("neos$")).count();
            int expected=1;
            if(count!=expected)throw new AssertionError("Mixin hook count "+name+" expected="+expected+" actual="+count);
            for(MethodNode m:node.methods)if(m.name.contains("neos$"))
                if(!ModTests.calls(m,"dev/neoscolony/autorestart/AutoRestartClient",
                    name.endsWith("ScreenImpl")?"observe":"screen"))
                    throw new AssertionError("Missing callback "+m.name);
            System.out.println("MIXIN_TRANSFORM_OK="+name+" hooks="+count);
        }
        System.out.println("OFFLINE_MIXINS_PASSED=2 (not an in-game classloading proof)");
    }
}
