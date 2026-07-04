package cmc.recap.user.domain;

import cmc.recap.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, unique = true)
    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false)
    private Platform platform;

    @Column(name = "fcm_token")
    private String fcmToken;

    @Column(name = "email")
    private String email;

    @Column(name = "oauth_provider")
    private String oauthProvider;

    @Column(name = "oauth_id")
    private String oauthId;

    private User(String deviceId, Platform platform) {
        this.deviceId = deviceId;
        this.platform = platform;
    }

    public static User createByDevice(String deviceId, Platform platform) {
        return new User(deviceId, platform);
    }

    public void linkOauth(String email, String oauthProvider, String oauthId) {
        if (this.oauthId != null) {
            throw new IllegalStateException("이미 소셜 계정이 연결된 유저입니다.");
        }
        this.email = email;
        this.oauthProvider = oauthProvider;
        this.oauthId = oauthId;
    }

    public void updateFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }
}
