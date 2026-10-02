package com.jaBook.demo.booking;

public class BookingNotFoundException extends RuntimeException {

    public BookingNotFoundException(Long id) {
        super("Booking %d not found".formatted(id));
    }
}
