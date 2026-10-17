package dev.neoscolony.autorestart;
import dev.neoscolony.autorestart.restart.RestartEngine;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModList;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
@Mod(value=AutoRestartClient.ID,dist=Dist.CLIENT)
public final class AutoRestartClient{
    public static final String ID="neoscolony_autorestart";
    private static final Logger LOG=LoggerFactory.getLogger(ID);
    private static final UpdateGate GATE=new UpdateGate();
    private static volatile boolean enabled;
    private static boolean busy;
    public AutoRestartClient(){
        enabled=version("automodpack_mod","4.0.6")&&version("neoforge","21.1.251")&&version("minecraft","1.21.1");
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,true,ScreenEvent.Opening.class,AutoRestartClient::openingBefore);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,true,ScreenEvent.Opening.class,AutoRestartClient::openingAfter);
        NeoForge.EVENT_BUS.addListener(AutoRestartClient::tick);
        LOG.info("[NeosAutoRestart] {} Version 1.0.0; explicit restart request + opening event required.",enabled?"ACTIVE":"DISABLED_VERSION");
    }
    private static boolean version(String id,String exact){
        return ModList.get().getModContainerById(id).map(c->c.getModInfo().getVersion().toString().equals(exact)).orElse(false);
    }
    public static void observe(Object[] args){
        if(!enabled)return;
        try{
            List<UpdateGate.Frame> frames=StackWalker.getInstance().walk(s->s.map(f->new UpdateGate.Frame(f.getClassName(),f.getMethodName())).toList());
            if(GATE.observe(args,frames))LOG.info("[NeosAutoRestart] UPDATE_SUCCESS_OBSERVED; RESTART_REQUEST_OBSERVED");
            else LOG.info("[NeosAutoRestart] RESTART_REQUEST_IGNORED origin_type_or_duplicate");
        }catch(Throwable e){failure("OBSERVE",e);}
    }
    public static void screen(Object screen,Path path,Object type,Object changes){
        if(!enabled)return;
        try{
            if(GATE.screenCreated(screen,path,type,changes))LOG.info("[NeosAutoRestart] RESTART_SCREEN_ASSOCIATED");
            else LOG.info("[NeosAutoRestart] RESTART_SCREEN_UNMATCHED no_update_ticket");
        }catch(Throwable e){failure("SCREEN",e);}
    }
    private static void openingBefore(ScreenEvent.Opening event){
        if(!enabled)return;
        try{
            if(GATE.beginOpening(event,event.getNewScreen()))LOG.info("[NeosAutoRestart] RESTART_OPENING_CAPTURED");
        }catch(Throwable e){failure("OPENING_BEFORE",e);}
    }
    private static void openingAfter(ScreenEvent.Opening event){
        if(!enabled)return;
        try{
            Object screen=event.getNewScreen();
            if(GATE.finishOpening(event,screen,event.isCanceled()))
                LOG.info("[NeosAutoRestart] RESTART_REQUEST_CONFIRMED target={}",screen.getClass().getName());
        }catch(Throwable e){failure("OPENING_AFTER",e);}
    }
    private static synchronized void tick(ClientTickEvent.Post event){
        if(!enabled||busy)return;
        Minecraft mc=Minecraft.getInstance();
        UpdateGate.Request request=GATE.take(mc.screen,mc.level!=null||mc.hasSingleplayerServer());
        if(request==null)return;
        busy=true;
        LOG.info("[NeosAutoRestart] PREPARING target={}; manual controls remain available.",request.screen().getClass().getName());
        CompletableFuture.supplyAsync(()->{
            try{
                String fingerprint=UpdateFingerprint.from(request.ticket().directory());
                if(!UpdateFingerprint.allowed(fingerprint,System.getProperty(RestartEngine.MARKER,""))){
                    LOG.warn("[NeosAutoRestart] MANUAL_RESTART_REQUIRED code=REPEATED_UPDATE_BLOCKED");
                    return null;
                }
                LOG.info("[NeosAutoRestart] UPDATE_FILES_VERIFIED; HELPER_PREPARE_BEGIN");
                RestartEngine.Prepared prepared=RestartEngine.prepare(fingerprint,mc.gameDirectory.toPath());
                LOG.info("[NeosAutoRestart] HELPER_READY");
                return prepared;
            }catch(Throwable e){failure("PREPARE",e);return null;}
        }).thenAccept(prepared->mc.execute(()->{
            synchronized(AutoRestartClient.class){
                busy=false;
                if(prepared==null)return;
                if(mc.screen!=request.screen()||mc.level!=null||mc.hasSingleplayerServer()){
                    prepared.close();LOG.info("[NeosAutoRestart] CANCELLED_SCREEN_CHANGED");return;
                }
                try{
                    prepared.commit();
                    LOG.info("[NeosAutoRestart] HELPER_COMMITTED; closing Minecraft normally; one restart attempt.");
                    mc.stop();
                }catch(Throwable e){prepared.abort();failure("COMMIT",e);}
                finally{prepared.close();}
            }
        }));
    }
    private static void failure(String stage,Throwable e){
        // Never log exception messages/stack: native/launcher errors can contain credentials.
        LOG.warn("[NeosAutoRestart] MANUAL_RESTART_REQUIRED stage={} error={}",stage,e.getClass().getSimpleName());
    }
}
