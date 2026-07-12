package com.wallet.wallet.dto;

import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

public record ExceptionHandlerResponse(
        int status,
        String message,
        LocalDateTime timeStamp
) {
}
