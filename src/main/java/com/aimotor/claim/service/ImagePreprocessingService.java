package com.aimotor.claim.service;

import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.UUID;

/**
 * Tien xu ly anh truoc khi gui AI:
 * resize chieu ngang toi da ve 1024px, nen JPG 85%, roi ma hoa Base64.
 */
@Service
public class ImagePreprocessingService {

    @Value("${app.image.max-width:1024}")
    private int maxWidth;

    @Value("${app.image.jpeg-quality:0.85}")
    private double jpegQuality;

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024; // 10MB

    public void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File anh rong.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Anh " + file.getOriginalFilename() + " vuot qua 10MB.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw new IllegalArgumentException("File " + file.getOriginalFilename() + " khong phai la anh.");
        }
    }

    /** Resize (neu rong hon maxWidth) + nen JPG 85% -> byte[]. */
    public byte[] toJpegBytes(byte[] originalBytes) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(originalBytes));
        if (image == null) {
            throw new IllegalArgumentException("Khong doc duoc file anh (dinh dang khong ho tro).");
        }
        BufferedImage processed = image;
        if (image.getWidth() > maxWidth) {
            processed = Thumbnails.of(image).width(maxWidth).asBufferedImage();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thumbnails.of(processed)
                .scale(1.0)
                .outputFormat("jpg")
                .outputQuality(jpegQuality)
                .toOutputStream(out);
        return out.toByteArray();
    }

    public byte[] toJpegBytes(MultipartFile file) throws IOException {
        return toJpegBytes(file.getBytes());
    }

    public String toBase64(MultipartFile file) throws IOException {
        return Base64.getEncoder().encodeToString(toJpegBytes(file));
    }

    public String fileToBase64(Path imagePath) throws IOException {
        return Base64.getEncoder().encodeToString(toJpegBytes(Files.readAllBytes(imagePath)));
    }

    /** Luu file goc vao thu muc chi dinh, tra ve duong dan tuong doi. */
    public String storeFile(MultipartFile file, Path dir) throws IOException {
        Files.createDirectories(dir);
        String original = file.getOriginalFilename() == null ? "photo.jpg" : file.getOriginalFilename();
        String ext = "jpg";
        int dot = original.lastIndexOf('.');
        if (dot > 0 && dot < original.length() - 1) {
            ext = original.substring(dot + 1).toLowerCase().replaceAll("[^a-z0-9]", "");
            if (ext.isEmpty()) ext = "jpg";
        }
        String fileName = UUID.randomUUID() + "." + ext;
        Path target = dir.resolve(fileName);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return dir.resolve(fileName).toString().replace("\\", "/");
    }
}
