package com.trading.desk.exception;

import org.springframework.http.HttpStatus;

/**
 * Domain exception for all intentional business-rule violations.
 * Carries an HTTP status so the global handler can render the right response
 * without coupling domain logic to Spring MVC.
 */
public class TradingException extends RuntimeException {

    private final HttpStatus httpStatus;

    public TradingException(String message, HttpStatus httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
