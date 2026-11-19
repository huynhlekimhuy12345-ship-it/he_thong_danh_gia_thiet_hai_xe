package com.aimotor.claim.service;

import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

/**
 * Danh gia do net cua anh bang phuong sai Laplacian (Variance of Laplacian).
 * Anh cang mo thi phuong sai cang thap. Ket qua chi la CANH BAO de Gemini
 * quyet dinh cuoi cung, khong tu dong loai anh.
 */
@Service
public class ImageSharpnessService {

    @Value("${app.image.sharpness-threshold:80.0}")
    private double threshold;

    /** Tra ve diem net; -1 neu khong doc duoc anh. */
    public double score(byte[] jpegBytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(jpegBytes));
            if (image == null) return -1;
            BufferedImage small = image.getWidth() > 320
                    ? Thumbnails.of(image).width(320).asBufferedImage()
                    : image;
            int w = small.getWidth();
            int h = small.getHeight();
            double[][] gray = new double[h][w];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int rgb = small.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;
                    gray[y][x] = 0.299 * r + 0.587 * g + 0.114 * b;
                }
            }
            // Laplacian kernel 3x3
            int[][] kernel = {{0, 1, 0}, {1, -4, 1}, {0, 1, 0}};
            double sum = 0;
            double sumSq = 0;
            int count = 0;
            for (int y = 1; y < h - 1; y++) {
                for (int x = 1; x < w - 1; x++) {
                    double v = 0;
                    for (int ky = -1; ky <= 1; ky++) {
                        for (int kx = -1; kx <= 1; kx++) {
                            v += kernel[ky + 1][kx + 1] * gray[y + ky][x + kx];
                        }
                    }
                    sum += v;
                    sumSq += v * v;
                    count++;
                }
            }
            if (count == 0) return -1;
            double mean = sum / count;
            return (sumSq / count) - (mean * mean);
        } catch (Exception e) {
            return -1;
        }
    }

    public boolean isBlurry(double score) {
        return score >= 0 && score < threshold;
    }

    public double getThreshold() {
        return threshold;
    }
}
