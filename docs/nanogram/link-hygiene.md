# Link hygiene

Settings > Nanogram features > Links. Both switches default OFF.

- **Remove tracking from links**: tracking parameters are removed from `http`/`https` links when the user opens them (`Browser.openUrl`) or copies them (message link long-press menu, photo viewer link menu, custom tab copy, embed sheet copy). Message text is never rewritten and outgoing messages are never touched.
- **Ask before opening Telegram links**: `t.me`, `telegram.me`, `telegram.dog` (also with `www.`) and `tg://` links show an Open / Cancel dialog first (`Browser.openUrl`). `telesco.pe` and a bare `tg:` are not Telegram links (same as iOS and the Mac).
- Never changed: every non-http(s) scheme (`tg://` parameters carry meaning), the fragment, parameter order, empty query items, parameters not on the list. Telegram links are cleaned like any other http(s) link; the list has no `start=`-style names.

## Shared list

One list for iOS, Mac and Android: `data/link-tracking-params.txt` in nanogram-deps (the `shared` submodule), with the test
vectors in `data/link-hygiene-vectors.json` (strip and Telegram-link cases). `NanogramLinkHygiene.RULES` holds the same lines
(exact names, `prefix*` entries and `host|name` entries; a host entry also covers its subdomains; names compare
case-insensitively). `NanogramLinkHygieneTest` reads both files and fails when the code drifts from them. Change the shared
file first, then `RULES`.
