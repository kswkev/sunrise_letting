# Issue #9: site layout options (exploration)

Status: **partly decided**. This is not the design spec for #9 and not the design system. It sets out options so the user can choose the site-wide layout (the brief's open question "Layout for website"). The site-wide decisions are recorded in `docs/design/design-system.md`, which takes precedence over this document.

Decisions so far:
- **Visitor layout (question 1):** superseded by `layout-suggestions.md`, where the user chose Suggestion 2, Brand-led agency (26 October 2026).
- **Property Manager area (question 2):** Variant A, under the URL prefix `/manage`. Decided by the product owner in issue #3 (28 October 2026).
- **Header navigation (question 3) and sign-in pages (question 4):** decided by the product owner in issues #1, #2 and #3 (28 October 2026). See section 7.

## Basis for this comparison

This comparison comes from **general knowledge** of well-known UK letting and property sites (Rightmove, Zoopla, OnTheMarket, OpenRent, SpareRoom, typical high-street letting agency sites) and of public-sector design conventions (GOV.UK Design System). **It is not a live review.** No sites were visited while writing it, and those sites may have changed. Only general layout patterns are described. No branding, copy or visual identity is taken from any site.

## Constraints that shape every option

- Pages are server-rendered with Thymeleaf in the `web` service. Each navigation is a full page load. HTMX is used only for in-page updates.
- There is no Node toolchain or CDN. CSS is plain hand-written CSS under `static/`, and any JavaScript library has to come as a WebJar. Interactive maps, carousels and similar widgets would each need a WebJar and possibly an external tile or image service. None of them is in the brief.
- Core tasks must work without JavaScript.
- The brief's features: Visitor sign up and log in (#1, #2), Property Manager log in (#3), search (#4), property details (#5), request a viewing (#6), apply (#7) and edit a listing (#8).
- #9 itself lists "Visual design, styling and branding" as out of scope. For #9 the layout decision mostly settles the **shared page skeleton** (head, header, navigation, main, footer, and whether the Property Manager area gets its own layout). The options below differ mainly in how later pages (#4, #5, #8) are laid out inside that skeleton.

---

## 1. Common layout patterns on letting sites

| Area | Common pattern |
|---|---|
| **Header / nav** | Logo on the left. A short primary nav (to rent, to buy, sometimes "find agents"). Sign in or account links on the right. On mobile the nav collapses behind a "Menu" button, and the sign-in link often stays visible. Portals keep the header thin. Agency sites often use a taller header with a phone number. |
| **Hero and search** | Portals put a single location box ("town, area or postcode") front and centre on the home page, with a large background image or plain colour behind it. The full filters (price, bedrooms, type) appear on the *next* step or in a results-page filter panel. Agency sites lead with a full-width photo and often a "featured properties" strip. Lower-cost sites such as OpenRent and SpareRoom have a plainer home page, with search first and a short explanation of how it works. |
| **Results listing** | **List** (one property per row: photo left, details right) is the dominant portal pattern on desktop. A **grid** of cards is common on agency sites. Most large portals also offer a **map** view as a toggle. Results pages show a result count, sort control, pagination and a filter panel (left sidebar or top bar on desktop, a "Filter" button that opens a panel on mobile). |
| **Property detail** | Photo gallery at the top. Headline facts (rent per month, bedrooms, type, available date) near the top. Then description, key features, and sometimes floor plan, map, EPC and deposit. On desktop the calls to action ("Request viewing", "Contact agent") sit in a **side panel**, often sticky. On mobile they sit in a **sticky bottom bar** or directly under the headline facts. |
| **Calls to action** | Two at most, close to the price. One is primary and the other secondary. When an action needs an account, sites usually show the button anyway and ask the user to sign in on click. |
| **Footer** | Several columns of links (about, help, legal), then legal links (privacy, cookies, terms, accessibility) and a copyright line. Agency sites add their office address and regulatory notices. |
| **Mobile** | Single column. Search box first. Filters behind a button. Cards are full width. CTAs are sticky or repeated. Tap targets are large. |
| **Logged-in manager area** | Agency back-office and landlord dashboards use a **separate shell**: plain top bar with the product name, the user's name and "Sign out", sometimes a left side nav, and a dense **table** of listings with an "Edit" link per row. Edit pages are long single-column forms with "Save" and "Cancel" at the bottom. Marketing elements (hero, footer link columns) are dropped. |

---

## 2. The shared skeleton (the same in every option)

Whichever option is chosen, the #9 layout template would contain the parts below. Only the header contents and some proportions change between options.

```
<html lang="en-GB">
  <head>  title "<page> - Sunrise Letting", meta viewport, site CSS, HTMX script (deferred)
  <body>
    <a href="#main-content">Skip to main content</a>        (first focusable element)
    <header>   site name/logo link to "/"  +  <nav aria-label="Main">
    <main id="main-content">   page content (each page fills this slot)
    <footer>   legal links + copyright
```

~~There would also be a Property Manager layout for the `/manage/...` pages (see section 4). It reuses the same `<head>` fragment but has its own header and nav.~~ **Decided (#3):** the Property Manager pages under `/manage` use **the same skeleton** (Variant A in section 4), with a coloured "Property Manager area" band added. There is no separate Property Manager header or nav.

---

## 3. Options

### Option 1: "Portal" (list results with a filter sidebar)

**Inspired by:** the large UK property portals (Rightmove, Zoopla, OnTheMarket). The patterns used are a search box centred on the home page, a list of results beside a filter sidebar, and a detail page with a side panel for actions.

#### Home (desktop)

```
+----------------------------------------------------------------------+
| Skip to main content (visible on focus)                              |
| SUNRISE LETTING                Properties to let  Sign in  Create account
+----------------------------------------------------------------------+
|                                                                      |
|                     Find a home to rent                              |
|                                                                      |
|      Location                                                        |
|      +-------------------------------------------+ +-----------+     |
|      | Town, area or postcode                    | |  Search   |     |
|      +-------------------------------------------+ +-----------+     |
|      Min rent [ No min v]  Max rent [ No max v]  Bedrooms [ Any v]   |
|                                                                      |
|      or browse all properties to let >                               |
|                                                                      |
+----------------------------------------------------------------------+
| Accessibility  Privacy  Cookies  Contact            (c) Sunrise Letting
+----------------------------------------------------------------------+
```

#### Search results (desktop)

```
+----------------------------------------------------------------------+
| SUNRISE LETTING                Properties to let  Sign in  Create account
+----------------------------------------------------------------------+
| Properties to let                                                    |
|                                                                      |
| +--------------------+  12 properties found      Sort [Newest    v]  |
| | Filter properties  |                                               |
| |                    |  +-------------------------------------------+|
| | Location           |  | +--------+  2 bedroom flat                 ||
| | [Leeds          ]  |  | | photo  |  Example Road, Leeds            ||
| | Min rent           |  | | (opt.) |  £1,200 per month               ||
| | [No min       v]   |  | +--------+  Available from 1 December 2026 ||
| | Max rent           |  |             View details >                 ||
| | [No max       v]   |  +-------------------------------------------+|
| | Bedrooms           |  +-------------------------------------------+|
| | [Any          v]   |  | +--------+  1 bedroom flat ...             ||
| |                    |  | | photo  |                                 ||
| | [ Apply filters ]  |  | +--------+                                 ||
| +--------------------+  +-------------------------------------------+|
|                                                                      |
|                         < Previous   1  2  3   Next >                |
+----------------------------------------------------------------------+
| footer                                                               |
+----------------------------------------------------------------------+
```

#### Property detail (desktop)

```
+----------------------------------------------------------------------+
| header                                                               |
+----------------------------------------------------------------------+
| < Back to search results                                             |
| 2 bedroom flat, Example Road, Leeds                                  |
|                                                                      |
| +--------------------------------------+  +------------------------+ |
| |                                      |  | £1,200 per month       | |
| |          main photo (optional)       |  | Deposit £1,380         | |
| |                                      |  | Available 1 Dec 2026   | |
| +--------------------------------------+  |                        | |
| [thumb] [thumb] [thumb]                   | [ Request a viewing ]  | |
|                                           | [ Apply for property ] | |
| Key facts                                 |                        | |
|  Bedrooms 2 | Bathrooms 1 | Flat          | You'll be asked to     | |
|                                           | sign in first.         | |
| Description                               +------------------------+ |
|  Lorem ipsum ...                          (panel stays put on scroll |
|                                            only if JS/CSS sticky)    |
+----------------------------------------------------------------------+
| footer                                                               |
+----------------------------------------------------------------------+
```

**Mobile:** The header shows the logo, a "Menu" button and "Sign in". The home search stacks into one column. On results, the sidebar becomes a `<details>` disclosure titled "Filter properties" above the list. It works without JavaScript and is closed by default. Result rows stack with the photo on top. On detail, the side panel moves directly under the title (price, then both buttons) and is **not** sticky, so it never covers content.

**Fit with the brief and HTMX:**
- #4 search: the filter form is `GET /properties?...`, so it works without JS. With HTMX the form gets `hx-get="/properties"`, `hx-target="#search-results"` and `hx-push-url="true"`. The controller returns the `properties/search :: results` fragment when the `HX-Request` header is present and the full page otherwise (set `Vary: HX-Request`). Tests can assert both responses. Pagination links work the same way.
- #5 detail: a full page load. The CTA panel links to the #6 and #7 pages. Signed-out users go to sign in and then return.
- #1 and #2 sign in and sign up are full pages linked from the header.
- The sort control and the search criteria depend on #4's open questions. They are shown here as placeholders.

**Accessibility:**
- Filters are a `<form role="search">` with an `<h2>`. The result count is in a polite live region (`role="status"`) so screen-reader users hear "12 properties found" after an HTMX swap.
- Each result is an `<article>` whose heading contains the link. Don't make the whole card a link.
- The sticky side panel on wide screens must not cover content at 400% zoom. Turn off stickiness below a set width.

**Pros:**
- Renters know this pattern well.
- Room for filters on desktop.
- Photos are optional: the row still works as text.
- Fits HTMX fragment swapping cleanly.

**Cons:**
- More layout CSS: sidebar, a two-column detail page, a responsive switch.
- If sorting or filters grow, the sidebar gets crowded.

**Rough effort (web service):**
- #9 skeleton: small. It is the same skeleton as every option, with a header nav of three links.
- Later pages: medium. Two-column results and detail pages plus the mobile disclosure.

---

### Option 2: "Agency showcase" (photo-led, card grid)

**Inspired by:** typical high-street letting agency websites. The patterns used are a full-width hero photo with a search box over it, a featured-properties strip, results as a grid of cards with a filter bar along the top, and a wide gallery on the detail page.

#### Home (desktop)

```
+----------------------------------------------------------------------+
| SUNRISE LETTING                Properties to let  Sign in  Create account
+----------------------------------------------------------------------+
|######################################################################|
|##################  full-width hero photograph  ######################|
|##                                                                  ##|
|##        +----------------------------------------------+          ##|
|##        | Find your next home                          |          ##|
|##        | [ Town, area or postcode         ] [Search ] |          ##|
|##        +----------------------------------------------+          ##|
|##                                                                  ##|
|######################################################################|
|                                                                      |
| Latest properties  (NEW FEATURE - not in the brief, see questions)   |
| +--------------+  +--------------+  +--------------+                 |
| |   photo      |  |   photo      |  |   photo      |                 |
| | £1,200 pcm   |  | £950 pcm     |  | £1,500 pcm   |                 |
| | 2 bed flat   |  | 1 bed flat   |  | 3 bed house  |                 |
| +--------------+  +--------------+  +--------------+                 |
|                                                                      |
+----------------------------------------------------------------------+
| About | Contact | Accessibility | Privacy | Cookies    (c) Sunrise   |
+----------------------------------------------------------------------+
```

#### Search results (desktop)

```
+----------------------------------------------------------------------+
| header                                                               |
+----------------------------------------------------------------------+
| Properties to let                                                    |
| Location [Leeds     ] Min [No min v] Max [No max v] Beds [Any v] [Go] |
| ---------------------------------------------------------------------|
| 12 properties found                                 Sort [Newest v]  |
|                                                                      |
| +--------------+  +--------------+  +--------------+                 |
| |   photo      |  |   photo      |  |   photo      |                 |
| |              |  |              |  |              |                 |
| | £1,200 pcm   |  | £950 pcm     |  | £1,500 pcm   |                 |
| | 2 bed flat   |  | 1 bed flat   |  | 3 bed house  |                 |
| | Example Rd   |  | Sample St    |  | Test Lane    |                 |
| +--------------+  +--------------+  +--------------+                 |
| +--------------+  +--------------+  +--------------+                 |
| |  ...         |  |  ...         |  |  ...         |                 |
| +--------------+  +--------------+  +--------------+                 |
|                   < Previous   1  2  3   Next >                      |
+----------------------------------------------------------------------+
```

#### Property detail (desktop)

```
+----------------------------------------------------------------------+
| header                                                               |
+----------------------------------------------------------------------+
|######################################################################|
|#############  wide photo gallery (needs photos)  ####################|
|######################################################################|
| 2 bedroom flat, Example Road, Leeds          £1,200 per month        |
|                                [ Request a viewing ] [ Apply ]       |
| -------------------------------------------------------------------- |
| Key facts           | Description                                    |
|  2 bed, 1 bath      |  Lorem ipsum ...                               |
|  Available 1 Dec    |                                                |
+----------------------------------------------------------------------+
```

**Mobile:** The hero image shrinks to a short band, or is hidden, with the search box under it. The filter bar becomes a `<details>` "Filter properties" disclosure. The grid becomes one card per row. On detail, the gallery is a single image followed by a "See all N photos" link to a plain list of images, with no carousel. CTAs stack under the price.

**Fit with the brief and HTMX:**
- Search uses the same HTMX fragment approach as Option 1, swapping the grid container.
- **Depends heavily on photos.** #5 still has "Is image/media hosting needed now?" open. Without photos, the hero, cards and gallery look empty.
- The home "Latest properties" strip is **not in the brief or any issue**. It would need its own issue and a call to the `properties` service from the home page, which #9 rules out. Without it, the home page is a hero and a search box.

**Accessibility:**
- Text over the hero image needs a solid panel behind it to keep 4.5:1 contrast. Don't rely on the photo being dark.
- Hero images are decorative (`alt=""`). Gallery images need meaningful alt text, which Property Managers would have to enter in #8.
- In the grid, keep the reading order equal to the visual order. Don't reorder cards with CSS.
- Avoid carousels. If one is ever added, it needs pause and keyboard controls.

**Pros:**
- Looks the most like a marketing site and showcases properties well.
- Cards are easy to scan when photos exist.

**Cons:**
- Most effort.
- Depends on image hosting, which isn't decided.
- Tempts adding features the brief doesn't have (featured properties, carousel).
- Cards show less text per property than list rows.
- Filter bar space is tight on mid-width screens.

**Rough effort (web service):**
- #9 skeleton: small. The hero and featured content are out of scope for #9.
- Later pages: medium to large. Grid, image handling and alt text, the hero, and a gallery page.

---

### Option 3: "Task-first" (plain, single column)

**Inspired by:** the lower-cost letting sites (OpenRent, SpareRoom), whose pages are plain and search comes first, and by GOV.UK Design System conventions (single column, summary lists, short forms, strong progressive enhancement). The patterns used are a short home page, a search form at the top of a single-column results list, and a detail page that reads top to bottom with the next steps after the key facts.

#### Home (desktop)

```
+----------------------------------------------------------------------+
| SUNRISE LETTING                Properties to let  Sign in  Create account
+----------------------------------------------------------------------+
|                                                                      |
|   Find a home to rent with Sunrise Letting                           |
|                                                                      |
|   Location                                                           |
|   [ Town, area or postcode                    ]                      |
|   [ Search properties ]                                              |
|                                                                      |
|   or see all properties to let                                       |
|                                                                      |
|   How it works                                                       |
|   1. Find a property                                                 |
|   2. Request a viewing                                               |
|   3. Apply online                                                    |
|   You need an account to request a viewing or apply.                 |
|                                                                      |
+----------------------------------------------------------------------+
| Accessibility  Privacy  Cookies  Contact            (c) Sunrise Letting
+----------------------------------------------------------------------+
         (content column max ~ 40em wide, left-aligned in the page)
```

#### Search results (desktop)

```
+----------------------------------------------------------------------+
| header                                                               |
+----------------------------------------------------------------------+
|   Properties to let                                                  |
|                                                                      |
|   Location [ Leeds                         ]  [ Search ]             |
|   > Filter by rent and bedrooms   (<details>, open if any are set)   |
|                                                                      |
|   12 properties found                                                |
|   ---------------------------------------------------------------    |
|   2 bedroom flat, Example Road, Leeds                    (link)      |
|   £1,200 per month  ·  Available from 1 December 2026                |
|   Short summary line ...                                             |
|   ---------------------------------------------------------------    |
|   1 bedroom flat, Sample Street, Leeds                   (link)      |
|   £950 per month  ·  Available now                                   |
|   ---------------------------------------------------------------    |
|                                                                      |
|   < Previous   Page 1 of 3   Next >                                  |
+----------------------------------------------------------------------+
```

A small thumbnail can sit to the left of each row later, if photos are added.

#### Property detail (desktop)

```
+----------------------------------------------------------------------+
| header                                                               |
+----------------------------------------------------------------------+
|   < Back to search results                                           |
|   2 bedroom flat, Example Road, Leeds                                |
|                                                                      |
|   Rent            £1,200 per month                                   |
|   Deposit         £1,380                                             |
|   Available from  1 December 2026                                    |
|   Bedrooms        2                                                  |
|   (summary list <dl>)                                                |
|                                                                      |
|   Next steps                                                         |
|   [ Request a viewing ]   Apply for this property                    |
|   You'll need to sign in or create an account first.                 |
|                                                                      |
|   Description                                                        |
|   Lorem ipsum ...                                                    |
+----------------------------------------------------------------------+
```

**Mobile:** It is almost the same as desktop, because the layout is already one column. The header nav wraps under the logo, or collapses behind a "Menu" `<details>`. The "Search" button stacks under the input.

**Fit with the brief and HTMX:**
- Same fragment contract as Option 1 (`#search-results`, `HX-Request` gives a fragment, otherwise the full page). By default the search runs **on submit only** (HTMX just avoids the full reload), which keeps it predictable for assistive technology. Live updating on change is optional.
- It doesn't need photos, so #5's media question doesn't block anything.
- Forms for #1, #2, #3, #6, #7 and #8 are the same single-column pattern, so one set of components (field, error summary, button) covers every page.

**Accessibility:**
- The simplest reading and tab order of the three options.
- The result count is in a `role="status"` live region.
- Filters inside `<details>` work natively without JavaScript.
- A narrow measure (about 40em) helps reading. It reflows at 320px without horizontal scrolling.
- The fewest custom widgets, so the fewest things to get wrong.

**Pros:**
- Least effort.
- Best progressive enhancement.
- Not blocked by open questions about photos or maps.
- Easiest to test with MockMvc (few conditional regions).
- Grows into Option 1 later (add a sidebar and thumbnails) without rework.

**Cons:**
- Looks plain and less "property portal".
- Less information density on wide screens.
- Without photos it may feel less appealing to renters.

**Rough effort (web service):**
- #9 skeleton: small.
- Later pages: small to medium. One layout pattern throughout.

---

## 4. Property Manager area (applies to any option)

**Decided (28 October 2026, issue #3): Variant A, under the URL prefix `/manage`.** Variant B is not chosen. The `/manage` home page is `/manage` and the first-sign-in password change page is `/manage/change-password`. The decided layout is described in `design-system.md` section 3.6; the wireframes below are kept for the record.

Two variants, independent of the Visitor option chosen. Both live under one URL prefix (`/manage`, now accepted), so the #3 sign-in redirect and access rules are simple.

#### Variant A: same site shell, distinct area banner (chosen)

The header links below have been updated to the decided Property Manager header (#3). The listings table is illustrative only: it belongs to #8. In #3 the `/manage` home page is a placeholder headed "Property Manager area".

```
+----------------------------------------------------------------------+
| SUNRISE LETTING      Properties to let  Property Manager area  [Sign out]
+----------------------------------------------------------------------+
| Property Manager area                                   (coloured band)
+----------------------------------------------------------------------+
|   Property listings                                    (#8, illustrative)
|                                                                      |
|                                                                      |
|   | Property                       | Rent       | Status   |       | |
|   |--------------------------------|------------|----------|-------| |
|   | 2 bed flat, Example Road       | £1,200 pcm | To let   | Edit  | |
|   | 1 bed flat, Sample Street      | £950 pcm   | To let   | Edit  | |
|                                                                      |
+----------------------------------------------------------------------+
| footer (legal links only)                                            |
+----------------------------------------------------------------------+
```

#### Variant B: separate admin shell with a side nav (not chosen)

```
+----------------------------------------------------------------------+
| Sunrise Letting - Property Manager           j.smith   Sign out      |
+------------------+---------------------------------------------------+
| Listings         |  Property listings                                |
|                  |                                                   |
|                  |  | Property                  | Rent     |      |  |
|                  |  |---------------------------|----------|------|  |
|                  |  | 2 bed flat, Example Road  | £1,200   | Edit |  |
|                  |  | 1 bed flat, Sample Street | £950     | Edit |  |
|                  |                                                   |
+------------------+---------------------------------------------------+
```

**Mobile:** Variant A needs no change. In Variant B the side nav moves above the content. The table either becomes stacked rows, each with a heading and an "Edit" link, or scrolls inside a labelled, focusable region.

Variant A is less work and enough for the single Property Manager feature in the brief (#8 edit listings). Variant B suits an area expected to grow, for example with viewings and applications handling, but those are out of scope today. Each "Edit" link names its property for screen readers (for example, visually hidden text "Edit 2 bed flat, Example Road").

---

## 5. Comparison

| | Option 1 Portal | Option 2 Agency showcase | Option 3 Task-first |
|---|---|---|---|
| Familiar to renters | High | High | Medium |
| Needs photos to look right | No (optional) | **Yes** | No |
| Tempts out-of-brief features | Some (map, sort) | Most (featured, carousel, map) | Least |
| Works without JS | Yes | Yes | Yes |
| HTMX search fragment | `#search-results` list | `#search-results` grid | `#search-results` list |
| Accessibility risk | Medium (sidebar, sticky panel) | Higher (images, contrast on hero, grid) | Lowest |
| #9 skeleton effort | Small | Small | Small |
| Effort for later pages | Medium | Medium to large | Small to medium |

## 6. Recommendation

**Option 3 (Task-first) now, built so it can grow into Option 1**, with **Property Manager Variant A**.

Reasons:
- It covers every acceptance criterion in #1 to #8 with the fewest components.
- It isn't blocked by the open questions on photos (#5) or search criteria (#4).
- It is the strongest for WCAG 2.2 AA and no-JS use.
- It is the cheapest to test-drive with MockMvc.

If the user wants a more familiar portal look, Option 1 is the next best choice. The skeleton for #9 is identical either way, so the choice between 1 and 3 doesn't need to be final until #4. Option 2 is not recommended until image hosting is decided and a featured-properties feature is actually wanted.

**Outcome:** the Visitor layout was decided in `layout-suggestions.md` (Suggestion 2, Brand-led agency). The Property Manager area follows the recommended Variant A, as decided in issue #3.

## 7. Questions to answer before writing the design system and the #9 spec

1. ~~**Which option** (1, 2, 3, or a stated mix) should the Visitor pages follow?~~ **Resolved (26 October 2026):** superseded by `layout-suggestions.md`; the user chose Suggestion 2, Brand-led agency.
2. ~~**Property Manager area:** Variant A (same shell with a banner) or Variant B (separate admin shell)? Is `/manage` acceptable as the URL prefix?~~ **Resolved (28 October 2026, issue #3):** Variant A. `/manage` is accepted: the home page is `/manage` and the first-sign-in password change page is `/manage/change-password`. Every Property Manager page is under `/manage`.
3. ~~**Header navigation for Visitors:** are "Properties to let", "Sign in" and "Create account" the right links? When signed in, what should show: "Sign out" only, or the user's name too? There is no account page in the brief.~~ **Resolved (28 October 2026, issues #2 and #3):**
   - Signed out: "Properties to let", "Sign in", "Create account" (unchanged).
   - Signed in as a Visitor: "Properties to let", "Sign out". No name is shown and there is no account page.
   - Signed in as a Property Manager: "Properties to let", "Property Manager area" (links to `/manage`), "Sign out".
   - "Sign out" is a form POST button protected against cross-site requests, not a link. After signing out the user lands on `/properties`.
4. ~~**Sign-in pages:** one shared sign-in page for Visitors and Property Managers, or a separate Property Manager sign-in under `/manage`? This affects the header link and the #2 and #3 designs.~~ **Resolved (28 October 2026, issues #2 and #3):** one shared sign-in page. There is no separate Property Manager sign-in page. After sign-in, Visitors return to the page they came from (paths on this site only), falling back to `/properties`. Property Managers always go to `/manage`, or to `/manage/change-password` first if they still have a temporary password.
5. **Footer links:** which pages will exist (accessibility statement, privacy notice, cookies, contact, terms)? Footer links must not point to missing pages. A privacy notice is likely needed because #1, #6 and #7 collect personal data. Should #9 show only the copyright line until those pages exist?
6. **Branding:** is there a logo, brand colour or typeface for Sunrise Letting? If not, may the design system define a neutral palette that meets AA contrast? (#9 rules out styling, so should the #9 layout ship unstyled with only structural CSS?)
7. **Photos:** will properties have photos (#5's open question)? This decides whether cards and detail pages reserve image space.
8. **Search behaviour (#4):** should results update live as filters change, or only when the user presses Search (HTMX still avoids a full reload)? Which criteria go on the home page form compared with the results-page filters?
9. **Maps:** is a map view wanted at any point? It is not in the brief and would need a JavaScript map library as a WebJar plus an external map tile service. The recommendation is to leave it out.
10. **Home page content:** besides search, should the home page have anything else (for example a short "How it works")? Should a "latest properties" strip be raised as a separate issue, or left out?
11. **Pagination:** numbered pages, "Previous / Next" only, or a "Show more" button (HTMX append with a plain link fallback)? Linked to #4's paging question.
