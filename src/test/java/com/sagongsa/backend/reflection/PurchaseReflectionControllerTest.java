package com.sagongsa.backend.reflection;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sagongsa.backend.auth.CurrentUserId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class PurchaseReflectionControllerTest {

	private PurchaseReflectionService purchaseReflectionService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		purchaseReflectionService = mock(PurchaseReflectionService.class);
		mockMvc = MockMvcBuilders
			.standaloneSetup(new PurchaseReflectionController(purchaseReflectionService))
			.setCustomArgumentResolvers(new HeaderUserIdArgumentResolver())
			.build();
	}

	@Test
	void createsReflectionAndReturnsLocationHeader() throws Exception {
		UUID userId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
		UUID reflectionId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
		UUID decisionId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
		UUID itemId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
		UUID reminderId = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
		given(purchaseReflectionService.create(eq(userId), argThat(request -> decisionId.equals(request.decisionId()))))
			.willReturn(new PurchaseReflectionResponse(
				reflectionId,
				decisionId,
				itemId,
				reminderId,
				5,
				"NONE",
				true,
				"잘 쓰고 있어요",
				Instant.parse("2026-05-28T00:00:00Z")
			));

		mockMvc.perform(post("/api/v1/reflections")
				.header("X-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
				{
					"decisionId": "%s",
					"satisfactionScore": 5,
					"regretLevel": "NONE",
					"stillUsing": true,
					"reflectionNote": "잘 쓰고 있어요"
				}
				""".formatted(decisionId)))
			.andExpect(status().isCreated())
			.andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/reflections/" + reflectionId))
			.andExpect(jsonPath("$.id").value(reflectionId.toString()))
			.andExpect(jsonPath("$.decisionId").value(decisionId.toString()))
			.andExpect(jsonPath("$.itemId").value(itemId.toString()))
			.andExpect(jsonPath("$.reminderId").value(reminderId.toString()))
			.andExpect(jsonPath("$.regretLevel").value("NONE"));

		verify(purchaseReflectionService).create(eq(userId), argThat(request ->
			decisionId.equals(request.decisionId())
				&& request.satisfactionScore() == 5
				&& "NONE".equals(request.regretLevel())
				&& Boolean.TRUE.equals(request.stillUsing())
				&& "잘 쓰고 있어요".equals(request.reflectionNote())
			));
	}

	@Test
	void rejectsMissingUserHeader() throws Exception {
		UUID decisionId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

		mockMvc.perform(post("/api/v1/reflections")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
				{
					"decisionId": "%s",
					"satisfactionScore": 5,
					"regretLevel": "NONE",
					"stillUsing": true
				}
				""".formatted(decisionId)))
			.andExpect(status().isBadRequest());

		verifyNoInteractions(purchaseReflectionService);
	}

	private static final class HeaderUserIdArgumentResolver implements HandlerMethodArgumentResolver {

		@Override
		public boolean supportsParameter(MethodParameter parameter) {
			return parameter.hasParameterAnnotation(CurrentUserId.class)
				&& UUID.class.equals(parameter.getParameterType());
		}

		@Override
		public Object resolveArgument(
			MethodParameter parameter,
			ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest,
			WebDataBinderFactory binderFactory
		) {
			String rawUserId = webRequest.getHeader("X-User-Id");
			if (!StringUtils.hasText(rawUserId)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-User-Id header is required.");
			}
			try {
				return UUID.fromString(rawUserId.trim());
			}
			catch (IllegalArgumentException exception) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-User-Id header must be a UUID.");
			}
		}
	}
}
