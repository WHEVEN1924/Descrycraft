package dev.descrycraft.descrycraft.server;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.descrycraft.descrycraft.Descrycraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * World scoped drone rules, kept in the overworld's data storage so they survive a restart.
 */
public class DroneSettings extends SavedData {

    // Defaults to on: worlds saved before the rule existed should keep the feature enabled.
    public static final Codec<DroneSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("DoorInteraction", true).forGetter(s -> s.doorInteraction))
            .apply(i, DroneSettings::new));

    public static final SavedDataType<DroneSettings> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Descrycraft.MOD_ID, "settings"), DroneSettings::new, CODEC);

    /** Whether a pilot may use the drone's own reach to work doors, trapdoors and fence gates. */
    private boolean doorInteraction;

    public DroneSettings() {
        this(true);
    }

    public DroneSettings(boolean doorInteraction) {
        this.doorInteraction = doorInteraction;
    }

    public static DroneSettings get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean isDoorInteraction() {
        return this.doorInteraction;
    }

    public void setDoorInteraction(boolean value) {
        if (this.doorInteraction != value) {
            this.doorInteraction = value;
            this.setDirty();
        }
    }
}