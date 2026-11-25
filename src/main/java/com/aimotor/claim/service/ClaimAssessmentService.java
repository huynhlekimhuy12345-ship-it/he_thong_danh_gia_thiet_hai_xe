package com.aimotor.claim.service;

import com.aimotor.claim.dto.GeminiAssessmentResult;
import com.aimotor.claim.model.ClaimAssessment;
import com.aimotor.claim.model.CustomerVehicle;
import com.aimotor.claim.repository.ClaimAssessmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Dieu phoi toan bo quy trinh tham dinh:
 * 1. Xac minh danh tinh xe (bien so + SDT + loai xe) voi ho so goc.
 * 2. Kiem tra anh dau vao (so luong, dinh dang, do net).
 * 3. Goi Gemini (kem anh mau + anh goc + anh khach) va luu lich su.
 */
@Service
public class ClaimAssessmentService {

    private static final Logger log = LoggerFactory.getLogger(ClaimAssessmentService.class);

    private final VehicleService vehicleService;
    private final ReferenceLibraryService referenceService;
    private final ImagePreprocessingService imageService;
    private final ImageSharpnessService sharpnessService;
    private final GeminiService geminiService;
    private final ClaimAssessmentRepository repository;
    private final AppConfigService appConfigService;
    private final VerificationAuditService auditService;

    @Value("${app.assessment.min-photos:2}")
    private int minPhotos;

    @Value("${app.storage.claim-dir:./storage/claims}")
    private String claimDir;

    public ClaimAssessmentService(VehicleService vehicleService,
                                  ReferenceLibraryService referenceService,
                                  ImagePreprocessingService imageService,
                                  ImageSharpnessService sharpnessService,
                                  GeminiService geminiService,
                                  ClaimAssessmentRepository repository,
                                  AppConfigService appConfigService,
                                  VerificationAuditService auditService) {
        this.vehicleService = vehicleService;
        this.referenceService = referenceService;
        this.imageService = imageService;
        this.sharpnessService = sharpnessService;
        this.geminiService = geminiService;
        this.repository = repository;
        this.appConfigService = appConfigService;
        this.auditService = auditService;
    }

    public ClaimAssessment assess(String licensePlate, String customerPhone, String vehicleType,
                                  MultipartFile[] photos, String customerNote) throws IOException {
        String plate = VehicleService.normalizePlate(licensePlate);
        String phone = VehicleService.normalizePhone(customerPhone);

        ClaimAssessment assessment = new ClaimAssessment();
        assessment.setClaimId(generateClaimId());
        assessment.setLicensePlate(plate);
        assessment.setCustomerPhone(phone);
        assessment.setCustomerNote(customerNote == null ? null : customerNote.trim());

        // ---- Buoc 1: xac minh danh tinh xe ----
        ClaimAssessment.IdentityCheck identity = new ClaimAssessment.IdentityCheck();
        assessment.setIdentityCheck(identity);
        var vehicleOpt = vehicleService.findByPlate(plate);
        if (vehicleOpt.isEmpty()) {
            identity.setPlateMatch(false);
            identity.setPassed(false);
            identity.setReason("Không tìm thấy hồ sơ xe có biển số " + plate
                    + ". Hãy đăng ký hồ sơ xe (kèm ảnh gốc) trước khi gửi thẩm định.");
            auditService.log("ASSESSMENT", plate, phone, vehicleType,
                    false, false, false, false, identity.getReason(), assessment.getClaimId());
            return saveRejected(assessment, "REJECTED",
                    List.of(identity.getReason()), List.of());
        }
        CustomerVehicle vehicle = vehicleOpt.get();
        assessment.setVehicleId(vehicle.getId());
        identity.setPlateMatch(true);
        identity.setPhoneMatch(phone.equals(vehicle.getCustomerPhone()));
        identity.setTypeMatch(vehicleType != null
                && vehicle.getVehicleType().equalsIgnoreCase(vehicleType.trim()));
        identity.setPassed(identity.isPlateMatch() && identity.isPhoneMatch() && identity.isTypeMatch());
        if (!identity.isPassed()) {
            StringBuilder reason = new StringBuilder("Thông tin không khớp với hồ sơ xe gốc: ");
            if (!identity.isPhoneMatch()) reason.append("SĐT không khớp. ");
            if (!identity.isTypeMatch()) reason.append("Loại xe không khớp (hồ sơ: ")
                    .append(vehicle.getVehicleType()).append("). ");
            reason.append("Chỉ xe đúng biển số + đúng SĐT + đúng loại xe mới được thẩm định.");
            identity.setReason(reason.toString());
            auditService.log("ASSESSMENT", plate, phone, vehicleType,
                    identity.isPlateMatch(), identity.isPhoneMatch(), identity.isTypeMatch(),
                    false, identity.getReason(), assessment.getClaimId());
            return saveRejected(assessment, "REJECTED", List.of(identity.getReason()), List.of());
        }
        identity.setReason("Khớp biển số, SĐT và loại xe với hồ sơ gốc. Màu gốc: " + vehicle.getColor() + ".");
        assessment.setOwnerName(vehicle.getOwnerName());
        auditService.log("ASSESSMENT", plate, phone, vehicleType,
                true, true, true, true, identity.getReason(), assessment.getClaimId());

        // ---- Buoc 2: kiem tra anh dau vao ----
        if (photos == null || photos.length == 0) {
            return saveRejected(assessment, "NEEDS_MORE_INFO",
                    List.of("Chưa có ảnh nào được gửi lên. Hãy chụp theo checklist: 4 góc ngoại thất, cốp, biển số và cận cảnh từng vết."),
                    List.of("Ảnh toàn xe 4 góc", "Ảnh cốp", "Ảnh biển số", "Ảnh cận cảnh vết hư hại"));
        }
        if (photos.length < minPhotos) {
            return saveRejected(assessment, "NEEDS_MORE_INFO",
                    List.of("Chỉ có " + photos.length + " ảnh, cần ít nhất " + minPhotos
                            + " ảnh để đánh giá. Hãy bổ sung theo checklist."),
                    List.of("Ảnh toàn xe 4 góc", "Ảnh cốp", "Ảnh cận cảnh vết hư hại"));
        }
        List<String> customerBase64 = new ArrayList<>();
        List<String> blurryHints = new ArrayList<>();
        List<String> claimPhotoPaths = new ArrayList<>();
        for (int i = 0; i < photos.length; i++) {
            MultipartFile photo = photos[i];
            imageService.validateImage(photo);
            // Luu anh goc cua khach xuong dia truoc khi gui AI: storage/claims/<ma ho so>/<uuid>.jpg
            // (anh gui AI chi la ban resize, anh goc luu o day de doi soat sau nay)
            claimPhotoPaths.add(imageService.storeFile(photo, Paths.get(claimDir, assessment.getClaimId())));
            byte[] jpeg = imageService.toJpegBytes(photo);
            double sharpness = sharpnessService.score(jpeg);
            if (sharpnessService.isBlurry(sharpness)) {
                blurryHints.add("Ảnh khách " + (i + 1) + " có dấu hiệu mờ (điểm nét "
                        + String.format("%.1f", sharpness) + ", ngưỡng " + sharpnessService.getThreshold() + ")");
            }
            customerBase64.add(java.util.Base64.getEncoder().encodeToString(jpeg));
        }

        // ---- Buoc 3: nap anh mau + anh goc, goi Gemini ----
        assessment.setClaimPhotoPaths(claimPhotoPaths);
        List<GeminiService.ReferencePayload> references = referenceService.loadPayloads();
        List<String> baselineBase64 = new ArrayList<>();
        for (String path : vehicle.getBaselinePhotoPaths()) {
            try {
                baselineBase64.add(imageService.fileToBase64(Paths.get(path)));
            } catch (Exception e) {
                log.warn("Khong doc duoc anh goc {}: {}", path, e.getMessage());
            }
        }
        GeminiAssessmentResult result = geminiService.assess(
                vehicle, references, baselineBase64, customerBase64, blurryHints,
                assessment.getCustomerNote());

        // ---- Buoc 4: ghi ket qua ----
        String status = result.getValidation() != null && result.getValidation().getStatus() != null
                ? result.getValidation().getStatus() : "OK";
        assessment.setValidationStatus(status);
        assessment.setValidationIssues(result.getValidation() != null && result.getValidation().getIssues() != null
                ? result.getValidation().getIssues() : List.of());
        assessment.setMissingPhotos(result.getValidation() != null && result.getValidation().getMissingPhotos() != null
                ? result.getValidation().getMissingPhotos() : List.of());
        assessment.setAssessmentSummary(result.getAssessmentSummary());
        assessment.setRequiresHumanReview(result.isRequiresHumanReview());
        assessment.setHumanReviewReason(result.getHumanReviewReason());
        assessment.setConsensusNote(result.getConsensusNote());

        List<ClaimAssessment.DamageItem> damages = new ArrayList<>();
        long total = 0;
        if ("OK".equals(status) && result.getDamages() != null) {
            for (GeminiAssessmentResult.Damage d : result.getDamages()) {
                ClaimAssessment.DamageItem item = new ClaimAssessment.DamageItem();
                item.setPartName(d.getPartName());
                item.setObservedCondition(d.getObservedCondition());
                item.setMatchedReferenceId(d.getMatchedReferenceId());
                item.setReferenceComparisonNote(d.getReferenceComparisonNote());
                item.setSeverityLevel(d.getSeverityLevel());
                item.setEstimatedCostVnd(d.getEstimatedCostVnd());
                item.setConfidenceScore(d.getConfidenceScore());
                damages.add(item);
                total += d.getEstimatedCostVnd();
            }
        }
        assessment.setDamages(damages);
        // Tong tien = tong cac vet sau khi gop trung lap (tin tong tu Gemini chi khi khop).
        // Tru muc mien thuong (mac dinh 500.000d/vu, tham chieu MoMo): so thuc nhan = max(0, tong - mien thuong).
        long deductible = "OK".equals(status) ? appConfigService.getDeductibleVnd() : 0;
        assessment.setTotalCompensationVnd("OK".equals(status) ? total : 0);
        assessment.setDeductibleVnd(deductible);
        assessment.setNetCompensationVnd("OK".equals(status) ? Math.max(0, total - deductible) : 0);

        // ---- Buoc 5: doi chieu voi lan tham dinh truoc cua cung xe ----
        // Cung 1 xe ma tong lech qua 1 trieu so voi lan truoc -> gan co de giam dinh vien
        // doi chieu (tranh truong hop chay lai cung bo anh ma ra so khac xa).
        if ("OK".equals(status)) {
            try {
                for (ClaimAssessment prev : repository.findByLicensePlateOrderByCreatedAtDesc(plate)) {
                    if (!"OK".equals(prev.getValidationStatus())) continue;
                    long diff = Math.abs(total - prev.getTotalCompensationVnd());
                    if (diff > 1_000_000) {
                        assessment.setRequiresHumanReview(true);
                        String prevDate = prev.getCreatedAt() == null ? "không rõ ngày"
                                : String.valueOf(prev.getCreatedAt()).substring(0, 10);
                        String flag = "Tổng chênh " + String.format("%,d", diff)
                                + " đ so với lần thẩm định trước (mã " + prev.getClaimId()
                                + ", ngày " + prevDate + ") — cần giám định viên đối chiếu.";
                        String reason = assessment.getHumanReviewReason();
                        assessment.setHumanReviewReason(reason == null || reason.isBlank() ? flag : reason + " " + flag);
                    }
                    break;
                }
            } catch (Exception e) {
                log.warn("Khong doi chieu duoc voi lan tham dinh truoc: {}", e.getMessage());
            }
        }
        return repository.save(assessment);
    }

    private ClaimAssessment saveRejected(ClaimAssessment assessment, String status,
                                         List<String> issues, List<String> missingPhotos) {
        assessment.setValidationStatus(status);
        assessment.setValidationIssues(issues);
        assessment.setMissingPhotos(missingPhotos);
        assessment.setDamages(List.of());
        assessment.setTotalCompensationVnd(0);
        assessment.setDeductibleVnd(0);
        assessment.setNetCompensationVnd(0);
        assessment.setRequiresHumanReview(false);
        assessment.setHumanReviewReason("");
        assessment.setAssessmentSummary(status.equals("REJECTED")
                ? "Hồ sơ bị từ chối ở bước kiểm tra: " + String.join(" ", issues)
                : "Hồ sơ chưa đủ điều kiện thẩm định, cần bổ sung thông tin/ảnh.");
        return repository.save(assessment);
    }

    private String generateClaimId() {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        int random = ThreadLocalRandom.current().nextInt(1000, 10000);
        return "CLM-" + date + "-" + random;
    }

    public List<ClaimAssessment> history() {
        return repository.findAll();
    }

    /** Lay 1 ho so day du de xem chi tiet: nhan ca id noi bo (UUID) lan ma ho so (CLM-...). */
    public ClaimAssessment getClaim(String key) {
        return repository.findById(key)
                .or(() -> repository.findByClaimId(key))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hồ sơ thẩm định."));
    }

    public List<ClaimAssessment> historyByPlate(String plate) {
        return repository.findByLicensePlateOrderByCreatedAtDesc(VehicleService.normalizePlate(plate));
    }
}
