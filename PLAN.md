# SAGIP — Smart Aid & Guidance for Immediate Preparedness

> **Offline-first emergency AI assistant for the Philippines.**
> "When the internet dies, your AI doesn't."

A native Android app that gives life-critical guidance — first aid, typhoon/flood,
and earthquake response — running **entirely on-device** with zero connectivity.
Grounded in curated, cited emergency knowledge (RAG), personalized to the user,
and bilingual (English + Tagalog).

---

## 1. Why SAGIP wins

- **Unbeatable demo:** turn on airplane mode on stage — it still works.
- **Offline is the point,** not a privacy nice-to-have. Typhoons kill signal in PH every year.
- **Trust-engineered:** every answer cites its source, flags severity, and admits what it doesn't know.
- **Localized hard:** Tagalog + PH disaster reality — impossible to copy from a foreign template.
- **Personalized:** advice adapts to the user's allergies, conditions, household, and emergency contact.

---

## 2. Locked specification

| Decision | Choice |
|---|---|
| Platform | Native Android — **Kotlin + Jetpack Compose** |
| Runtime | **LiteRT-LM / MediaPipe LLM Inference API** |
| Primary model | **Gemma 4 E2B** (best Tagalog, newest on-device model) |
| Fallback model | **Gemma 3 1B INT4** (529MB, proven, ~2585 tok/s prefill) — decide at H1:30 |
| Third fallback | Phi-3-mini (English-only emergencies) |
| Retrieval | **Full on-device RAG** (embeddings + vector store) |
| Embedder | **EmbeddingGemma** (Gemma-family, on-device); fallback = MediaPipe text embedder |
| Vector store | sqlite-vec or ObjectBox |
| Knowledge packs | First Aid · Typhoon/Flood · Earthquake |
| Personalization | In MVP — **chat-style onboarding** (+ voice option), profile injected into answers |
| Language | **English + Tagalog (non-negotiable)** |
| Dev model | Kiro writes code, user directs |
| Clock | 12 hours straight, toolchain starts at zero |
| Primary device | Samsung S23 Ultra (8 Gen 2) — USB debugging on |
| Backup device | Redmi Tab 2 Pro — USB debugging on |
| Build target | Real device over USB (skip emulator — saves 8GB-Mac RAM) |

### Two safety valves (agreed)
1. **Full RAG → CAG** (cache-augmented: keyword/section lookup + prompt injection) if RAG not green by **H7:00**. Identical demo, ~45 min swap.
2. **Gemma 4 E2B → Gemma 3 1B** if the newest-model tooling fights us >45 min at **H1:30**.

---

## 3. Feature set

### MVP (build first — these ARE the demo)
1. Grounded Q&A over curated emergency KB (on-device RAG).
2. Offline-by-design — zero network calls.
3. Curated knowledge packs: First Aid, Typhoon/Flood, Earthquake (EN + TL).
4. Fast, scannable answers — numbered steps, not paragraphs.
5. **Personalization** — chat-style onboarding, profile injected into every answer.
6. Source citations on every answer.
7. Severity triage banner ("⚠️ life-threatening — call 911 when signal returns").
8. Quick-action tiles (CPR, choking, severe bleeding).

### Good-to-have (post-MVP, in priority order)
9. Voice input/output (hands-free).
10. Extra quick-action tiles.
11. Offline shelter/evac list (bundled static data).
12. Printable/shareable checklist.

---

## 4. Personalization profile (onboarding fields)

Stored locally (DataStore/SQLite), gathered via chat-style onboarding (voice optional):

- Full name
- Blood type
- Allergies (esp. drug allergies — affects first-aid advice)
- Chronic conditions / current medications
- Emergency contact (name + number — used in "call when signal returns")
- Home barangay/city (localized evac framing)
- Preferred language (English / Tagalog)
- Household flags: infants, elderly, PWD, pregnant members

**Payoff in answers:** "You're allergic to penicillin — avoid X"; "You have an infant
at home — prioritize…"; "Call [emergency contact] when signal returns."

---

## 5. The 12-hour plan

Philosophy: **get a deployable app on the real phone ASAP**, then layer features —
each one committed and demoable before the next. Never be one overrun away from nothing.

### Phase 0 — Toolchain & "hello phone" (H0:00–H1:30)
- Install Android Studio + SDK + NDK; create Kotlin/Compose project.
- Add MediaPipe GenAI / LiteRT-LM dependency.
- Deploy blank Compose app to S23 over USB (Redmi as backup).
- ✅ **Checkpoint:** app launches on the real phone. Nothing proceeds until green.

### Phase 1 — LLM talks, on-device (H1:30–H3:00)
- Push **Gemma 4 E2B** to device; load via LiteRT-LM/MediaPipe.
- Hard-coded prompt → streamed response in Compose.
- 🚦 **Model gate:** if Gemma 4 tooling fights >45 min → fall back to **Gemma 3 1B**.
- ✅ **Checkpoint:** phone generates text **in airplane mode**. Commit (proof-of-life).

### Phase 2 — Knowledge packs + ingestion (H3:00–H4:00)
- Load the 3 pre-built packs (prepared pre-clock) as app assets.
- ✅ **Checkpoint:** packs load as structured chunks.

### Phase 3 — Full RAG pipeline (H4:00–H7:00) ← riskiest
- EmbeddingGemma → embed all chunks on first launch → store (sqlite-vec/ObjectBox).
- Query → embed → top-k similarity → inject chunks → grounded answer **with citations**.
- ✅ **HARD CHECKPOINT (H7:00):** grounded, cited answer to "someone is bleeding badly."
  - 🚦 **If RED → fall back to CAG immediately.** No heroics past H7.

### Phase 4 — Personalization + chat onboarding (H7:00–H9:00)
- Local profile store.
- Chat-style onboarding: LLM-guided Q&A fills profile fields; parse to structured profile.
- Inject profile into every answer.
- ✅ **Checkpoint:** a saved profile visibly changes an answer. (Voice deferred to Phase 6; form fallback if chat-parse flaky.)

### Phase 5 — Tagalog + trust UI (H9:00–H10:30)
- EN/TL toggle; Tagalog answers driven by Tagalog pack content.
- Trust/UX: numbered steps, severity banner, citations, "call [contact] when signal returns," quick-action tiles.
- ✅ **Checkpoint:** a Tagalog query returns a cited, personalized, well-formatted answer.

### Phase 6 — Stretch, pick ONE (H10:30–H11:15)
- Priority: voice onboarding/input → extra tiles → shelter list. Do one, commit, stop.

### Phase 7 — Demo rehearsal + buffer (H11:15–H12:00)
- Script 3 queries: 1 EN first-aid, 1 TL typhoon, 1 personalized.
- Airplane-mode-on-stage rehearsal, cold, twice.
- Backup: screen-recorded run in case live phone fails. Final commit + tag.

---

## 6. Cut-line (what dies first if behind, in order)
1. Voice onboarding → plain chat, or a form.
2. Full RAG → CAG (hard trigger at H7).
3. Chat onboarding → simple form.
4. Third pack (Earthquake) → ship 2.
5. English-only demo with Tagalog "coming" → **last resort** (Tagalog is the edge).

**Never cut:** offline proof (airplane mode), citations, severity banner, personalization affecting an answer. Those four *are* the winning demo.

---

## 7. Edge over copycat teams (pick 2–3, already baked in)
1. **Tagalog + PH disaster localization** — the primary moat.
2. **Trust engineering** — citations + severity + "I don't know" honesty.
3. **Demo theater** — airplane mode on stage, visceral scenario, rehearsed cold.
4. **Format for panic** — big numbered steps, severity banner, call-when-signal footer.
5. **Named authoritative sources** — Red Cross, DOH, NDRRMC, WHO.
6. **One extra sense** — voice OR image, only if ahead.
7. **Personalization** — tailored advice + their emergency contact.

---

## 8. Demo script (rehearse cold)
1. **Open with airplane mode ON** (show the toggle). "No internet. No cloud. Watch."
2. **Query 1 (EN, first aid):** "Someone is bleeding heavily and feeling dizzy."
   → numbered steps, severity banner, citation, personalized allergy note.
3. **Query 2 (TL, typhoon):** "May baha na papasok sa bahay, ano gagawin ko?"
   → Tagalog steps, cited, household-aware (infant/elderly).
4. **Query 3 (personalized):** show the profile, then an answer that uses blood type /
   emergency contact ("Call [name] when signal returns").
5. **Close:** "Built in the Philippines, for the moment the Philippines needs it most —
   when the signal is gone."

---

## 9. Pre-clock work (highest leverage, done BEFORE H0)
- [x] Finalized plan (this doc)
- [x] 3 knowledge packs (EN + TL), pre-chunked JSON, with sources + severity tags
- [x] Chunk schema + personalization-injection design

---

## 10. Build progress log

### Phase 0 — toolchain & hello-phone ✅
Kotlin/Compose project builds via Gradle CLI (no Android Studio needed), installs +
launches on S23 Ultra. SAGIP design system (dark canvas, orb, Offline-Ready dot)
rendering. Offline-by-design: no INTERNET permission in the manifest.

### Phase 2–5 (built during a model-download block) ✅
Entire app except inference, test-backed:
- Data layer + pack ingestion (`PackRepository`), 7 tests.
- CAG retrieval (`KeywordRetriever`, RAG-ready `Retriever` seam) + relevance gate, 10 tests.
- Personalization prompt builder (`PromptBuilder`), 12 tests.
- Real chat UI (bubbles, severity banner, citations, call line, quick-action tiles).
- **29 unit tests, 0 failures.**

### Phase 1 — real on-device LLM ✅
- **Model: Gemma 3 1B INT4 (~584MB), sourced via AI Edge Gallery (Google CDN).**
  Gemma 4 E2B (2.6GB) **OOMs on the 8GB S23** (only ~2.7GB free) — kept as a
  secondary in `ModelConfig` for higher-RAM devices. HuggingFace was throttled
  (13h ETA) so the Gallery's Google-hosted copy was used instead.
- Hard-won fixes: async engine load off the main thread; model file perms `644`
  in `/data/local/tmp/llm` so the app uid can `open()` it; `FLAG_KEEP_SCREEN_ON`.
- MediaPipe `tasks-genai:0.10.27`: sampling params live on the **Session** API,
  streaming via `ProgressListener` (verified by decompiling the AAR).

### Small-model tuning ✅ (verified on device)
- Retrieval relevance gate (`RELEVANCE_RATIO=0.5`) → one relevant source, no loose drag-in.
- Prompt rewritten for a 1B: guidance steps front-and-center, language instruction
  last (recency) → **real Tagalog numbered first-aid steps**, not rule-parroting.
- Emergency-contact line rendered deterministically by the UI (always correct).
- Known minor 1B artifacts: occasional duplicated step; acceptable.

### Model note for the demo
- **Gemma 3 1B is the demo model** — stable on 8GB, strong enough because RAG grounds
  every answer in trusted Tagalog pack content.
- If a higher-RAM device is used, dropping a Gemma 4 E2B `.litertlm` into
  `/data/local/tmp/llm` auto-upgrades via `ModelConfig` (no rebuild).

### Next
- [ ] On-device check: typed TL query + an EN-profile query (confirm language switch).
- [ ] Phase 4: chat-style onboarding + profile persistence (currently DEMO profile).
- [ ] Phase 5/6: polish, optional voice, demo rehearsal with airplane mode.

---

*SAGIP — Architected in the Philippines, for Filipinos, offline-first.*

---

## Update 2026-10-10 — pivot + fine-tuning

* Runtime: LiteRT-LM (`litertlm-android` 0.14.0, Kotlin 2.2.21) + **Gemma 4 E2B** on GPU (TTFT < 1 s, 6–10 s answers). MediaPipe tasks-genai removed.
* Product: packs are browsable (Home → categories → guides → detail), Search, and **Ask** is a grounded support agent
  with related-guide cards, chat history (local, delete/clear), and an animated typing bubble.
* Guardrails: retrieval floor + stem rules; prompt SCOPE gate → `OFF_TOPIC` sentinel → fixed refusal; phone-number
  requests answered from `contacts.json` (never by the model); "call my mom/wife" dials the saved emergency contact.
* Contacts: area-based (`contacts.json` → `areas[]`), current area = GPS/saved home, only entries with numbers shown,
  `sample:true` entries badged. Samples exist for Makati and San Pablo. Replace with official numbers.
* Debug: `adb shell am broadcast -a dev.darl.sagip.DEBUG_ASK --es q "<question>" -p dev.darl.sagip` (and `--es to home|ask|contacts|sos|category:<id>|topic:<pack:topic>`), logs tag `SagipTest`/`SagipGen`.

## Final state (2026-10-10, submission)

* **Shipped:** native Android app, Gemma 4 E2B on the phone GPU via LiteRT-LM 0.14.0, 13 packs / 112 topics / 224 chunks (EN+TL),
  illustrated guides, profile-aware grounded chat with follow-up memory, direct "call my wife", GPS-aware contacts,
  light/dark themes, full app reset, 109 unit tests.
* **Differences from the plan above:** the 12-hour phase plan and the Gemma 3 1B / "verbatim steps only" design were
  superseded — the model now writes the answer from the retrieved guides (verbatim steps remain only as the stall/loop
  fallback). Persistence is `SharedPreferences` JSON (no DataStore). The mascot was dropped from the UI.
* **Not done / known limits:** voice input only verified for plumbing (no real-speech test); Makati and San Pablo
  contact numbers are samples; some AI-generated illustrations have imperfect lettering (`docs/IMAGE-MANIFEST.md`).
* Submission material: `README.md`, `docs/SUBMISSION.md`, `docs/JUDGE-BRIEFING.md`, `docs/screenshots/`.
