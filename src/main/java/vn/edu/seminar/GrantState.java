package vn.edu.seminar;
import java.time.Instant;
import java.util.Set;
import org.springframework.security.core.Authentication;
public final class GrantState {
    public static final String PENDING="seminar.pendingGrant",LAST="seminar.lastGrant";
    public record Pending(String purpose,String subject,String returnTo,Instant at,Authentication previous){}
    public record Snapshot(String purpose,Set<String> requested,boolean state,boolean pkce,boolean nonce){}
}
