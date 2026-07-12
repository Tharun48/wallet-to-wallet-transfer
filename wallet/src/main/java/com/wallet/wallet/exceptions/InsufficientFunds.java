package com.wallet.wallet.exceptions;

public class InsufficientFunds extends Exception{

    public InsufficientFunds() {
    }

    public InsufficientFunds(Exception cause) {
        super(cause);
    }

    public InsufficientFunds(String message) {
        super(message);
    }

    public InsufficientFunds(String message, Exception cause) {
        super(message, cause);
    }
}
