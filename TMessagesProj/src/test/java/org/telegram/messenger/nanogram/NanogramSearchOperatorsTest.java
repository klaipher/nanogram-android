package org.telegram.messenger.nanogram;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.TimeZone;

public class NanogramSearchOperatorsTest {

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    // 2026-11-14 12:00:00 UTC
    private static final long NOW = 1_794_657_600_000L;

    private static NanogramSearchOperators.Result parse(String q) {
        return NanogramSearchOperators.parse(q, UTC, NOW);
    }

    @Test
    public void plainTextHasNoOperators() {
        final NanogramSearchOperators.Result r = parse("hello world");
        assertEquals("hello world", r.text);
        assertFalse(r.hasOperators());
    }

    @Test
    public void nullAndEmptyAreEmpty() {
        assertEquals("", parse(null).text);
        assertEquals("", parse("   ").text);
        assertFalse(parse("").hasOperators());
    }

    @Test
    public void fromTakesUsernameWithOrWithoutAt() {
        assertEquals("durov", parse("from:@durov pizza").from);
        assertEquals("durov", parse("pizza from:durov").from);
        assertEquals("pizza", parse("from:durov pizza").text);
    }

    @Test
    public void fromKeepsTokenInTextWithFrom() {
        final NanogramSearchOperators.Result r = parse("from:nobody pizza");
        assertEquals("pizza", r.text);
        assertEquals("from:nobody pizza", r.textWithFrom);
    }

    @Test
    public void emptyValuesStayPlainText() {
        assertEquals("from: pizza", parse("from: pizza").text);
        assertEquals("type:", parse("type:").text);
        assertEquals(":x", parse(":x").text);
    }

    @Test
    public void unknownKeysAndValuesStayPlainText() {
        assertEquals("foo:bar", parse("foo:bar").text);
        assertEquals("type:banana", parse("type:banana").text);
        assertEquals("after:soon", parse("after:soon").text);
        assertEquals("https://example.com", parse("https://example.com").text);
        assertFalse(parse("time 10:30").hasOperators());
    }

    @Test
    public void operatorsAreCaseInsensitive() {
        assertEquals(NanogramSearchOperators.Type.PHOTO, parse("TYPE:Photo").type);
        assertEquals("a", parse("From:a").from);
    }

    @Test
    public void typesMap() {
        assertEquals(NanogramSearchOperators.Type.VIDEO, parse("type:video").type);
        assertEquals(NanogramSearchOperators.Type.FILE, parse("type:file").type);
        assertEquals(NanogramSearchOperators.Type.FILE, parse("type:document").type);
        assertEquals(NanogramSearchOperators.Type.LINK, parse("type:link").type);
        assertEquals(NanogramSearchOperators.Type.VOICE, parse("type:voice").type);
        assertEquals(NanogramSearchOperators.Type.MUSIC, parse("type:music").type);
        assertEquals(NanogramSearchOperators.Type.GIF, parse("type:gif").type);
        assertEquals(NanogramSearchOperators.Type.ROUND, parse("type:round").type);
    }

    @Test
    public void afterIsEndOfRangeBeforeIsStartOfRange() {
        // 2026-05-01 00:00:00 UTC = 1777593600
        assertEquals(1_777_593_600 + 86_400, parse("after:2026-05-01").minDate);
        assertEquals(1_777_593_600, parse("before:2026-05-01").maxDate);
        assertEquals(0, parse("after:2026-05-01").maxDate);
        assertEquals(0, parse("before:2026-05-01").minDate);
    }

    @Test
    public void dateCoversWholeDayMonthOrYear() {
        final NanogramSearchOperators.Result day = parse("date:2026-05-01");
        assertEquals(1_777_593_600, day.minDate);
        assertEquals(1_777_593_600 + 86_400, day.maxDate);
        final NanogramSearchOperators.Result month = parse("date:2026-02");
        assertEquals(1_769_904_000, month.minDate);
        assertEquals(1_769_904_000 + 28 * 86_400, month.maxDate);
        final NanogramSearchOperators.Result year = parse("date:2026");
        assertEquals(1_767_225_600, year.minDate);
        assertEquals(1_767_225_600 + 365 * 86_400, year.maxDate);
    }

    @Test
    public void specVectorsForNovember() {
        // 2026-11-01 00:00:00 UTC = 1793491200
        assertEquals(1_793_491_200, parse("before:2026-11-01").maxDate);
        assertEquals(1_793_491_200 + 86_400, parse("after:2026-11-01").minDate);
        final NanogramSearchOperators.Result month = parse("date:2026-11");
        assertEquals(1_793_491_200, month.minDate);
        assertEquals(1_793_491_200 + 30 * 86_400, month.maxDate);
        assertEquals(1_793_491_200 + 30 * 86_400, parse("after:2026-11").minDate);
        assertEquals(1_793_491_200 - 31 * 86_400, parse("before:2026-11 before:2026-10").maxDate);
    }

    @Test
    public void dateWithBeforeAndAfterTakesTheTighterBounds() {
        final NanogramSearchOperators.Result r = parse("date:2026-11 after:2026-11-10 before:2026-11-20");
        assertEquals(1_793_491_200 + 10 * 86_400, r.minDate);
        assertEquals(1_793_491_200 + 19 * 86_400, r.maxDate);
        final NanogramSearchOperators.Result wide = parse("date:2026-11-05 after:2026-01-01 before:2026-12-01");
        assertEquals(1_793_491_200 + 4 * 86_400, wide.minDate);
        assertEquals(1_793_491_200 + 5 * 86_400, wide.maxDate);
    }

    @Test
    public void pollAndPinnedTypes() {
        assertEquals(NanogramSearchOperators.Type.POLL, parse("type:poll").type);
        assertEquals(NanogramSearchOperators.Type.PINNED, parse("type:Pinned x").type);
    }

    @Test
    public void fromMeIsParsedAsMe() {
        assertEquals("me", parse("from:me hi").from);
    }

    @Test
    public void unclosedQuoteStaysPlainAndDoesNotSwallowTheRest() {
        final NanogramSearchOperators.Result r = parse("from:\"Jane type:photo");
        assertEquals("from:\"Jane", r.text);
        assertNull(r.from);
        assertEquals(NanogramSearchOperators.Type.PHOTO, r.type);
        assertEquals("from:\"Jane", parse("from:\"Jane").text);
    }

    @Test
    public void todayAndYesterdayUseTheGivenZone() {
        final NanogramSearchOperators.Result today = parse("date:today");
        assertEquals(1_794_614_400, today.minDate);
        assertEquals(1_794_614_400 + 86_400, today.maxDate);
        assertEquals(1_794_614_400, parse("after:yesterday").minDate);
        final NanogramSearchOperators.Result kyiv = NanogramSearchOperators.parse("date:today", TimeZone.getTimeZone("Europe/Kyiv"), NOW);
        assertEquals(1_794_614_400 - 7200, kyiv.minDate);
    }

    @Test
    public void invalidDatesStayPlainText() {
        assertEquals("date:2026-13-01", parse("date:2026-13-01").text);
        assertEquals("date:2026-02-30", parse("date:2026-02-30").text);
        assertEquals("after:26-05-01", parse("after:26-05-01").text);
        assertEquals("after:1969", parse("after:1969").text);
        assertEquals("after:2026-5-1", parse("after:2026-5-1").text);
    }

    @Test
    public void combinedOperatorsAndText() {
        final NanogramSearchOperators.Result r = parse("invoice from:@ann after:2026-01-01 type:file before:2026-06-01");
        assertEquals("invoice", r.text);
        assertEquals("ann", r.from);
        assertEquals(NanogramSearchOperators.Type.FILE, r.type);
        assertTrue(r.minDate > 0 && r.maxDate > r.minDate);
    }

    @Test
    public void lastOperatorWins() {
        assertEquals("b", parse("from:a from:b").from);
        assertEquals(NanogramSearchOperators.Type.GIF, parse("type:photo type:gif").type);
    }

    @Test
    public void quotedValueMayHoldSpaces() {
        final NanogramSearchOperators.Result r = parse("from:\"Ann Lee\" report");
        assertEquals("Ann Lee", r.from);
        assertEquals("report", r.text);
    }

    @Test
    public void operatorsOnlyLeavesEmptyText() {
        final NanogramSearchOperators.Result r = parse("type:photo");
        assertEquals("", r.text);
        assertTrue(r.hasOperators());
        assertNull(r.from);
    }
}
