package com.jaBook.demo.user;

import java.time.OffsetDateTime;

record UserResponse(Long id, String name, String email, OffsetDateTime createdAt) {

    static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getCreatedAt());
    }
}
