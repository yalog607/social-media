package com.aloute.service.auth;

import com.aloute.dto.auth.SocialIdentity;

import com.aloute.model.user.AuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;

import java.util.Map;

/** Xác thực ID token bằng Firebase Admin SDK (kiểm tra chữ ký, hạn dùng, audience). */
public class FirebaseTokenVerifier implements SocialTokenVerifier {

    private final FirebaseAuth firebaseAuth;

    public FirebaseTokenVerifier(FirebaseAuth firebaseAuth) {
        this.firebaseAuth = firebaseAuth;
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public SocialIdentity verify(String idToken) {
        try {
            FirebaseToken token = firebaseAuth.verifyIdToken(idToken);
            return new SocialIdentity(token.getUid(), token.getEmail(), token.isEmailVerified(),
                    token.getName(), token.getPicture(), providerOf(token));
        } catch (FirebaseAuthException | IllegalArgumentException e) {
            throw new InvalidSocialTokenException("ID token không hợp lệ", e);
        }
    }

    private static AuthProvider providerOf(FirebaseToken token) {
        Object firebase = token.getClaims().get("firebase");
        if (firebase instanceof Map<?, ?> claim) {
            Object signInProvider = claim.get("sign_in_provider");
            if ("facebook.com".equals(signInProvider)) {
                return AuthProvider.FACEBOOK;
            }
        }
        return AuthProvider.GOOGLE;
    }
}
