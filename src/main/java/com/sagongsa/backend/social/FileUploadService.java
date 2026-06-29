package com.sagongsa.backend.social;

import jakarta.annotation.PostConstruct;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

@Service
class FileUploadService {

	private static final String SUB_DIR = "social";
	private static final String SUPPORTED_FORMAT_MESSAGE = "지원하지 않는 파일 형식입니다. (jpg, jpeg, png, gif만 가능)";

	@Value("${app.upload.dir:./uploads}")
	private String uploadDir;

	@Value("${app.upload.max-file-size:5MB}")
	private DataSize maxFileSize;

	@Value("${app.upload.max-pixels:20000000}")
	private long maxPixels;

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
			throw new FileUploadInvalidException("업로드할 파일이 없습니다.");
		}
		String contentType = file.getContentType();
		String original = file.getOriginalFilename();
		if (original == null || !original.contains(".")) {
			throw new FileUploadInvalidException("파일 이름이 유효하지 않습니다.");
		}
		if (maxFileSize != null && file.getSize() > maxFileSize.toBytes()) {
			throw new FileUploadInvalidException("파일 크기가 너무 큽니다.");
		}

		try {
			byte[] bytes = file.getBytes();
			if (maxFileSize != null && bytes.length > maxFileSize.toBytes()) {
				throw new FileUploadInvalidException("파일 크기가 너무 큽니다.");
			}

			String ext = original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
			ImageFormat imageFormat = detectFormat(bytes);
			if (imageFormat == null
				|| !imageFormat.extensions().contains(ext)
				|| contentType == null
				|| !imageFormat.mimeTypes().contains(contentType.toLowerCase(Locale.ROOT))) {
				throw new FileUploadInvalidException(SUPPORTED_FORMAT_MESSAGE);
			}

			ImageDimensions dimensions = readDimensions(bytes, imageFormat);
			assertPixelLimit(dimensions.width(), dimensions.height());

			BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
			if (image == null) {
				throw new FileUploadInvalidException("이미지 파일을 읽을 수 없습니다.");
			}
			assertPixelLimit(image.getWidth(), image.getHeight());
			byte[] sanitizedBytes = encodeImage(image, imageFormat);
			String filename = UUID.randomUUID() + "." + imageFormat.extension();
			Path target = baseDir.resolve(filename);
			Files.write(target, sanitizedBytes, StandardOpenOption.CREATE_NEW);
			return "/uploads/" + SUB_DIR + "/" + filename;
		} catch (IOException e) {
			throw new RuntimeException("파일 저장 실패", e);
		}
	}

	private ImageDimensions readDimensions(byte[] bytes, ImageFormat imageFormat) {
		Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(imageFormat.readerFormat());
		if (!readers.hasNext()) {
			throw new FileUploadInvalidException(SUPPORTED_FORMAT_MESSAGE);
		}

		ImageReader reader = readers.next();
		try (ImageInputStream inputStream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
			if (inputStream == null) {
				throw new FileUploadInvalidException("이미지 파일을 읽을 수 없습니다.");
			}
			reader.setInput(inputStream, true, true);
			return new ImageDimensions(reader.getWidth(0), reader.getHeight(0));
		} catch (IOException | RuntimeException e) {
			throw new FileUploadInvalidException("이미지 파일을 읽을 수 없습니다.");
		} finally {
			reader.dispose();
		}
	}

	private void assertPixelLimit(int width, int height) {
		long pixels = (long) width * height;
		if (width <= 0 || height <= 0 || pixels > maxPixels) {
			throw new FileUploadInvalidException("이미지 해상도가 너무 큽니다.");
		}
	}

	private ImageFormat detectFormat(byte[] bytes) {
		if (startsWith(bytes, new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff})) {
			return ImageFormat.JPEG;
		}
		if (startsWith(bytes, new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a})) {
			return ImageFormat.PNG;
		}
		if (startsWith(bytes, new byte[] {0x47, 0x49, 0x46, 0x38, 0x37, 0x61})
			|| startsWith(bytes, new byte[] {0x47, 0x49, 0x46, 0x38, 0x39, 0x61})) {
			return ImageFormat.GIF;
		}
		return null;
	}

	private boolean startsWith(byte[] bytes, byte[] prefix) {
		if (bytes.length < prefix.length) {
			return false;
		}
		for (int index = 0; index < prefix.length; index++) {
			if (bytes[index] != prefix[index]) {
				return false;
			}
		}
		return true;
	}

	private byte[] encodeImage(BufferedImage image, ImageFormat imageFormat) throws IOException {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		boolean written = ImageIO.write(image, imageFormat.writerFormat(), outputStream);
		if (!written) {
			throw new FileUploadInvalidException(SUPPORTED_FORMAT_MESSAGE);
		}
		return outputStream.toByteArray();
	}

	private enum ImageFormat {
		JPEG("jpg", "jpg", "jpg", Set.of("jpg", "jpeg"), Set.of("image/jpeg")),
		PNG("png", "png", "png", Set.of("png"), Set.of("image/png")),
		GIF("gif", "gif", "gif", Set.of("gif"), Set.of("image/gif"));

		private final String extension;
		private final String writerFormat;
		private final String readerFormat;
		private final Set<String> extensions;
		private final Set<String> mimeTypes;

		ImageFormat(String extension, String writerFormat, String readerFormat, Set<String> extensions, Set<String> mimeTypes) {
			this.extension = extension;
			this.writerFormat = writerFormat;
			this.readerFormat = readerFormat;
			this.extensions = extensions;
			this.mimeTypes = mimeTypes;
		}

		String extension() {
			return extension;
		}

		String writerFormat() {
			return writerFormat;
		}

		String readerFormat() {
			return readerFormat;
		}

		Set<String> extensions() {
			return extensions;
		}

		Set<String> mimeTypes() {
			return mimeTypes;
		}
	}

	private record ImageDimensions(int width, int height) {
	}
}
