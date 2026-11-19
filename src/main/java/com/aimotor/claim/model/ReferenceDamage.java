package com.aimotor.claim.model;

import java.time.Instant;

/**
 * 1 anh mau trong Kho anh du tru (Reference Damage Library), luu trong SQLite (bang reference_damages).
 * Moi anh mau co nhan day du de Gemini doi chieu va dinh gia.
 */
public class ReferenceDamage {

    private String id;

    /** Ma mau, VD: REF_01 */
    private String referenceId;

    /** Ten bo phan, VD: Can truoc, Cua sau ben phu, Den pha trai */
    private String partName;

    /** 1 = Xuoc nhe | 2 = Mop vua | 3 = Vo/Bien dang nang */
    private int severityLevel;

    private String severityLabel;

    /** Mo ta chi tiet tinh trang hu hai cua anh mau */
    private String description;

    /** Don gia boi thuong chuan (VND) */
    private long standardPriceVnd;

    /** Duong dan file anh mau, VD: storage/reference-damages/ref-01-bumper-scratch.jpg */
    private String imagePath;

    /**
     * true = anh tu dong quet tu folder, chua du metadata (bo phan/gia chuan).
     * Anh nhap bi loai khoi request Gemini cho toi khi bo sung xong, tranh
     * AI doi chieu voi anh mau gia 0 VND.
     */
    private boolean needsMetadata = false;

    /** SHA-256 noi dung file anh, dung de chan trung anh (cung anh, khac ten file). */
    private String imageHash;

    private Instant createdAt = Instant.now();

    public ReferenceDamage() {
    }

    public ReferenceDamage(String referenceId, String partName, int severityLevel,
                           String severityLabel, String description,
                           long standardPriceVnd, String imagePath) {
        this.referenceId = referenceId;
        this.partName = partName;
        this.severityLevel = severityLevel;
        this.severityLabel = severityLabel;
        this.description = description;
        this.standardPriceVnd = standardPriceVnd;
        this.imagePath = imagePath;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getReferenceId() { return referenceId; }
    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }
    public String getPartName() { return partName; }
    public void setPartName(String partName) { this.partName = partName; }
    public int getSeverityLevel() { return severityLevel; }
    public void setSeverityLevel(int severityLevel) { this.severityLevel = severityLevel; }
    public String getSeverityLabel() { return severityLabel; }
    public void setSeverityLabel(String severityLabel) { this.severityLabel = severityLabel; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public long getStandardPriceVnd() { return standardPriceVnd; }
    public void setStandardPriceVnd(long standardPriceVnd) { this.standardPriceVnd = standardPriceVnd; }
    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }
    public boolean isNeedsMetadata() { return needsMetadata; }
    public void setNeedsMetadata(boolean needsMetadata) { this.needsMetadata = needsMetadata; }
    public String getImageHash() { return imageHash; }
    public void setImageHash(String imageHash) { this.imageHash = imageHash; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
