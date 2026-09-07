package com.sagongsa.backend.wishlist;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

class WishlistServiceUserValidationTest {

	@Test
	void validatesUserAccessWithSingleJdbcCall() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		UUID userId = UUID.randomUUID();
		when(jdbcTemplate.queryForObject(contains("from users"), eq(Boolean.class), eq(userId)))
			.thenReturn(Boolean.TRUE);
		WishlistService wishlistService = new WishlistService(new WishlistJdbcRepository(jdbcTemplate), new ObjectMapper(), new WishlistQueries(jdbcTemplate));

		assertThatThrownBy(() -> wishlistService.create(userId, null))
			.isInstanceOf(BadRequestException.class);

		verify(jdbcTemplate, times(1))
			.queryForObject(contains("from users"), eq(Boolean.class), eq(userId));
		verifyNoMoreInteractions(jdbcTemplate);
	}

	@Test
	void mapsMissingUserToNotFoundWithoutASecondJdbcCall() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		UUID userId = UUID.randomUUID();
		when(jdbcTemplate.queryForObject(contains("from users"), eq(Boolean.class), eq(userId)))
			.thenThrow(new EmptyResultDataAccessException(1));
		WishlistService wishlistService = new WishlistService(new WishlistJdbcRepository(jdbcTemplate), new ObjectMapper(), new WishlistQueries(jdbcTemplate));

		assertThatThrownBy(() -> wishlistService.create(userId, null))
			.isInstanceOf(WishlistItemNotFoundException.class);

		verify(jdbcTemplate, times(1))
			.queryForObject(contains("from users"), eq(Boolean.class), eq(userId));
		verifyNoMoreInteractions(jdbcTemplate);
	}

	@Test
	void mapsDisallowedUserToForbiddenWithoutASecondJdbcCall() {
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		UUID userId = UUID.randomUUID();
		when(jdbcTemplate.queryForObject(contains("from users"), eq(Boolean.class), eq(userId)))
			.thenReturn(Boolean.FALSE);
		WishlistService wishlistService = new WishlistService(new WishlistJdbcRepository(jdbcTemplate), new ObjectMapper(), new WishlistQueries(jdbcTemplate));

		assertThatThrownBy(() -> wishlistService.create(userId, null))
			.isInstanceOf(WishlistForbiddenException.class);

		verify(jdbcTemplate, times(1))
			.queryForObject(contains("from users"), eq(Boolean.class), eq(userId));
		verifyNoMoreInteractions(jdbcTemplate);
	}
}
