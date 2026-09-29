package com.jaBook.demo.user;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(Long id) {
        super("User %d not found".formatted(id));
    }
}
