package cmc.recap.card.service;

import cmc.recap.card.dto.response.UploadUrlsResponse;
import cmc.recap.card.dto.response.UploadUrlsResponse.UploadItem;
import cmc.recap.card.image.CaptureObjectKeyGenerator;
import cmc.recap.card.image.ImagePresignedUrlProvider;
import cmc.recap.card.image.PresignedUploadInfo;
import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CaptureService {

    private final ImagePresignedUrlProvider imagePresignedUrlProvider;

    public UploadUrlsResponse issueUploadUrls(Long userId, int count) {
        List<UploadItem> uploads = IntStream.range(0, count)
                .mapToObj(i -> issueUploadItem(userId))
                .toList();
        return UploadUrlsResponse.of(uploads);
    }

    private UploadItem issueUploadItem(Long userId) {
        String objectKey = CaptureObjectKeyGenerator.generate(userId);
        PresignedUploadInfo uploadInfo = imagePresignedUrlProvider.issueUploadUrl(objectKey);
        return UploadItem.of(objectKey, uploadInfo.uploadUrl());
    }
}
