package com.sagongsa.backend.domain.social;

import com.sagongsa.backend.domain.auth.UserAccount;
import com.sagongsa.backend.domain.common.BaseEntity;
import com.sagongsa.backend.domain.enums.ReportCategory;
import com.sagongsa.backend.domain.enums.ReportTargetType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

@Entity
@Table(
	name = "post_reports",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_post_reports",
		columnNames = {"reporter_user_id", "target_type", "target_id"}
	)
)
public class PostReport extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "reporter_user_id", nullable = false)
	private UserAccount reporter;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "reported_user_id", nullable = false)
	private UserAccount reportedUser;

	@Enumerated(EnumType.STRING)
	@Column(name = "target_type", nullable = false, length = 10)
	private ReportTargetType targetType;

	@Column(name = "target_id", nullable = false)
	private UUID targetId;

	@Column(name = "post_id")
	private UUID postId;

	@Enumerated(EnumType.STRING)
	@Column(name = "report_category", nullable = false, length = 30)
	private ReportCategory reportCategory;

	@Column(length = 100)
	private String reason;

	protected PostReport() {
	}

	public PostReport(UserAccount reporter, UserAccount reportedUser, ReportTargetType targetType, UUID targetId, UUID postId,
		ReportCategory reportCategory, String reason) {
		this.reporter = reporter;
		this.reportedUser = reportedUser;
		this.targetType = targetType;
		this.targetId = targetId;
		this.postId = postId;
		this.reportCategory = reportCategory;
		this.reason = reason;
	}
}
