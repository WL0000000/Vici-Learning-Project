package ca.vicilearning.dashboard.notion;

public record NotionTutor(
        String id,
        String name,
        String email,
        String subject,
        String status,
        String url
) {}
