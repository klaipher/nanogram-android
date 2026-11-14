package org.telegram.messenger.nanogram;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Strips known tracking parameters from http(s) links when opening and copying them. Never touches t.me, tg://
 * or any other scheme (their parameters carry meaning) and never rewrites message text. Shared list:
 * ios-integration docs/specs/link-tracking-params.txt.
 */
public final class NanogramLinkHygiene {

    // Mirror of ios-integration docs/specs/link-tracking-params.txt; change both together.
    private static final Set<String> PARAMS = new HashSet<>(Arrays.asList(
            "fbclid", "gclid", "gclsrc", "dclid", "gbraid", "wbraid", "msclkid", "twclid", "ttclid", "li_fat_id",
            "igshid", "igsh", "mc_eid", "mc_cid", "_hsenc", "_hsmi", "__hssc", "__hstc", "__hsfp", "hsctatracking",
            "mkt_tok", "oly_anon_id", "oly_enc_id", "vero_id", "vero_conv", "yclid", "ysclid", "_openstat",
            "ref_src", "ref_url", "s_cid", "spm", "scm"
    ));

    private static final String[] PREFIXES = {"utm_"};

    // host|name entries; a host entry also covers its subdomains. A name ending in * is a prefix.
    private static final String[][] HOST_PARAMS = {
            {"youtube.com", "si"}, {"youtu.be", "si"}, {"open.spotify.com", "si"}, {"music.youtube.com", "si"},
            {"youtube.com", "feature"}, {"youtube.com", "pp"},
            {"amazon.com", "ref"}, {"amazon.com", "tag"}, {"amazon.com", "pd_rd_*"}, {"amazon.com", "pf_rd_*"},
            {"tiktok.com", "share_id"}, {"tiktok.com", "is_copy_url"}, {"tiktok.com", "is_from_webapp"}, {"tiktok.com", "sender_device"},
            {"facebook.com", "refsrc"}, {"facebook.com", "__tn__"}, {"facebook.com", "__cft__*"}
    };

    private static final Set<String> TELEGRAM_HOSTS = new HashSet<>(Arrays.asList(
            "t.me", "telegram.me", "telegram.dog", "telesco.pe"
    ));

    private NanogramLinkHygiene() {
    }

    public static boolean isTelegramLink(String url) {
        if (url == null) {
            return false;
        }
        final String lower = url.trim().toLowerCase(Locale.ROOT);
        if (lower.startsWith("tg:")) {
            return true;
        }
        final String host = host(lower);
        return host != null && TELEGRAM_HOSTS.contains(host);
    }

    public static String clean(String url) {
        if (url == null) {
            return null;
        }
        final String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return url;
        }
        final String host = host(lower);
        if (host == null || TELEGRAM_HOSTS.contains(host)) {
            return url;
        }
        final int hash = url.indexOf('#');
        final String fragment = hash < 0 ? "" : url.substring(hash);
        final String beforeFragment = hash < 0 ? url : url.substring(0, hash);
        final int question = beforeFragment.indexOf('?');
        if (question < 0) {
            return url;
        }
        final String base = beforeFragment.substring(0, question);
        final String query = beforeFragment.substring(question + 1);
        final StringBuilder kept = new StringBuilder();
        boolean removed = false;
        for (String part : query.split("&", -1)) {
            final int eq = part.indexOf('=');
            final String name = (eq < 0 ? part : part.substring(0, eq)).toLowerCase(Locale.ROOT);
            if (isTracking(name, host)) {
                removed = true;
                continue;
            }
            if (kept.length() > 0) {
                kept.append('&');
            }
            kept.append(part);
        }
        if (!removed) {
            return url;
        }
        return kept.length() == 0 ? base + fragment : base + "?" + kept + fragment;
    }

    static boolean isTracking(String name, String host) {
        if (PARAMS.contains(name)) {
            return true;
        }
        for (String prefix : PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        for (String[] entry : HOST_PARAMS) {
            if (!host.equals(entry[0]) && !host.endsWith("." + entry[0])) {
                continue;
            }
            final String pattern = entry[1];
            if (pattern.endsWith("*") ? name.startsWith(pattern.substring(0, pattern.length() - 1)) : name.equals(pattern)) {
                return true;
            }
        }
        return false;
    }

    private static String host(String lowerUrl) {
        final int scheme = lowerUrl.indexOf("://");
        if (scheme < 0) {
            return null;
        }
        int end = lowerUrl.length();
        for (char stop : new char[]{'/', '?', '#'}) {
            final int i = lowerUrl.indexOf(stop, scheme + 3);
            if (i >= 0 && i < end) {
                end = i;
            }
        }
        String authority = lowerUrl.substring(scheme + 3, end);
        final int at = authority.lastIndexOf('@');
        if (at >= 0) {
            authority = authority.substring(at + 1);
        }
        final int colon = authority.lastIndexOf(':');
        if (colon >= 0 && authority.indexOf(']') < colon) {
            authority = authority.substring(0, colon);
        }
        return authority.isEmpty() ? null : authority;
    }
}
