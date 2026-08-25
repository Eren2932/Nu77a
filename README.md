# Nuva

Мессенджер, где твои слова принадлежат тебе.

Релиз 1.0 — **10 декабря 2026**.

---

## Что уже есть в этом репозитории (Спринт 0)

Это не заготовка «hello world». Это работающий вертикальный срез: регистрация →
токены → защищённый HTTP-запрос → живой WebSocket → ответ назад.

| Слой | Технология | Состояние |
|---|---|---|
| Сервер | Go 1.23, chi, pgx, coder/websocket | ✅ работает |
| БД | PostgreSQL 16, встроенные миграции | ✅ схема на весь 1.0 |
| Аутентификация | bcrypt + JWT access + ротируемый refresh + recovery-код | ✅ работает |
| Realtime | WebSocket-хаб, envelope-протокол, авто-реконнект | ✅ ping/echo |
| Клиент | Kotlin 2.0, Jetpack Compose, Ktor, EncryptedSharedPreferences | ✅ работает |
| Инфраструктура | Docker Compose + Caddy (авто-HTTPS) | ✅ одна команда |
| CI/CD | GitHub Actions: тесты, APK, образ, деплой | ✅ настроено |

## Быстрый старт

### 1. Сервер (локально или на VPS)

```bash
cp .env.example .env
./scripts/gen-secrets.sh        # сгенерирует JWT-секрет и пароль Postgres
docker compose --profile tls up -d --build   # VPS + domain
# or, on your own PC with no domain:
# docker compose --profile tunnel up -d --build
./scripts/smoke-test.sh http://localhost:8080
```

Последняя команда проверит весь цикл авторизации и напечатает
`All smoke tests passed.` Если да — сервер живой.

### 2. Ключ подписи Android (сделать один раз в жизни)

```bash
./scripts/make-keystore.sh
```

Скрипт создаст `android/nuva-release.jks`, положит локальный
`android/keystore.properties` и напечатает четыре секрета для GitHub.

> **Забэкапь `.jks` в два независимых места.** Потеря этого файла = ты больше
> никогда не сможешь выпустить обновление Nuva. Только новое приложение с
> нуля, без пользователей.

### 3. APK

```bash
# отладочный, ставится рядом с релизным (id club.nuva.app.debug)
cd android && gradle :app:assembleDebug

# релизный, подписанный постоянным ключом
cd android && gradle :app:assembleRelease
```

Или просто поставь тег — GitHub Actions соберёт и приложит APK к релизу:

```bash
git tag v0.1.0 && git push origin v0.1.0
```

## Документация

| Файл | О чём |
|---|---|
| [docs/BUILD.md](docs/BUILD.md) | Сборка, GitHub Actions, подпись, частые ошибки |
| [docs/HOSTING.md](docs/HOSTING.md) | Где и за сколько держать сервер из России |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Как устроен проект и почему именно так |
| [docs/DECISIONS.md](docs/DECISIONS.md) | Технические решения и их обоснование |
| [docs/ROADMAP.md](docs/ROADMAP.md) | План спринтов до 10 декабря 2026 |
| [docs/API.md](docs/API.md) | Контракт HTTP и WebSocket |
| [docs/WEB.md](docs/WEB.md) | План веб-версии (после Android) |
| [docs/SECURITY.md](docs/SECURITY.md) | Что защищено сейчас и что будет в 1.1 |

## Структура

```
nuva/
├── server/              Go API + WebSocket
│   ├── cmd/nuva-server/ точка входа
│   ├── internal/
│   │   ├── api/         HTTP-роуты и хендлеры
│   │   ├── auth/        пароли, JWT, recovery-коды
│   │   ├── config/      всё чтение окружения, единственное место
│   │   ├── store/       Postgres + миграции
│   │   └── ws/          realtime-хаб и протокол
│   └── Dockerfile
├── android/             Kotlin + Compose приложение
│   └── app/src/main/kotlin/club/nuva/app/
│       ├── data/        local (хранение) + remote (сеть)
│       ├── di/          ServiceLocator
│       └── ui/          Compose-экраны
├── infra/Caddyfile      HTTPS-прокси
├── scripts/             keystore, секреты, smoke-тесты
├── docs/                документация
└── .github/workflows/   CI/CD
```

## Правила проекта

Они появились не из книжки, а из грабель прошлых версий.

1. **Один keystore на всю жизнь приложения.** `applicationId` не меняется никогда.
2. **Релизная сборка без ключа падает с ошибкой**, а не подписывается debug-ключом.
3. **Ни одной переменной окружения вне `internal/config`.** Не хватает — сервер не стартует.
4. **Миграции только новым файлом.** Применённую миграцию не редактируют.
5. **`/v1` не ломаем.** Несовместимые изменения идут в `/v2`.
6. **Меняешь DTO — меняешь обе стороны в одном коммите.**
7. **Секреты не коммитим.** `.env`, `*.jks`, `keystore.properties` в `.gitignore`.
8. **Не обещаем защиту, которой нет.** См. `docs/SECURITY.md`.

## Лицензия

Пока не выбрана. До первого публичного релиза — все права у автора.
