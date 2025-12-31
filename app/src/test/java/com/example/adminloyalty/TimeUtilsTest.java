package com.example.adminloyalty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.adminloyalty.utils.TimeUtils;

import org.junit.Test;

import java.util.Calendar;
import java.util.Date;

public class TimeUtilsTest {

    @Test
    public void calculateAge_returnsCorrectAge() {
        // Setup a date 20 years ago
        Calendar cal = Calendar.getInstance();
        int currentYear = cal.get(Calendar.YEAR);
        String dob = (currentYear - 20) + "-01-01";
        int age = TimeUtils.calculateAge(dob);
        // Verify
        assertEquals(20, age);
    }

    @Test
    public void calculateAge_handlesNotBornYetThisYear() {
        Calendar cal = Calendar.getInstance();
        int currentYear = cal.get(Calendar.YEAR);

        int month1Based = cal.get(Calendar.MONTH) + 1; // 1..12
        int nextMonth = month1Based + 1;

        int birthYear = currentYear - 20;

        // if next month wraps to January, the birthday would be next year,
        // so to still be 19 today (in December), birth year must be currentYear - 19
        if (nextMonth == 13) {
            nextMonth = 1;
            birthYear = currentYear - 19;
        }

        String dob = birthYear + "-" + String.format("%02d", nextMonth) + "-01";
        int age = TimeUtils.calculateAge(dob);

        assertEquals(19, age);
    }

    @Test
    public void calculateAge_returnsNegativeOneForInvalidFormat() {
        assertEquals(-1, TimeUtils.calculateAge("invalid-date"));
        assertEquals(-1, TimeUtils.calculateAge("1990/01/01"));
        assertEquals(-1, TimeUtils.calculateAge(null));
    }

    // --- 2. PROMO DURATION TESTS ---

    @Test
    public void isDateWithinRange_validCases() {
        long now = System.currentTimeMillis();
        Date today = new Date(now);
        Date yesterday = new Date(now - 86400000L);
        Date tomorrow = new Date(now + 86400000L);

        // Standard case: Today is inside Yesterday -> Tomorrow
        assertTrue(TimeUtils.isDateWithinRange(today, yesterday, tomorrow));

        // Edge case: Promo started exactly now (Inclusive check)
        assertTrue(TimeUtils.isDateWithinRange(today, today, tomorrow));
    }

    @Test
    public void isDateWithinRange_expiredOrFuture() {
        long now = System.currentTimeMillis();
        Date today = new Date(now);
        Date pastStart = new Date(now - 100000);
        Date pastEnd = new Date(now - 50000);
        Date futureStart = new Date(now + 50000);

        // Expired case: Range was in the past
        assertFalse(TimeUtils.isDateWithinRange(today, pastStart, pastEnd));

        // Future case: Range hasn't started yet
        assertFalse(TimeUtils.isDateWithinRange(today, futureStart, null));
    }

    // --- 3. INACTIVE DAYS TESTS ---

    @Test
    public void getDaysDifference_calculatesCorrectly() {
        long now = System.currentTimeMillis();
        Date today = new Date(now);

        // 5 days ago = 5 * 24 * 60 * 60 * 1000
        long fiveDaysMs = 5L * 24 * 60 * 60 * 1000;
        Date fiveDaysAgo = new Date(now - fiveDaysMs);

        long diff = TimeUtils.getDaysDifference(today, fiveDaysAgo);

        assertEquals(5, diff);
    }

}