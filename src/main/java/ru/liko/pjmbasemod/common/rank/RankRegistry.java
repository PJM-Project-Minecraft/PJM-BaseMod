package ru.liko.pjmbasemod.common.rank;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;
import ru.liko.pjmbasemod.Pjmbasemod;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class RankRegistry {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final RankRegistry INSTANCE = new RankRegistry();

    private RankConfig config = RankConfig.defaults();
    private boolean loaded;

    private RankRegistry() {
    }

    public static RankRegistry get() {
        return INSTANCE;
    }

    public synchronized RankConfig config() {
        if (!loaded) reload();
        return config;
    }

    public synchronized boolean reload() {
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            if (Files.notExists(file)) {
                config = RankConfig.defaults();
                write(file, config);
                loaded = true;
                Pjmbasemod.LOGGER.info("Ranks: created default config at {}", file);
                return true;
            }

            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                RankConfig loadedConfig = GSON.fromJson(reader, RankConfig.class);
                config = loadedConfig == null ? RankConfig.defaults() : loadedConfig;
                config.normalize();
                loaded = true;
                Pjmbasemod.LOGGER.info("Ranks: loaded {} rank definitions.", config.ranks().size());
                return true;
            }
        } catch (Exception e) {
            loaded = true;
            config = RankConfig.defaults();
            Pjmbasemod.LOGGER.error("Ranks: failed to load {}, using defaults.", file, e);
            return false;
        }
    }

    public RankDefinition rankForXp(int xp) {
        List<RankDefinition> ranks = config().ranks();
        RankDefinition current = ranks.getFirst();
        int clamped = Math.max(0, xp);
        for (RankDefinition rank : ranks) {
            if (rank.minXp() <= clamped) {
                current = rank;
            } else {
                break;
            }
        }
        return current;
    }

    @Nullable
    public RankDefinition nextRankAfter(RankDefinition current) {
        List<RankDefinition> ranks = config().ranks();
        for (RankDefinition rank : ranks) {
            if (rank.minXp() > current.minXp()) return rank;
        }
        return null;
    }

    private Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(Pjmbasemod.MODID).resolve("ranks.json");
    }

    private void write(Path file, RankConfig config) throws IOException {
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(config, writer);
        }
    }
}
