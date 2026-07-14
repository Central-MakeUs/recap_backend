package cmc.recap.card.image;

import java.net.URL;

public interface ImagePresignedUrlProvider {

    PresignedUploadInfo issueUploadUrl(String objectKey);

    URL issueDownloadUrl(String objectKey);
}
