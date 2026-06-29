package com.sagongsa.backend.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;

class FileUploadServiceTest {

	@TempDir
	private Path tempDir;

	private FileUploadService fileUploadService;

	@BeforeEach
	void setUp() {
		fileUploadService = new FileUploadService();
		ReflectionTestUtils.setField(fileUploadService, "uploadDir", tempDir.toString());
		ReflectionTestUtils.setField(fileUploadService, "maxFileSize", DataSize.ofKilobytes(64));
		ReflectionTestUtils.setField(fileUploadService, "maxPixels", 10_000L);
		fileUploadService.init();
	}

	@Test
	void savesDecodedAndReencodedImage() throws Exception {
		byte[] png = imageBytes("png");

		String url = fileUploadService.saveImage(new MockMultipartFile("file", "profile.png", "image/png", png));

		assertThat(url).startsWith("/uploads/social/").endsWith(".png");
		Path savedFile = tempDir.resolve(url.replace("/uploads/", ""));
		assertThat(Files.exists(savedFile)).isTrue();
		assertThat(ImageIO.read(savedFile.toFile())).isNotNull();
	}

	@Test
	void rejectsContentTypeMismatch() throws Exception {
		byte[] png = imageBytes("png");

		MockMultipartFile file = new MockMultipartFile("file", "profile.png", "image/jpeg", png);

		assertThatThrownBy(() -> fileUploadService.saveImage(file))
			.isInstanceOf(FileUploadInvalidException.class)
			.hasMessageContaining("지원하지 않는 파일 형식");
	}

	@Test
	void rejectsMagicByteMismatch() {
		MockMultipartFile file = new MockMultipartFile("file", "profile.png", "image/png", "%PDF-1.7".getBytes());

		assertThatThrownBy(() -> fileUploadService.saveImage(file))
			.isInstanceOf(FileUploadInvalidException.class)
			.hasMessageContaining("지원하지 않는 파일 형식");
	}

	@Test
	void rejectsOversizedFile() throws Exception {
		ReflectionTestUtils.setField(fileUploadService, "maxFileSize", DataSize.ofBytes(8));
		byte[] png = imageBytes("png");

		MockMultipartFile file = new MockMultipartFile("file", "profile.png", "image/png", png);

		assertThatThrownBy(() -> fileUploadService.saveImage(file))
			.isInstanceOf(FileUploadInvalidException.class)
			.hasMessageContaining("파일 크기");
	}

	@Test
	void rejectsImageWhenPixelCountExceedsLimitBeforeSaving() throws Exception {
		ReflectionTestUtils.setField(fileUploadService, "maxPixels", 3L);
		byte[] png = imageBytes("png");

		MockMultipartFile file = new MockMultipartFile("file", "profile.png", "image/png", png);

		assertThatThrownBy(() -> fileUploadService.saveImage(file))
			.isInstanceOf(FileUploadInvalidException.class)
			.hasMessageContaining("이미지 해상도");
		try (var files = Files.list(tempDir.resolve("social"))) {
			assertThat(files).isEmpty();
		}
	}

	@Test
	void rejectsWebpUntilItCanBeSafelyReencoded() {
		byte[] webpHeader = new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
		MockMultipartFile file = new MockMultipartFile("file", "profile.webp", "image/webp", webpHeader);

		assertThatThrownBy(() -> fileUploadService.saveImage(file))
			.isInstanceOf(FileUploadInvalidException.class)
			.hasMessageContaining("지원하지 않는 파일 형식");
	}

	private byte[] imageBytes(String format) throws Exception {
		BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		ImageIO.write(image, format, outputStream);
		return outputStream.toByteArray();
	}
}
