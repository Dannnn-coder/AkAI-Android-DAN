# Two-Device Offline Sync — Feature Handoff

> **For any AI assistant (Claude Code) or developer picking up this branch.**
> Read this fully before touching the two-device sync code. It explains WHAT was
> built, WHY each decision was made, and HOW to test/extend it safely.

**Branch:** `feature/two-device-sync`
**Status:** Built, compiles clean, **NOT yet verified on two physical phones.**
**Goal of this feature:** Let a deaf user and a hearing user each use their OWN
phone and share one live conversation thread — **fully offline**, no internet, no
server. The existing single-device mode is preserved as an option.

---

## 1. The Core Idea (mental model)

AkAI normally runs on ONE shared phone. This feature adds an OPTIONAL second mode
where TWO phones connect **directly to each other** (Google Nearby Connections —
Bluetooth + Wi-Fi Direct under the hood) and share the conversation.

**Critical principle — only TEXT crosses the wire.** The camera, the audio, and the
ML models (MediaPipe + TFLite + Vosk) all stay on each device. When a phone
recognizes a sign or transcribes speech, it sends only the resulting
`ConversationEntry` (as JSON) to the other phone. This is what makes it:
- **Fast** (tiny payloads)
- **Private / RA 10173-compliant** (no video/audio/personal data leaves the device)
- **Offline-capable** (small text is easy to send device-to-device)

```
 DEVICE 1 (Deaf)                              DEVICE 2 (Hearing)
 Camera -> FSL (on-device) --"HELLO"(text)--> shows on both threads
 shows on both threads <--"hi"(text)-- Mic -> Vosk (on-device)
        NO INTERNET. NO SERVER. Phone-to-phone only.
```

---

## 2. Roles: Host vs Guest

- **Host** taps **"📡 Start"** → app generates a session code (e.g. `AK-4829`) →
  device **advertises** under that code and waits.
- **Guest** taps **"🔗 Join"** → enters the code → device **discovers**, matches the
  code, and connects.

Matching on the exact session code is deliberate: it stops a phone from connecting
to a random nearby AkAI device. `AK-4829` only ever connects to `AK-4829`.

---

## 3. Files Changed / Added

| File | Role |
|------|------|
| `service/NearbySyncService.kt` | **NEW.** All Nearby Connections plumbing: advertise/discover/connect/send/receive/teardown. Transport-only — it ships Strings, doesn't interpret them. |
| `data/ConversationEntry.kt` | Added `toJson()` / `fromJson()` so an entry can travel the wire and be rebuilt identically on the other phone. `fromJson()` returns null on bad input (won't crash). |
| `viewmodel/ConversationViewModel.kt` | Session control (`startSession`/`joinSession`/`endSession`), broadcasts local entries to peer, adds remote entries. Also holds `SyncMode` + `ConnectionState` enums. |
| `MainActivity.kt` | Session bar wiring, join-code dialog, Nearby runtime permissions (requested ONLY when user opts into sync), state observers. |
| `res/layout/activity_main.xml` | The session bar UI (Start / Join / status / End), inserted below the header. |
| `AndroidManifest.xml` | Bluetooth + Wi-Fi + location permissions (version-split for API ≤30 vs 31+). |
| `app/build.gradle.kts` + `gradle/libs.versions.toml` | `play-services-nearby` dependency (v19.3.0). |

**UNCHANGED:** `FSLRecognitionService`, `VoskSTTService`, `WordAssemblyLayer`, and the
whole single-device flow. This feature is purely additive.

---

## 4. The Message Flow (most important logic to understand)

Every message flows through ONE chokepoint in `ConversationViewModel`:

```
LOCAL message (this phone signs/speaks):
  addDeafMessage()/addHearingMessage()
    -> addAndBroadcast(entry)
       -> repository.addEntry() + entries.postValue()   // show on my screen
       -> if connected: nearbyService.sendMessage(json)  // send to peer

REMOTE message (arrived from the other phone):
  nearbyService.onMessageReceived
    -> addRemoteEntry(json)
       -> add to MY thread ONLY. Does NOT re-broadcast.  // prevents infinite loop
       -> dedupe by entry.id                              // prevents doubles
```

⚠️ **Two bugs this design prevents — DO NOT break these when editing:**
1. **Infinite loop:** remote entries must NEVER be re-sent. `addRemoteEntry` adds
   locally only. If you route remote messages back through `addAndBroadcast`, phones
   will ping-pong the same message forever.
2. **Duplicates:** `addRemoteEntry` skips an entry whose `id` already exists.

---

## 5. RA 10173 (Data Privacy) — enforced in code, not just promised

- Only text is transmitted; no video/audio/personal data ever leaves the device.
- Conversation lives only in memory (`ConversationRepository` = an in-memory list).
- `endSession()` disconnects the peer AND clears the conversation.
- `onCleared()` (ViewModel destroyed / app closed) calls `nearbyService.stop()`.
- Nothing is written to disk by this feature.

If you extend this, **do not persist conversation entries to disk or any server**
without revisiting the privacy claim.

---

## 6. How To Test (REQUIRES TWO PHYSICAL ANDROID PHONES)

Nearby Connections **cannot** be tested on emulators. You need two real devices
(API 26+, ARM), both with **Bluetooth AND Wi-Fi turned ON**, physically near each
other. No internet required.

**Option A — same APK on both phones (simplest):**
1. `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
2. Install that same APK on BOTH phones (allow "install from unknown sources").
3. See §7 for the model-asset requirement first, or FSL recognition won't work.

**Test steps:**
1. Phone A: tap **📡 Start** → note the code (e.g. `AK-4829`). Grant BT/Wi-Fi perms.
2. Phone B: tap **🔗 Join** → type the code. Grant BT/Wi-Fi perms.
3. Both should show **"AK-4829 • Connected ✓"** (green).
4. On A, sign or speak a message → it must appear on **BOTH** screens.
5. Tap **End** → both return to "Single device" and the thread clears.

**If it fails, capture logcat filtered by tag `NearbySyncService`** and report:
- Does discovery find the host? (`onEndpointFound`)
- Does `onConnectionResult` return `STATUS_OK`?
- Do payloads arrive? (`onPayloadReceived`)

---

## 7. REQUIRED before the app fully works: model assets (not in git)

Per the main README, these files are git-ignored and must be copied into
`app/src/main/assets/` before building, or sign recognition won't function:
- `akai_model.tflite`, `actions.txt`, `sequence_length.txt` (from the ML repo `models/`)
- `hand_landmarker.task` (MediaPipe download — see README)

The two-device UI/connection will still work without them, but FSL recognition
(the thing that produces messages to sync) needs them.

---

## 8. Known Limitations / Next Steps

- **Range:** phones must be within Nearby range (~same room). Fine for a
  face-to-face deaf↔hearing conversation; not for remote/different-location use.
- **1:1 only:** current strategy is `P2P_STAR` used for a single host+guest pair.
- **Not yet verified on hardware** — first real 2-phone test is the immediate TODO.
- If a connection drops mid-conversation, the app falls back to `IDLE` (local thread
  preserved). Reconnect flow could be added later.

---

## 9. For the AI assistant reading this

- Before editing sync code, read `NearbySyncService.kt` and the message-flow section
  above. The loop-prevention and dedupe logic are load-bearing — don't remove them.
- This is a Capstone 2 project; the human is a student. Explain WHY behind changes,
  catch edge cases early, and never claim the feature "works" until it's been
  verified on two physical phones.
