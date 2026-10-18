package com.buildmybundle.catalog.admin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.buildmybundle.shared.audit.AuditLog;
import com.buildmybundle.shared.error.ApiException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Product photos uploaded by admins, stored on local disk (a Docker volume in production) and served under
 * {@code /media}. Files are recognised by their content (JPEG, PNG or WebP signature), never by the name or
 * the declared type, and are stored under a generated name.
 */
@Service
public class ProductImageService {

	private static final Logger log = LoggerFactory.getLogger(ProductImageService.class);
	public static final String URL_PREFIX = "/media/";
	static final long MAX_BYTES = 5L * 1024 * 1024;
	private static final int MAX_IMAGES = 12;

	public record AdminImage(Long id, String url, String altText, String color, int sortOrder, boolean uploaded) {
	}

	private final JdbcClient jdbc;
	private final AuditLog audit;
	private final Path mediaDir;

	public ProductImageService(JdbcClient jdbc, AuditLog audit, @Value("${app.media.dir:./.data/media}") String mediaDir) {
		this.jdbc = jdbc;
		this.audit = audit;
		this.mediaDir = Path.of(mediaDir).toAbsolutePath().normalize();
	}

	public Path mediaDir() {
		return mediaDir;
	}

	@Transactional(readOnly = true)
	public List<AdminImage> list(Long productId) {
		return jdbc.sql("SELECT id, url, alt_text, color, sort_order, stored_name FROM product_images WHERE product_id = :id ORDER BY sort_order, id")
			.param("id", productId)
			.query((rs, n) -> new AdminImage(rs.getLong("id"), rs.getString("url"), rs.getString("alt_text"),
					rs.getString("color"), rs.getInt("sort_order"), rs.getString("stored_name") != null))
			.list();
	}

	@Transactional
	public List<AdminImage> upload(Long productId, MultipartFile file, String altText, String color) {
		requireProduct(productId);
		if (file == null || file.isEmpty()) {
			throw ApiException.badRequest("IMAGE_REQUIRED", "Choose an image to upload.");
		}
		if (file.getSize() > MAX_BYTES) {
			throw ApiException.badRequest("IMAGE_TOO_LARGE", "Images can be at most 5 MB.");
		}
		if (list(productId).size() >= MAX_IMAGES) {
			throw ApiException.conflict("TOO_MANY_IMAGES", "A product can have at most " + MAX_IMAGES + " images.");
		}
		String extension;
		try (InputStream in = file.getInputStream()) {
			extension = imageType(in.readNBytes(12));
		}
		catch (IOException ex) {
			throw ApiException.badRequest("IMAGE_UNREADABLE", "The file could not be read.");
		}
		if (extension == null) {
			throw ApiException.badRequest("IMAGE_TYPE", "Upload a JPEG, PNG or WebP image.");
		}
		String storedName = "products/" + productId + "-" + UUID.randomUUID() + "." + extension;
		Path target = mediaDir.resolve(storedName).normalize();
		try {
			Files.createDirectories(target.getParent());
			try (InputStream in = file.getInputStream()) {
				Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		catch (IOException ex) {
			log.error("Could not store an uploaded image at {}", target, ex);
			throw ApiException.conflict("IMAGE_STORAGE", "The image could not be saved. Please try again.");
		}
		Long id = jdbc.sql("""
				INSERT INTO product_images (product_id, url, alt_text, color, sort_order, stored_name)
				VALUES (:product, :url, :alt, :color,
				        (SELECT coalesce(max(sort_order), -1) + 1 FROM product_images WHERE product_id = :product), :stored)
				RETURNING id
				""")
			.param("product", productId)
			.param("url", URL_PREFIX + storedName)
			.param("alt", blankToNull(altText, 200))
			.param("color", blankToNull(color, 40))
			.param("stored", storedName)
			.query(Long.class)
			.single();
		audit.record("PRODUCT_IMAGE_ADDED", "PRODUCT", productId, Map.of("imageId", id));
		return list(productId);
	}

	/** Makes an image the first one shown (on cards and at the top of the gallery). */
	@Transactional
	public List<AdminImage> makePrimary(Long productId, Long imageId) {
		image(productId, imageId);
		jdbc.sql("UPDATE product_images SET sort_order = sort_order + 1 WHERE product_id = :product")
			.param("product", productId)
			.update();
		jdbc.sql("UPDATE product_images SET sort_order = 0 WHERE id = :id").param("id", imageId).update();
		audit.record("PRODUCT_IMAGE_PRIMARY", "PRODUCT", productId, Map.of("imageId", imageId));
		return list(productId);
	}

	@Transactional
	public List<AdminImage> delete(Long productId, Long imageId) {
		String storedName = image(productId, imageId);
		jdbc.sql("DELETE FROM product_images WHERE id = :id").param("id", imageId).update();
		if (storedName != null) {
			try {
				Files.deleteIfExists(mediaDir.resolve(storedName).normalize());
			}
			catch (IOException ex) {
				log.warn("Could not delete image file {}", storedName, ex);
			}
		}
		audit.record("PRODUCT_IMAGE_REMOVED", "PRODUCT", productId, Map.of("imageId", imageId));
		return list(productId);
	}

	/** "jpg", "png" or "webp" from the file signature, or null for anything else. */
	static String imageType(byte[] head) {
		if (head.length >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
			return "jpg";
		}
		byte[] png = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };
		if (head.length >= 8 && Arrays.equals(Arrays.copyOf(head, 8), png)) {
			return "png";
		}
		if (head.length >= 12 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F' && head[8] == 'W'
				&& head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
			return "webp";
		}
		return null;
	}

	private String image(Long productId, Long imageId) {
		return jdbc.sql("SELECT stored_name FROM product_images WHERE id = :id AND product_id = :product")
			.param("id", imageId)
			.param("product", productId)
			.query((rs, n) -> java.util.Optional.ofNullable(rs.getString("stored_name")))
			.optional()
			.orElseThrow(() -> ApiException.notFound("IMAGE_NOT_FOUND", "This image does not exist."))
			.orElse(null);
	}

	private void requireProduct(Long productId) {
		if (jdbc.sql("SELECT count(*) FROM products WHERE id = :id").param("id", productId).query(Long.class).single() == 0) {
			throw ApiException.notFound("PRODUCT_NOT_FOUND", "This product does not exist.");
		}
	}

	private static String blankToNull(String value, int max) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
	}
}
