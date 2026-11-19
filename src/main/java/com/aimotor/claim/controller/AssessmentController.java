package com.aimotor.claim.controller;

import com.aimotor.claim.model.ClaimAssessment;
import com.aimotor.claim.service.ClaimAssessmentService;
import com.aimotor.claim.service.ReferenceLibraryService;
import com.aimotor.claim.service.VehicleService;
import com.aimotor.claim.service.VerificationAuditService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** API Tham dinh ho so boi thuong + lich su tham dinh + tra cuu boi thuong. */
@RestController
@RequestMapping("/api/assessments")
public class AssessmentController {

    private final ClaimAssessmentService service;
    private final VehicleService vehicleService;
    private final ReferenceLibraryService referenceService;
    private final VerificationAuditService auditService;

    public AssessmentController(ClaimAssessmentService service,
                                VehicleService vehicleService,
                                ReferenceLibraryService referenceService,
                                VerificationAuditService auditService) {
        this.service = service;
        this.vehicleService = vehicleService;
        this.referenceService = referenceService;
        this.auditService = auditService;
    }

    /**
     * Gui ho so tham dinh: bien so + SDT + loai xe + nhieu anh thiet hai.
     * He thong tu xac minh danh tinh xe, kiem tra anh, goi Gemini, luu lich su.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ClaimAssessment> assess(
            @RequestParam("licensePlate") String licensePlate,
            @RequestParam("customerPhone") String customerPhone,
            @RequestParam("vehicleType") String vehicleType,
            @RequestParam("photos") MultipartFile[] photos,
            @RequestParam(value = "customerNote", required = false) String customerNote) throws Exception {
        ClaimAssessment result = service.assess(licensePlate, customerPhone, vehicleType, photos, customerNote);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping
    public List<ClaimAssessment> history() {
        return service.history();
    }

    /**
     * Luu 1 ho so tham dinh da dinh gia vao Kho anh mau: moi vet hu hai thanh
     * 1 anh mau (anh khach gui + bo phan + muc do + gia uoc tinh + thong tin
     * xe). De AI doi chieu cho cac vu tuong tu sau nay, ke ca khac loai xe.
     */
    @PostMapping("/{id}/save-to-library")
    public Map<String, Object> saveToLibrary(@PathVariable String id) throws Exception {
        var saved = referenceService.saveClaimToLibrary(id);
        return Map.of("saved", saved.size(), "references", saved);
    }

    @GetMapping("/plate/{plate}")
    public List<ClaimAssessment> historyByPlate(@PathVariable String plate) {
        return service.historyByPlate(plate);
    }

    /**
     * Tra cuu tinh trang boi thuong: nhap dung bien so + loai xe + SDT da dang ky
     * moi xem duoc cac ho so cua xe minh. Sai thong tin -> 403 chung chung,
     * khong tiet lo bien so co ton tai hay khong. Ket qua chi neu tinh trang
     * (bien so da che, khong tra ve SDT).
     */
    @GetMapping("/lookup")
    public ResponseEntity<?> lookup(@RequestParam("licensePlate") String licensePlate,
                                    @RequestParam("customerPhone") String customerPhone,
                                    @RequestParam("vehicleType") String vehicleType) {
        var result = vehicleService.checkIdentity(licensePlate, customerPhone, vehicleType);
        auditService.log("LOOKUP", VehicleService.normalizePlate(licensePlate),
                VehicleService.normalizePhone(customerPhone), vehicleType,
                result.plateMatch(), result.phoneMatch(), result.typeMatch(), result.passed(),
                result.passed() ? "Tra cứu bồi thường thành công." : "Thông tin không khớp với hồ sơ xe gốc.", null);
        if (!result.passed()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Thông tin không khớp với hồ sơ xe. Kiểm tra lại biển số, loại xe và SĐT."));
        }
        List<ClaimStatusDto> claims = service.historyByPlate(
                VehicleService.normalizePlate(licensePlate)).stream().map(ClaimStatusDto::from).toList();
        return ResponseEntity.ok(claims);
    }

    /** 1 ho so trong ket qua tra cuu: chi neu tinh trang boi thuong, bien so da che, khong co SDT. */
    public record ClaimStatusDto(String claimId, String ownerName, String licensePlate, String validationStatus,
                                 long totalCompensationVnd, long deductibleVnd, long netCompensationVnd,
                                 boolean requiresHumanReview, String humanReviewReason,
                                 String assessmentSummary, Instant createdAt) {
        static ClaimStatusDto from(ClaimAssessment a) {
            return new ClaimStatusDto(a.getClaimId(), a.getOwnerName(), VehicleService.maskPlate(a.getLicensePlate()),
                    a.getValidationStatus(), a.getTotalCompensationVnd(), a.getDeductibleVnd(),
                    a.getNetCompensationVnd(), a.isRequiresHumanReview(), a.getHumanReviewReason(),
                    a.getAssessmentSummary(), a.getCreatedAt());
        }
    }
}
