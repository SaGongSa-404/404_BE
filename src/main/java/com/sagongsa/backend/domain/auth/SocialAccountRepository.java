package com.sagongsa.backend.domain.auth;

import com.sagongsa.backend.domain.enums.SocialProvider;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, UUID> {

	Optional<SocialAccount> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);
}
