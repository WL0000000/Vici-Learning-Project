package ca.vicilearning.dashboard.association;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccountIdNormalizerTest {

    @Test
    void compareKey_ignoresCaseAndAccountSuffix() {
        // The whole point: a Brevo company "Gray" and a SimplyBook "Gray_Account" are one family.
        assertThat(AccountIdNormalizer.compareKey("Gray")).isEqualTo("gray");
        assertThat(AccountIdNormalizer.compareKey("gray")).isEqualTo("gray");
        assertThat(AccountIdNormalizer.compareKey("Gray_Account")).isEqualTo("gray");
        assertThat(AccountIdNormalizer.compareKey("  GRAY_account  ")).isEqualTo("gray");
    }

    @Test
    void compareKey_stripsOnlyTheAccountSuffix_notSuffixLikeTails() {
        // A de-duplicated key such as "Tran_Account2" is a genuinely different family: the suffix
        // is "_Account2", not "_Account", so it must NOT collapse onto "tran".
        assertThat(AccountIdNormalizer.compareKey("Tran_Account2")).isEqualTo("tran_account2");
        assertThat(AccountIdNormalizer.compareKey("Tran_Account")).isEqualTo("tran");
    }

    @Test
    void compareKey_blankOrNull_isEmpty() {
        assertThat(AccountIdNormalizer.compareKey(null)).isEmpty();
        assertThat(AccountIdNormalizer.compareKey("   ")).isEmpty();
    }

    @Test
    void canonical_ensuresAccountSuffix_preservingCoreCasing() {
        assertThat(AccountIdNormalizer.canonical("Gray")).isEqualTo("Gray_Account");
        assertThat(AccountIdNormalizer.canonical("McLeod")).isEqualTo("McLeod_Account");
        // Already suffixed (any casing) is left as-is — we don't restyle staff-entered keys.
        assertThat(AccountIdNormalizer.canonical("Gray_Account")).isEqualTo("Gray_Account");
        assertThat(AccountIdNormalizer.canonical("gray_account")).isEqualTo("gray_account");
    }

    @Test
    void canonical_blankOrNull_isNull() {
        assertThat(AccountIdNormalizer.canonical(null)).isNull();
        assertThat(AccountIdNormalizer.canonical("  ")).isNull();
    }

    @Test
    void matches_isTrueAcrossSpellings_falseAcrossFamilies() {
        assertThat(AccountIdNormalizer.matches("Gray", "Gray_Account")).isTrue();
        assertThat(AccountIdNormalizer.matches("gray_account", "GRAY")).isTrue();
        assertThat(AccountIdNormalizer.matches("Gray", "Tran_Account")).isFalse();
        // Blank never matches, even against another blank.
        assertThat(AccountIdNormalizer.matches("", "")).isFalse();
        assertThat(AccountIdNormalizer.matches(null, "Gray")).isFalse();
    }
}
