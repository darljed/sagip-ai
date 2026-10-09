# SAGIP — Submission answers (Cerebral Valley)

Copy-paste sheet for the AppBuildersPH Hackathon 2026 submission form. Deadline: **10:00 AM, Sat 10 Oct 2026**, no
extensions. Fields marked **TODO** need a human before submitting.

*Last updated: 2026-10-10.*

## The project

| Field | Answer |
|---|---|
| **Project name** | SAGIP — Smart Aid & Guidance for Immediate Preparedness |
| **Short description** | An offline emergency assistant for the Philippines. A real LLM (Gemma 4 E2B, on the phone GPU) answers first-aid, disaster and survival questions in English and Tagalog from 112 curated, source-cited guides, personalised with a profile that never leaves the device — in airplane mode, when typhoons take the signal down. |
| **Team name** | **TODO** — enter exactly as it appears on the official participant list |
| **Members** | Darl Jed Matundan (solo) — **TODO** confirm the exact spelling on the official list |
| **GitHub repository** | https://github.com/darljed/sagip-ai (public; default branch `main`, mirrored on `master`) |
| **Hardware tested on** | Samsung Galaxy S23 Ultra (SM-S918B), Snapdragon 8 Gen 2, 12 GB RAM, Android 16 |

## The proof

| Field | Answer |
|---|---|
| **Demo video (~1 min)** | **TODO** — record the 60-second script in the README (airplane mode on → bleeding → Tagalog flood → allergies → "call my wife") |
| **Screenshots** | [`docs/screenshots/`](screenshots/) — `hero.jpg` is the combined image |
| **X / LinkedIn video URL** | **TODO** — post the video, tag **Devin / Cognition**, include **#AppBuildersPH** |
| **What runs locally** | Everything that matters: LLM inference (Gemma 4 E2B via LiteRT-LM on the phone GPU), retrieval over the 112 bundled guides, prompt personalisation, guardrails, phone-number lookup, GPS → area lookup (bundled coordinates), illustrations, chat history and the user's health profile. |
| **What requires internet** | Nothing at runtime — the app declares **no `INTERNET` permission** (verified in the merged manifest). One-time setup only: downloading the 2.4 GiB model file to a computer. Two optional Android system services may use a connection outside our app's control: voice input (offline only if the phone has its offline speech pack) and turning GPS into a place name. Typing, answers and all guides are fully offline. |

## Why does this product benefit from running AI locally?

Emergencies and network outages happen together — typhoons, floods and earthquakes take down Philippine cell towers
exactly when people need first-aid and evacuation guidance. A cloud assistant is a dead icon at that moment, so local
inference is the product, not an optimisation: remove the on-device model and nothing is left to use when the signal
drops. It is also private (allergies, medications, conditions, home area and emergency contact are put in the prompt on
the device and never leave it), fast (first word in about a second on the phone GPU) and free to run (no per-request
cost). Grounding a small local model only in vetted, source-cited guides makes it more predictable for safety-critical
steps than a large remote model improvising.

## The disclosures

| Item | Answer |
|---|---|
| **Models used** | Google **Gemma 4 E2B** (`gemma-4-E2B-it.litertlm`, on-device, GPU). Optional fallbacks present in code: Gemma 3n E2B, Gemma 3 1B INT4 (not needed for the demo). Image models used at development time only: `google/gemini-2.5-flash-image`, `google/gemini-3.1-flash-lite-image` (via OpenRouter). |
| **Technologies & frameworks** | Kotlin 2.2.21, Jetpack Compose, AndroidX, Gradle, **LiteRT-LM** `litertlm-android` 0.14.0, Android `SpeechRecognizer` / `LocationManager` / Telecom, Geist font (SIL OFL). |
| **APIs & cloud services** | None at runtime. OpenRouter was used at development time to generate topic illustrations. |
| **Existing code & assets** | Open-source libraries above; Gemma weights under the Gemma terms. The plan (`PLAN.md`) and the first three seed knowledge packs (first aid, typhoon/flood, earthquake; 32 chunks) were drafted just before the clock started — **TODO confirm this statement is accurate**; everything else (the app, 100+ more topics, retrieval, prompts, UI, contacts, tests, art, tooling) was built during the hackathon — see the git history from Fri 9 Oct. |
| **AI-generated assets** | 121 PNG topic illustrations (OpenRouter models above), reviewed only in part; app icon and logo generated with unslop.site tooling; 13 category covers hand-authored as SVG. |
| **AI development tools** | kiro-cli (Claude Sonnet) and opencode as coding assistants. Devin / Cognition was not used. |

## Judging-criteria map

| Criterion | Where to look |
|---|---|
| Problem & Usefulness (25%) | README "Why this exists"; target user = any Filipino with a phone; 13 packs, 112 topics, EN + TL |
| Local AI Implementation (25%) | README "How it works" + "What runs locally"; `llm/LlmEngine.kt`, `data/PromptBuilder.kt`; airplane-mode demo |
| Technical Execution (20%) | GPU inference with watchdog + fallback, 109 unit tests, `./gradlew :app:testDebugUnitTest` |
| Innovation (15%) | Grounded generation over a bilingual curated corpus, profile-aware answers, deterministic phone-number handling, direct "call my wife" |
| Product & Demo Quality (15%) | `docs/screenshots/`, the 60-second script in the README |

## Before you press submit

- [ ] Repo is **public** and `main` has the final commit (code freezes at 10:00 AM).
- [ ] Team name and member names match the official list exactly.
- [ ] Demo video recorded (~1 min), posted to X or LinkedIn with **#AppBuildersPH** and a **Devin / Cognition** tag; URL pasted above.
- [ ] Re-read the "existing code & assets" row and confirm it is accurate.
- [ ] Phone charged, model on the device (`./scripts/push-model.sh`), airplane-mode demo rehearsed; one team member on site at Cyberzone, SM Makati by 12:00 PM.
