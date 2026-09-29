package com.jaBook.demo.resource;

import java.time.OffsetDateTime;

record ResourceResponse(Long id, String name, boolean active, OffsetDateTime createdAt) {

    static ResourceResponse from(Resource resource) {
        return new ResourceResponse(resource.getId(), resource.getName(), resource.isActive(),
                resource.getCreatedAt());
    }
}
