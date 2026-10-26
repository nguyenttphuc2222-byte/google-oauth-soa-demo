package vn.edu.seminar;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DriveSecretBoxTest {
    static final String KEY=Base64.getEncoder().encodeToString(new byte[32]);
    @Test void refreshTokenIsEncryptedWithRandomNonceAndBoundToCredentialMetadata(){
        var box=new DriveSecretBox(new DriveAccessSettings(DriveAccessSettings.Mode.ISOLATED,AutoDriveGoogle.OWNER,KEY));
        String first=box.seal("sensitive-refresh-token","owner|root|client|mode"),second=box.seal("sensitive-refresh-token","owner|root|client|mode");
        assertThat(first).doesNotContain("sensitive-refresh-token").isNotEqualTo(second);
        assertThat(box.open(first,"owner|root|client|mode")).isEqualTo("sensitive-refresh-token");
        assertThatThrownBy(()->box.open(first,"different-root")).isInstanceOf(IllegalStateException.class);
        byte[] alternate=new byte[32];alternate[0]=1;
        var wrong=new DriveSecretBox(new DriveAccessSettings(DriveAccessSettings.Mode.ISOLATED,AutoDriveGoogle.OWNER,Base64.getEncoder().encodeToString(alternate)));
        assertThatThrownBy(()->wrong.open(first,"owner|root|client|mode")).isInstanceOf(IllegalStateException.class);
    }
    @Test void missingOrShortKeysCannotEnableUnencryptedStorage(){
        for(String key:new String[]{"","not-base64",Base64.getEncoder().encodeToString(new byte[16])}){
            var settings=new DriveAccessSettings(DriveAccessSettings.Mode.ISOLATED,AutoDriveGoogle.OWNER,key);
            assertThat(settings.keyReady()).isFalse();
            assertThatThrownBy(()->new DriveSecretBox(settings).seal("token","context")).isInstanceOf(IllegalStateException.class);
            assertThat(settings.toString()).doesNotContain("tokenKey="+key+"]");
        }
    }
}
