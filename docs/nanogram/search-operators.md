# Search operators (in-chat search)

The grammar, date rules, type table and test vectors live in the shared spec: `docs/specs/search-operators.md` in the iOS
worktree (ios-integration). iOS, Mac and Android implement it; `NanogramSearchOperatorsTest` runs the spec's vectors.

## Android mapping

- Parsed in `MediaDataController.searchMessagesInChat` on a fresh (non-paging) search. The resolved sender, dates and type are kept
  for paging (arrow keys and "load more" reuse `lastSearchUser`/`lastSearchChat` and the stored dates and type).
- `from:` resolves `me` to the signed-in account, otherwise a user or chat the app already knows; if it cannot be resolved the
  token stays in the text.
- `after:` gives `min_date`, `before:` gives `max_date` (exclusive), `date:` gives both; the tighter bound wins.
- `type:` maps to `messages.search` filters: photo (Photos), video (Video), file (Document), link (Url), voice (Voice),
  music (Music), gif (Gif), round (RoundVideo), poll (Poll), pinned (Pinned). `contact`, `geo` and `mention` are not offered yet and stay plain text.
- Saved-messages tag search (a reaction) is never parsed. Not applied to global search or the search tab. Secret chats search locally.
- Setting: Settings > Nanogram features > Search operators, default ON.
