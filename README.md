# VicProj pentru Android

Aplicația Android VicProj, cu același design (negru/alb), aceleași fluxuri și același server ca aplicația Windows.
Scrisă în **Kotlin + Jetpack Compose**. Pachet: `md.vicproj.app` · Versiune: `1.4.0` · Android 7.0+ (API 24).

## Ce poate face

**Pagina publică (oricine)**
- Cod de 9 cifre → **VERIFICĂ CODUL** → PIN de 6 cifre → **DESCARCĂ** (alegi unde se salvează; bară de progres; buton **DESCHIDE FIȘIERUL**)
- **Scanare QR** cu camera (CameraX + ML Kit, funcționează offline). Scanarea extrage doar codul de 9 cifre, PIN-ul se introduce mereu manual
- Cerere de permisiune pentru cameră: întâi fereastra VicProj („Permiți accesul la cameră?”), apoi cererea Android; dacă e refuzată definitiv, buton direct către Setări
- Dacă nu pornește camera: mesaj clar după 5 secunde, **REÎNCEARCĂ**, lanternă, eroare numerotată **#3**
- Linkuri deschise din exterior (`https://vicproj.netlify.app/cod/123456789` sau `vicproj://share/123456789`) completează și verifică automat codul

**Fără internet / server picat**
- Același mecanism ca în Windows, verifică la 5 secunde și instant la schimbarea rețelei:
  - **fără internet** → ilustrația `offline.jpg` + „Fără conexiune la internet”
  - **internet OK, dar serverul nu răspunde** (502/503/504 sau timeout) → ilustrația `server_down.png` + „Serverul VicProj nu răspunde”
- Buton **REÎNCEARCĂ**, apoi formularul revine singur când conexiunea e restabilită
- Erori numerotate, la fel ca în Windows: #1 Fără conexiune · #2 Serverul nu răspunde · #3 Camera nu poate porni · #4 Descărcarea a eșuat · #5 Nu am putut deschide linkul · #6 Eroare de fișier · #9 Eroare neașteptată

**Accesare Administrator** (5 atingeri rapide pe logo)
- Cod de acces (PIN) sau jeton Magic Link; sesiunea se păstrează criptat (Android Keystore)
- Publică fișier (materie, descriere, fișier) → **codul QR apare automat** + PIN-ul (o singură dată)
- Listă cu căutare și filtre (Toate / Active / Pauzate / Blocate); atinge un fișier pentru: copiază linkul, cod QR, generează PIN nou, pauză / reactivează, blochează definitiv
- **Cod QR**: **COPIAZĂ** imaginea, **SALVEAZĂ** ca PNG, **TRIMITE** prin orice aplicație. QR-ul conține doar linkul, fără PIN

**Actualizări**: citește `https://vicproj.netlify.app/version.json` (vezi mai jos).

## Cum obții APK-ul cu GitHub Actions

1. Creează un repo pe GitHub și urcă **tot conținutul acestui folder** (inclusiv `.github/`).
2. `git add . && git commit -m "VicProj Android" && git push origin main`
3. GitHub → tab **Actions** → „Build Android APK” rulează singur (sau **Run workflow**).
4. La final: deschide rularea → **Artifacts** → `vicproj-apk` → descarcă `vicproj.apk`.
5. Pentru un link permanent: creezi un **Release** pe GitHub, iar APK-ul se atașează automat.

Primul build durează ~5–8 minute (descarcă dependențele). Dacă apare o eroare, deschide pașii din Actions și trimite-mi textul din pasul „Build APK”.

### Semnare cu cheia ta (recomandat)

Fără cheie proprie, APK-ul e semnat cu o cheie de debug: se instalează, dar actualizările viitoare nu se pot instala peste el dacă schimbi cheia. O singură dată:

```bash
keytool -genkeypair -v -keystore vicproj.jks -alias vicproj -keyalg RSA -keysize 2048 -validity 10000
base64 -w 0 vicproj.jks > keystore.b64      # (macOS: base64 -i vicproj.jks -o keystore.b64)
```

GitHub → Settings → Secrets and variables → Actions → **New repository secret**:

| Secret | Valoare |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | conținutul fișierului `keystore.b64` |
| `ANDROID_KEYSTORE_PASSWORD` | parola keystore-ului |
| `ANDROID_KEY_ALIAS` | `vicproj` |
| `ANDROID_KEY_PASSWORD` | parola cheii |

**Păstrează `vicproj.jks` și parolele în siguranță** (nu le urca în repo): fără ele nu mai poți publica actualizări.

## Publică APK-ul pe site

Copiază `vicproj.apk` în proiectul Netlify (lângă `index.html`) și fă deploy. `llms.txt` și site-ul trimit deja la `https://vicproj.netlify.app/vicproj.apk`.

## version.json (actualizări Android)

În `https://vicproj.netlify.app/version.json` adaugă (separat de câmpurile Windows):

```json
{
  "latest_version_android": "1.4.0",
  "min_required_version_android": "1.0.0",
  "force_update_android": false,
  "download_url_android": "https://vicproj.netlify.app/vicproj.apk",
  "message_android": "Ce e nou în această versiune."
}
```

- `latest_version_android` mai mare decât versiunea instalată → fereastră **ACTUALIZARE DISPONIBILĂ** (MAI TÂRZIU / DESCARCĂ)
- `min_required_version_android` mai mare decât versiunea instalată sau `force_update_android: true` → ecran **E nevoie de o actualizare** (nu se poate închide)
- Fără aceste câmpuri, aplicația ignoră verificarea.

La o versiune nouă crește `versionCode` și `versionName` în `app/build.gradle.kts`.

## Structura

```
app/src/main/java/md/vicproj/app/
  MainActivity.kt      rădăcina UI, permisiuni, dialoguri
  MainViewModel.kt     toată logica (cod/PIN, admin, conectivitate)
  ApiClient.kt         aceleași endpoint-uri ca Windows (/api/v1/...)
  Connectivity.kt      Online / ServerUnreachable / Offline
  ScannerScreen.kt     scanner QR (CameraX + ML Kit)
  QrGenerator.kt       GenerareCodQR + copiere/salvare/trimitere
  Screens.kt Dialogs.kt Components.kt Theme.kt   interfața
app/src/main/res/      logo, offline.jpg, server_down.png, sunete, iconițe
```

Serverul: `https://victorrosca89-room89.hf.space` (constanta `API_BASE_URL` din `MainViewModel.kt`).
