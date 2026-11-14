# Mercurygram bug-fix and performance port (Android)

Source: Mercurygram Android (github.com/Mercurygram/Mercurygram, GPL-2.0, 103 commits on DrKLO 13.2.2), read commit by
commit. Nanogram is on the same upstream (13.2.2, 7303), so the upstream bugs are the same code. Every ported change is
reimplemented or adapted from the original commit with attribution here and in `NANOGRAM-CHANGES.md`; the Inugram
(MIT) backports inside those commits are named where the commit message says so. Nothing was built: the Android code is
only syntax-parsed (`javac` parse of every changed file) and the JVM tests of the pure classes pass on JDK 21 with a
stub JUnit runner (no real JUnit jar on the machine).

Excluded by owner decision (spec section 9): changes to data the server sees (PFS key ladder, CDN refusal, local-only
drafts, link-preview off, global search off), Premium/ads/sponsored changes, Tor, GMS/Firebase removal and UnifiedPush,
reproducible-build flags that drop LTO/ICF.

Status words: ported, partly ported, already had, not applicable, declined.

## Coverage table

| Mercurygram commit | What | Status | Where / reason |
|---|---|---|---|
| 28fda5a3c | Push latch: one `CountDownLatch` per push, 20 s bounded await, OAUTH_REQUEST branches count down | ported | `PushListenerController.processRemoteMessage`; the static latch was never recreated, so every push after the first dropped its wakelock early on our FCM path |
| 1c9dcb6fd | Ten upstream crashes (nine from Inugram, MIT) | ported, all hunks | `RecyclerView.removeAndRecycleViewAt` (null child / holder), `MessagesController` (three admin lookups returned past a null array), `MessagesStorage` (`folder_id` -1 indexed the badge arrays and wiped folder badges), `ChatUsersActivity` (int-overflow comparator), `EditCoverButton`, `StickerMakerView` (recycled bitmap), `RecyclerListView` (first DiffUtil update reported phantom inserts), `UniversalAdapter` (detached holder), `DialogsActivity` (null community avatar), `PeerStoriesView` (null first name of a deleted user) |
| 0545efe54 | Memory leaks | ported, minus the ad-related hunks | Fixed: `FeedWidgetService`, `MessagesController` cancel of the loading delegate, `MusicBrowserService` (recents query), `BaseFragment` + `ReactionsEffectOverlay.dismissByFragment`, `ChatActivity` comments observer, `Bulletin`, `ChatAttachAlert` and `PhotoViewer` mentions adapters, `ChatAvatarContainer`, `EmojiView` observers, `BlurredBackgroundDrawableViewFactory.unsubscribe` + `PhotoViewer.shutdownPaintView`, `IntroActivity`, `LaunchActivity` null frame layout, `BotWebViewContainer` QR permission delegate, `ImageLoader` byte arrays above 16 MB are not kept per thread, video thumbnail fallback. Left out: `VideoAds` (sponsored messages, declined) and a duplicated `removeObserver` line in `ChatCustomReactionsEditActivity` (no-op) |
| b1274170f | Large-account slowdowns | ported (merged with our code) | `MessagesController`: no admin-list request for non-admins of a community (COMMUNITY_FILTER_INVALID re-fire), read-only unread topics send `readDiscussion`, one TTL delete loop at a time (`gettingNewDeleteTask` set before the post), no history row for a chat we left, "will wait more time" log line removed. `MessagesStorage`: read dialog subtracted from badges once, deleted-message batch commits its transaction, folder badge recount only when a counter changed and throttled (`NanogramRecountThrottle`), dialog reload only when the last message was deleted, `finalMessagesCount` for processed loads. `DialogsActivity`: Mark all as read sends requests only for unread chats (muted chats are still marked read, as upstream). Our own performance patches (`NanogramDbTuning`, `NanogramTranslitCache`) do not touch these paths |
| 793d5745a | Media I/O and decode | partly ported | Ported: parts file `rw` plus one `getFD().sync()` before close, iv and encrypted temp file `rwd` (IO-1 had left them `rws`), two `cacheOutQueue` threads with `allowCoreThreadTimeOut`, per-task header buffers (the shared static buffers would race on two threads), cached stripped thumbnails (evicted in `clearMemory`). Already had: larger bitmap cache (`NanogramImageCacheSize`); key and preload files stay `rws` (small, written once) |
| 81011faf9 | TL legacy `draftMessageEmpty_layer81` / `botInfoEmpty_layer48` read too far | ported | `TLRPC.TL_draftMessageEmpty_layer81`, `TL_bots.TL_botInfoEmpty_layer48`: empty `readParams`. Correctness fix, not a security fix (only old stored rows) |
| ea4c440b6 | tgcalls: null `_codecContext` in livestream `decode()` | ported in the shared fork | tgcalls fork `nanogram-shared-next` commit 4f251c4 (not pushed). Same file is built by iOS and Mac; the gitlink bump in all three apps is for the combined step. Not applied as an Android patch script: tgcalls is a submodule here and one shared source is the owner rule |
| acd1861de | Dice sticker cache restamp and cross-thread read | ported | `MediaDataController`: `UPDATE stickers_dice ... WHERE emoji = ?`, `ConcurrentHashMap` |
| ede78cac5 | Android 16 heads-up listed every unread chat | ported | `NotificationsController.showExtraNotifications`: the newest chat's own notification alerts, the summary alerts only when no child does |
| 231015942 | Opening a chat's Sound screen reset the tone to Default and synced it | ported | `NotificationsSoundActivity` no longer marks the tone changed when it cannot resolve it |
| fd3d55126 | Notification permission prompt back on every launch | ported | `NotificationPermissionDialog` calls `askLater()` after the request |
| 01230d2c1 | Secret chat "delete for everyone" did not reach the peer | ported | `SecretChatHelper.discardEncryption`: flush-history service message first, re-enqueued dialog delete, discard queued behind it on the stage queue. Server relay bug bugs.telegram.org/c/27416. Not verified against a real peer pair |
| 3ac8b2bd1 | Pin limit on folder tabs counted pins twice | ported (Inugram variant, inline) | `NanogramFolderPins.maxPinned` used by both `DialogsActivity` checks, with the chats-per-folder limit (Premium 200) instead of a hardcoded 100 |
| 84e783f3b | ThemeDescription fields renamed by R8 | partly ported, not reproduced | `proguard-rules.pro`: `-keepclassmembernames,allowoptimization` with `<fields>;` for `org.telegram.ui.**` (field names only, unused fields can still be shrunk). The first version used `View+ *;` types, which R8 rejects ("Expected field or method name"; checked with R8 9.2.4). Cannot be reproduced without a release build; the combined build must check it |
| a2d04746f | Live location "until stopped" read as expired, failed load blocked later loads | ported | `MessageObject.isExpiredLiveLocation` (long sum, `NanogramLiveLocation`), the four hand-written checks in `LocationActivity`, `SharingLiveLocationCell`, `FragmentContextView`, `LocationController.cacheRequests` cleared on error |
| 9e8f8efc9 | Live location without Play Services | partly ported | Ported: `PermissionRequest.hasPermission` asks the application context (a grant is per process; services reported it denied). Not applicable: waiting for a fresh GPS fix (we have the fused provider). Declined: sticky service and boot restore (upstream chose `START_NOT_STICKY` and `onAppResumed` restart on purpose; location foreground services cannot start from boot on Android 15) |
| b02b89bd7 | Stale album layout after a member is deleted | ported | `MessageObject` group recalc resets `isDocuments`, `hasSibling`, `hasCaption`, `captionAbove`, `cachedWidthForCaption` |
| b569e7725 | Save to Downloads skipped freshly downloaded media | ported | `MediaController.MediaLoader` keeps the file reported by `fileLoaded`. The path fallback keeps our `getPathToAttach(..., true)` |
| e7019b3e8 | Edited photo lost resolution (crop before downscale, quality 87 intermediates, high quality on the ungrouped path) | ported | `NanogramPhotoCrop` + `NanogramCropMath` (pure), `MediaController`, `SendMessagesHelper`, `PhotoViewer`. Inugram-origin ideas, GPL-2.0 reimplementation of Mercurygram's helper |
| 0dc808924 | Photo picking and editing bugs (Inugram, MIT) | ported | bake from disk when the bitmap is gone and abort send instead of sending the wrong image, caption typed in the viewer applied on send, 90 degree rotation keeps the crop, caption follows its photo on reorder, pan bounds, Gboard paste, album spoiler reveal on the group's primary message, album caption link ripple |
| d19ba21d2 | Save, share and open of a watched video with quality variants | ported | `NanogramLocalMedia.cachedFile` used in `ChatActivity`, `ChannelAdminLogActivity`, `PhotoViewer`, `AndroidUtilities`. The Message Details screen of the original does not exist here |
| d7f19a657 | IPv6 proxy connectivity | ported | `ConnectionSocket.cpp`: socket created after the address family is known, delegate-resolved IPv6 handled; `isDisconnected()` is false while a hostname lookup runs so early writes queue instead of being dropped. Native, not compiled |
| 752c41b28 | All resolved IPs for a proxy hostname | ported | `ConnectionsManager.ResolveHostByNameTask`: `getAllByName`, IPv4 preferred, IPv6 only when there is no IPv4 |
| 19fee5dae (DNS part) | Google DoH removed from proxy-hostname resolution | ported (parity with iOS and Mac) | `ResolveHostByNameTask` no longer asks `www.google.com/resolve` first (up to 3 s per resolve, leaked the proxy hostname). The backup-config DoH (`GoogleDnsLoadTask`, `MozillaDnsLoadTask`) stays, as on iOS |
| db32ac048 (adapter factory part) | `RuntimeClassNameTypeAdapterFactory` null exclusion strategy | partly ported | Null guard only; their change of `getRawType().getClass()` to `getRawType()` changes which classes are skipped and was left out |
| 769b4008a | Inline code tap-to-copy ignoring "no forwards" | declined | Bypasses protected content; not a bug fix |
| 0e07a6727, 9337fdeec, 7390b8846, 93ad13bfe, f62262508 | Push watchdog, foreground push service, keep-alive notification text, UnifiedPush, gateways | declined | UnifiedPush and GMS removal are owner decisions; our FCM path is untouched apart from the latch fix |
| 9c625e165 | PFS temp-key rotation against auth_key_id tracking | declined | Changes what the server sees (owner decision) |
| efa0255f0 | Native flags that drop LTO/ICF, reproducible builds | declined | Slower and larger code; we keep ThinLTO |
| 0ec92ad56, 30da375f1, 9fd85105f, 8147a9da1, ce9a628ce, 197a708ae | Link previews, local drafts, global search, Instant View, AI buttons, secret chats by default | declined or not bug fixes | Server-visible data or features |
| 5cafb7616, 8c9f90a9b, 6d2af8eae | Premium gates and promo | declined | Premium changes |
| 64d6a2cd9 | Tor | declined | Owner decision |
| b7278c004, 95418c493, 61ed3f602 | Tracking-parameter strip, confirm t.me links, search operators | already done | Spec section 7, commit 0de8c37 |
| 38dbb8733, 8052c3b10, 981afd26a, aea32936e, 2fce50e2f, 79ec5183d, fb1b2bf33, 6ca487775, b2e730d59, 91383dab0, 9fc1bc705 and the other `[MG]` feature commits (folders, history, QR login, emoji packs, translation engines, hidden accounts, branding, updater, CI) | Features, settings, branding, build | not a bug fix or performance change | Out of scope of spec section 9 |

## Top-15 fix items (analysis review)

| # | Item | Status |
|---|---|---|
| 1 | Push latch | ported |
| 2 | Crash pack | ported |
| 3 | Large-account slowdowns | ported; badge recount checked on iOS and Mac (below) |
| 4 | Google DoH removed from proxy-hostname resolution | ported |
| 5 | Notification trio | ported |
| 6 | Live location until stopped | ported; iOS and Mac checked (below) |
| 7 | Theme colours with R8 | partly ported, not reproduced |
| 8 | Search operators | already done (0de8c37) |
| 9 | Media I/O remainder | partly ported |
| 10, 11 | Local folders, Unicode folding | not bug fixes; owner decision pending |
| 12 | Link hygiene | already done (0de8c37) |
| 13 | Secret-chat delete for everyone | ported; iOS and Mac checked (below) |
| 14 | TL legacy readParams | ported |
| 15 | UnifiedPush | declined |

## Same bug on iOS and Mac

- Live location "until stopped" (item 6): **not affected.** iOS tests `liveLocationIndefinitePeriod` before every Int32
  sum (`LiveLocationManager.swift:73`, `PeerLiveLocationsContext.swift:22`, `ChatMessageMapBubbleContentNode.swift:89`,
  `ChatHistoryListNode.swift:3258`, `LocationLiveListItem.swift:265`, `LocationViewControllerNode.swift:577`). Mac sums
  in `Int` (`ChatController.swift:10020`, `ChatMapRowItem.swift:74-79`, `ChatRowItem.swift:661-665`), so there is no overflow.
- Secret-chat delete for everyone (item 9): **affected on both**, one fix in the shared core. `_internal_removePeerChat`
  (`submodules/TelegramCore/Sources/TelegramEngine/Peers/RemovePeerChat.swift:52`) and `_internal_terminateSecretChat`
  (`RemovePeerChat.swift:13-15`, called with `requestRemoteHistoryRemoval: true` from
  `submodules/TelegramUI/Sources/Chat/ChatControllerReport.swift:206`) queue only
  `.terminate(requestRemoteHistoryRemoval:)`, which becomes `messages.discardEncryption(delete_history)` in
  `ManagedSecretChatOutgoingOperations.swift:2182-2187`. Fix: when `requestRemoteHistoryRemoval` is true and the chat is
  established, first queue `SecretChatOutgoingOperationContents.clearHistory(layer:actionGloballyUniqueId:)` the way
  `DeleteMessagesInteractively.swift:203` does, then the terminate operation (operations run in order,
  `ManagedSecretChatOutgoingOperations.swift:150-151` and `:170-171`). Mac reaches it through
  `packages/ChatUI/Sources/ChatUI/LeaveChatActions.swift:98,131` (`engine.peers.removePeerChat`), so it needs no Mac code.
- Badge recount per deleted message (part of item 3): **not affected.** Postbox keeps incremental unread summaries
  (`ChatListIndexTable.swift:389`); the only full recount paths are `recalculateChatListGroupStats`
  (`ManagedSynchronizeGroupMessageStats.swift:78`) and `ReindexUnreadCounters.swift`, neither runs per delete.
- livestream null decoder context: iOS, Mac and Android build the same `AudioStreamingPartPersistentDecoder.cpp`; fixed
  once in the shared tgcalls fork (4f251c4), each app only needs the pin bump.
- DNS (item 4) is already done on iOS and Mac; IPv6 proxy hosts are handled in `MTTcpConnection.m:1069,1087`.
- Not applicable to iOS and Mac (Android-only code): push latch, crash pack, leaks, TL legacy classes, notification
  trio, theme R8, media I/O, photo editing, large-account slowdowns.

## What the combined build and test must check

- Compiles: `NanogramPhotoCrop` (uses `PhotoViewer.createCroppedBitmap`, `StoryEntry`), the `PhotoViewer` hunks from
  e7019b3e8/0dc808924/d19ba21d2, `MessagesStorage.updateDialogsWithDeletedMessagesInternal`, `NotificationsController`
  (`chatType`, `lastDialogId`, `lastTopicId` in `showExtraNotifications`), `ConnectionSocket.cpp`.
- JVM tests with real JUnit: `NanogramCropMathTest`, `NanogramLiveLocationTest`, `NanogramFolderPinsTest`,
  `NanogramRecountThrottleTest` (plus the two from section 7).
- Release build: switch dark/light with a chat open; every ThemeDescription colour must refresh. Compare R8
  `mapping.txt` for the fields of `ChatMessageCell` and `DialogCell`.
- Pushes: three pushes in a row each show their notification; an OAUTH_REQUEST push does not block the next one.
- Notifications on Android 16: two chats, new message in each: heads-up names the chat, not "N messages from M chats".
  Opening a chat's Sound screen and leaving it does not change the stored tone. The permission prompt does not return
  after dismiss.
- Large account: delete many messages in a channel with auto-delete; the chat list does not rebuild per delete, badges
  stay correct, Mark all as read in a folder sends requests only for unread chats.
- Live location "until stopped": shows as active in the chat, the map and the context bar after a restart.
- Secret chat: delete for everyone on a test pair; the peer's chat is cleared.
- Downloads: save a just-downloaded video to Downloads; a streamed video with quality variants can be saved and shared.
- Photo editing: crop a quarter of a 12 MP photo and send as high quality: the sent size is above the old one; rotate
  by 90 degrees in the crop editor keeps the crop; reorder in the attach preview keeps each caption.
- Proxy: an IPv6 literal proxy and a hostname proxy with both A and AAAA records connect.
- Media: a large download, pause and resume (parts file now `rw`), kill the process mid-download and resume: size matches.
