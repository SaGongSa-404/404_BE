package com.sagongsa.backend.social;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
class FileUploadService {

	private static final Set<String> ALLOWED = Set.of("jpg", "jpeg", "png", "gif", "webp");
	private static final String SUB_DIR = "social";

	@Value("${app.upload.dir:./uploads}")
	private String uploadDir;

	private Path baseDir;

	@PostConstruct
	void init() {
		try {
			baseDir = Paths.get(uploadDir, SUB_DIR).toAbsolutePath().normalize();
			Files.createDirectories(baseDir);
		} catch (IOException e) {
			throw new RuntimeException("업로드 디렉토리 생성 실패: " + uploadDir, e);
		}
	}

	String saveImage(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("업로드할 파일이 없습니다.");
		}
		String original = file.getOriginalFilename();
		if (original == null || !original.contains(".")) {
			throw new IllegalArgumentException("파일 이름이 유효하지 않습니다.");
		}
		String ext = original.substring(original.lastIndexOf('.') + 1).toLowerCase();
		if (!ALLOWED.contains(ext)) {
			throw new IllegalArgumentException("지원하지 않는 파일 형식입니다. (jpg, jpeg, png, gif, webp만 가능)");
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
