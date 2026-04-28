package com.example._04_backend.global.config;

import com.example._04_backend.global.auth.CustomOAuth2UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String AUTHORIZATION_BASE_URI = "/oauth2/authorization";

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            CustomOAuth2UserService customOAuth2UserService,
            OAuth2AuthorizationRequestResolver authorizationRequestResolver
    ) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/h2-console/**").permitAll()
                        .requestMatchers("/", "/index.html", "/favicon.ico", "/error").permitAll()
                        .requestMatchers("/uploads/**").permitAll()
                        // 약관은 비로그인도 허용
                        .requestMatchers(HttpMethod.GET, "/api/terms", "/api/terms/**").permitAll()
                        // 소셜 피드 조회는 비로그인도 허용
                        .requestMatchers(HttpMethod.GET, "/social/posts", "/social/posts/**").permitAll()
                        // 소셜 피드 쓰기(게시글/댓글/투표)는 로그인 필요
                        .requestMatchers(HttpMethod.POST, "/social/posts/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/social/posts/**").authenticated()
                        // 마이페이지는 로그인 필요
                        .requestMatchers("/api/users/me", "/api/users/me/**").authenticated()
                        // 개발용 엔드포인트는 로그인 필요 (prod 프로파일에선 비활성)
                        .requestMatchers("/api/dev", "/api/dev/**").authenticated()
                        // 구매 숙려는 로그인 필요
                        .requestMatchers("/api/wishes", "/api/wishes/**").authenticated()
                        .anyRequest().permitAll()
                )
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(endpoint ->
                                endpoint.authorizationRequestResolver(authorizationRequestResolver))
                        .userInfoEndpoint(userInfo ->
                                userInfo.userService(customOAuth2UserService))
                        .defaultSuccessUrl("/", true)
                )
                .logout(logout -> logout
                        .logoutUrl("/api/logout")
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpStatus.NO_CONTENT.value()))
                )
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                RegexRequestMatcher.regexMatcher("^/api/.*")
                        )
                );

        return http.build();
    }

    @Bean
    public OAuth2AuthorizationRequestResolver authorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository
    ) {
        DefaultOAuth2AuthorizationRequestResolver delegate =
                new DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository, AUTHORIZATION_BASE_URI);

        return new OAuth2AuthorizationRequestResolver() {
            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
                return customize(request, delegate.resolve(request));
            }

            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
                return customize(clientRegistrationId, delegate.resolve(request, clientRegistrationId));
            }
        };
    }

    private OAuth2AuthorizationRequest customize(HttpServletRequest request, OAuth2AuthorizationRequest authorizationRequest) {
        if (authorizationRequest == null) return null;
        String registrationId = request.getRequestURI().substring(request.getRequestURI().lastIndexOf('/') + 1);
        return customize(registrationId, authorizationRequest);
    }

    private OAuth2AuthorizationRequest customize(String registrationId, OAuth2AuthorizationRequest authorizationRequest) {
        if (authorizationRequest == null) return null;

        Map<String, Object> additionalParameters = new LinkedHashMap<>(authorizationRequest.getAdditionalParameters());

        if ("google".equals(registrationId)) {
            additionalParameters.put("prompt", "select_account");
        } else if ("kakao".equals(registrationId)) {
            additionalParameters.put("prompt", "login");
        } else if ("naver".equals(registrationId)) {
            additionalParameters.put("auth_type", "reprompt");
        }

        return OAuth2AuthorizationRequest.from(authorizationRequest)
                .additionalParameters(additionalParameters)
                .build();
    }
}
