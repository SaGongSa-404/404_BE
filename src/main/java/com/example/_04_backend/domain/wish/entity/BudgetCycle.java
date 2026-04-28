package com.example._04_backend.domain.wish.entity;

import com.example._04_backend.domain.user.entity.User;
import com.example._04_backend.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(
        name = "budget_cycles",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_budget_cycles_user_year_month",
                columnNames = {"user_id", "year_month"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BudgetCycle extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "year_month", nullable = false, length = 7)
    private String yearMonth;

    @Column(name = "monthly_budget_amount", nullable = false)
    private Integer monthlyBudgetAmount;

    @Column(name = "spent_amount", nullable = false)
    private Integer spentAmount = 0;

    @Column(name = "warning_threshold_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal warningThresholdRate;

    @Builder
    public BudgetCycle(User user, String yearMonth, Integer monthlyBudgetAmount, BigDecimal warningThresholdRate) {
        this.user = user;
        this.yearMonth = yearMonth;
        this.monthlyBudgetAmount = monthlyBudgetAmount;
        this.spentAmount = 0;
        this.warningThresholdRate = warningThresholdRate != null
                ? warningThresholdRate
                : new BigDecimal("80.00");
    }

    public void updateBudgetAmount(Integer monthlyBudgetAmount) {
        this.monthlyBudgetAmount = monthlyBudgetAmount;
    }

    public void addSpent(int amount) {
        this.spentAmount += amount;
    }
}
