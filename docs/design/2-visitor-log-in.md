# #2 Visitor log in: design spec

Issue: [#2 Visitor log in](https://github.com/kswkev/sunrise_letting/issues/2)
Depends on: #1 (`docs/design/1-visitor-sign-up.md`), whose return address rules and session this spec reuses.
Design system: `docs/design/design-system.md`. Section numbers in brackets, such as (4.20), refer to it.

Research for this spec (patterns only, nothing copied):
- GOV.UK "Passwords" pattern (fetched): one generic error for a wrong email or password, never keep the entered password.
- W3C ARIA Authoring Practices, alert dialog pattern (fetched): `alertdialog` role, name from a visible heading, description from the message.
- WCAG 2.2 Understanding 2.2.1 Timing Adjustable (fetched): warn in advance, give at least 20 seconds to respond, offer a single simple action to extend, and allow it at least ten times. The 2-minute warning and an unlimited "Stay signed in" meet this.
- The HMRC timeout pattern page (`design.tax.service.gov.uk`) and the OpenRent sign-in page could not be fetched (DNS failure and HTTP 404).

---

## User flow

### Sign in

Entry points:
- "Sign in" in the signed-out header (3.1): `/sign-in`, no return address.
- A redirect from a page that needs a signed-in user: `/sign-in?returnTo=<path>`. Pages that need sign-in come in #6 and #7; in #2 this is `/manage` (see Access denied below).
- A redirect after a time-out: `/sign-in?timedOut=true&returnTo=<path>` (see Time-outs).
- The "sign in" links on the Create an account page (#1), which pass on its return address.

Main path:
1. `GET /sign-in`: the full page with Email address, Password, "Remember me" (unticked) and "Sign in". The valid `returnTo` goes into a hidden field.
2. The Visitor submits (full-page POST, no HTMX).
3. The email is trimmed and matched ignoring case. Identity checks the password and returns the account's identity and role.
4. A session starts (30 minutes idle). With "Remember me" ticked, a remember-me login lasting 30 days is also set.
5. `303` to the return address (same-site paths only, as in #1), otherwise `/properties`. There's no success banner after signing in.

Alternative and error paths:
- **Already signed in, no time-out marker:** `GET /sign-in` doesn't show the form. It sends a `302` to `/properties` for a Visitor, or to `/manage/change-password` or `/manage` for a Property Manager (3.5). The return address is ignored.
- **Already signed in, with `timedOut=true`** (a tab whose timer ran out while another tab kept the session active): `302` straight to the valid return address, otherwise `/properties`. Property Managers go to `/manage` (#3). No form and no message.
- **Empty email or password:** the page is shown again with field errors and an error summary. The email is kept, the password is cleared, "Remember me" keeps its state, and the user isn't signed in.
- **Unknown email or wrong password:** the page is shown again with one form-level error that doesn't say which part was wrong. The email is kept, the password is cleared and the user isn't signed in.
- **Identity unavailable:** the page is shown again with status `503` and a form-level error. The email is kept and the password is cleared.
- **Unsafe return address:** ignored; the Visitor goes to `/properties`.
- **No account yet:** the "Create an account" link goes to `/create-account`, passing on the valid `returnTo`.

### Signed-in header and sign out

- On every page, a signed-in Visitor sees "Properties to let" and "Sign out" (3.1). "Sign in" and "Create account" are not rendered at all.
- "Sign out" is the sign-out button (4.18): `POST /sign-out` with the CSRF token. It ends the session and any remember-me login, then sends a `303` to `/properties`, where the header shows the signed-out items. There's no confirmation message.
- If the session has already ended when "Sign out" is pressed (for example on a page left open), the result is the same: `303` to `/properties`, signed out. A missing or stale CSRF token on `/sign-out` must not show an error page in that case (see Time-outs, "Expired CSRF token").

### Time-outs

Sessions without "Remember me" end after 30 minutes of inactivity. Any request to the web service counts as activity, including HTMX requests and the keep-alive.

**Warning dialog** (4.20). With JavaScript, on any page of a signed-in session without "Remember me":
1. After 28 minutes with no request from this tab, the dialog opens: "You're about to be signed out".
2. "Stay signed in", or Escape: `POST /session/keep-alive`. `204` closes the dialog, restarts the timer and returns focus to where it was. The page isn't reloaded and nothing typed is lost.
3. "Stay signed in" fails (a `401` because the session has ended, a network error, or any other response): `location.replace` to the timed-out address.
4. "Sign out": the same POST as the header button, landing on `/properties`.
5. No response: at 30 minutes, plus a 5-second margin so the server session has certainly ended, `location.replace` to the timed-out address. Back doesn't return to the page.

**Timed-out address:** `/sign-in?timedOut=true&returnTo=<current path and query>`. The server renders `data-timed-out-url="/sign-in?timedOut=true"`, and the script appends `&returnTo=` plus `encodeURIComponent(location.pathname + location.search)`. Using the browser's current location keeps it right after an HTMX `hx-push-url` (for example on search results).

**After a session has run out on the server** (with or without JavaScript):
- **GET of a page that needs sign-in:** `302` to `/sign-in?timedOut=true&returnTo=<request path and query>`.
- **POST of a form that needs sign-in:** `302` to `/sign-in?timedOut=true&returnTo=<request path>`. This relies on the convention that a form posts to the URL of the page it is on, so the return address is that page, which opens with an empty form after sign-in. Anything typed is not kept.
- **Expired CSRF token:** with an expired session, the CSRF token in a POST is also no longer valid, and that check would normally run first and give a `403`. The server must treat a missing or invalid CSRF token on a request whose session has expired the same way as an expired session: redirect to the timed-out sign-in address (or, for `/sign-out`, to `/properties`). It must not show a 403 page.
- **HTMX request:** `HX-Redirect` to the timed-out address instead of a fragment (5.6). The return address is taken from the `HX-Current-URL` request header (its path and query), not from the HTMX endpoint's URL.
- **Keep-alive:** `401` with no body and no redirect (4.20).
- **Users who were never signed in** (no session, or a session that was never authenticated): `/sign-in?returnTo=<path>` without `timedOut`, so no inactivity message.
- **"Remember me":** an expired session with a valid remember-me login is signed back in silently. No redirect and no message.

`GET /sign-in?timedOut=true` with **no** active session shows the sign-in page with the information banner "You were signed out because you were inactive".

### Access denied (Visitor opens the Property Manager area)

- A signed-in Visitor opening any `/manage` URL gets status `403` and the full access-denied page below. Their session is unchanged.
- A signed-out user opening `/manage` is redirected to `/sign-in?returnTo=/manage` (no inactivity message). Property Managers always go to `/manage` after signing in (#3).

---

## Pages and fragments

### Page: Sign in

| | |
|---|---|
| URL | `GET /sign-in` (optional `returnTo`, `timedOut=true`), `POST /sign-in` |
| Purpose | Sign in for Visitors and Property Managers (one shared page, 3.5) |
| Who | Signed-out users. Signed-in users are redirected (see User flow). |
| Layout | Shared skeleton (3.1), signed-out header, no band, account page pattern (3.5) |
| Scripts | HTMX (site default); password field script (4.17). No time-out script, because the page is only shown to signed-out users. |

Form: `<form id="sign-in-form" method="post" action="/sign-in" novalidate>` with `_csrf`, the hidden `returnTo` (when valid) and the hidden `timedOut` (when present), so a failed submit keeps them.

Source order in `<main>`: information banner (only with `timedOut=true`), error summary (only after a failed submit), `<h1>`, form, "No account yet?" paragraph.

Mobile:
```
+------------------------------+
| Sunrise Letting              |
| Properties to let  Sign in   |
| Create account               |
+------------------------------+
| +--------------------------+ |
| | You were signed out      | |  info banner, only with timedOut=true
| | because you were inactive| |
| +--------------------------+ |
| +--------------------------+ |
| | There is a problem       | |  error summary, only after a failed submit
| | The email address or ... | |
| +--------------------------+ |
| Sign in                (h1)  |
|                              |
| Email address                |
| [                        ]   |
| Password                     |
| [                ] [Show]    |
| [x] Remember me              |
|     Stay signed in on this   |
|     device for 30 days...    |
|                              |
| [ Sign in ]                  |
|                              |
| No account yet?              |
| Create an account            |
+------------------------------+
| © Sunrise Letting            |
+------------------------------+
```

Wide (40em and up):
```
+----------------------------------------------------------------------+
| Sunrise Letting              Properties to let  Sign in  Create account
+----------------------------------------------------------------------+
|  +------------------------------------------------+                  |
|  | You were signed out because you were inactive  |  (only if timed out)
|  +------------------------------------------------+                  |
|  Sign in   (h1)                                                      |
|  Email address                                                       |
|  [                                    ]                              |
|  Password                                                            |
|  [                              ] [ Show ]                           |
|  [ ] Remember me                                                     |
|      Stay signed in on this device for 30 days. Don't use this on a  |
|      shared computer.                                                |
|  [ Sign in ]                                                         |
|                                                                      |
|  No account yet? Create an account                                   |
+----------------------------------------------------------------------+
| © Sunrise Letting                                                    |
+----------------------------------------------------------------------+
```
The form column is at most 40em wide, left-aligned (3.5).

### Fields

| Label | Hint | id / name | Control | Attributes |
|---|---|---|---|---|
| Email address | none | `email` / `email` | `type="email"`, form field (4.10), group `#field-email` | `autocomplete="username"` `spellcheck="false"` `autocapitalize="none"` |
| Password | none | `password` / `password` | password field (4.17), group `#field-password`, error slot `#password-error-slot` | `autocomplete="current-password"`; **no** `hx-` attributes |
| Remember me | Stay signed in on this device for 30 days. Don't use this on a shared computer. | `remember-me` / `remember-me` (value `on`) | single checkbox with hint (4.10) | unticked unless it was ticked in a failed submit |

- No `required` and no `maxlength`; the form has `novalidate` (6.6).
- Email: `autocomplete="username"` (not `email`), so password managers pair it with the password.
- No inline validation on this page (5.4).
- The email is rendered back exactly as typed after a failed submit. The password `value` is always empty.
- Button: `<button type="submit" class="button">Sign in</button>`.

### Fragment

None. Sign in and sign out are full-page requests (5.5).

### Signed-in header (all pages)

The Visitor row of the header table (3.1): `<li><a href="/properties">Properties to let</a></li>` and `<li>` with the sign-out form (4.18): `action="/sign-out"`, with `_csrf`. "Properties to let" has `aria-current="page"` on `/properties` pages.

### Time-out warning dialog (all pages, signed-in sessions without "Remember me")

The component in 4.20, with these values:
- `data-timeout-seconds="1800"` for Visitors (`7200` for Property Managers, #3), `data-warning-seconds="120"`
- `data-keep-alive-url="/session/keep-alive"`
- `data-timed-out-url="/sign-in?timedOut=true"` (the script adds `returnTo`)
- Sign-out form inside it: `action="/sign-out"` with `_csrf`. The keep-alive POST sends the same `_csrf` value, which the script reads from this form's hidden field. The page needs no other token markup.

```
+------------------------------------+
| You're about to be signed out (h2) |
|                                    |
| We will sign you out in 2 minutes  |
| unless you choose to stay signed   |
| in.                                |
|                                    |
| [ Stay signed in ]                 |   stacked on mobile,
| [ Sign out ]                       |   side by side at 40em
+------------------------------------+
```

### Endpoint: keep-alive

| | |
|---|---|
| Request | `POST /session/keep-alive`, form-encoded `_csrf` |
| Signed-in session | `204 No Content`; the idle timer is reset |
| Session ended | `401`, no body, no redirect, no `HX-Redirect` |
| Not HTMX | Called only by the time-out script with `fetch`. It returns no HTML. |

### Page: Access denied

| | |
|---|---|
| URL | Any `/manage` URL, requested by a signed-in Visitor |
| Status | `403` |
| Layout | Shared skeleton, the Visitor's signed-in header, **no** Property Manager band (the band is for Property Managers only) |

```
Mobile                              Wide
+------------------------------+    +---------------------------------------------+
| header (Visitor, signed in)  |    | header (Visitor, signed in)                 |
+------------------------------+    +---------------------------------------------+
| You do not have permission   |    | You do not have permission to view this     |
| to view this page      (h1)  |    | page                                  (h1)  |
| This part of the site is for |    | This part of the site is for Sunrise        |
| Sunrise Letting Property     |    | Letting Property Managers only.             |
| Managers only.               |    | Find properties to let                      |
| Find properties to let       |    |                                             |
+------------------------------+    +---------------------------------------------+
```
- `<title>`: "You do not have permission to view this page - Sunrise Letting"
- The link "Find properties to let" goes to `/properties`.
- #3 reuses this page for every `/manage` page.

---

## States

| State | What is shown |
|---|---|
| Empty (first load) | Sign-in form, email empty, "Remember me" unticked, toggle `hidden` in server HTML. No banner, no error summary. |
| Timed out (`timedOut=true`, no active session) | Information banner (4.14 `notification-banner--info`, `role="status"`, `autofocus`) above the `<h1>`, with the heading "You were signed out because you were inactive". Form as normal. |
| Timed out, session still active | No page; `302` to the return address (Visitor) or `/manage` (Property Manager). |
| Already signed in, no marker | No page; `302` (see User flow). |
| Loading | No indicator (full-page submit). |
| Empty fields on submit | Title "Error: Sign in - Sunrise Letting". Error summary with links to `#email` and/or `#password`. Field errors "Enter your email address" and/or "Enter your password". Status `200`. |
| Wrong email or password | Title "Error: Sign in - Sunrise Letting". Form-level error summary (4.13): "There is a problem", paragraph "The email address or password is incorrect." No field marked as wrong. Email kept, password empty, "Remember me" kept. Status `200`. |
| Server error on submit | Title "Error: Sign in - Sunrise Letting". Form-level error summary: "There is a problem", paragraph "Sorry, you can't sign in at the moment. Try again later." Status `503`. |
| Timed out and then a failed submit | The banner stays (because `timedOut` is carried in a hidden field) but loses `autofocus`, which goes to the error summary (4.14). |
| Success | `303` to the return address or `/properties`. No message. |
| Signed out | `303` to `/properties`, signed-out header. No message. |
| Dialog open | Modal dialog, focus on "Stay signed in", the page behind it inert. |
| Dialog "Stay signed in" fails | The tab goes to the timed-out address (above). |
| Access denied | The 403 page above. |
| Not found | Not applicable. |

---

## Content

Sign-in page:
- `<title>`: "Sign in - Sunrise Letting" ("Error: " in front when errors are shown)
- `<h1>`: "Sign in"
- Labels: "Email address", "Password", "Remember me"
- Remember me hint: "Stay signed in on this device for 30 days. Don't use this on a shared computer."
- Button: "Sign in"
- "No account yet? [Create an account]"
- Show/hide: "Show" / "Hide" with visually hidden " password"; status "Your password is visible" / "Your password is hidden"

Messages:

| Situation | Message |
|---|---|
| Email empty | Enter your email address |
| Password empty | Enter your password |
| Wrong email or password (form level) | The email address or password is incorrect. |
| Identity unavailable (form level) | Sorry, you can't sign in at the moment. Try again later. |
| Timed out (information banner heading) | You were signed out because you were inactive |

Header: "Properties to let", "Sign out".

Time-out dialog:
- Heading: "You're about to be signed out"
- Text: "We will sign you out in 2 minutes unless you choose to stay signed in."
- Buttons: "Stay signed in", "Sign out"

Access denied:
- `<h1>`: "You do not have permission to view this page"
- Text: "This part of the site is for Sunrise Letting Property Managers only."
- Link: "Find properties to let"

---

## Accessibility

Beyond the design-system defaults (4.13, 4.14, 4.17, 4.18, 4.20, 6.6, 6.7):
- **Generic error:** the form-level summary is focused on load. Because it has no field links, the next Tab goes to the first control after it in source order, which is the Email address input (the `<h1>` isn't focusable).
- **Banner and summary together:** only the error summary has `autofocus`; the banner stays `role="status"` but isn't focused (4.14).
- **Remember me hint:** linked to the checkbox with `aria-describedby="remember-me-hint"`.
- **Dialog:** opened with `showModal()`, so the rest of the page is inert and focus is trapped. Initial focus is on "Stay signed in" (`autofocus`). Escape counts as "Stay signed in"; the script handles the dialog's `cancel` event and sends the keep-alive instead of just closing it. After a successful keep-alive, focus returns to the element that had it before the dialog opened. If that element no longer exists (for example it was swapped by HTMX), focus goes to `<main>`.
- **No countdown:** the dialog text says "2 minutes" and doesn't update (4.20).
- **Time-out navigation** uses `location.replace`, so Back doesn't show personal data again.
- **Sign-out button** has the same name ("Sign out") in the header and the dialog, so the two match (WCAG 3.2.4).
- **Header** after sign-in has the same position and order as before, with the items replaced (WCAG 3.2.3).
- **Access-denied page:** a full page with a clear `<h1>`, not an empty 403 response.

---

## Acceptance criteria mapping

| Acceptance criterion | Where the design meets it |
|---|---|
| Correct email and password: signed in and returned to the page I came from | Sign in, main path steps 3 to 5. Test: POST with `returnTo=/properties/123` gives `303` to it, signed in. |
| Opened directly: taken to `/properties` | Step 5, no `returnTo`. |
| Return address not on this site: `/properties` | Return address rules from #1. |
| Already signed in as a Visitor: sent to `/properties`, no form | "Already signed in, no marker". Test: `302` to `/properties`, no `#sign-in-form`. |
| Different capitals or spaces in the email still sign in | Step 3 (trim and ignore case). |
| Unknown email or wrong password: not signed in, email kept, password cleared, generic error | States "Wrong email or password"; Content. |
| Empty email or password: see which field | States "Empty fields on submit". |
| Inactive 30 minutes without "Remember me": signed out | Time-outs; session 30 minutes idle. |
| "Remember me": still signed in within 30 days after closing the browser | Step 4; the remember-me login is set only when the box is ticked. |
| 2 minutes before the time-out: accessible dialog with "Stay signed in" and "Sign out" | Time-out warning dialog; 4.20. Test: `#timeout-dialog` with `data-timeout-seconds="1800"` and `data-warning-seconds="120"`; absent for "Remember me" and signed-out users. |
| "Stay signed in" or Escape: timer reset, stay on the page, nothing lost | Dialog step 2; keep-alive `204`. |
| "Sign out" in the dialog: same as the header | Dialog sign-out form posts to `/sign-out`. |
| No response: replaced by sign-in with the message; Back doesn't return | Dialog step 5; timed-out address; `location.replace`; States "Timed out". |
| "Stay signed in" fails: sign-in page with the message | Dialog step 3; keep-alive `401`. |
| Timer ran out but still signed in via another tab: straight back, no message | "Already signed in, with `timedOut=true`". Test: GET `/sign-in?timedOut=true&returnTo=/properties/123` with an active session gives `302` to `/properties/123`, with no banner. |
| Session ran out, then form submit or page needing sign-in: sign-in with the message; afterwards back to that page with an empty form | "After a session has run out on the server", including expired CSRF and HTMX. |
| Signed in (including straight after #1): header shows "Sign out", not "Sign in" or "Create account" | Signed-in header. Test: those links are absent from the HTML. |
| "Sign out": session and remember-me end, `/properties`, signed-out header | Sign out. |
| Visitor opens the Property Manager area: access denied | Access-denied page, `403`. |

---

## Open questions

1. **"Remember me" for Property Managers.** The hint says "Stay signed in on this device for 30 days", but #3 ignores "Remember me" for Property Manager accounts on this shared page. Should the hint also say it doesn't apply to Property Manager accounts, or stay as it is?
2. **Message after signing out.** The issue doesn't ask for one, so this design shows none: the user lands on `/properties` and sees the signed-out header. Is that acceptable, or do you want a banner such as "You have signed out"?
