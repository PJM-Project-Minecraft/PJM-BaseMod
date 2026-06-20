# Иконки рангов в чате

**Дата:** 2026-06-20
**Статус:** одобрено

## Цель

Рядом с никнеймом игрока в чате Minecraft показывать ранг: PNG-иконку ранга
(инлайн, через bitmap-шрифт) и короткий текстовый код (`shortName`) цветом ранга.

Итоговый формат строки (во всех режимах чата):

```
[G] ⟦иконка⟧[SGT] Likonchik: привет
```

Порядок: тег режима → иконка ранга → код ранга → ник → сообщение.

## Ограничение Minecraft

Чат — это `Component` (текст). Вставить произвольный PNG в строку нельзя.
Единственный штатный способ инлайн-картинки — **bitmap font provider**: PNG
мапится на спец-символ, и символ с этим шрифтом рисуется клиентом как картинка.
Сервер отправляет только символ + имя шрифта (строка в `Style`); саму текстуру
читает клиент, у которого мод заведомо установлен.

## Компоненты

### 1. Font provider (новый ассет, client-only)

`src/client/resources/assets/pjmbasemod/font/rank_icons.json` — тип `bitmap`,
по одному провайдеру на каждую текстуру ранга (файлы разные). Мапит приватные
Unicode-кодпоинты U+E000…U+E007 на существующие `textures/rangs/*.png`:

| codepoint | файл |
|-----------|------|
| U+E000 | private.png |
| U+E001 | corporal.png |
| U+E002 | sergeant.png |
| U+E003 | lieutenant.png |
| U+E004 | captain.png |
| U+E005 | major.png |
| U+E006 | colonel.png |
| U+E007 | general.png |

Дефолт `height: 8`, `ascent: 7` (16×16 масштабируется в высоту строки чата).
Точные значения подбираются визуально в игре — см. «Проверка».

### 2. Маппинг ранг → символ (`RankDefinition`, common)

Новое опциональное поле `chatGlyph` (читается из `ranks.json`). Метод
`chatGlyph()`:
- если задано в JSON — вернуть его;
- иначе — взять из фиксированной таблицы по `id` (private→U+E000 … general→U+E007);
- иначе (неизвестный id без явного glyph) — пусто → иконка не рисуется,
  показывается только текстовый код.

Так стандартные ранги работают из коробки, кастомные id не ломаются.

### 3. Бейдж ранга (`RankService.chatBadge`)

Новый метод `@Nullable Component chatBadge(ServerPlayer sender)` (рядом с уже
существующим `tabListName`). Возвращает `null`, если система рангов выключена
или `showChatRank == false`. Иначе строит:

- символ `chatGlyph()` со `Style.withFont(pjmbasemod:rank_icons)` (если glyph есть);
- `[shortName]` цветом `accentColorRgb()`;
- завершающий пробел.

Ранг отправителя: `RankRegistry.get().rankForXp(RankService.xp(sender))`.

### 4. Сборка строки (`ChatService.decorate`)

Между тегом режима и ником вставляется `RankService.chatBadge(sender)`
(если не `null`). Остальное без изменений.

### 5. Охват всех режимов (`PjmServerEvents.onChat`)

Убрать ранний `return` для `GLOBAL`. Теперь все режимы отменяют ванильное
сообщение и идут через `ChatService.deliver` (для GLOBAL получатели = все,
лог в консоль делает `deliver`). Компромисс: GLOBAL-сообщения теряют ванильную
криптоподпись/репорт — как уже происходит с LOCAL/TEAM.

### 6. Флаг конфига (`RankConfig`)

Новый `showChatRank` (default `true`) + геттер, по аналогии с
`showRankHud`/`showTabPrefix`. Выкл → чат без ранг-блока.

## Что НЕ трогаем

- Сетевой слой / `PjmNetworking.VERSION` — новых пакетов нет, идёт через
  стандартный `displayClientMessage`.
- Локализация — `shortName` берётся из `ranks.json`, символ не требует lang-ключа.

## Проверка

- `./gradlew compileJava` + `./gradlew compileClientJava` — компиляция обоих
  source set.
- Валидация JSON font provider.
- **Визуальный подбор `height`/`ascent` иконки в чате — за пользователем** в игре
  из пути без `!` (`runClient` в этом каталоге не работает).
