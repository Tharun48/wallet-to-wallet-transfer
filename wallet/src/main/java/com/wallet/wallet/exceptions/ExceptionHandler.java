package com.wallet.wallet.exceptions;

import com.wallet.wallet.dto.ExceptionHandlerResponse;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;

import java.time.LocalDateTime;

@ControllerAdvice
public class ExceptionHandler {

    @org.springframework.web.bind.annotation.ExceptionHandler
    public ResponseEntity<ExceptionHandlerResponse> exceptionHandler(EntityNotFoundException e){
        ExceptionHandlerResponse exceptionHandlerResponse =
                new ExceptionHandlerResponse(HttpStatus.NOT_FOUND.value(),
                        e.getMessage(), LocalDateTime.now()
                );
        return new ResponseEntity<>(exceptionHandlerResponse,HttpStatus.NOT_FOUND);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler
    public ResponseEntity<ExceptionHandlerResponse> exceptionHandler(InsufficientFunds e){
        ExceptionHandlerResponse exceptionHandlerResponse =
                new ExceptionHandlerResponse(HttpStatus.NOT_FOUND.value(),
                        e.getMessage(), LocalDateTime.now()
                );
        return new ResponseEntity<>(exceptionHandlerResponse,HttpStatus.NOT_FOUND);
    }

}
