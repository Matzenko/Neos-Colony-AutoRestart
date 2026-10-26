package dev.neoscolony.autorestart;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;
/** Changes only trusted UI transitions. Original updater and restart gate/engine remain authoritative. */
public final class TrustedDialogs {
 private static final Map<Object,Screen> ORIGINALS=new IdentityHashMap<>();
 private static final Set<Object> AUTHORIZED=Collections.newSetFromMap(new IdentityHashMap<>());
 public static void restartAuthorized(Object event){AUTHORIZED.add(event);}
 private TrustedDialogs(){}
 static Object field(Object object,String name)throws ReflectiveOperationException{
  var f=object.getClass().getDeclaredField(name);if(!f.trySetAccessible())throw new IllegalAccessException();return f.get(object);
 }
 public static void before(ScreenEvent.Opening event){
  Screen screen=event.getNewScreen();if(screen==null)return;
  if(screen.getClass().getName().equals("pl.skidam.automodpack.client.ui.DangerScreen")
    ||screen.getClass().getName().equals("pl.skidam.automodpack.client.ui.RestartScreen"))ORIGINALS.put(event,screen);
 }
 public static void after(ScreenEvent.Opening event){
  Screen original=ORIGINALS.remove(event);boolean authorized=AUTHORIZED.remove(event);if(original==null||event.isCanceled()||event.getNewScreen()==null)return;
  Minecraft mc=Minecraft.getInstance();
  try{
   if(original.getClass().getName().endsWith(".DangerScreen")){
    Object updater=field(original,"modpackUpdaterInstance");
    if(!TrustedServer.trusted(mc.gameDirectory.toPath(),field(updater,"modpackAddresses")))return;
    Object items=field(original,"filesToUpdate");if(!(items instanceof Collection<?> collection))return;
    event.setNewScreen(new Download(original,(Screen)field(original,"parent"),updater,Set.copyOf(collection)));
   }else{
    if(!authorized)return;
    Path dir=(Path)field(original,"modpackDir");if(dir==null)return;
    Class<?> globals=Class.forName("pl.skidam.automodpack_core.GlobalVariables");
    Object client=globals.getField("clientConfig").get(null);
    Object installed=client.getClass().getField("installedModpacks").get(client);
    Object manifest=Class.forName("pl.skidam.automodpack_core.config.ConfigTools").getMethod("loadModpackContent",Path.class).invoke(null,dir.resolve("automodpack-content.json"));
    if(manifest==null||!(installed instanceof Map<?,?> map))return;
    String name=(String)manifest.getClass().getField("modpackName").get(manifest);
    if(!TrustedServer.trusted(mc.gameDirectory.toPath(),map.get(name)))return;
    // Only the existing success gate can authorize preparation on this non-interactive screen.
    event.setNewScreen(new Progress(event.getNewScreen()));
   }
  }catch(ReflectiveOperationException|RuntimeException|LinkageError e){/* Retain original UI, no trust broadening. */}
 }
 public static void failedPreparation(Minecraft mc,Object screen){
  if(screen instanceof Progress progress&&mc.screen==screen)mc.setScreen(progress.fallback);
 }
 static final class Download extends Screen {
  final Screen original,parent;final Object updater;final Set<?> files;boolean clicked;
  Download(Screen original,Screen parent,Object updater,Set<?> files){super(Component.literal("Modpack-Update"));this.original=original;this.parent=parent;this.updater=updater;this.files=files;}
  protected void init(){
   addRenderableWidget(Button.builder(Component.literal("Zurück"),b->minecraft.setScreen(parent)).bounds(width/2-145,height/2+15,140,20).build());
   addRenderableWidget(Button.builder(Component.literal("JARs herunterladen"),b->{
    try{if(clicked)return;if(!TrustedServer.trusted(minecraft.gameDirectory.toPath(),field(updater,"modpackAddresses"))){minecraft.setScreen(original);return;}
     clicked=true;b.active=false;Util.backgroundExecutor().execute(()->{
      try{updater.getClass().getMethod("startUpdate",Set.class).invoke(updater,files);}
      catch(ReflectiveOperationException|RuntimeException|LinkageError e){minecraft.execute(()->minecraft.setScreen(original));}
     });
    }catch(ReflectiveOperationException|RuntimeException|LinkageError e){minecraft.setScreen(original);}
   }).bounds(width/2+5,height/2+15,140,20).build());
  }
  public void render(GuiGraphics g,int x,int y,float d){super.render(g,x,y,d);g.drawCenteredString(font,title,width/2,height/2-20,0xffffff);}
  public boolean shouldCloseOnEsc(){return false;}
 }
 static final class Progress extends Screen {
  final Screen fallback;
  Progress(Screen fallback){super(Component.literal("Minecraft wird neu gestartet ..."));this.fallback=fallback;}
  public void render(GuiGraphics g,int x,int y,float d){super.render(g,x,y,d);g.drawCenteredString(font,title,width/2,height/2,0xffffff);}
  public boolean shouldCloseOnEsc(){return false;}
 }
}
