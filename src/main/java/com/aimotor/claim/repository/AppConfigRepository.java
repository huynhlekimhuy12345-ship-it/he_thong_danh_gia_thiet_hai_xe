package com.aimotor.claim.repository;

import com.aimotor.claim.model.AppConfig;
import com.aimotor.claim.service.DatabaseService;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;

/** Cau hinh ung dung (1 dong duy nhat, id = "gemini"), luu trong SQLite. */
@Repository
public class AppConfigRepository {

    private final DatabaseService db;

    public AppConfigRepository(DatabaseService db) {
        this.db = db;
    }

    public Optional<AppConfig> findById(String id) {
        try (var conn = db.newConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, api_key, model, deductible_vnd FROM app_config WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Không đọc được cấu hình từ SQLite.", e);
        }
    }

    /** Ghi de theo id (INSERT neu chua co, UPDATE neu da co). */
    public AppConfig save(AppConfig config) {
        try (var conn = db.newConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE app_config SET api_key = ?, model = ?, deductible_vnd = ? WHERE id = ?")) {
                ps.setString(1, config.getApiKey());
                ps.setString(2, config.getModel());
                if (config.getDeductibleVnd() == null) ps.setNull(3, java.sql.Types.INTEGER);
                else ps.setLong(3, config.getDeductibleVnd());
                ps.setString(4, config.getId());
                if (ps.executeUpdate() > 0) return config;
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO app_config (id, api_key, model, deductible_vnd) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, config.getId());
                ps.setString(2, config.getApiKey());
                ps.setString(3, config.getModel());
                if (config.getDeductibleVnd() == null) ps.setNull(4, java.sql.Types.INTEGER);
                else ps.setLong(4, config.getDeductibleVnd());
                ps.executeUpdate();
            }
            return config;
        } catch (Exception e) {
            throw new IllegalStateException("Không lưu được cấu hình xuống SQLite.", e);
        }
    }

    private AppConfig mapRow(ResultSet rs) throws Exception {
        AppConfig c = new AppConfig();
        c.setId(rs.getString("id"));
        c.setApiKey(rs.getString("api_key"));
        c.setModel(rs.getString("model"));
        long d = rs.getLong("deductible_vnd");
        c.setDeductibleVnd(rs.wasNull() ? null : d);
        return c;
    }
}
