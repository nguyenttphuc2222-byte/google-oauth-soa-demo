package com.example.googleoauthspring;

import java.util.Locale;
import java.util.Set;

/** Chính sách dùng sau khi Spring Security xác thực danh tính Google. */
public final class TdtuRolePolicy {

    private static final Set<String> HOSTED_DOMAINS =
            Set.of("tdtu.edu.vn", "student.tdtu.edu.vn");

    private TdtuRolePolicy() {
    }

    public static String roleFor(
            String email, Boolean emailVerified, String hostedDomain) {
        if (!Boolean.TRUE.equals(emailVerified)
                || email == null || hostedDomain == null) {
            throw new IllegalArgumentException("Verified TDTU identity required");
        }
        String domain = hostedDomain.trim().toLowerCase(Locale.ROOT);
        if (!HOSTED_DOMAINS.contains(domain)) {
            throw new IllegalArgumentException("TDTU hosted domain required");
        }

        String[] parts = email.trim().toLowerCase(Locale.ROOT).split("@", -1);
        if (parts.length != 2 || parts[0].isBlank()) {
            throw new IllegalArgumentException("Invalid email identity");
        }
        return switch (parts[1]) {
            case "tdtu.edu.vn" -> "LECTURER";
            case "student.tdtu.edu.vn" -> "STUDENT";
            default -> throw new IllegalArgumentException("TDTU email required");
        };
    }
}
