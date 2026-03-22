package com.fourohfour.backend.modules.content.api.http;

import com.fourohfour.backend.modules.auth.domain.CurrentAuthenticatedUser;
import com.fourohfour.backend.modules.content.application.ContentService;
import com.fourohfour.backend.modules.content.application.ContentService.SaveContentCommand;
import com.fourohfour.backend.modules.content.application.ContentService.SaveContentResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/content-links")
public class ContentController {

    private final ContentService contentService;

    public ContentController(ContentService contentService) {
        this.contentService = contentService;
    }

    @PostMapping
    public SaveContentResult saveContent(
            Authentication authentication,
            @Valid @RequestBody SaveContentRequest request
    ) {
        return contentService.save(new SaveContentCommand(
                CurrentAuthenticatedUser.userId(authentication),
                request.url(),
                request.title(),
                request.note(),
                request.tags() == null ? List.of() : request.tags()
        ));
    }

    public record SaveContentRequest(
            @NotBlank(message = "링크는 비워둘 수 없어요.")
            String url,
            String title,
            String note,
            List<String> tags
    ) {
    }
}

