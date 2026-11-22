# EX1 LEGEND — Native design token baseline

Branch: `feature/native-android-migration`
Reference sources: `app.html`, `admin.html`, `manager.html`
Implementation: `android/app/src/main/java/com/ex1legend/app/Ex1DesignTokens.kt`

## Token extraction rules

The HTML sources contain multiple layers of CSS overrides, runtime theme variables, and page-specific effects. A single flat list of every CSS custom property would be misleading. This baseline separates stable default values from runtime/theme variants and does not claim pixel parity.

## Stable base colors

### Player
The player stylesheet includes several historical and theme-specific declarations. The latest shared base layer uses:
- Background: `#0B0B12`
- Surface: `#171725`
- Text: `#FFFFFF`
- Muted: `#A6A6B8`
- Violet: `#8B5CF6`
- Neon: `#B66CFF`
- Cyan: `#27E6F2`
- Pink: `#EC4FFF`
- Blue: `#536DFF`
- Gold: `#D9B45B`

The player also has user/site theme overrides (including accent and button colors). The tokens above are the stable fallback only; runtime theme values must be loaded and applied independently when that feature is implemented.

### Admin and manager
Their shared base variables include:
- Background: `#090910` in the initial root, later overridden by the shared EX1 visual layer to `#0B0B12`
- Panel: `#12121D` initially, later `#171725`
- Line: `#27273A`
- Text: `#F4F3FF`
- Muted: `#9998B2` initially, with later visual-layer values used in specific components
- Purple: `#8B5CF6`
- Cyan: `#22D3EE` initially, with later visual-layer accent `#27E6F2`
- Green: `#34D399`

The Native token object uses the final shared visual-layer background/panel values where present, while retaining a control-panel-specific text and line palette.

## Typography

- Player source font stack: `Tahoma, Arial, sans-serif`, with some system-ui and monospace contexts.
- Admin/manager source font stack: `system-ui, Tahoma, Arial, sans-serif`.
- Frequent player sizes include 9–16 CSS px for compact metadata/body controls, and 18–24 px for larger headings; special hero/display elements go larger.
- Admin/manager common sizes cluster around 11–13 CSS px, with larger headings around 18–27 px.

Android token fallback uses `FontFamily.SansSerif`. This is not a pixel-identical Tahoma/Arial replacement. A custom font asset should only be added after confirming licensing, source availability, script coverage (especially Persian), and rendering behavior on Android.

## Shape and spacing baseline

Repeated player radii include 10, 12, 13, 14, 16, 18 and 20 CSS px, plus pill/circle shapes. Admin/manager controls commonly use 12px radii. Compose baseline tokens provide 4/8/12/16/20/24 dp spacing and 10/12/14/20 dp corners as starting primitives. These are design tokens for consistent implementation, not a claim that every source component uses the same values.

## Motion and effects

- Player declares 23 keyframe names; examples include ambient drift, page/dialog entry, navigation selection, avatar pulse, room glow/scan and particle effects.
- Admin and manager declare 12 keyframe names each; these include grid drift, hero sweep, toast entry, ambient effects, tab entry, matrix drift and shimmer.
- The Native baseline does not yet reproduce these animations. Each animation must be mapped by target element, duration, easing, delay, iteration, reduced-motion behavior and trigger before parity is claimed.

## Assets

Static CSS/markup references found during this pass include:
- `/ex1-legend-mark.svg`
- Player room artwork: `/room-arena-art.svg`

Dynamic profile, room and uploaded imagery must be inventoried separately, including storage bucket, crop behavior, fallback, and loading/error states.

## Implementation in this commit

- Added `Ex1DesignTokens.kt` with separate player and control-panel palettes, spacing, corner and typography primitives.
- Updated the isolated Native preview Material theme and system bar base colors to consume the player palette.
- The launcher remains WebView; no production path or database changes were made.
- This is a foundational styling step, not a complete redesign or visual parity claim.

## Remaining design audit

- [ ] Resolve CSS cascade winners per route and per theme, not just declaration order.
- [ ] Map each keyframe to the exact component and timing.
- [ ] Extract component-level dimensions, typography weight/line height, borders, shadows and gradients.
- [ ] Inventory all image, icon and font assets, including dynamic Storage URLs.
- [ ] Compare screenshots at matching viewport sizes and device density.
- [ ] Validate RTL baseline, Persian text shaping, status/navigation bars, edge-to-edge insets and accessibility scaling on Android.
