package com.jaBook.demo.user;
public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException(String email) {
        super("Email %s is already in use".formatted(email));
    }
}
