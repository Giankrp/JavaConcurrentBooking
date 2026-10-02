package com.jaBook.demo;

import com.jaBook.demo.booking.BookingConflictException;
import com.jaBook.demo.booking.BookingNotFoundException;
import com.jaBook.demo.booking.ResourceInactiveException;
import com.jaBook.demo.resource.ResourceNotFoundException;
import com.jaBook.demo.user.EmailAlreadyUsedException;
import com.jaBook.demo.user.UserNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({ UserNotFoundException.class, ResourceNotFoundException.class,
            BookingNotFoundException.class })
    ProblemDetail handleNotFound(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({ EmailAlreadyUsedException.class, DataIntegrityViolationException.class,
            ResourceInactiveException.class, BookingConflictException.class })
    ProblemDetail handleConflict(Exception e) {
        String detail = switch (e) {
            case EmailAlreadyUsedException ex -> ex.getMessage();
            case ResourceInactiveException ex -> ex.getMessage();
            case BookingConflictException ex -> ex.getMessage();
            default -> "Resource already exists";
        };
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setProperty("errors", e.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .toList());
        return problem;
    }
}
