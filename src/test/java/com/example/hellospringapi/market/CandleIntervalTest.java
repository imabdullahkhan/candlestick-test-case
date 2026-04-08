package com.example.hellospringapi.market;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CandleIntervalTest {

    @ParameterizedTest
    @CsvSource({"1s,S1", "5s,S5", "1m,M1", "15m,M15", "1h,H1"})
    void fromValueResolvesAllIntervals(String input, String expectedName) {
        CandleInterval result = CandleInterval.fromValue(input);
        assertEquals(expectedName, result.name());
    }

    @Test
    void fromValueIsCaseInsensitive() {
        assertEquals(CandleInterval.M1, CandleInterval.fromValue("1M"));
        assertEquals(CandleInterval.H1, CandleInterval.fromValue("1H"));
        assertEquals(CandleInterval.S5, CandleInterval.fromValue("5S"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2s", "10m", "1d", "", "abc"})
    void fromValueThrowsOnInvalidInput(String input) {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> CandleInterval.fromValue(input)
        );
        assertEquals("Unsupported interval: " + input, ex.getMessage());
    }

    @Test
    void valueReturnsDisplayString() {
        assertEquals("1s", CandleInterval.S1.value());
        assertEquals("5s", CandleInterval.S5.value());
        assertEquals("1m", CandleInterval.M1.value());
        assertEquals("15m", CandleInterval.M15.value());
        assertEquals("1h", CandleInterval.H1.value());
    }

    @Test
    void secondsReturnsCorrectDuration() {
        assertEquals(1L, CandleInterval.S1.seconds());
        assertEquals(5L, CandleInterval.S5.seconds());
        assertEquals(60L, CandleInterval.M1.seconds());
        assertEquals(900L, CandleInterval.M15.seconds());
        assertEquals(3600L, CandleInterval.H1.seconds());
    }

    @Test
    void bucketStartAlignsToSecondBoundary() {
        assertEquals(1000L, CandleInterval.S1.bucketStart(1000));
        assertEquals(1001L, CandleInterval.S1.bucketStart(1001));
    }

    @Test
    void bucketStartAlignsToFiveSecondBoundary() {
        assertEquals(1000L, CandleInterval.S5.bucketStart(1000));
        assertEquals(1000L, CandleInterval.S5.bucketStart(1003));
        assertEquals(1005L, CandleInterval.S5.bucketStart(1005));
        assertEquals(1005L, CandleInterval.S5.bucketStart(1009));
    }

    @Test
    void bucketStartAlignsToMinuteBoundary() {
        assertEquals(960L, CandleInterval.M1.bucketStart(960));
        assertEquals(960L, CandleInterval.M1.bucketStart(999));
        assertEquals(1020L, CandleInterval.M1.bucketStart(1020));
        assertEquals(1020L, CandleInterval.M1.bucketStart(1079));
    }

    @Test
    void bucketStartAlignsToFifteenMinuteBoundary() {
        assertEquals(0L, CandleInterval.M15.bucketStart(0));
        assertEquals(0L, CandleInterval.M15.bucketStart(899));
        assertEquals(900L, CandleInterval.M15.bucketStart(900));
        assertEquals(900L, CandleInterval.M15.bucketStart(1799));
    }

    @Test
    void bucketStartAlignsToHourBoundary() {
        assertEquals(0L, CandleInterval.H1.bucketStart(0));
        assertEquals(0L, CandleInterval.H1.bucketStart(3599));
        assertEquals(3600L, CandleInterval.H1.bucketStart(3600));
        assertEquals(3600L, CandleInterval.H1.bucketStart(7199));
    }

    @Test
    void bucketStartAtZeroTimestamp() {
        assertEquals(0L, CandleInterval.S1.bucketStart(0));
        assertEquals(0L, CandleInterval.S5.bucketStart(0));
        assertEquals(0L, CandleInterval.M1.bucketStart(0));
        assertEquals(0L, CandleInterval.H1.bucketStart(0));
    }
}
