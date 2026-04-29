package com.example._04_backend.global.auth;

import com.example._04_backend.domain.user.entity.User;
import com.example._04_backend.domain.user.entity.UserProfile;
import com.example._04_backend.domain.user.repository.UserProfileRepository;
import com.example._04_backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = delegate.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        SocialUserProfile profile = SocialUserProfile.from(registrationId, oauth2User.getAttributes());

        User user = upsertUser(profile);

        Set<GrantedAuthority> authorities = new LinkedHashSet<>(oauth2User.getAuthorities());
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

        return new LoginUser(user.getId(), profile, authorities);
    }

    User upsertUser(SocialUserProfile profile) {
        return userRepository.findByProviderAndProviderUserId(profile.provider(), profile.providerUserId())
                .map(existing -> {
                    // 기존 유저 — UserProfile만 업데이트
                    userProfileRepository.findByUserId(existing.getId()).ifPresent(p ->
                            p.updateProfile(profile.name(), null));
                    return existing;
                })
                .orElseGet(() -> {
                    User newUser = userRepository.save(
                            User.builder()
                                    .provider(profile.provider())
                                    .providerUserId(profile.providerUserId())
                                    .build()
                    );
                    // UserProfile 함께 생성
                    userProfileRepository.save(
                            UserProfile.builder()
                                    .user(newUser)
                                    .nickname(profile.name() != null ? profile.name() : "사용자")
                                    .mascotName("너구리")
                                    .profileImageUrl(profile.profileImageUrl())
                                    .build()
                    );
                    return newUser;
                });
    }
}
