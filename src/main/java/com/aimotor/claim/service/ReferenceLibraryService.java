package com.aimotor.claim.service;

import com.aimotor.claim.model.ClaimAssessment;
import com.aimotor.claim.model.CustomerVehicle;
import com.aimotor.claim.model.ReferenceDamage;
import com.aimotor.claim.repository.ClaimAssessmentRepository;
import com.aimotor.claim.repository.CustomerVehicleRepository;
import com.aimotor.claim.repository.ReferenceDamageRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Quan ly Kho anh mau du tru: them/xoa/lay danh sach + nap anh gui Gemini.
 *
 * Anh mau MOI chi vao kho bang 2 duong:
 * (1) Tu ho so tham dinh cua khach: sau khi AI dinh gia xong, chu he thong bam
 * "Luu vao kho mau" — toan bo anh + thong tin xe lien quan duoc luu lai de
 * doi chieu cho cac vu tuong tu sau nay (ke ca khac loai xe).
 * (2) Quet folder storage/reference-damages (anh chu he thong tu tha vao).
 * KHONG them anh mau thu cong tung cai tren giao dien nua.
 */
@Service
public class ReferenceLibraryService {

    private static final Logger log = LoggerFactory.getLogger(ReferenceLibraryService.class);

    private final ReferenceDamageRepository repository;
    private final ClaimAssessmentRepository claimRepository;
    private final CustomerVehicleRepository vehicleRepository;
    private final ImagePreprocessingService imageService;
    private final GeminiService geminiService;
    private final ObjectMapper objectMapper;

    @Value("${app.storage.reference-dir:./storage/reference-damages}")
    private String referenceDir;

    public ReferenceLibraryService(ReferenceDamageRepository repository,
                                   ClaimAssessmentRepository claimRepository,
                                   CustomerVehicleRepository vehicleRepository,
                                   ImagePreprocessingService imageService,
                                   GeminiService geminiService,
                                   ObjectMapper objectMapper) {
        this.repository = repository;
        this.claimRepository = claimRepository;
        this.vehicleRepository = vehicleRepository;
        this.imageService = imageService;
        this.geminiService = geminiService;
        this.objectMapper = objectMapper;
    }

    public List<ReferenceDamage> listAll() {
        return repository.findAll();
    }

    /**
     * Luu 1 ho so tham dinh da dinh gia vao Kho anh mau.
     *
     * Moi vet hu hai trong ho so thanh 1 anh mau: anh lay tu anh khach gui cua
     * ho so (theo thu tu, xoay vong neu it anh hon vet), gia = chi phi AI uoc
     * tinh cho vet do, mo ta ghi ro thong tin xe (loai xe, hang, dong, nam,
     * mau) + tinh trang quan sat + ma ho so goc de doi soat. Muc do lay theo
     * muc do cua anh mau ma AI doi chieu gan nhat (mac dinh Muc 2 neu khong co).
     * Vet nao gia 0 thi luu o dang nhap (can bo sung gia sau).
     * Anh goc duoc COPY sang storage/reference-damages/ de anh mau tu dung,
     * khong phu thuoc vao thu muc ho so cua khach. Anh da co trong kho (trung
     * noi dung, kiem tra bang SHA-256) thi bo qua, khong luu trung.
     */
    public List<ReferenceDamage> saveClaimToLibrary(String claimId) throws IOException {
        ClaimAssessment claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new IllegalArgumentException("Khong tim thay ho so " + claimId));
        if (claim.getDamages() == null || claim.getDamages().isEmpty()) {
            throw new IllegalStateException("Ho so nay khong co vet hu hai de luu vao kho mau.");
        }
        List<String> photos = claim.getClaimPhotoPaths() == null ? List.of() : claim.getClaimPhotoPaths();
        if (photos.isEmpty()) {
            throw new IllegalStateException("Ho so nay khong co anh de luu vao kho mau.");
        }
        CustomerVehicle vehicle = claim.getVehicleId() == null ? null
                : vehicleRepository.findById(claim.getVehicleId()).orElse(null);
        String vehicleInfo = vehicle == null
                ? "Xe chua ro thong tin"
                : (vehicle.getVehicleType() + " " + nullToEmpty(vehicle.getBrand()) + " "
                   + nullToEmpty(vehicle.getModel())
                   + (vehicle.getYear() == 0 ? "" : " " + vehicle.getYear())
                   + " (mau " + vehicle.getColor() + ")").trim().replaceAll(" +", " ");
        Files.createDirectories(Paths.get(referenceDir));
        java.util.Set<String> knownHashes = repository.findAll().stream()
                .map(ReferenceDamage::getImageHash)
                .filter(h -> h != null)
                .collect(java.util.stream.Collectors.toSet());
        List<ReferenceDamage> saved = new ArrayList<>();
        List<ClaimAssessment.DamageItem> damages = claim.getDamages();
        for (int i = 0; i < damages.size(); i++) {
            ClaimAssessment.DamageItem d = damages.get(i);
            Path source = Paths.get(photos.get(i % photos.size()));
            if (!Files.exists(source)) {
                log.warn("Bo qua vet {} cua ho so {}: khong tim thay anh {}", d.getPartName(), claimId, source);
                continue;
            }
            byte[] bytes = Files.readAllBytes(source);
            String hash = sha256(bytes);
            if (knownHashes.contains(hash)) {
                log.info("Bo qua vet {} cua ho so {}: anh da co trong kho.", d.getPartName(), claimId);
                continue;
            }
            String ext = "jpg";
            String name = source.getFileName().toString();
            int dot = name.lastIndexOf('.');
            if (dot > 0 && dot < name.length() - 1) {
                ext = name.substring(dot + 1).toLowerCase().replaceAll("[^a-z0-9]", "");
                if (ext.isEmpty()) ext = "jpg";
            }
            String fileName = "ref-" + claim.getClaimId().toLowerCase() + "-" + (i + 1) + "." + ext;
            Path target = Paths.get(referenceDir, fileName);
            Files.copy(new java.io.ByteArrayInputStream(bytes), target, StandardCopyOption.REPLACE_EXISTING);
            String imagePath = Paths.get(referenceDir, fileName).toString().replace("\\", "/");
            if (imagePath.startsWith("./")) imagePath = imagePath.substring(2);

            int severityLevel = 2;
            if (d.getMatchedReferenceId() != null && !d.getMatchedReferenceId().isBlank()) {
                severityLevel = repository.findByReferenceId(d.getMatchedReferenceId())
                        .map(ReferenceDamage::getSeverityLevel).orElse(2);
            }
            String severityLabel = severityLevel == 1 ? "Mức 1: Xước nhẹ"
                    : severityLevel == 2 ? "Mức 2: Móp vừa"
                    : "Mức 3: Vỡ/Biến dạng nặng";
            StringBuilder desc = new StringBuilder("Case thuc te — ").append(vehicleInfo)
                    .append(": ").append(d.getObservedCondition() == null ? "" : d.getObservedCondition())
                    .append(" (ho so ").append(claim.getClaimId())
                    .append(", boi thuong de xuat ").append(d.getEstimatedCostVnd()).append(" VND).");
            if (d.getReferenceComparisonNote() != null && !d.getReferenceComparisonNote().isBlank()) {
                desc.append(" Doi chieu: ").append(d.getReferenceComparisonNote());
            }
            ReferenceDamage ref = new ReferenceDamage(
                    nextReferenceId(), d.getPartName(), severityLevel, severityLabel,
                    desc.toString(), d.getEstimatedCostVnd(), imagePath);
            ref.setNeedsMetadata(d.getEstimatedCostVnd() <= 0);
            ref.setImageHash(hash);
            saved.add(repository.save(ref));
            knownHashes.add(hash);
        }
        if (saved.isEmpty()) {
            throw new IllegalStateException("Khong luu duoc anh mau nao (anh da co trong kho hoac thieu file anh).");
        }
        return saved;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** SHA-256 noi dung file anh (hex, chu thuong). */
    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("Khong tinh duoc hash anh.", e);
        }
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
     * Neu co file metadata kem theo (cung ten file, duoi .json, VD:
     * ref-05-seltos-thuy-kich.json) voi partName + standardPriceVnd > 0
     * thi anh duoc nhan thang vao kho chinh thuc, khong can bo sung tay.
     */
    public List<ReferenceDamage> scanFolder() throws IOException {
        Path dir = Paths.get(referenceDir);
        Files.createDirectories(dir);
        java.util.Set<String> knownPaths = repository.findAll().stream()
                .map(ReferenceDamage::getImagePath)
                .collect(java.util.stream.Collectors.toSet());
        java.util.Set<String> knownHashes = repository.findAll().stream()
                .map(ReferenceDamage::getImageHash)
                .filter(h -> h != null)
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
                // Cung anh nhung khac ten file -> bo qua (chan trung noi dung).
                String hash = sha256(Files.readAllBytes(file));
                if (knownHashes.contains(hash)) {
                    continue;
                }
                // File metadata kem theo (VD: ref-05-....json): du thong tin thi
                // nhan thang vao kho chinh thuc, khong can bo sung tay.
                String partName = "Chưa phân loại";
                int severityLevel = 2;
                String severityLabel = "Mức 2: Móp vừa";
                String description = "Ảnh tự quét từ folder — cần bổ sung bộ phận, mức độ và giá chuẩn trước khi dùng để thẩm định.";
                long price = 0;
                boolean complete = false;
                Path sidecar = file.resolveSibling(
                        file.getFileName().toString().replaceFirst("\\.[^.]+$", "") + ".json");
                if (Files.exists(sidecar)) {
                    try {
                        JsonNode meta = objectMapper.readTree(sidecar.toFile());
                        String p = meta.path("partName").asText("").trim();
                        long pr = meta.path("standardPriceVnd").asLong(0);
                        if (!p.isEmpty() && pr > 0) {
                            partName = p;
                            severityLevel = Math.min(3, Math.max(1, meta.path("severityLevel").asInt(2)));
                            severityLabel = meta.path("severityLabel").asText("").trim();
                            if (severityLabel.isEmpty()) {
                                severityLabel = severityLevel == 1 ? "Mức 1: Xước nhẹ"
                                        : severityLevel == 2 ? "Mức 2: Móp vừa"
                                        : "Mức 3: Vỡ/Biến dạng nặng";
                            }
                            description = meta.path("description").asText("");
                            price = pr;
                            complete = true;
                        }
                    } catch (Exception e) {
                        log.warn("Khong doc duoc file metadata {}: {}", sidecar, e.getMessage());
                    }
                }
                ReferenceDamage ref = new ReferenceDamage(
                        nextReferenceId(), partName, severityLevel, severityLabel,
                        description, price, normalized);
                ref.setNeedsMetadata(!complete);
                ref.setImageHash(hash);
                imported.add(repository.save(ref));
                knownPaths.add(normalized);
                knownHashes.add(hash);
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

    /** Goi y gia chuan cho 1 anh mau (thuong la anh nhap) dua tren anh mau cu da co gia. */
    public GeminiService.PriceSuggestion suggestPriceFor(String id) throws IOException {
        ReferenceDamage ref = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Khong tim thay anh mau " + id));
        List<GeminiService.ReferencePayload> payloads = loadPayloads();
        if (payloads.isEmpty()) {
            throw new IllegalStateException("Chua co anh mau cu nao co gia chuan de doi chieu.");
        }
        String newImageBase64 = imageService.fileToBase64(Paths.get(ref.getImagePath()));
        return geminiService.suggestPrice(newImageBase64, payloads);
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
