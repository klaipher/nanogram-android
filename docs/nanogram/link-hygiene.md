# Link hygiene

Settings > Nanogram features > Links. Both switches default OFF.

- **Remove tracking from links**: tracking parameters are removed from `http`/`https` links when the user opens them (`Browser.openUrl`) or copies them (message link long-press menu, photo viewer link menu, custom tab copy, embed sheet copy). Message text is never rewritten and outgoing messages are never touched.
- **Ask before opening Telegram links**: `t.me`, `telegram.me`, `telegram.dog`, `telesco.pe` and `tg:` links show an Open / Cancel dialog first (`Browser.openUrl`).
- Never changed: Telegram links and every non-http scheme (their parameters carry meaning, for example `start=`), the fragment, parameter order, parameters not on the list.

## Shared list

The list is the shared data file `docs/specs/link-tracking-params.txt` in the iOS worktree (ios-integration), mirrored in
`NanogramLinkHygiene` (exact names, `prefix*` entries and `host|name` entries; a host entry also covers its subdomains).
Names compare case-insensitively. Change the shared file and every platform's mirror together, and add the case to `NanogramLinkHygieneTest`.
