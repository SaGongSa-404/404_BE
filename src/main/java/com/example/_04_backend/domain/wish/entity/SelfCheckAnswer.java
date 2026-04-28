package com.example._04_backend.domain.wish.entity;

import com.example._04_backend.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "self_check_answers",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_self_check_answers_response_question",
                columnNames = {"response_set_id", "question_code"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SelfCheckAnswer extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "response_set_id", nullable = false)
    private SelfCheckResponseSet responseSet;

    @Column(nullable = false, length = 80)
    private String questionCode;

    @Column(nullable = false)
    private boolean answerBoolean;

    @Builder
    public SelfCheckAnswer(SelfCheckResponseSet responseSet, String questionCode, boolean answerBoolean) {
        this.responseSet = responseSet;
        this.questionCode = questionCode;
        this.answerBoolean = answerBoolean;
    }
}
