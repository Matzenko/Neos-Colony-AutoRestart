package dev.neoscolony.autorestart.mixin;
import dev.neoscolony.autorestart.AutoRestartClient;
import java.nio.file.Path;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="pl.skidam.automodpack.client.ui.RestartScreen",remap=false)
public abstract class RestartScreenMixin{
    @Inject(method="<init>(Ljava/nio/file/Path;Lpl/skidam/automodpack_loader_core/utils/UpdateType;Lpl/skidam/automodpack_loader_core/client/Changelogs;)V",
        at=@At("RETURN"),require=1,remap=false)
    private void neos$created(Path path,@Coerce Object type,@Coerce Object changes,CallbackInfo ci){
        AutoRestartClient.screen(this,path,type,changes);
    }
}
