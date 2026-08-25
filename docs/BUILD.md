# Сборка

## Почему в репозитории нет `gradlew`

Обычно Android-проект тащит в git бинарник `gradle/wrapper/gradle-wrapper.jar`.
Мы этого не делаем сознательно:

* бинарь в git невозможно проверить глазами (риск supply-chain);
* именно он даёт классические ошибки `Could not find or load main class
  org.gradle.wrapper.GradleWrapperMain` и `wrapper jar is corrupted` после
  неудачного `git clone` или архивирования проекта в zip.

Вместо него Gradle версии `8.10.2` устанавливается явно — и в CI, и у тебя.

## Локальная сборка

### Что нужно один раз

```bash
# JDK 17 (обязательно 17, не 21 и не 11)
sudo apt install -y openjdk-17-jdk unzip

# Gradle 8.10.2
curl -fsSLo /tmp/gradle.zip https://services.gradle.org/distributions/gradle-8.10.2-bin.zip
sudo unzip -q -d /opt /tmp/gradle.zip
sudo ln -sf /opt/gradle-8.10.2/bin/gradle /usr/local/bin/gradle
gradle --version   # должно быть 8.10.2, JVM 17
```

Android SDK: проще всего поставить Android Studio, она сама всё скачает.
Если собираешь без студии — распакуй command line tools и создай
`android/local.properties`:

```properties
sdk.dir=/home/ты/Android/Sdk
```

### Команды

```bash
cd android

gradle :app:assembleDebug            # отладочный APK
gradle :app:testDebugUnitTest        # юнит-тесты
gradle :app:assembleRelease          # релизный (нужен keystore)
gradle :app:dependencies             # дерево зависимостей
gradle clean                         # если что-то залипло
```

APK появится в `android/app/build/outputs/apk/<debug|release>/`.

## Подпись: главное правило проекта

Android определяет «то же самое приложение» по паре
`applicationId` + **ключ подписи**. Если ключ другой — система говорит
*«приложение не установлено»* / *«конфликтует с существующим пакетом»*, и
единственный выход — удалить старую версию вместе со всеми данными.

Поэтому:

* `applicationId = club.nuva.app` — заморожен навсегда;
* релизный ключ создаётся **один раз** через `scripts/make-keystore.sh`;
* если ключа нет, `assembleRelease` **падает с понятной ошибкой** и никогда не
  подписывается debug-ключом молча (см. конец `android/app/build.gradle.kts`);
* debug-сборка живёт под `club.nuva.app.debug`, поэтому её можно держать на
  телефоне одновременно с релизной и они не конфликтуют.

### Секреты GitHub

`scripts/make-keystore.sh` напечатает всё, что нужно завести в
**Settings → Secrets and variables → Actions**:

| Секрет | Что это |
|---|---|
| `NUVA_KEYSTORE_BASE64` | сам `.jks`, закодированный в base64 одной строкой |
| `NUVA_KEYSTORE_PASSWORD` | пароль хранилища |
| `NUVA_KEY_ALIAS` | `nuva` |
| `NUVA_KEY_PASSWORD` | пароль ключа (у нас совпадает с паролем хранилища) |

Опционально в **Variables** (не секрет, просто настройка):

| Переменная | Пример |
|---|---|
| `NUVA_API_BASE_URL` | `https://api.nuva.club` |

### Проверка, что ключ тот же

После каждого релиза CI печатает отпечаток сертификата APK. Он **обязан**
совпадать во всех релизах. Проверить вручную:

```bash
apksigner verify --print-certs nuva-0.1.0.apk
```

## GitHub Actions

| Workflow | Когда запускается | Что делает |
|---|---|---|
| `Android / debug` | push и PR в `android/**` | тесты + debug APK в артефакты |
| `Android / release` | тег `v*` или запуск вручную | подписанный APK + GitHub Release |
| `Server / test` | push и PR в `server/**` | vet, тесты, сборка, smoke-тест на живом Postgres |
| `Server / image` | push в `main` и теги | образ в GHCR |
| `Server / deploy` | тег `v*` | деплой по SSH (если заведены секреты) |

### Выпуск версии

```bash
git tag v0.1.0
git push origin v0.1.0
```

`versionCode` берётся из номера запуска workflow, поэтому он всегда растёт —
Android никогда не сочтёт новую сборку «более старой».

## Частые ошибки и что они значат

| Симптом | Причина | Лечение |
|---|---|---|
| `App not installed` / «конфликтует с существующим пакетом» | APK подписан другим ключом | ставь релизные APK из Actions; удали debug-версию `club.nuva.app.debug` |
| `Release signing is not configured` | нет keystore | `scripts/make-keystore.sh` или заведи 4 секрета |
| `Unsupported class file major version` | JDK не 17 | `sudo update-alternatives --config java` |
| `SDK location not found` | нет `local.properties` | создай с `sdk.dir=...` |
| `Could not resolve io.ktor:...` | нет сети / прокси | проверь интернет, `gradle --refresh-dependencies` |
| `no required module provides package` (Go) | не синхронизирован go.mod | `cd server && go mod tidy` |
| `NUVA_JWT_SECRET` в логах сервера | не заполнен `.env` | `./scripts/gen-secrets.sh` |
| WebSocket рвётся каждую минуту | прокси режет idle | в нашем Caddyfile это уже учтено, не убирай блок `@websockets` |

## Локальная разработка «телефон ↔ свой ПК»

Debug-сборка ходит на `http://10.0.2.2:8080` — это адрес твоего ПК из
эмулятора Android. Для реального телефона в одной Wi-Fi:

```bash
cd android
gradle :app:assembleDebug -PnuvaApiBaseUrlDebug=http://192.168.1.50:8080
```

Открытый HTTP разрешён **только** в debug-сборке
(`app/src/debug/AndroidManifest.xml`). Релиз всегда HTTPS.
