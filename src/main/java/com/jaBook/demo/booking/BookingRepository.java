package com.jaBook.demo.booking;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByResourceId(Long resourceId);

    @Query("""
        SELECT COUNT(b) > 0 FROM Booking b
        WHERE b.resource.id = :resourceId
          AND b.status = :status
          AND b.startTime < :endTime
          AND b.endTime > :startTime
        """)
    boolean existsConflict(@Param("resourceId") Long resourceId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("status") BookingStatus status);
}
