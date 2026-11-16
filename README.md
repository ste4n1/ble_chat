# ble_chat

[Русский](#русский) | [English](#english)

---

## Русский

**ble_chat** — Android-приложение для обмена сообщениями и файлами между телефонами по Bluetooth (RFCOMM, без интернета). Проект написан на Kotlin с интерфейсом Jetpack Compose и Material You.

### Возможности
- **Связь по Bluetooth:** поиск устройств рядом, подключение и чат 1-на-1 в реальном времени (Bluetooth Classic RFCOMM).
- **Только пользователи приложения:** фильтрация устройств — в списке отображаются только телефоны с запущенным ble_chat; автоподключение и автопоиск.
- **Сообщения и файлы:** текстовые сообщения, отправка фото/файлов любых типов с прогрессом передачи, предпросмотр изображений.
- **Иконки файлов:** распознавание типов (APK, PDF, изображения, видео, аудио, архивы, документы и др.).
- **Управление сообщениями:** отмена отправки файла, удаление сообщения «у меня» / «у всех» (при активном соединении), анимации списка.
- **История:** локальное хранение чатов и сообщений (Room).
- **Оформление:** Material You (цвета из обоев системы) или выбор цвета в приложении; темы «Системная», «Светлая», «Тёмная (серая)», «AMOLED»; пользовательские обои чата.
- **Настройки:** имя пользователя, автовидимость для устройств рядом, отображение статусов отправки, очистка истории.
- **Локализация:** русский, English, español (выбирается по языку системы).
- **Удобство:** поле ввода поднимается над клавиатурой.

### Установка
Готовый APK доступен во вкладке [**Releases**](https://github.com/ste4n1/ble_chat/releases) — скачайте `ble_chat.apk` и установите на оба Android-телефона.

> Для работы нужны разрешения Bluetooth (и видимость устройства). Откройте приложение на обоих телефонах, разрешите доступ и поиск/видимость.

### Сборка из исходников
Требования:
- JDK **21**
- Android SDK (compileSdk/targetSdk 36)

```bash
git clone https://github.com/ste4n1/ble_chat.git
cd ble_chat
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleDebug
```

APK будет в `app/build/outputs/apk/debug/app-debug.apk`.

### Технологии
Kotlin, Jetpack Compose (Material 3), Room, Coroutines/Flow, Bluetooth RFCOMM, Coil.

### Автор
**ste4n1**

### Лицензия
[MIT](LICENSE)

---

## English

**ble_chat** is an Android app for messaging and file sharing between phones over Bluetooth (RFCOMM, no internet required). Built with Kotlin, Jetpack Compose, and Material You.

### Features
- **Bluetooth messaging:** nearby device discovery, connection, and real-time 1-on-1 chat (Bluetooth Classic RFCOMM).
- **App-only discovery:** shows only devices running ble_chat, with auto-connect and auto-discovery.
- **Messages & files:** text messages, send photos/files of any type with transfer progress, and image previews.
- **File type icons:** APK, PDF, images, video, audio, archives, documents, etc.
- **Message controls:** cancel file sending, delete messages for me / for everyone (when connected), with list animations.
- **History:** local chat and message storage (Room).
- **Appearance:** Material You (system wallpaper colors) or custom in-app accent color; themes: System, Light, Dark (gray), AMOLED; custom chat wallpaper.
- **Settings:** display name, automatic visibility to nearby devices, send status indicators, clear history.
- **Localization:** Russian, English, Spanish (follows system language).
- **UX:** input field stays above the keyboard.

### Install
A ready-to-use APK is available in [**Releases**](https://github.com/ste4n1/ble_chat/releases) — download `ble_chat.apk` and install it on both Android phones.

> Bluetooth permissions (and device visibility) are required. Open the app on both phones and grant permissions/discoverability.

### Build from source
Requirements:
- JDK **21**
- Android SDK (compileSdk/targetSdk 36)

```bash
git clone https://github.com/ste4n1/ble_chat.git
cd ble_chat
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

### Tech stack
Kotlin, Jetpack Compose (Material 3), Room, Coroutines/Flow, Bluetooth RFCOMM, Coil.

### Author
**ste4n1**

### License
[MIT](LICENSE)
