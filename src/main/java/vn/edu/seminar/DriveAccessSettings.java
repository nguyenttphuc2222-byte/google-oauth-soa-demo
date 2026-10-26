package vn.edu.seminar;

import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("drive-access")
public record DriveAccessSettings(Mode mode, String ownerEmail, String tokenKey) {
    public enum Mode { MANUAL, ISOLATED, SHARED }
    public DriveAccessSettings {
        if (mode == null) mode = Mode.MANUAL;
        ownerEmail = AccountPolicy.normalize(ownerEmail);
        tokenKey = tokenKey == null ? "" : tokenKey.trim();
        if (!ownerEmail.isEmpty()) AccountPolicy.email(ownerEmail);
    }
    public boolean automatic() { return mode != Mode.MANUAL; }
    public byte[] keyBytes() {
        try {
            byte[] key = Base64.getDecoder().decode(tokenKey);
            if (key.length == 32) return key;
        } catch (IllegalArgumentException ignored) { }
        throw new IllegalStateException("Cần cấu hình DRIVE_ADMIN_TOKEN_KEY là khóa ngẫu nhiên 32 byte dạng Base64 theo README.");
    }
    public boolean keyReady() { try { keyBytes(); return true; } catch (IllegalStateException missing) { return false; } }
    @Override public String toString() { return "DriveAccessSettings[mode=" + mode + ", ownerEmail=" + ownerEmail + ", tokenKey=REDACTED]"; }
}
