package com.intellirs.reader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DateUtilsTest {
    @Test
    public void parsesRfc1123() {
        assertTrue(DateUtils.parse("Mon, 12 Oct 2026 08:30:00 GMT") > 0);
        assertEquals(DateUtils.parse("Mon, 12 Oct 2026 08:30:00 +0000"),
                DateUtils.parse("Mon, 12 Oct 2026 08:30:00 GMT"));
    }

    @Test
    public void parsesAtomIsoDates() {
        assertTrue(DateUtils.parse("2026-10-14T09:10:00Z") > 0);
        assertTrue(DateUtils.parse("2026-10-14T09:10:00+05:30") > 0);
        assertTrue(DateUtils.parse("2026-10-14T09:10:00.123Z") > 0);
    }

    @Test
    public void parsesRssVariantsWithSingleDigitDay() {
        assertTrue(DateUtils.parse("Mon, 12 Oct 2026 08:30:00 +0000") > 0);
        assertTrue(DateUtils.parse("Mon, 2 Oct 2026 08:30:00 GMT") > 0);
    }

    @Test
    public void invalidDatesReturnZero() {
        assertEquals(0L, DateUtils.parse("not a date"));
        assertEquals(0L, DateUtils.parse(""));
        assertEquals(0L, DateUtils.parse(null));
    }
}
