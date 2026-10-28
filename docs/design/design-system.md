# Sunrise Letting design system

Status: **adopted** (26 October 2026). Based on **Suggestion 2, Brand-led agency** in `docs/design/layout-suggestions.md`, chosen by the user. **Updated 28 October 2026** with the product owner's decisions in issues #1, #2 and #3: the Property Manager area (Variant A under `/manage`), one shared sign-in page, the signed-in headers, sign out, and the account pages (sections 0, 3.1, 3.5, 3.6, 4.3, 4.10, 4.17 to 4.19, 5.4, 6.6, 6.7, 7). **Updated again 28 October 2026** with the product owner's answers to open questions 7 to 10 (now in issues #1, #2 and #3): the show/hide script, whole-group inline validation, no blur check on empty fields, and the session time-out warning (sections 0, 3.1, 3.5, 4.14, 4.17, 4.20, 5.4, 5.5, 6.6, 6.7, 7). **Updated a third time 28 October 2026** with the answers to open questions 11 to 13 (now in issues #1 and #2): message-only inline validation for password fields, and the time-out edge cases (sections 0, 3.5, 4.10, 4.17, 4.20, 5.4, 5.7, 6.7, 7). Then open question 14 was answered in #2: a timed-out tab whose session is still active goes straight back to its page (sections 0, 3.5, 4.20, 7). Then open question 15 was answered in #1 and #2: signed-in users opening the sign-in or sign-up page are redirected (sections 0, 3.5, 7).

This file holds the site-wide decisions. Every issue spec (`docs/design/<issue-number>-<title>.md`) reuses what is defined here and only adds what is specific to that issue. When an issue needs a new reusable component, it is added here, not in the issue spec.

Reference mockup: `docs/design/mockups/layout-2-brand-led-agency.html`. Where the mockup and this document differ, **this document wins**. The known differences are:
- The mockup shows a "Menu" link on small screens. The design system has no menu button (see 3.1).
- The mockup's filter summary reads "Filter properties (2 selected)". The design system uses "Filter properties" with no count (see 3.3).
- The mockup's third home page block ("Property Managers", "Sign in to manage listings") implies a Property Manager sign-in. There is only **one shared sign-in page** (3.5), so any such link goes there. Whether the block is kept at all is open question 1.
- The mockup shows only the signed-out header. The signed-in headers and the Property Manager area are defined in 3.1 and 3.6.

---

## 0. Things still undecided

These parts of the design are built as placeholders until the user decides. Issue specs must not design past them.

| Topic | Placeholder until decided | Where tracked |
|---|---|---|
| **Property photos and image hosting** | Every image slot renders a neutral decorative placeholder block. No real images, no upload, no image service. | `layout-suggestions.md` open question 5; issue #5 open question |
| **Hero imagery and branding** | The hero background is a decorative CSS gradient. No logo file; the site name is text. | `layout-suggestions.md` open question 5 |
| **Home page service blocks** | Up to three text blocks. Only links to pages that exist are allowed (see 4.3). | Open question 1 in this file |
| **Footer links** | Only links to pages that exist. | `9-layout-options.md` question 5 |
| **Filter placement, search trigger, pagination style, map** | As defined below; open questions remain. | `layout-suggestions.md` open questions 3, 4, 6, 7 |

Decided since adoption (28 October 2026, product owner, issues #1 to #3), so no longer placeholders:
- **Property Manager area layout:** Variant A under `/manage` (3.6).
- **Signed-in header:** defined for Visitors and Property Managers (3.1).
- **Sign-in pages:** one shared sign-in page (3.5).
- **Password show/hide:** a small script of our own in `static/`; not shown without JavaScript (4.17).
- **Inline validation:** whole field group swapped on blur; empty fields not checked until submit (5.4).
- **Session time-outs:** warning dialog 2 minutes before an idle time-out, and a sign-in message after a time-out (3.5, 4.20).
- **Inline validation of password fields:** only the error message is swapped; the input is never re-rendered (5.4, 4.17).
- **Time-out edge cases:** each tab runs its own timer; no response or a failed keep-alive goes to the sign-in page with the inactivity message; if the session is still active, the sign-in page sends the tab straight back to its return address with no message (3.5, 4.20).
- **Already signed in:** opening "Sign in" or "Create account" directly while signed in redirects to `/properties` (Visitors) or `/manage` / `/manage/change-password` (Property Managers), with no form (3.5).

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
  site scripts of our own, deferred, from static/ (see 5.7): password toggle (pages with a password field),
  session time-out warning (signed-in sessions without "Remember me" only)
</head>
<body>
  <a class="skip-link" href="#main-content">Skip to main content</a>
  <header class="site-header"> ... </header>
  <main id="main-content" tabindex="-1"> ... </main>
  <footer class="site-footer"> ... </footer>
  <dialog class="timeout-dialog"> ... </dialog>             <!-- only when the time-out warning applies, see 4.20 -->
</body>
```

**Header** (`.site-header`):
- `--colour-dark` background, white text, `--space-3` padding.
- Left: the site name "Sunrise Letting" as text in a link to `/` ("Letting" in `--colour-accent-on-dark`). No logo image until branding is decided.
- Right: `<nav aria-label="Main">` with a `<ul>` of items. The items depend on who is signed in (decided in #2 and #3):

  | State | Items, in order |
  |---|---|
  | Signed out | "Properties to let" (`/properties`), "Sign in" (shared sign-in page, 3.5), "Create account" (sign-up page, 3.5) |
  | Signed in as a Visitor | "Properties to let" (`/properties`), "Sign out" (4.18) |
  | Signed in as a Property Manager | "Properties to let" (`/properties`), "Property Manager area" (`/manage`), "Sign out" (4.18) |

  - The header never shows the user's name. There is no account page.
  - When signed in, "Sign in" and "Create account" are not rendered at all (not hidden with CSS), so tests can assert their absence.
  - The link for the current section has `aria-current="page"`.
  - The header is the same on every page, including `/manage` pages; the Property Manager area adds only the band (4.19).
- **No menu button.** On mobile the links wrap onto a second line under the site name. This avoids a JavaScript or `<details>` menu. The largest set is three items, so this still holds. Revisit only if the number of items grows.
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

### 3.5 Account pages: sign up, sign in, sign out (decided in #1, #2, #3)

All account pages are **full pages** in the shared skeleton (3.1), using the single-column form pattern: `<h1>`, error summary (4.13) when needed, then form fields (4.10) and one primary submit button (4.9). Exact URLs (other than `/manage/...`), headings, hints and messages are set by the issue specs.

**Sign up ("Create account", #1)**
- Linked from "Create account" in the header.
- Fields, all required: Title (a `<select>` with Mr, Mrs, Miss, Ms, Mx, Dr, Other; choosing "Other" requires a free-text title), First name, Surname, Email, Phone number (UK), Password and Confirm password. Both password fields use the password field component (4.17). The way the "Other" text field is presented is set in the #1 spec.
- Inline validation when a field **that has a value** loses focus (5.4). For every field except Password and Confirm password, the whole field group is swapped. For the two password fields, only the error message is swapped and the input is never re-rendered, so what was typed is kept and no response contains a password (decided in #1). A field left empty is not checked until submit. The inline email check covers **format only** and never reveals whether an account exists. The server checks everything again on submit.
- After a failed submit the entered values are kept, **except the passwords**, which are always rendered empty.
- On success the Visitor is signed in and goes to the return address (see "Where users go after signing in" below). If there is none, they go to `/properties`, which shows a success notification banner (4.14) reading "Your account has been created".

**Sign in (#2, shared with Property Managers in #3)**
- **One shared sign-in page** for Visitors and Property Managers, linked from "Sign in" in the header. There is **no separate Property Manager sign-in page**.
- Fields: Email, Password (password field component, 4.17) and a "Remember me" checkbox (single checkbox, 4.10), unticked by default.
- No inline validation is decided for this page; it validates on submit only.
- Wrong details: one generic error that doesn't say which part was wrong. The email entered is kept and the password is cleared. Property Managers see the same error as Visitors.

**Where users go after signing in or signing up**

| Who | Destination |
|---|---|
| Visitor | The page they came from (e.g. a property's "Request a viewing"), but only if the return address is a path on this site. Otherwise, or if there is none, `/properties`. |
| Property Manager with a temporary password | `/manage/change-password`, whatever the return address |
| Property Manager with a permanent password | `/manage`, whatever the return address |

How the return address is carried to the sign-in and sign-up pages is set in the #1 and #2 specs.

**Already signed in (decided in #1 and #2)**
- If someone who is already signed in opens the "Sign in" or "Create account" page directly (for example from a bookmark, without the timed-out marker), the form isn't shown. The server redirects instead:

  | Who | Destination |
  |---|---|
  | Visitor | `/properties` (any return address is ignored) |
  | Property Manager with a temporary password | `/manage/change-password` |
  | Property Manager with a permanent password | `/manage` |

- A tab that arrives at the sign-in page with the timed-out marker while the session is still active is handled differently: it goes back to its return address (see the warning section below and 4.20).
- Test hooks: a GET to either page with a signed-in session returns a redirect, not the form.

**Sign out (#2)**
- A form POST button in the header (4.18), protected against cross-site request forgery. Never a link.
- Ends the session and any remember-me login, then the user lands on `/properties`, and the header shows the signed-out items.

**Sessions** (decided in #1 to #3)
- Visitors: signed out after 30 minutes of inactivity; with "Remember me" ticked, they stay signed in for 30 days, even after closing the browser.
- Property Managers: signed out after 2 hours of inactivity; "Remember me" is ignored for them.

**Warning before an idle session runs out (#2, #3; WCAG 2.2.1)**
- 2 minutes before an idle session ends (after 28 minutes for Visitors, 1 hour 58 minutes for Property Managers), the time-out warning dialog (4.20) opens on whatever page the user is on. It offers "Stay signed in" and "Sign out".
- "Stay signed in", or pressing Escape, resets the idle timer through a keep-alive endpoint, with no page reload and nothing typed lost. "Sign out" works exactly like the header "Sign out" (4.18).
- If nobody responds before the time runs out, the page goes to the sign-in page with "You were signed out because you were inactive", so nothing personal is left on screen (decided in #2).
- If "Stay signed in" fails because the session has already ended (for example after the computer was asleep), the user is also taken to the sign-in page with that message (decided in #2).
- Each browser tab runs its own timer (decided in #2). A tab that isn't in use may show the warning while the session is active in another tab; "Stay signed in" covers that case. Sharing the timer between tabs is out of scope.
- If a tab's timer runs out while the session is still active (because the user was busy in another tab), the sign-in page notices the active session when the tab arrives. It sends the tab straight back to its return address, with **no** inactivity message and no sign-in form (decided in #2). The return address follows the usual rules in the table above: same-site paths only, otherwise `/properties`; Property Managers go to `/manage` (or `/manage/change-password`).
- Sessions kept by "Remember me" don't run out after inactivity, so they never show the warning. Signed-out users never see it.
- Without JavaScript there is no warning; the "after a session has run out" behaviour below still applies.

**After a session has run out (#2, #3)**
- If the user then submits a form or opens a page that needs them to be signed in, they are sent to the shared sign-in page, which shows an information notification banner (4.14) reading "You were signed out because you were inactive".
- After signing in, a Visitor goes back to the page they were on, with an **empty form**: anything typed before the time-out is not kept (keeping it is out of scope in #2). A Property Manager goes to `/manage` (or `/manage/change-password`), as in the table above.
- If the request that found the session expired was an HTMX request, the server responds with an `HX-Redirect` header to the sign-in page instead of a fragment, so the whole page changes rather than a sign-in page being swapped into a region (5.1).
- Users who were never signed in (for example a signed-out user opening `/manage`) go to the sign-in page **without** this message.

### 3.6 Property Manager area (`/manage`, decided in #3: Variant A)

- **Every Property Manager page is under `/manage`.** Home: `/manage`. First-sign-in password change: `/manage/change-password`.
- **Layout: Variant A** from `9-layout-options.md` section 4. The same skeleton as every other page (3.1): same header (with the Property Manager items), same `<main>`, same footer. The only addition is the "Property Manager area" band (4.19) at the bottom of the header. There is no separate admin shell and no side navigation (Variant B is not chosen).
- Property Managers can browse the public pages as normal. On public pages they see the Property Manager header items but **no** band; the band appears only on `/manage` pages.
- Access: signed-out users opening any `/manage` page are redirected to the shared sign-in page. Visitors are denied access. A Property Manager with a temporary password is redirected from every other `/manage` page to `/manage/change-password`. The content of the access-denied page is set in the #2 and #3 specs.

Mobile:
```
+------------------------------+
| Sunrise Letting              |
| Properties to let            |
| Property Manager area        |
| [Sign out]                   |
|##############################|
| Property Manager area        |  band (4.19)
|##############################|
| Property Manager area  (h1)  |  #3 placeholder home
|                              |
+------------------------------+
| © Sunrise Letting            |
+------------------------------+
```

Wide (64em):
```
+----------------------------------------------------------------------+
| Sunrise Letting      Properties to let  Property Manager area  [Sign out]
+----------------------------------------------------------------------+
|######################################################################|
| Property Manager area                                    (band, 4.19)|
|######################################################################|
|  Property Manager area   (h1, #3 placeholder home; #8 adds the list)  |
|                                                                      |
+----------------------------------------------------------------------+
| © Sunrise Letting                                                    |
+----------------------------------------------------------------------+
```

**Change your password (`/manage/change-password`, #3)**
- Full page with the band. Heading "Change your password".
- Fields: New password and Confirm new password, both using the password field component (4.17). Same rules as sign-up passwords (#1); the new password must also differ from the temporary one.
- No inline validation is decided for this page; it validates on submit. Errors use the error summary (4.13) and field errors (4.10). On success the Property Manager goes to `/manage`.

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
  - A block may only link to a page that exists or is in the brief (for example the shared sign-in page from #2, which Property Managers also use; there is no separate Property Manager sign-in page).
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
- **Single checkbox** (e.g. "Remember me" on the sign-in page): a lone yes/no checkbox needs no fieldset. Markup: `<div class="form-group checkbox"><input type="checkbox" id="remember-me" name="remember-me" class="checkbox__input"><label class="checkbox__label" for="remember-me">Remember me</label></div>`. The checkbox is 24px or larger and the label is clickable. Unticked by default unless the issue spec says otherwise.
- Use the right `type` and `autocomplete` values (WCAG 1.3.5).
- The wrapper id `field-<name>` is the swap target for inline validation: the whole group is replaced (5.4). **Exception:** password fields (4.17) swap only their error slot, never the group.

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

### 4.14 Notification banner (success and information)
- `<div class="notification-banner notification-banner--success" role="status" tabindex="-1" autofocus>` with a heading, used at the top of `<main>` after a successful action (for example after a viewing request, or "Your account has been created" on `/properties` after sign-up). Exact copy comes from each issue spec.
- Border and heading background `--colour-success`.
- **Information variant** (added for #2): `notification-banner--info`, same markup, role and focus behaviour, with border and heading background `--colour-link` (white text on it 8.3:1). Used for neutral messages that are not errors and not successes, for example "You were signed out because you were inactive" on the sign-in page (3.5). It sits above any error summary; if both are present only the error summary has `autofocus`.

### 4.15 Visually hidden text
- `.visually-hidden`: standard clip pattern, used for extra screen-reader text such as card button names and the "Error:" prefix. Never `display: none`.

### 4.16 Image placeholder
- `.image-placeholder`: striped `--colour-placeholder` block, fixed aspect ratio (3:2 by default), `aria-hidden="true"`, no text.
- Used in the property card, the detail gallery and anywhere a photo will go. Replaced when photos and hosting are decided.

### 4.17 Password field with show/hide toggle
Used for every password input: sign-up Password and Confirm password (#1), sign-in Password (#2), and New password and Confirm new password (#3).
```html
<div class="form-group password-field" id="field-password">       <!-- "form-group--error" when invalid -->
  <label class="label" for="password">Password</label>
  <p class="hint" id="password-hint">...</p>                         <!-- optional -->
  <div class="password-field__error-slot" id="password-error-slot"> <!-- always present -->
    <p class="error-message" id="password-error">...</p>            <!-- only when invalid -->
  </div>
  <div class="password-field__wrapper">
    <input class="input password-field__input" id="password" name="password" type="password"
           autocomplete="new-password" spellcheck="false" autocapitalize="none"
           aria-describedby="password-hint password-error">
    <button type="button" class="button button--secondary password-field__toggle"
            aria-controls="password" hidden>Show<span class="visually-hidden"> password</span></button>
  </div>
  <p class="visually-hidden password-field__status" aria-live="polite"></p>
</div>
```
- Based on the form field (4.10); the order label, hint, error, control is unchanged. The error message sits inside an **error slot** that is always present (empty when there is no error), so inline validation can swap the message without touching the input.
- On a full page render (first load or after a failed submit) the server sets the error state as for any field: `form-group--error` on the group, the message in the slot and `aria-invalid="true"` on the input.
- `autocomplete`: `new-password` on sign-up and change-password fields, `current-password` on the sign-in field.
- The server **never** renders a password back into the `value` attribute, including after a failed submit.
- Pressing "Show" changes the input to `type="text"` and the button text to "Hide"; pressing "Hide" masks it again. The visually hidden part of the button text names the field (e.g. " password", " confirm password") so each toggle has a unique accessible name.
- The status element announces "Your password is visible" or "Your password is hidden" after each press. It is in the page from the start and only its text changes (6.7).
- When the form is submitted, the input is set back to `type="password"` first.
- **Script (decided in #1):** a small script of our own in the `web` service's `static/` folder (for example `/js/password-toggle.js`), loaded with `defer` on pages that have a password field (5.7). Not inline handlers (no `hx-on`, no `onclick`) and not a library. #2 and #3 reuse the same script. The same script also keeps the error styling in step during inline validation (below).
- **Without JavaScript** the toggle doesn't appear: the button is rendered with `hidden` and only revealed by the script, and the field works as a normal masked password field.
- Password field groups are never swapped by HTMX (5.4), so the toggle state and the typed value survive inline validation.
- Focus stays on the toggle button after each press. The button meets the 44px target size (4.9).

**Inline validation of password fields (decided in #1; the mechanism is the design system's choice, as #1 asks):**
- Only the error message is swapped. The input has `hx-post` (the endpoint is set in the #1 spec), `hx-trigger="blur"`, `hx-target="#<name>-error-slot"` and `hx-swap="innerHTML"`. Confirm password also sends Password (`hx-include`).
- The fragment is the error message `<p class="error-message" id="<name>-error">` or nothing (also nothing for an empty value, 5.4). **It never contains a password**, and the input is never re-rendered, so what was typed stays in the field.
- **Error styling and `aria-invalid`** are updated by the same password field script (5.7), not by inline script. After HTMX swaps a `.password-field__error-slot` (`htmx:afterSwap`), the script checks whether the slot now contains an `.error-message`:
  - if it does, it adds `form-group--error` to the `.password-field` group and sets `aria-invalid="true"` on the input;
  - if it doesn't, it removes the class and the `aria-invalid` attribute.
  The input's `aria-describedby` always lists `<name>-error`, so the message is read when the user returns to the field.
- Without JavaScript there is no inline validation at all (HTMX needs JavaScript), so there is no state for the script to keep in step; the submit path sets everything.
- These steps apply only where the issue spec turns inline validation on (sign-up, #1). The sign-in and change-password pages render the same markup without the `hx-` attributes.

- Test hooks: `.password-field__toggle` with `aria-controls` equal to the input id and the `hidden` attribute in the server-rendered HTML; input `type="password"` and empty `value` in every server response; a `<script>` reference to the password field script (not inline script) on pages with a password field; `#<name>-error-slot` always present; the inline endpoint for a password field returns only the error message (or an empty body) and its response body never contains the submitted password.

### 4.18 Sign-out button
- In the header nav, as the last `<li>`: `<form class="sign-out" method="post" action="...">` containing the CSRF token hidden field and `<button type="submit" class="sign-out__button">Sign out</button>`. The action URL is set in the #2 spec.
- **Always a POST form, never a link** (decided in #2), so it is protected against cross-site requests and can't be triggered by a prefetch.
- Styled to look like the other header links (`--colour-link-on-dark`, `--font-ui`, underlined) so the nav reads as one list, with the same focus style (6.2) and at least a 24 by 24px target.
- No HTMX. A full page POST followed by a redirect to `/properties`.

### 4.19 Property Manager area band
- Shown on every `/manage` page and nowhere else (3.6).
- Markup: `<div class="area-band"><p class="area-band__text">Property Manager area</p></div>`, placed **inside** `<header class="site-header">`, after the site name and nav, so it sits within the banner landmark and doesn't add content outside landmarks.
- Full width, `--colour-primary` background, white bold `--font-ui` text at `--text-body` size (7.8:1), `--space-2` vertical padding, text aligned with the content container.
- It is a label, not a heading and not a link. Each `/manage` page still has its own `<h1>`.
- Test hook: `.area-band` present with the text "Property Manager area" on `/manage` pages; absent on public pages.

### 4.20 Time-out warning dialog (#2, #3)
Warns a signed-in user 2 minutes before an idle session ends (3.5). Meets WCAG 2.2.1 Timing Adjustable.

```html
<dialog class="timeout-dialog" id="timeout-dialog" role="alertdialog"
        aria-labelledby="timeout-dialog-title" aria-describedby="timeout-dialog-text"
        data-timeout-seconds="1800" data-warning-seconds="120" data-keep-alive-url="..."
        data-timed-out-url="...">
  <h2 id="timeout-dialog-title">...</h2>
  <p id="timeout-dialog-text">...</p>
  <div class="timeout-dialog__actions">
    <button type="button" class="button timeout-dialog__stay" autofocus>Stay signed in</button>
    <form class="sign-out" method="post" action="...">            <!-- same as 4.18 -->
      CSRF hidden field
      <button type="submit" class="button button--secondary">Sign out</button>
    </form>
  </div>
</dialog>
```

- **When it is rendered:** the server includes the dialog, and the time-out script, only for signed-in sessions that can run out through inactivity (any Property Manager session; a Visitor session without "Remember me"). It is not rendered for signed-out users or "Remember me" sessions. The element is closed (no `open` attribute) in the server HTML.
- **Time-out from the server (decided in #2):** `data-timeout-seconds` carries the idle time-out for this session (1800 for Visitors, 7200 for Property Managers) and `data-warning-seconds` the warning lead time (120). The script never hard-codes them. The keep-alive and sign-out URLs are set in the #2 spec.
- **Script (decided in #2):** a small script of our own in `static/` (for example `/js/session-timeout.js`), loaded with `defer` (5.7). It starts its timer on page load and restarts it after every completed HTMX request, because those also reset the server's idle timer. When the warning time is reached it opens the dialog with `showModal()`.
- **"Stay signed in"** (primary button): the script sends a POST with the CSRF token to the keep-alive endpoint, which resets the idle timer and returns no content (`204`). The dialog closes, the timer restarts, the page is not reloaded and nothing typed is lost. Focus returns to the element that had it before the dialog opened.
- **"Stay signed in" fails** (decided in #2): if the session has already ended (for example after the computer was asleep), the keep-alive endpoint returns `401` and no redirect, so the script can tell. The script then goes to the timed-out address (below). Any other failure (for example the network is down) is treated the same way, so the user is never left believing they are still signed in.
- **"Sign out"** (secondary button): the same CSRF-protected POST form as the header "Sign out" (4.18), so it works the same way and lands on `/properties`.
- **Escape key** (confirmed in #2): closing the dialog with Escape counts as "Stay signed in" (the keep-alive is sent, with the same failure handling), so a user who dismisses the dialog isn't signed out unexpectedly.
- **No response before the time runs out** (decided in #2): when the full idle time has passed with the dialog still open, the script goes to the timed-out address, so nothing personal is left on screen.
- **Timed-out address:** `data-timed-out-url` is the shared sign-in page with a marker that makes it show the information banner "You were signed out because you were inactive" (4.14, 3.5), plus the current page's path as the return address (same-site paths only, as in 3.5), so a Visitor returns to that page after signing in (with an empty form) and a Property Manager goes to `/manage`. The script navigates with `location.replace`, so Back doesn't return to the old page. The exact URL and marker are set in the #2 spec. This is a plain GET and must not change any state.
- **What the sign-in page does with the timed-out marker** (decided in #2): if the session has ended, it shows the sign-in form with the inactivity banner. If the session is **still active** (activity in another tab), it does not show the page or the message: it redirects straight to the return address (same-site paths only, otherwise `/properties`), or to `/manage` (or `/manage/change-password`) for a Property Manager.
- **Several tabs** (decided in #2): each tab runs its own timer; sharing the timer between tabs is out of scope. A tab that isn't in use may show the warning while the session is active in another tab, and "Stay signed in" covers that case. If nobody answers in that tab, it goes to the timed-out address and is sent straight back as above.
- **Accessibility:** a native modal `<dialog>` keeps focus inside and makes the rest of the page inert. `role="alertdialog"` with `aria-labelledby` and `aria-describedby` means the heading and text are announced when it opens. Focus goes to "Stay signed in". No countdown that updates every second (it would be announced repeatedly); if the text shows a time, it is fixed when the dialog opens. Buttons meet the 44px target size. Contrast and focus as 6.2 and 6.3. Dark backdrop (`::backdrop`) is decorative.
- **Layout:** centred, `max-width: 30rem`, white background, 4px `--colour-dark` border, `--space-4` padding. Buttons stack on mobile and sit side by side at `md`. At 400% zoom the dialog scrolls inside itself rather than being cut off.
- **Without JavaScript:** there is no warning. The behaviour after a session has run out (3.5) still applies.
- **Copy:** the heading and text are set in the #2 spec. They must say that the user will be signed out soon (#2) and must make sense for both Visitors and Property Managers. The button text is fixed: "Stay signed in" and "Sign out".
- Test hooks: `#timeout-dialog` present with the correct `data-timeout-seconds` for Visitors (1800) and Property Managers (7200); absent when signed out and for "Remember me" sessions; buttons "Stay signed in" and "Sign out"; the sign-out form posts with a CSRF token; `data-timed-out-url` present. The keep-alive endpoint returns `204` for a signed-in session and `401` (not a redirect) when the session has ended. With the timed-out marker and no active session, the sign-in page shows the inactivity banner. With the marker and an active session, it returns a redirect to the return address (Visitor; `/properties` if the address isn't a same-site path) or to `/manage` (Property Manager), and no banner.

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
- Optional per issue spec. The server always re-validates the full form on submit and shows the error summary (4.13) if needed. Sign-up (#1) uses it; sign-in (#2) and change password (#3) don't.
- Trigger: **when a field that has a value loses focus** (decided in #1). The input has `hx-trigger="blur"`, not `keyup`.
- **Empty fields are not checked on blur** (decided in #1). If the value sent is empty (after trimming), the endpoint returns the field group in its neutral state, with no error and no `aria-invalid`. Empty required fields are reported only on submit. This is done on the server, not with an HTMX trigger filter, because trigger filters are evaluated as inline JavaScript (the site avoids inline script, 5.7). A side effect: clearing a field that showed an error removes the error until submit.
- Request: `hx-post` to the endpoint named in the issue spec, sending that field's value; CSRF token included. Fields whose rule depends on another field (Confirm password, and the "Other" title text) also send the field they depend on (`hx-include`).
- Target, every field **except** Password and Confirm password: **the whole field group** (decided in #1): `hx-target="#field-<name>"`, `hx-swap="outerHTML"`. The fragment is the complete form group (4.10): label, hint, error message (if any) and input, in its neutral, valid or error state. Error styling (`form-group--error`), the error message and `aria-invalid="true"` are therefore always in step. The fragment re-renders the input with the value that was sent, so nothing typed is lost.
- Target, **Password and Confirm password** (decided in #1): **only the error message**, swapped into the field's always-present error slot (`hx-target="#<name>-error-slot"`, `hx-swap="innerHTML"`). The input is never re-rendered, so what was typed is kept, and the response never contains a password. The error styling and `aria-invalid` on the input are kept in step by the password field script (4.17, 5.7).
- The input's `aria-describedby` includes `<name>-error` whenever the message is present, so the message is read when the user returns to the field.
- **Email:** the inline check covers format only and never reveals whether an account exists (#1).
- **Focus:** the swap happens after focus has moved on, so focus is normally unaffected. If the user has gone back into the field before the response arrives, focus must stay in that input (HTMX keeps focus on a replaced element that has the same `id`; developers should confirm this for the htmx version in the WebJar). No live region (6.7).
- No-JS: nothing happens until submit; the submit path does all validation.
- Test hooks: for non-password fields the inline endpoint returns only the `#field-<name>` group (no layout) for an `HX-Request`; for an invalid value it has `form-group--error`, the message and `aria-invalid="true"`; for an empty value it has none of them. For password fields it returns only the `.error-message` element, or an empty body for a valid or empty value, and never the submitted password (see 4.17).

### 5.5 What never uses HTMX
- Page navigation, pagination, submitting sign up, sign in, change password and sign out, the detail page and any multi-step flow. (Sign-up uses HTMX only for inline validation, 5.4.)

### 5.6 Error response handling
- The layout includes an `htmx-config` meta tag so that 4xx and 5xx responses are still swapped into their target (HTMX's default is to ignore them). Developers should confirm the exact config syntax against the htmx version in the WebJar.
- If an HTMX request needs a signed-in user and the session has run out, the server responds with `HX-Redirect` to the sign-in page (3.5) rather than a fragment.

### 5.7 Site scripts of our own (decided in #1 and #2)
- Besides HTMX, the site has small scripts of its own in the `web` service's `static/` folder, for behaviour HTMX can't provide: the password field script (show/hide toggle, and keeping error styling and `aria-invalid` in step after an inline validation swap, 4.17) and the time-out warning (4.20). For example `/js/password-toggle.js` and `/js/session-timeout.js`.
- Plain JavaScript, no library, no build step, loaded with `defer` only on the pages that need them.
- **No inline script:** no `hx-on` attributes, no `onclick` or other inline handlers, no HTMX trigger filters, no `<script>` blocks with code. Values the scripts need come from `data-` attributes rendered by the server.
- Every page must still work without them (principle 1): without JavaScript the toggle doesn't appear and the warning isn't shown.

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
- No time limits on forms, except the session time-outs decided in #1 to #3 (30 minutes idle for Visitors, 2 hours idle for Property Managers). For WCAG 2.2.1 these are announced 2 minutes in advance by the time-out warning dialog (4.20), which lets the user extend the session ("Stay signed in"). After a time-out, the sign-in page explains why the user was signed out (3.5).
- Password fields allow pasting and password managers (WCAG 3.3.8): never block paste, and use the `autocomplete` values in 4.17.

### 6.7 Live regions
- Only the results count (`#results-count`, `role="status"`), the notification banners (4.14) and the password show/hide status (4.17) are live regions. Never wrap a whole results list or form in a live region.
- Live region elements exist in the initial page and stay in the DOM; only their contents change. Password field groups are never swapped (5.4), so the password status element always stays in place.
- The inline error message swapped into a password field's error slot is not announced either; the script sets `aria-invalid` and the message is read through `aria-describedby` when the user returns to the field.
- The time-out warning (4.20) is a modal `alertdialog`, not a live region: it is announced because focus moves into it when it opens.
- Inline validation fragments (5.4) are not announced; the message is read when the user returns to the field.

---

## 7. Open questions

1. **Home page service blocks:** what should the three blocks say, and where should they link? The brief has no pages for "how viewings work" or "what you'll need". Options: (a) text only, no links; (b) keep only a block for Property Managers, linking to the shared sign-in page (there is no separate Property Manager sign-in page, per #3); (c) raise issues for those content pages; (d) drop the blocks.
2. ~~**Property Manager area:** Variant A (same shell with a banner) or Variant B (separate admin shell), from `9-layout-options.md` section 4? Is `/manage` acceptable as the URL prefix?~~ **Resolved (28 October 2026, issue #3):** Variant A; `/manage` accepted. See 3.6 and 4.19.
3. **Photos and image hosting:** still undecided, so all image slots are placeholders.
4. **Branding:** is there a logo, brand colour or typeface? The colours here are a neutral palette chosen to meet AA. If brand colours arrive, they must be checked against the contrast column in 2.1.
5. Remaining from `layout-suggestions.md`: filter placement on wide screens (3; this document follows Suggestion 2's side panel until answered), search trigger (4; submit only until answered), map (6; none until answered), pagination numbers (7; Previous and Next only until answered).
6. Remaining from `9-layout-options.md`: footer links (5). Signed-in header links (3) and one or two sign-in pages (4) were **resolved on 28 October 2026** in issues #2 and #3 (see 3.1 and 3.5).
7. ~~**Password show/hide without a site script:** a small script in `static/`, or inline `hx-on` handlers?~~ **Resolved (28 October 2026, issues #1, #2, #3):** a small script of our own in the `web` service's `static/` folder, no inline handlers and no library; without JavaScript the toggle doesn't appear. See 4.17 and 5.7.
8. ~~**Error styling during inline validation:** message on blur, but border and `aria-invalid` only on submit?~~ **Resolved (28 October 2026, issue #1):** the blur check swaps the **whole field group** (label, input and error message), so error styling and `aria-invalid` stay in step with the message. This replaces the earlier "message only" error slot for every field except the two password fields (see question 11). See 5.4.
9. ~~**Blur on untouched fields:** show an error straight away, or only check fields that have a value?~~ **Resolved (28 October 2026, issue #1):** a field left empty is not checked on blur; empty required fields are reported on submit. See 5.4.
10. ~~**Session time-outs and WCAG 2.2.1:** warn, rely on no data loss, or accept as is?~~ **Resolved (28 October 2026, issues #2 and #3):** an accessible dialog warns 2 minutes before an idle session ends, with "Stay signed in" (keep-alive, no reload, nothing typed lost) and "Sign out" (same as the header). It is driven by a script of our own that takes the time-out from the server. "Remember me" sessions don't show it. After a time-out the user is sent to the sign-in page with "You were signed out because you were inactive" and, after signing in, returns to the page with an empty form (Property Managers go to `/manage`). See 3.5, 4.14 and 4.20.
11. ~~**Inline validation of password fields:** echo the value back, skip inline checks, or swap only the message?~~ **Resolved (28 October 2026, issue #1):** option (c). For Password and Confirm password only the error message is swapped; the input is never re-rendered, so what was typed is kept, and the server never sends a password back. Every other field still swaps the whole group. #1 left the mechanism for error styling and `aria-invalid` to the design system: the password field script updates both after the swap (4.17, 5.4, 5.7).
12. ~~**Time-out warning with several tabs open:** acceptable, or share the timer between tabs?~~ **Resolved (28 October 2026, issue #2):** each tab runs its own timer, which is acceptable for now; "Stay signed in" covers a warning in a tab that isn't in use. Sharing the timer between tabs is out of scope. See 3.5 and 4.20.
13. ~~**When time runs out with the warning still open, or "Stay signed in" fails:** stay on the page, or go to sign in?~~ **Resolved (28 October 2026, issue #2):** in both cases the page goes to the sign-in page with "You were signed out because you were inactive", so nothing personal is left on screen. Pressing Escape counts as "Stay signed in" (confirmed in #2). See 3.5 and 4.20.
14. ~~**Idle tab times out while the session is still active in another tab.**~~ **Resolved (28 October 2026, issue #2):** option (b). The sign-in page notices the active session and sends the tab straight back to its return address, with no inactivity message; Property Managers go to `/manage`, as in #3. See 3.5 and 4.20. Original question, for the record: because each tab runs its own timer (question 12), a tab nobody is looking at will reach its time-out and go to the sign-in page with "You were signed out because you were inactive", even though the user is still signed in and working in another tab. That redirect is a plain GET and doesn't end the session. What should the sign-in page do when it is opened this way but the session is still active? Options: (a) show the sign-in page and the message anyway (misleading: the header would show "Sign out", and the message would be untrue); (b) recognise the active session and skip the message, for example by sending the tab to its return address or showing the page without the banner; (c) end the session from that tab, which would also sign the user out of the tab they are using (and would need a state-changing request, unlike the plain GET in 4.20).
15. ~~**Signed-in user opens the sign-in or sign-up page directly.**~~ **Resolved (28 October 2026, issues #1 and #2):** the form isn't shown. Visitors are sent to `/properties`; Property Managers to `/manage`, or `/manage/change-password` if they still have a temporary password. See 3.5. Original question, for the record: (without the timed-out marker, for example from a bookmark or the browser history): #2 now sends a signed-in tab straight back only when it arrives through a time-out. Should the plain sign-in and "Create account" pages do the same for a signed-in user (redirect to the return address, `/properties` or `/manage`), or show the form as normal?
