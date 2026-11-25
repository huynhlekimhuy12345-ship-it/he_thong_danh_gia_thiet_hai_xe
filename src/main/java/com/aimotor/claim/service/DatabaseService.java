package com.aimotor.claim.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Database van hanh chinh: 1 file SQLite (mac dinh ./storage/aimotor.db).
 *
 * Toan bo du lieu van hanh (hồ sơ xe, ho so tham dinh, kho anh mau, cau hinh)
 * deu luu o day — khong can MongoDB, khong can cai them database server.
 * Rieng nhat ky xac minh danh tinh van giu file SQLite rieng
 * (./storage/verification-audit.db, xem VerificationAuditService) vi do la
 * thong tin bao mat chi luu cuc bo.
 *
 * Moi thao tac mo connection rieng (SQLite JDBC), kem WAL + busy_timeout
 * de tranh loi "database is locked" khi ghi lien tiep.
 */
@Service
public class DatabaseService {

    private final String dbPath;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    public DatabaseService(
            @Value("${app.storage.main-db:./storage/aimotor.db}") String dbPath) {
        this.dbPath = dbPath;
    }

    /** Mo 1 connection moi (nho dong sau khi dung xong, try-with-resources). */
    public Connection newConnection() throws Exception {
        ensureSchema();
        Class.forName("org.sqlite.JDBC");
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA busy_timeout=5000");
            st.execute("PRAGMA foreign_keys=ON");
        }
        return conn;
    }

    private synchronized void ensureSchema() throws Exception {
        if (initialized.get()) return;
        var parent = Paths.get(dbPath).getParent();
        if (parent != null) Files.createDirectories(parent);
        Class.forName("org.sqlite.JDBC");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS customer_vehicles ("
                    + "id TEXT PRIMARY KEY, "
                    + "owner_name TEXT, "
                    + "owner_dob TEXT, "
                    + "license_plate TEXT NOT NULL UNIQUE, "
                    + "customer_phone TEXT NOT NULL, "
                    + "vehicle_type TEXT NOT NULL, "
                    + "brand TEXT, model TEXT, "
                    + "year INTEGER NOT NULL DEFAULT 0, "
                    + "color TEXT, "
                    + "baseline_photo_paths TEXT NOT NULL DEFAULT '[]', "
                    + "created_at TEXT NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS claim_assessments ("
                    + "id TEXT PRIMARY KEY, "
                    + "claim_id TEXT NOT NULL UNIQUE, "
                    + "license_plate TEXT NOT NULL, "
                    + "customer_phone TEXT NOT NULL, "
                    + "vehicle_id TEXT, "
                    + "owner_name TEXT, "
                    + "validation_status TEXT, "
                    + "validation_issues TEXT NOT NULL DEFAULT '[]', "
                    + "missing_photos TEXT NOT NULL DEFAULT '[]', "
                    + "identity_json TEXT NOT NULL DEFAULT '{}', "
                    + "damages_json TEXT NOT NULL DEFAULT '[]', "
                    + "claim_photo_paths TEXT NOT NULL DEFAULT '[]', "
                    + "total_compensation_vnd INTEGER NOT NULL DEFAULT 0, "
                    + "deductible_vnd INTEGER NOT NULL DEFAULT 0, "
                    + "net_compensation_vnd INTEGER NOT NULL DEFAULT 0, "
                    + "requires_human_review INTEGER NOT NULL DEFAULT 0, "
                    + "human_review_reason TEXT, "
                    + "assessment_summary TEXT, "
                    + "customer_note TEXT, "
                    + "consensus_note TEXT, "
                    + "created_at TEXT NOT NULL)");
            // Migration: DB da tao tu ban truoc (chua co cot customer_note) thi them cot.
            // Cot da ton tai thi SQLite bao loi "duplicate column name" -> bo qua.
            try {
                st.execute("ALTER TABLE claim_assessments ADD COLUMN customer_note TEXT");
            } catch (Exception ignored) {
            }
            // Migration: luu ghi chu kiem chung 3 lan de mo lai ho so cu van thay duoc.
            try {
                st.execute("ALTER TABLE claim_assessments ADD COLUMN consensus_note TEXT");
            } catch (Exception ignored) {
            }
            st.execute("CREATE INDEX IF NOT EXISTS idx_claims_plate "
                    + "ON claim_assessments(license_plate, created_at DESC)");
            st.execute("CREATE TABLE IF NOT EXISTS reference_damages ("
                    + "id TEXT PRIMARY KEY, "
                    + "reference_id TEXT NOT NULL UNIQUE, "
                    + "part_name TEXT NOT NULL, "
                    + "severity_level INTEGER NOT NULL, "
                    + "severity_label TEXT, "
                    + "description TEXT, "
                    + "standard_price_vnd INTEGER NOT NULL DEFAULT 0, "
                    + "image_path TEXT NOT NULL, "
                    + "needs_metadata INTEGER NOT NULL DEFAULT 0, "
                    + "image_hash TEXT, "
                    + "created_at TEXT NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS app_config ("
                    + "id TEXT PRIMARY KEY, "
                    + "api_key TEXT, "
                    + "model TEXT, "
                    + "deductible_vnd INTEGER)");
        }
        initialized.set(true);
    }

    /** List<String> -> JSON, de luu xuong cot TEXT (null -> "[]"). */
    public static String toJson(ObjectMapper om, Object value) {
        try {
            return value == null ? "[]" : om.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Không ghi được JSON xuống SQLite.", e);
        }
    }

    /** JSON trong cot TEXT -> List<String> (rong/sai dinh dang -> list rong). */
    public static List<String> stringList(ObjectMapper om, String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return om.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
