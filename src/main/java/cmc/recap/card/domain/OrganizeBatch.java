package cmc.recap.card.domain;

import cmc.recap.global.entity.BaseTimeEntity;
import cmc.recap.user.domain.User;
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
@Table(name = "organize_batches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrganizeBatch extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BatchStatus status;

    @Column(name = "total_count", nullable = false)
    private int totalCount;

    @Column(name = "success_count", nullable = false)
    private int successCount;

    @Column(name = "fail_count", nullable = false)
    private int failCount;

    @Column(name = "acknowledged", nullable = false)
    private boolean acknowledged;

    public static OrganizeBatch start(User user, int totalCount) {
        OrganizeBatch batch = new OrganizeBatch();
        batch.user = user;
        batch.status = BatchStatus.PROCESSING;
        batch.totalCount = totalCount;
        batch.successCount = 0;
        batch.failCount = 0;
        batch.acknowledged = false;
        return batch;
    }

    /** 이미지 1건 처리 완료 시 호출. 개별 커밋 아키텍처의 핵심 메서드. */
    public void recordSuccess() {
        this.successCount++;
        refreshStatusIfDone();
    }

    public void recordFailure() {
        this.failCount++;
        refreshStatusIfDone();
    }

    public void cancel() {
        if (this.status == BatchStatus.PROCESSING) {
            this.status = BatchStatus.CANCELLED;
        }
    }

    public void acknowledge() {
        this.acknowledged = true;
    }

    private void refreshStatusIfDone() {
        if (this.status == BatchStatus.CANCELLED) {
            return; // 취소된 배치는 상태를 되돌리지 않음
        }
        if (successCount + failCount < totalCount) {
            return; // 아직 처리 중
        }
        if (failCount == 0) {
            this.status = BatchStatus.COMPLETED;
        } else if (successCount == 0) {
            this.status = BatchStatus.FAILED;
        } else {
            this.status = BatchStatus.PARTIAL_FAILED;
        }
    }
}
