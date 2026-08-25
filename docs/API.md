# API v1

База: `https://api.nuva.club`
Версия в пути: `/v1`. Ломающие изменения идут в `/v2`, `/v1` живёт, пока живы
установленные APK.

Формат ошибки всегда один:

```json
{ "error": { "code": "username_taken", "message": "this username is already taken" } }
```

Клиент разбирает ошибки одним парсером и показывает `message` пользователю,
а по `code` принимает решения.

## Служебные

| Метод | Путь | Описание |
|---|---|---|
| GET | `/healthz` | процесс жив (для Docker healthcheck) |
| GET | `/readyz` | процесс + база готовы (для деплоя) |
| GET | `/v1/meta` | версия API, версия сборки, лимиты, число онлайн |

## Аутентификация

### POST `/v1/auth/register`

```json
{
  "username": "kolya",
  "display_name": "Коля",
  "password": "минимум-8-символов",
  "device_name": "Xiaomi Redmi Note 12",
  "platform": "android"
}
```

`201 Created`:

```json
{
  "user": { "id": "uuid", "username": "kolya", "display_name": "Коля", "bio": "", "avatar_url": "" },
  "access_token": "eyJ...",
  "refresh_token": "opaque-32-bytes-base64url",
  "expires_at": "2026-08-25T12:15:00Z",
  "recovery_code": "NUVA-4K7Q2-M9XPT-..." 
}
```

> `recovery_code` приходит **только здесь и только один раз**. Сервер хранит
> лишь bcrypt-хеш и повторно выдать код не может.

Ошибки: `409 username_taken`, `422 invalid_username`, `422 weak_password`.

### POST `/v1/auth/login`

Тело как у register без `display_name`. Ответ тот же, без `recovery_code`.
Неверный логин и неверный пароль дают **одинаковый** `401 invalid_credentials` —
чтобы нельзя было перебором собрать список существующих username.

### POST `/v1/auth/refresh`

```json
{ "refresh_token": "..." }
```

Возвращает новую пару токенов. **Старый refresh мгновенно становится
недействительным.** Повторный вызов с ним → `401 invalid_refresh_token`.

### POST `/v1/auth/logout`

```json
{ "refresh_token": "..." }
```

### POST `/v1/auth/recover`

```json
{ "username": "kolya", "recovery_code": "NUVA-...", "new_password": "..." }
```

Меняет пароль и отзывает **все** сессии пользователя, включая активные
WebSocket-соединения. Код нормализуется: регистр, пробелы и путаница
`O/0`, `I/L/1` не мешают.

## Профиль (нужен `Authorization: Bearer <access_token>`)

| Метод | Путь | Описание |
|---|---|---|
| GET | `/v1/me` | свой профиль |
| PATCH | `/v1/me` | `display_name` ≤ 48, `bio` ≤ 280, `avatar_url` |
| GET | `/v1/users/{username}` | публичный профиль + признак online |

## WebSocket `/v1/ws`

Авторизация: заголовок `Authorization: Bearer <token>` (нативный клиент) или
`?access_token=<token>` (браузер, где заголовок задать нельзя).

Каждый кадр в обе стороны — один и тот же конверт:

```json
{ "type": "send_text", "id": "необязательный-клиентский-id", "payload": { } }
```

`id` возвращается в ответе, поэтому клиент сопоставляет ответ с запросом без
угадывания.

### Уже работает

| Направление | type | Смысл |
|---|---|---|
| ← сервер | `hello` | сразу после подключения: `user_id`, `server_time`, `heartbeat_secs` |
| → клиент | `ping` | heartbeat, обязателен раз в 30 с |
| ← сервер | `pong` | ответ на ping |
| → клиент | `echo` | диагностика канала |
| ← сервер | `echo_reply` | тот же payload назад |
| ← сервер | `error` | `{ code, message }` |

### Появится в спринте 2

`send_text`, `message_new`, `typing` / `typing_relay`, `read_up_to`,
`presence`.

### Правила соединения

* Сервер разрывает связь, если 90 секунд нет ни одного кадра. Клиент шлёт
  `ping` каждые 30 секунд — три попытки в запасе.
* Лимит кадра 1 МБ. Медиа идёт через HTTP, не через socket.
* Буфер клиента 64 сообщения. Не успеваешь читать — тебя отключают, при
  переподключении досинхронизируешься по HTTP. Один медленный телефон не
  тормозит сервер.
* Реконнект: 1 с, 2 с, 4 с … до 30 с, плюс случайные до 1 с. Джиттер
  обязателен, иначе после рестарта сервера все клиенты ударят одновременно.

## Как проверить руками

```bash
BASE=https://api.nuva.club

curl -s $BASE/healthz

TOKEN=$(curl -s -X POST $BASE/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"test123","display_name":"Test","password":"password123","device_name":"curl","platform":"cli"}' \
  | jq -r .access_token)

curl -s $BASE/v1/me -H "Authorization: Bearer $TOKEN" | jq
```

Полный автоматический прогон: `./scripts/smoke-test.sh $BASE`
