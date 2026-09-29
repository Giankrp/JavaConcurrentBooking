package com.jaBook.demo.resource;

import jakarta.validation.constraints.NotBlank;

record ResourceCreateRequest(@NotBlank String name) {
}
