package ca.vicilearning.dashboard.rules;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represent an administrative follow-up action flagged by the Vici Rules Engine.
 * * DESIGN REALITY (Brevo Free Sandbox Tier Constraints):
 * Because Brevo operates on a flat Contact model (one row per parent email) and lacks 
 * Custom Relational Objects, multi-child metrics must be flattened into text arrays.
 * * This record takes true Java collections (Lists) and exposes them as pipe-delimited 
 * strings ("|"), creating synchronized parallel arrays across Brevo custom attributes:
 * Index [0] -> Child A's Name | Child A's Last Booking Date
 * Index [1] -> Child B's Name | Child B's Last Booking Date
 */
public record BrevoReviewTask(
    Long id,
    String familyName,
    String email,
    String viciAccountId,
    String triggerReason,
    Long templateId,
    String paymentStatus,          // Credit-agnostic replacement tracking billing states
    List<String> studentNames,     // Handles 1-to-Many student mappings natively
    List<LocalDate> bookingDates   // Positional index matching studentNames list
) {
    
    /**
     * Collapses list of students into a single CRM-compatible text field.
     * @return Delimited string e.g., "Alex Student | Emma Student"
     */
    public String getFlattenedStudentNames() {
        if (studentNames == null || studentNames.isEmpty()) return "";
        return String.join(" | ", studentNames);
    }

    /**
     * Collapses parallel student booking timelines into single ISO date text segments.
     * @return Delimited string e.g., "2026-05-24 | 2026-06-06"
     */
    public String getFlattenedBookingDates() {
        if (bookingDates == null || bookingDates.isEmpty()) return "";
        return bookingDates.stream()
            .map(LocalDate::toString)
            .collect(Collectors.joining(" | "));
    }
}