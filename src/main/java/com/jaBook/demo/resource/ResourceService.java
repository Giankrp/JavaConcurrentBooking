package com.jaBook.demo.resource;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class ResourceService {

    private final ResourceRepository resourceRepository;

    ResourceService(ResourceRepository resourceRepository) {
        this.resourceRepository = resourceRepository;
    }

    @Transactional
    public ResourceResponse create(ResourceCreateRequest request) {
        Resource saved = resourceRepository.save(new Resource(request.name()));
        log.info("Resource created: id={}, name={}", saved.getId(), saved.getName());
        return ResourceResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ResourceResponse> getAll() {
        return resourceRepository.findAll().stream()
            .map(ResourceResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public ResourceResponse getById(Long id) {
        return resourceRepository.findById(id)
            .map(ResourceResponse::from)
            .orElseThrow(() -> new ResourceNotFoundException(id));
    }
}
