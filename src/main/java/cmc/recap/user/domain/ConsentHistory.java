package cmc.recap.user.domain;

import cmc.recap.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "consent_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsentHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private ConsentAction action;

    public static ConsentHistory give(User user) {
        ConsentHistory history = new ConsentHistory();
        history.user = user;
        history.action = ConsentAction.GIVEN;
        return history;
    }

    public static ConsentHistory withdraw(User user) {
        ConsentHistory history = new ConsentHistory();
        history.user = user;
        history.action = ConsentAction.WITHDRAWN;
        return history;
    }
}
