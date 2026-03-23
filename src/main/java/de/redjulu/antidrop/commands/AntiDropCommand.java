package de.redjulu.antidrop.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import de.redjulu.antidrop.Antidrop;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class AntiDropCommand {
    private static ItemStack lastRemovedItem = null;

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    ClientCommandManager.literal("antidrop")
                            .executes(context -> {
                                context.getSource().sendFeedback(Text.literal("§8[§6AntiDrop§8] §7Nutze: §eadd§7, §elist§7, §eremove§7, §eclear"));
                                playCenteredSound(SoundEvents.UI_BUTTON_CLICK, 0.5f, 1.5f);
                                return Command.SINGLE_SUCCESS;
                            })
                            .then(ClientCommandManager.literal("add").executes(context -> {
                                MinecraftClient client = MinecraftClient.getInstance();
                                if (client.player == null) return Command.SINGLE_SUCCESS;
                                ItemStack item = client.player.getMainHandStack();
                                if (item.isEmpty()) {
                                    context.getSource().sendFeedback(Text.literal("§8[§c!§8] §7Du musst ein Item halten."));
                                    playCenteredSound(SoundEvents.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                                    return Command.SINGLE_SUCCESS;
                                }
                                Antidrop ad = Antidrop.getInstance();
                                if (ad.isProtected(item)) {
                                    context.getSource().sendFeedback(Text.literal("§8[§6!§8] §7Dieses Item ist bereits geschützt."));
                                    playCenteredSound(SoundEvents.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                                } else {
                                    ad.ITEMS.add(item.copy());
                                    ad.save();
                                    String type = item.getComponentChanges().isEmpty() ? "§8(§7Global§8)" : "§8(§bSpezifisch§8)";
                                    context.getSource().sendFeedback(Text.literal("§8[§a✔§8] §7Schutz für ")
                                            .append(item.getName().copy().formatted(Formatting.YELLOW))
                                            .append(" " + type + " §aaktiviert§7."));
                                    playCenteredSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
                                }
                                return Command.SINGLE_SUCCESS;
                            }))
                            .then(ClientCommandManager.literal("remove").executes(context -> {
                                MinecraftClient client = MinecraftClient.getInstance();
                                if (client.player == null) return Command.SINGLE_SUCCESS;
                                ItemStack item = client.player.getMainHandStack();
                                Antidrop ad = Antidrop.getInstance();

                                lastRemovedItem = null;
                                for (ItemStack stack : ad.ITEMS) {
                                    if (stack.getComponentChanges().isEmpty() ? item.isOf(stack.getItem()) : ad.equalsIgnoreDamage(stack, item)) {
                                        lastRemovedItem = stack.copy();
                                        break;
                                    }
                                }

                                if (lastRemovedItem != null) {
                                    ad.ITEMS.removeIf(stack -> stack.getComponentChanges().isEmpty() ? item.isOf(stack.getItem()) : ad.equalsIgnoreDamage(stack, item));
                                    ad.save();
                                    Text msg = Text.literal("§8[§a✔§8] §7Schutz für ")
                                            .append(item.getName().copy().withColor(Formatting.YELLOW.getColorValue()))
                                            .append(" §centfernt§7. ")
                                            .append(Text.literal("§6§l↩")
                                                    .styled(s -> s.withClickEvent(new ClickEvent.RunCommand("/internal-antidrop-restore " + lastRemovedItem.hashCode()))
                                                            .withHoverEvent(new HoverEvent.ShowText(Text.literal("§7Klicke zum §6Wiederherstellen")))));
                                    context.getSource().sendFeedback(msg);
                                    playCenteredSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS, 0.5f, 1.5f);
                                } else {
                                    context.getSource().sendFeedback(Text.literal("§8[§c!§8] §7Dieses Item ist nicht geschützt."));
                                    playCenteredSound(SoundEvents.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                                }
                                return Command.SINGLE_SUCCESS;
                            }))
                            .then(ClientCommandManager.literal("clear").executes(context -> {
                                if (Antidrop.getInstance().ITEMS.isEmpty()) {
                                    context.getSource().sendFeedback(Text.literal("§8[§c❌§8] §7Die Liste ist bereits leer."));
                                    playCenteredSound(SoundEvents.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                                    return Command.SINGLE_SUCCESS;
                                }
                                Antidrop.getInstance().ITEMS.clear();
                                Antidrop.getInstance().save();
                                context.getSource().sendFeedback(Text.literal("§8[§a✔§8] §7Alle Items aus der Liste §cgelöscht§7."));
                                playCenteredSound(SoundEvents.ENTITY_ITEM_BREAK, 0.5f, 0.8f);
                                return Command.SINGLE_SUCCESS;
                            }))
                            .then(ClientCommandManager.literal("list").executes(context -> {
                                if (Antidrop.getInstance().ITEMS.isEmpty()) {
                                    context.getSource().sendFeedback(Text.literal("§8[§6!§8] §7Keine geschützten Items."));
                                    playCenteredSound(SoundEvents.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                                    return Command.SINGLE_SUCCESS;
                                }
                                context.getSource().sendFeedback(Text.literal("§8--- §6AntiDrop Liste §8---"));
                                for (int i = 0; i < Antidrop.getInstance().ITEMS.size(); i++) {
                                    ItemStack stack = Antidrop.getInstance().ITEMS.get(i);
                                    int index = i;
                                    Text itemText = Text.literal(" §8» ").append(stack.getName().copy().formatted(Formatting.YELLOW))
                                            .append(stack.getComponentChanges().isEmpty() ? " §8(§7Global§8)" : " §8(§bSpezifisch§8)")
                                            .styled(s -> s.withHoverEvent(new HoverEvent.ShowItem(stack))
                                                    .withClickEvent(new ClickEvent.RunCommand("/internal-antidrop-remove " + index)));
                                    context.getSource().sendFeedback(itemText);
                                }
                                playCenteredSound(SoundEvents.BLOCK_CHEST_OPEN, 0.5f, 1.2f);
                                return Command.SINGLE_SUCCESS;
                            }))
            );

            dispatcher.register(
                    ClientCommandManager.literal("internal-antidrop-remove")
                            .then(ClientCommandManager.argument("index", IntegerArgumentType.integer(0))
                                    .executes(context -> {
                                        int index = IntegerArgumentType.getInteger(context, "index");
                                        Antidrop ad = Antidrop.getInstance();
                                        if (index >= 0 && index < ad.ITEMS.size()) {
                                            lastRemovedItem = ad.ITEMS.remove(index).copy();
                                            ad.save();
                                            Text msg = Text.literal("§8[§6AntiDrop§8] §7Schutz für ")
                                                    .append(lastRemovedItem.getName().copy().formatted(Formatting.YELLOW))
                                                    .append(" §centfernt§7. ")
                                                    .append(Text.literal("§6§l↩")
                                                            .styled(s -> s.withClickEvent(new ClickEvent.RunCommand("/internal-antidrop-restore " + lastRemovedItem.hashCode()))
                                                                    .withHoverEvent(new HoverEvent.ShowText(Text.literal("§7Klicke zum §6Wiederherstellen")))));
                                            context.getSource().sendFeedback(msg);
                                            playCenteredSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS, 0.5f, 1.5f);
                                        }
                                        return Command.SINGLE_SUCCESS;
                                    })
                            )
            );

            dispatcher.register(
                    ClientCommandManager.literal("internal-antidrop-restore")
                            .then(ClientCommandManager.argument("hash", IntegerArgumentType.integer())
                                    .executes(context -> {
                                        int hash = IntegerArgumentType.getInteger(context, "hash");
                                        if (lastRemovedItem != null && lastRemovedItem.hashCode() == hash) {
                                            Antidrop ad = Antidrop.getInstance();
                                            ad.ITEMS.add(lastRemovedItem.copy());
                                            ad.save();
                                            Text msg = Text.literal("§8[§6AntiDrop§8] §7Schutz für ")
                                                    .append(lastRemovedItem.getName().copy().withColor(Formatting.YELLOW.getColorValue()))
                                                    .append(" §awiederhergestellt§7.");
                                            context.getSource().sendFeedback(msg);
                                            playCenteredSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
                                            lastRemovedItem = null;
                                        }
                                        return Command.SINGLE_SUCCESS;
                                    })
                            )
            );
        });
    }

    private static void playCenteredSound(Object soundObj, float volume, float pitch) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            SoundEvent event;
            if (soundObj instanceof SoundEvent) {
                event = (SoundEvent) soundObj;
            } else if (soundObj instanceof net.minecraft.registry.entry.RegistryEntry<?>) {
                event = ((net.minecraft.registry.entry.RegistryEntry<SoundEvent>) soundObj).value();
            } else {
                return;
            }
            client.player.playSound(event, volume, pitch);
        }
    }
}