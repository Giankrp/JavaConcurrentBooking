package com.jaBook.demo.booking;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jaBook.demo.resource.Resource;
import com.jaBook.demo.resource.ResourceRepository;
import com.jaBook.demo.user.User;
import com.jaBook.demo.user.UserRepository;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BookingApiTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ResourceRepository resourceRepository;

    @Autowired
    BookingRepository bookingRepository;

    @Test
    void createsFetchesListsAndCancels() throws Exception {
        User user = userRepository.save(new User("Ada Lovelace", "ada@booking.test"));
        Resource resource = resourceRepository.save(new Resource("Room A"));

        String location = mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"userId":%d,"resourceId":%d,
                         "startTime":"2026-01-01T10:00:00Z",
                         "endTime":"2026-01-01T11:00:00Z"}
                        """.formatted(user.getId(), resource.getId())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.userId").value(user.getId()))
            .andExpect(jsonPath("$.resourceId").value(resource.getId()))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(get(location))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(get("/api/bookings").param("resourceId", resource.getId().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].status").value("ACTIVE"));

        mockMvc.perform(delete(location))
            .andExpect(status().isNoContent());

        mockMvc.perform(get(location))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/api/bookings").param("resourceId", resource.getId().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].status").value("CANCELLED"));
    }

    @Test
    void rejectsInvalidInterval() throws Exception {
        User user = userRepository.save(new User("Bob", "bob@booking.test"));
        Resource resource = resourceRepository.save(new Resource("Room B"));

        mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"userId":%d,"resourceId":%d,
                         "startTime":"2026-02-01T11:00:00Z",
                         "endTime":"2026-02-01T10:00:00Z"}
                        """.formatted(user.getId(), resource.getId())))
            .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnknownUserAndResource() throws Exception {
        Resource resource = resourceRepository.save(new Resource("Room C"));
        User user = userRepository.save(new User("Carol", "carol@booking.test"));

        mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"userId":999999,"resourceId":%d,
                         "startTime":"2026-03-01T10:00:00Z",
                         "endTime":"2026-03-01T11:00:00Z"}
                        """.formatted(resource.getId())))
            .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"userId":%d,"resourceId":999999,
                         "startTime":"2026-03-01T10:00:00Z",
                         "endTime":"2026-03-01T11:00:00Z"}
                        """.formatted(user.getId())))
            .andExpect(status().isNotFound());
    }

    @Test
    void rejectsInactiveResource() throws Exception {
        User user = userRepository.save(new User("Dave", "dave@booking.test"));
        Resource resource = new Resource("Room D");
        resource.setActive(false);
        resource = resourceRepository.save(resource);

        mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"userId":%d,"resourceId":%d,
                         "startTime":"2026-04-01T10:00:00Z",
                         "endTime":"2026-04-01T11:00:00Z"}
                        """.formatted(user.getId(), resource.getId())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value(
                "Resource %d is inactive and cannot be reserved".formatted(resource.getId())));
    }

    @Test
    void rejectsOverlapButAllowsAdjacent() throws Exception {
        User user = userRepository.save(new User("Eve", "eve@booking.test"));
        Resource resource = resourceRepository.save(new Resource("Room E"));

        bookingRepository.save(new Booking(user, resource,
                OffsetDateTime.parse("2026-05-01T10:00:00Z"),
                OffsetDateTime.parse("2026-05-01T11:00:00Z")));

        mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"userId":%d,"resourceId":%d,
                         "startTime":"2026-05-01T10:30:00Z",
                         "endTime":"2026-05-01T11:30:00Z"}
                        """.formatted(user.getId(), resource.getId())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value(
                "Resource %d already has an overlapping active booking".formatted(resource.getId())));

        mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"userId":%d,"resourceId":%d,
                         "startTime":"2026-05-01T11:00:00Z",
                         "endTime":"2026-05-01T12:00:00Z"}
                        """.formatted(user.getId(), resource.getId())))
            .andExpect(status().isCreated());
    }
}
