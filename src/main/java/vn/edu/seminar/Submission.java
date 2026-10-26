package vn.edu.seminar;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
public record Submission(String id, String submittedAt, String studentSub, String studentName,
        String studentEmail, String groupName, String topic, String lecturerEmail,
        String fileId, String fileName, String fileUrl, long fileSize, String phase, boolean ccSelf, String downloadUrl) {
    public Submission(String id, String submittedAt, String studentSub, String studentName,
            String studentEmail, String groupName, String topic, String lecturerEmail,
            String fileId, String fileName, String fileUrl, long fileSize, String phase, boolean ccSelf) {
        this(id, submittedAt, studentSub, studentName, studentEmail, groupName, topic, lecturerEmail,
            fileId, fileName, fileUrl, fileSize, phase, ccSelf, "");
    }
    public boolean ready() { return "READY".equals(phase); }
    public boolean uploaded() { return !fileId.isBlank(); }
    public String safeFileUrl() { return AppSettings.validId(fileId) ? "https://drive.google.com/file/d/" + fileId + "/view" : ""; }
    public boolean zip() { return fileName.toLowerCase(Locale.ROOT).endsWith(".zip"); }
    public String safeDownloadUrl() {
        if (!AppSettings.validId(fileId) || downloadUrl == null || downloadUrl.isBlank()) return "";
        try {
            var uri = URI.create(downloadUrl);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && Set.of("drive.google.com", "drive.usercontent.google.com").contains(uri.getHost().toLowerCase(Locale.ROOT))
                    && uri.getRawUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443)
                ? uri.toASCIIString() : "";
        } catch (IllegalArgumentException exception) { return ""; }
    }
    public String fileActionUrl() { return zip() && !safeDownloadUrl().isBlank() ? safeDownloadUrl() : safeFileUrl(); }
    public String fileActionLabel() {
        return zip() ? (safeDownloadUrl().isBlank() ? "Mở Drive để tải ZIP ↗" : "Tải file ZIP ↓") : "Xem tài liệu ↗";
    }
    public String shortId() { return id.substring(0, Math.min(8, id.length())).toUpperCase(Locale.ROOT); }
    public String sizeLabel() { return String.format(Locale.ROOT, "%.2f MB", fileSize / 1048576d); }
}
