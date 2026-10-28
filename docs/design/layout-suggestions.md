# Layout suggestions (design exploration)

> **Reference mockup for internal design discussion only. Images and text belong to the credited sites and must be replaced with Sunrise Letting's own content before any use.**
>
> In practice this document and its mockups contain **no** images or text from other sites. Every photo is a neutral placeholder and every listing is fictional (for example "Example Road, Leeds"). Only **layout patterns** were taken from the sites below, and each pattern is credited to the page where it was seen. See "About the example content" below for why.

Status: **decided** (26 October 2026). The user chose **Suggestion 2, Brand-led agency**, and kept placeholders for photos and listing content. The site-wide decisions are now in `docs/design/design-system.md`, which takes precedence over this document and the mockups. The home page "service" blocks are kept only as placeholders, because the brief has no pages for them (see design-system.md section 4.3 and its open question 1).

Update (28 October 2026): the product owner decided in issues #2 and #3 that there is **one shared sign-in page** for Visitors and Property Managers, with no separate Property Manager sign-in page. Where this document or its mockups show a Property Manager sign-in link (for example the "Already a manager? Sign in" block in Suggestion 2), read it as a link to the shared sign-in page. The signed-in headers, sign out and the Property Manager area (Variant A under `/manage`) are in `design-system.md` sections 3.1, 3.5 and 3.6. Site scripts of our own (password show/hide and the session time-out warning) are allowed alongside HTMX; see `design-system.md` 5.7.

This is not an issue spec and not the design system. It builds on `docs/design/9-layout-options.md`, which was written from general knowledge. This document adds a **live review** of six sites (26 October 2026) and one new option (the map and list split).

Mockups (open in a browser, no JavaScript, inline CSS only):

- `docs/design/mockups/layout-1-search-first.html`
- `docs/design/mockups/layout-2-brand-led-agency.html`
- `docs/design/mockups/layout-3-map-list-split.html`

---

## About the example content

The request asked for real listing photos (hotlinked) and real listing text from the researched sites. I didn't do this because:

1. The rules for this design role say to take layout patterns only and never copy another site's branding, copy or visual identity.
2. Listing photos and descriptions are owned by the portals, agents or landlords. Hotlinking them would also make every mockup view send a request to their servers, and the links break when listings are let.

Instead:
- Each photo is a grey placeholder box labelled "Photo".
- Listing text is invented, with realistic field types (rent per month, bedrooms, available date, short summary).
- Under each example card is a visible line: "Example content (fictional). Layout pattern seen on: <site> – <page URL>".

**Resolved:** the user chose to keep placeholders for photos and listing content for now.

---

## 1. Research: sites reviewed

| Site | Pages fetched | What could be seen |
|---|---|---|
| Rightmove | [Rent landing](https://www.rightmove.co.uk/property-to-rent.html), [London results](https://www.rightmove.co.uk/property-to-rent/London.html) | Landing page has a single location box plus Search; results are a vertical **list** with a row of filter dropdowns **above** it (radius, min/max rent, min/max beds, type, "Filters"); result count, sort, "Map" link; cards show photo, price per month and per week, type, beds/baths, address, summary, agent; numbered pagination with Previous/Next. |
| Zoopla | [To-rent landing](https://www.zoopla.co.uk/to-rent/) | H1 and one-line intro, then the search widget. **The search widget did not render in the fetch** ("Loading search"), and the header and footer were not in the extracted content. A five-step numbered renting guide sits below. Results and detail pages were not reviewed. |
| OpenRent | [Home](https://www.openrent.co.uk/), [Leeds results](https://www.openrent.co.uk/properties-to-rent/leeds), [one listing](https://www.openrent.co.uk/property-to-rent/leeds/1-bed-flat-st-george-building-ls1/3076221), [tenant process](https://www.openrent.co.uk/tenants) | Results: top row of dropdowns (price, beds, distance) plus "More filters"; list/map toggle; list cards with thumbnail, price pcm and pw, title, summary, bullet facts, "Enquire". Detail: photos first, then title and key facts, description, price and bills, availability, features, EPC, static map; a **sticky side panel** with rent and the two calls to action ("request viewing", "rent now"). The enquiry form itself is on a separate page behind sign-in, so it **could not be seen**. |
| SpareRoom | [Home](https://www.spareroom.co.uk/), [Leeds results](https://www.spareroom.co.uk/flatshare/leeds) | Home: tabbed search panel first, then "why use us" blurbs. Results: breadcrumb, "Showing 1–10 of N results" above and below the list, sort control, "View on map" link, **list** view, Next link only. |
| Savills | [Rent results](https://search.savills.com/list/property-to-rent/england) | "Filter results" panel with grouped filters and "Clear filters"; result count and sort above a vertical list; cards with photo, badges, address, price per month, highlights, agent and "Contact now"; numbered pagination with first/last and ellipsis. No map toggle seen. Detail page not reviewed. |
| Foxtons | [Home](https://www.foxtons.co.uk/), [London rent results](https://www.foxtons.co.uk/properties-to-rent/london), [one listing](https://www.foxtons.co.uk/properties-to-rent/w1s/chpk5689146) | Home: brand-led hero with headline, Buy/Rent toggle and search box; service blocks; promotional banners; office finder and opening hours. Results: heading with count, sort, "List & Map view" toggle, "Narrow search results" panel with area/type/bedroom links. Detail: gallery with Photos/Video/Location tabs, title, summary, price, then **Book a viewing** right under the price, key features, further details, EPC; an enquiry panel with a **list of viewing dates** is repeated lower down. No name/email fields were visible, so the rest of the viewing flow **could not be seen**. |

Accessibility references:
- [GDS: improving accessibility on GOV.UK search](https://technology.blog.gov.uk/2014/08/14/improving-accessibility-on-gov-uk-search/): announce only the result count in a live region that stays in the DOM, not the whole list.
- [GOV.UK publishing design guide: search filter](https://design-guide.publishing.service.gov.uk/patterns/search-filter/) and [MoJ filter component](https://design-patterns.service.justice.gov.uk/components/filter/): filters in a disclosure on small screens; filters apply to the whole set and return to page 1.
- [DfE: applying filters consistently](https://dfedigital.blog.gov.uk/2025/03/03/applying-filters-consistently-in-dfe): GOV.UK has no official filter component yet.

### Patterns seen on most sites

- **Search entry:** one location field plus a Search button is the universal first step (Rightmove, Foxtons, OpenRent, SpareRoom). Price and bedroom filters come on the results page.
- **Results:** every reviewed results page uses a **vertical list**, not a grid. Filters sit in a row above the list (Rightmove, OpenRent) or in a panel (Savills, Foxtons). Count and sort sit directly above the first result.
- **Map:** offered as a **toggle** or separate link (Rightmove, OpenRent, Foxtons, SpareRoom), never as the only view.
- **Detail:** photos first, then title and price. The viewing call to action sits **next to or just under the price**, and is often repeated lower down or kept in a sticky side panel.
- **Viewing/apply:** both sites whose flows were visible send the user to a **separate step** (messaging page, date list) rather than a form inline on the detail page. Sign-in is required before contacting.
- **Clutter to avoid:** in-list promotional cards, badges, app prompts, guides, news and testimonials. None of these are in the Sunrise Letting brief.

---

## 2. Constraints that apply to every suggestion

- Thymeleaf pages in the `web` service. Navigation is full page loads; HTMX is only for in-page updates.
- No Node toolchain or CDN. Libraries only as WebJars.
- Core tasks work without JavaScript.
- Brief features: sign up and log in (#1, #2), Property Manager log in (#3), search (#4), property details (#5), request a viewing (#6), apply (#7), edit a listing (#8).
- WCAG 2.2 AA throughout. Particular 2.2 criteria that layouts affect: 2.4.11 Focus Not Obscured (sticky panels and bars), 2.5.8 Target Size Minimum (24 by 24 CSS pixels for filter controls and pagination), 3.3.7 Redundant Entry (don't re-ask details in the apply flow).
- The HTMX search contract is the same in all three suggestions: the filter form is a plain `GET /properties?...`. With HTMX it gets `hx-get="/properties"`, `hx-target="#search-results"`, `hx-push-url="true"`. The controller returns the results fragment when the `HX-Request` header is present and the full page otherwise, with `Vary: HX-Request`. Tests assert both.

Shared skeleton (unchanged from `9-layout-options.md` section 2): skip link, `<header>` with site name and `<nav aria-label="Main">`, `<main id="main-content">`, `<footer>`.

---

## 3. Suggestion 1: Search-first portal

**Inspired by:** Rightmove results (filter row above a list, count and sort, numbered pagination), OpenRent results and detail (list cards with bullet facts; side panel with rent and calls to action), SpareRoom results (count above and below the list, plain list). Colours and type are neutral and Sunrise Letting's own.

Mockup: `docs/design/mockups/layout-1-search-first.html`

### Home

Mobile:
```
+------------------------------+
| SUNRISE LETTING     [Menu]   |
+------------------------------+
| Find a home to rent          |
|                              |
| Location                     |
| [Town, area or postcode    ] |
| [ Search ]                   |
|                              |
| See all properties to let >  |
|                              |
| How it works                 |
| 1 Find a property            |
| 2 Request a viewing          |
| 3 Apply online               |
+------------------------------+
| footer: legal links, (c)     |
+------------------------------+
```

Wide:
```
+----------------------------------------------------------------------+
| SUNRISE LETTING              Properties to let  Sign in  Create account
+----------------------------------------------------------------------+
|                       Find a home to rent                            |
|        Location                                                      |
|        [ Town, area or postcode                     ] [ Search ]     |
|        See all properties to let >                                   |
|                                                                      |
|   How it works:  1 Find a property  2 Request a viewing  3 Apply     |
+----------------------------------------------------------------------+
| footer                                                               |
+----------------------------------------------------------------------+
```

### Search results

Mobile:
```
+------------------------------+
| header                       |
+------------------------------+
| Properties to let in Leeds   |
| Location [Leeds          ]   |
| > Filter by rent and beds    |  <details>, open if any set
|   Min rent [No min  v]       |
|   Max rent [No max  v]       |
|   Bedrooms [Any     v]       |
| [ Search ]                   |
|                              |
| 12 properties found          |  role="status"
| Sort [Newest v]              |
| +--------------------------+ |
| | [ photo             ]    | |
| | 2 bedroom flat (link)    | |
| | Example Road, Leeds      | |
| | £1,200 per month         | |
| | Available 1 Dec 2026     | |
| +--------------------------+ |
| ...                          |
| < Previous  Page 1 of 3  Next >
+------------------------------+
```

Wide:
```
+----------------------------------------------------------------------+
| header                                                               |
+----------------------------------------------------------------------+
| Properties to let in Leeds                                           |
| Location [Leeds   ] Min rent [v] Max rent [v] Bedrooms [v] [Search]  |
| ---------------------------------------------------------------------|
| 12 properties found                               Sort [Newest   v]  |
| +----------+  2 bedroom flat, Example Road, Leeds   (heading link)   |
| |  photo   |  £1,200 per month                                       |
| |          |  2 bedrooms · 1 bathroom · Unfurnished                  |
| +----------+  Available from 1 December 2026                         |
| ---------------------------------------------------------------------|
| +----------+  1 bedroom flat, Sample Street, Leeds                   |
| ...                                                                  |
|                    < Previous  1  2  3  Next >                       |
+----------------------------------------------------------------------+
```

### Property detail

Mobile:
```
+------------------------------+
| < Back to search results     |
| 2 bedroom flat,              |
| Example Road, Leeds          |
| [ main photo           ]     |
| See all 6 photos >           |
| £1,200 per month             |
| Available 1 Dec 2026         |
| [ Request a viewing ]        |
| [ Apply for this property ]  |  secondary
| Sign in first...             |
| Key facts  (dl)              |
| Description                  |
+------------------------------+
```

Wide:
```
+----------------------------------------------------------------------+
| < Back to search results                                             |
| 2 bedroom flat, Example Road, Leeds                                  |
| +--------------------------------------+  +------------------------+ |
| |          main photo                  |  | £1,200 per month       | |
| +--------------------------------------+  | Deposit £1,380         | |
| [thumb][thumb][thumb]                     | Available 1 Dec 2026   | |
| Key facts (dl)                            | [ Request a viewing ]  | |
| Description                               | [ Apply for property ] | |
|                                           | Sign in first ...      | |
|                                           +------------------------+ |
+----------------------------------------------------------------------+
```

### Where HTMX fits
- **Search and filters:** submit-driven by default (`hx-trigger="submit"`), swapping `#search-results` (count, sort, list, pagination). Optional: `hx-trigger="change"` on the selects with a short delay. Without JS the same form does a full page `GET`.
- **Sort and pagination:** links/forms with `hx-get` to the same URL, same target; plain links without JS.
- **Inline validation:** on the viewing and apply forms (#6, #7) and sign up (#1), e.g. `hx-post="/validate/email"` on `blur`, swapping only that field's error message element. The server still validates the full submit. (For sign up this is now decided differently in #1: the **whole field group** is swapped (except Password and Confirm password, which swap only their error message), and only fields that have a value are checked. See `design-system.md` 5.4.)
- **Not HTMX:** detail page, sign in, viewing/apply pages are full page loads.

### Accessibility notes
- Filter form is `<form role="search">` with a visible heading. On mobile, filters are a native `<details>` (no JS).
- The count "12 properties found" sits in a `role="status"` element **outside** the swapped list, which is replaced with `hx-swap="innerHTML"` on a stable container, so only the count is announced.
- Page `<title>` includes the page number ("Properties to let in Leeds (page 2 of 3) - Sunrise Letting"), updated via `hx-push-url` and an out-of-band `<title>` swap.
- Each result is an `<article>` with the address heading as the only link; the photo is decorative in the list (`alt=""`) because the heading already names the property.
- Side panel on wide screens is **not sticky** by default (WCAG 2.4.11 and 400% zoom). If sticky is wanted, it switches off below 64em.

### Pros and cons for Sunrise Letting
Pros:
- Matches what renters see on every reviewed portal: list, count, sort, filter row.
- Photos are optional; rows still read well as text.
- One clean HTMX fragment; simple MockMvc tests.
- Grows from the plain "task-first" option in `9-layout-options.md` without rework.

Cons:
- Filter row needs care between about 40em and 64em, where it wraps.
- Looks generic, with little brand personality.

---

## 4. Suggestion 2: Brand-led agency

**Inspired by:** Foxtons home (brand hero with search box over it, service blocks), Foxtons detail (Book a viewing directly under the price, enquiry panel repeated lower down), Savills results (filter panel with grouped filters and "Clear filters", rich list cards). Only the arrangement is used; no brand styling.

Mockup: `docs/design/mockups/layout-2-brand-led-agency.html`

### Home

Mobile:
```
+------------------------------+
| SUNRISE LETTING     [Menu]   |
+------------------------------+
|##### short hero band ########|
| +--------------------------+ |
| | Find your next home      | |  solid panel, not text on photo
| | Location [            ]  | |
| | [ Search ]               | |
| +--------------------------+ |
| Renting with us              |
| [Viewings] [Applying]        |  two text blocks with links
|                              |
| Contact the lettings team    |
+------------------------------+
```

Wide:
```
+----------------------------------------------------------------------+
| SUNRISE LETTING              Properties to let  Sign in  Create account
+----------------------------------------------------------------------+
|############################ hero photo ##############################|
|##   +--------------------------------------------+                 ##|
|##   | Find your next home                        |                 ##|
|##   | [ Town, area or postcode      ] [ Search ] |                 ##|
|##   +--------------------------------------------+                 ##|
|######################################################################|
|  +------------------+ +------------------+ +------------------+      |
|  | Book a viewing   | | Apply online     | | Already a        |      |
|  | how it works >   | | what you need >  | | manager? Sign in>|      |
|  +------------------+ +------------------+ +------------------+      |
+----------------------------------------------------------------------+
```

### Search results

Mobile:
```
+------------------------------+
| Properties to let            |
| [ Filter properties (2) v ]  |  <details>
| 12 properties   Sort [v]     |
| +--------------------------+ |
| | [ large photo         ]  | |
| | £1,200 per month         | |
| | 2 bedroom flat (link)    | |
| | Example Road, Leeds      | |
| | - Garden                 | |
| | - Available now          | |
| +--------------------------+ |
+------------------------------+
```

Wide:
```
+----------------------------------------------------------------------+
| Properties to let                                                    |
| +-------------------+  12 properties found        Sort [Newest   v]  |
| | Filter properties |  +--------------------------------------------+|
| | Location [     ]  |  | +--------------+ £1,200 per month          ||
| | Rent              |  | |              | 2 bedroom flat (link)     ||
| |  Min [v] Max [v]  |  | |  large photo | Example Road, Leeds       ||
| | Bedrooms          |  | |              | - Garden  - Available now ||
| |  ( ) Any ( ) 1 .. |  | +--------------+ [ Request a viewing ]     ||
| | [ Apply filters ] |  +--------------------------------------------+|
| | Clear filters     |  ...                                          |
| +-------------------+       < Previous  1  2  3  Next >              |
+----------------------------------------------------------------------+
```

### Property detail

Mobile:
```
+------------------------------+
| [ full-width photo     ]     |
| Photo 1 of 6 · All photos >  |
| 2 bedroom flat               |
| Example Road, Leeds          |
| £1,200 per month             |
| [ Request a viewing ]        |
| [ Apply for this property ]  |
| Key features (ul)            |
| Further details (dl)         |
| Description                  |
| +--------------------------+ |
| | Interested? (repeat CTA) | |
| +--------------------------+ |
+------------------------------+
```

Wide:
```
+----------------------------------------------------------------------+
|############### wide photo  |  [thumb] [thumb]  #######################|
|###############             |  [thumb] All 6 photos >  ###############|
| 2 bedroom flat, Example Road, Leeds              £1,200 per month    |
| Short summary line           [ Request a viewing ] [ Apply ]         |
| ---------------------------------------------------------------------|
| Key features          | Description                                  |
|  - Garden             |  ...                                         |
| Further details (dl)  |                                              |
| ---------------------------------------------------------------------|
| Interested in this property?  [ Request a viewing ] [ Apply ]        |
+----------------------------------------------------------------------+
```

### Where HTMX fits
- **Filters:** same `#search-results` contract. Filter count badge "(2)" in the disclosure summary is updated with an out-of-band swap.
- **"Clear filters":** a plain link to `/properties`, with `hx-get` enhancement.
- **Inline validation:** same as Suggestion 1.
- **Gallery:** "All photos" is a **separate page** (`/properties/{id}/photos`) listing every image with alt text. No carousel, so no JS needed.

### Accessibility notes
- Hero text sits on a **solid panel** (contrast 4.5:1 regardless of the photo); hero photo is decorative (`alt=""`).
- Gallery photos need meaningful alt text, which means #8 must let Property Managers enter it.
- Card "Request a viewing" buttons inside the list must have unique accessible names ("Request a viewing for 2 bedroom flat, Example Road").
- Repeated calls to action are fine but must use the same text each time (WCAG 3.2.4 Consistent Identification).
- No carousel. If one is ever added, it needs pause and keyboard controls.

### Pros and cons for Sunrise Letting
Pros:
- Strongest brand presence; feels like a high-street agency.
- Calls to action close to the price and repeated at the end, as Foxtons does.

Cons:
- **Depends on photos and image hosting**, which #5 hasn't decided.
- The home page service blocks and repeated card buttons add content the brief doesn't ask for.
- Most CSS and the most alt-text work.
- Higher accessibility risk (images, contrast, repeated controls).

---

## 5. Suggestion 3: Map and list split

**Inspired by:** the list/map toggles on Rightmove ("Map" link, "Show results on map"), OpenRent ("Map View" / "List View") and Foxtons ("List & Map view"), and the static clickable map on OpenRent's detail page. On every reviewed site the map is an **alternative** to the list, never the only view; this suggestion keeps that.

**Not in the brief.** A map needs a JavaScript map library (as a WebJar) and an external map tile service, plus latitude and longitude for each property from the `properties` service. This suggestion is included because the user asked to see it; it would need its own issue.

Mockup: `docs/design/mockups/layout-3-map-list-split.html`

### Home

Same as Suggestion 1, plus a secondary link "Search on a map".

### Search results

Mobile (list is default, map is a separate view):
```
+------------------------------+
| Properties to let in Leeds   |
| [Location] [Search]          |
| > Filters                    |
| [ List ]  [ Map ]            |  two links, current one marked
| 12 properties found          |
| +--------------------------+ |
| | 2 bedroom flat  (link)   | |
| | £1,200 pcm · 2 bed       | |
| +--------------------------+ |
+------------------------------+
```

Wide (split view, list on the left in reading order):
```
+----------------------------------------------------------------------+
| Properties to let in Leeds   [Location   ] [Search]  > Filters       |
| [ List ] [ List and map ]                       12 properties found  |
| +---------------------------+  +-------------------------------------+
| | 1  2 bedroom flat (link)  |  |                                     |
| |    £1,200 pcm · 2 bed     |  |        map with numbered pins       |
| | 2  1 bedroom flat         |  |          (1)        (2)             |
| |    £950 pcm · 1 bed       |  |                (3)                  |
| | 3  3 bedroom house        |  |                                     |
| |    £1,500 pcm · 3 bed     |  |   Without JS: static image or       |
| | ...                       |  |   hidden; list still complete       |
| | < Prev  1 2 3  Next >     |  |                                     |
| +---------------------------+  +-------------------------------------+
+----------------------------------------------------------------------+
```

### Property detail

```
+----------------------------------------------------------------------+
| < Back to results                                                    |
| 2 bedroom flat, Example Road, Leeds          £1,200 per month        |
| [ main photo                      ]  [ Request a viewing ]           |
|                                      [ Apply for this property ]     |
| Key facts (dl)                                                       |
| Description                                                          |
| Location                                                             |
| Example Road, Leeds LS1  (text address always shown)                 |
| [ static map image, alt="Map showing Example Road, Leeds" ]          |
+----------------------------------------------------------------------+
```
Mobile: one column in the order above.

### Where HTMX fits
- **Filters:** same `#search-results` contract. The fragment carries the list **and** a `data-` list of pin positions; with JS, the map script redraws pins after `htmx:afterSwap`.
- **View toggle:** "List" and "List and map" are plain links (`?view=map`), so they work without JS; HTMX can swap `#search-results` with `hx-push-url`.
- **"Search this area" on map pan:** would need JS-only behaviour. **Not recommended**; keep searches form-driven.

### Accessibility notes
- The list is the accessible view and must always be complete; the map is a supplement. Numbered pins match numbered list items.
- Map pins must be keyboard focusable with names, or the map must be `aria-hidden="true"` with the list as the equivalent. The second is far simpler and recommended.
- Map panning must not trap focus or capture arrow keys outside the map.
- At 400% zoom / 320px wide, the split must collapse to list only.
- Third-party tiles may set cookies and fail contrast; needs a cookie and privacy review.

### Pros and cons for Sunrise Letting
Pros:
- Location is the first thing renters search by; a map helps compare areas.
- Familiar from three of the six reviewed sites.

Cons:
- **Out of brief**; needs a new issue, a WebJar map library, a tile provider (cost, privacy, cookies) and coordinates in the `properties` service.
- JS-dependent part; harder to test with MockMvc (only the fragment and data attributes can be asserted).
- Highest accessibility and maintenance risk of the three.

---

## 6. Comparison

| | 1 Search-first portal | 2 Brand-led agency | 3 Map and list split |
|---|---|---|---|
| Seen on | Rightmove, OpenRent, SpareRoom | Foxtons, Savills | Rightmove, OpenRent, Foxtons (as a toggle) |
| Within the brief | Yes | Mostly (home blocks extra) | No (map is a new feature) |
| Needs photos | No | Yes | No |
| Works without JS | Fully | Fully | List yes, map no |
| Accessibility risk | Low | Medium | High |
| Effort in `web` | Medium | Medium to large | Large, plus a new issue |

## 7. Recommendation and decision

**Decision:** the user chose **Suggestion 2, Brand-led agency**, with placeholders for photos and listing content. See `docs/design/design-system.md`. The recommendation below is kept for the record.

Recommendation was **Suggestion 1, Search-first portal**, using the plain styling of the "task-first" option in `9-layout-options.md` until photos and branding are decided.

- Every reviewed results page uses a vertical list with count, sort and filters above it, so renters will find it familiar.
- It covers #1 to #8 without new features and isn't blocked by the photo question in #5.
- It has the lowest accessibility risk and the simplest HTMX fragment to test.
- The detail page's side panel can take Suggestion 2's ideas later (a larger photo, calls to action repeated at the end) once branding and image hosting exist.
- Suggestion 3's map should only be raised as a separate issue if users ask for it.

## 8. Open questions

1. ~~**Real content in mockups:** keep placeholders, or use the user's own photos and sample listings?~~ **Resolved (26 October 2026):** keep placeholders for photos and listing content for now.
2. ~~**Which suggestion** should drive the design system?~~ **Resolved (26 October 2026):** Suggestion 2, Brand-led agency. Written up in `docs/design/design-system.md`.
3. **Filter placement on wide screens:** row above the list (Rightmove, OpenRent) or side panel (Savills, Foxtons)? The design system uses Suggestion 2's side panel until this is answered.
4. **Search trigger:** on submit only, or live on change? The design system uses submit only until this is answered.
5. **Photos and branding:** still open from `9-layout-options.md` questions 6 and 7. All image slots are placeholders until answered.
6. **Map:** should a map ever be raised as its own issue, or ruled out?
7. **Pagination:** numbered pages (Rightmove, Savills) or Next/Previous only (SpareRoom)? The design system uses Previous and Next only until this is answered.
