package com.jaBook.demo.resource;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(Long id) {
        super("Resource %d not found".formatted(id));
    }
}
