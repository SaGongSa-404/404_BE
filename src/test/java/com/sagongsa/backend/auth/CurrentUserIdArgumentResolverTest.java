package com.sagongsa.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.server.ResponseStatusException;

class CurrentUserIdArgumentResolverTest {

	private static final UUID JWT_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID HEADER_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

	@Test
	void supportsOnlyCurrentUserIdUuidParameters() throws Exception {
		CurrentUserIdArgumentResolver resolver = resolver(false, "prod");

		assertThat(resolver.supportsParameter(parameter("annotatedUuid"))).isTrue();
		assertThat(resolver.supportsParameter(parameter("plainUuid"))).isFalse();
		assertThat(resolver.supportsParameter(parameter("annotatedString"))).isFalse();
	}

	@Test
	void resolvesJwtUserIdClaimBeforeTrustedHeader() {
		CurrentUserIdArgumentResolver resolver = resolver(true, "prod");
		NativeWebRequest request = request(jwtPrincipal(JWT_USER_ID.toString(), "ignored-subject"), HEADER_USER_ID.toString());

		Object resolved = resolver.resolveArgument(null, null, request, null);

		assertThat(resolved).isEqualTo(JWT_USER_ID);
	}

	@Test
	void resolvesJwtSubjectWhenUserIdClaimIsMissing() {
		CurrentUserIdArgumentResolver resolver = resolver(false, "prod");
		NativeWebRequest request = request(jwtPrincipal(null, JWT_USER_ID.toString()), null);

		Object resolved = resolver.resolveArgument(null, null, request, null);

		assertThat(resolved).isEqualTo(JWT_USER_ID);
	}

	@Test
	void resolvesTrustedHeaderOutsideProdWhenPrincipalIsMissing() {
		CurrentUserIdArgumentResolver resolver = resolver(false, "local");
		NativeWebRequest request = request(null, HEADER_USER_ID.toString());

		Object resolved = resolver.resolveArgument(null, null, request, null);

		assertThat(resolved).isEqualTo(HEADER_USER_ID);
	}

	@Test
	void rejectsMalformedTrustedHeaderWhenExplicitlyEnabledInProdAndPrincipalIsMissing() {
		CurrentUserIdArgumentResolver resolver = resolver(true, "prod");
		NativeWebRequest request = request(null, "not-a-uuid");

		assertThatThrownBy(() -> resolver.resolveArgument(null, null, request, null))
			.isInstanceOf(ResponseStatusException.class)
			.satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
				.isEqualTo(HttpStatus.BAD_REQUEST));
	}

	@Test
	void requiresTrustedHeaderWhenExplicitlyEnabledInProdAndPrincipalIsMissing() {
		CurrentUserIdArgumentResolver resolver = resolver(true, "prod");
		NativeWebRequest request = request(null, null);

		assertThatThrownBy(() -> resolver.resolveArgument(null, null, request, null))
			.isInstanceOf(ResponseStatusException.class)
			.satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
				.isEqualTo(HttpStatus.BAD_REQUEST));
	}

	@Test
	void requiresAuthenticationInProdWhenTrustedHeaderIsDisabled() {
		CurrentUserIdArgumentResolver resolver = resolver(false, "prod");
		NativeWebRequest request = request(null, HEADER_USER_ID.toString());

		assertThatThrownBy(() -> resolver.resolveArgument(null, null, request, null))
			.isInstanceOf(ResponseStatusException.class)
			.satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
				.isEqualTo(HttpStatus.UNAUTHORIZED));
	}

	private static CurrentUserIdArgumentResolver resolver(boolean trustedHeaderEnabled, String activeProfile) {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles(activeProfile);
		return new CurrentUserIdArgumentResolver(trustedHeaderEnabled, "X-User-Id", environment);
	}

	private static NativeWebRequest request(Principal principal, String headerValue) {
		NativeWebRequest request = mock(NativeWebRequest.class);
		given(request.getUserPrincipal()).willReturn(principal);
		given(request.getHeader("X-User-Id")).willReturn(headerValue);
		return request;
	}

	private static JwtAuthenticationToken jwtPrincipal(String userIdClaim, String subject) {
		Jwt.Builder builder = Jwt.withTokenValue("token")
			.header("alg", "none")
			.subject(subject);
		if (userIdClaim != null) {
			builder.claim("userId", userIdClaim);
		}
		return new JwtAuthenticationToken(builder.build());
	}

	private static MethodParameter parameter(String methodName) throws Exception {
		Method method = TestController.class.getDeclaredMethod(methodName, methodName.equals("annotatedString") ? String.class : UUID.class);
		return new MethodParameter(method, 0);
	}

	@SuppressWarnings("unused")
	private static final class TestController {

		void annotatedUuid(@CurrentUserId UUID userId) {
		}

		void plainUuid(UUID userId) {
		}

		void annotatedString(@CurrentUserId String userId) {
		}
	}
}
