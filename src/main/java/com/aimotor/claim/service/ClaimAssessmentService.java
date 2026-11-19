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

    @Value("${app.assessment.min-photos:2}")
    private int minPhotos;

    public ClaimAssessmentService(VehicleService vehicleService,
                                  ReferenceLibraryService referenceService,
                                  ImagePreprocessingService imageService,
                                  ImageSharpnessService sharpnessService,
                                  GeminiService geminiService,
                                  ClaimAssessmentRepository repository) {
        this.vehicleService = vehicleService;
        this.referenceService = referenceService;
        this.imageService = imageService;
        this.sharpnessService = sharpnessService;
        this.geminiService = geminiService;
        this.repository = repository;
    }

    public ClaimAssessment assess(String licensePlate, String customerPhone, String vehicleType,
                                  MultipartFile[] photos) throws IOException {
        String plate = VehicleService.normalizePlate(licensePlate);
        String phone = VehicleService.normalizePhone(customerPhone);

        ClaimAssessment assessment = new ClaimAssessment();
        assessment.setClaimId(generateClaimId());
        assessment.setLicensePlate(plate);
        assessment.setCustomerPhone(phone);

        // ---- Buoc 1: xac minh danh tinh xe ----
        ClaimAssessment.IdentityCheck identity = new ClaimAssessment.IdentityCheck();
        assessment.setIdentityCheck(identity);
        var vehicleOpt = vehicleService.findByPlate(plate);
        if (vehicleOpt.isEmpty()) {
            identity.setPlateMatch(false);
            identity.setPassed(false);
            identity.setReason("Khong tim thay ho so xe co bien so " + plate
                    + ". Hay dang ky ho so xe (kem anh goc) truoc khi gui tham dinh.");
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
            StringBuilder reason = new StringBuilder("Thong tin khong khop voi ho so xe goc: ");
            if (!identity.isPhoneMatch()) reason.append("SDT khong khop. ");
            if (!identity.isTypeMatch()) reason.append("Loai xe khong khop (ho so: ")
                    .append(vehicle.getVehicleType()).append("). ");
            reason.append("Chi xe dung bien so + dung SDT + dung loai xe moi duoc tham dinh.");
            identity.setReason(reason.toString());
            return saveRejected(assessment, "REJECTED", List.of(identity.getReason()), List.of());
        }
        identity.setReason("Khop bien so, SDT va loai xe voi ho so goc. Mau goc: " + vehicle.getColor() + ".");

        // ---- Buoc 2: kiem tra anh dau vao ----
        if (photos == null || photos.length == 0) {
            return saveRejected(assessment, "NEEDS_MORE_INFO",
                    List.of("Chua co anh nao duoc gui len. Hay chup theo checklist: 4 goc ngoai that, khoang truoc, khoang sau, cop, bien so va can canh tung vet."),
                    List.of("Anh toan xe 4 goc", "Anh khoang truoc", "Anh khoang sau", "Anh cop", "Anh bien so", "Anh can canh vet hu hai"));
        }
        if (photos.length < minPhotos) {
            return saveRejected(assessment, "NEEDS_MORE_INFO",
                    List.of("Chi co " + photos.length + " anh, can it nhat " + minPhotos
                            + " anh de danh gia. Hay bo sung theo checklist."),
                    List.of("Anh toan xe 4 goc", "Anh khoang truoc", "Anh khoang sau", "Anh can canh vet hu hai"));
        }
        List<String> customerBase64 = new ArrayList<>();
        List<String> blurryHints = new ArrayList<>();
        for (int i = 0; i < photos.length; i++) {
            MultipartFile photo = photos[i];
            imageService.validateImage(photo);
            byte[] jpeg = imageService.toJpegBytes(photo);
            double sharpness = sharpnessService.score(jpeg);
            if (sharpnessService.isBlurry(sharpness)) {
                blurryHints.add("Anh khach " + (i + 1) + " co dau hieu mo (diem net "
                        + String.format("%.1f", sharpness) + ", nguong " + sharpnessService.getThreshold() + ")");
            }
            customerBase64.add(java.util.Base64.getEncoder().encodeToString(jpeg));
        }

        // ---- Buoc 3: nap anh mau + anh goc, goi Gemini ----
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
                vehicle, references, baselineBase64, customerBase64, blurryHints);

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

        List<ClaimAssessment.DamageItem> damages = new ArrayList<>();
        long total = 0;
        if ("OK".equals(status) && result.getDamages() != null) {
            for (GeminiAssessmentResult.Damage d : result.getDamages()) {
                ClaimAssessment.DamageItem item = new ClaimAssessment.DamageItem();
                item.setPartName(d.getPartName());
                item.setObservedCondition(d.getObservedCondition());
                item.setMatchedReferenceId(d.getMatchedReferenceId());
                item.setReferenceComparisonNote(d.getReferenceComparisonNote());
                item.setEstimatedCostVnd(d.getEstimatedCostVnd());
                item.setConfidenceScore(d.getConfidenceScore());
                damages.add(item);
                total += d.getEstimatedCostVnd();
            }
        }
        assessment.setDamages(damages);
        // Tong tien = tong cac vet sau khi gop trung lap (tin tong tu Gemini chi khi khop).
        assessment.setTotalCompensationVnd("OK".equals(status) ? total : 0);
        return repository.save(assessment);
    }

    private ClaimAssessment saveRejected(ClaimAssessment assessment, String status,
                                         List<String> issues, List<String> missingPhotos) {
        assessment.setValidationStatus(status);
        assessment.setValidationIssues(issues);
        assessment.setMissingPhotos(missingPhotos);
        assessment.setDamages(List.of());
        assessment.setTotalCompensationVnd(0);
        assessment.setRequiresHumanReview(false);
        assessment.setHumanReviewReason("");
        assessment.setAssessmentSummary(status.equals("REJECTED")
                ? "Ho so bi tu choi o buoc kiem tra: " + String.join(" ", issues)
                : "Ho so chua du dieu kien tham dinh, can bo sung thong tin/anh.");
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

    public List<ClaimAssessment> historyByPlate(String plate) {
        return repository.findByLicensePlateOrderByCreatedAtDesc(VehicleService.normalizePlate(plate));
    }
}
