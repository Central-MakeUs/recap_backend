package cmc.recap.card.dto.response;

import java.util.List;

public record UploadUrlsResponse(List<UploadItem> uploads) {

    public static UploadUrlsResponse of(List<UploadItem> uploads) {
        return new UploadUrlsResponse(uploads);
    }

    public record UploadItem(String imageKey, String uploadUrl) {

        public static UploadItem of(String imageKey, String uploadUrl) {
            return new UploadItem(imageKey, uploadUrl);
        }
    }
}
