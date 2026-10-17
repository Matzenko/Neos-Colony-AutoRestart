package dev.neoscolony.autorestart.mixin;
import dev.neoscolony.autorestart.AutoRestartClient;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="pl.skidam.automodpack.client.ScreenImpl",remap=false)
public abstract class ScreenImplMixin{
    @Inject(method="restart([Ljava/lang/Object;)V",at=@At("HEAD"),require=1,remap=false)
    private void neos$observe(Object[] args,CallbackInfo ci){AutoRestartClient.observe(args);}
}
