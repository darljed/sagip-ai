# SAGIP — Chunk Schema & Personalization Design

This governs the shape of every knowledge-pack chunk and how the user profile
modifies answers. Keep packs conformant to this so the RAG + personalization code
stays simple.

---

## 1. Chunk schema

Each pack is a JSON array of chunk objects. One chunk = one self-contained,
retrievable unit of guidance (a single topic/step-group), small enough to embed
cleanly and inject without blowing the prompt.

```jsonc
{
  "id": "firstaid.bleeding.severe.001",   // <pack>.<topic>.<subtopic>.<seq>, globally unique
  "pack": "first_aid",                      // first_aid | typhoon_flood | earthquake
  "topic": "severe_bleeding",               // machine key for the topic
  "lang": "en",                             // en | tl  (TL chunks mirror EN chunks 1:1)
  "title": "Severe bleeding (hemorrhage)",  // human-readable, shown as source label
  "severity": "critical",                   // info | caution | urgent | critical
  "tags": ["bleeding","wound","blood","hemorrhage","dugo","sugat"], // retrieval keywords (bilingual)
  "text": "…the actual guidance, as scannable numbered steps…",
  "source": "Philippine Red Cross / WHO First Aid Guidelines",
  "personalize": ["allergies","blood_type","household_infant"], // profile keys that may modify this chunk
  "call_emergency": true                    // if true, UI shows "call X when signal returns"
}
```

### Field rules
- **id** — stable, unique; EN and TL versions share the stem but differ by `lang`
  (`firstaid.bleeding.severe.001` + lang field, OR suffix `.en`/`.tl` — we use the `lang` field).
- **severity** — drives the UI banner color/label:
  - `info` (grey) · `caution` (yellow) · `urgent` (orange) · `critical` (red).
- **tags** — include BOTH English and Tagalog keywords so keyword fallback (CAG) works
  in either language even before semantic search.
- **text** — numbered steps, imperative voice, panic-readable. No long prose.
- **source** — a NAMED authoritative body (Red Cross, DOH, NDRRMC, WHO, PHIVOLCS, PAGASA).
- **personalize** — which profile keys are relevant; empty array = generic chunk.
- **call_emergency** — true for anything life-threatening.

### Language pairing
- Every EN chunk has a TL twin with the same `id` stem, `topic`, `severity`, `tags`
  (tags stay bilingual in both), and `source`. Only `lang` + `title` + `text` differ.
- This 1:1 mirror lets the app answer in the user's `preferred_language` by filtering
  retrieved chunks on `lang` after semantic match.

---

## 2. Retrieval (RAG) flow

1. Embed all chunks once at first launch (EmbeddingGemma) → store vectors (sqlite-vec).
2. On query: embed query → top-k cosine similarity (k=3–5).
3. Filter retrieved chunks by `lang == profile.preferred_language`
   (fall back to EN if a TL twin is missing).
4. Inject the chunk `text` + `title` + `source` into the prompt (see §4).
5. Model synthesizes/reformats; UI renders severity banner + citations from chunk metadata.

**CAG fallback (if full RAG is cut at H7):** replace steps 1–2 with keyword match over
`tags` + `topic`; everything downstream is identical.

---

## 3. User profile object

```jsonc
{
  "name": "Juan Dela Cruz",
  "blood_type": "O+",
  "allergies": ["penicillin"],
  "conditions": ["asthma"],
  "medications": ["salbutamol inhaler"],
  "emergency_contact": { "name": "Maria", "number": "+639xxxxxxxxx" },
  "home": "Barangay San Roque, San Pablo City",
  "preferred_language": "tl",
  "household": { "infant": true, "elderly": false, "pwd": false, "pregnant": false }
}
```

Stored locally only. Never leaves the device (reinforce in the demo).

---

## 4. Personalization injection

A retrieved chunk's `personalize` array tells the prompt builder which profile facts
to surface. The system prompt template:

```
You are SAGIP, an offline emergency guide. Answer ONLY from the GUIDANCE below.
If the guidance does not cover it, say so — do not invent medical advice.
Respond in {preferred_language}. Use short numbered steps.

USER PROFILE (surface only what is relevant to the guidance):
- Blood type: {blood_type}
- Allergies: {allergies}
- Conditions/meds: {conditions}, {medications}
- Household: {household flags that are true}
- Emergency contact: {emergency_contact.name} ({emergency_contact.number})

GUIDANCE (cite the Source in your answer):
{for each retrieved chunk}
  [Source: {title} — {source}] (severity: {severity})
  {text}

Rules:
- If a retrieved chunk's severity is "critical", begin with the severity banner line.
- If any allergy/condition conflicts with the guidance, warn explicitly.
- If call_emergency is true, end with: "Call {emergency_contact.name} at
  {emergency_contact.number} as soon as signal returns, or 911."
```

### Examples of the payoff
- Chunk has `personalize:["allergies"]` + user allergic to penicillin →
  "⚠️ You're allergic to penicillin — do not take amoxicillin."
- Chunk has `personalize:["household_infant"]` + `household.infant=true` →
  "You have an infant — carry them chest-to-chest and prioritize their evacuation."
- Any `call_emergency:true` chunk → appends the user's real emergency contact.

---

## 5. File layout (assets)

```
app/src/main/assets/packs/
  first_aid.json        // EN + TL chunks (lang field distinguishes)
  typhoon_flood.json
  earthquake.json
```

One file per pack, both languages inside (filtered by `lang` at query time).

---

## 6. Content safety guardrails (baked into content, not just code)
- Every `critical` chunk ends by directing to professional help / 911.
- No dosages beyond what the named source states; no diagnosis.
- The model is instructed to refuse to freelance beyond retrieved guidance.
- Disclaimer shown once on first launch: *SAGIP gives first-aid guidance from
  trusted manuals; it is not a substitute for professional medical care.*
