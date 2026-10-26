# Sunrise Letting design system

Status: **adopted** (26 October 2026). Based on **Suggestion 2, Brand-led agency** in `docs/design/layout-suggestions.md`, chosen by the user.

This file holds the site-wide decisions. Every issue spec (`docs/design/<issue-number>-<title>.md`) reuses what is defined here and only adds what is specific to that issue. When an issue needs a new reusable component, it is added here, not in the issue spec.

Reference mockup: `docs/design/mockups/layout-2-brand-led-agency.html`. Where the mockup and this document differ, **this document wins**. The known differences are:
- The mockup shows a "Menu" link on small screens. The design system has no menu button (see 3.1).
- The mockup's filter summary reads "Filter properties (2 selected)". The design system uses "Filter properties" with no count (see 3.3).

---

## 0. Things still undecided

These parts of the design are built as placeholders until the user decides. Issue specs must not design past them.

| Topic | Placeholder until decided | Where tracked |
|---|---|---|
| **Property photos and image hosting** | Every image slot renders a neutral decorative placeholder block. No real images, no upload, no image service. | `layout-suggestions.md` open question 5; issue #5 open question |
| **Hero imagery and branding** | The hero background is a decorative CSS gradient. No logo file; the site name is text. | `layout-suggestions.md` open question 5 |
| **Home page service blocks** | Up to three text blocks. Only links to pages that exist are allowed (see 3.4). | Open question 1 in this file |
| **Property Manager area layout** | Not covered by this decision. See `9-layout-options.md` section 4 (Variant A or B). | Open question 2 in this file |
| **Signed-in header** | Signed-out links only are defined. | `9-layout-options.md` question 3 |
| **Footer links** | Only links to pages that exist. | `9-layout-options.md` question 5 |
| **Filter placement, search trigger, pagination style, map** | As defined below; open questions remain. | `layout-suggestions.md` open questions 3, 4, 6, 7 |

---

## 1. Principles

1. **Server-rendered first.** Every page is a full Thymeleaf page reached by a normal link or form. HTMX only improves in-page updates. Every core task works with JavaScript turned off.
2. **Brand-led but plain underneath.** The brand shows in the dark header, the hero band, the accent colour and the serif body text. Structure stays simple: one column on mobile, at most two on wide screens.
3. **WCAG 2.2 AA everywhere.** See section 6.
4. **Only what the brief needs.** No carousels, no featured strips, no map, no promotional cards in lists.

---

## 2. Tokens

Tokens are CSS custom properties on `:root` in the single site stylesheet. Templates use the tokens or component classes, never raw values.

### 2.1 Colour

| Token | Value | Use | Contrast |
|---|---|---|---|
| `--colour-text` | `#1d1d1b` | Body text, input borders | 16.8:1 on white |
| `--colour-text-muted` | `#505a5f` | Hints, secondary text, attribution | 7.0:1 on white |
| `--colour-bg` | `#ffffff` | Page background | |
| `--colour-panel` | `#f3f2f1` | Filter panel, service blocks, "Interested" panel | Text 15:1, link 7.4:1, primary 7.0:1 on it |
| `--colour-dark` | `#1d1d1b` | Header, footer, hero search panel background | White text 16.8:1 |
| `--colour-primary` | `#8a3a00` | Primary buttons, prices, accent borders | White text on it 7.8:1; on white 7.8:1 |
| `--colour-primary-shadow` | `#3d1a00` | Bottom edge of primary buttons | Decorative |
| `--colour-accent-on-dark` | `#ffb37a` | Second word of the site name in the header | 9.7:1 on `--colour-dark` |
| `--colour-link` | `#1a4f8b` | Links on light backgrounds | 8.3:1 on white |
| `--colour-link-on-dark` | `#ffffff` | Links in header and footer | 16.8:1 |
| `--colour-focus` | `#ffdd00` | Focus background and outline | Text on it is `--colour-text` |
| `--colour-error` | `#a10a28` | Error messages, error borders, error summary border | 8.1:1 on white |
| `--colour-success` | `#00703c` | Success banner border and heading background | 5.6:1 on white; white on it 5.6:1 |
| `--colour-border` | `#b1b4b6` | Decorative dividers and card outlines only | Not used where 3:1 is required |
| `--colour-placeholder` | `#dedede` / `#e9e9e9` stripes | Image placeholder blocks | Decorative |

Rules:
- Form control borders use `--colour-text` (3:1 non-text contrast is met).
- Colour is never the only signal. Errors also have text and a thicker border; the current page in pagination also has `aria-current` and bold text.

### 2.2 Typography

System fonts only. No web fonts, no CDN.

| Token | Value | Use |
|---|---|---|
| `--font-ui` | `Arial, Helvetica, sans-serif` | Headings, labels, buttons, navigation, prices, form inputs, tables, hints |
| `--font-body` | `Georgia, "Times New Roman", serif` | Running text: paragraphs, descriptions, list items in content |

Type scale (mobile / at 40em and wider):

| Token | Mobile | 40em+ | Line height | Use |
|---|---|---|---|---|
| `--text-h1` | 2rem | 2.5rem | 1.15 | One `<h1>` per page |
| `--text-h2` | 1.5rem | 1.75rem | 1.2 | Sections |
| `--text-h3` | 1.2rem | 1.25rem | 1.25 | Card headings, sub-sections |
| `--text-price` | 1.4rem | 1.5rem | 1.2 | Prices, bold, `--colour-primary` |
| `--text-body` | 1rem | 1.1875rem | 1.45 | Default |
| `--text-small` | 0.875rem | 1rem | 1.4 | Hints, footer, meta lines. Nothing smaller. |

Text must reflow at 320 CSS pixels wide and stay readable with text spacing overrides (WCAG 1.4.12). Line length for running text is capped at about 40em.

### 2.3 Spacing

| Token | Value |
|---|---|
| `--space-1` | 0.25rem |
| `--space-2` | 0.5rem |
| `--space-3` | 1rem |
| `--space-4` | 1.5rem |
| `--space-5` | 2rem |
| `--space-6` | 3rem |
| `--space-7` | 4rem |

Default vertical gap between form fields: `--space-3`. Between page sections: `--space-5`. Page gutter: `--space-3` on mobile, `--space-5` at 64em and wider.

### 2.4 Layout grid and breakpoints

Mobile first. Breakpoints in `em` so they respond to text zoom.

| Name | Min width | What changes |
|---|---|---|
| (base) | 0 | Single column everywhere |
| `md` | 40em | Hero search: input and button side by side. Property card: photo left, body right. Service blocks: three columns. Detail gallery: main area plus a thumbnail column. Body text size steps up. |
| `lg` | 64em | Results: filter side panel (18rem) beside the results column. Detail title row: title left, price and action panel right. Detail content: two columns (facts 1fr, description 2fr). |

- Content container: `max-width: 72rem`, centred, with the page gutter.
- Use CSS grid. Visual order must equal source order at every width; no `order` or grid placement that reorders content.
- At 400% zoom (equal to 320px wide) every page shows the base single-column layout.

---

## 3. Page structure

### 3.1 Shared skeleton (every Visitor page)

```html
<html lang="en-GB">
<head>
  <title>{Page title} - Sunrise Letting</title>          <!-- "Error: " prefix when the page shows a validation error -->
  <meta name="viewport" content="width=device-width, initial-scale=1">
  site stylesheet; HTMX script (deferred, from the WebJar); htmx-config meta (see 5.6)
</head>
<body>
  <a class="skip-link" href="#main-content">Skip to main content</a>
  <header class="site-header"> ... </header>
  <main id="main-content" tabindex="-1"> ... </main>
  <footer class="site-footer"> ... </footer>
</body>
```

**Header** (`.site-header`):
- `--colour-dark` background, white text, `--space-3` padding.
- Left: the site name "Sunrise Letting" as text in a link to `/` ("Letting" in `--colour-accent-on-dark`). No logo image until branding is decided.
- Right: `<nav aria-label="Main">` with a `<ul>` of links. Signed out: "Properties to let" (`/properties`), "Sign in", "Create account". The link for the current section has `aria-current="page"`.
- **No menu button.** On mobile the three links wrap onto a second line under the site name. This avoids a JavaScript or `<details>` menu. Revisit only if the number of links grows.
- No sticky header.

**Footer** (`.site-footer`):
- `--colour-dark` background, white links, `--text-small`.
- A `<ul>` of links to legal pages **that exist**. Until those pages exist, only the line "© Sunrise Letting" is shown.

### 3.2 Home (`/`)

Mobile:
```
+------------------------------+
| Sunrise Letting              |
| Properties to let  Sign in   |
| Create account               |
+------------------------------+
|##### hero band (gradient) ###|
| +--------------------------+ |
| | Find your next home  (h1)| |
| | Location                 | |
| | Town, area or postcode   | |
| | [                      ] | |
| | [ Search ]               | |
| | See all properties to let| |
| +--------------------------+ |
|##############################|
| [ service block 1 ]          |  placeholders, see 3.4
| [ service block 2 ]          |
| [ service block 3 ]          |
+------------------------------+
| © Sunrise Letting            |
+------------------------------+
```

Wide (64em):
```
+----------------------------------------------------------------------+
| Sunrise Letting              Properties to let  Sign in  Create account
+----------------------------------------------------------------------+
|##################### hero band (gradient) ###########################|
|##   +--------------------------------------------+                 ##|
|##   | Find your next home                        |                 ##|
|##   | Location                                   |                 ##|
|##   | [ Town, area or postcode      ] [ Search ] |                 ##|
|##   | See all properties to let                  |                 ##|
|##   +--------------------------------------------+                 ##|
|######################################################################|
|  +------------------+ +------------------+ +------------------+      |
|  | service block 1  | | service block 2  | | service block 3  |      |
|  +------------------+ +------------------+ +------------------+      |
+----------------------------------------------------------------------+
| © Sunrise Letting                                                    |
+----------------------------------------------------------------------+
```

Components: hero search panel (4.2), service block (4.3).

### 3.3 Search results (`/properties`)

Mobile:
```
+------------------------------+
| header                       |
+------------------------------+
| Properties to let in Leeds   |  h1
| +--------------------------+ |
| | > Filter properties      | |  filter panel (details)
| +--------------------------+ |
| 12 properties found          |  #results-count (role=status)
| Sort by [Newest v] [Sort]    |
| +--------------------------+ |
| | [ photo placeholder ]    | |  property card
| | £1,200 per month         | |
| | 2 bedroom flat, ... (h2) | |
| | - key fact               | |
| | [ Request a viewing ]    | |
| +--------------------------+ |
| ...                          |
| Previous  Next               |  pagination
+------------------------------+
```

Wide (64em):
```
+----------------------------------------------------------------------+
| header                                                               |
+----------------------------------------------------------------------+
| Properties to let in Leeds                                           |
| +-------------------+  12 properties found      Sort by [Newest v]   |
| | Filter properties |  +--------------------------------------------+|
| | Location [     ]  |  | +--------------+ £1,200 per month          ||
| | ...criteria...    |  | | placeholder  | 2 bedroom flat, ... (h2)  ||
| | [ Apply filters ] |  | |              | - key facts               ||
| | Clear filters     |  | +--------------+ [ Request a viewing ]     ||
| +-------------------+  +--------------------------------------------+|
|                        Previous  1  2  3  Next                       |
+----------------------------------------------------------------------+
```

Source order: h1, filter panel, results count, sort form, `#search-results` (cards, pagination).

Components: filter side panel (4.4), results count (4.5), property card (4.6), pagination (4.12). The search criteria themselves (which fields) are decided in issue #4's spec.

### 3.4 Property detail (`/properties/{id}`)

Mobile:
```
+------------------------------+
| header                       |
+------------------------------+
| [ gallery placeholder ]      |
| 2 bedroom flat, ... (h1)     |
| Summary line                 |
| +--------------------------+ |
| | £1,200 per month         | |  price and action panel
| | [ Request a viewing ]    | |
| | [ Apply for this property]| |
| | You'll need to sign in...| |
| +--------------------------+ |
| Key features (h2, ul)        |
| Further details (h2, dl)     |
| Description (h2)             |
| +--------------------------+ |
| | Interested in this       | |  repeated actions
| | property? [..] [..]      | |
| +--------------------------+ |
+------------------------------+
```

Wide (64em):
```
+----------------------------------------------------------------------+
| header                                                               |
+----------------------------------------------------------------------+
| +-------------------------------------+ +---------------+            |
| |   gallery main area (placeholder)   | | thumb slot    |            |
| |                                     | | thumb slot    |            |
| +-------------------------------------+ +---------------+            |
| 2 bedroom flat, Example Road (h1)     | £1,200 per month             |
| Summary line                          | [ Request a viewing ]        |
|                                       | [ Apply for this property ]  |
| ---------------------------------------------------------------------|
| Key features          | Description                                  |
| Further details (dl)  |                                              |
| ---------------------------------------------------------------------|
| Interested in this property?  [ Request a viewing ] [ Apply ... ]    |
+----------------------------------------------------------------------+
```

Components: detail gallery area (4.7), price and action panel (4.8), summary list (4.11). The fields shown (deposit, available date, features) are decided in issue #5's spec.

---

## 4. Components

Each component lists the stable hooks (ids, classes, attributes) that MockMvc tests can assert on. Class names are fixed by this document; issue specs may add ids.

### 4.1 Skip link
- First focusable element: `<a class="skip-link" href="#main-content">Skip to main content</a>`.
- Off-screen until focused, then shown at the top left with focus style.
- `<main id="main-content" tabindex="-1">` so focus lands on it.

### 4.2 Hero search panel (home only)
- `<section class="hero">` with the decorative gradient background (placeholder for hero imagery). No `<img>`. If a photo is added later it is CSS background or `alt=""`.
- Inside: `<div class="hero-panel">` with `--colour-dark` background and white text. **Text never sits directly on the gradient or a photo.**
- Contains the page `<h1>`, then `<form class="hero-search" role="search" action="/properties" method="get">` with one text field (4.10) and a primary button "Search", then a link "See all properties to let" to `/properties`.
- At `md` the input and button sit on one row; below `md` they stack.
- No HTMX. It is a plain GET to the results page.

### 4.3 Service block (home only, **placeholder**)
- `<section class="service-block" aria-labelledby="...">` with `--colour-panel` background and a 6px top border in `--colour-primary`. Contains an `<h2>`, one short paragraph and at most one link.
- Up to three blocks: one column on mobile, three columns at `md`.
- **Placeholder only.** The brief has no "how viewings work" or "what you'll need" pages, and this design does not create them. Rules until the user decides (open question 1):
  - A block may only link to a page that exists or is in the brief (for example the Property Manager sign-in from #3).
  - Blocks that would link to pages not in the brief are left out, or shown as text with no link.
  - No issue spec should design the pages these blocks might point to.

### 4.4 Filter side panel (results)
- `<div class="filter-panel">` with `--colour-panel` background.
- Contains a native `<details class="filter-panel__disclosure">` with `<summary>Filter properties</summary>`. The server adds the `open` attribute when **any** search criterion is set (including location), so most visits show it open; it is closed only for "See all properties to let" with no criteria. Works without JavaScript.
- Inside: `<form id="filter-form" role="search" aria-label="Filter properties" action="/properties" method="get">`. The fields come from issue #4's spec. Grouped choices (for example bedrooms) use a fieldset with a legend and radios.
- Buttons: primary "Apply filters", then a link "Clear filters" to `/properties`.
- Below `lg` the panel sits above the results; at `lg` it is an 18rem column to the left. It is **not sticky**.
- HTMX: see 5.2.

### 4.5 Results count
- `<p id="results-count" class="results-count" role="status">12 properties found</p>`. Singular: "1 property found".
- It sits **outside** `#search-results` and stays in the DOM; only its text changes (5.2), so screen readers announce the new count and nothing else.
- Empty-state wording is set by issue #4's spec (for example "No properties found").

### 4.6 Property card
```html
<article class="property-card">
  <div class="property-card__image" aria-hidden="true"></div>   <!-- placeholder -->
  <div class="property-card__body">
    <p class="property-card__price">£1,200 per month</p>
    <h2 class="property-card__title"><a href="/properties/123">2 bedroom flat, Example Road, Leeds LS1</a></h2>
    <ul class="property-card__facts"> ... </ul>
    <a class="button button--secondary" href="...">Request a viewing<span class="visually-hidden"> for 2 bedroom flat, Example Road, Leeds LS1</span></a>
  </div>
</article>
```
- One card per property, in a list container. Cards stack on mobile; at `md` the image slot is 18rem wide on the left.
- The **title link** is the only link to the detail page. Don't wrap the whole card in a link.
- The image slot is a decorative placeholder block (striped `--colour-placeholder`, 3:2 ratio, `aria-hidden="true"`) until photos are decided. When photos exist, the list image stays `alt=""` because the title names the property.
- Which facts appear is set by issue #4's spec.
- The "Request a viewing" button links to the viewing request page (#6). Its hidden text gives each button a unique accessible name. Whether the card button ships with #4 or waits for #6 is for those issue specs.
- Heading level: `<h2>` on the results page (the page has one `<h1>`).

### 4.7 Detail gallery area
- `<div class="gallery">` at the top of the detail page.
- **Placeholder until photos and image hosting are decided:** one main placeholder block (3:2) and, at `md`, a column of two smaller placeholder blocks. All `aria-hidden="true"`, no text, no link.
- When photos exist (future issue): the main image has meaningful alt text entered by Property Managers; a "See all N photos" link may go to a separate photos page. **No carousel, no lightbox, no JavaScript.** These are not designed yet.

### 4.8 Price and action panel (detail)
- `<div class="action-panel">`: the price (`.price`, e.g. "£1,200 per month"), then a primary link-button "Request a viewing" and a secondary link-button "Apply for this property", then a hint "You'll need to sign in or create an account first." shown to signed-out Visitors only.
- Below `lg` it follows the title and summary line; at `lg` it sits to the right of the title row.
- **Not sticky** (WCAG 2.4.11 Focus Not Obscured, and 400% zoom).
- The same two actions are repeated at the end of the page in `<section class="interested" aria-labelledby="interested-heading">` with the heading "Interested in this property?". Repeated buttons use **exactly the same text** and destinations (WCAG 3.2.4).

### 4.9 Buttons
| Variant | Class | Look | Use |
|---|---|---|---|
| Primary | `button` | `--colour-primary` background, white bold `--font-ui` text, 2px bottom shadow `--colour-primary-shadow` | The main action on a page or panel. At most one per panel. |
| Secondary | `button button--secondary` | White background, 2px inset border and text in `--colour-primary` | Other actions |

- Minimum height 2.75rem (44px), padding `--space-2` `--space-3`. Text in sentence case, starting with a verb ("Search", "Apply filters", "Request a viewing").
- An action that **navigates** (GET) is an `<a class="button">`. An action that **submits** is a `<button type="submit" class="button">`.
- Focus: see 6.2.
- Disabled buttons are not used; validate and explain instead.

### 4.10 Form field
```html
<div class="form-group" id="field-email">                          <!-- add "form-group--error" when invalid -->
  <label class="label" for="email">Email address</label>
  <p class="hint" id="email-hint">...</p>                            <!-- optional -->
  <p class="error-message" id="email-error"><span class="visually-hidden">Error:</span> Enter an email address</p>  <!-- only when invalid -->
  <input class="input" id="email" name="email" type="email" autocomplete="email"
         aria-describedby="email-hint email-error" aria-invalid="true">
</div>
```
- Order: label, hint, error message, control.
- Every control has a visible `<label>`. Placeholder text is never used as a label.
- Inputs: 2px `--colour-text` border, min height 2.5rem, `--font-ui`. Error state: 4px left border on the group in `--colour-error`, input border `--colour-error`, message in `--colour-error` bold.
- Radios and checkboxes: inside `<fieldset>` with a `<legend>`; each 24px or larger with its label clickable.
- Use the right `type` and `autocomplete` values (WCAG 1.3.5).
- The wrapper id `field-<name>` is the swap target for inline validation (5.4).

### 4.11 Summary list
- `<dl class="summary-list">` of `<dt>`/`<dd>` pairs for key facts (deposit, available from, bedrooms). Two columns at all widths (`max-content 1fr`); wraps naturally.

### 4.12 Pagination
- `<nav class="pagination" aria-label="Pagination">` with a `<ul>`. "Previous" and "Next" links; page numbers pending `layout-suggestions.md` open question 7.
- Current page: `aria-current="page"`, bold, `--colour-dark` background, white text. Previous on page 1 and Next on the last page are not shown as links.
- Each target is at least 44 by 44px.
- Links are **full page loads** (no HTMX), carrying the current criteria in the query string. The page `<title>` includes the page: "Properties to let in Leeds (page 2 of 3) - Sunrise Letting".

### 4.13 Error summary
```html
<div class="error-summary" tabindex="-1" autofocus aria-labelledby="error-summary-title">
  <h2 id="error-summary-title">There is a problem</h2>
  <ul><li><a href="#email">Enter an email address</a></li></ul>
</div>
```
- Shown at the top of `<main>` when a submitted form has errors. Each link goes to the field's input id and uses the same text as the field's error message.
- `autofocus` moves focus to it on page load and after an HTMX swap. The page title gets the prefix "Error: ".
- 4px border in `--colour-error`.

### 4.14 Notification banner (success)
- `<div class="notification-banner notification-banner--success" role="status" tabindex="-1" autofocus>` with a heading, used at the top of `<main>` after a successful action (for example after a viewing request). Exact copy comes from each issue spec.
- Border and heading background `--colour-success`.

### 4.15 Visually hidden text
- `.visually-hidden`: standard clip pattern, used for extra screen-reader text such as card button names and the "Error:" prefix. Never `display: none`.

### 4.16 Image placeholder
- `.image-placeholder`: striped `--colour-placeholder` block, fixed aspect ratio (3:2 by default), `aria-hidden="true"`, no text.
- Used in the property card, the detail gallery and anywhere a photo will go. Replaced when photos and hosting are decided.

---

## 5. HTMX conventions

### 5.1 General
- HTMX is loaded from the WebJar at `/webjars/htmx.org/dist/htmx.min.js` with `defer`.
- Every HTMX element sits on a working link or form with a real `href`/`action` and `method`. With JavaScript off, the same request does a full page load.
- The controller checks the `HX-Request` header. With it, it returns a **fragment**; without it, the **full page**. Responses that can be either set `Vary: HX-Request`.
- HTMX endpoints return Thymeleaf fragments, never JSON.
- `hx-boost` is not used. Navigation between pages is always a normal full page load.
- Swaps never move focus unless this document or the issue spec says so.

### 5.2 Search results (the only swapping region on the results page)

| | |
|---|---|
| Trigger | Submitting `#filter-form` (`hx-trigger="submit"` by default; live update on change is `layout-suggestions.md` open question 4) |
| Request | `GET /properties?<criteria>` with `hx-get="/properties"`, `hx-push-url="true"` |
| Target | `#search-results`, `hx-swap="innerHTML"` |
| Fragment | Cards (or the empty state) and pagination, plus `<p id="results-count" hx-swap-oob="innerHTML">N properties found</p>` so the count's text updates while the live region element stays in place |
| Also updated | The page `<title>` (HTMX updates it from a `<title>` in the fragment) |
| No-JS | Same form submits a full page GET |

- The sort form sits outside `#search-results`. It is a separate GET form that carries the current criteria as hidden fields. With HTMX it uses the same target and swap as the filter form.
- Focus stays on the control the user used (the Apply filters or Sort button), because it isn't inside the swapped region.

### 5.3 Loading, empty and error states for swapped regions
- **Loading:** an element `<p class="htmx-indicator" id="results-loading">Loading properties</p>` next to the count, referenced by `hx-indicator`. While the request runs, `#search-results` gets reduced opacity via the `htmx-request` class. No spinner animation; nothing is announced (the new count is announced on completion). With `prefers-reduced-motion`, no transitions.
- **Empty:** the fragment contains the empty state from the issue spec in place of the cards, and the count reads "0 properties found" or the issue spec's wording. Include a "Clear filters" link.
- **Server error:** the server returns the error fragment with a 5xx status. HTMX swaps it into the same target (see 5.6). The fragment contains a short message from the issue spec and a link to reload the page with the same URL. The count's text is set to the same message so it is announced.
- **Not found** (detail page, missing property): always a full page with status 404; never a fragment.

### 5.4 Inline validation (forms that opt in)
- Optional per issue spec. The server always re-validates the full form on submit and shows the error summary (4.13) if needed.
- Trigger: `hx-trigger="change"` on the input (not `keyup`, not `blur`), so errors appear only after the user has changed and left a value.
- Request: `hx-post` to the endpoint named in the issue spec, sending that field's value; CSRF token included.
- Target: the field wrapper `#field-<name>`, `hx-swap="outerHTML"`. The fragment is the whole form group (4.10), in its valid or error state.
- No focus change. The error message is linked by `aria-describedby`, so it is read when the user returns to the field.
- No-JS: nothing happens until submit; the submit path does all validation.

### 5.5 What never uses HTMX
- Page navigation, pagination, sign in and sign out, the detail page and any multi-step flow.

### 5.6 Error response handling
- The layout includes an `htmx-config` meta tag so that 4xx and 5xx responses are still swapped into their target (HTMX's default is to ignore them). Developers should confirm the exact config syntax against the htmx version in the WebJar.

---

## 6. Accessibility rules (site-wide)

### 6.1 Structure
- `lang="en-GB"`. Title format "{Page} - Sunrise Letting"; "Error: " prefix with validation errors; page number on paginated results.
- One `<h1>` per page; headings in order with no skipped levels.
- Landmarks: one `header`, `nav aria-label="Main"`, one `main`, one `footer`. Extra `nav`s have unique labels ("Pagination").
- Visual order equals source and tab order at all widths.

### 6.2 Focus
- Every focusable element shows focus: `--colour-focus` background with `--colour-text` text and a 4px `--colour-text` bottom bar for links and buttons; for inputs a 3px `--colour-focus` outline plus a 2px `--colour-text` inset border.
- Focus is never hidden by sticky or fixed content. Nothing is sticky on this site (WCAG 2.4.11).

### 6.3 Colour, size and reflow
- Contrast: text 4.5:1 or higher (all tokens in 2.1 exceed 5.5:1); UI component boundaries 3:1.
- Target size: 44 by 44px for buttons and pagination; 24 by 24px minimum for anything else (WCAG 2.5.8).
- Reflow at 320px and 400% zoom with no horizontal scrolling.

### 6.4 Images
- Placeholders are `aria-hidden="true"`.
- Future list-card images: `alt=""`. Future gallery images: meaningful alt text from Property Managers. Hero images: decorative.
- No carousels or auto-moving content.

### 6.5 Links and buttons
- Link text makes sense out of context, or has visually hidden text to make it unique.
- Repeated actions use identical text (WCAG 3.2.4).

### 6.6 Forms and errors
- Labelled controls, hints and errors linked with `aria-describedby`, `aria-invalid="true"` on invalid fields.
- Error summary at the top with links to fields, focused on load.
- Don't ask again for information the user has already given in the same process (WCAG 3.3.7).
- No time limits.

### 6.7 Live regions
- Only the results count (`#results-count`, `role="status"`) and the success banner are live regions. Never wrap a whole results list or form in a live region.
- Live region elements exist in the initial page and stay in the DOM; only their contents change.

---

## 7. Open questions

1. **Home page service blocks:** what should the three blocks say, and where should they link? The brief has no pages for "how viewings work" or "what you'll need". Options: (a) text only, no links; (b) keep only the Property Manager sign-in block; (c) raise issues for those content pages; (d) drop the blocks.
2. **Property Manager area:** Variant A (same shell with a banner) or Variant B (separate admin shell), from `9-layout-options.md` section 4? Is `/manage` acceptable as the URL prefix? The design system will be extended once this is decided.
3. **Photos and image hosting:** still undecided, so all image slots are placeholders.
4. **Branding:** is there a logo, brand colour or typeface? The colours here are a neutral palette chosen to meet AA. If brand colours arrive, they must be checked against the contrast column in 2.1.
5. Remaining from `layout-suggestions.md`: filter placement on wide screens (3; this document follows Suggestion 2's side panel until answered), search trigger (4; submit only until answered), map (6; none until answered), pagination numbers (7; Previous and Next only until answered).
6. Remaining from `9-layout-options.md`: signed-in header links (3), one or two sign-in pages (4), footer links (5).
