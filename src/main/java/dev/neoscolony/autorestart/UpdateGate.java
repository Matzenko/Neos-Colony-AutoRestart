package dev.neoscolony.autorestart;
import java.nio.file.Path;
import java.util.*;
/** Only an explicit AutoModpack restart request after startUpdate can authorize a screen flow. */
public final class UpdateGate {
    public record Frame(String owner,String method){}
    public record Ticket(Path directory,String type,Object changes){}
    public record Request(Ticket ticket,Object screen){}
    private Ticket pending;
    private final Set<Object> consumed=Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<Object,Ticket> created=new IdentityHashMap<>();
    private final Map<Object,Ticket> openings=new IdentityHashMap<>();
    private Request ready;
    public static boolean successfulOrigin(List<Frame> frames){
        boolean relaunch=frames.stream().anyMatch(f->f.owner.equals("pl.skidam.automodpack_loader_core.ReLauncher")&&f.method.equals("handleClientRestart"));
        Optional<Frame> nearest=frames.stream().filter(f->f.owner.equals("pl.skidam.automodpack_loader_core.client.ModpackUpdater")).findFirst();
        return relaunch&&nearest.isPresent()&&nearest.get().method.equals("startUpdate");
    }
    public synchronized boolean observe(Object[] args,List<Frame> stack){
        if(args.length!=3||!(args[0] instanceof Path path)||!(args[1] instanceof Enum<?> type)||
           args[2]==null||!Set.of("FULL","UPDATE").contains(type.name())||!successfulOrigin(stack))return false;
        if(consumed.contains(args[2])||pending!=null)return false;
        pending=new Ticket(path.toAbsolutePath().normalize(),type.name(),args[2]);return true;
    }
    public synchronized Ticket associate(Path path,Object type,Object changes){
        if(pending==null||!(type instanceof Enum<?> e)||!pending.directory.equals(path.toAbsolutePath().normalize())||
            !pending.type.equals(e.name())||pending.changes!=changes)return null;
        Ticket result=pending;pending=null;consumed.add(changes);return result;
    }
    public synchronized boolean screenCreated(Object screen,Path path,Object type,Object changes){
        if(screen==null)return false;
        Ticket ticket=associate(path,type,changes);
        if(ticket==null)return false;
        created.put(screen,ticket);return true;
    }
    /** HIGHEST priority, before UI replacement; event and screen are matched by identity. */
    public synchronized boolean beginOpening(Object event,Object original){
        Ticket ticket=created.remove(original);
        if(ticket==null||event==null)return false;
        openings.put(event,ticket);return true;
    }
    /** LOWEST priority, after UI replacement; does not modify or cancel the event. */
    public synchronized boolean finishOpening(Object event,Object finalScreen,boolean cancelled){
        Ticket ticket=openings.remove(event);
        if(ticket==null)return false;
        if(cancelled||finalScreen==null){ready=null;return false;}
        ready=new Request(ticket,finalScreen);return true;
    }
    /** Called after setScreen has completed. Any intervening screen/world change consumes the request. */
    public synchronized Request take(Object currentScreen,boolean worldOpen){
        Request result=ready;ready=null;
        if(result==null||result.screen!=currentScreen||worldOpen)return null;
        return result;
    }
}
