package com.aloute.service.auth;

import com.aloute.model.user.AuthProvider;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FirebaseTokenVerifierTest {
    @Test
    void acceptsGoogleProvider() {
        assertThat(FirebaseTokenVerifier.providerOf(Map.of("firebase", Map.of("sign_in_provider", "google.com"))))
                .isEqualTo(AuthProvider.GOOGLE);
    }

    @Test
    void rejectsFacebookOtherAndMissingProviders() {
        for (Map<String, Object> claims : java.util.List.<Map<String, Object>>of(
                Map.of("firebase", Map.of("sign_in_provider", "facebook.com")),
                Map.of("firebase", Map.of("sign_in_provider", "password")),
                Map.of("firebase", Map.of()), Map.of())) {
            assertThatThrownBy(() -> FirebaseTokenVerifier.providerOf(claims))
                    .isInstanceOf(SocialTokenVerifier.InvalidSocialTokenException.class);
        }
    }
}
