package dev.neoscolony.probe;
import java.util.*;import java.nio.file.*;import java.net.*;
import dev.neoscolony.autorestart.*;
import net.minecraft.client.Minecraft;import net.minecraft.client.gui.components.Button;
import net.neoforged.fml.common.Mod;import net.neoforged.neoforge.common.NeoForge;import net.neoforged.neoforge.client.event.ClientTickEvent;
import pl.skidam.automodpack.client.ui.*;import pl.skidam.automodpack_core.config.Jsons;import pl.skidam.automodpack_loader_core.client.*;
@Mod("dialogprobe")
public final class DialogProbe {
 int ticks;boolean started;Object fallback;
 public DialogProbe(){NeoForge.EVENT_BUS.addListener(this::tick);}
 public static class RecordingUpdater extends ModpackUpdater {
  public Jsons.ModpackAddresses modpackAddresses;int calls;
  public RecordingUpdater(){super(null,null,null,null);}
  @Override public void startUpdate(Set<Jsons.ModpackContentFields.ModpackContentItem> files){calls++;}
  @Override public Set<Jsons.ModpackContentFields.ModpackContentItem> getUnverifiedJarFiles(){var item=new Jsons.ModpackContentFields.ModpackContentItem("/mods/unverified.jar","1","mod",false,false,"a".repeat(40),"");return Set.of(item);}
 }
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);System.out.println("PASS_REAL_CLIENT "+message);}
 void tick(ClientTickEvent.Post event){
  var mc=Minecraft.getInstance();if(++ticks<100||mc.getOverlay()!=null)return;
  try{
   if(!started){
    started=true;var game=mc.gameDirectory.toPath();Files.createDirectories(game.resolve("config"));
    Properties pin=new Properties();pin.setProperty("serverHost","fixture.invalid");pin.setProperty("serverPort","25565");pin.setProperty("modpackHost","fixture.invalid");pin.setProperty("modpackPort","30037");pin.setProperty("fingerprint","a".repeat(64));
    try(var out=Files.newOutputStream(game.resolve("config").resolve(TrustedServer.FILE))){pin.store(out,"isolated synthetic fixture");}
    var globals=Class.forName("pl.skidam.automodpack_core.GlobalVariables");var knownField=globals.getField("knownHosts");Object known=knownField.get(null);if(known==null){known=knownField.getType().getConstructor().newInstance();knownField.set(null,known);}var hostsField=known.getClass().getField("hosts");var hosts=(Map<String,String>)hostsField.get(known);if(hosts==null){hosts=new HashMap<>();hostsField.set(known,hosts);}hosts.put("fixture.invalid","a".repeat(64));
    var addresses=new Jsons.ModpackAddresses();addresses.serverAddress=InetSocketAddress.createUnresolved("fixture.invalid",25565);addresses.hostAddress=InetSocketAddress.createUnresolved("fixture.invalid",30037);
    var f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);var unsafe=(sun.misc.Unsafe)f.get(null);var updater=(RecordingUpdater)unsafe.allocateInstance(RecordingUpdater.class);updater.modpackAddresses=addresses;
    var danger=new DangerScreen(mc.screen,updater,Set.of());mc.setScreen(danger);
    check(mc.screen.getClass().getName().endsWith("TrustedDialogs$Download"),"trusted first prompt only");
    var buttons=mc.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).toList();
    check(buttons.size()==2&&buttons.stream().anyMatch(b->b.getMessage().getString().equals("Zurück")),"first prompt retains exactly two actions");
    buttons.stream().filter(b->b.getMessage().getString().equals("JARs herunterladen")).findFirst().orElseThrow().onPress();
    Thread.sleep(100);check(updater.calls==1,"one click directly delegates original updater entry without acknowledgement");
    addresses.hostAddress=InetSocketAddress.createUnresolved("foreign.invalid",30037);var foreign=new DangerScreen(null,updater,Set.of());mc.setScreen(foreign);check(mc.screen==foreign,"foreign server retains native security dialog");var timer=DangerScreen.class.getDeclaredField("ticksRemaining");timer.setAccessible(true);check(timer.getInt(foreign)==200,"foreign unverified JAR retains full native acknowledgement timer");
    addresses.hostAddress=InetSocketAddress.createUnresolved("fixture.invalid",30037);
    Path dir=game.resolve("fixture-pack");Files.createDirectories(dir);Files.writeString(dir.resolve("automodpack-content.json"),"{\"modpackName\":\"fixture\"}");
    var clientField=globals.getField("clientConfig");Object client=clientField.get(null);if(client==null){client=clientField.getType().getConstructor().newInstance();clientField.set(null,client);}var installedField=client.getClass().getField("installedModpacks");var installed=(Map)installedField.get(client);if(installed==null){installed=new HashMap();installedField.set(client,installed);}installed.put("fixture",addresses);
    var startup=new RestartScreen(dir,pl.skidam.automodpack_loader_core.utils.UpdateType.UPDATE,new Changelogs());mc.setScreen(startup);check(mc.screen==startup,"restart without completed-update ticket keeps manual UI");var changes=new Changelogs();var gateField=AutoRestartClient.class.getDeclaredField("GATE");gateField.setAccessible(true);var gate=(UpdateGate)gateField.get(null);
    check(gate.observe(new Object[]{dir,pl.skidam.automodpack_loader_core.utils.UpdateType.UPDATE,changes},List.of(new UpdateGate.Frame("pl.skidam.automodpack_loader_core.ReLauncher","handleClientRestart"),new UpdateGate.Frame("pl.skidam.automodpack_loader_core.client.ModpackUpdater","startUpdate"))),"synthetic success ticket inserted for UI-only test");
    var restart=new RestartScreen(dir,pl.skidam.automodpack_loader_core.utils.UpdateType.UPDATE,changes);mc.setScreen(restart);
    check(mc.screen.getClass().getName().endsWith("TrustedDialogs$Progress")&&mc.screen.children().isEmpty(),"trusted successful restart opens no confirmation controls");fallback=mc.screen;
    // Deliberately invalid file manifest: existing integrity check must refuse and restore manual UI.
   }else if(ticks>200){
    check(mc.screen!=fallback,"failed integrity/preparation restores fallback instead of restarting");
    System.out.println("REAL_CLIENT_DIALOG_TEST_OK no_server_connection no_real_download synthetic_success_ticket");mc.stop();
   }
  }catch(Throwable e){e.printStackTrace();System.out.println("REAL_CLIENT_DIALOG_TEST_FAILED");mc.stop();}
 }
}
