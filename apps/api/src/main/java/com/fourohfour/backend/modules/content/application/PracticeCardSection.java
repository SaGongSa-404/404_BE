package com.fourohfour.backend.modules.content.application;

import java.util.List;

public record PracticeCardSection(
        String title,
        String body,
        List<String> items
) {
}

