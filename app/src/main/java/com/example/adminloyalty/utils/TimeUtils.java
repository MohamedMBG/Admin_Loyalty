package com.example.adminloyalty.utils;

import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class TimeUtils {
    /**
     * Calculates age from a YYYY-MM-DD string.
     * @param dobStr Date of birth string
     * @return Age in years, or -1 if invalid
     */
    public static int calculateAge(String dobStr) {
        if (dobStr == null || dobStr.isEmpty() ){
            return -1;
        }
        String[] parts = dobStr.split("-");
        if ( parts.length != 3 ){
            return -1;
        }
        try {
            // adjusting the date format ( if necessary ) to avoid crashing or wrong calculations
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int day = Integer.parseInt(parts[2]);

            Calendar dob = Calendar.getInstance();
            dob.set(year, month - 1, day); // Month is 0-based

            Calendar today = Calendar.getInstance();

            int age = today.get(Calendar.YEAR) - dob.get(Calendar.YEAR);

            // Adjust if birthday hasn't occurred yet this year
            if (today.get(Calendar.MONTH) < dob.get(Calendar.MONTH) ||
                (today.get(Calendar.MONTH) == dob.get(Calendar.MONTH) &&
                 today.get(Calendar.DAY_OF_MONTH) < dob.get(Calendar.DAY_OF_MONTH))) {
                age--;
            }

            return age;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Checks if the 'current' date is strictly between start and end.
     * Useful for Promo duration checks.
     */
    public static boolean isDateWithinRange(Date current, Date start, Date end) {
        if (current == null) return false;

        // If start is null, assume it started infinite time ago.
        // If end is null, assume it never ends.
        boolean afterStart = (start == null) || !current.before(start);
        boolean beforeEnd = (end == null) || !current.after(end);

        return afterStart && beforeEnd;
    }

    /**
     * Calculates the number of days between two dates.
     * Useful for "Inactive for X days" logic.
     */
    public static long getDaysDifference(Date recent, Date old) {
        if (recent == null || old == null) return 0;
        long diffMs = recent.getTime() - old.getTime();
        return TimeUnit.DAYS.convert(diffMs, TimeUnit.MILLISECONDS);
    }
}
