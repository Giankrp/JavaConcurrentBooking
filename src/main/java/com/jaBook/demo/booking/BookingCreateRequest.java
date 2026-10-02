package com.jaBook.demo.booking;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

record BookingCreateRequest(
    @NotNull Long userId,
    @NotNull Long resourceId,
    @NotNull OffsetDateTime startTime,
    @NotNull OffsetDateTime endTime
) {

    @AssertTrue(message = "startTime must be before endTime")
    boolean isValidInterval() {
        return startTime == null || endTime == null || startTime.isBefore(endTime);
    }
}
