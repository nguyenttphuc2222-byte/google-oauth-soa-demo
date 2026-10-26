package vn.edu.seminar;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties("app")
public record AppSettings(String clientId, String clientSecret, String redirectUri, String baseUrl,
        String apiKey, String projectNumber, String folderId, String studentEmails, String lecturerEmails,
        String allowedWorkspaceDomains, boolean autoStudentLogin, boolean autoDomainRoles, String lecturerOverrideEmail,
        int maxUploadMb, String displayTimezone, Google google) {
    public AppSettings {
        URI uri = URI.create(baseUrl);
        if (!java.util.Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || (uri.getPath() != null && !uri.getPath().isEmpty() && !uri.getPath().equals("/")))
            throw new IllegalArgumentException("APP_BASE_URL phải là địa chỉ gốc http(s), không có path/query.");
        baseUrl = baseUrl.replaceAll("/+$", "");
        if (maxUploadMb < 1 || maxUploadMb > 50) throw new IllegalArgumentException("MAX_UPLOAD_MB phải từ 1 đến 50.");
    }
    public record Google(String authorizationUri, String tokenUri, String jwkSetUri, String userInfoUri,
            String issuerUri, String revokeUri, String driveBase, String uploadBase, String gmailBase) {}
    public boolean credentialsReady() {
        return clientId != null && !clientId.isBlank() && !clientId.startsWith("configure-")
                && clientSecret != null && !clientSecret.isBlank() && !clientSecret.startsWith("configure-");
    }
    public boolean resourcesReady() { return validId(folderId); }
    public String submissionUrl(String id) { return baseUrl + "/submissions/" + id; }
    public static boolean validId(String value) { return value != null && value.matches("[A-Za-z0-9_-]{8,200}"); }
}
