package vn.edu.seminar;
import java.util.*;
public final class Scopes {
    private Scopes() {}
    public static final String DRIVE = "https://www.googleapis.com/auth/drive.file";
    public static final String MAIL = "https://www.googleapis.com/auth/gmail.send";
    public static final Set<String> IDENTITY = Set.of("openid", "profile", "email");
    public static Set<String> requested(Set<String> previous, String purpose) {
        var result = new LinkedHashSet<>(IDENTITY);
        previous.stream().filter(s -> IDENTITY.contains(s) || s.equals(DRIVE) || s.equals(MAIL)).forEach(result::add);
        switch (purpose) {
            case "submit" -> result.addAll(Set.of(DRIVE, MAIL));
            case "review" -> result.add(MAIL);
            case "drive_admin" -> result.add(DRIVE);
            default -> { }
        }
        return result;
    }
    public static boolean includes(Set<String> granted, String required) { return granted.contains(required); }
    public static class Missing extends RuntimeException {
        public final String purpose;
        public Missing(String purpose) { super("Chưa đủ quyền Google. Hãy cấp thêm quyền cho chức năng này."); this.purpose = purpose; }
    }
}
