# Search operators (in-chat search)

Grammar shared by Android, iOS and Mac. The iOS spec file (`docs/specs/search-operators.md` in the iOS worktree) did not exist when
this was written; Android implements the grammar below and the iOS and Mac parsers must produce the same results for the same
cases. The Android test cases (`NanogramSearchOperatorsTest`) are the conformance list.

## Tokens

- The query is split on whitespace. Double quotes keep a run together (`from:"Ann Lee"`).
- A token is an operator when it is `key:value`, the key (case-insensitive) is one of the five below and the value is valid.
  Anything else (unknown key, empty value, bad date, unknown type, `10:30`, `https://x`) stays plain text, in its original order.
- If an operator is given twice the last one wins.
- The plain text left after removing operators is joined with single spaces and sent as `q`. It may be empty.

## Operators

| Operator | Value | Maps to |
|---|---|---|
| `from:` | username with or without `@` (quoted allowed) | `from_id`. Resolved from users and chats the app already knows; if it cannot be resolved the token stays in the text |
| `after:` | date | `min_date` = start of that day, month or year (inclusive) |
| `before:` | date | `max_date` = one second before the start of that day, month or year (exclusive) |
| `date:` | date | `min_date` = start, `max_date` = last second of that day, month or year |
| `type:` | photo, video, file (document, doc), link (url), voice, music (audio), gif, round | `filter`: Photos, Video, Document, Url, Voice, Music, Gif, RoundVideo |

Dates: `YYYY-MM-DD`, `YYYY-MM`, `YYYY` (digits exactly 4-2-2, year 1970 to 2100, real calendar dates), `today`, `yesterday`.
Interpreted in the device time zone. Plural and alias spellings are accepted for `type:` (photos, videos, files, links, gifs, image, roundvideo).

## Behaviour

- Setting: Settings > Nanogram features > Search operators, default ON. When off the query is sent as typed.
- Android: parsed in `MediaDataController.searchMessagesInChat` on a fresh (non-paging) search, so paging and the merge-dialog request keep the same filters. Saved-messages tag search (a reaction) is never parsed.
- Not applied to global search or the search tab.
