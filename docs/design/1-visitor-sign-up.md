# #1 Visitor sign up: design spec

Issue: [#1 Visitor sign up](https://github.com/kswkev/sunrise_letting/issues/1)
Design system: `docs/design/design-system.md` (adopted, brand-led). This spec reuses its components and only adds what is specific to #1. Section numbers in brackets, such as (4.17), refer to the design system.

Research for this spec (patterns only, nothing copied):
- GOV.UK "Passwords" pattern (fetched): explain password rules in a hint, allow paste, never keep an entered password after a failed attempt.
- gov.cy design system text input guidance (found by search; the GOV.UK text input page itself couldn't be fetched): use `novalidate`, don't use `required` or `maxlength` because the browser's messages and silent truncation bypass the service's own errors.
- WCAG 2.2 Understanding 2.2.1 (fetched), for the session time-out, which #2 builds on.
- `design-system.service.gov.uk/patterns/create-a-username/` and the OpenRent sign-in page could not be fetched (DNS failure and HTTP 404).

---

## User flow

Entry points:
- "Create account" in the signed-out header (3.1), which links to `/create-account` with no return address.
- The "Create an account" link on the sign-in page (#2), which passes on that page's return address. This is how a Visitor sent to sign in from a property's "Request a viewing" (#6) can sign up and still get back to it.
- A direct link or bookmark to `/create-account`, with or without `?returnTo=...`.

Main path:
1. The Visitor opens `/create-account`. The server renders the full page, with the return address (if any) in a hidden field.
2. They fill in the fields. When they leave a field that has a value, it is checked inline (HTMX). If it's wrong, its error appears under the label. If it's right, any earlier error is removed. If it's empty, nothing is shown.
3. They can press "Show" on either password field to check what they typed, and "Hide" to mask it again.
4. They press "Create account". The form is a normal full-page POST.
5. The server checks every field again. Identity creates the account with the Visitor role only. The web service starts a signed-in session (30 minutes idle).
6. The server responds `303 See Other` to:
   - the return address, if it is a path on this site (see "Return address" below), with no message; or
   - `/properties`, which shows the success banner "Your account has been created".

Alternative and error paths:
- **Already signed in:** a GET (or POST) to `/create-account` with a signed-in session doesn't show the form. It redirects (`302`) to `/properties` for a Visitor, or to `/manage/change-password` or `/manage` for a Property Manager (3.5). Any return address is ignored.
- **Invalid or missing fields on submit:** the page is shown again with status `200`. It has an error summary (4.13), an error on each field that needs correcting, the entered values kept and both passwords empty. No account is created and nobody is signed in.
- **Email already registered** (compared trimmed and ignoring case): the page is shown again with the form-level error (see States). No field is marked as wrong, values are kept except the passwords, no account is created and nobody is signed in.
- **Unsafe return address** (not a path on this site): ignored. The Visitor goes to `/properties` with the success banner, as if there were none.
- **Identity service unavailable or failing on submit:** the page is shown again with status `503` and a form-level error. Values are kept except the passwords.
- **Inline check can't be done** (identity unavailable): the endpoint returns `204 No Content` and nothing on the page changes. The submit still checks everything.
- **No JavaScript:** no inline checks and no show/hide toggle. Everything else works the same way through the full-page submit.

### Return address (decided in this spec, used by #1 and #2)

- It is carried in a query parameter `returnTo` on the GET (for example `/create-account?returnTo=%2Fproperties%2F123%2Fviewing`) and as a hidden field `returnTo` in the form, so it survives a failed submit.
- It is followed only if it is a path on this site: it starts with a single `/`, doesn't start with `//` or `/\`, contains no backslash, no scheme and no control characters. Anything else is treated as if there were no return address.
- The header "Create account" and "Sign in" links don't carry a return address. Open question 1 asks whether they should.
- The "Already have an account? Sign in" link on this page and the "sign in" link in the duplicate-email message both go to `/sign-in`, carrying the same `returnTo` if there is a valid one.

---

## Pages and fragments

### Page: Create an account

| | |
|---|---|
| URL | `GET /create-account` (optional `?returnTo=<path>`), `POST /create-account` |
| Purpose | Let a Visitor create an account and be signed in |
| Who | Signed-out users only. Signed-in users are redirected (see User flow). |
| Layout | Shared skeleton (3.1), signed-out header, no band. Account page pattern (3.5). |
| Scripts | HTMX; password field script (4.17, 5.7). No time-out script (signed-out). |

The form is `<form id="create-account-form" method="post" action="/create-account" novalidate>`. It holds the CSRF hidden field `_csrf`, the hidden `returnTo` (only when valid), the fields below in this order, then the submit button.

Source order inside `<main>`: error summary (only after a failed submit), `<h1>`, intro paragraph, "Already have an account?" paragraph, form.

Mobile (base):
```
+------------------------------+
| Sunrise Letting              |
| Properties to let  Sign in   |
| Create account               |
+------------------------------+
| +--------------------------+ |
| | There is a problem       | |  error summary, only after a failed submit
| | - Enter your first name  | |
| +--------------------------+ |
| Create an account      (h1)  |
| You need an account to       |
| request a viewing or apply   |
| for a property.              |
| Already have an account?     |
| Sign in                      |
|                              |
| Title                        |
| [ Select a title        v ]  |
| Other title                  |
| Only if you chose Other      |
| [                        ]   |
| First name                   |
| [                        ]   |
| Surname                      |
| [                        ]   |
| Email address                |
| [                        ]   |
| Phone number                 |
| A UK mobile or landline...   |
| [                        ]   |
| Password                     |
| Must be 8 to 128 chars...    |
| [                ] [Show]    |
| Confirm password             |
| [                ] [Show]    |
|                              |
| [ Create account ]           |
+------------------------------+
| © Sunrise Letting            |
+------------------------------+
```

Wide (40em and up; nothing more changes at 64em):
```
+----------------------------------------------------------------------+
| Sunrise Letting              Properties to let  Sign in  Create account
+----------------------------------------------------------------------+
|  Create an account   (h1)                                            |
|  You need an account to request a viewing or apply for a property.   |
|  Already have an account? Sign in                                    |
|                                                                      |
|  Title                                                               |
|  [ Select a title   v ]                                              |
|  Other title                                                         |
|  Only if you chose Other                                             |
|  [                    ]                                              |
|  First name                                                          |
|  [                                    ]                              |
|  Surname                                                             |
|  [                                    ]                              |
|  Email address                                                       |
|  [                                    ]                              |
|  Phone number                                                        |
|  A UK mobile or landline number, for example 07700 900123            |
|  [                    ]                                              |
|  Password                                                            |
|  Must be 8 to 128 characters and include ...                         |
|  [                              ] [ Show ]                           |
|  Confirm password                                                    |
|  [                              ] [ Show ]                           |
|                                                                      |
|  [ Create account ]                                                  |
+----------------------------------------------------------------------+
| © Sunrise Letting                                                    |
+----------------------------------------------------------------------+
```
- The form column is at most 40em wide and left-aligned in the content container at every width (3.5).
- At 40em and up, Title and Other title inputs are about 12em wide and Phone number about 20em, as a hint to the expected length. Name and email inputs fill the column. Below 40em every input is full width.
- The password inputs and their "Show" buttons sit on one row at every width. At 320px the input shrinks and the button keeps its 44px height. If the text is enlarged so they no longer fit, the button wraps below the input (flex-wrap), with no horizontal scrolling.

### Fields

All fields use the form field component (4.10), or the password field component (4.17) for the two passwords. No `required` and no `maxlength` attributes: the form has `novalidate` and the server reports every error, including length, in its own messages (6.6).

| # | Label | Hint | id / name | Control | Attributes | Group id (swap target) |
|---|---|---|---|---|---|---|
| 1 | Title | none | `title` / `title` | `<select>` with options: "Select a title" (value `""`, selected by default), Mr, Mrs, Miss, Ms, Mx, Dr, Other | `autocomplete="honorific-prefix"` | `#field-title` |
| 2 | Other title | Only if you chose Other | `title-other` / `titleOther` | text input | `autocomplete="off"` `spellcheck="false"` | `#field-title-other` |
| 3 | First name | none | `first-name` / `firstName` | text input | `autocomplete="given-name"` `spellcheck="false"` | `#field-first-name` |
| 4 | Surname | none | `surname` / `surname` | text input | `autocomplete="family-name"` `spellcheck="false"` | `#field-surname` |
| 5 | Email address | none | `email` / `email` | `type="email"` | `autocomplete="email"` `spellcheck="false"` `autocapitalize="none"` | `#field-email` |
| 6 | Phone number | A UK mobile or landline number, for example 07700 900123 or 0113 496 0000 | `phone` / `phone` | `type="tel"` | `autocomplete="tel"` | `#field-phone` |
| 7 | Password | Must be 8 to 128 characters and include an upper-case letter, a lower-case letter, a number and a symbol | `password` / `password` | password field (4.17) | `autocomplete="new-password"` | `#field-password` (never swapped); error slot `#password-error-slot` |
| 8 | Confirm password | none | `confirm-password` / `confirmPassword` | password field (4.17) | `autocomplete="new-password"` | `#field-confirm-password` (never swapped); error slot `#confirm-password-error-slot` |

Notes:
- **"Other" title** is always shown as its own field after Title, because revealing it only when "Other" is chosen would need JavaScript. Its label and hint make clear it is only needed for "Other". If Title isn't "Other", whatever is typed in Other title is ignored and not stored, and no error is shown for it.
- Title starts on "Select a title" (empty), so no title is chosen for the user.
- After a failed submit, Title, Other title, First name, Surname, Email address and Phone number are rendered exactly as typed (not trimmed or normalised), so the user recognises them. Both password inputs are always rendered with an empty value.
- Toggle buttons: "Show" with visually hidden " password", and "Show" with visually hidden " confirm password" (4.17).
- Submit: `<button type="submit" class="button">Create account</button>`.

### Fragment: inline validation of one field (not the passwords)

| | |
|---|---|
| Trigger | The input or select loses focus: `hx-trigger="blur"` |
| Request | `hx-post="/create-account/validate/<field>"`, where `<field>` is `title`, `title-other`, `first-name`, `surname`, `email` or `phone`. `hx-params="<name>,_csrf"` (for example `hx-params="firstName,_csrf"`) so only that field and the CSRF token are sent. Without it, HTMX would send every value in the enclosing form, including the passwords. |
| Target / swap | `hx-target="#field-<field>"`, `hx-swap="outerHTML"` |
| Response | `200` with the complete form group for that field and nothing else (no layout): label, hint, error message if any, and the control with the value that was sent and the same `hx-` attributes. |

What the fragment checks:
- Value empty after trimming: the group in its neutral state, with no error, no `form-group--error` and no `aria-invalid` (5.4).
- Title: only that the value is one of the listed options.
- Other title: only the length (20 characters or fewer). Whether it is needed (Title is "Other") is checked on submit only.
- First name, Surname: length.
- Email address: format and length only. It never checks or reveals whether an account exists.
- Phone number: that it is a valid UK mobile or landline number, with or without +44.
- Invalid: the group with `form-group--error`, the error message (with the visually hidden "Error:" prefix) and `aria-invalid="true"` on the control; `aria-describedby` includes `<id>-error`.
- Valid: the group in its neutral state.
- The check can't be done (identity unavailable): `204 No Content`. HTMX doesn't swap on 204, so nothing changes.
- Request without the `HX-Request` header: `303` redirect to `/create-account`. These endpoints serve HTMX only.

### Fragment: inline validation of Password and Confirm password

| | |
|---|---|
| Trigger | `hx-trigger="blur"` on the input |
| Request | Password: `hx-post="/create-account/validate/password"`, `hx-params="password,_csrf"`. Confirm password: `hx-post="/create-account/validate/confirm-password"`, `hx-params="password,confirmPassword,_csrf"`. |
| Target / swap | `hx-target="#password-error-slot"` or `#confirm-password-error-slot`, `hx-swap="innerHTML"` |
| Response | `200` with only `<p class="error-message" id="<id>-error">...</p>`, or an empty body when the value is valid or empty. **It never contains any password.** |

- The input is never re-rendered, so what was typed and the show/hide state are kept.
- The password field script updates `form-group--error` and `aria-invalid` after the swap (4.17).
- Confirm password compares with Password as sent. If Password changes later, Confirm password's message isn't updated until Confirm password loses focus again or the form is submitted.
- 204 and missing `HX-Request` behave as for the other fields.
- The server must not log these request bodies.

### Page used after success: `/properties` with the success banner

- `/properties` is the search page from #4. After sign-up with no valid return address, it shows the success notification banner (4.14) at the top of `<main>`, above the `<h1>`: `notification-banner--success`, `role="status"`, `tabindex="-1"`, `autofocus`.
- The banner is shown once, on the first `GET /properties` after the redirect (for example a flash attribute), and not on later visits or reloads.
- If #4 isn't built yet, `/properties` needs at least a page in the shared skeleton with the `<h1>` "Properties to let" so the banner can be shown (open question 4).

No HTMX is used for the submit (5.5).

---

## States

| State | What is shown |
|---|---|
| Empty (first load) | Form with all fields empty, Title on "Select a title", no error summary, both toggles rendered with `hidden` (shown by the script). |
| Loading | No indicator. Inline checks run after the user has moved on; submit is a normal page load. |
| Inline error on one field | That field's group in the error state with its message (see Content). Nothing else on the page changes. No error summary. |
| Inline success | That field's group back in the neutral state. There's no "valid" tick. |
| Empty field left on blur | No change; no error until submit. |
| Validation errors on submit | Page title "Error: Create an account - Sunrise Letting". Error summary "There is a problem" with one link per wrong field, in field order, each going to the field's input id (`#title`, `#title-other`, `#first-name`, `#surname`, `#email`, `#phone`, `#password`, `#confirm-password`) and using the field's message text. Each wrong field is in its error state. Values kept, passwords empty. Status `200`. |
| Email already registered | Page title "Error: Create an account - Sunrise Letting". Form-level error summary (4.13): heading "There is a problem", paragraph "We couldn't create an account with these details. If you already have an account, sign in." where "sign in" links to `/sign-in` (with `returnTo` if valid). No field marked as wrong. Values kept, passwords empty. Status `200`. If other fields are also wrong, only the field errors are shown, because the duplicate check runs only when every field is valid. |
| Server error on submit | Page title "Error: Create an account - Sunrise Letting". Form-level error summary: "There is a problem", paragraph "Sorry, we couldn't create your account. Try again later." Values kept, passwords empty. Status `503`. |
| Server error inline | No change (`204`). |
| Not found | Not applicable. |
| Already signed in | No page; `302` redirect (see User flow). |
| Success with return address | `303` to the return address. No banner. |
| Success without return address | `303` to `/properties`, which shows the success banner "Your account has been created". |

---

## Content

Page:
- `<title>`: "Create an account - Sunrise Letting" (with "Error: " in front when errors are shown)
- `<h1>`: "Create an account"
- Intro: "You need an account to request a viewing or apply for a property."
- "Already have an account? [Sign in]"
- Button: "Create account"

Labels and hints: see the Fields table.

Show/hide (4.17): "Show" / "Hide", with visually hidden " password" or " confirm password". Status: "Your password is visible" / "Your password is hidden".

Error messages (same text in the field and in the error summary):

| Field | Problem | Message |
|---|---|---|
| Title | none chosen | Select your title |
| Title | value not in the list | Select your title |
| Other title | Title is "Other" and this is empty (submit only) | Enter your title |
| Other title | over 20 characters | Title must be 20 characters or fewer |
| First name | empty | Enter your first name |
| First name | over 100 characters | First name must be 100 characters or fewer |
| Surname | empty | Enter your surname |
| Surname | over 100 characters | Surname must be 100 characters or fewer |
| Email address | empty | Enter your email address |
| Email address | over 254 characters | Email address must be 254 characters or fewer |
| Email address | wrong format | Enter an email address in the correct format, like name@example.com |
| Phone number | empty | Enter your phone number |
| Phone number | not a valid UK number | Enter a UK phone number, like 07700 900123 or +44 7700 900123 |
| Password | empty | Enter a password |
| Password | under 8 characters | Password must be at least 8 characters |
| Password | over 128 characters | Password must be 128 characters or fewer |
| Password | missing a character type | Password must include an upper-case letter, a lower-case letter, a number and a symbol |
| Confirm password | empty | Confirm your password |
| Confirm password | doesn't match | Passwords must match |

Where a field breaks more than one rule, only the first message in the table's order for that field is shown (for example, length before format).

Form-level messages:
- Duplicate email: "We couldn't create an account with these details. If you already have an account, sign in."
- Server error: "Sorry, we couldn't create your account. Try again later."

Success banner on `/properties`: heading "Your account has been created".

---

## Accessibility

Beyond the design-system defaults (4.10, 4.13, 4.17, 5.4, 6.6, 6.7):
- **Focus after an inline swap:** the field group is replaced after focus has left it, so focus stays wherever the user moved it. If the user tabbed back into the field before the response arrived, focus must stay in that input; the swapped input keeps the same `id` (5.4). Developers should check this with the htmx version in the WebJar.
- **No announcements for inline errors** (6.7). The message is read through `aria-describedby` when the user returns to the field. The error summary on submit is the main way errors are announced.
- **Error summary focus:** after a failed submit, focus goes to the error summary (`autofocus`). Its links move focus to the input, and the label is visible above it.
- **Form-level error summary:** the duplicate and server error summaries contain a paragraph, not a list of links (4.13). The "sign in" link inside it is a normal link.
- **Success banner focus:** on `/properties` the banner has `autofocus` and `role="status"`, so the confirmation is announced and is the first thing a keyboard user reaches.
- **Select:** the native `<select>` is keyboard operable. Inline checks run on blur, not on change, so moving through options with the arrow keys doesn't trigger requests.
- **Autocomplete** values as in the Fields table (WCAG 1.3.5). Paste is never blocked (WCAG 3.3.8).
- **Hints** are linked with `aria-describedby` (`phone-hint`, `password-hint`, `title-other-hint`).
- **Target size:** the "Show" buttons are 44px high (4.9). Their accessible names are unique: "Show password" and "Show confirm password".
- **Reflow:** at 320px and 400% zoom the page is one column, and password buttons wrap below their inputs when needed.

---

## Acceptance criteria mapping

| Acceptance criterion | Where the design meets it |
|---|---|
| Valid details: account created, signed in, returned to the page I came from | User flow steps 4 to 6; "Return address". Test: POST with valid fields and `returnTo=/properties/123` gives `303` to `/properties/123`, with a signed-in session. |
| Opened directly: taken to `/properties` with "Your account has been created" | User flow step 6; "Page used after success"; States "Success without return address". |
| Return address not a path on this site: `/properties` instead | "Return address" rules. Test cases: `https://evil.example`, `//evil.example`, `/\evil.example`, `javascript:...`. Each gives `303` to `/properties`. |
| Already signed in as a Visitor: sent to `/properties`, no form | User flow "Already signed in"; States. Test: GET with a Visitor session gives `302` to `/properties`, and the body has no `#create-account-form`. |
| Duplicate email (ignoring case and spaces): no account, not signed in, generic message | States "Email already registered"; Content form-level message. Test: email `" Jo@Example.com "` against an existing `jo@example.com`. |
| Empty or wrong fields on submit: no account, values kept except passwords, see which fields | States "Validation errors on submit"; Error messages table; Fields notes (password `value` empty). |
| Blur on a non-password field: only that field updated; email format only | Fragment "inline validation of one field". Test: response is only `#field-<field>`, with `form-group--error`, the message and `aria-invalid="true"` for a bad value; an email of an existing account in a valid format returns the neutral group. |
| Blur on a password field: message updated, typed value kept, no password in the response | Fragment "inline validation of Password and Confirm password". Test: response body is only `.error-message` or empty, and doesn't contain the submitted password. |
| Empty field on blur: no error until submit | Fragment "What the fragment checks", first bullet; States "Empty field left on blur". |
| Over the maximum length: rejected on submit with a message for that field | Error messages table (length rows); no `maxlength` attribute, so long values reach the server. |
| Password stored only as a strong one-way hash | Not a UI concern. In the web service: password inputs never have a `value`, inline and submit responses never contain the password, and request bodies with passwords are not logged. Hashing is in identity. |
| Visitor role only; can't reach the Property Manager area | Not a UI concern in #1. The access-denied page for Visitors opening `/manage` is defined in the #2 spec. |
| "Show" shows the password, "Hide" masks it again | Password field component (4.17); Content "Show/hide". Test hooks: toggle `hidden` in server HTML, `aria-controls`, script reference. |
| Inactive for 30 minutes: treated as signed out | Session created at step 5 with a 30-minute idle time-out. The signed-in header and time-out warning are #2. |

---

## Open questions

1. **Header links and the return address.** This spec has the header "Create account" and "Sign in" links carry **no** return address, so signing up from the header always ends on `/properties` with "Your account has been created". If they carried the current page instead, users would come back to where they were, but would usually not see the success message (it only shows on `/properties`). Which do you want?
2. **Phone number formatting.** The hint shows numbers with spaces. Should spaces, hyphens and brackets (for example "(0113) 496-0000") be accepted and ignored, or only spaces?
3. **Form token expiry for signed-out users.** If a signed-out Visitor leaves the form open long enough for their anonymous session (and its CSRF token) to expire, the submit would be rejected with a 403. Should the server instead show the form again with the values kept (except passwords) and a message such as "Your form has expired. Check your details and try again."? This is not in #1's acceptance criteria.
4. **`/properties` before #4 exists.** If #1 ships before #4, may the web service add a minimal `/properties` page (heading "Properties to let" only) to show the success banner?
