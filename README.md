# AkAI — Android App

**AkAI** is an offline Android app that lets a Deaf or Hard-of-Hearing user and a hearing user hold a two-way conversation on one shared device. The Deaf user signs Filipino Sign Language (FSL) to the camera; the hearing user speaks into the microphone; both read the result in a shared conversation thread. Everything runs **on-device** — no internet required.

**Team:** 0xACE · 2526-sem2-IT411-10 · CIT-U BSIT 4th Year
**Current version:** 1.8.3 (versionCode 15)

---

## Try It / Deployment

- **Download / install:** the signed release APK is attached to the latest [GitHub Release](https://github.com/Desiigner101/AkAI-Android/releases) (`AkAI-1.8.3.apk`). Also distributed via **Google Play closed testing**.
- **No account needed** — AkAI is fully offline. Just install the APK and open the app; grant camera + microphone permissions when prompted. There is no login, no sign-up, and no server.
- **Requirements to run:** a physical **ARM64 Android device**, Android 8.0 (API 26) or newer. (MediaPipe does not run on x86_64 emulators.)

---

## Features

- **FSL Sign Recognition** — recognizes FSL **word** signs from the camera in real time (MediaPipe landmarks → TFLite LSTM).
- **Fingerspelling + Word Assembly** — recognizes FSL alphabet letters one at a time and assembles them into words, with a Top-3 panel so the user taps the intended letter.
- **Speech-to-Text** — transcribes the hearing user's **Filipino or English** speech offline with Vosk, selectable via a language toggle (default Filipino).
- **Shared Conversation Thread** — every sent message appears as a chat bubble on the correct side (Deaf / Hearing), in chronological order.
- **Optional Two-Device Mode** — two phones can pair directly over **Google Nearby Connections** (Bluetooth / Wi-Fi Direct) and share one conversation thread; only text crosses the link, still fully offline.

> **Privacy:** conversation data lives in memory for the session only — no database, no accounts, no camera or audio is ever stored. (Complies with RA 10173.)

---

## Tech Stack

| Component | Technology |
|---|---|
| Language | Kotlin |
| Architecture | MVVM (Activity + ViewModel + Repository) |
| UI | XML layouts + programmatic chat bubbles |
| Camera | CameraX 1.3.1 |
| Hand & Pose Landmarks | MediaPipe Tasks Vision 0.10.14 (HandLandmarker + PoseLandmarker) |
| ML Inference | TensorFlow Lite 2.14.0 + `select-tf-ops` (LSTM support) |
| Speech-to-Text | Vosk 0.3.47 (offline, Filipino + English) |
| Device-to-device sync | Google Play Services Nearby 19.3.0 |
| Async | Kotlin Coroutines 1.7.3 |
| Build | AGP 8.3.2 · Kotlin 1.9.22 |
| Min / Target SDK | API 26 (Android 8.0) / API 36 · ARM64 only |

---

## Project Structure

```
app/src/main/
├── java/com/akai/
│   ├── MainActivity.kt              ← camera, UI, mode switching, conversation screen
│   ├── SplashActivity.kt            ← splash + model preload
│   ├── PermissionActivity.kt        ← camera/mic permission flow
│   ├── SettingsActivity.kt          ← app settings
│   ├── DictionaryActivity.kt        ← FSL sign reference
│   ├── data/
│   │   ├── ConversationEntry.kt     ← one message (id, text, sender, timestamp)
│   │   ├── ConversationRepository.kt← in-memory message store (session only)
│   │   └── AppPreferences.kt        ← user settings
│   ├── viewmodel/
│   │   └── ConversationViewModel.kt ← conversation state + broadcast to peer
│   ├── service/
│   │   ├── FSLRecognitionService.kt ← MediaPipe landmarks → 156-feature vector → TFLite LSTM
│   │   ├── VoskSTTService.kt        ← offline Filipino/English speech-to-text
│   │   ├── WordAssemblyLayer.kt     ← assembles fingerspelled letters into words
│   │   ├── NearbySyncService.kt     ← two-device conversation sync
│   │   └── LanguageHeuristics.kt    ← language helpers
│   └── ui/
│       ├── ConversationBubbleWidget.kt ← chat bubble (styled by Deaf/Hearing side)
│       ├── CoachMarkTutorial.kt / SpotlightView.kt ← onboarding
│       └── ... overlays, dialogs, waveform view
├── res/                             ← layouts, drawables, themes
└── assets/                          ← model files (git-ignored — see Setup)
    ├── akai_model.tflite            ← trained FSL LSTM model
    ├── actions.txt                  ← 64 class labels (one per line)
    ├── sequence_length.txt          ← model sequence length (120)
    ├── hand_landmarker.task         ← MediaPipe hand detection model
    └── pose_landmarker.task         ← MediaPipe pose detection model
```

The large Vosk Filipino voice model ships through **Play Asset Delivery** (the `models_pack` module), not in the base APK.

---

## Setup

### Requirements
- Android Studio Iguana (2023.2.1) or newer
- A **physical ARM64 Android device**, API 26+ (MediaPipe does not run on x86_64 emulators)
- The model files below (not committed to git — they're too large)

### Steps
1. Clone this repo.
2. Place the model files into `app/src/main/assets/`:

   | File | Where it comes from |
   |---|---|
   | `akai_model.tflite` | ML repo → `linkfsl-model/models/` |
   | `actions.txt` | ML repo → `linkfsl-model/models/` |
   | `sequence_length.txt` | ML repo → `linkfsl-model/models/` (value **120**) |
   | `hand_landmarker.task` | MediaPipe (see below) |
   | `pose_landmarker.task` | MediaPipe (see below) |

3. Open in Android Studio → **Sync Gradle** → **Run** on a connected physical device.

### Download the MediaPipe `.task` models
```powershell
Invoke-WebRequest -Uri "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task" -OutFile "app/src/main/assets/hand_landmarker.task"
Invoke-WebRequest -Uri "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker.task" -OutFile "app/src/main/assets/pose_landmarker.task"
```

### Release signing
Release builds are signed from a `keystore.properties` file at the repo root (git-ignored). Without it, debug builds still work and release signing is skipped gracefully.

---

## The FSL Model

The LSTM model (`akai_model.tflite`) is trained in the companion ML repo and exported to TFLite.

- **Input:** `[1, SEQUENCE_LENGTH, 156]` — a sequence of 156-element keypoint vectors (SEQUENCE_LENGTH = 120).
- **Feature vector (156 per frame):**
  - Left hand — 21 landmarks × 3 = **63** (wrist-centered, scale-normalized)
  - Right hand — 21 landmarks × 3 = **63**
  - Upper pose — nose + shoulders + left elbow × 3 = **12** (nose-centered)
  - Finger geometry — **18** (9 per hand: 5 finger-curl + 4 fingertip-spread distances)
- **Architecture:** LSTM 64 → 128 → 64 → Dense 128 → Dense(N) softmax.
- **Classes:** **64 total** — 26 FSL alphabet letters + 38 word signs (greetings/phrases, 12 colors, and *father* / *mother*).

### Accuracy

Measured on the same 64-class model, reported with two standard measures:

| Measure | Accuracy | Meaning |
|---|---|---|
| Signer-dependent (same-signer) | **99.1%** | Confirms the model trains and converges correctly (not real-world). |
| Signer-independent (LOSO) | **72.5%** | Real-world generalization to users the model has never seen, via Leave-One-Signer-Out cross-validation. This is the honest headline figure. |

> Signer-independent accuracy in the low-to-mid 70s is consistent with published FSL/sign-recognition benchmarks (e.g. LSA64 ≈ 74%, KArSL-100 ≈ 68% at 100 classes). Number signs (1–10) were evaluated but **deferred to future work (IT333)** because FSL numeral signs share handshapes and showed high inter-signer confusion.

> The feature-extraction logic in `FSLRecognitionService.kt` **must stay identical** to the ML repo's `dataset_builder.py` / `live_recognizer.py`. A mismatch silently breaks live inference even at high validation accuracy.

---

## Notes

- **Physical ARM64 device required** — MediaPipe does not work on x86_64 emulators.
- **Model files are git-ignored** — copy them in manually as shown above.
- **After retraining**, replace `akai_model.tflite`, `actions.txt`, and `sequence_length.txt` and confirm the feature vector / sequence length still match the Kotlin constants.

---

## Version History (recent)

| Version | Highlights |
|---|---|
| **1.8.3** | Model expanded 50 → **64 classes** (added 12 colors + *father* / *mother*; numbers deferred to IT333). Sequence length 106 → 120. Filipino (Tagalog) STT preloads at startup; fixed Tagalog model load timeout on slower phones. |
| 1.8.2 | Tagalog speech-model load-timeout fix for slower devices. |
| earlier | Resizable conversation box, confidence cues, text-size setting; two-device Nearby sync; fingerspelling word assembly. |

See the [commit history](https://github.com/Desiigner101/AkAI-Android/commits/master) and merged pull requests (#5, #6) for full refactoring evidence.

---

## Related Repo

**`linkfsl-model`** — the ML training pipeline: dataset builder, keypoint extraction, augmentation, LSTM training, evaluation (LOSO), and TFLite conversion.
