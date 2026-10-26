package vn.edu.seminar;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class DriveSecretBox {
    private final DriveAccessSettings settings;
    private final SecureRandom random = new SecureRandom();
    public DriveSecretBox(DriveAccessSettings settings) { this.settings = settings; }
    public String seal(String value, String context) {
        byte[] nonce = new byte[12]; random.nextBytes(nonce);
        try {
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(settings.keyBytes(), "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return "v1." + Base64.getUrlEncoder().withoutPadding().encodeToString(ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array());
        } catch (GeneralSecurityException exception) { throw new IllegalStateException("Không mã hóa được quyền Drive lưu trên server."); }
    }
    public String open(String value, String context) {
        try {
            if (!value.startsWith("v1.")) throw new IllegalArgumentException();
            var buffer = ByteBuffer.wrap(Base64.getUrlDecoder().decode(value.substring(3)));
            if (buffer.remaining() < 28) throw new IllegalArgumentException();
            byte[] nonce = new byte[12]; buffer.get(nonce); byte[] encrypted = new byte[buffer.remaining()]; buffer.get(encrypted);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(settings.keyBytes(), "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Không mở được quyền Drive đã lưu. Kiểm tra khóa mã hóa hoặc để chủ thư mục kết nối lại.");
        }
    }
}
