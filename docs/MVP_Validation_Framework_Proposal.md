# AkAI — MVP Validation Framework Proposal

**Course:** IT411 Capstone 2 | SY 2026–27, Sem 1
**Team:** 0xACE (2526-sem2-IT332-10) | CIT-U BSIT
**Project:** AkAI — A Two-Way Filipino Sign Language Communication Assistant
**Purpose of this document:** To propose, for adviser approval, the evaluation framework, respondents, tasks, metrics, and success criteria for the ≥30-user MVP validation (Weeks 1–2 deliverable).

> **STATUS: DRAFT — pending adviser sign-off.** Per course guidance, the validation framework, instruments, respondents, metrics, and success criteria must be agreed with the adviser *before* the 30-user validation is conducted.

---

## 1. What We Need to Prove

AkAI is an **assistive communication tool**, not an e-learning system. Its core value claim is:

> *A deaf/hard-of-hearing user and a hearing user, who do not share a common language, can successfully hold a two-way conversation using a single shared device.*

Therefore our validation must produce evidence that the system **lets real users complete real communication tasks**, not merely that the software runs.

---

## 2. Chosen Evaluation Framework

### Primary: ISO 9241-11 (Usability)
ISO 9241-11 defines usability as **effectiveness + efficiency + satisfaction** within a specified context of use. It maps directly onto AkAI's value claim:

| ISO 9241-11 dimension | What it means for AkAI | How we measure it |
|---|---|---|
| **Effectiveness** | Can the two users complete the conversation accurately? | **Task Success Rate** (% of tasks completed correctly) |
| **Efficiency** | How much effort/time does it take? | **Time-on-Task** (seconds per task) |
| **Satisfaction** | How do users feel about the interaction? | **System Usability Scale (SUS)** score |

### Supporting measures
- **Task Success Rate** — % of users who complete each defined task successfully.
- **Time-on-Task** — average seconds to complete each task.
- **SUS** — standardized 10-item questionnaire → single 0–100 usability score.
- **User Acceptance Testing (UAT)** — confirms the system meets user requirements in a realistic setting (feeds Weeks 8–9 STD too).

### Why not the others
- **Teaching/Learning frameworks (SAMR, TPACK, Bloom's, etc.)** — excluded; AkAI is not educational software, so pedagogy frameworks do not fit its purpose.
- **TAM** — strong for *adoption intent*, but our primary research question is about *task performance* (can they communicate?), which ISO 9241-11 answers more directly. TAM constructs can optionally be folded into the post-test interview if the adviser wants adoption evidence.

---

## 3. Respondents (Target: ≥30)

AkAI is two-way, so respondents must represent **both sides** of a conversation, tested as **pairs**.

| Group | Target n | Profile |
|---|---|---|
| Deaf / Hard-of-Hearing signers | ~15 | FSL users; the "signing" side of the conversation |
| Hearing users | ~15 | Non-signers who speak; the "hearing" side |
| **Total** | **≥30** | Tested in **deaf–hearing pairs** (~15 pairs) |

**Recruitment notes (to finalize with adviser):**
- Source deaf/HoH participants via [school for the deaf / FSL org / community partner — TO FILL IN].
- Provide an FSL interpreter present during sessions for consent and debrief.
- Obtain informed consent; anonymize all data.

---

## 4. Validation Tasks (Context of Use)

Each pair attempts a short scripted conversation. Tasks reflect real AkAI usage.

| # | Task | Success criterion |
|---|---|---|
| T1 | Deaf user signs a greeting; hearing user reads it on the shared thread | Correct message appears; hearing user understands it |
| T2 | Hearing user speaks a reply; deaf user reads the text (STT) | Speech correctly transcribed; deaf user understands it |
| T3 | Deaf user **fingerspells** a word not in the vocabulary; confirms it into the sentence | Word assembled correctly and sent |
| T4 | Complete a full 3–4 turn exchange (e.g., greeting → question → answer) | Both users report the exchange was understood |
| T5 | Hearing user changes a setting (e.g., TTS voice / bubble color) and continues | Setting applied; conversation resumes |

*(Task list to be finalized with adviser; keep it short — 5–8 tasks max so a session stays under ~20–25 min.)*

---

## 5. Metrics & Proposed Success Criteria

| Metric | Instrument | Proposed target (confirm w/ adviser) |
|---|---|---|
| Task Success Rate | Facilitator observation sheet | **≥ 80%** of tasks completed successfully |
| Time-on-Task | Stopwatch / timestamp | Within an acceptable range per task (baseline set from pilot) |
| SUS score | 10-item SUS questionnaire | **≥ 68** (industry "above average") |
| Sign-recognition accuracy (live) | Logged predictions vs. intended sign | **≥ 85%** (matches project target) |
| STT word accuracy | Transcript vs. spoken script | **≥ 80%** (matches project target) |
| Qualitative feedback | Short post-test interview | Themes coded for the Validation Report |

> These success criteria should be **agreed with the adviser** and then written into the SPMP as the project's success metrics.

---

## 6. Instruments (attached / to prepare)
1. **Facilitator Task & Observation Sheet** — one row per task: success (Y/N), time, notes, errors.
2. **SUS Questionnaire** — standard 10 items, 5-point scale (with FSL-interpreted version for deaf participants).
3. **Post-Test Interview Guide** — 4–5 open questions (what was hard? would you use this? what's missing?).
4. **Informed Consent Form** — plain language + FSL interpretation.

---

## 7. Pre-Flight Checklist (before touching 30 users)
Run a **2–3 person pilot** first to avoid burning real participants on a preventable bug:
- [ ] App installs & runs on the exact device(s) used for testing
- [ ] Sign→text works in the actual room lighting / camera angle
- [ ] Speech→text works with the actual mic / background noise
- [ ] Fingerspell → word assembly completes end to end
- [ ] Settings changes don't crash or lose conversation state
- [ ] Data logging (predictions, timings) actually records for later analysis

---

## 8. How This Feeds the Deliverables
- **MVP Validation Report (Weeks 1–2)** ← results of this validation
- **Updated SPMP (Weeks 1–2)** ← success metrics from §5 written in as official targets
- **UAT + STD (Weeks 8–9)** ← task/observation approach reused for formal acceptance testing

---

## 9. Open Items to Confirm With Adviser
1. Approve **ISO 9241-11 primary + supporting measures** (or request TAM addition).
2. Approve **respondent mix** (deaf–hearing pairs) and **recruitment source**.
3. Approve **task list** and **success-criteria thresholds** (§5).
4. Confirm whether **ethics/consent** documentation is required by the department.
