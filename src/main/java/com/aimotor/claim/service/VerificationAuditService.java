package com.aimotor.claim.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Nhat ky xac minh danh tinh, luu rieng vao SQLite tren may chay app.
 *
 * Thong tin nguoi dung nhap de kiem tra khop xe (bien so, SDT, loai xe) + ket qua
 * tung muc khop hay khong la thong tin BAO MAT cua he thong: chi luu o file SQLite
 * cuc bo (mac dinh ./storage/verification-audit.db), KHONG dua len database
 * van hanh (SQLite chinh), KHONG tra ve qua API, KHONG commit len git. Xem lai bang DB Browser for SQLite.
 */
@Service
public class VerificationAuditService {

    private static final Logger log = LoggerFactory.getLogger(VerificationAuditService.class);

    private final String dbPath;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    public VerificationAuditService(
            @Value("${app.storage.verification-db:./storage/verification-audit.db}") String dbPath) {
        this.dbPath = dbPath;
    }

    /**
     * Ghi 1 lan xac minh danh tinh. source: ASSESSMENT (gui tham dinh),
     * LOOKUP (tra cuu boi thuong), CONFIRM (xac nhan xe goc). Ghi loi chi log
     * canh bao, khong bao gio lam hong luong chinh.
     */
    public void log(String source, String licensePlate, String customerPhone, String vehicleType,
                    boolean plateMatch, boolean phoneMatch, boolean typeMatch,
                    boolean passed, String reason, String claimId) {
        try {
            ensureTable();
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO verification_log (created_at, source, license_plate, customer_phone,"
                         + " vehicle_type, plate_match, phone_match, type_match, passed, reason, claim_id)"
                         + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, Instant.now().toString());
                ps.setString(2, source);
                ps.setString(3, licensePlate);
                ps.setString(4, customerPhone);
                ps.setString(5, vehicleType);
                ps.setInt(6, plateMatch ? 1 : 0);
                ps.setInt(7, phoneMatch ? 1 : 0);
                ps.setInt(8, typeMatch ? 1 : 0);
                ps.setInt(9, passed ? 1 : 0);
                ps.setString(10, reason);
                ps.setString(11, claimId);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            log.warn("Khong ghi duoc nhat ky xac minh vao SQLite {}: {}", dbPath, e.toString());
        }
    }

    private synchronized void ensureTable() throws Exception {
        if (initialized.get()) return;
        var parent = Paths.get(dbPath).getParent();
        if (parent != null) Files.createDirectories(parent);
        Class.forName("org.sqlite.JDBC");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS verification_log ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "created_at TEXT NOT NULL, "
                    + "source TEXT NOT NULL, "
                    + "license_plate TEXT NOT NULL, "
                    + "customer_phone TEXT NOT NULL, "
                    + "vehicle_type TEXT, "
                    + "plate_match INTEGER NOT NULL, "
                    + "phone_match INTEGER NOT NULL, "
                    + "type_match INTEGER NOT NULL, "
                    + "passed INTEGER NOT NULL, "
                    + "reason TEXT, "
                    + "claim_id TEXT)");
        }
        initialized.set(true);
    }
}
