package com.fourohfour.backend.api.facades.home;

import com.fourohfour.backend.modules.practice.application.PracticeCardService;
import com.fourohfour.backend.modules.practice.application.PracticeCardService.PracticeCardView;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class HomeFacadeService {

    private final PracticeCardService practiceCardService;

    public HomeFacadeService(PracticeCardService practiceCardService) {
        this.practiceCardService = practiceCardService;
    }

    public HomeView getHome(UUID userId, LocalDate targetDate) {
        List<PracticeCardView> deck = practiceCardService.getDeck(userId, targetDate);
        PracticeCardView recommendedCard = deck.stream()
                .filter(card -> "OPEN".equals(card.status()))
                .findFirst()
                .orElse(deck.isEmpty() ? null : deck.getFirst());

        int completedTodayCount = (int) deck.stream()
                .filter(card -> "DONE".equals(card.status()))
                .count();
        int openCardCount = (int) deck.stream()
                .filter(card -> "OPEN".equals(card.status()))
                .count();

        return new HomeView(
                targetDate,
                openCardCount,
                completedTodayCount,
                recommendedCard,
                deck
        );
    }

    public record HomeView(
            LocalDate date,
            int openCardCount,
            int completedTodayCount,
            PracticeCardView recommendedCard,
            List<PracticeCardView> cards
    ) {
    }
}

