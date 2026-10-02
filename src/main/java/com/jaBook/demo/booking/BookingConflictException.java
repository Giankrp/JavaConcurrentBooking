package com.jaBook.demo.booking;

public class BookingConflictException extends RuntimeException {

    public BookingConflictException(Long resourceId) {
        super("Resource %d already has an overlapping active booking".formatted(resourceId));
    }
}
