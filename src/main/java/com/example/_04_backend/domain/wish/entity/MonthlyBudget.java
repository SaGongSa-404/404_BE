package com.example._04_backend.domain.wish.entity;

import com.example._04_backend.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "monthly_budgets",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "year_month"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MonthlyBudget {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 7)
    private String yearMonth;

    @Column(nullable = false)
    private Integer budgetAmount;

    @Column(nullable = false)
    private Integer spentAmount = 0;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public MonthlyBudget(User user, String yearMonth, Integer budgetAmount) {
        this.user = user;
        this.yearMonth = yearMonth;
        this.budgetAmount = budgetAmount;
        this.spentAmount = 0;
    }

    public void updateBudgetAmount(Integer budgetAmount) {
        this.budgetAmount = budgetAmount;
    }

    public void addSpent(int amount) {
        this.spentAmount += amount;
    }
}
