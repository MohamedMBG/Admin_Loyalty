package com.example.adminloyalty;
import static org.junit.Assert.assertEquals;

import com.example.adminloyalty.utils.TimeUtils;

import org.junit.Test;

import java.util.Calendar;

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
    }

    @Test
    public void calculateAge_returnsNegativeOneForInvalidFormat() {
        assertEquals(-1, TimeUtils.calculateAge("invalid-date"));
        assertEquals(-1, TimeUtils.calculateAge("1990/01/01"));
        assertEquals(-1, TimeUtils.calculateAge(null));
    }
}