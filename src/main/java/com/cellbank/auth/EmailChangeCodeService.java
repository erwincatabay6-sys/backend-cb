package com.cellbank.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmailChangeCodeService {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec secretKey;

    public EmailChangeCodeService(
            @Value("${CELLBANK_EMAIL_CHANGE_OTP_KEY}") String encodedKey) {

        byte[] keyBytes = Base64.getDecoder().decode(encodedKey.strip());

        if (keyBytes.length != 32) {
            throw new IllegalArgumentException(
                    "CELLBANK_EMAIL_CHANGE_OTP_KEY must decode to 32 bytes."
            );
        }

        this.secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
    }

    public String generateCode() {
        return String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
    }

    public String hashCodeForChange(
            long userId, String proposedEmail, String code) {

        String input = userId + "\n" + proposedEmail + "\n" + code;

        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(secretKey);

            byte[] hash = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "Unable to compute the email change code hash.", exception
            );
        }
    }

    public boolean matches(PendingEmailChange change, String submittedCode) {
        if (submittedCode == null || !submittedCode.matches("[0-9]{6}")) {
            return false;
        }

        String candidateHash = hashCodeForChange(
                change.getUserId(),
                change.getProposedEmail(),
                submittedCode
        );

        return MessageDigest.isEqual(
                change.getCodeHash().getBytes(StandardCharsets.US_ASCII),
                candidateHash.getBytes(StandardCharsets.US_ASCII)
        );
    }
}