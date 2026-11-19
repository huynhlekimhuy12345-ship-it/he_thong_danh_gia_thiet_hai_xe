package com.aimotor.claim.service;

import com.aimotor.claim.model.ReferenceDamage;
import com.aimotor.claim.repository.ReferenceDamageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** Quan ly Kho anh mau du tru: them/xoa/lay danh sach + nap anh gui Gemini. */
@Service
public class ReferenceLibraryService {

    private final ReferenceDamageRepository repository;
    private final ImagePreprocessingService imageService;

    @Value("${app.storage.reference-dir:./storage/reference-damages}")
    private String referenceDir;

    public ReferenceLibraryService(ReferenceDamageRepository repository,
                                   ImagePreprocessingService imageService) {
        this.repository = repository;
        this.imageService = imageService;
    }

    public List<ReferenceDamage> listAll() {
        return repository.findAll();
    }

    public ReferenceDamage add(String referenceId, String partName, int severityLevel,
                               String severityLabel, String description, long standardPriceVnd,
                               MultipartFile image) throws IOException {
        if (repository.existsByReferenceId(referenceId)) {
            throw new IllegalArgumentException("Ma mau " + referenceId + " da ton tai.");
        }
        imageService.validateImage(image);
        String imagePath = imageService.storeFile(image, Paths.get(referenceDir));
        ReferenceDamage ref = new ReferenceDamage(referenceId, partName, severityLevel,
                severityLabel, description, standardPriceVnd, imagePath);
        return repository.save(ref);
    }

    public void delete(String id) {
        repository.findById(id).ifPresent(ref -> {
            repository.delete(ref);
            try {
                Files.deleteIfExists(Paths.get(ref.getImagePath()));
            } catch (IOException ignored) {
                // Xoa DB duoc la chinh, file rac co the don sau.
            }
        });
    }

    /**
     * Quet thu muc anh mau: file anh nao chua co trong DB se duoc nhan vao kho
     * o dang NHAP (needsMetadata = true) voi ma REF_xx tiep theo.
     * Anh nhap CHUA co gia chuan nen tam thoi khong dung de tham dinh.
     */
    public List<ReferenceDamage> scanFolder() throws IOException {
        Path dir = Paths.get(referenceDir);
        Files.createDirectories(dir);
        java.util.Set<String> knownPaths = repository.findAll().stream()
                .map(ReferenceDamage::getImagePath)
                .collect(java.util.stream.Collectors.toSet());
        List<ReferenceDamage> imported = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        return n.endsWith(".jpg") || n.endsWith(".jpeg")
                                || n.endsWith(".png") || n.endsWith(".webp");
                    })
                    .sorted()
                    .toList();
            for (Path file : files) {
                String rel = dir.resolve(file.getFileName()).toString().replace("\\", "/");
                String normalized = rel.startsWith("./") ? rel.substring(2) : rel;
                if (knownPaths.contains(rel) || knownPaths.contains(normalized)) {
                    continue;
                }
                ReferenceDamage ref = new ReferenceDamage(
                        nextReferenceId(),
                        "Chưa phân loại", 2, "Mức 2: Móp vừa",
                        "Ảnh tự quét từ folder — cần bổ sung bộ phận, mức độ và giá chuẩn trước khi dùng để thẩm định.",
                        0, normalized);
                ref.setNeedsMetadata(true);
                imported.add(repository.save(ref));
                knownPaths.add(normalized);
            }
        }
        return imported;
    }

    private String nextReferenceId() {
        int max = 0;
        for (ReferenceDamage r : repository.findAll()) {
            String id = r.getReferenceId();
            if (id != null && id.startsWith("REF_")) {
                try {
                    max = Math.max(max, Integer.parseInt(id.substring(4)));
                } catch (NumberFormatException ignored) {
                    // Bo qua ma khong theo dinh dang REF_xx
                }
            }
        }
        return String.format("REF_%02d", max + 1);
    }

    /** Bo sung / sua metadata cho anh mau (dung cho anh nhap sau khi quet folder). */
    public ReferenceDamage update(String id, String partName, int severityLevel,
                                  String severityLabel, String description, long standardPriceVnd) {
        ReferenceDamage ref = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Khong tim thay anh mau " + id));
        if (partName != null && !partName.isBlank()) ref.setPartName(partName.trim());
        ref.setSeverityLevel(severityLevel);
        if (severityLabel != null) ref.setSeverityLabel(severityLabel);
        if (description != null) ref.setDescription(description);
        ref.setStandardPriceVnd(standardPriceVnd);
        ref.setNeedsMetadata(false);
        return repository.save(ref);
    }

    /** Nap toan bo anh mau (resize + Base64) de gui kem trong 1 request Gemini. */
    public List<GeminiService.ReferencePayload> loadPayloads() throws IOException {
        List<GeminiService.ReferencePayload> payloads = new ArrayList<>();
        for (ReferenceDamage ref : repository.findAll()) {
            // Anh nhap chua co metadata (gia 0) thi bo qua, tranh AI dinh gia 0 dong.
            if (ref.isNeedsMetadata()) {
                continue;
            }
            Path path = Paths.get(ref.getImagePath());
            if (Files.exists(path)) {
                payloads.add(new GeminiService.ReferencePayload(ref, imageService.fileToBase64(path)));
            }
        }
        return payloads;
    }
}
