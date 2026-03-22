package de.redjulu.antidrop.mixin;

import de.redjulu.antidrop.Antidrop;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerEntity.class)
public class DropMixin {

    @Unique
    private long lastDropAttempt = 0;

    @Unique
    private int lastSlot = -1;

    @Inject(method = "dropSelectedItem", at = @At("HEAD"), cancellable = true)
    private void onDropSelectedItem(boolean entireStack, CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        ItemStack stack = client.player.getMainHandStack();
        int currentSlot = client.player.getInventory().getSelectedSlot();

        if (Antidrop.getInstance().isProtected(stack)) {
            long currentTime = System.currentTimeMillis();

            if (currentSlot == lastSlot && (currentTime - lastDropAttempt) < 3000) {
                lastDropAttempt = 0;
                return;
            }

            lastDropAttempt = currentTime;
            lastSlot = currentSlot;

            String dropKey = client.options.dropKey.getBoundKeyLocalizedText().getString();

            client.inGameHud.setTitle(Text.literal("§c❌ §eItem Gesperrt!"));
            client.inGameHud.setSubtitle(Text.literal("§7§oDrücke §6§o'" + dropKey + "' §7§ozum Bestätigen"));

            client.player.playSound(SoundEvents.ENTITY_VILLAGER_NO, 0.5f, 1.0f);

            cir.setReturnValue(false);
        }
    }
}