package com.ucentral.desarrollos.backendparkspotter.shared.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecureTokensTest {

    @Test
    void tokensCodesHashesAndComparison() {
        assertThat(SecureTokens.randomToken(32)).hasSizeGreaterThan(40).doesNotContain("=");
        assertThat(SecureTokens.randomCode(6)).hasSize(6).doesNotContain("0", "O", "1", "I");
        assertThat(SecureTokens.sha256Hex("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(SecureTokens.sha256Base64("abc")).isNotBlank();
        assertThat(SecureTokens.constantTimeEquals("a", "a")).isTrue();
        assertThat(SecureTokens.constantTimeEquals("a", "b")).isFalse();
        assertThat(SecureTokens.constantTimeEquals(null, "b")).isFalse();
        assertThat(SecureTokens.constantTimeEquals("a", null)).isFalse();
    }
}
