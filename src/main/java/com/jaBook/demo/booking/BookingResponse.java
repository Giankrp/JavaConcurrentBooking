package com.jaBook.demo.booking;

import java.time.OffsetDateTime;

/**
 * BookingResponse
 */
record BookingResponse(
    Long id,
    Long userId,
    Long resourceId,
    OffsetDateTime startTime,
    OffsetDateTime endTime
) {
    static BookingResponse from(Booking booking){
        return new BookingResponse(
            booking.getId(),
            booking.getUser().getId(),
            booking.getResource().getId(),
            booking.getStartTime(),
            booking.getEndTime()
        );
    }
}
