package dev.neoscolony.autorestart;
import java.io.*;
import java.net.*;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.service.*;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.launch.platform.container.*;
/** Test-only bytecode environment: never loads or starts Minecraft. */
public final class OfflineMixinService extends MixinServiceAbstract implements IClassProvider,IClassBytecodeProvider,ITransformerProvider{
    public String getName(){return "NeosOfflineTest";}
    public boolean isValid(){return true;}
    public MixinEnvironment.Phase getInitialPhase(){return MixinEnvironment.Phase.DEFAULT;}
    public IClassProvider getClassProvider(){return this;}
    public IClassBytecodeProvider getBytecodeProvider(){return this;}
    public ITransformerProvider getTransformerProvider(){return this;}
    public IClassTracker getClassTracker(){return null;}
    public IMixinAuditTrail getAuditTrail(){return null;}
    public Collection<String> getPlatformAgents(){return List.of();}
    public IContainerHandle getPrimaryContainer(){return new ContainerHandleVirtual("test");}
    public InputStream getResourceAsStream(String name){return getClass().getClassLoader().getResourceAsStream(name);}
    public URL[] getClassPath(){return new URL[0];}
    public Class<?> findClass(String n)throws ClassNotFoundException{return findClass(n,false);}
    public Class<?> findClass(String n,boolean init)throws ClassNotFoundException{return Class.forName(n,init,getClass().getClassLoader());}
    public Class<?> findAgentClass(String n,boolean init)throws ClassNotFoundException{return findClass(n,init);}
    public ClassNode getClassNode(String n)throws ClassNotFoundException,IOException{return getClassNode(n,false,0);}
    public ClassNode getClassNode(String n,boolean t)throws ClassNotFoundException,IOException{return getClassNode(n,t,0);}
    public ClassNode getClassNode(String n,boolean t,int flags)throws ClassNotFoundException,IOException{
        try(InputStream in=getResourceAsStream(n.replace('.','/')+".class")){
            if(in==null)throw new ClassNotFoundException(n);
            ClassNode c=new ClassNode();new ClassReader(in).accept(c,flags);return c;
        }
    }
    public Collection<ITransformer> getTransformers(){return List.of();}
    public Collection<ITransformer> getDelegatedTransformers(){return List.of();}
    public void addTransformerExclusion(String n){}
}
