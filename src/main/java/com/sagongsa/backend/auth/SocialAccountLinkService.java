package com.sagongsa.backend.auth;

import com.sagongsa.backend.domain.auth.SocialAccount;
import com.sagongsa.backend.domain.auth.SocialAccountRepository;
import com.sagongsa.backend.domain.auth.UserAccount;
import com.sagongsa.backend.domain.auth.UserAccountRepository;
import com.sagongsa.backend.domain.enums.SocialProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SocialAccountLinkService {

	private final SocialAccountRepository socialAccountRepository;
	private final UserAccountRepository userAccountRepository;

	public SocialAccountLinkService(
		SocialAccountRepository socialAccountRepository,
		UserAccountRepository userAccountRepository
	) {
		this.socialAccountRepository = socialAccountRepository;
		this.userAccountRepository = userAccountRepository;
	}

	@Transactional
	public SocialUserProfile linkOrCreateUser(SocialUserProfile profile) {
		SocialProvider provider = resolveProvider(profile.provider());

		return socialAccountRepository.findByProviderAndProviderUserId(provider, profile.providerUserId())
			.map(existingAccount -> {
				existingAccount.updateProfile(profile.email(), profile.profileImageUrl());
				return profile.withUserId(existingAccount.getUser().getId());
			})
			.orElseGet(() -> createNewAccount(profile, provider));
	}

	private SocialUserProfile createNewAccount(SocialUserProfile profile, SocialProvider provider) {
		try {
			UserAccount userAccount = userAccountRepository.save(UserAccount.create());
			SocialAccount socialAccount = SocialAccount.create(
				userAccount,
				provider,
				profile.providerUserId(),
				profile.email(),
				profile.profileImageUrl()
			);
			socialAccountRepository.save(socialAccount);
			return profile.withUserId(userAccount.getId());
		} catch (DataIntegrityViolationException exception) {
			return socialAccountRepository.findByProviderAndProviderUserId(provider, profile.providerUserId())
				.map(existingAccount -> {
					existingAccount.updateProfile(profile.email(), profile.profileImageUrl());
					return profile.withUserId(existingAccount.getUser().getId());
				})
				.orElseThrow(() -> exception);
		}
	}

	private SocialProvider resolveProvider(String registrationId) {
		return switch (registrationId) {
			case "google" -> SocialProvider.GOOGLE;
			case "kakao" -> SocialProvider.KAKAO;
			default -> throw new IllegalArgumentException("Unsupported provider: " + registrationId);
		};
	}
}
