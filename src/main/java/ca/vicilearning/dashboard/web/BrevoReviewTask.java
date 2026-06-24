package ca.vicilearning.dashboard.web;

public record BrevoReviewTask(
    Long id,
    String familyName,
    String email,
    String triggerReason,
    Long templateId
) {}