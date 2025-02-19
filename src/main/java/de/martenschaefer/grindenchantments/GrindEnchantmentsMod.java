package de.martenschaefer.grindenchantments;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.function.Function;
import net.minecraft.util.Identifier;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.github.fourmisain.taxfreelevels.TaxFreeLevels;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import de.martenschaefer.grindenchantments.config.GrindEnchantmentsConfig;
import de.martenschaefer.grindenchantments.cost.CostCountModeType;
import de.martenschaefer.grindenchantments.event.ApplyLevelCostEvent;
import de.martenschaefer.grindenchantments.event.GrindstoneEvents;
import de.martenschaefer.grindenchantments.impl.DisenchantOperation;
import de.martenschaefer.grindenchantments.impl.MoveOperation;
import de.martenschaefer.grindenchantments.registry.GrindEnchantmentsRegistries;

public class GrindEnchantmentsMod implements ModInitializer {
    public static final String MODID = "grindenchantments";
    public static final Logger LOGGER = LogManager.getLogger("Grind Enchantments");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final Path CONFIG_PATH = Paths.get(MODID + ".json");

    private static GrindEnchantmentsConfig CONFIG = GrindEnchantmentsConfig.DEFAULT;

    @Override
    public void onInitialize() {
        GrindEnchantmentsRegistries.init();
        CostCountModeType.init();

        initializeConfig();

        DisenchantOperation disenchant = new DisenchantOperation();
        MoveOperation move = new MoveOperation();

        GrindstoneEvents.registerAll(disenchant);
        GrindstoneEvents.registerAll(move);

        ApplyLevelCostEvent.EVENT.register(ApplyLevelCostEvent.DEFAULT, (cost, player) -> {
            player.addExperienceLevels(-cost);
            return true;
        });

        // Mod compatibility with Tax Free Levels
        if (FabricLoader.getInstance().isModLoaded("taxfreelevels")) {
            ApplyLevelCostEvent.EVENT.register(ApplyLevelCostEvent.MOD_COMPATIBILITY, (cost, player) -> {
                TaxFreeLevels.applyFlattenedXpCost(player, cost);
                return true;
            });
        }
    }

    private static void initializeConfig() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_PATH);

        if (Files.exists(configPath) && Files.isRegularFile(configPath)) {
            try (InputStream input = Files.newInputStream(configPath)) {
                log(Level.INFO, "Reading config.");

                CONFIG = decodeConfig(input);
            } catch (IOException e) {
                log(Level.ERROR, "IO exception while trying to read config: " + e.getLocalizedMessage());
            } catch (RuntimeException e) {
                log(Level.ERROR, e.getLocalizedMessage());
            }
        } else {
            try (OutputStream output = Files.newOutputStream(configPath, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
                 OutputStreamWriter writer = new OutputStreamWriter(new BufferedOutputStream(output))) {
                log(Level.INFO, "Writing default config.");

                encodeConfig(writer);
            } catch (IOException e) {
                log(Level.ERROR, "IO exception while trying to write config: " + e.getLocalizedMessage());
            } catch (RuntimeException e) {
                log(Level.ERROR, e.getLocalizedMessage());
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static GrindEnchantmentsConfig decodeConfig(InputStream input) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(new BufferedInputStream(input))) {
            JsonElement element = new JsonParser().parse(reader); // Using this for 1.17 compatibility, would be JsonReader.parseReader in 1.18+

            Either<GrindEnchantmentsConfig, DataResult.PartialResult<GrindEnchantmentsConfig>> result =
                GrindEnchantmentsConfig.CODEC.parse(JsonOps.INSTANCE, element).get();

            return result.map(Function.identity(), partialResult -> {
                throw new RuntimeException("Error decoding config: " + partialResult.message());
            });
        }
    }

    private static void encodeConfig(Writer writer) throws IOException {
        DataResult<JsonElement> result = GrindEnchantmentsConfig.CODEC
            .encodeStart(JsonOps.INSTANCE, GrindEnchantmentsConfig.DEFAULT);

        JsonElement element = result.get().map(Function.identity(), partialResult -> {
            throw new RuntimeException("Error encoding config: " + partialResult.message());
        });

        String json = GSON.toJson(element);
        writer.append(json);
    }

    public static GrindEnchantmentsConfig getConfig() {
        return CONFIG;
    }

    public static void log(Level level, Object message) {
        LOGGER.log(level, "[Grind Enchantments] " + message);
    }

    public static Identifier id(String path) {
        return new Identifier(MODID, path);
    }
}
