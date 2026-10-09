# SAGIP — Design System

Direction: **"Photo Journal"** from [unslop.site](https://unslop.site) (Mobile Apps / Core Apps) — studied
for hierarchy, typography, spacing, density and interaction, then adapted to an emergency product. We do not
copy its brand or wording. The earlier dark "AI Chat" direction was replaced; the app now ships a **light and a
dark theme** (System / Light / Dark in Settings → Appearance).

*Last updated: 2026-10-10.*

## Principles
- **Calm and legible under stress:** generous spacing, large text, one typeface, icon-only secondary actions.
- **Paper + ink:** warm off-white paper with near-black ink; dark theme flips them. One loud colour (coral) is
  reserved for SOS and critical states.
- **Photography/illustration first:** guide art leads cards, the inline chat image and guide headers.
- **Trust cues on every answer:** severity tag, named source, related guides.

## Typography
One family — **Geist** (variable font, `res/font/geist.ttf`) — merged from the reference's three fonts.
Text colour is never baked into the type scale; it comes from `LocalContentColor`.

## Colour tokens (`ui/theme/Color.kt`)
| Token | Light | Dark | Role |
|---|---|---|---|
| `paper` | `#F4F3EE` | `#11110F` | app background |
| `card` | `#FFFFFF` | `#1C1C19` | cards, assistant bubble |
| `ink` | `#151513` | `#F4F3EE` | text, primary pills |
| `muted` | `#6D6D66` | `#A3A29A` | secondary text |
| `line` | `#D7D6CF` | `#34342F` | hairline borders |
| `acid` | `#D6FF3F` | `#D6FF3F` | active-tab accent |
| `blue` | `#3458F4` | `#8CA0FF` | links, acronym highlights |
| `coral` | `#FF5E6C` | `#FF5E6C` | SOS, emergency actions |
| `ok` | `#1F9D63` | `#3DD68C` | "Offline mode" dot, GPS source |

Severity (safety layer, not from the reference): caution `#B7791F`/`#E0A23A`, urgent `#E8590C`/`#FF8A4C`,
critical `#D92D3A`/`#FF5C66` (light/dark).

## Components (`ui/components/Components.kt`)
`ImageSlot`, `IconPill`, `PillChip`, `SeverityTag`, `CategoryCard`, `TopicCard`, `SosPill`, `CallRow`
(with "Sample" badge), `TypingBubble`. Navigation: top bar (logo, SAGIP, settings, SOS) + bottom tabs
(Home / Search / Ask / Contacts).

## Brand
Wordmark **SAGIP** (uppercase). Launcher icon and in-app logo come from `images/icon/SAGIP.jpg`; the loading
screen shows the logo, the name and its meaning (**S**mart **A**id & **G**uidance for **I**mmediate
**P**reparedness).

Screenshots of the result: [`docs/screenshots/`](screenshots/).
