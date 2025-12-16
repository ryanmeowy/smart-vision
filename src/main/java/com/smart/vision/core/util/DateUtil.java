package com.smart.vision.core.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Date util class providing common date formatting operations
 * This utility class contains static methods and constants for handling date formatting
 * operations throughout the application. It provides a standardized way to format
 * dates consistently across different components.
 *
 * @author Ryan
 * @since 2025/12/15
 */
public class DateUtil {
    /**
     * Standard date formatter using yyyy/MM/dd pattern
     */
    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /**
     * Formats the current date using the provided DateTimeFormatter
     */
    public static String fetchFormatTime(LocalDate date, DateTimeFormatter formatter) {
        return date.format(formatter);
    }
}
