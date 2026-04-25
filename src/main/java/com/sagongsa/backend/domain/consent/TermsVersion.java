package com.sagongsa.backend.domain.consent;

import com.sagongsa.backend.domain.common.BaseEntity;
import com.sagongsa.backend.domain.enums.TermsType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
	name = "terms_versions",
	indexes = {
		@Index(name = "idx_terms_versions_type_effective_from", columnList = "terms_type,effective_from")
	},
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_terms_versions_type_version", columnNames = {"terms_type", "version"})
	}
)
public class TermsVersion extends BaseEntity {

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private TermsType termsType;

	@Column(nullable = false, length = 40)
	private String version;

	@Column(nullable = false, length = 120)
	private String title;

	@Column(columnDefinition = "text")
	private String contentUrl;

	@Column(nullable = false)
	private boolean isRequired;

	@Column(nullable = false)
	private Instant effectiveFrom;

	protected TermsVersion() {
	}
}
