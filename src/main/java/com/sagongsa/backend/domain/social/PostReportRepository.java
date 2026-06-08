package com.sagongsa.backend.domain.social;

import com.sagongsa.backend.domain.enums.ReportTargetType;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostReportRepository extends JpaRepository<PostReport, UUID> {

	boolean existsByReporterIdAndTargetTypeAndTargetId(
	    UUID reporterId, ReportTargetType targetType, UUID targetId);

	long countByTargetTypeAndTargetId(ReportTargetType targetType, UUID targetId);
}
