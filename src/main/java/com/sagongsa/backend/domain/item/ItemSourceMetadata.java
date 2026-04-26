package com.sagongsa.backend.domain.item;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "item_source_metadata")
public class ItemSourceMetadata {

	@Id
	@Column(name = "item_id", nullable = false, updatable = false)
	private UUID id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@MapsId
	@JoinColumn(name = "item_id", nullable = false)
	private SavedItem item;

	@Column(length = 120)
	private String sourceDomain;

	@Column(columnDefinition = "text")
	private String rawTitle;

	@Column(columnDefinition = "text")
	private String rawDescription;

	@Column(length = 120)
	private String rawPriceText;

	@Column(columnDefinition = "jsonb")
	@JdbcTypeCode(SqlTypes.JSON)
	private String rawPayloadJson;

	@Column(nullable = false)
	private Instant extractedAt;

	protected ItemSourceMetadata() {
	}

	@PrePersist
	void onCreate() {
		if (extractedAt == null) {
			extractedAt = Instant.now();
		}
	}
}
