# SAGIP — Design System

Adapted from the unslop.site "AI Chat" reference (Mobile Apps / Everyday Utilities).
We study its hierarchy, type, color, spacing, density, borders, and interaction
vocabulary — and adapt to SAGIP's emergency-assistant content. We do **not** copy
its brand ("Aria") or wording. Values below are the exact computed CSS from the
reference, remapped to SAGIP semantics.

---

## 1. Principles borrowed from the reference
- **Dark, calm canvas**; content floats as cards. Reads well in low light / emergencies.
- **Soft-white text** (not pure white) to reduce glare.
- **One expressive accent** (the AI "orb" gradient) used sparingly for the live/voice moment.
- **Asymmetric chat bubbles**; user = solid accent, assistant = translucent card.
- **Tiny meta labels** with `·` separators carry status + provenance.
- **Pill input** with mic + send; voice is a first-class, central gesture.
- **Structured answer blocks** (the itinerary list → our numbered steps) with action chips.

## 2. Color tokens (remapped to SAGIP)

| Token | Value | Role |
|---|---|---|
| `canvas` | deep indigo→black gradient `#0B0A1A → #050410` | app background |
| `surface` | `rgba(255,255,255,0.06)` | assistant card / raised surfaces |
| `surface-strong` | `rgba(255,255,255,0.08)` | input bar, chips |
| `text` | `rgb(232,234,255)` `#E8EAFF` | primary text (soft lavender-white) |
| `text-dim` | `rgba(232,234,255,0.5)` | meta labels, timestamps, source line |
| `accent` | `#7C5CFF` (rgb 124,92,255) | user bubble, send button, primary |
| `accent-2` | `#4CC8FF` (rgb 76,200,255) | gradient partner for the orb |
| `accent-soft` | `rgba(124,92,255,0.18)` | chip bg, selected states |
| `ok` | `#7BE3A8` (rgb 123,227,168) | **"Offline · Ready" status dot** (was "Online") |

### Severity palette (SAGIP-specific, NOT in reference — our safety layer)
| Severity | Color | Use |
|---|---|---|
| `info` | `#8AA0C0` grey-blue | general tips |
| `caution` | `#F2C94C` amber | preparedness |
| `urgent` | `#F2994A` orange | act soon |
| `critical` | `#EB5757` red | life-threatening banner |

## 3. Typography
- Family: system stack `-apple-system, "SF Pro", system-ui, sans-serif`
  → Android: **Roboto / system default** (the Compose equivalent).
- Body message: **15px**, line-height ~1.4.
- Meta label: **~11–12px**, `text-dim`, letter-spacing slight, `·` separators.
- Header title: ~15px semibold.
- Numbered steps: 15px, bold step number, regular body (mirror the "Day 1 · …" treatment).

## 4. Shape & spacing
- **User bubble radius:** `20px 20px 6px 20px` (tail bottom-right).
- **Assistant card radius:** `20px 20px 20px 6px` (tail bottom-left).
- **Bubble padding:** `12px 16px`. **Max width:** `78%`.
- **Input pill radius:** `22px`, padding `10px 14px`.
- **Chips:** radius `999px`, `accent-soft` bg.
- **Orb / FAB:** circle, radius `50%`; glow shadow (see below).
- Base spacing unit: 8px grid; message vertical gap ~16px.

## 5. Elevation / glow
- Assistant card shadow: `rgba(0,0,0,0.18) 0px 22px 70px`.
- Orb glow: `rgba(124,92,255,0.6) 0 0 60px, rgba(76,200,255,0.3) 0 0 100px`.
- Status dot glow: `rgb(123,227,168) 0 0 8px`.

## 6. Interaction vocabulary (adapted)
- **Reference "Listening · tap orb to stop"** → SAGIP voice input orb with the same
  center-stage treatment (ties to our voice onboarding/input feature).
- **Reference "drafted in 1.4s"** meta → SAGIP **"from Philippine Red Cross"** provenance
  line under the assistant label (our trust/citation feature).
- **Reference "Online" green dot** → SAGIP **"Offline · Ready"** green dot — reframes the
  SAME visual as our core differentiator (works with no signal).
- **Reference action chips ("+ Add to itinerary", "Refine")** → SAGIP **quick-action
  chips** ("Call [contact]", "Next step", "Show in Tagalog").
- **Reference structured list** → SAGIP **numbered emergency steps** + severity banner.

## 7. SAGIP-specific additions the reference lacks
- **Severity banner** above critical answers (red, bold, "⚠ Life-threatening").
- **Source citation line** on every assistant message.
- **Language toggle** EN/TL in header.
- **Quick-action emergency tiles** on the home/empty state (CPR, Choking, Bleeding, Flood, Earthquake).

---

*Study the reference, keep SAGIP's voice. Accessible (contrast ≥ 4.5:1 for body text
on canvas — verify E8EAFF on #0B0A1A passes), responsive, production-ready.*
