package com.team.study.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private static final String SECRET =
            "RevMateTestSecretKeyForUnitTestingMustBeAtLeast256BitsLong!!";

    private final JwtTokenProvider provider = new JwtTokenProvider(SECRET, 86_400_000L);

    @Test
    void generatedTokenValidatesAndCarriesUserId() {
        String token = provider.generateToken(42L, "alice");

        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.getUserIdFromToken(token)).isEqualTo(42L);
    }

    @Test
    void malformedTokenFailsValidation() {
        assertThat(provider.validateToken("not-a-jwt")).isFalse();
    }

    @Test
    void expiredTokenFailsValidation() {
        JwtTokenProvider shortLived = new JwtTokenProvider(SECRET, -1_000L);
        String token = shortLived.generateToken(1L, "bob");

        assertThat(shortLived.validateToken(token)).isFalse();
    }
}
