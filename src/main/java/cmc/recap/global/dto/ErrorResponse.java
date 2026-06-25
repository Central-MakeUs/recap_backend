package cmc.recap.global.dto;

public record ErrorResponse(
        String code,
        String message) {
}