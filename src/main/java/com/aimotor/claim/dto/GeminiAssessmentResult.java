package com.aimotor.claim.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Ket qua JSON Gemini tra ve (ep bang responseSchema). */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeminiAssessmentResult {

    private Validation validation;
    private String assessmentSummary;
    private List<Damage> damages;
    private long totalCompensationVnd;
    private boolean requiresHumanReview;
    private String humanReviewReason;
    /** Ghi chu ve qua trinh kiem chung (khong phai do AI tra ve): VD chay 3 lan lay trung vi. */
    private String consensusNote;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Validation {
        private String status; // OK | NEEDS_MORE_INFO | REJECTED
        private List<String> issues;
        private List<String> missingPhotos;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public List<String> getIssues() { return issues; }
        public void setIssues(List<String> issues) { this.issues = issues; }
        public List<String> getMissingPhotos() { return missingPhotos; }
        public void setMissingPhotos(List<String> missingPhotos) { this.missingPhotos = missingPhotos; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Damage {
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

    public Validation getValidation() { return validation; }
    public void setValidation(Validation validation) { this.validation = validation; }
    public String getAssessmentSummary() { return assessmentSummary; }
    public void setAssessmentSummary(String assessmentSummary) { this.assessmentSummary = assessmentSummary; }
    public List<Damage> getDamages() { return damages; }
    public void setDamages(List<Damage> damages) { this.damages = damages; }
    public long getTotalCompensationVnd() { return totalCompensationVnd; }
    public void setTotalCompensationVnd(long totalCompensationVnd) { this.totalCompensationVnd = totalCompensationVnd; }
    public boolean isRequiresHumanReview() { return requiresHumanReview; }
    public void setRequiresHumanReview(boolean requiresHumanReview) { this.requiresHumanReview = requiresHumanReview; }
    public String getHumanReviewReason() { return humanReviewReason; }
    public void setHumanReviewReason(String humanReviewReason) { this.humanReviewReason = humanReviewReason; }
    public String getConsensusNote() { return consensusNote; }
    public void setConsensusNote(String consensusNote) { this.consensusNote = consensusNote; }
}
