package com.marketPlace.MarketPlace.Service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloudinaryService {

    private final Cloudinary cloudinary;

    private static final long MAX_SIZE_BYTES  = 500 * 1024;       // 500KB
    private static final long MAX_VIDEO_BYTES = 100 * 1024 * 1024; // 100MB

    // ─────────────────────────────────────────────────────────
    // UPLOAD IMAGE
    // ─────────────────────────────────────────────────────────
    public Map<String, Object> uploadImage(MultipartFile file, String folder) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be null or empty");
        }

        byte[] imageBytes = file.getBytes();

        if (imageBytes.length > MAX_SIZE_BYTES) {
            log.info("Image is {}KB — compressing...", imageBytes.length / 1024);
            imageBytes = compressImage(imageBytes);
            log.info("Compressed to {}KB", imageBytes.length / 1024);
        } else {
            log.info("Image is {}KB — no compression needed", imageBytes.length / 1024);
        }

        String uniquePublicId = folder + "/" + UUID.randomUUID();

        @SuppressWarnings("unchecked")
        Map<String, Object> uploadResult = cloudinary.uploader().upload(
                imageBytes,
                ObjectUtils.asMap(
                        "public_id",     uniquePublicId,
                        "resource_type", "image",
                        "overwrite",     false,
                        "quality",       "auto",
                        "fetch_format",  "auto",
                        "flags",         "progressive"
                )
        );

        log.info("Uploaded image: public_id={}, url={}, size={}bytes, dims={}x{}",
                uploadResult.get("public_id"),
                uploadResult.get("secure_url"),
                uploadResult.get("bytes"),
                uploadResult.get("width"),
                uploadResult.get("height"));

        return uploadResult;
    }

    // ─────────────────────────────────────────────────────────
    // DELETE IMAGE
    // ─────────────────────────────────────────────────────────
    public void deleteImage(String publicId) throws IOException {
        if (publicId == null || publicId.isBlank()) {
            log.warn("deleteImage called with null or blank publicId — skipping");
            return;
        }
        log.info("Deleting image from Cloudinary: {}", publicId);
        Map result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        log.info("Delete result for [{}]: {}", publicId, result.get("result"));
    }

    // ─────────────────────────────────────────────────────────
    // UPLOAD VIDEO
    // ─────────────────────────────────────────────────────────
    public Map<String, Object> uploadVideo(MultipartFile file, String folder) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Video file cannot be null or empty");
        }

        // Basic MIME type guard — reject anything clearly not a video
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("video/")) {
            throw new IllegalArgumentException(
                    "Invalid file type [" + contentType + "] — only video files are accepted");
        }

        long fileSizeBytes = file.getSize();
        if (fileSizeBytes > MAX_VIDEO_BYTES) {
            throw new IllegalArgumentException(
                    "Video exceeds maximum allowed size of 100MB (got " + fileSizeBytes / (1024 * 1024) + "MB)");
        }

        log.info("Uploading video: name={}, size={}MB, type={}",
                file.getOriginalFilename(),
                fileSizeBytes / (1024 * 1024),
                contentType);

        String uniquePublicId = folder + "/" + UUID.randomUUID();

        @SuppressWarnings("unchecked")
        Map<String, Object> uploadResult = cloudinary.uploader().upload(
                file.getBytes(),
                ObjectUtils.asMap(
                        "public_id",     uniquePublicId,
                        "resource_type", "video",   // must be "video" for Cloudinary to process it
                        "overwrite",     false,
                        "quality",       "auto",
                        "fetch_format",  "auto"
                )
        );

        log.info("Uploaded video: public_id={}, url={}, size={}bytes, duration={}s, dims={}x{}",
                uploadResult.get("public_id"),
                uploadResult.get("secure_url"),
                uploadResult.get("bytes"),
                uploadResult.get("duration"),
                uploadResult.get("width"),
                uploadResult.get("height"));

        return uploadResult;
    }

    // ─────────────────────────────────────────────────────────
    // DELETE VIDEO
    // ─────────────────────────────────────────────────────────
    public void deleteVideo(String publicId) throws IOException {
        if (publicId == null || publicId.isBlank()) {
            log.warn("deleteVideo called with null or blank publicId — skipping");
            return;
        }
        log.info("Deleting video from Cloudinary: {}", publicId);

        // resource_type MUST be "video" — destroy() defaults to "image" and will
        // return "not found" silently if you forget this for video public_ids
        @SuppressWarnings("unchecked")
        Map<String, Object> result = cloudinary.uploader().destroy(
                publicId,
                ObjectUtils.asMap("resource_type", "video")
        );

        String outcome = (String) result.get("result");
        if (!"ok".equals(outcome)) {
            log.warn("Cloudinary video delete for [{}] returned unexpected result: {}", publicId, outcome);
        } else {
            log.info("Video deleted successfully: {}", publicId);
        }
    }

    // ─────────────────────────────────────────────────────────
    // GET OPTIMIZED URL
    // ─────────────────────────────────────────────────────────
    public String getOptimizedImageUrl(String publicId) {
        String url = cloudinary.url()
                .transformation(new com.cloudinary.Transformation()
                        .quality("auto")
                        .fetchFormat("auto"))
                .generate(publicId);
        log.debug("Optimized URL for [{}]: {}", publicId, url);
        return url;
    }

    // ─────────────────────────────────────────────────────────
    // LIST IMAGES IN FOLDER
    // ─────────────────────────────────────────────────────────
    @SuppressWarnings("unchecked")
    public List<Map> listAllImagesInFolder(String folder) throws Exception {
        log.info("Listing images in Cloudinary folder: {}", folder);
        Map result = cloudinary.api().resources(
                ObjectUtils.asMap(
                        "type",        "upload",
                        "prefix",      folder,
                        "max_results", 100
                )
        );
        List<Map> resources = (List<Map>) result.get("resources");
        log.info("Found {} images in folder [{}]", resources.size(), folder);
        return resources;
    }

    // ─────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────

    private byte[] compressImage(byte[] originalBytes) throws IOException {
        BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(originalBytes));

        if (bufferedImage == null) {
            log.warn("Could not read image for compression — uploading as-is");
            return originalBytes;
        }

        // Convert ARGB/transparent PNG to RGB for JPEG compression
        if (bufferedImage.getType() == BufferedImage.TYPE_4BYTE_ABGR
                || bufferedImage.getType() == BufferedImage.TYPE_INT_ARGB) {
            BufferedImage rgbImage = new BufferedImage(
                    bufferedImage.getWidth(),
                    bufferedImage.getHeight(),
                    BufferedImage.TYPE_INT_RGB
            );
            Graphics2D g = rgbImage.createGraphics();
            g.drawImage(bufferedImage, 0, 0, Color.WHITE, null);
            g.dispose();
            bufferedImage = rgbImage;
        }

        float quality = 0.85f;
        byte[] compressed = originalBytes;

        while (compressed.length > MAX_SIZE_BYTES && quality > 0.1f) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            var jpegWriter = ImageIO.getImageWritersByFormatName("jpeg").next();
            var jpegParams = jpegWriter.getDefaultWriteParam();
            jpegParams.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
            jpegParams.setCompressionQuality(quality);

            try (var ios = ImageIO.createImageOutputStream(baos)) {
                jpegWriter.setOutput(ios);
                jpegWriter.write(null,
                        new javax.imageio.IIOImage(bufferedImage, null, null),
                        jpegParams);
            }
            jpegWriter.dispose();
            compressed = baos.toByteArray();
            quality -= 0.1f;
        }

        if (compressed.length > MAX_SIZE_BYTES) {
            log.warn("Could not compress image below 500KB — uploading at minimum quality");
        }

        return compressed;
    }
}