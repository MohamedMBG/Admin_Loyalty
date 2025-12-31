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
        // Setup a date 20 years ago, but next month (so they are still 19)
        Calendar cal = Calendar.getInstance();
        int currentYear = cal.get(Calendar.YEAR);
        int nextMonth = cal.get(Calendar.MONTH) + 2;
        if (nextMonth > 12) nextMonth = 1;

        String dob = (currentYear - 20) + "-" + String.format("%02d", nextMonth) + "-01";
        int age = TimeUtils.calculateAge(dob);

        // Still 19 because birthday hasn't happened yet this year
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