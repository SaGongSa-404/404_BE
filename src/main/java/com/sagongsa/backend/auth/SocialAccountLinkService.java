package com.sagongsa.backend.auth;

import com.sagongsa.backend.domain.auth.SocialAccount;
import com.sagongsa.backend.domain.auth.SocialAccountRepository;
import com.sagongsa.backend.domain.auth.UserAccount;
import com.sagongsa.backend.domain.auth.UserAccountRepository;
import com.sagongsa.backend.domain.enums.SocialProvider;
import com.sagongsa.backend.domain.enums.UserStatus;
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
			.map(existingAccount -> linkExistingAccount(profile, existingAccount))
			.orElseGet(() -> createNewAccount(profile, provider));
	}

	private SocialUserProfile linkExistingAccount(SocialUserProfile profile, SocialAccount existingAccount) {
		existingAccount.updateProfile(profile.email(), profile.profileImageUrl());
		UserAccount linkedUser = existingAccount.getUser();
		if (linkedUser.getStatus() == UserStatus.WITHDRAWN) {
			linkedUser = userAccountRepository.save(UserAccount.create());
			existingAccount.relinkUser(linkedUser);
		}
		return profile.withUserId(linkedUser.getId());
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
				.map(existingAccount -> linkExistingAccount(profile, existingAccount))
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
