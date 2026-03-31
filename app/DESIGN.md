# Design System Specification: The Kinetic Laboratory

## 1. Overview & Creative North Star
The "Creative North Star" for this design system is **The Kinetic Laboratory**. Unlike traditional IDEs that feel like rigid, spreadsheet-based utilities, this system treats the code editor as a living, breathing environment. It prioritizes "Tonal Depth" over "Lineal Separation." 

The goal is to break the "VS Code Clone" mold by moving away from 1px borders and high-contrast dividers. Instead, we use intentional asymmetry, layered translucency (Glassmorphism), and a sophisticated color palette that balances deep, nocturnal charcoals with hyper-vibrant "bioluminescent" accents. The result is an editorial-grade workspace that feels premium, focused, and high-productivity.

---

## 2. Colors & Surface Philosophy
The palette is rooted in a deep-sea navy-charcoal (`surface: #040e1f`), allowing the vibrant `primary` (#d1ff9f) and `secondary` (#ac8aff) to pop with elective intensity.

### The "No-Line" Rule
**Explicit Instruction:** 1px solid borders are prohibited for sectioning. Structural boundaries must be defined solely through background color shifts. To separate the Sidebar from the Editor, use `surface-container-low` against `surface`. To separate the Terminal, use `surface-container-highest`.

### Surface Hierarchy & Nesting
Treat the UI as a series of physical layers. Use the following tiers to define importance:
*   **Base Layer:** `surface` (#040e1f) - The main editor canvas.
*   **Recessed Layer:** `surface-container-lowest` (#000000) - For heavy background tasks or inactive panels.
*   **Elevated Panels:** `surface-bright` (#1b2c47) - For floating modals or active popovers.
*   **Nesting:** Place a `surface-container-low` explorer item on top of a `surface-container` sidebar to create a "soft lift" without a shadow.

### The "Glass & Gradient" Rule
*   **Sidebars & Overlays:** Apply `surface-container-low` with a 70% opacity and a `20px` backdrop-blur. This allows code colors to bleed subtly into the UI periphery, creating an integrated feel.
*   **Signature Textures:** For primary CTAs or "Running" states, use a linear gradient from `primary` (#d1ff9f) to `primary_container` (#97fc00) at a 135-degree angle. This adds a "soul" to the UI that flat colors cannot achieve.

---

## 3. Typography: The Editorial Monospace
The typography system juxtaposes the technical precision of Monospace with the approachable clarity of a high-end Sans-Serif.

*   **Display & Headlines (`Space Grotesk`):** Used for project titles and empty-state headers. Its quirky, geometric terminals reinforce the "slightly unique" brand personality.
*   **UI & Labels (`Inter`):** Used for all menus, buttons, and tooltips. It is the workhorse that ensures legibility at small scales (`label-sm: 0.6875rem`).
*   **The Code (Crisp Mono):** While not explicitly in the token set, code must be rendered in a high-legibility mono font with generous line-height (1.6) to prevent eye strain against the dark `surface`.

---

## 4. Elevation & Depth
In this system, depth is a product of light and layering, not structural lines.

*   **The Layering Principle:** Stacking determines hierarchy. An active file tab should be `surface-container-high`, while inactive tabs remain `surface-container-low`.
*   **Ambient Shadows:** For floating elements (like a Command Palette), use a `24px` blur shadow with 6% opacity, using a tinted color derived from `on_surface` (#dbe6fe). Never use pure black shadows.
*   **The "Ghost Border" Fallback:** If accessibility requires a border, use `outline_variant` at 15% opacity. This "Ghost Border" provides a hint of a container without breaking the fluid aesthetic.
*   **Glassmorphism:** Use for the Activity Bar and Status Bar. By using semi-transparent `surface_container_lowest`, the editor feels lighter and less claustrophobic than traditional "boxy" IDEs.

---

## 5. Components

### Buttons
*   **Primary:** Gradient of `primary` to `primary_container`. Text color: `on_primary_fixed` (#274700). Radius: `md` (0.375rem).
*   **Secondary:** Ghost style. No background, `secondary` (#ac8aff) text, and a `secondary` ghost border (20% opacity).
*   **Interaction:** On hover, primary buttons should "glow" subtly using a `primary` tinted shadow at 10% opacity.

### Input Fields
*   **Style:** `surface_container_highest` background. No border.
*   **State:** On focus, a subtle `1.5` (0.3rem) bottom-bar of `tertiary` (#47c4ff) appears, rather than a full-box highlight.

### Chips (Language Tags / Git Branches)
*   **Design:** Pill-shaped (`full` roundedness). Use `surface_variant` for the background with `on_surface_variant` text.
*   **Scale:** Use `label-md` for text.

### Cards & Lists (The File Explorer)
*   **Rule:** Forbid divider lines. 
*   **Separation:** Use `4` (0.9rem) vertical whitespace between logical groups.
*   **Active State:** Use a `primary_dim` vertical "indicator" (2px wide) on the far left of the active list item, with a subtle `surface_bright` background shift.

### Custom Component: The "Biolume" Scrollbar
Standard scrollbars are hidden. In their place, a slim (4px) track using `surface_container_highest` with a `primary` thumb that glows slightly when hovered, mimicking a bioluminescent trail.

---

## 6. Do's and Don'ts

### Do:
*   **Do** use `secondary` (Electric Violet) for logic-related UI elements (e.g., debug icons, breakpoints).
*   **Do** use `tertiary` (Blue) for informative states and background tasks.
*   **Do** embrace asymmetry. Allow the sidebar to be wider or the editor to be offset to create an "editorial" layout.

### Don't:
*   **Don't** use 100% opaque, high-contrast borders. It kills the "Kinetic Laboratory" vibe.
*   **Don't** use sharp corners. Every element must adhere to the `md` (0.375rem) or `lg` (0.5rem) roundness scale to maintain the "soft dark" aesthetic.
*   **Don't** use pure white (#FFFFFF) for text. Always use `on_surface` (#dbe6fe) to reduce eye fatigue and maintain the sophisticated tonal range.

### Accessibility Note:
While we use subtle tonal shifts, ensure the contrast ratio between text (`on_surface`) and its container (`surface-container-low`) always meets WCAG AA standards. If a background shift is too subtle for a specific monitor, the "Ghost Border" at 20% opacity is your mandatory fallback.

---

## 7. Приложение Spraute Studio (Electron)

Папка `app/` — десктопный клиент: мастер (папка Minecraft → тема → редактор), проводник по выбранному корню, вкладки, CodeMirror 6, сохранение **Ctrl+S** (только файлы внутри выбранной папки).

**Запуск в разработке** (нужны Node.js и npm):

```bash
cd app
npm install
npm run dev
```

Откроется **только окно Electron** (Vite на `127.0.0.1:5173` подгружает интерфейс внутри приложения). Страницу в браузере открывать не нужно — там нет `preload`, появится экран «Нужно приложение Electron».

Стили: **Tailwind через Vite/PostCSS** (`src/style.css`), без CDN. Настройки (`minecraftPath`, тема, флаг мастера) — `electron-store`.

**Сборка и запуск как приложение без dev-сервера:**

```bash
cd app
npm run build
npm start
```

Макет `code.html` — статичный референс; логика в `index.html` + `src/main.js`.