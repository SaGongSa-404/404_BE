package com.sagongsa.backend.domain.item;

import com.sagongsa.backend.domain.common.UserScopedEntity;
import com.sagongsa.backend.domain.enums.ItemCategory;
import com.sagongsa.backend.domain.enums.ItemInputSource;
import com.sagongsa.backend.domain.enums.ItemStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
	name = "saved_items",
	indexes = {
		@Index(name = "idx_saved_items_user_status_created", columnList = "user_id,status,created_at"),
		@Index(name = "idx_saved_items_user_category_created", columnList = "user_id,category,created_at")
	}
)
public class SavedItem extends UserScopedEntity {

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ItemInputSource inputSource;

	@Column(columnDefinition = "text")
	private String originalUrl;

	@Column(columnDefinition = "text")
	private String normalizedUrl;

	@Column(nullable = false, length = 255)
	private String title;

	@Column(columnDefinition = "text")
	private String imageUrl;

	@Column
	private Integer listedPrice;

	@Column(columnDefinition = "char(3)")
	@JdbcTypeCode(SqlTypes.CHAR)
	private String currencyCode;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ItemCategory category;

	@Column(precision = 5, scale = 2)
	private BigDecimal categoryConfidence;

	@Column(nullable = false)
	private boolean categoryLockedByUser;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ItemStatus status = ItemStatus.SAVED;

	protected SavedItem() {
	}
}
