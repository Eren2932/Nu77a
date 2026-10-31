# NUVA — Фаза 1 (PoC): техническая спецификация

> Читать после `NUVA_CONTEXT.md`. Стек и решения зафиксированы там (ADR-006, 009–013). Здесь — как именно строим PoC.
> Версия спецификации: **1.0.1** (2026-10-31). Меняешь формат или протокол — поднимай версию и пиши в changelog внизу.

---

## 1. Цель и критерии готовности (DoD)

**Цель:** два реальных устройства (Android + Desktop) обмениваются E2EE-текстом через наш сервер, а сервер физически не может прочитать содержимое.

**DoD (все пункты руками + автотесты):**
1. Регистрация по инвайту и юзернейму на Android и на Desktop.
2. A находит B по юзернейму, переписка в обе стороны.
3. B офлайн → A пишет → B онлайн → получает; после ack строка в `messages` удалена.
4. `SELECT envelope FROM messages` — только случайные байты; в логах сервера нет ни текста, ни IP.
5. Убил приложение во время отправки → после перезапуска сообщение доходит ровно один раз (outbox + дедупликация).
6. B переустановил приложение (новые ключи) → у A предупреждение о смене ключа.
7. Safety number совпадает на обоих устройствах.
8. Неверная подпись → закрытие 4401; нет авторизации за 10 с → 4408.
9. Тест-векторы конверта совпадают в Kotlin (JVM + Android) и в Python.

**Вне PoC:** медиа, push, группы, мультидевайс в UI (схема его уже поддерживает), профиль, iOS.

---

## 2. Структура репозиториев

Кодовое имя в коде нейтральное (ADR-018), бренд — только в ресурсах.

### Клиент (KMP)
```
client/
  core/
    crypto/    commonMain: Keys, EnvelopeV1, Hkdf, SafetyNumber, B64url
    protocol/  commonMain: @Serializable фреймы (kotlinx.serialization, sealed class по "type")
    net/       commonMain: Ktor WebSocket-клиент (engine: OkHttp — Android, CIO/OkHttp — desktop), реконнект, ping
    db/        SQLDelight (.sq) + драйверы SQLCipher (android / jvm)
    sync/      Outbox, Inbox, дедупликация, обработка ack
  ui/          Compose Multiplatform: экраны регистрации, контактов, чата, safety number
  app-android/
  app-desktop/
```

### Сервер
```
server/
  pyproject.toml        fastapi, uvicorn[standard], asyncpg, pydantic>=2, pydantic-settings, pynacl
  app/
    main.py             FastAPI, /ws, /healthz
    config.py           настройки из .env (SERVER_HOST, DATABASE_URL, лимиты)
    db.py               пул asyncpg
    hub.py              реестр device_id → WebSocket (в памяти, 1 воркер)
    protocol.py         pydantic-модели фреймов, discriminated union по "type"
    crypto.py           PyNaCl: проверка Ed25519, base64url
    ratelimit.py        token bucket в памяти
    jobs.py             очистка просроченных сообщений (каждые 15 мин)
    handlers/           auth.py, register.py, keys.py, messaging.py
  migrations/0001_init.sql
  tools/invite.py       CLI: создаёт инвайт, печатает код ОДИН раз
  tools/vectors.py      эталонная реализация конверта → test-vectors.json
  tests/                pytest: 2 виртуальных устройства через websockets
  Caddyfile
  docker-compose.yml    caddy + api + postgres (Redis в PoC не нужен); project `nuva`, mem_limit на каждый сервис
```
**Где живёт:** единый сервер Aeza, Стокгольм (1 ядро / 4 ГБ / 10 ГБ), рядом с ZiziPlayer → `/opt/nuva`. Наружу только Caddy `80/443`; `api` и `postgres` — во внутренней docker-сети. Порты `8080` и `1080` заняты Zizi. Правила соседства — `NUVA_CONTEXT.md` §8.2 и `SERVER.md` в репо ZiziPlayer.
Запуск: `uvicorn app.main:app --workers 1` (реестр соединений в памяти; при >1 воркере нужен Redis pub/sub).

---

## 3. Схема PostgreSQL

PostgreSQL 16+. ID — UUID, генерирует приложение (UUIDv7: время в префиксе, хорошо для индексов). На PG 18+ можно `DEFAULT uuidv7()`.

```sql
-- migrations/0001_init.sql

CREATE TABLE invites (
    code_hash   BYTEA PRIMARY KEY CHECK (octet_length(code_hash) = 32), -- SHA-256(нормализованный код)
    created_by  UUID NULL,                                              -- FK ниже; NULL = выдан админом
    max_uses    INT  NOT NULL DEFAULT 1 CHECK (max_uses > 0),
    use_count   INT  NOT NULL DEFAULT 0 CHECK (use_count >= 0),
    expires_at  TIMESTAMPTZ NOT NULL DEFAULT now() + INTERVAL '14 days',
    revoked_at  TIMESTAMPTZ NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (use_count <= max_uses)
);

CREATE TABLE accounts (
    id                UUID PRIMARY KEY,
    username          TEXT NOT NULL UNIQUE
                      CHECK (username ~ '^[a-z0-9_]{3,32}$'),            -- храним в нижнем регистре
    invite_code_hash  BYTEA NULL REFERENCES invites(code_hash) ON DELETE SET NULL,
    status            TEXT NOT NULL DEFAULT 'active'
                      CHECK (status IN ('active', 'banned', 'deleted')),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE invites
    ADD CONSTRAINT invites_created_by_fk
    FOREIGN KEY (created_by) REFERENCES accounts(id) ON DELETE SET NULL;

CREATE TABLE devices (
    id            UUID PRIMARY KEY,
    account_id    UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    identity_key  BYTEA NOT NULL UNIQUE CHECK (octet_length(identity_key) = 32), -- Ed25519 public (IK)
    x25519_key    BYTEA NOT NULL CHECK (octet_length(x25519_key) = 32),         -- X25519 public (XK)
    x25519_sig    BYTEA NOT NULL CHECK (octet_length(x25519_sig) = 64),         -- подпись IK над XK, §4.2
    key_version   INT  NOT NULL DEFAULT 1,                                      -- ротация XK в будущем
    platform      TEXT NOT NULL CHECK (platform IN ('android', 'desktop', 'ios')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_on  DATE NULL,                                                    -- только день: минимизация метаданных
    revoked_at    TIMESTAMPTZ NULL
);
CREATE INDEX devices_account_active_idx ON devices (account_id) WHERE revoked_at IS NULL;

-- Очередь store-and-forward: одна строка = один конверт для одного устройства.
CREATE TABLE messages (
    server_id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    recipient_device_id  UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    sender_device_id     UUID NULL REFERENCES devices(id) ON DELETE SET NULL,   -- станет NULL с sealed sender
    client_msg_id        UUID NOT NULL,                                         -- от отправителя, для идемпотентности
    envelope             BYTEA NOT NULL CHECK (octet_length(envelope) <= 65536),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at           TIMESTAMPTZ NOT NULL DEFAULT now() + INTERVAL '30 days',
    UNIQUE (sender_device_id, client_msg_id, recipient_device_id)
);
CREATE INDEX messages_queue_idx  ON messages (recipient_device_id, server_id);
CREATE INDEX messages_expiry_idx ON messages (expires_at);
```

### Ключевые запросы
```sql
-- Регистрация: всё в одной транзакции.
UPDATE invites SET use_count = use_count + 1
 WHERE code_hash = $1 AND revoked_at IS NULL AND expires_at > now() AND use_count < max_uses
RETURNING code_hash;                       -- 0 строк → invite_invalid, ROLLBACK
INSERT INTO accounts (id, username, invite_code_hash) VALUES ($2, $3, $1);  -- unique violation → username_taken
INSERT INTO devices (id, account_id, identity_key, x25519_key, x25519_sig, platform)
VALUES ($4, $2, $5, $6, $7, $8);

-- Отправка (на каждый конверт; идемпотентно):
INSERT INTO messages (recipient_device_id, sender_device_id, client_msg_id, envelope)
VALUES ($1, $2, $3, $4)
ON CONFLICT (sender_device_id, client_msg_id, recipient_device_id) DO NOTHING
RETURNING server_id;

-- Выгрузка очереди после авторизации (пачками по 100):
SELECT m.server_id, m.sender_device_id, d.account_id AS sender_account_id,
       m.client_msg_id, m.envelope, m.created_at
  FROM messages m LEFT JOIN devices d ON d.id = m.sender_device_id
 WHERE m.recipient_device_id = $1 AND m.server_id > $2
 ORDER BY m.server_id LIMIT 100;

-- Ack: ТОЛЬКО свои строки (иначе чужое устройство сможет удалять чужую очередь).
DELETE FROM messages WHERE recipient_device_id = $1 AND server_id = ANY($2::bigint[]);

-- Очистка (jobs.py):
DELETE FROM messages WHERE expires_at < now();
```

---

## 4. Криптография v1

Все примитивы — libsodium (клиент: KMP-биндинги; сервер: PyNaCl). Сервер только **проверяет подписи**, никогда не шифрует и не расшифровывает.

### 4.1 Соглашения
- `||` — конкатенация байтов. Строковые метки — ASCII. `0x00` — разделитель.
- UUID в байтах — 16 байт, RFC 4122, big-endian.
- base64url **без паддинга** — везде в JSON.
- `HOST` — строка из конфига сервера (напр. `api.example.org`), зашита в клиент. Защищает от переиспользования подписи на другом сервере.

### 4.2 Ключи устройства
```
IK = Ed25519 keypair      (crypto_sign_keypair)
XK = X25519 keypair       (crypto_box_keypair / crypto_kx_keypair — отдельный ключ, НЕ конвертация из IK)
x25519_sig = Ed25519_sign(IK.priv, "NUVA-XK-V1" || 0x00 || XK.pub)
```
Клиент, получив ключи собеседника, **обязан** проверить `x25519_sig` по `identity_key`.

### 4.3 Подписи авторизации
```
auth_payload = "NUVA-AUTH-V1" || 0x00 || HOST || 0x00 || device_id(16) || nonce(32)
reg_payload  = "NUVA-REG-V1"  || 0x00 || HOST || 0x00 || nonce(32) || IK.pub(32) || XK.pub(32) || username(utf8)
sig = Ed25519_sign(IK.priv, payload)       (crypto_sign_detached)
```
`nonce` — 32 случайных байта от сервера, одноразовый, живёт только в рамках WS-соединения (в памяти, не в БД).

### 4.4 Конверт v1 (Noise one-way pattern `K`: e, es, ss)
Отправитель S (устройство), получатель R (устройство). На **каждое** сообщение:
```
e        = свежий X25519 keypair
es       = X25519(e.priv,  R.XK.pub)       ── результат из нулей → ошибка (libsodium вернёт -1)
ss       = X25519(S.XK.priv, R.XK.pub)
key      = HKDF-SHA256(salt = "" (32 нуля),
                       ikm  = es || ss,
                       info = "NUVA-MSG-V1" || S.device_id || R.device_id || e.pub,
                       L    = 32)
nonce    = 24 случайных байта
AD       = "NUVA-MSG-V1" || 0x00 || 0x01 || S.device_id || R.device_id || client_msg_id || e.pub
ct       = XChaCha20-Poly1305-IETF_encrypt(key, nonce, AD, padded_plaintext)
```
Получатель: берёт `S.device_id` и `client_msg_id` из фрейма доставки, `S.XK.pub` — из закреплённых ключей контакта (неизвестное устройство → `keys.get` → проверка подписи → TOFU), пересчитывает `es = X25519(R.XK.priv, e.pub)` и `ss = X25519(R.XK.priv, S.XK.pub)`, затем `key`, `AD` и расшифровывает. Ошибка AEAD → сообщение отбрасывается и логируется локально (без содержимого).

**Байтовый формат `envelope`:**
| Смещение | Длина | Поле |
|---|---|---|
| 0 | 1 | version = `0x01` |
| 1 | 32 | `e.pub` |
| 33 | 24 | nonce |
| 57 | n + 16 | ciphertext ‖ tag Poly1305 |

Накладные расходы — 73 байта.

**Plaintext** (UTF-8 JSON, затем `sodium_pad` до кратного 256):
```json
{"v":1,"t":"text","ts":1793434200000,"body":"Привет"}
```
`t` в будущем: `receipt`, `edit`, `delete`, `media`, `profile` — сервер про них не знает.

**Свойства и ограничения:** см. `NUVA_CONTEXT.md` §6.2 и ADR-013 (нет FS при краже ключа получателя → ратчет до беты).

### 4.5 HKDF
Если в биндингах нет `crypto_kdf_hkdf_sha256` — реализовать по RFC 5869 поверх HMAC-SHA256 (`crypto_auth_hmacsha256`). Обязательно прогнать тест-кейсы 1–3 из RFC 5869.

### 4.6 Safety number
```
sn = SHA-256( "NUVA-SN-V1" || 0x00 || min(IK_A, IK_B) || max(IK_A, IK_B) )   (min/max — побайтово, лексикографически)
отображение: первые 30 байт → 6 кусков по 5 байт → каждый как uint40 big-endian mod 100000
             → 6 групп по 5 цифр (30 цифр) + QR с тем же sn
```
Одинаково на обоих устройствах благодаря сортировке ключей. Сверка — вслух или сканом QR.
Для мультидевайса (бета) — от identity аккаунта, а не устройства; пересмотреть вместе с ADR-013.

---

## 5. WebSocket-протокол v1

### 5.1 Общее
- URL: `wss://HOST/ws`, сабпротокол `nuva.v1`.
- Текстовые фреймы, JSON. Каждый фрейм: `"v": 1`, `"type"`.
- `id` — ставит клиент в запросах (строка, напр. `"c-17"`); ответ сервера несёт тот же `id`. Push-фреймы сервера — без `id`.
- Бинарные поля — base64url без паддинга.
- Одно активное соединение на устройство: новое вытесняет старое (код 4409).

### 5.2 Жизненный цикл
```
Клиент                                   Сервер
  │──── WS connect ─────────────────────────▶│
  │◀─── auth.challenge {nonce} ──────────────│  сразу после accept; таймер 10 с
  │                                          │
  │── account.register (первый запуск) ─────▶│  или
  │── auth.response {device_id, sig} ───────▶│
  │◀─── account.registered / auth.ok ────────│
  │◀─── msg.deliver × N (очередь) ───────────│
  │◀─── queue.empty ─────────────────────────│
  │       ... рабочий режим ...              │
  │── ping (каждые 25 с) ───────────────────▶│  тишина 60 с → сервер закрывает
```

### 5.3 Авторизация

**Сервер → клиент** (сразу после подключения):
```json
{"v":1,"type":"auth.challenge","nonce":"q2C7m…43 символа…","server_time":1793434200000}
```

**Клиент → сервер — регистрация** (первый запуск):
```json
{
  "v": 1, "type": "account.register", "id": "c-1",
  "invite": "7K2QF-M9XTA",
  "username": "leon",
  "device": {
    "identity_key": "<b64url 32B Ed25519 pub>",
    "x25519_key":   "<b64url 32B X25519 pub>",
    "x25519_sig":   "<b64url 64B>",
    "platform":     "android"
  },
  "sig": "<b64url 64B — подпись reg_payload, §4.3>"
}
```
Сервер: проверяет `sig` по `identity_key`, `x25519_sig`, формат юзернейма, затем транзакция из §3. Успех:
```json
{"v":1,"type":"account.registered","id":"c-1","account_id":"0192f…","device_id":"0192f…"}
```
Соединение сразу считается авторизованным.

**Клиент → сервер — вход:**
```json
{"v":1,"type":"auth.response","id":"c-1","device_id":"0192f…","sig":"<b64url 64B — подпись auth_payload>"}
```
Сервер: устройство существует, не `revoked`, аккаунт `active`, подпись верна → регистрирует соединение в `hub`, обновляет `last_seen_on`, отвечает:
```json
{"v":1,"type":"auth.ok","id":"c-1","account_id":"0192f…","device_id":"0192f…","pending":3}
```
Затем выгружает очередь (`msg.deliver`), в конце — `{"v":1,"type":"queue.empty"}`.
Ошибка → `error` с `auth_failed` и закрытие 4401. Любой другой `type` до авторизации → 4401.

### 5.4 Ключи собеседника
```json
{"v":1,"type":"keys.get","id":"c-2","username":"anna"}
```
(или `"account_id": "…"` вместо `username` — когда пришло сообщение от неизвестного устройства)
```json
{
  "v": 1, "type": "keys.result", "id": "c-2",
  "account_id": "0192f…", "username": "anna",
  "devices": [
    {"device_id":"0192f…","identity_key":"…","x25519_key":"…","x25519_sig":"…","key_version":1}
  ]
}
```
Только активные устройства. Клиент: проверяет каждую `x25519_sig`, сравнивает `identity_key` с закреплёнными (TOFU), при расхождении — предупреждение в UI.

### 5.5 Отправка: устройство A → устройство B
**A → сервер:**
```json
{
  "v": 1, "type": "msg.send", "id": "c-3",
  "client_msg_id": "0192f3a1-…",
  "to_account": "0192f…B",
  "envelopes": [
    {"device_id": "0192f…B1", "envelope": "<b64url конверта §4.4>"}
  ]
}
```
Сервер проверяет:
1. Набор `device_id` **точно** равен активным устройствам `to_account`. Иначе:
```json
{"v":1,"type":"error","id":"c-3","code":"devices_mismatch",
 "details":{"missing":["0192f…B2"],"stale":["0192f…B0"]}}
```
   → клиент делает `keys.get`, перешифровывает, повторяет с тем же `client_msg_id`.
2. Размеры и лимиты (§5.7).
3. INSERT всех конвертов в одной транзакции, `ON CONFLICT DO NOTHING` (повтор = не дубль).

**Сервер → A:**
```json
{"v":1,"type":"msg.accepted","id":"c-3","client_msg_id":"0192f3a1-…","server_ts":1793434200123}
```
После этого A помечает сообщение «отправлено» и удаляет из outbox.

**Сервер → B** (если онлайн — сразу; иначе — при следующем входе):
```json
{
  "v": 1, "type": "msg.deliver",
  "server_id": 1234,
  "from_account": "0192f…A",
  "from_device": "0192f…A1",
  "client_msg_id": "0192f3a1-…",
  "envelope": "<b64url>",
  "server_ts": 1793434200123
}
```

**B → сервер** (после того как сохранил в локальную БД — не раньше!):
```json
{"v":1,"type":"msg.ack","id":"c-7","server_ids":[1234]}
```
```json
{"v":1,"type":"ack.ok","id":"c-7","deleted":1}
```
Ack пачками: до 100 id, не реже раза в секунду при потоке.

**Семантика:** at-least-once. Дубли возможны (переподключение во время выгрузки) — клиент отбрасывает их по уникальному ключу `(from_device, client_msg_id)` в локальной БД, но всё равно шлёт ack.
Порядок на сервере: сначала регистрация в `hub`, потом выгрузка по курсору `server_id` — так живые сообщения не теряются в окне между выгрузкой и регистрацией.

### 5.6 Служебные фреймы
```json
{"v":1,"type":"ping","id":"c-9"}
{"v":1,"type":"pong","id":"c-9","server_time":1793434200000}
{"v":1,"type":"error","id":"c-3","code":"rate_limited","message":"retry later","details":{"retry_after_ms":5000}}
```

**Коды ошибок:** `bad_request`, `unauthorized`, `auth_failed`, `invite_invalid`, `username_taken`, `username_invalid`, `not_found`, `devices_mismatch`, `payload_too_large`, `rate_limited`, `internal`.

**Коды закрытия WS:** `4400` нарушение протокола, `4401` не авторизован / неверная подпись, `4408` таймаут авторизации (10 с), `4409` вытеснено новым соединением того же устройства, `4429` превышен лимит.

### 5.7 Лимиты (PoC, в конфиге)
| Параметр | Значение |
|---|---|
| Размер текстового фрейма | ≤ 256 КБ |
| `envelope` | ≤ 64 КБ |
| Конвертов в `msg.send` | ≤ 10 |
| `msg.send` на устройство | 30 / 10 с |
| `keys.get` на устройство | 60 / мин |
| `account.register` на соединение | 3 попытки, затем 4429 |
| Выгрузка очереди | пачки по 100 |

---

## 6. Сервер: правила реализации
1. **Логи:** `uvicorn --no-access-log`; в Caddy access-log выключен. В app-логах — только `device_id` (в PoC) и коды ошибок. Никогда — `envelope`, IP, юзернеймы при поиске.
2. **pydantic discriminated union** по `type`: неизвестный тип → `bad_request`, кривой JSON → 4400.
3. **Проверка подписи** — `nacl.signing.VerifyKey(identity_key).verify(payload, sig)`; ловить `BadSignatureError`.
4. **Хаб** — `dict[UUID, WebSocket]` + `asyncio.Lock` на отправку в сокет.
5. **Фоновая задача** очистки — `asyncio.create_task` в lifespan, раз в 15 минут.
6. **Инвайты:** `tools/invite.py --uses 1 --days 14` → печатает код формата `XXXXX-XXXXX` (Crockford Base32, ~50 бит), в БД кладёт `SHA-256(upper(code без дефиса))`.
7. **Секреты** — только `.env` (`DATABASE_URL`, `SERVER_HOST`). `.env` в `.gitignore`.
8. **Caddy:** автоматический TLS (Let's Encrypt), `reverse_proxy /ws api:8000`, только TLS 1.3 (`protocols tls1.3`).

## 7. Клиент: правила реализации
1. **Локальная БД (SQLCipher):** `identity` (свои ключи), `contacts` (`account_id`, `username`, `verified`), `contact_devices` (`device_id`, `identity_key`, `x25519_key`, `first_seen`), `messages` (`UNIQUE(from_device, client_msg_id)`), `outbox`.
2. **Outbox:** сообщение пишется в БД до отправки; ретраи с экспоненциальной паузой до `msg.accepted`.
3. **Ack после коммита** в локальную БД.
4. **Реконнект:** пауза 1 → 2 → 4 … 30 с + джиттер; ping каждые 25 с.
5. **Приватные ключи** — только в зашифрованной БД; ключ БД — в Android Keystore / системном keychain.
6. **Никакого логирования plaintext** и ключей, даже в debug.

---

## 8. Тестирование
1. **Тест-векторы:** `server/tools/vectors.py` (PyNaCl) генерирует `test-vectors.json` (фиксированные ключи, `e`, nonce → ожидаемый `envelope` и подписи). Тот же файл гоняется в `commonTest` (JVM + Android). Расхождение в одном байте = красный CI.
2. **HKDF** — RFC 5869, кейсы 1–3.
3. **Сервер** — pytest + `websockets`: два виртуальных устройства проходят регистрацию → вход → отправку → офлайн-доставку → ack → проверку, что в `messages` пусто; негативные кейсы (неверная подпись, чужой ack, `devices_mismatch`, лимиты).
4. **Ручной прогон DoD** (§1) на реальных Android + Desktop.

---

## 9. Порядок работ
| # | Задача | Результат |
|---|---|---|
| 1 | Спайк libsodium в KMP (Android + Desktop): Ed25519, X25519, XChaCha20-Poly1305, HMAC-SHA256 | Выбрана библиотека (вопрос §13.6 в CONTEXT) |
| 2 | `vectors.py` + эталонный конверт на Python | `test-vectors.json` |
| 3 | Сервер: миграция, `/ws`, challenge, register, auth, CLI инвайтов | Вход работает из pytest |
| 4 | Сервер: `keys.get`, `msg.send` / `deliver` / `ack`, очистка | Интеграционные тесты зелёные |
| 5 | `core/crypto` + `core/protocol` (Kotlin) | Векторы совпадают |
| 6 | `core/net` + `core/db` + `core/sync` | Отправка / приём из unit-тестов против локального сервера |
| 7 | UI: регистрация, поиск по юзернейму, чат, safety number | Android + Desktop |
| 0 | Харденинг единого сервера (SSH-ключи, без пароля root, ufw) — до первого деплоя | `ssh root@…` с паролем не пускает |
| 8 | Деплой: домен, Caddy, compose в `/opt/nuva` на сервере Aeza | Прогон DoD; Zizi продолжает работать (`/health` на `:8080`) |

---

## 10. Changelog спецификации
- **1.0 (2026-10-31)** — первая версия: схема БД, крипто v1 (Noise K), WS-протокол v1, DoD, порядок работ.
