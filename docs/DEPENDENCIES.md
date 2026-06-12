# Зависимости фронтенда

Что UI ожидает увидеть в новом моде. Все импорты сгруппированы по слоям.

## 1. Корневые классы мода

| Импорт | Что от него нужно UI |
|--------|----------------------|
| `ru.liko.pjmbasemod.Pjmbasemod` | Константа `Pjmbasemod.MODID = "pjmbasemod"` — используется в `ResourceLocation.fromNamespaceAndPath(...)` для всех текстур и слоёв оверлеев. |
| `ru.liko.pjmbasemod.Config` | Серверные флаги (squadHud и т.п.), читаемые в HUD-оверлеях. Можно заменить на свой `Config` или заглушить дефолтами. |

## 2. Клиентские кэши и хелперы (`client/*`, **не** в `gui/`)

UI обращается к этим объектам как к источнику актуального состояния:

| Класс | Назначение | Как минимально заглушить |
|-------|------------|--------------------------|
| `ClientPlayerDataCache` | Снимок `PjmPlayerData` для отрисованного игрока. | Singleton с публичными геттерами данных игрока. |
| `ClientKitsCache` | Список китов по классу/команде. | `Map<PjmPlayerClass, KitDefinition>` per team. |
| `ClientTeamConfig` | Имена/цвета/иконки команд. | Структура `Team(name, color, icon)`. |
| `client.api.ClientStatsApi` | Асинхронные HTTP-запросы статистики. | Можно сразу вернуть пустой `CompletableFuture<PlayerStatsResponse>`. |
| `client.capture.CaptureUiHelper` | Утилиты отрисовки полоски КП. | Используется только в `SpawnSelectionScreen` и `CaptureStatusBar`. |
| `client.compat.WarBornGuardCompat` | Совместимость с WarBornGuard (anti-cheat). | Заглушить методом `boolean isLocked() { return false; }`. |
| `client.input.ModKeyBindings` | KeyMapping-и (радиальное меню, item switch). | Регистрируй через `RegisterKeyMappingsEvent`. |
| `client.radio.RadioManager` | Состояние Simple Voice Chat. | Если радио не нужно — оставь поля-заглушки `isTransmitting()`. |
| `client.shader.*` (`PostShaderController`, `PostShaderPreset`, `ClientPostShaderStorage`) | Нужны только `PostShaderSettingsScreen`. Если шейдеров нет — удали экран. |

## 3. Common-классы (общая логика, **не** клиент)

UI читает доменные сущности из `common/`:

| Импорт | Назначение | Замечание |
|--------|------------|-----------|
| `common.player.PjmPlayerClass` | Enum классов (assault, sniper, …). | Чистый enum — копируется как есть. |
| `common.player.PjmPlayerData` / `PjmAttachments` / `PjmPlayerDataProvider` | Серверная модель + `IAttachmentType`. Клиент использует только геттеры. | Можно оставить как есть или заменить на свой DataAttachment. |
| `common.KitDefinition` | Описание кита. | Скопируй: класс + список item-id строк. |
| `common.customization.*` (`CustomizationManager`, `CustomizationOption`, `CustomizationType`) | Кастомизация внешнего вида. | Если в новом моде кастомизации нет — удалить вкладку в `ClassSelectionScreen`. |
| `common.chat.ChatMode` | Enum LOCAL/GLOBAL/TEAM (`RadialMenuScreen`). | Чистый enum. |
| `common.gamemode.ControlPointSnapshot` | DTO КП, используется в `SpawnSelectionScreen`/`CaptureStatusBar`. | Скопировать DTO. |
| `common.init.PjmSounds` | Звуки UI (`UI_MENU_PRESS`, `UI_MENU_SHARED`, `UI_CLASS_CHANGE`). | Зарегистрируй `SoundEvent` под этими именами. |
| `common.util.ItemParser` | Парсер `"minecraft:diamond_sword{Enchantments:[...]}"` → `ItemStack`. | Скопируй класс целиком. |
| `common.util.TeamBalanceHelper` | Подсчёт игроков по командам (для `TeamSelectionScreen`). | Скопируй или замени на свою функцию. |
| `common.api.dto.PlayerStatsResponse` | DTO ответа API. | Только для `PlayerStatsScreen`. |

## 4. Сетевые пакеты

UI **отправляет** на сервер:

- `SelectTeamPacket`, `SelectClassPacket`, `SelectSpawnPacket`, `SelectCustomizationPacket`
- `RequestOpenTeamSelectionPacket`, `RequestRallyItemPacket`, `RefillAmmunitionPacket`
- `ChangeChatModePacket`

UI **получает** от сервера (открытие экранов):

- `OpenClassSelectionPacket`, `OpenSpawnMenuPacket`, `OpenTeamSelectionPacket`

Все идут через `PjmNetworking.CHANNEL.sendToServer(...)`. В новом моде создай свой `IPayloadRegistrar` с теми же типами либо адаптируй вызовы под `PacketDistributor.SERVER.noArg().send(...)`.

## 5. Ванильные API NeoForge 1.21.1

Экраны полагаются на:
- `net.minecraft.client.gui.screens.Screen`
- `net.minecraft.client.gui.GuiGraphics`
- `net.minecraft.client.gui.components.Button`
- `net.minecraft.network.chat.Component`
- `net.minecraft.resources.ResourceLocation` (через `fromNamespaceAndPath` — NeoForge 1.21+ API)
- `com.mojang.blaze3d.systems.RenderSystem`
- `org.joml.Quaternionf`, `com.mojang.blaze3d.platform.Lighting` (3D-предпросмотр игрока в `ClassSelectionScreen`)

Оверлеи 1.21.1:
- `net.neoforged.neoforge.client.event.RegisterGuiLayersEvent`
- `net.minecraft.client.gui.LayeredDraw.Layer` (вместо устаревшего `IGuiOverlay` из 1.20)
- `net.neoforged.neoforge.client.gui.VanillaGuiLayers` (для перекрытия ванильных слоёв)

## 6. Опциональные интеграции

- **GeckoLib** — не нужен напрямую UI, но `ClassSelectionScreen` использует `EntityRenderDispatcher` для 3D-предпросмотра.
- **Simple Voice Chat** — нужен только для `VoiceChatOverlay` и `RadioManager`.

Если эти моды отсутствуют — удали соответствующие компоненты или оберни вызовы в `if (ModList.get().isLoaded("..."))`.
