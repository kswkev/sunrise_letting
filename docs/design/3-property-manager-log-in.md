# #3 Property Manager log in: design spec

Issue: [#3 Property Manager log in](https://github.com/kswkev/sunrise_letting/issues/3)
Depends on: #2 (`docs/design/2-visitor-log-in.md`) for the shared sign-in page, the session, the time-out warning, sign out and the access-denied page. Password rules come from #1 (`docs/design/1-visitor-sign-up.md`).
Design system: `docs/design/design-system.md`, especially 3.6 (Property Manager area, Variant A) and 4.19 (band).

Research: no new research for this spec. It reuses the patterns from the #1 and #2 specs (GOV.UK "Passwords" pattern; WCAG 2.2.1). Seeding Property Manager accounts has no user interface.

---

## User flow

### First sign-in (temporary password)
1. The Property Manager opens "Sign in" in the header, which goes to the shared `/sign-in` page (#2). There is no separate Property Manager sign-in.
2. They enter their email and temporary password. "Remember me" is shown, as for everyone, but is **ignored**: no remember-me login is set for a Property Manager account.
3. The server starts a session with a 2-hour idle time-out and sends a `303` to `/manage/change-password`, whatever `returnTo` says.
4. They see "Change your password" inside the Property Manager area (header items and band). They enter a new password twice. They can use "Show" and "Hide".
5. They submit (full-page POST, no HTMX). Identity checks the rules and that the new password isn't the temporary one, then changes it. The session is updated so it no longer counts as temporary.
6. `303` to `/manage`, the Property Manager area home.

### Later sign-ins (permanent password)
1 to 2 as above. Then `303` to `/manage`, whatever `returnTo` says.

### Alternative and error paths
- **Wrong details:** the same generic error as Visitors on the shared page (#2).
- **Temporary password, any other `/manage` URL:** `302` to `/manage/change-password`. Public pages (`/`, `/properties`, ...) open as normal.
- **Permanent password, `/manage/change-password`:** `302` to `/manage`. Changing a password at other times is out of scope.
- **Change password rejected** (rules broken, same as the temporary password, or no match): the page is shown again with errors, both fields empty, and the password unchanged.
- **Identity unavailable on change:** the page is shown again with status `503` and a form-level error. The password is unchanged.
- **Not signed in, any `/manage` URL:** `302` to `/sign-in?returnTo=<path>` (no inactivity message). After signing in, the Property Manager goes to `/manage` or `/manage/change-password` anyway.
- **Signed in as a Visitor, any `/manage` URL:** `403` access-denied page (#2 spec).
- **Already signed in as a Property Manager, opening `/sign-in` or `/create-account`:** `302` to `/manage/change-password` (temporary) or `/manage` (3.5).
- **Session time-out:** as in #2, with `data-timeout-seconds="7200"`. The dialog opens after 1 hour 58 minutes. After a time-out, `/manage` pages redirect to `/sign-in?timedOut=true&returnTo=...`. After signing in again, the Property Manager goes to `/manage` (or `/manage/change-password`). Anything typed is not kept.
- **"Remember me" ticked:** ignored. Closing the browser ends the session.
- **Sign out:** the header (or dialog) "Sign out", as in #2. It lands on `/properties` with the signed-out header, and `/manage` then redirects to sign-in.

---

## Pages and fragments

All `/manage` pages use the shared skeleton (3.1) with the **Property Manager header** and the **band** (4.19) inside `<header>`. The time-out dialog (4.20) is on every page for a Property Manager session, public pages included, with `data-timeout-seconds="7200"`.

### Property Manager header (every page, signed in as a Property Manager)
Items in order (3.1): "Properties to let" (`/properties`), "Property Manager area" (`/manage`), "Sign out" (4.18, `POST /sign-out`).
- "Property Manager area" has `aria-current="page"` on every `/manage` page; "Properties to let" has it on `/properties` pages.
- "Sign in" and "Create account" are not rendered.
- The band "Property Manager area" appears on `/manage` pages only.

### Page: Property Manager area home

| | |
|---|---|
| URL | `GET /manage` |
| Purpose | Landing page of the Property Manager area. A placeholder in #3; #8 adds the property list. |
| Who | Property Managers with a permanent password. Temporary password: redirected to change password. Visitor: `403`. Signed out: redirected to sign-in. |
| Content | Only the `<h1>` "Property Manager area". Nothing else in `<main>`. |

Mobile:
```
+------------------------------+
| Sunrise Letting              |
| Properties to let            |
| Property Manager area        |  aria-current="page"
| Sign out                     |  POST form button
|##############################|
| Property Manager area        |  band (.area-band)
|##############################|
| Property Manager area  (h1)  |
|                              |
+------------------------------+
| © Sunrise Letting            |
+------------------------------+
```

Wide:
```
+----------------------------------------------------------------------+
| Sunrise Letting      Properties to let  Property Manager area  Sign out
|######################################################################|
| Property Manager area                                         (band) |
|######################################################################|
|  Property Manager area   (h1)                                        |
|                                                                      |
+----------------------------------------------------------------------+
| © Sunrise Letting                                                    |
+----------------------------------------------------------------------+
```

### Page: Change your password

| | |
|---|---|
| URL | `GET /manage/change-password`, `POST /manage/change-password` |
| Purpose | Replace the temporary password at first sign-in |
| Who | Property Managers with a temporary password. Permanent password: `302` to `/manage`. Visitor: `403`. Signed out: redirected to sign-in. |
| Layout | Property Manager header and band, account page pattern (3.5), form column at most 40em |
| Scripts | HTMX (site default), password field script (4.17), time-out script (4.20) |

Form: `<form id="change-password-form" method="post" action="/manage/change-password" novalidate>` with `_csrf`. No `returnTo`.

Source order in `<main>`: error summary (after a failed submit), `<h1>`, intro, form.

Mobile:
```
+------------------------------+
| header (Property Manager)    |
|##############################|
| Property Manager area (band) |
|##############################|
| +--------------------------+ |
| | There is a problem       | |  only after a failed submit
| +--------------------------+ |
| Change your password   (h1)  |
| You're signed in with a      |
| temporary password. Choose a |
| new password to continue.    |
|                              |
| New password                 |
| Must be 8 to 128 chars ...   |
| [                ] [Show]    |
| Confirm new password         |
| [                ] [Show]    |
|                              |
| [ Change password ]          |
+------------------------------+
| © Sunrise Letting            |
+------------------------------+
```

Wide:
```
+----------------------------------------------------------------------+
| Sunrise Letting      Properties to let  Property Manager area  Sign out
|######################################################################|
| Property Manager area                                         (band) |
|######################################################################|
|  Change your password   (h1)                                         |
|  You're signed in with a temporary password. Choose a new password   |
|  to continue.                                                        |
|  New password                                                        |
|  Must be 8 to 128 characters and include an upper-case letter, a     |
|  lower-case letter, a number and a symbol. It must be different from |
|  your temporary password.                                            |
|  [                              ] [ Show ]                           |
|  Confirm new password                                                |
|  [                              ] [ Show ]                           |
|  [ Change password ]                                                 |
+----------------------------------------------------------------------+
```

Fields (password field component, 4.17, without `hx-` attributes; no inline validation, 5.4):

| Label | Hint | id / name | Group / error slot | Attributes |
|---|---|---|---|---|
| New password | Must be 8 to 128 characters and include an upper-case letter, a lower-case letter, a number and a symbol. It must be different from your temporary password. | `new-password` / `newPassword` | `#field-new-password` / `#new-password-error-slot` | `autocomplete="new-password"` |
| Confirm new password | none | `confirm-new-password` / `confirmNewPassword` | `#field-confirm-new-password` / `#confirm-new-password-error-slot` | `autocomplete="new-password"` |

- The current (temporary) password isn't asked for again: the user has just entered it to sign in (WCAG 3.3.7).
- Toggle names: "Show" with visually hidden " new password", and " confirm new password".
- Both values are always rendered empty.
- Button: `<button type="submit" class="button">Change password</button>`.

### Fragments
None. Every request in #3 is a full page or a redirect (5.5).

---

## States

| State | What is shown |
|---|---|
| Sign in, temporary password | `303` to `/manage/change-password` |
| Sign in, permanent password | `303` to `/manage` (any `returnTo` ignored) |
| Sign in, wrong details | #2's generic error: "The email address or password is incorrect." |
| Change password, empty | Form with both fields empty, no summary |
| Change password, validation errors | Title "Error: Change your password - Sunrise Letting". Error summary with links to `#new-password` and/or `#confirm-new-password`. Field errors. Both fields empty. Status `200`. |
| Change password, server error | Title "Error: Change your password - Sunrise Letting". Form-level error summary (4.13): "There is a problem", paragraph "Sorry, we couldn't change your password. Try again later." Status `503`. |
| Change password, success | `303` to `/manage`. No banner (open question 1). |
| Loading | No indicator (full-page submit). |
| `/manage` home | Band and `<h1>` "Property Manager area". |
| Empty (`/manage` home) | The placeholder home has no content beyond the heading, so it has no empty state in #3. |
| Visitor on `/manage...` | `403` access-denied page (#2). |
| Not found | A `/manage/...` URL that doesn't exist, for a Property Manager: the site's normal 404 handling, with no design specific to #3. Access rules apply first, so a signed-out user is still redirected to sign-in. |

---

## Content

`/manage`:
- `<title>`: "Property Manager area - Sunrise Letting"
- Band: "Property Manager area"
- `<h1>`: "Property Manager area"

Change your password:
- `<title>`: "Change your password - Sunrise Letting" ("Error: " in front when errors are shown)
- `<h1>`: "Change your password"
- Intro: "You're signed in with a temporary password. Choose a new password to continue."
- Labels and hints: see the Fields table
- Button: "Change password"

Error messages (same text in the field and the summary; only the first that applies per field):

| Field | Problem | Message |
|---|---|---|
| New password | empty | Enter a new password |
| New password | under 8 characters | New password must be at least 8 characters |
| New password | over 128 characters | New password must be 128 characters or fewer |
| New password | missing a character type | New password must include an upper-case letter, a lower-case letter, a number and a symbol |
| New password | same as the temporary password | New password must be different from your temporary password |
| Confirm new password | empty | Confirm your new password |
| Confirm new password | doesn't match | Passwords must match |

Form-level: "Sorry, we couldn't change your password. Try again later."

Header: "Properties to let", "Property Manager area", "Sign out".

Time-out dialog: the same copy as #2. It says "2 minutes", which is correct for both time-outs.

---

## Accessibility

Beyond the design-system defaults and #2:
- **Band** is plain text inside `<header>`, not a heading or a link (4.19). Each `/manage` page has its own `<h1>`, even where it repeats the band text.
- **`aria-current="page"`** on "Property Manager area" on `/manage` pages, so screen-reader users know where they are, along with the visible band.
- **Redirect to change password:** because it is a full page with its own `<h1>` and intro explaining why, users who expected `/manage` understand what happened.
- **Error summary** focus and field links as in 4.13; both password fields are cleared after a failed submit, and the summary says which to correct.
- **Toggle names** are unique: "Show new password", "Show confirm new password".
- **Time-out:** 2 hours with the 2-minute warning (WCAG 2.2.1).

---

## Acceptance criteria mapping

| Acceptance criterion | Where the design meets it |
|---|---|
| Seeded accounts exist with the Property Manager role only, hashed, no duplicates | No UI; identity service. |
| Temporary password: signing in goes to "Change your password" | First sign-in step 3; States. |
| Temporary password: other `/manage` pages redirect to change password | Alternative paths. Test: GET `/manage` gives `302` to `/manage/change-password`. |
| Valid new password: changed, taken to `/manage` | First sign-in steps 5 and 6. |
| Rules broken, same as temporary, or no match: not changed, see which field | States "validation errors"; Error messages table. |
| Permanent password: `/manage` even with a different return address | "Later sign-ins". Test: POST `/sign-in` with `returnTo=/properties/1` gives `303` to `/manage`. |
| Incorrect details: same generic error as Visitors | #2's form-level message. |
| Not signed in: `/manage` redirects to sign-in | Alternative paths. Test: `302` to `/sign-in?returnTo=%2Fmanage`, with no `timedOut`. |
| Visitor: access denied on `/manage` | `403` access-denied page (#2). |
| `/manage` home shows the band and heading | Page "Property Manager area home". Test: `.area-band` with the text "Property Manager area" and `<h1>` "Property Manager area". |
| Header shows "Properties to let", "Property Manager area", "Sign out" on any page | Property Manager header. Test on `/manage` and `/properties`. |
| "Remember me" ticked: no longer signed in after reopening the browser | Step 2: no remember-me login set. Test: the sign-in response sets no remember-me cookie for a Property Manager. |
| Inactive 1 hour 58 minutes: warning appears | Dialog with `data-timeout-seconds="7200"` and `data-warning-seconds="120"`. |
| Inactive 2 hours: signed out, `/manage` sends to sign-in | Session time-out; #2's redirect. |
| Sign out: session ends, `/manage` no longer accessible | Sign out as in #2. |

---

## Open questions

1. **Confirmation after changing the password.** The issue says the Property Manager is taken to `/manage`, and doesn't mention a message. This design shows none. Should `/manage` show a success banner such as "Your password has been changed" the first time after the change?
