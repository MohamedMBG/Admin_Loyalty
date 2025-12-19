package com.example.adminloyalty.utils;

import java.util.Calendar;

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
}
