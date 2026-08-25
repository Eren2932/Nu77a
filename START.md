# Nuva — с чего начать прямо сейчас

Пошагово, по порядку. Каждый шаг заканчивается проверкой, что он сработал.
Если проверка не прошла — не идём дальше.

---

## Шаг 1. Залить код на GitHub (10 минут)

```bash
# распаковал архив
cd nuva

git init
git add .
git commit -m "Nuva: sprint 0 foundation (server + android + ci)"
git branch -M main
git remote add origin https://github.com/ТВОЙ_АККАУНТ/nuva.git
git push -u origin main
```

Репозиторий сделай **приватным** пока. Откроем перед релизом.

**Проверка:** на GitHub во вкладке Actions запустился workflow «Android».
Он должен стать зелёным и положить в артефакты `nuva-debug-apk`.

> Если Actions ругается на Android SDK — это нормально в первый раз, обычно
> помогает повторный запуск (кеш Gradle прогревается).

---

## Шаг 2. Создать ключ подписи (5 минут, один раз в жизни)

Нужен JDK 17 на твоём ПК (`sudo apt install openjdk-17-jdk`, или он уже стоит
вместе с Android Studio).

```bash
./scripts/make-keystore.sh
```

Скрипт спросит пароль (запиши его на бумагу!), создаст
`android/nuva-release.jks` и напечатает четыре значения.

Заведи их в GitHub: **Settings → Secrets and variables → Actions → New
repository secret**:

* `NUVA_KEYSTORE_BASE64`
* `NUVA_KEYSTORE_PASSWORD`
* `NUVA_KEY_ALIAS`
* `NUVA_KEY_PASSWORD`

### ⚠️ Самое важное действие всего проекта

Скопируй `android/nuva-release.jks` минимум в **два** места вне ПК:
флешка, второй телефон, зашифрованный архив в облаке.

Потеря этого файла = ты больше никогда не выпустишь обновление Nuva.
Только новое приложение с нуля и с потерей всех пользователей.

**Проверка:** файл `android/nuva-release.jks` существует, четыре секрета видны
в списке секретов GitHub, копия ключа лежит в двух местах.

---

## Шаг 3. Сервер (30 минут)

Берём VPS. Рекомендация: **Timeweb Cloud, 2 vCPU / 2 ГБ / 40 ГБ, ~400 ₽/мес**,
Ubuntu 24.04. Другие варианты — в `docs/HOSTING.md`.

Домен: `reg.ru`, `.ru` за ~200 ₽/год. A-запись `api.твойдомен.ru` → IP VPS.

Дальше по SSH:

```bash
apt update && apt install -y docker.io docker-compose-v2 git ufw
systemctl enable --now docker
ufw allow 22/tcp && ufw allow 80/tcp && ufw allow 443/tcp && ufw --force enable

mkdir -p /opt && cd /opt
git clone https://github.com/ТВОЙ_АККАУНТ/nuva.git
cd nuva

cp .env.example .env
./scripts/gen-secrets.sh
nano .env
#   NUVA_DOMAIN=api.твойдомен.ru
#   NUVA_ACME_EMAIL=твоя@почта
#   NUVA_ALLOWED_ORIGINS=https://твойдомен.ru

docker compose up -d --build
docker compose logs -f server
```

В логах должно быть `migrations applied` и `http server listening`.

**Проверка:**

```bash
curl https://api.твойдомен.ru/healthz     # {"status":"ok"}
curl https://api.твойдомен.ru/readyz      # {"status":"ready"}
./scripts/smoke-test.sh https://api.твойдомен.ru
```

Последняя команда должна напечатать **All smoke tests passed.**
Это значит: регистрация, вход, ротация токенов и восстановление пароля
работают на живом сервере. Сервер готов.

Не забудь бэкап базы — скрипт есть в конце `docs/HOSTING.md`.

---

## Шаг 4. Прописать адрес сервера в приложение (2 минуты)

На GitHub: **Settings → Secrets and variables → Actions → Variables →
New repository variable**

| Имя | Значение |
|---|---|
| `NUVA_API_BASE_URL` | `https://api.твойдомен.ru` |

И заодно в `android/gradle.properties` поменяй `nuvaApiBaseUrl` на свой домен,
чтобы локальные релизные сборки тоже смотрели куда надо.

---

## Шаг 5. Первый настоящий APK (5 минут)

```bash
git tag v0.1.0
git push origin v0.1.0
```

Actions соберёт подписанный APK и создаст GitHub Release с файлом
`nuva-0.1.0.apk`.

**Проверка:** скачай APK на телефон, поставь, зарегистрируйся.
Должно быть видно:

* зелёная плашка **Realtime online** — WebSocket живой;
* кнопка **Send echo** пишет в лог `-> echo` и `<- echo_reply` — канал
  работает в обе стороны;
* диалог с recovery-кодом при регистрации — запиши код, он больше не покажется.

Теперь поставь **v0.1.1** поверх — просто чтобы убедиться: обновление ставится
без удаления, аккаунт на месте. Именно эта проверка закрывает боль прошлых
версий.

---

## Шаг 6. Дальше

Открываем `docs/ROADMAP.md`, спринт 1 (аккаунты и профили), и идём по нему.

Порядок работы, который сработает: ты берёшь один пункт из спринта, я выдаю
готовый код и обе стороны (сервер + клиент) в одном куске, ты коммитишь и
тегируешь. Каждые две недели у тебя на руках рабочий APK.

---

## Если что-то сломалось

1. `docs/BUILD.md`, раздел «Частые ошибки» — там уже собраны те грабли,
   на которые мы наступали.
2. Логи сервера: `docker compose logs --tail=200 server`
3. Логи приложения: `adb logcat -s NuvaApp NuvaHttp NuvaRealtime SessionStore`
4. Присылай мне точный текст ошибки. Не пересказ — текст.
