package com.jaBook.demo.booking;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jaBook.demo.resource.Resource;
import com.jaBook.demo.resource.ResourceNotFoundException;
import com.jaBook.demo.resource.ResourceRepository;
import com.jaBook.demo.user.User;
import com.jaBook.demo.user.UserNotFoundException;
import com.jaBook.demo.user.UserRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final ResourceRepository resourceRepository;

    BookingService(
        BookingRepository bookingRepository,
        UserRepository userRepository,
        ResourceRepository resourceRepository
    ) {
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.resourceRepository = resourceRepository;
    }

    @Transactional
    public BookingResponse createBooking(BookingCreateRequest request) {
        User user = userRepository.findById(request.userId())
            .orElseThrow(() -> new UserNotFoundException(request.userId()));
        Resource resource = resourceRepository.findById(request.resourceId())
            .orElseThrow(() -> new ResourceNotFoundException(request.resourceId()));

        if (!resource.isActive()) {
            log.warn("Booking rejected: resourceId={}, reason=inactive", resource.getId());
            throw new ResourceInactiveException(resource.getId());
        }
        if (bookingRepository.existsConflict(request.resourceId(),
                request.startTime(), request.endTime(), BookingStatus.ACTIVE)) {
            log.warn("Booking rejected: resourceId={}, reason=overlap", request.resourceId());
            throw new BookingConflictException(request.resourceId());
        }

        Booking saved;
        try {
            saved = bookingRepository.save(
                new Booking(user, resource, request.startTime(), request.endTime()));
        } catch (DataIntegrityViolationException e) {
            if (isOverlapViolation(e)) {
                log.warn("Booking rejected by database constraint: resourceId={}, reason=overlap-race",
                    request.resourceId());
                throw new BookingConflictException(request.resourceId());
            }
            throw e;
        }
        log.info("Booking created: id={}, resourceId={}, userId={}",
            saved.getId(), resource.getId(), user.getId());
        return BookingResponse.from(saved);
    }

    private boolean isOverlapViolation(DataIntegrityViolationException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (String.valueOf(cause.getMessage()).contains("excl_bookings_no_overlap")) {
                return true;
            }
        }
        return false;
    }

    @Transactional(readOnly = true)
    public BookingResponse getById(Long id) {
        return bookingRepository.findById(id)
            .map(BookingResponse::from)
            .orElseThrow(() -> new BookingNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getByResource(Long resourceId) {
        return bookingRepository.findByResourceId(resourceId).stream()
            .map(BookingResponse::from)
            .toList();
    }

    @Transactional
    public void cancel(Long id) {
        Booking booking = bookingRepository.findById(id)
            .orElseThrow(() -> new BookingNotFoundException(id));
        booking.cancel();
        log.info("Booking cancelled: id={}", booking.getId());
    }
}
