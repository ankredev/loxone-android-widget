# Loxone Widget (Android)

Frei konfigurierbares **Android-Home-Screen-Widget** für [Loxone](https://www.loxone.com)-Werte.
Über eine Admin-Oberfläche lassen sich beliebige im Miniserver konfigurierte Controls durchsuchen
und zu Widgets zusammenstellen — **anzeigen und steuern**.

> **Disclaimer:** Inoffizielles, privates Open-Source-Projekt. **Nicht mit der Loxone Electronics GmbH
> affiliiert, von ihr unterstützt oder geprüft.** „Loxone" ist eine Marke ihrer jeweiligen Inhaber und wird
> hier ausschließlich beschreibend verwendet. Die App enthält keinen Loxone-Code und keine Loxone-Assets;
> sie kommuniziert lediglich über die HTTP-Schnittstelle mit einem Miniserver, den **du selbst besitzt**.
> Nutzung auf eigene Verantwortung.

## Funktionen

- **Anzeige** beliebiger Loxone-Werte (Temperaturen, Zustände, Verbräuche …) mit Formatierung pro Wert.
- **Steuerung** direkt aus dem Widget: Licht schalten, Jalousie (Auf/Stop/Ab) u. a.
- **Admin-Oberfläche** (Compose): Verbindung einrichten, alle Controls nach Raum/Kategorie/Typ/Name
  durchsuchen, auswählen, **Reihenfolge per Drag & Drop** sortieren, Hintergrundfarbe und Abfrage-Intervall
  pro Wert festlegen.
- **Mehrere Widgets** mit je eigener Konfiguration.
- **Live-Modus** pro Widget (durchgehende Aktualisierung bei eingeschaltetem Bildschirm).

## Architektur

| Thema | Entscheidung |
|-------|--------------|
| Plattform | Native **Kotlin + Jetpack Glance** (Widgets), **Compose** (Admin-UI) |
| Datenquelle | **`LoxAPP3.json`** vom Miniserver (`GET /data/LoxAPP3.json`) |
| Werte-Updates | **Burst-Polling** per Foreground-Service (`PollBurstService`) nach Widget-Interaktion; einmalige On-demand-Updates via `WidgetUpdateWorker` |
| Abfrage-Intervall | **pro Item** konfigurierbar (Default 5 s, Untergrenze 2 s) |
| Auth | **Token/JWT**; Passwort/Token in `EncryptedSharedPreferences`, nie im Klartext gespeichert oder geloggt |
| Persistenz | **DataStore** für Verbindung + Widget-Configs |
| Verbindung | v1 lokales Netz; Remote (Cloud-DNS/VPN) geplant |

```
app/src/main/java/com/ankredev/loxwidget/
  data/
    LoxoneRepository.kt      Fassade: Token-Caching, Struktur, Werte, Befehle
    loxone/                  LoxoneClient, LoxoneAuth, StructureParser, Modelle
    config/                  ConfigStore (DataStore), SecretStore (verschlüsselt), Modelle
  ui/config/                 Verbindungs-Setup + Widget-Builder (Compose)
  widget/                    Glance-Widget, Receiver, Update-Worker, Burst-Service, Formatter
```

## Loxone-Protokoll (Kurzreferenz)

- **Struktur:** `GET /data/LoxAPP3.json` → `controls`, `rooms`, `cats`, States je Control.
- **Token-Auth:**
  1. `GET /jdev/sys/getkey2/{user}` → `key` (hex), `salt`, `hashAlg` (SHA1/SHA256)
  2. `pwHash = UPPER(HEX(SHA(password + ":" + salt)))`
  3. `authHash = HEX(HMAC-SHA(key, user + ":" + pwHash))`
  4. `GET /jdev/sys/getjwt/{authHash}/{user}/{perm}/{uuid}/{info}` → JWT
- **Wert lesen:** `GET /jdev/sps/io/{uuidAction}/all` — Top-Level-`value` im `{"LL":{...}}`-Envelope.
  (Der einfache Pfad und `/state` spiegeln bei Schaltern nicht zuverlässig den Live-Status; `/all` schon.)
- **Befehl senden:** `GET /jdev/sps/io/{uuidAction}/{cmd}` (z. B. `On`/`Off`/`up`/`down`/Zahl).

## Build

Voraussetzung: **Android Studio** (bringt SDK + Gradle mit) oder ein lokales Android SDK.

```bash
# In Android Studio: Projekt öffnen → Gradle-Sync → Run.
# Oder per Terminal (JAVA_HOME auf die JBR von Android Studio):
./gradlew :app:assembleDebug        # Debug-APK bauen
./gradlew :app:installDebug         # auf verbundenes Gerät installieren
```

- minSdk 26 (Android 8), targetSdk 35, Kotlin 2.0.x, AGP 8.13.x / Gradle 8.13.
- `local.properties` (SDK-Pfad) wird **nicht** eingecheckt — Android Studio legt sie beim Sync an.

## Lizenz

[GNU General Public License v3.0](LICENSE) — © 2026 ankredev.

Du darfst die Software nutzen, weitergeben und verändern; abgeleitete Werke müssen unter GPL-3.0 stehen.
Es gibt **keine Gewährleistung**, soweit gesetzlich zulässig.
