package cmc.recap.user.service;

import cmc.recap.auth.repository.RefreshTokenRepository;
import cmc.recap.card.domain.InfoCard;
import cmc.recap.card.repository.InfoCardRepository;
import cmc.recap.global.exception.ErrorCode;
import cmc.recap.global.exception.model.BusinessException;
import cmc.recap.user.domain.User;
import cmc.recap.user.repository.UserRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final InfoCardRepository infoCardRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final S3Client s3Client;
    private final String bucketName;

    public UserService(
            UserRepository userRepository,
            InfoCardRepository infoCardRepository,
            RefreshTokenRepository refreshTokenRepository,
            S3Client s3Client,
            @Value("${aws.s3.bucket-name}") String bucketName) {
        this.userRepository = userRepository;
        this.infoCardRepository = infoCardRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        deleteInfoCards(user);
        refreshTokenRepository.deleteByUser(user);
        user.withdraw();
    }

    private void deleteInfoCards(User user) {
        List<InfoCard> cards = infoCardRepository.findByUser(user);
        if (cards.isEmpty()) {
            return;
        }
        List<ObjectIdentifier> objectIds = cards.stream()
                .map(card -> ObjectIdentifier.builder().key(card.getOriginalImageKey()).build())
                .toList();
        try {
            s3Client.deleteObjects(DeleteObjectsRequest.builder()
                    .bucket(bucketName)
                    .delete(Delete.builder().objects(objectIds).build())
                    .build());
        } catch (SdkException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, e);
        }
        infoCardRepository.deleteAll(cards);
    }
}
