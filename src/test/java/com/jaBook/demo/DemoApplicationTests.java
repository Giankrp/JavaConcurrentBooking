package com.jaBook.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class DemoApplicationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void flywayMigrationsCreateCoreSchema() {
		List<String> tables = jdbcTemplate.queryForList("""
				SELECT table_name
				FROM information_schema.tables
				WHERE table_schema = 'public'
				ORDER BY table_name
				""", String.class);

		assertThat(tables).contains("bookings", "flyway_schema_history", "resources", "users");
	}

	@Test
	void bookingTimeIntervalIsEnforcedByDatabase() {
		Long userId = jdbcTemplate.queryForObject(
				"INSERT INTO users (name, email) VALUES ('Test User', 'test@example.com') RETURNING id",
				Long.class);
		Long resourceId = jdbcTemplate.queryForObject(
				"INSERT INTO resources (name, active) VALUES ('Room 1', TRUE) RETURNING id",
				Long.class);

		assertThat(isRejected("2026-01-01T10:00:00Z", "2026-01-01T09:00:00Z", userId, resourceId)).isTrue();
		assertThat(isRejected("2026-01-01T10:00:00Z", "2026-01-01T10:00:00Z", userId, resourceId)).isTrue();
		assertThat(isRejected("2026-01-01T10:00:00Z", "2026-01-01T11:00:00Z", userId, resourceId)).isFalse();
	}

	private boolean isRejected(String start, String end, Long userId, Long resourceId) {
		try {
			jdbcTemplate.update("""
					INSERT INTO bookings (user_id, resource_id, start_time, end_time)
					VALUES (?, ?, ?, ?)
					""", userId, resourceId, java.time.OffsetDateTime.parse(start),
					java.time.OffsetDateTime.parse(end));
			return false;
		} catch (org.springframework.dao.DataIntegrityViolationException e) {
			return true;
		}
	}

}
