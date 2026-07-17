package ca.vicilearning.dashboard.association;

/**
 * Normalizes the family <b>Account_ID</b> key so the same family matches across systems even when
 * the two sides spell it differently.
 *
 * <p>Background (Meeting #4): the family identifier lives in SimplyBook.me as a {@code Surname_Account}
 * custom field, and in Brevo as a <b>Company name</b> that staff type by hand — so the Brevo side may
 * read {@code "Gray"} while SimplyBook reads {@code "Gray_Account"}. A live probe of Brevo's company
 * schema confirmed there is no dedicated id field: the family name is free text in {@code attributes.name}.
 * We therefore can't assume a format and must match tolerantly on both sides.
 *
 * <ul>
 *   <li>{@link #compareKey(String)} — the case-insensitive, suffix-stripped key used to decide whether
 *       two account ids denote the same family ({@code "Gray"}, {@code "gray"}, {@code "Gray_Account"}
 *       all share the key {@code "gray"}).</li>
 *   <li>{@link #canonical(String)} — the stored form, with the {@code _Account} suffix ensured, used
 *       when we have to mint a brand-new family key from a Brevo company name.</li>
 * </ul>
 */
public final class AccountIdNormalizer {

    private static final String SUFFIX = "_Account";
    private static final String SUFFIX_LOWER = "_account";

    private AccountIdNormalizer() {
    }

    /**
     * Comparison key for two-sided matching: trimmed, lower-cased, with a single trailing
     * {@code _account} suffix removed. Null or blank input yields {@code ""} (matches nothing via
     * {@link #matches}).
     */
    public static String compareKey(String accountId) {
        if (accountId == null) {
            return "";
        }
        String v = accountId.trim().toLowerCase();
        if (v.endsWith(SUFFIX_LOWER)) {
            v = v.substring(0, v.length() - SUFFIX_LOWER.length());
        }
        return v.trim();
    }

    /**
     * Canonical stored form: the value with a {@code _Account} suffix ensured (case-insensitively),
     * preserving the original casing of the surname core. Null or blank input yields {@code null}.
     */
    public static String canonical(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return null;
        }
        String v = accountId.trim();
        if (v.toLowerCase().endsWith(SUFFIX_LOWER)) {
            return v;
        }
        return v + SUFFIX;
    }

    /** True when both ids denote the same family (share a non-empty {@link #compareKey}). */
    public static boolean matches(String a, String b) {
        String keyA = compareKey(a);
        return !keyA.isEmpty() && keyA.equals(compareKey(b));
    }
}
