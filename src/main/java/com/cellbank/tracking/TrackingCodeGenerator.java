package com.cellbank.tracking;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

@Component
public class TrackingCodeGenerator {

    private static final String ALPHABET =
            "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    private static final int CODE_LENGTH = 8;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder code = new StringBuilder(CODE_LENGTH + 1);

        for (int index = 0; index < CODE_LENGTH; index++) {
            if (index == 4) {
                code.append('-');
            }

            code.append(
                    ALPHABET.charAt(random.nextInt(ALPHABET.length()))
            );
        }

        return code.toString();
    }
}