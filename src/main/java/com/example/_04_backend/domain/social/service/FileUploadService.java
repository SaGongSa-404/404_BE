package com.example._04_backend.domain.social.service;

import com.example._04_backend.global.error.BusinessException;
import com.example._04_backend.global.error.ErrorCode;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Service
public class FileUploadService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final String SUB_DIR = "social";

    @Value("${app.upload.dir}")
    private String uploadDir;

    private Path baseDir;

    @PostConstruct
    public void init() {
        try {
            baseDir = Paths.get(uploadDir, SUB_DIR).toAbsolutePath().normalize();
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new RuntimeException("업로드 디렉토리 생성 실패: " + uploadDir, e);
        }
    }

    public String saveImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "업로드할 파일이 없습니다.");
        }

        String original = file.getOriginalFilename();
        if (original == null || !original.contains(".")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "파일 이름이 유효하지 않습니다.");
        }

        String ext = original.substring(original.lastIndexOf('.') + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "지원하지 않는 파일 형식입니다. (jpg, jpeg, png, gif, webp만 가능)");
        }

        String filename = UUID.randomUUID() + "." + ext;
        Path target = baseDir.resolve(filename);

        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("파일 저장 실패", e);
        }

        return "/uploads/" + SUB_DIR + "/" + filename;
    }
}
