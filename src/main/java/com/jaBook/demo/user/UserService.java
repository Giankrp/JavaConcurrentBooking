package com.jaBook.demo.user;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserResponse create(UserCreateRequest request) {
        userRepository.findByEmail(request.email())
            .ifPresent(existing -> {
                throw new EmailAlreadyUsedException(request.email());
            });

        try {
            User saved = userRepository.save(new User(request.name(), request.email()));
            return UserResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyUsedException(request.email());
        }
    }

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        return userRepository.findById(id)
            .map(UserResponse::from)
            .orElseThrow(() -> new UserNotFoundException(id));
    }
}
