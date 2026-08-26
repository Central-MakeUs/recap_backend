package cmc.recap.user.service;

import cmc.recap.card.repository.InfoCardRepository;
import cmc.recap.global.exception.ErrorCode;
import cmc.recap.global.exception.model.BusinessException;
import cmc.recap.user.domain.User;
import cmc.recap.user.dto.response.UsageResponse;
import cmc.recap.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UsageService {

    private static final int MONTHLY_LIMIT = 20;

    private final UserRepository userRepository;
    private final InfoCardRepository infoCardRepository;

    public UsageResponse getUsage(Long userId) {
        User user = userRepository.getReferenceById(userId);
        int usedCount = countThisMonth(user);
        return UsageResponse.of(usedCount, MONTHLY_LIMIT, resetAt());
    }

    public void checkLimit(Long userId, int requestCount) {
        User user = userRepository.getReferenceById(userId);
        int usedCount = countThisMonth(user);
        if (usedCount + requestCount > MONTHLY_LIMIT) {
            throw new BusinessException(ErrorCode.MONTHLY_USAGE_LIMIT_EXCEEDED);
        }
    }

    private int countThisMonth(User user) {
        return (int) infoCardRepository.countByUserAndCreatedAtAfter(user, monthStart());
    }

    private Instant monthStart() {
        return LocalDate.now(ZoneOffset.UTC)
                .withDayOfMonth(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();
    }

    private Instant resetAt() {
        return LocalDate.now(ZoneOffset.UTC)
                .withDayOfMonth(1)
                .plusMonths(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();
    }
}
