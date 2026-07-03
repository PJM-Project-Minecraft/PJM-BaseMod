package ru.liko.pjmbasemod.common.antigrief;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;
import ru.liko.pjmbasemod.Config;
import ru.liko.pjmbasemod.Pjmbasemod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Анти-гриф: запрещает ломать/ставить блоки и взаимодействовать с ними,
 * если блок не входит в whitelist соответствующего действия (секция {@code antigrief} конфига).
 *
 * <p>Записи whitelist-а: id блока ({@code minecraft:lever}) или тег блока с решёткой
 * ({@code #minecraft:doors}). Распарсенные whitelist-ы кэшируются и автоматически
 * инвалидируются после {@code /pjm config reload} — {@link Config#reload()} создаёт
 * новые списки, кэш сверяется по ссылке на исходный список.</p>
 *
 * <p>Защита не действует на {@link FakePlayer} (механизмы модов), креатив
 * (при {@code exemptCreative}) и игроков с permission level ≥ {@code bypassPermissionLevel}.</p>
 */
public final class AntiGriefService {

    private AntiGriefService() {}

    private static volatile Whitelist breakWhitelist;
    private static volatile Whitelist interactWhitelist;
    private static volatile Whitelist placeWhitelist;

    // ---------------------------------------------------------------- публичный API

    /** true — ломать можно; иначе шлёт игроку actionbar-уведомление и возвращает false. */
    public static boolean checkBreak(ServerPlayer player, BlockState state) {
        return check(player, state, breakWhitelist(), "pjmbasemod.antigrief.break_denied");
    }

    /** true — взаимодействовать можно; иначе шлёт игроку actionbar-уведомление и возвращает false. */
    public static boolean checkInteract(ServerPlayer player, BlockState state) {
        return check(player, state, interactWhitelist(), "pjmbasemod.antigrief.interact_denied");
    }

    /** true — ставить можно; иначе шлёт игроку actionbar-уведомление и возвращает false. */
    public static boolean checkPlace(ServerPlayer player, BlockState state) {
        return check(player, state, placeWhitelist(), "pjmbasemod.antigrief.place_denied");
    }

    // ---------------------------------------------------------------- проверка

    private static boolean check(ServerPlayer player, BlockState state, Whitelist whitelist, String denyKey) {
        if (!Config.isAntiGriefEnabled()) return true;
        if (isBypassed(player)) return true;
        if (whitelist.matches(state)) return true;
        player.displayClientMessage(Component.translatable(denyKey), true);
        return false;
    }

    private static boolean isBypassed(ServerPlayer player) {
        if (player instanceof FakePlayer) return true;
        if (Config.isAntiGriefExemptCreative() && player.isCreative()) return true;
        int level = Config.getAntiGriefBypassPermissionLevel();
        return level > 0 && player.hasPermissions(level);
    }

    // ---------------------------------------------------------------- кэш whitelist-ов

    private static Whitelist breakWhitelist() {
        Whitelist cached = breakWhitelist;
        List<? extends String> source = Config.getAntiGriefAllowedBreakBlocks();
        if (cached == null || cached.source != source) {
            cached = Whitelist.parse(source, "allowedBreakBlocks");
            breakWhitelist = cached;
        }
        return cached;
    }

    private static Whitelist interactWhitelist() {
        Whitelist cached = interactWhitelist;
        List<? extends String> source = Config.getAntiGriefAllowedInteractBlocks();
        if (cached == null || cached.source != source) {
            cached = Whitelist.parse(source, "allowedInteractBlocks");
            interactWhitelist = cached;
        }
        return cached;
    }

    private static Whitelist placeWhitelist() {
        Whitelist cached = placeWhitelist;
        List<? extends String> source = Config.getAntiGriefAllowedPlaceBlocks();
        if (cached == null || cached.source != source) {
            cached = Whitelist.parse(source, "allowedPlaceBlocks");
            placeWhitelist = cached;
        }
        return cached;
    }

    private record Whitelist(List<? extends String> source, Set<ResourceLocation> blockIds, List<TagKey<Block>> tags) {

        boolean matches(BlockState state) {
            for (TagKey<Block> tag : tags) {
                if (state.is(tag)) return true;
            }
            return !blockIds.isEmpty()
                    && blockIds.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        }

        static Whitelist parse(List<? extends String> raw, String configKey) {
            Set<ResourceLocation> blockIds = new HashSet<>();
            List<TagKey<Block>> tags = new ArrayList<>();
            for (String entry : raw) {
                if (entry == null || entry.isBlank()) continue;
                String value = entry.trim();
                boolean isTag = value.startsWith("#");
                ResourceLocation id = ResourceLocation.tryParse(isTag ? value.substring(1) : value);
                if (id == null) {
                    Pjmbasemod.LOGGER.warn("AntiGrief: некорректная запись '{}' в antigrief.{}", entry, configKey);
                    continue;
                }
                if (isTag) {
                    tags.add(TagKey.create(Registries.BLOCK, id));
                } else {
                    if (!BuiltInRegistries.BLOCK.containsKey(id)) {
                        Pjmbasemod.LOGGER.warn("AntiGrief: блок '{}' из antigrief.{} не найден в реестре (опечатка?)", value, configKey);
                    }
                    blockIds.add(id);
                }
            }
            return new Whitelist(raw, Set.copyOf(blockIds), List.copyOf(tags));
        }
    }
}
