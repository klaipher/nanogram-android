package org.telegram.messenger.nanogram;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Strips known tracking parameters from http(s) links when opening and copying them, and recognises Telegram links.
 * Same behaviour as iOS and the Mac (owner decision 2026-11-15): the list is the shared data file
 * nanogram-deps data/link-tracking-params.txt (the {@code shared} submodule), the vectors are data/link-hygiene-vectors.json;
 * {@code NanogramLinkHygieneTest} reads both and fails when this class drifts from them. Never rewrites message text.
 */
public final class NanogramLinkHygiene {

    /**
     * The shared list, one entry per line as in the data file: {@code name}, {@code prefix*}, or {@code host|name}
     * (a host entry also covers its subdomains). Names compare case-insensitively. Change data/link-tracking-params.txt, then this array.
     */
    static final String[] RULES = {
            "utm_*", "fbclid", "gclid", "gclsrc", "dclid", "gbraid", "wbraid", "msclkid", "twclid", "ttclid", "li_fat_id",
            "igshid", "igsh", "mc_eid", "mc_cid", "_hsenc", "_hsmi", "__hssc", "__hstc", "__hsfp", "hsctatracking",
            "mkt_tok", "oly_anon_id", "oly_enc_id", "vero_id", "vero_conv", "yclid", "ysclid", "_openstat",
            "ref_src", "ref_url", "s_cid", "spm", "scm",
            "si|youtube.com", "si|youtu.be", "si|open.spotify.com", "si|music.youtube.com",
            "feature|youtube.com", "pp|youtube.com",
            "ref|amazon.com", "tag|amazon.com", "pd_rd_*|amazon.com", "pf_rd_*|amazon.com",
            "share_id|tiktok.com", "is_copy_url|tiktok.com", "is_from_webapp|tiktok.com", "sender_device|tiktok.com",
            "refsrc|facebook.com", "__tn__|facebook.com", "__cft__*|facebook.com"
    };

    private static final class Rule {
        final String name;
        final boolean prefix;
        final String host;

        Rule(String name, boolean prefix, String host) {
            this.name = name;
            this.prefix = prefix;
            this.host = host;
        }
    }

    private static final List<Rule> PARSED = parse(RULES);

    private static final String[] TELEGRAM_HOSTS = {"t.me", "telegram.me", "telegram.dog", "www.t.me", "www.telegram.me"};

    private NanogramLinkHygiene() {
    }

    static List<Rule> parse(String[] lines) {
        final List<Rule> rules = new ArrayList<>();
        for (String raw : lines) {
            final String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String name = line;
            String host = null;
            final int bar = line.indexOf('|');
            if (bar >= 0) {
                name = line.substring(0, bar);
                host = line.substring(bar + 1).toLowerCase(Locale.ROOT);
            }
            final boolean prefix = name.endsWith("*");
            if (prefix) {
                name = name.substring(0, name.length() - 1);
            }
            if (!name.isEmpty()) {
                rules.add(new Rule(name.toLowerCase(Locale.ROOT), prefix, host));
            }
        }
        return rules;
    }

    /** t.me, telegram.me, telegram.dog (also with www.) over http(s), and tg:// links. telesco.pe and a bare tg: are not Telegram links. */
    public static boolean isTelegramLink(String url) {
        if (url == null) {
            return false;
        }
        final String lower = url.trim().toLowerCase(Locale.ROOT);
        if (lower.startsWith("tg://")) {
            return true;
        }
        for (String scheme : new String[]{"https://", "http://"}) {
            if (lower.startsWith(scheme)) {
                final String rest = lower.substring(scheme.length());
                int end = rest.length();
                for (char stop : new char[]{'/', '?', '#'}) {
                    final int i = rest.indexOf(stop);
                    if (i >= 0 && i < end) {
                        end = i;
                    }
                }
                final String host = rest.substring(0, end);
                for (String telegramHost : TELEGRAM_HOSTS) {
                    if (telegramHost.equals(host)) {
                        return true;
                    }
                }
                return false;
            }
        }
        return false;
    }

    /** Removes tracking parameters from an http(s) link. Anything else, and links with nothing to remove, come back as given; empty query items are kept. */
    public static String clean(String url) {
        if (url == null) {
            return null;
        }
        final int question = url.indexOf('?');
        if (question < 0 || url.substring(0, question).indexOf('#') >= 0) {
            return url;
        }
        final int scheme = url.indexOf("://");
        if (scheme < 0 || scheme + 3 > question) {
            return url;
        }
        final String schemeName = url.substring(0, scheme).toLowerCase(Locale.ROOT);
        if (!schemeName.equals("http") && !schemeName.equals("https")) {
            return url;
        }
        final String afterScheme = url.substring(scheme + 3, question);
        final int slash = afterScheme.indexOf('/');
        String host = slash < 0 ? afterScheme : afterScheme.substring(0, slash);
        final int at = host.lastIndexOf('@');
        if (at >= 0) {
            host = host.substring(at + 1);
        }
        final int colon = host.lastIndexOf(':');
        if (colon >= 0 && !host.endsWith("]")) {
            host = host.substring(0, colon);
        }
        host = host.toLowerCase(Locale.ROOT);

        final String base = url.substring(0, question);
        String query = url.substring(question + 1);
        String fragment = "";
        final int hash = query.indexOf('#');
        if (hash >= 0) {
            fragment = query.substring(hash);
            query = query.substring(0, hash);
        }
        final StringBuilder kept = new StringBuilder();
        boolean removed = false;
        boolean first = true;
        for (String part : query.split("&", -1)) {
            final int eq = part.indexOf('=');
            final String name = decode(eq < 0 ? part : part.substring(0, eq));
            if (!name.isEmpty() && isTracking(name, host)) {
                removed = true;
                continue;
            }
            if (!first) {
                kept.append('&');
            }
            first = false;
            kept.append(part);
        }
        if (!removed) {
            return url;
        }
        return base + (kept.length() == 0 ? "" : "?" + kept) + fragment;
    }

    static boolean isTracking(String name, String host) {
        final String lowerName = name.toLowerCase(Locale.ROOT);
        final String lowerHost = host.toLowerCase(Locale.ROOT);
        for (Rule rule : PARSED) {
            if (rule.host != null && !lowerHost.equals(rule.host) && !lowerHost.endsWith("." + rule.host)) {
                continue;
            }
            if (rule.prefix ? lowerName.startsWith(rule.name) : lowerName.equals(rule.name)) {
                return true;
            }
        }
        return false;
    }

    private static String decode(String name) {
        if (name.indexOf('%') < 0) {
            return name;
        }
        try {
            return URLDecoder.decode(name.replace("+", "%2B"), "UTF-8");
        } catch (Throwable e) {
            return name;
        }
    }
}
