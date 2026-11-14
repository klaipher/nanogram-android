# Link hygiene

Settings > Nanogram features > Links. Both switches default OFF.

- **Remove tracking from links**: tracking parameters are removed from `http`/`https` links when the user opens them (`Browser.openUrl`) or copies them (message link long-press menu, photo viewer link menu, custom tab copy, embed sheet copy). Message text is never rewritten and outgoing messages are never touched.
- **Ask before opening Telegram links**: `t.me`, `telegram.me`, `telegram.dog`, `telesco.pe` and `tg:` links show an Open / Cancel dialog first (`Browser.openUrl`).
- Never changed: Telegram links and every non-http scheme (their parameters carry meaning, for example `start=`), the fragment, parameter order, parameters not on the list.

## Maintained list (`NanogramLinkHygiene`)

Exact names: fbclid, gclid, gclsrc, dclid, gbraid, wbraid, msclkid, twclid, ttclid, li_fat_id, igshid, igsh, mc_eid, mc_cid, _hsenc, _hsmi,
mkt_tok, vero_id, oly_enc_id, oly_anon_id, yclid, ysclid, _openstat, rb_clickid, wickedid, _ga, _gl, s_cid, ref_src, ref_url,
fb_action_ids, fb_action_types, fb_source, fb_ref, srsltid.
Prefixes: utm_, hsa_, pk_, piwik_, mtm_, matomo_.
`si` only on youtube.com, youtu.be, music.youtube.com, open.spotify.com, spotify.link, twitter.com, x.com (it is an ordinary parameter elsewhere).
Names compare case-insensitively. Add new entries to the list and to `NanogramLinkHygieneTest`.
