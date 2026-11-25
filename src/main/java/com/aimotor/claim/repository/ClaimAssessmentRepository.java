package com.aimotor.claim.repository;

import com.aimotor.claim.model.ClaimAssessment;
import com.aimotor.claim.service.DatabaseService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Lich su tham dinh ho so boi thuong, luu trong SQLite (bang claim_assessments). */
@Repository
public class ClaimAssessmentRepository {

    private final DatabaseService db;
    private final ObjectMapper om;

    public ClaimAssessmentRepository(DatabaseService db, ObjectMapper om) {
        this.db = db;
        this.om = om;
    }

    public Optional<ClaimAssessment> findById(String id) {
        if (id == null) return Optional.empty();
        try (var conn = db.newConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM claim_assessments WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Không tìm được hồ sơ thẩm định theo id trong SQLite.", e);
        }
    }

    /** Tim theo ma ho so nghiep vu (VD CLM-20261120-3853) de mo chi tiet tu lich su / tra cuu. */
    public Optional<ClaimAssessment> findByClaimId(String claimId) {
        if (claimId == null) return Optional.empty();
        try (var conn = db.newConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM claim_assessments WHERE claim_id = ?")) {
            ps.setString(1, claimId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Không tìm được hồ sơ thẩm định theo mã hồ sơ trong SQLite.", e);
        }
    }

    public List<ClaimAssessment> findByLicensePlateOrderByCreatedAtDesc(String licensePlate) {
        try (var conn = db.newConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM claim_assessments WHERE license_plate = ?"
                     + " ORDER BY created_at DESC, id DESC")) {
            ps.setString(1, licensePlate);
            try (ResultSet rs = ps.executeQuery()) {
                List<ClaimAssessment> out = new ArrayList<>();
                while (rs.next()) out.add(mapRow(rs));
                return out;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Không đọc được lịch sử thẩm định theo biển số từ SQLite.", e);
        }
    }

    public List<ClaimAssessment> findAll() {
        try (var conn = db.newConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM claim_assessments ORDER BY rowid");
             ResultSet rs = ps.executeQuery()) {
            List<ClaimAssessment> out = new ArrayList<>();
            while (rs.next()) out.add(mapRow(rs));
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("Không đọc được lịch sử thẩm định từ SQLite.", e);
        }
    }

    /** Ghi moi (id rong -> sinh UUID) hoac cap nhat theo id. */
    public ClaimAssessment save(ClaimAssessment a) {
        if (a.getId() == null || a.getId().isBlank()) a.setId(UUID.randomUUID().toString());
        if (a.getCreatedAt() == null) a.setCreatedAt(Instant.now());
        try (var conn = db.newConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE claim_assessments SET claim_id = ?, license_plate = ?, customer_phone = ?,"
                    + " vehicle_id = ?, owner_name = ?, validation_status = ?, validation_issues = ?,"
                    + " missing_photos = ?, identity_json = ?, damages_json = ?, claim_photo_paths = ?,"
                    + " total_compensation_vnd = ?, deductible_vnd = ?, net_compensation_vnd = ?,"
                    + " requires_human_review = ?, human_review_reason = ?, assessment_summary = ?,"
                    + " customer_note = ?, consensus_note = ?, created_at = ? WHERE id = ?")) {
                bind(ps, a);
                ps.setString(21, a.getId());
                if (ps.executeUpdate() > 0) return a;
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO claim_assessments (claim_id, license_plate, customer_phone,"
                    + " vehicle_id, owner_name, validation_status, validation_issues,"
                    + " missing_photos, identity_json, damages_json, claim_photo_paths,"
                    + " total_compensation_vnd, deductible_vnd, net_compensation_vnd,"
                    + " requires_human_review, human_review_reason, assessment_summary,"
                    + " customer_note, consensus_note, created_at, id)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                bind(ps, a);
                ps.setString(21, a.getId());
                ps.executeUpdate();
            }
            return a;
        } catch (Exception e) {
            throw new IllegalStateException("Không lưu được hồ sơ thẩm định xuống SQLite.", e);
        }
    }

    private void bind(PreparedStatement ps, ClaimAssessment a) throws Exception {
        ps.setString(1, a.getClaimId());
        ps.setString(2, a.getLicensePlate());
        ps.setString(3, a.getCustomerPhone());
        ps.setString(4, a.getVehicleId());
        ps.setString(5, a.getOwnerName());
        ps.setString(6, a.getValidationStatus());
        ps.setString(7, DatabaseService.toJson(om, a.getValidationIssues()));
        ps.setString(8, DatabaseService.toJson(om, a.getMissingPhotos()));
        ps.setString(9, a.getIdentityCheck() == null ? "{}" : om.writeValueAsString(a.getIdentityCheck()));
        ps.setString(10, DatabaseService.toJson(om, a.getDamages()));
        ps.setString(11, DatabaseService.toJson(om, a.getClaimPhotoPaths()));
        ps.setLong(12, a.getTotalCompensationVnd());
        ps.setLong(13, a.getDeductibleVnd());
        ps.setLong(14, a.getNetCompensationVnd());
        ps.setInt(15, a.isRequiresHumanReview() ? 1 : 0);
        ps.setString(16, a.getHumanReviewReason());
        ps.setString(17, a.getAssessmentSummary());
        ps.setString(18, a.getCustomerNote());
        ps.setString(19, a.getConsensusNote());
        ps.setString(20, a.getCreatedAt() == null ? Instant.now().toString() : a.getCreatedAt().toString());
    }

    private ClaimAssessment mapRow(ResultSet rs) throws Exception {
        ClaimAssessment a = new ClaimAssessment();
        a.setId(rs.getString("id"));
        a.setClaimId(rs.getString("claim_id"));
        a.setLicensePlate(rs.getString("license_plate"));
        a.setCustomerPhone(rs.getString("customer_phone"));
        a.setVehicleId(rs.getString("vehicle_id"));
        a.setOwnerName(rs.getString("owner_name"));
        a.setValidationStatus(rs.getString("validation_status"));
        a.setValidationIssues(DatabaseService.stringList(om, rs.getString("validation_issues")));
        a.setMissingPhotos(DatabaseService.stringList(om, rs.getString("missing_photos")));
        String identityJson = rs.getString("identity_json");
        a.setIdentityCheck(identityJson == null || identityJson.isBlank() || identityJson.equals("{}")
                ? null : om.readValue(identityJson, ClaimAssessment.IdentityCheck.class));
        String damagesJson = rs.getString("damages_json");
        a.setDamages(damagesJson == null || damagesJson.isBlank() ? List.of()
                : om.readValue(damagesJson, new TypeReference<List<ClaimAssessment.DamageItem>>() {}));
        a.setClaimPhotoPaths(DatabaseService.stringList(om, rs.getString("claim_photo_paths")));
        a.setTotalCompensationVnd(rs.getLong("total_compensation_vnd"));
        a.setDeductibleVnd(rs.getLong("deductible_vnd"));
        a.setNetCompensationVnd(rs.getLong("net_compensation_vnd"));
        a.setRequiresHumanReview(rs.getInt("requires_human_review") == 1);
        a.setHumanReviewReason(rs.getString("human_review_reason"));
        a.setAssessmentSummary(rs.getString("assessment_summary"));
        a.setCustomerNote(rs.getString("customer_note"));
        a.setConsensusNote(rs.getString("consensus_note"));
        String created = rs.getString("created_at");
        a.setCreatedAt(created == null || created.isBlank() ? Instant.now() : Instant.parse(created));
        return a;
    }
}
