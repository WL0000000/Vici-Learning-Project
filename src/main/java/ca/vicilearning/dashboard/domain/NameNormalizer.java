package ca.vicilearning.dashboard.domain;

public final class NameNormalizer {

    private NameNormalizer() {}

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String collapsed = value.replaceAll("[\\s\\u00A0]+", " ").trim();
        return collapsed.isEmpty() ? null : collapsed;
    }
}
