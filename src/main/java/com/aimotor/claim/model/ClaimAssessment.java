package com.aimotor.claim.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Lich su 1 lan tham dinh ho so boi thuong (luu lai de doi soat). */
@Document(collection = "claim_assessments")
public class ClaimAssessment {

    @Id
    private String id;

    /** Ma ho so, VD: CLM-20261119-4821 */
    private String claimId;

    private String licensePlate;
    private String customerPhone;
    private String vehicleId;

    /** OK | NEEDS_MORE_INFO | REJECTED */
    private String validationStatus;

    /** Cac van de AI phat hien (anh mo, khong phai xe, thieu goc...) */
    private List<String> validationIssues = new ArrayList<>();

    /** Cac goc anh con thieu theo checklist */
    private List<String> missingPhotos = new ArrayList<>();

    private IdentityCheck identityCheck;

    private List<DamageItem> damages = new ArrayList<>();

    private long totalCompensationVnd;

    /** Muc mien thuong da tru (VND/vu, theo cau hinh luc tham dinh). */
    private long deductibleVnd;

    /** So tien boi thuong thuc nhan de xuat = max(0, tong thiet hai - mien thuong). */
    private long netCompensationVnd;

    private boolean requiresHumanReview;
    private String humanReviewReason;

    private String assessmentSummary;

    private Instant createdAt = Instant.now();

    public ClaimAssessment() {
    }

    /** Ket qua xac minh danh tinh xe: dung loai xe + dung SDT + dung bien so. */
    public static class IdentityCheck {
        private boolean plateMatch;
        private boolean phoneMatch;
        private boolean typeMatch;
        /** Co the null khi AI khong xac dinh duoc mau tu anh */
        private Boolean colorMatch;
        private boolean passed;
        private String reason;

        public boolean isPlateMatch() { return plateMatch; }
        public void setPlateMatch(boolean plateMatch) { this.plateMatch = plateMatch; }
        public boolean isPhoneMatch() { return phoneMatch; }
        public void setPhoneMatch(boolean phoneMatch) { this.phoneMatch = phoneMatch; }
        public boolean isTypeMatch() { return typeMatch; }
        public void setTypeMatch(boolean typeMatch) { this.typeMatch = typeMatch; }
        public Boolean getColorMatch() { return colorMatch; }
        public void setColorMatch(Boolean colorMatch) { this.colorMatch = colorMatch; }
        public boolean isPassed() { return passed; }
        public void setPassed(boolean passed) { this.passed = passed; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class DamageItem {
        private String partName;
        private String observedCondition;
        private String matchedReferenceId;
        private String referenceComparisonNote;
        private long estimatedCostVnd;
        private double confidenceScore;

        public String getPartName() { return partName; }
        public void setPartName(String partName) { this.partName = partName; }
        public String getObservedCondition() { return observedCondition; }
        public void setObservedCondition(String observedCondition) { this.observedCondition = observedCondition; }
        public String getMatchedReferenceId() { return matchedReferenceId; }
        public void setMatchedReferenceId(String matchedReferenceId) { this.matchedReferenceId = matchedReferenceId; }
        public String getReferenceComparisonNote() { return referenceComparisonNote; }
        public void setReferenceComparisonNote(String referenceComparisonNote) { this.referenceComparisonNote = referenceComparisonNote; }
        public long getEstimatedCostVnd() { return estimatedCostVnd; }
        public void setEstimatedCostVnd(long estimatedCostVnd) { this.estimatedCostVnd = estimatedCostVnd; }
        public double getConfidenceScore() { return confidenceScore; }
        public void setConfidenceScore(double confidenceScore) { this.confidenceScore = confidenceScore; }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getClaimId() { return claimId; }
    public void setClaimId(String claimId) { this.claimId = claimId; }
    public String getLicensePlate() { return licensePlate; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }
    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }
    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }
    public String getValidationStatus() { return validationStatus; }
    public void setValidationStatus(String validationStatus) { this.validationStatus = validationStatus; }
    public List<String> getValidationIssues() { return validationIssues; }
    public void setValidationIssues(List<String> validationIssues) { this.validationIssues = validationIssues; }
    public List<String> getMissingPhotos() { return missingPhotos; }
    public void setMissingPhotos(List<String> missingPhotos) { this.missingPhotos = missingPhotos; }
    public IdentityCheck getIdentityCheck() { return identityCheck; }
    public void setIdentityCheck(IdentityCheck identityCheck) { this.identityCheck = identityCheck; }
    public List<DamageItem> getDamages() { return damages; }
    public void setDamages(List<DamageItem> damages) { this.damages = damages; }
    public long getTotalCompensationVnd() { return totalCompensationVnd; }
    public void setTotalCompensationVnd(long totalCompensationVnd) { this.totalCompensationVnd = totalCompensationVnd; }
    public long getDeductibleVnd() { return deductibleVnd; }
    public void setDeductibleVnd(long deductibleVnd) { this.deductibleVnd = deductibleVnd; }
    public long getNetCompensationVnd() { return netCompensationVnd; }
    public void setNetCompensationVnd(long netCompensationVnd) { this.netCompensationVnd = netCompensationVnd; }
    public boolean isRequiresHumanReview() { return requiresHumanReview; }
    public void setRequiresHumanReview(boolean requiresHumanReview) { this.requiresHumanReview = requiresHumanReview; }
    public String getHumanReviewReason() { return humanReviewReason; }
    public void setHumanReviewReason(String humanReviewReason) { this.humanReviewReason = humanReviewReason; }
    public String getAssessmentSummary() { return assessmentSummary; }
    public void setAssessmentSummary(String assessmentSummary) { this.assessmentSummary = assessmentSummary; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
