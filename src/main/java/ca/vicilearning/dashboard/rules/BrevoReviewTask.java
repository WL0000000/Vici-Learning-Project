package ca.vicilearning.dashboard.rules;

public record BrevoReviewTask(
    Long id,
    String familyName,
    String email,
    String triggerReason,
    Long templateId
) {}