package com.aimotor.claim.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Lich su 1 lan tham dinh ho so boi thuong (luu lai de doi soat), luu trong SQLite (bang claim_assessments). */
public class ClaimAssessment {

    private String id;

    /** Ma ho so, VD: CLM-20261119-4821 */
    private String claimId;

    private String licensePlate;
    private String customerPhone;
    private String vehicleId;

    /** Ten chu xe (copy tu ho so xe goc khi xac minh danh tinh xong, de hien tren ho so) */
    private String ownerName;

    /** OK | NEEDS_MORE_INFO | REJECTED */
    private String validationStatus;

    /** Cac van de AI phat hien (anh mo, khong phai xe, thieu goc...) */
    private List<String> validationIssues = new ArrayList<>();

    /** Cac goc anh con thieu theo checklist */
    private List<String> missingPhotos = new ArrayList<>();

    private IdentityCheck identityCheck;

    private List<DamageItem> damages = new ArrayList<>();

    /** Duong dan anh khach gui cua ho so nay, luu tren dia o storage/claims/<claimId>/ */
    private List<String> claimPhotoPaths = new ArrayList<>();

    private long totalCompensationVnd;

    /** Muc mien thuong da tru (VND/vu, theo cau hinh luc tham dinh). */
    private long deductibleVnd;

    /** So tien boi thuong thuc nhan de xuat = max(0, tong thiet hai - mien thuong). */
    private long netCompensationVnd;

    private boolean requiresHumanReview;
    private String humanReviewReason;

    private String assessmentSummary;

    /** Mo ta them cua khach ve vu viec (khong bat buoc), gui kem cho Gemini + luu vao kho mau. */
    private String customerNote;

    /** Ghi chu ve qua trinh kiem chung 3 lan (chi tra ve kem ket qua, KHONG luu xuong SQLite). */
    private String consensusNote;

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
        /** Muc do hu hai: 1 = Xuoc nhe, 2 = Mop vua, 3 = Vo/Bien dang nang. */
        private int severityLevel;
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
        public int getSeverityLevel() { return severityLevel; }
        public void setSeverityLevel(int severityLevel) { this.severityLevel = severityLevel; }
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
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
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
    public List<String> getClaimPhotoPaths() { return claimPhotoPaths; }
    public void setClaimPhotoPaths(List<String> claimPhotoPaths) { this.claimPhotoPaths = claimPhotoPaths; }
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
    public String getCustomerNote() { return customerNote; }
    public void setCustomerNote(String customerNote) { this.customerNote = customerNote; }
    public String getConsensusNote() { return consensusNote; }
    public void setConsensusNote(String consensusNote) { this.consensusNote = consensusNote; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
