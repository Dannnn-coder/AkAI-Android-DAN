# AkAI — Android Application

A native Android app for real-time Filipino Sign Language (FSL) recognition using MediaPipe and TensorFlow Lite LSTM.

**Team:** 0xACE | 2526-sem2-IT332-10 | CIT-U BSIT 4th Year

---

## What It Does

- **FSL Sign Recognition** — detects FSL hand gestures using the device camera in real time
- **Fingerspelling** — recognizes FSL alphabet letters one by one and assembles them into words
- **Speech-to-Text** — converts hearing user's spoken response to text (coming soon)
- **Shared Conversation Thread** — both users see the full conversation on one shared screen

---

## Tech Stack

| Component | Technology |
|---|---|
| Language | Kotlin |
| UI | XML layouts |
| Camera | CameraX 1.3.1 |
| Hand Landmark Detection | MediaPipe Tasks Vision 0.10.14 |
| ML Inference | TensorFlow Lite 2.14.0 + select-tf-ops |
| Async | Kotlin Coroutines |
| Min SDK | API 26 (Android 8.0) |

---

## Project Structure

```
app/src/main/
├── java/com/akai/
│   ├── MainActivity.kt          ← camera, UI, mode switcher, conversation thread
│   └── FSLRecognitionService.kt ← MediaPipe + TFLite LSTM inference pipeline
├── res/
│   ├── layout/
│   │   ├── activity_main.xml        ← main screen layout
│   │   ├── item_message_deaf.xml    ← blue chat bubble (deaf user)
│   │   └── item_message_hearing.xml ← green chat bubble (hearing user)
│   └── drawable/                ← bubble shapes, avatar backgrounds
└── assets/                      ← model files (not in git — copy manually)
    ├── akai_model.tflite        ← trained LSTM model
    ├── hand_landmarker.task     ← MediaPipe hand detection model
    ├── actions.txt              ← gesture class labels
    └── sequence_length.txt      ← model sequence length
```

---

## Setup

### Requirements
- Android Studio Iguana 2023.2.1+
- Android phone with API 26+ (ARM64)
- Model files from the ML repo.

### Steps

1. Clone this repo
2. Copy these files into `app/src/main/assets/`:
   - `akai_model.tflite` — from `LINKFSL-MODEL/linkfsl-model/models/`
   - `hand_landmarker.task` — download from MediaPipe
   - `actions.txt` — from `LINKFSL-MODEL/linkfsl-model/models/`
   - `sequence_length.txt` — from `LINKFSL-MODEL/linkfsl-model/models/`
3. Open in Android Studio → Sync → Run on physical ARM device

### Download hand_landmarker.task
```powershell
Invoke-WebRequest -Uri "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task" -OutFile "app/src/main/assets/hand_landmarker.task"
```

---

## ML Model

The LSTM model (`akai_model.tflite`) is trained in the companion ML repo:
- **Architecture:** LSTM 64→128→64 → Dense 128 → Dense N_classes
- **Input:** `[1, SEQUENCE_LENGTH, 138]` — sequence of 138-feature keypoint vectors
- **Features:** Left hand (63) + Right hand (63) + Upper pose (12) = 138, wrist-centered
- **Classes:** 26 FSL alphabet letters + word gestures

---

## Important Notes

- **MediaPipe requires a physical ARM device** — does not work on x86_64 emulators
- **Model files are not in git** — too large, copy manually from ML repo
- After retraining, replace `akai_model.tflite`, `actions.txt`, and `sequence_length.txt`

---

## Related Repo

ML training pipeline: `LINKFSL-MODEL` — dataset builder, augmentation, LSTM training, TFLite conversion
