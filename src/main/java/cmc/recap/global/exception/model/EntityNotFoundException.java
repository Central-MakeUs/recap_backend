package cmc.recap.global.exception.model;

import cmc.recap.global.exception.ErrorCode;

public class EntityNotFoundException extends BusinessException {

    public EntityNotFoundException(ErrorCode errorCode,
                                   String customMessage) {
        super(errorCode, customMessage);
    }

    public EntityNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }
}