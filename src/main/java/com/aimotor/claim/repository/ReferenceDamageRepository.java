package com.aimotor.claim.repository;

import com.aimotor.claim.model.ReferenceDamage;
import com.aimotor.claim.service.DatabaseService;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Kho anh mau du tru, luu trong SQLite (bang reference_damages). */
@Repository
public class ReferenceDamageRepository {

    private final DatabaseService db;

    public ReferenceDamageRepository(DatabaseService db) {
        this.db = db;
    }

    public Optional<ReferenceDamage> findByReferenceId(String referenceId) {
        try (var conn = db.newConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM reference_damages WHERE reference_id = ?")) {
            ps.setString(1, referenceId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Không tìm được ảnh mẫu theo mã trong SQLite.", e);
        }
    }

    public boolean existsByReferenceId(String referenceId) {
        return findByReferenceId(referenceId).isPresent();
    }

    public Optional<ReferenceDamage> findById(String id) {
        if (id == null) return Optional.empty();
        try (var conn = db.newConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM reference_damages WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Không tìm được ảnh mẫu theo id trong SQLite.", e);
        }
    }

    public List<ReferenceDamage> findAll() {
        try (var conn = db.newConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM reference_damages ORDER BY rowid");
             ResultSet rs = ps.executeQuery()) {
            List<ReferenceDamage> out = new ArrayList<>();
            while (rs.next()) out.add(mapRow(rs));
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("Không đọc được kho ảnh mẫu từ SQLite.", e);
        }
    }

    public long count() {
        try (var conn = db.newConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM reference_damages");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (Exception e) {
            throw new IllegalStateException("Không đếm được kho ảnh mẫu trong SQLite.", e);
        }
    }

    /** Ghi moi (id rong -> sinh UUID) hoac cap nhat theo id. */
    public ReferenceDamage save(ReferenceDamage r) {
        if (r.getId() == null || r.getId().isBlank()) r.setId(UUID.randomUUID().toString());
        if (r.getCreatedAt() == null) r.setCreatedAt(Instant.now());
        try (var conn = db.newConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE reference_damages SET reference_id = ?, part_name = ?, severity_level = ?,"
                    + " severity_label = ?, description = ?, standard_price_vnd = ?, image_path = ?,"
                    + " needs_metadata = ?, image_hash = ?, created_at = ? WHERE id = ?")) {
                bind(ps, r);
                ps.setString(11, r.getId());
                if (ps.executeUpdate() > 0) return r;
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO reference_damages (reference_id, part_name, severity_level,"
                    + " severity_label, description, standard_price_vnd, image_path,"
                    + " needs_metadata, image_hash, created_at, id)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                bind(ps, r);
                ps.setString(11, r.getId());
                ps.executeUpdate();
            }
            return r;
        } catch (Exception e) {
            throw new IllegalStateException("Không lưu được ảnh mẫu xuống SQLite.", e);
        }
    }

    public void delete(ReferenceDamage r) {
        if (r == null || r.getId() == null) return;
        try (var conn = db.newConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM reference_damages WHERE id = ?")) {
            ps.setString(1, r.getId());
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Không xóa được ảnh mẫu trong SQLite.", e);
        }
    }

    private void bind(PreparedStatement ps, ReferenceDamage r) throws Exception {
        ps.setString(1, r.getReferenceId());
        ps.setString(2, r.getPartName());
        ps.setInt(3, r.getSeverityLevel());
        ps.setString(4, r.getSeverityLabel());
        ps.setString(5, r.getDescription());
        ps.setLong(6, r.getStandardPriceVnd());
        ps.setString(7, r.getImagePath());
        ps.setInt(8, r.isNeedsMetadata() ? 1 : 0);
        ps.setString(9, r.getImageHash());
        ps.setString(10, r.getCreatedAt() == null ? Instant.now().toString() : r.getCreatedAt().toString());
    }

    private ReferenceDamage mapRow(ResultSet rs) throws Exception {
        ReferenceDamage r = new ReferenceDamage();
        r.setId(rs.getString("id"));
        r.setReferenceId(rs.getString("reference_id"));
        r.setPartName(rs.getString("part_name"));
        r.setSeverityLevel(rs.getInt("severity_level"));
        r.setSeverityLabel(rs.getString("severity_label"));
        r.setDescription(rs.getString("description"));
        r.setStandardPriceVnd(rs.getLong("standard_price_vnd"));
        r.setImagePath(rs.getString("image_path"));
        r.setNeedsMetadata(rs.getInt("needs_metadata") == 1);
        r.setImageHash(rs.getString("image_hash"));
        String created = rs.getString("created_at");
        r.setCreatedAt(created == null || created.isBlank() ? Instant.now() : Instant.parse(created));
        return r;
    }
}
