package com.jaBook.demo.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

record UserCreateRequest(
        @NotBlank String name,
        @NotBlank @Email String email) {
}
