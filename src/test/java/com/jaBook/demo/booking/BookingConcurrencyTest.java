package com.jaBook.demo.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.jaBook.demo.resource.Resource;
import com.jaBook.demo.resource.ResourceRepository;
import com.jaBook.demo.user.User;
import com.jaBook.demo.user.UserRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class BookingConcurrencyTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    BookingService bookingService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ResourceRepository resourceRepository;

    @Autowired
    BookingRepository bookingRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void concurrentBookingsForSameIntervalProduceExactlyOneWinner() throws Exception {
        User user = userRepository.save(new User("Racer", "racer@booking.test"));
        Resource resource = resourceRepository.save(new Resource("Race Room"));

        int racers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        CyclicBarrier barrier = new CyclicBarrier(racers);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();

        List<Future<Void>> futures = new ArrayList<>();
        for (int i = 0; i < racers; i++) {
            futures.add(pool.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                try {
                    bookingService.createBooking(new BookingCreateRequest(
                        user.getId(), resource.getId(),
                        OffsetDateTime.parse("2026-06-01T10:00:00Z"),
                        OffsetDateTime.parse("2026-06-01T11:00:00Z")));
                    successes.incrementAndGet();
                } catch (BookingConflictException e) {
                    conflicts.incrementAndGet();
                }
                return null;
            }));
        }
        for (Future<Void> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(successes.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(racers - 1);

        Integer activeCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM bookings WHERE resource_id = ? AND status = 'ACTIVE'",
            Integer.class, resource.getId());
        assertThat(activeCount).isEqualTo(1);
    }

    @Test
    void cancelledBookingFreesIntervalForNewActiveBooking() {
        User user = userRepository.save(new User("Reuser", "reuser@booking.test"));
        Resource resource = resourceRepository.save(new Resource("Reuse Room"));

        BookingResponse first = bookingService.createBooking(new BookingCreateRequest(
            user.getId(), resource.getId(),
            OffsetDateTime.parse("2026-07-01T10:00:00Z"),
            OffsetDateTime.parse("2026-07-01T11:00:00Z")));
        bookingService.cancel(first.id());

        BookingResponse second = bookingService.createBooking(new BookingCreateRequest(
            user.getId(), resource.getId(),
            OffsetDateTime.parse("2026-07-01T10:00:00Z"),
            OffsetDateTime.parse("2026-07-01T11:00:00Z")));

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(second.status()).isEqualTo(BookingStatus.ACTIVE);

        BookingResponse reloadedFirst = bookingService.getById(first.id());
        assertThat(reloadedFirst.status()).isEqualTo(BookingStatus.CANCELLED);

        Integer activeCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM bookings WHERE resource_id = ? AND status = 'ACTIVE'",
            Integer.class, resource.getId());
        assertThat(activeCount).isEqualTo(1);
    }
}
