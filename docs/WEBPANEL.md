# Веб-панель администратора (WebPanel)

Встроенная веб-панель даёт оператору сервера браузерный интерфейс мониторинга
и модерации без рекомпиляции мода и без SSH-сессий.

- Серверная логика: `common/web/WebPanelService` (Javalin 6 через jarJar).
- Аутентификация: `common/web/WebAuthService`, `WebSessions`, `LoginCodes`, `RateLimiter`.
- Метрики: `common/web/metrics/MetricsCollector`, `MetricsHistory`, `MetricsSample`.
- Профайлер entity: `common/web/metrics/EntityProfiler`, `ProfilerWindow`.
- Снапшоты для HTTP: `common/web/WebState`.
- Действия (кик/наказания/телепорт/удаление): `common/web/WebActions`.
- API: `common/web/api/WebApiRoutes` (REST), `api/WebSocketHub` (WS-пуш раз в секунду).
- Фронтенд: `web/` (React + Vite), скомпилированный `dist/` коммитится в `src/main/resources/web/`.
- Команда входа: `/pjm web login` (permission level 3+).

---

## Содержание

1. [Обзор возможностей](#обзор-возможностей)
2. [Конфиг секции `web`](#конфиг-секции-web)
3. [Вход в панель](#вход-в-панель)
4. [Профайлер entity](#профайлер-entity)
5. [API-эндпоинты](#api-эндпоинты)
6. [Развёртывание](#развёртывание)
7. [Разработка фронтенда](#разработка-фронтенда)

---

## Обзор возможностей

**Что умеет панель:**
- Графики TPS, MSPT, использования кучи JVM и онлайна в реальном времени (ring buffer до `web.historyMinutes` минут истории).
- Список игроков с позицией, дименшеном, пингом, командой, ролью и статусом наказаний.
- Список entity с фильтрами по дименшену и типу; массовое удаление по UUID или по радиусу.
- Модерация: варн / бан / мут / снятие наказаний / кик / телепортация.
- Интерактивная карта: позиции игроков, «горячие» чанки при включённом профайлере.
- WebSocket-пуш (1 Гц) без опроса — браузер получает живые данные без перезагрузки.

**Что сознательно НЕ реализовано:**
- Консоль ввода команд (опасно без гранулярных прав; в планах нет).
- Встроенный TLS — панель работает по HTTP напрямую на своём порту; шифрование при желании добавляется reverse proxy (nginx/Caddy/др.).
- Гранулярные права доступа — любой op level 3+ видит всё.
- Персистентные сессии — рестарт сервера сбрасывает все сессии.

---

## Конфиг секции `web`

Секция `web` в `config/pjmbasemod/config.json`:

| Ключ | По умолчанию | Описание |
|------|-------------|----------|
| `enabled` | `false` | Включить ли веб-панель. Требует рестарта сервера. |
| `port` | `33005` | TCP-порт, на котором слушает Javalin. Диапазон: 1–65535. |
| `bindAddress` | `"0.0.0.0"` | Адрес привязки. Для прямого доступа оставить `"0.0.0.0"`; если ставите за прокси — `"127.0.0.1"`. |
| `sessionTtlMinutes` | `720` | TTL cookie-сессии (минуты). Диапазон: 5–43 200. |
| `historyMinutes` | `120` | Глубина истории метрик в ring buffer (минуты). Диапазон: 5–1 440. |
| `profilerEnabled` | `true` | Разрешить ли операторам включать профайлер тика entity. `false` — тумблер недоступен. |
| `publicUrl` | `""` | Базовый URL панели (например, `https://panel.example.com`). Пустой — ссылка в `/pjm web login` строится автоматически из IP сервера и порта. |

Пример минимального конфига (прямой доступ по порту):

```json
{
  "web": {
    "enabled": true,
    "port": 33005
  }
}
```

---

## Вход в панель

### Получение кода

Команда требует permission level 3 (op):

```
/pjm web login
```

Сервер генерирует одноразовый 8-символьный код из алфавита без неоднозначных символов
(без `0`/`O`, `1`/`I`). Код действует **5 минут** и сгорает после первого использования.

В чате игрока появляется код золотом (кликабелен — копирует в буфер) и кликабельная
ссылка входа. URL ссылки: `web.publicUrl`, если задан, иначе строится автоматически —
`http://<IP сервера>:<порт>/login?code=XXXXXXXX` (IP берётся из `server-ip` в
server.properties, при пустом — определяется по исходящему интерфейсу машины).

### Ввод кода в браузере

Откройте `http://<IP сервера>:33005` (или перейдите по ссылке из чата) и введите код на странице входа.
После успешного обмена браузер получает `HttpOnly; SameSite=Strict` cookie с токеном сессии
продолжительностью `web.sessionTtlMinutes` минут.

**Троттлинг:** 5 попыток ввода кода за 60 секунд с одного IP, после чего запросы отклоняются.

### Выход

```
/pjm web logout
```

Отзывает все активные сессии текущего игрока. Браузеры теряют доступ немедленно.

**Важно:** все сессии автоматически сбрасываются при остановке сервера — сессии хранятся
только в памяти JVM.

---

## Профайлер entity

Профайлер замеряет время тика каждого entity через `EntityTickEvent.Pre/Post` (пара
`System.nanoTime()` без синхронизации — entity тикают на server thread). Результат
накапливается в скользящем **окне 30 секунд** (600 тиков) и публикуется в `WebState`.

### Как включить

1. `web.profilerEnabled = true` в конфиге (по умолчанию включено).
2. В панели: вкладка «Entity» → тумблер «Профайлер» → вкл.
3. Данные появятся через 30 секунд (первый flush).

Через REST: `POST /api/profiler/toggle` (требует сессию и `X-Requested-With: PJMPanel`).

### Оверхед

В выключенном состоянии — одна проверка `volatile boolean` на событие, оверхед пренебрежим.
При включении добавляется `System.nanoTime()` вокруг каждого entity-тика — заметно на серверах
с тысячами entity. Рекомендуется включать только для диагностики и выключать после.

Если `web.profilerEnabled = false`, тумблер в панели недоступен (`403 profiler_disabled`).

---

## API-эндпоинты

Все эндпоинты под `/api/*` (кроме `/api/auth/*`) требуют валидную сессию-cookie.
POST-запросы дополнительно требуют заголовок `X-Requested-With: PJMPanel` (анти-CSRF).

### Аутентификация

| Метод | Путь | Описание |
|-------|------|----------|
| `POST` | `/api/auth/exchange` | Обмен кода на сессию. Тело: `{"code":"XXXXXXXX"}`. |
| `POST` | `/api/auth/logout` | Отзыв текущей сессии. |

### Чтение (только WebState, без блокировки игрового потока)

| Метод | Путь | Описание |
|-------|------|----------|
| `GET` | `/api/overview` | Текущий сэмпл метрик + история + счётчики entity + статус профайлера. |
| `GET` | `/api/players` | Список всех онлайн-игроков (`PlayerDto`). |
| `GET` | `/api/entities` | Список entity. Query-параметры: `dim`, `type`, `limit` (по умолчанию 2000). |
| `GET` | `/api/profiler` | Текущий отчёт профайлера (топ entity по времени тика). |
| `GET` | `/api/moderation/history` | История наказаний игрока. Query: `player=<UUID>`. |

### WebSocket

| Путь | Описание |
|------|----------|
| `WS /ws/live` | Пуш живых данных раз в секунду. Каждый кадр: `{type:"tick", sample, players}`. Каждый второй кадр добавляет `entityCounts` и `profiler`. Требует cookie сессии. |

### Действия (исполняются через `server.execute()`, таймаут 5 с → 504)

| Метод | Путь | Тело | Описание |
|-------|------|------|----------|
| `POST` | `/api/profiler/toggle` | — | Включить/выключить профайлер. |
| `POST` | `/api/actions/kick` | `{uuid, reason?}` | Кик игрока. |
| `POST` | `/api/actions/punish` | `{uuid, name, type, duration?, reason?}` | Наказание. `type` ∈ `warn`, `ban`, `mute_voice`, `mute_text`. `duration`: строка `30m`, `7d`, `permanent`. |
| `POST` | `/api/actions/pardon` | `{uuid, name?, type}` | Снять наказание. `type` ∈ `ban`, `mute_voice`, `mute_text`. |
| `POST` | `/api/actions/teleport` | `{uuid, toPlayer?, x?, y?, z?, dim?}` | Телепортировать игрока к игроку или на координаты. |
| `POST` | `/api/actions/entities/remove` | `{uuids: [...]}` | Удалить entity по списку UUID. |
| `POST` | `/api/actions/entities/remove-bulk` | `{type?, dim, x?, z?, radius?}` | Массовое удаление entity по типу и радиусу. |

**Коды ответов:** `200` — успех, `400` — плохой запрос, `401` — нет сессии / CSRF,
`403` — функция отключена, `404` — цель не найдена, `504` — сервер занят (таймаут).

---

## Развёртывание

### Основной сценарий: прямой доступ по порту

Панель поднимается вместе с сервером и слушает `web.bindAddress:web.port`
(по умолчанию `0.0.0.0:33005`) — достаточно открыть порт в фаерволе:

```bash
sudo ufw allow 33005/tcp   # или аналог в вашем фаерволе/панели хостинга
```

После этого панель доступна по `http://<IP сервера>:33005`, и именно такую ссылку
печатает `/pjm web login`.

**Про безопасность прямого HTTP:** трафик не шифруется — код входа и cookie сессии
видны на пути между браузером и сервером. Доступ к панели защищён одноразовыми кодами,
троттлингом и HttpOnly-сессиями, но для критичных продакшнов рекомендуется TLS через
reverse proxy (ниже).

### Опционально: reverse proxy с TLS

Javalin не предоставляет встроенный TLS. Если нужен HTTPS — nginx (или аналог)
принимает HTTPS снаружи и форвардит HTTP на localhost:

- Установите `web.bindAddress = "127.0.0.1"` — панель не будет доступна напрямую из интернета.
- Задайте `web.publicUrl` (например `https://panel.example.com`) — ссылка в `/pjm web login` будет использовать его вместо IP.

#### Пример конфига nginx

```nginx
server {
    listen 443 ssl;
    server_name panel.example.com;
    # ssl_certificate /etc/letsencrypt/live/panel.example.com/fullchain.pem;
    # ssl_certificate_key /etc/letsencrypt/live/panel.example.com/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:33005;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}

server {
    listen 80;
    server_name panel.example.com;
    return 301 https://$host$request_uri;
}
```

Заголовки `Upgrade` и `Connection "upgrade"` обязательны для работы WebSocket (`/ws/live`).

---

## Разработка фронтенда

Фронтенд — React + Vite, исходники в `web/`. Dev-сервер Vite проксирует API и WS на
запущенный Javalin-сервер (`localhost:33005`).

```bash
cd web
npm install          # первый раз
npm run dev          # dev-сервер на http://localhost:5173 с hot-reload
```

Перед коммитом собери prod-бандл:

```bash
cd web
npm run build        # dist/ → src/main/resources/web/
```

Содержимое `src/main/resources/web/` (скомпилированный фронтенд) **коммитится в репозиторий**
и пакуется в JAR через classpath-static Javalin. Gradle-задачи сборки фронтенд не запускают —
нужно пересобрать вручную при изменении UI.

Конфигурация прокси в Vite:
```javascript
// web/vite.config.ts — dev-прокси на Javalin
proxy: {
    '/api': 'http://localhost:33005',
    '/ws': { target: 'ws://localhost:33005', ws: true }
}
```
