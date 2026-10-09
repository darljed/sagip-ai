# SAGIP — Knowledge Pack Authoring Guide (for an AI agent)

**Audience:** an AI agent generating new emergency guidance packs for SAGIP,
working in a **separate local folder**. The output is JSON files that a human will
later copy into `app/src/main/assets/packs/`. You do **not** touch the Android app.

**Your single job:** produce **correct, authoritative, bilingual (English +
Tagalog) emergency guidance chunks** in the exact schema below.

> ⚠️ This is an emergency app. **Accuracy and safety outweigh everything.** Never
> invent medical steps, dosages, or procedures. Every chunk must trace to a named
> authoritative source (Philippine Red Cross, DOH, WHO, NDRRMC, PHIVOLCS, PAGASA,
> AHA). If you are unsure of a step, omit it — do not guess.

---

## 0. Why the pack content IS the answer (read this first)

SAGIP runs a tiny on-device model (Gemma 3 1B). After hard lessons, the app was
redesigned so that:

- **The model only writes a 1–2 sentence calming intro.** It does NOT write the
  steps.
- **The numbered steps shown to the user are your chunk's `text`, rendered
  verbatim.** The model never touches them.

**Consequence:** the quality, correctness, and coverage of SAGIP is *entirely*
your pack content. Write the `text` as the final, user-facing instructions —
clean, numbered, panic-readable. What you write is what a person in an emergency
reads and acts on. There is no model safety net rewriting it.

---

## 1. The chunk schema (exact)

Each pack is a **JSON array** of chunk objects (the root is a list, not an
object). One chunk = one self-contained topic in one language.

```jsonc
{
  "id": "firstaid.bleeding.severe.001",   // <pack>.<topic>.<subtopic>.<seq> — globally unique
  "pack": "first_aid",                      // see allowed pack names in §3
  "topic": "severe_bleeding",               // machine key; EN & TL twins share this EXACTLY
  "lang": "en",                             // "en" or "tl"
  "title": "Severe bleeding (hemorrhage)",  // human-readable; shown as the citation label
  "severity": "critical",                   // "info" | "caution" | "urgent" | "critical"
  "tags": ["bleeding","wound","blood","hemorrhage","dugo","sugat","dumudugo"], // BILINGUAL keywords
  "text": "1. ...\n2. ...\n3. ...",         // the actual steps, numbered, \n-separated
  "source": "Philippine Red Cross / WHO First Aid Guidelines", // NAMED authority
  "personalize": ["blood_type","allergies"],// profile keys relevant to this topic ([] if none)
  "call_emergency": true                     // true for anything life-threatening
}
```

Every field is **required**. Use `[]` for empty `personalize`. Do not add extra
fields — the app ignores unknown keys but keep it clean.

---

## 2. Field rules (how each field is actually used)

### `id`
- Format: `<pack>.<topic>.<subtopic>.<seq>`, lowercase, dot-separated,
  e.g. `firstaid.stroke.recognize.001`.
- Must be globally unique across ALL packs.
- **The EN and TL twin share the same `id`.** They are distinguished only by the
  `lang` field. (Yes — two chunks with the same `id`, different `lang`.)

### `pack`
- Exactly one of the allowed pack names (§3). This is also the JSON filename.

### `topic`
- The machine key that groups a concept. **The EN and TL twin MUST have the
  identical `topic` string** — this is how the app pairs them.
- lowercase_snake_case, e.g. `heart_attack`, `heat_stroke`, `allergic_reaction`.

### `lang`
- `"en"` or `"tl"` only. Every topic needs **both**.

### `title`
- Short human label, shown to the user as the citation ("Source: {title} —
  {source}"). Write it in the chunk's own language:
  - EN: `"Heart attack (chest pain)"`
  - TL: `"Atake sa puso (sakit sa dibdib)"`

### `severity` — drives the UI banner color/label
| value | banner | use for |
|---|---|---|
| `info` | grey | general knowledge, non-urgent |
| `caution` | yellow | be careful, could worsen |
| `urgent` | orange | act soon, time-sensitive |
| `critical` | red "LIFE-THREATENING — act now" | life-threatening |

Pick the **highest** severity the situation can be. Bleeding, CPR, choking,
stroke, heart attack, anaphylaxis, drowning → `critical`.

### `tags` — THE most important field for retrieval
The app finds chunks by **keyword overlap** (no semantic search in the shipped
build). Scoring weights: **tag hit = 5 pts**, topic = 4, title = 2, body = 1.
So tags decide whether your chunk is found at all.

Rules:
- **Bilingual in EVERY chunk** (both the EN and TL twin carry EN *and* TL tags).
  A Tagalog query must be able to hit an English chunk and vice-versa.
- Include the words a **panicking lay person** would actually type, including:
  - the condition ("stroke", "atake sa puso"),
  - the symptom ("chest pain", "sakit sa dibdib", "hindi makahinga"),
  - colloquial / Taglish terms ("nahihilo", "nalunod", "kagat ng ahas"),
  - common misspellings only if very frequent.
- 6–12 tags is a good range. Single words tokenise best (the app splits on
  non-letters), but short phrases are fine — they get tokenised too.
- Do NOT rely on stopwords; these are stripped: `the and for are was with what how
  when who ang ng sa na ko ba ano may mga ay si do to is it my me in on of at a an`.

### `text` — the user-facing steps (write these as final copy)
- **Numbered steps**, one per line, separated by `\n` in the JSON string.
- Imperative, short, scannable, panic-readable. One action per step.
- 3–6 steps is ideal. Never a wall of prose.
- EN and TL twins must convey the **same instructions** (translation, not a
  different procedure).
- Use plain Tagalog/Taglish a real Filipino understands — not formal/deep Tagalog.
  (e.g. "Diinan ang sugat", not "Idiin ang nabibitak na himaymay".)
- End a `critical` chunk's last step with the help-seeking action (press until
  help arrives / get to a hospital / call for help).
- No dosages or drug names beyond what the named source states. No diagnosis.

Example `text` (stored as a single JSON string with `\n`):
```
1. Apply firm, direct pressure on the wound with a clean cloth or your hand.
2. Keep pressing continuously — do not lift to check. Add more cloth if it soaks through.
3. If possible, raise the injured area above the level of the heart.
4. Lay the person down and keep them warm to prevent shock.
5. Keep pressing until bleeding stops or help arrives. Do not remove an embedded object — press around it.
```

### `source`
- A **named** authoritative body. Preferred, by domain:
  - First aid / medical: `Philippine Red Cross`, `WHO`, `DOH`, `American Heart Association (AHA)`.
  - Typhoon / flood: `NDRRMC`, `PAGASA`.
  - Earthquake: `PHIVOLCS`, `NDRRMC`.
- You may combine, e.g. `"Philippine Red Cross / WHO First Aid Guidelines"`.
- Never write "internet" or leave it generic.

### `personalize` — profile keys this topic should surface
Only use keys the app knows (from the user profile). Allowed keys:
`allergies`, `blood_type`, `conditions`, `medications`,
`household_infant`, `household_elderly`, `household_pwd`, `household_pregnant`,
`emergency_contact`.
- Use `["allergies"]` for anaphylaxis, drug reactions.
- Use `["household_infant"]`, `["household_elderly"]`, etc. for evacuation /
  shelter topics.
- Use `["conditions","medications"]` for cardiac / asthma / diabetic topics.
- `[]` is fine for a generic topic.
- The EN and TL twin should carry the **same** `personalize` array.

### `call_emergency`
- `true` for anything life-threatening (the UI then shows the tap-to-call
  911 + emergency-contact button). When in doubt for a serious topic, `true`.

---

## 3. Allowed pack names (filenames)

Put chunks in the matching file. One file per pack, **both languages inside**.

| `pack` value | file | scope |
|---|---|---|
| `first_aid` | `first_aid.json` | medical / injury first aid |
| `typhoon_flood` | `typhoon_flood.json` | typhoon, flood, storm surge |
| `earthquake` | `earthquake.json` | earthquake, aftershock, tsunami, gas leak, trapped |
| `volcano` | `volcano.json` | volcanic eruption, ashfall, lahar ⚠️ NEW — see below |
| `fire` | `fire.json` | house fire escape, clothes on fire ⚠️ NEW — see below |
| `wilderness` | `wilderness.json` | hiking, mountain, cave, open-field, no-signal SOS ⚠️ NEW — see below |
| `workplace` | `workplace.json` | office quake/fire evacuation, worksite injury ⚠️ NEW — see below |
| `home` | `home.json` | elderly falls, child scalds, hot car, bucket drowning ⚠️ NEW — see below |
| `sea` | `sea.json` | rip current, capsized boat, stranded at sea ⚠️ NEW — see below |
| `public` | `public.json` | crowd crush, lost child ⚠️ NEW — see below |
| `survival` | `survival.json` | rule of 3s, water, fire, shelter, signals, knots, navigation, wild food, camp animals ⚠️ NEW — see below |
| `vehicle` | `vehicle.json` | blowout, brake failure, overheat, dead battery, stalled in flood, breakdown ⚠️ NEW — see below |

> ⚠️ `volcano`, `fire`, `wilderness`, `workplace`, `home`, `sea`, `public`,
> `survival`, and `vehicle`
> were added 2026-10-09. Before shipping them, a human
> must register the new pack names in the app's chip menu + repo loader, which
> are keyed to known packs — otherwise these files will never be loaded.

If a new topic doesn't fit these three, ask the human before inventing a new pack
name (the app's chip menu + repo loader are keyed to known packs).

---

## 4. The hard rules (non-negotiable)

1. **Every topic ships as a matched EN + TL pair.** Same `id`, same `topic`, same
   `severity`, same `source`, same `personalize`, bilingual `tags` in both. Only
   `lang`, `title`, `text` differ.
2. **`text` is final user-facing copy** — numbered, short, correct. What you write
   is shown verbatim.
3. **Source every chunk** to a named authority. No invented steps or dosages.
4. **Tags are bilingual and lay-person-oriented** in every chunk.
5. **Valid JSON.** Root is an array. UTF-8. Tagalog characters (ñ, é, —) are fine.
   Escape `\n` inside `text`. No trailing commas. No comments in the real files
   (the `//` above are illustrative only).
6. **One file per pack**, EN and TL chunks interleaved or grouped — order doesn't
   matter to the app, but group each topic's EN+TL together for human review.

---

## 5. High-value topics to cover (priority order)

The current corpus already has: severe_bleeding, cpr_adult, choking_adult, burns,
fracture, snakebite, shock, heart_attack, stroke, allergic_reaction, seizure,
fainting, heat_stroke, drowning, electric_shock, nosebleed, poisoning,
high_fever_child, eye_injury, cpr_child, cpr_infant (first_aid);
before_typhoon, flood_entering_home, electrical_hazard, safe_water,
when_to_evacuate, during_typhoon, after_flood, landslide, storm_surge,
leptospirosis (typhoon_flood);
during_earthquake, after_earthquake, gas_leak, trapped, tsunami_warning
(earthquake); volcano_eruption, ashfall, vog (volcano); house_fire,
clothes_on_fire, lpg_leak_home, grease_fire (fire). First-aid also has
febrile_seizure, road_crash, lightning_strike, chemical_burn; typhoon_flood also
has brownout_generator, stranded_rooftop, dengue_after_flood; earthquake also has
aftershock, elevator_trapped — batches 1–3 added 2026-10-09, see §9.
Batch 4 (2026-10-09): heat_exhaustion, amputation, marine_sting (first_aid);
evacuation_center, mold_cleanup (typhoon_flood); lost_on_trail, no_signal_sos,
trail_hypothermia, cave_emergency, open_field_storm (wilderness);
office_earthquake, workplace_fire, worksite_injury (workplace).
Batch 5 (2026-10-09): pesticide_exposure (first_aid); elderly_fall_home,
scald_child, child_in_hot_car, bucket_drowning (home); rip_current,
boat_capsize, stranded_at_sea (sea); crowd_crush, lost_child_public (public).
Batch 6 (2026-10-09): survival_priorities, finding_water, purifying_water,
making_fire, emergency_shelter, signaling_rescue, rescue_knots,
navigation_no_compass, wild_food_safety, camp_animals (survival).
Batch 7 (2026-10-09, research-backed): scorpion_sting, food_poisoning,
knocked_out_tooth, object_in_nose_ear, button_battery, firecracker_injury
(first_aid); tornado_ipoipo (typhoon_flood); electrical_fire (fire);
altitude_sickness (wilderness); robbery_response (public). Retrieval upgrades
(no new topics): `poisoning` tags += gaas/kerosene/gasolina; `dog_bite` tags
swapped generic "animal bite" for unggoy/monkey (rabies vectors).
Batch 8 (2026-10-09): tire_blowout, brake_failure, overheating_engine,
dead_battery, stalled_in_flood, roadside_breakdown (vehicle).
NOTE: `wildlife.json` (`snake_encounter`) was found in the working tree but was
NOT authored in this session — verify its origin before shipping.

**Fill these common gaps next** (a judge/user is likely to try them):

first_aid:
- `heart_attack` (chest pain) — critical
- `stroke` (FAST: face/arm/speech/time) — critical
- `allergic_reaction` / anaphylaxis — critical, `personalize:["allergies"]`
- `seizure` — urgent
- `fainting` (syncope) — caution/urgent
- `heat_stroke` — urgent
- `drowning` (rescue + after) — critical
- `electric_shock` (to a person) — critical
- `nosebleed` — caution
- `poisoning` / ingestion — urgent/critical
- `high_fever_child` — urgent, `personalize:["household_infant"]`
- `eye_injury` — urgent
- `cpr_child` / `cpr_infant` (distinct from adult) — critical

typhoon_flood:
- `during_typhoon` (shelter in place) — urgent
- `after_flood` (cleanup, disease) — caution
- `landslide` — urgent

earthquake:
- `tsunami_warning` (coastal, head inland/uphill) — critical

Do first_aid gaps first (most-tried), then the disaster additions.

---

## 6. Worked example — one complete EN + TL pair

A new `heart_attack` topic in `first_aid.json`:

```json
[
  {
    "id": "firstaid.heart_attack.recognize.001",
    "pack": "first_aid",
    "topic": "heart_attack",
    "lang": "en",
    "title": "Heart attack (chest pain)",
    "severity": "critical",
    "tags": ["heart attack","chest pain","dibdib","atake sa puso","heart","palpitation","hirap huminga","shortness of breath","sweating","arm pain"],
    "text": "1. Have the person sit down, rest, and stay calm. Loosen tight clothing.\n2. Call for emergency help immediately — this is time-critical.\n3. If they are not allergic to aspirin and it is available, have them chew one adult aspirin.\n4. If they have prescribed heart medicine (like nitroglycerin), help them take it.\n5. If they become unresponsive and are not breathing normally, start CPR and send for an AED.",
    "source": "American Heart Association (AHA) / Philippine Red Cross",
    "personalize": ["conditions","medications","allergies"],
    "call_emergency": true
  },
  {
    "id": "firstaid.heart_attack.recognize.001",
    "pack": "first_aid",
    "topic": "heart_attack",
    "lang": "tl",
    "title": "Atake sa puso (sakit sa dibdib)",
    "severity": "critical",
    "tags": ["heart attack","chest pain","dibdib","atake sa puso","sakit sa dibdib","hirap huminga","pawis","sakit sa braso","heart"],
    "text": "1. Paupuin ang tao, pahingahin, at panatilihing kalmado. Luwagan ang masikip na damit.\n2. Humingi agad ng tulong medikal — mahalaga ang oras.\n3. Kung hindi siya allergic sa aspirin at may available, panguyain siya ng isang adult aspirin.\n4. Kung may reseta siyang gamot sa puso (tulad ng nitroglycerin), tulungan siyang inumin ito.\n5. Kung mawalan siya ng malay at hindi humihinga nang normal, simulan ang CPR at ipakuha ang AED.",
    "source": "American Heart Association (AHA) / Philippine Red Cross",
    "personalize": ["conditions","medications","allergies"],
    "call_emergency": true
  }
]
```

Notice: identical `id`/`topic`/`severity`/`source`/`personalize`/`call_emergency`;
bilingual tags in both; only `lang`/`title`/`text` differ.

---

## 7. Self-check before you hand off (run this on your output)

For each file you produce, verify:

- [ ] Valid JSON, root is an array, UTF-8, no trailing commas, no `//` comments.
- [ ] **Every `topic` appears exactly twice** — once `"lang":"en"`, once `"lang":"tl"`.
- [ ] Paired twins share identical `id`, `topic`, `severity`, `source`,
      `personalize`; both have bilingual `tags`.
- [ ] `pack` matches the filename and is an allowed name.
- [ ] `severity` is one of info/caution/urgent/critical; life-threatening = critical.
- [ ] `text` is numbered steps (3–6), `\n`-separated, imperative, correct, sourced.
- [ ] `call_emergency` is `true` for every life-threatening topic.
- [ ] Tags include lay-person words a panicking user would type, in EN and TL.
- [ ] No invented medical steps/dosages; every chunk traces to a named authority.

A quick validator you can run locally (Python, no deps):

```python
import json, sys, collections
for path in sys.argv[1:]:
    data = json.load(open(path, encoding="utf-8"))
    assert isinstance(data, list), f"{path}: root must be a JSON array"
    req = {"id","pack","topic","lang","title","severity","tags","text","source","personalize","call_emergency"}
    sev = {"info","caution","urgent","critical"}
    by_topic = collections.defaultdict(set)
    for c in data:
        missing = req - c.keys()
        assert not missing, f"{c.get('id')}: missing {missing}"
        assert c["lang"] in ("en","tl"), f"{c['id']}: bad lang"
        assert c["severity"] in sev, f"{c['id']}: bad severity"
        assert isinstance(c["tags"], list) and c["tags"], f"{c['id']}: tags empty"
        assert "\n" in c["text"] or c["text"].strip().startswith("1."), f"{c['id']}: text not stepped"
        by_topic[c["topic"]].add(c["lang"])
    for topic, langs in by_topic.items():
        assert langs == {"en","tl"}, f"topic '{topic}' missing twin: has {langs}"
    print(f"{path}: OK — {len(data)} chunks, {len(by_topic)} topics (EN+TL each)")
```
Run: `python3 validate_packs.py first_aid.json typhoon_flood.json earthquake.json`

---

## 8. Handoff

Produce the three JSON files (or just the ones you added to), run the validator,
and report: topics added, total chunk count per file, and any topic you were
unsure about (so a human can verify the medical accuracy before it ships). The
human copies the files into `app/src/main/assets/packs/`.

---

## 9. Corpus maintenance notes (human-verified findings — do not regress)

### Known legacy issue: `"home"` personalize key
Three older `typhoon_flood` chunks (`before_typhoon`, `flood_entering_home`,
`when_to_evacuate`) carry `"home"` in `personalize`. `"home"` is NOT an allowed
key (§2) — the app does not know it, so it is silently ignored. Future edits to
those topics should drop `"home"` and keep only allowlisted keys
(`household_infant`, `household_elderly`, `household_pwd`, `emergency_contact`).

### Verify-before-ship list (added 2026-10-09, pending human medical review)- `heart_attack`: includes the chew-one-aspirin step (with allergy caveat), taken
  from the §6 worked example (AHA/PRC). Confirm this stays current guidance.
- `high_fever_child`: deliberately gives NO medicine dosages — confirm that
  omission (vs. adding weight-based paracetamol guidance) is the right call.
- `poisoning`: marked `critical` with "do NOT induce vomiting" — confirm severity
  choice, since mild ingestions could arguably be `urgent`.

### Batch 2 (added 2026-10-09, pending human medical review)
- first_aid core: `choking_infant` (critical), `asthma_attack` (critical),
  `dog_bite` incl. rabies/Animal Bite Treatment Center (urgent, DOH),
  `head_injury` (urgent), `diabetic_emergency` low blood sugar (urgent),
  `dehydration` with ORS (urgent, no brand/dosage named).
- first_aid minor ailments: `minor_wound`, `sprain` (RICE), `jellyfish_sting`
  (vinegar + hot-water soak), `insect_sting` (both `caution` except jellyfish
  `urgent`). Human check: jellyfish hot-water temperature wording ("as hot as
  can be tolerated") and vinegar-first order.
- typhoon_flood: `storm_surge` (critical, PAGASA) and `leptospirosis`
  (urgent, DOH — deliberately names no drug/dosage; confirm that omission).
- NEW packs `volcano` (`volcano_eruption` critical, `ashfall` urgent, PHIVOLCS)
  and `fire` (`house_fire`, `clothes_on_fire`, both critical, BFP). Both need
  app-side registration (chip menu + loader) before they take effect.

### Review resolutions (2026-10-09, agent-reviewed against AHA 2024, WHO, DOH, BFP, PHIVOLCS)
All six flagged items above were validated and are KEPT as written:
- `heart_attack` aspirin: matches 2024 AHA/Red Cross first-aid guidance (alert
  adults, nontraumatic chest pain, 162–325 mg chewed, allergy caveat).
- `high_fever_child` no-dosage: correct — weight-based dosing is unsafe in a
  static chunk; fluids + danger signs is the DOH/WHO home-care line.
- `poisoning` critical: correct — unknown ingestion + no-vomiting rule warrants it.
- Jellyfish vinegar + hot water: matches Mayo/Cleveland/WMS guidance
  (vinegar ≥30 s, 43–45 °C soak); vinegar-first is right for PH box-jellyfish risk.
- Leptospirosis drug omission: correct — DOH routes prophylaxis through
  health-center prescription ("ask the health center", no self-medication).
- `asthma_attack` critical: correct — steps triage properly; over-triage is the
  safe error direction.
Three wording fixes applied same day (EN+TL): `storm_surge` "later waves" →
"water may keep rising as the typhoon makes landfall"; `jellyfish_sting` added
"hot, not scalding — test first, extra care with children"; `head_injury`
"first hours" → "at least 24 hours" observation.

### Batch 3 (added 2026-10-09, all inside existing packs — no app changes needed)
- first_aid: `febrile_seizure` (urgent), `road_crash` (urgent), `lightning_strike`
  (critical — includes "safe to touch" myth-bust), `chemical_burn` (urgent —
  brush-off-dry-first, no neutralizing).
- typhoon_flood: `brownout_generator` (urgent — CO warning), `stranded_rooftop`
  (critical), `dengue_after_flood` (caution, DOH).
- earthquake: `aftershock` (urgent), `elevator_trapped` (urgent, BFP).
- volcano: `vog` (caution, PHIVOLCS/DOH).
- fire: `lpg_leak_home` (critical), `grease_fire` (urgent — never water, never flour).

### Batch 4 (added 2026-10-09 — offline-zone scenarios + remaining gaps)
Design note for offline zones (mountain, cave, open field): the tap-to-call
button is useless without signal, so these chunks emphasize prevention
(itinerary with a contact), signal conservation (SMS over calls, airplane mode
between tries), and no-signal distress signaling (3 whistle blasts / 3 flashes).
- first_aid: `heat_exhaustion` (caution — feeds into heat_stroke), `amputation`
  (critical — pressure + wrap part on ice, never direct), `marine_sting`
  (urchin/stonefish, urgent — tweezers + hot soak).
- typhoon_flood: `evacuation_center` (caution, DSWD), `mold_cleanup` (caution —
  never mix bleach + ammonia).
- NEW pack `wilderness` (DENR/NDRRMC/PAGASA): `lost_on_trail`, `no_signal_sos`,
  `trail_hypothermia`, `cave_emergency`, `open_field_storm` (all urgent; needs
  app-side registration like volcano/fire).
- NEW pack `workplace` (DOLE OSH/BFP/PHIVOLCS): `office_earthquake` (urgent),
  `workplace_fire` (critical), `worksite_injury` (urgent; needs app-side
  registration).

### Batch 5 (added 2026-10-09 — home + more offline zones)
- NEW pack `home` (DOH/WHO/PRC): `elderly_fall_home` (urgent — hip-fracture
  signs + prevention), `scald_child` (urgent), `child_in_hot_car` (critical),
  `bucket_drowning` (critical — timba/drum reality).
- NEW pack `sea` (Philippine Coast Guard): `rip_current` (critical — swim
  parallel), `boat_capsize` (critical — stay with the boat), `stranded_at_sea`
  (urgent — never drink seawater).
- NEW pack `public` (PNP/NDRRMC/DSWD): `crowd_crush` (critical — boxer stance,
  diagonal movement), `lost_child_public` (urgent).
- first_aid: `pesticide_exposure` (urgent — farming zones; strip, rinse 15–20
  min, save the label).
All three new packs need app-side registration (chip menu + loader).

### Batch 6 (added 2026-10-09 — survival hacks & tricks)NEW pack `survival` (US Army FM 21-76 / WHO / DOH / DENR / BFP / NDRRMC):
`survival_priorities` (info — Rule of 3s + S.T.O.P.), `finding_water`
(transpiration bag, dew, downhill clues — never seawater/urine/alcohol),
`purifying_water` (boil / SODIS sun-bottle / bleach), `making_fire` (teepee
build + drown-stir-feel extinguishing), `emergency_shelter` (site selection +
ground insulation first), `signaling_rescue` (urgent — rule of threes, mirror,
ground marks), `rescue_knots` (bowline + clove hitch, test-low-first warning),
`navigation_no_compass` (stick-shadow, North Star, moss myth-bust),
`wild_food_safety` (strict no-unknown-foraging rule — edibility tests called
out as unreliable), `camp_animals` (shake shoes, no feeding, back away).
Deliberately excluded: plant/mushroom ID guides (misidentification kills) and
friction fire-starting (unreliable for lay users). Needs app-side registration.

### Batch 7 (added 2026-10-09 — research-backed unexpected scenarios)
Chosen from web research on PH injury patterns (falls #1 home injury; hundreds
of yearly firecracker injuries, mostly minors, illegal boga/piccolo/5-star;
frequent DOH road-crash tolls; button-battery 2-hour burn window; increasing
ipo-ipo events):
- first_aid: `scorpion_sting` (caution), `food_poisoning` (caution + ORS),
  `knocked_out_tooth` (urgent — 60-minute window, milk storage), 
  `object_in_nose_ear` (caution — mother's-kiss + live-insect oil rules),
  `button_battery` (critical — 2-hour race, guarded honey note for >1 yr),
  `firecracker_injury` (urgent — DOH Iwas Paputok).
- typhoon_flood: `tornado_ipoipo` (urgent — lowest room, never overpasses).
- fire: `electrical_fire` (urgent — octopus wiring, never water).
- wilderness: `altitude_sickness` (urgent — Pulag-relevant, descend on worsening).
- public: `robbery_response` (urgent — comply, observe, report; PNP).
Pending human review: button-battery honey wording and tornado severity
(urgent vs critical for direct hits).

### Batch 8 (added 2026-10-09 — vehicle emergencies)
NEW pack `vehicle` (LTO Driver's Manual / MMDA / NDRRMC): `tire_blowout`
(critical — no swerve, no brake-slam), `brake_failure` (critical — pump,
downshift, gradual handbrake, guardrail last resort), `overheating_engine`
(urgent — heater-full trick, never open hot radiator cap), `dead_battery`
(caution — exact cable order + reverse removal), `stalled_in_flood` (urgent —
never restart, abandon if rising), `roadside_breakdown` (urgent — EWD triangle,
stay-inside-on-highways rule). Needs app-side registration.
