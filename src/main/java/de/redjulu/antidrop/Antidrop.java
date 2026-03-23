package de.redjulu.antidrop;

import com.google.gson.*;
import de.redjulu.antidrop.commands.AntiDropCommand;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryWrapper;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Antidrop implements ClientModInitializer {

    public final List<ItemStack> ITEMS = new ArrayList<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Antidrop instance;
    private File configFile;

    @Override
    public void onInitializeClient() {
        instance = this;
        AntiDropCommand.register();

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            updateConfigFile(client);
            load();
        });
    }

    private void updateConfigFile(MinecraftClient client) {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("antidrop");
        File dir = configDir.toFile();
        if (!dir.exists()) dir.mkdirs();

        String fileName;
        if (client.isInSingleplayer()) {
            fileName = "local_" + client.getServer().getSaveProperties().getLevelName().replaceAll("[^a-zA-Z0-9_-]", "_");
        } else {
            ServerInfo serverInfo = client.getCurrentServerEntry();
            if (serverInfo != null) {
                fileName = "server_" + serverInfo.address.replaceAll("[^a-zA-Z0-9_-]", "_");
            } else {
                fileName = "default";
            }
        }
        this.configFile = new File(dir, fileName + ".json");
    }

    public static Antidrop getInstance() {
        return instance;
    }

    private RegistryWrapper.WrapperLookup getLookup() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.world != null) {
            return client.world.getRegistryManager();
        }
        return DynamicRegistryManager.EMPTY;
    }

    public void save() {
        if (configFile == null) return;
        try (FileWriter writer = new FileWriter(configFile)) {
            RegistryWrapper.WrapperLookup lookup = getLookup();
            JsonArray jsonArray = new JsonArray();
            for (ItemStack stack : ITEMS) {
                ItemStack.CODEC.encodeStart(lookup.getOps(NbtOps.INSTANCE), stack)
                        .result()
                        .ifPresent(nbt -> jsonArray.add(nbt.toString()));
            }
            GSON.toJson(jsonArray, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void load() {
        ITEMS.clear();
        if (configFile == null || !configFile.exists()) return;
        try (FileReader reader = new FileReader(configFile)) {
            JsonElement jsonElement = JsonParser.parseReader(reader);
            if (!jsonElement.isJsonArray()) return;
            JsonArray array = jsonElement.getAsJsonArray();

            List<ItemStack> loadedItems = new ArrayList<>();
            RegistryWrapper.WrapperLookup lookup = getLookup();

            for (JsonElement element : array) {
                try {
                    NbtCompound nbt = StringNbtReader.parse(element.getAsString());
                    ItemStack.CODEC.parse(lookup.getOps(NbtOps.INSTANCE), nbt)
                            .result()
                            .ifPresent(loadedItems::add);
                } catch (Exception ignored) {}
            }
            this.ITEMS.addAll(loadedItems);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isProtected(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        for (ItemStack protectedStack : ITEMS) {
            if (!stack.isOf(protectedStack.getItem())) continue;
            if (protectedStack.getComponentChanges().isEmpty()) {
                return true;
            }
            if (equalsIgnoreDamage(protectedStack, stack)) {
                return true;
            }
        }
        return false;
    }

    public boolean equalsIgnoreDamage(ItemStack a, ItemStack b) {
        if (!a.isOf(b.getItem())) return false;
        ItemStack copyA = a.copy();
        ItemStack copyB = b.copy();
        copyA.setDamage(0);
        copyB.setDamage(0);
        return ItemStack.areItemsAndComponentsEqual(copyA, copyB);
    }
}