package com.example.googleoauthspring;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StudentApiClient {

    private final RestClient client;

    public StudentApiClient(
            @Value("${student.service.url}") String baseUrl,
            @Value("${student.service.key}") String serviceKey) {

        var httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(5));

        client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("X-Service-Key", serviceKey)
                .build();
    }

    public static String roleOf(OidcUser user) {
        if (user.getAuthorities().stream().anyMatch(
                authority -> "ROLE_STUDENT".equals(
                        authority.getAuthority()))) {
            return "STUDENT";
        }

        if (user.getAuthorities().stream().anyMatch(
                authority -> "ROLE_LECTURER".equals(
                        authority.getAuthority()))) {
            return "LECTURER";
        }

        throw new AccessDeniedException("TDTU role required");
    }

    private void requireRole(OidcUser user, String expected) {
        if (!expected.equals(roleOf(user))) {
            throw new AccessDeniedException(
                    "You do not have permission");
        }
    }

    private void identity(HttpHeaders headers, OidcUser user) {
        headers.set("X-Login-Email", user.getEmail());
        headers.set("X-User-Role", roleOf(user));
    }

    public StudentProfile ensureProfile(OidcUser user) {
        requireRole(user, "STUDENT");

        String name = user.getFullName();
        String fullName = name == null || name.isBlank()
                ? "Sinh viên Demo"
                : name;

        return call(() -> client.post()
                .uri("/students/me/ensure")
                .headers(headers -> identity(headers, user))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("full_name", fullName))
                .retrieve()
                .body(StudentProfile.class));
    }

    public StudentProfile ownProfile(OidcUser user) {
        requireRole(user, "STUDENT");

        return call(() -> client.get()
                .uri("/students/me")
                .headers(headers -> identity(headers, user))
                .retrieve()
                .body(StudentProfile.class));
    }

    public StudentProfile updateProfile(
            OidcUser user, Map<String, Object> payload) {

        requireRole(user, "STUDENT");

        return call(() -> client.put()
                .uri("/students/me")
                .headers(headers -> identity(headers, user))
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(StudentProfile.class));
    }

    public List<StudentProfile.Summary> search(
            OidcUser user, String q) {

        requireRole(user, "LECTURER");

        return call(() -> client.get()
                .uri(builder -> builder.path("/students")
                        .queryParam("q", "{query}")
                        .build(q))
                .headers(headers -> identity(headers, user))
                .retrieve()
                .body(new ParameterizedTypeReference<
                        List<StudentProfile.Summary>>() {}));
    }

    public StudentProfile details(
            OidcUser user, String mssv) {

        requireRole(user, "LECTURER");

        return call(() -> client.get()
                .uri("/students/{mssv}", mssv)
                .headers(headers -> identity(headers, user))
                .retrieve()
                .body(StudentProfile.class));
    }

    private <T> T call(Supplier<T> request) {
        try {
            T result = request.get();

            if (result == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Không thể tải dữ liệu hồ sơ. Vui lòng thử lại.");
            }

            return result;

        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();

            if (status == 404) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy sinh viên.",
                        exception);
            }

            if (status == 422) {
                throw new ResponseStatusException(
                        HttpStatusCode.valueOf(422),
                        "Thông tin chưa hợp lệ. Kiểm tra họ tên, tuổi, "
                                + "giới tính, số điện thoại, địa chỉ và email.",
                        exception);
            }

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Không thể tải dữ liệu hồ sơ. Vui lòng thử lại.",
                    exception);

        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Tạm thời không thể kết nối dịch vụ hồ sơ. "
                            + "Vui lòng thử lại.",
                    exception);
        }
    }
}