package dev.neoscolony.autorestart;
import java.nio.file.Path;
import java.util.*;
/** Production gate exercised with identity objects; no fake Minecraft launch. */
public final class ScreenFlowTests {
    static int n;
    static void check(boolean value,String name){if(!value)throw new AssertionError(name);n++;System.out.println("PASS_FLOW "+name);}
    static UpdateGate success(Path dir,Object changes){
        UpdateGate gate=new UpdateGate();
        check(gate.observe(new Object[]{dir,ModTests.Type.UPDATE,changes},ModTests.origin("startUpdate")),"explicit restart request after successful update");
        return gate;
    }
    public static int run(Path dir)throws Exception{
        n=0;
        Object original=new Object(),replacement=new Object(),event=new Object(),changes=new Object();
        UpdateGate empty=new UpdateGate();
        check(!empty.screenCreated(original,dir,ModTests.Type.UPDATE,changes),"screen without completed update not authorized");
        check(!empty.beginOpening(event,original),"opening without success/restart request ignored");
        check(!empty.finishOpening(event,replacement,false),"replacement alone cannot authorize restart");
        check(empty.take(null,false)==null,"normal game exit does nothing");
        check(empty.take(replacement,false)==null,"successful update alone without actual restart callback does nothing");

        for(boolean gui:List.of(false,true)){
            changes=new Object();original=new Object();replacement=gui?new Object():original;event=new Object();
            UpdateGate gate=success(dir,changes);
            check(gate.screenCreated(original,dir,ModTests.Type.UPDATE,changes),"constructor associates "+(gui?"GUI":"original"));
            check(gate.take(original,false)==null,"no restart before opening is confirmed");
            check(gate.beginOpening(event,original),"capture original before replacement");
            check(!gate.finishOpening(new Object(),replacement,false),"unrelated event rejected");
            check(gate.finishOpening(event,replacement,false),"confirm same event final screen");
            check(gate.take(original==replacement?new Object():original,false)==null,"wrong active screen consumes authorization");
            check(gate.take(replacement,false)==null,"consumed authorization cannot resurrect");

            // Repeat with a fresh update, now really display the final screen.
            changes=new Object();gate=success(dir,changes);
            check(gate.screenCreated(original,dir,ModTests.Type.UPDATE,changes),"new update constructor accepted");
            check(gate.beginOpening(event,original)&&gate.finishOpening(event,replacement,false),"same opening completed");
            UpdateGate.Request request=gate.take(replacement,false);
            check(request!=null&&request.screen()==replacement&&request.ticket().changes()==changes,
                gui?"GUI replacement authorizes exactly same update without original init":"original screen authorizes restart");
            check(gate.take(replacement,false)==null,"one preparation attempt per request");
            check(!gate.observe(new Object[]{dir,ModTests.Type.UPDATE,changes},ModTests.origin("startUpdate")),"same update cannot retry after helper failure");
            check(gate.take(null,false)==null,"later ordinary shutdown does not arm restart");
        }
        for(String cancelled:List.of("cancel_event","null_screen","world_open")){
            changes=new Object();UpdateGate gate=success(dir,changes);
            gate.screenCreated(original,dir,ModTests.Type.UPDATE,changes);
            gate.beginOpening(event,original);
            boolean accepted=gate.finishOpening(event,cancelled.equals("null_screen")?null:replacement,cancelled.equals("cancel_event"));
            check(accepted==cancelled.equals("world_open"),cancelled+" event disposition");
            check(gate.take(replacement,cancelled.equals("world_open"))==null,cancelled+" no restart");
        }
        changes=new Object();UpdateGate startup=new UpdateGate();
        check(!startup.observe(new Object[]{dir,ModTests.Type.UPDATE,changes},ModTests.origin("CheckAndLoadModpack")),"new game startup cannot arm restart");
        check(!startup.screenCreated(original,dir,ModTests.Type.UPDATE,changes),"startup restart screen without completed update ignored");
        return n;
    }
}
