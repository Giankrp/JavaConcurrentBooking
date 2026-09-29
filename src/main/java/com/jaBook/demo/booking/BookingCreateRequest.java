package com.jaBook.demo.booking;

import com.jaBook.demo.resource.Resource;
import com.jaBook.demo.user.User;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

record BookingCreateRequest(
    @NotNull User user,
    @NotNull Resource resource,
    @NotNull OffsetDateTime startTime,
    @NotNull OffsetDateTime endTime
) {}
