package dev.optimizer30000.mixin;

import dev.optimizer30000.Optimizer30000Mod;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.ExecutorService;

/**
 * Ограничивает main worker executor.
 *
 * Для старого i5-2520M это предотвращает ситуацию, когда worldgen/IO работа
 * начинает конкурировать с main thread и render thread за оба физических ядра.
 */
@Mixin(Util.class)
public abstract class UtilMainWorkerBudgetMixin {
    @Inject(method = "getMainWorkerExecutor", at = @At("RETURN"), cancellable = true)
    private static void optimizer30000$limitMainWorkerExecutor(CallbackInfoReturnable<ExecutorService> cir) {
        cir.setReturnValue(Optimizer30000Mod.threadBudgetGovernor().limitMainWorkerExecutor(cir.getReturnValue()));
    }
}
