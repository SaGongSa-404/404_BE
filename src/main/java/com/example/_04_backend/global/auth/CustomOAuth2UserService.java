package com.example._04_backend.global.auth;

import com.example._04_backend.domain.user.entity.User;
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

    private User upsertUser(SocialUserProfile profile) {
        return userRepository.findByProviderAndProviderUserId(profile.provider(), profile.providerUserId())
                .map(existing -> {
                    existing.updateProfile(profile.name(), profile.profileImageUrl());
                    return existing;
                })
                .orElseGet(() -> userRepository.save(
                        User.builder()
                                .provider(profile.provider())
                                .providerUserId(profile.providerUserId())
                                .nickname(profile.name())
                                .email(profile.email())
                                .profileImageUrl(profile.profileImageUrl())
                                .build()
                ));
    }
}
