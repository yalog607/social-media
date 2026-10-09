package com.aloute.user;

import com.aloute.util.user.PasswordPolicy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyTest {

    @Test
    void acceptsLettersAndDigits() {
        assertThat(PasswordPolicy.validate("Aloute123")).isNull();
        assertThat(PasswordPolicy.validate("mật khẩu 2026")).isNull();
    }

    @Test
    void rejectsTooShortMissingDigitOrMissingLetter() {
        assertThat(PasswordPolicy.validate("a1")).contains("ít nhất");
        assertThat(PasswordPolicy.validate("chiletters")).contains("chữ và số");
        assertThat(PasswordPolicy.validate("12345678")).contains("chữ và số");
        assertThat(PasswordPolicy.validate(null)).isNotNull();
    }

    @Test
    void rejectsPasswordsOverBcryptLimitEvenWhenFewChars() {
        // 32 ký tự (chưa tới 72) nhưng mỗi "€" là 3 byte: 2 + 30 × 3 = 92 byte > 72
        String tooLong = "a1" + "€".repeat(30);

        assertThat(tooLong.length()).isLessThan(PasswordPolicy.MAX_BYTES);
        assertThat(PasswordPolicy.validate(tooLong)).contains("quá dài");
    }
}
