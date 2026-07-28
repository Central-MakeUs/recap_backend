package cmc.recap.user.service;

import cmc.recap.global.exception.ErrorCode;
import cmc.recap.global.exception.model.BusinessException;
import cmc.recap.user.domain.ConsentAction;
import cmc.recap.user.domain.ConsentHistory;
import cmc.recap.user.domain.User;
import cmc.recap.user.dto.response.ConsentStatusResponse;
import cmc.recap.user.repository.ConsentHistoryRepository;
import cmc.recap.user.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConsentService {

    private final UserRepository userRepository;
    private final ConsentHistoryRepository consentHistoryRepository;

    public ConsentStatusResponse getStatus(Long userId) {
        User user = getUser(userId);
        return ConsentStatusResponse.from(latestHistory(user));
    }

    @Transactional
    public void give(Long userId) {
        User user = getUser(userId);
        consentHistoryRepository.save(ConsentHistory.give(user));
    }

    @Transactional
    public void withdraw(Long userId) {
        User user = getUser(userId);
        consentHistoryRepository.save(ConsentHistory.withdraw(user));
    }

    public boolean hasActiveConsent(Long userId) {
        User user = getUser(userId);
        return latestHistory(user)
                .map(h -> h.getAction() == ConsentAction.GIVEN)
                .orElse(false);
    }

    private Optional<ConsentHistory> latestHistory(User user) {
        return consentHistoryRepository.findFirstByUserOrderByCreatedAtDesc(user);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
}
