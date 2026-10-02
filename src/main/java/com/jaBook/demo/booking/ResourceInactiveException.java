package com.jaBook.demo.booking;

public class ResourceInactiveException extends RuntimeException {

    public ResourceInactiveException(Long resourceId) {
        super("Resource %d is inactive and cannot be reserved".formatted(resourceId));
    }
}
