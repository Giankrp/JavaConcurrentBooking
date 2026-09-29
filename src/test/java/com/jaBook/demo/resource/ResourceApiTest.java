package com.jaBook.demo.resource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ResourceApiTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    MockMvc mockMvc;

    @Test
    void createsFetchesAndListsResources() throws Exception {
        String first = mockMvc.perform(post("/api/resources")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Room 1"}
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.name").value("Room 1"))
            .andExpect(jsonPath("$.active").value(true))
            .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(get(first))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Room 1"));

        mockMvc.perform(post("/api/resources")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Room 2"}
                        """))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/resources"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void rejectsBlankName() throws Exception {
        mockMvc.perform(post("/api/resources")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"  "}
                        """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void returns404ForUnknownResource() throws Exception {
        mockMvc.perform(get("/api/resources/999999"))
            .andExpect(status().isNotFound());
    }
}
