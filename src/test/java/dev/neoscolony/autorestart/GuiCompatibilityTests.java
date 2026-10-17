package dev.neoscolony.autorestart;
import java.io.InputStream;
import java.nio.file.Path;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
public final class GuiCompatibilityTests {
    static int n;
    static void check(boolean b,String name){if(!b)throw new AssertionError(name);n++;System.out.println("PASS_GUI "+name);}
    public static int run(Path gui)throws Exception{
        n=0;
        ClassNode screens=ModTests.cls(gui,"de/neoscolony/gui/client/ClientScreens");
        MethodNode opening=ModTests.method(screens,"opening",null);
        check(ModTests.calls(opening,"net/neoforged/neoforge/client/event/ScreenEvent$Opening","setNewScreen"),"actual GUI replaces screen in opening event");
        check(opening.visibleAnnotations.stream().anyMatch(a->a.desc.equals("Lnet/neoforged/bus/api/SubscribeEvent;")&&(a.values==null||!a.values.contains("priority"))),"GUI listener uses default NORMAL priority between our HIGHEST and LOWEST");
        ClassNode replacement=ModTests.cls(gui,"de/neoscolony/gui/client/RestartPrompt");
        check(replacement.superName.equals("net/minecraft/client/gui/screens/Screen"),"GUI replacement is separate Screen not AutoModpack RestartScreen");
        check(replacement.fields.stream().anyMatch(f->f.name.equals("original")&&f.desc.equals("Lnet/minecraft/client/gui/screens/Screen;")),"GUI retains original screen without initializing it");
        try(InputStream in=GuiCompatibilityTests.class.getClassLoader().getResourceAsStream("net/minecraft/client/Minecraft.class")){
            ClassNode mc=new ClassNode();new ClassReader(in).accept(mc,0);
            MethodNode set=ModTests.method(mc,"setScreen","(Lnet/minecraft/client/gui/screens/Screen;)V");
            int post=-1,get=-1,assign=-1,init=-1,index=0;
            for(var ins:set.instructions){
                if(ins instanceof MethodInsnNode call){
                    if(call.owner.equals("net/neoforged/bus/api/IEventBus")&&call.name.equals("post")&&post<0)post=index;
                    if(call.owner.endsWith("ScreenEvent$Opening")&&call.name.equals("getNewScreen"))get=index;
                    if(call.owner.equals("net/minecraft/client/gui/screens/Screen")&&call.name.equals("init"))init=index;
                }
                if(ins instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.PUTFIELD&&field.name.equals("screen"))assign=index;
                index++;
            }
            check(post>=0&&get>post&&assign>get&&init>assign,"actual Minecraft applies event replacement before screen init");
        }
        return n;
    }
}
