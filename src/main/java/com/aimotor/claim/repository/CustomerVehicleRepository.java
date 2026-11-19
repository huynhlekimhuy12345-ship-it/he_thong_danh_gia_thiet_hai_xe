package com.aimotor.claim.repository;

import com.aimotor.claim.model.CustomerVehicle;
import com.aimotor.claim.service.DatabaseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Ho so xe goc cua khach hang, luu trong SQLite (bang customer_vehicles). */
@Repository
public class CustomerVehicleRepository {

    private final DatabaseService db;
    private final ObjectMapper om;

    public CustomerVehicleRepository(DatabaseService db, ObjectMapper om) {
        this.db = db;
        this.om = om;
    }

    public Optional<CustomerVehicle> findByLicensePlate(String licensePlate) {
        try (var conn = db.newConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM customer_vehicles WHERE license_plate = ?")) {
            ps.setString(1, licensePlate);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Khong tim duoc xe theo bien so trong SQLite.", e);
        }
    }

    public boolean existsByLicensePlate(String licensePlate) {
        return findByLicensePlate(licensePlate).isPresent();
    }

    public Optional<CustomerVehicle> findById(String id) {
        if (id == null) return Optional.empty();
        try (var conn = db.newConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM customer_vehicles WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Khong tim duoc xe theo id trong SQLite.", e);
        }
    }

    public List<CustomerVehicle> findAll() {
        try (var conn = db.newConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM customer_vehicles ORDER BY rowid");
             ResultSet rs = ps.executeQuery()) {
            List<CustomerVehicle> out = new ArrayList<>();
            while (rs.next()) out.add(mapRow(rs));
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("Khong doc duoc danh sach xe tu SQLite.", e);
        }
    }

    /** Ghi moi (id rong -> sinh UUID) hoac cap nhat theo id. */
    public CustomerVehicle save(CustomerVehicle v) {
        if (v.getId() == null || v.getId().isBlank()) v.setId(UUID.randomUUID().toString());
        if (v.getCreatedAt() == null) v.setCreatedAt(Instant.now());
        try (var conn = db.newConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE customer_vehicles SET owner_name = ?, owner_dob = ?, license_plate = ?,"
                    + " customer_phone = ?, vehicle_type = ?, brand = ?, model = ?, year = ?,"
                    + " color = ?, baseline_photo_paths = ?, created_at = ? WHERE id = ?")) {
                bind(ps, v);
                ps.setString(12, v.getId());
                if (ps.executeUpdate() > 0) return v;
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO customer_vehicles (owner_name, owner_dob, license_plate,"
                    + " customer_phone, vehicle_type, brand, model, year,"
                    + " color, baseline_photo_paths, created_at, id)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                bind(ps, v);
                ps.setString(12, v.getId());
                ps.executeUpdate();
            }
            return v;
        } catch (Exception e) {
            throw new IllegalStateException("Khong luu duoc ho so xe xuong SQLite.", e);
        }
    }

    private void bind(PreparedStatement ps, CustomerVehicle v) throws Exception {
        ps.setString(1, v.getOwnerName());
        ps.setString(2, v.getOwnerDateOfBirth() == null ? null : v.getOwnerDateOfBirth().toString());
        ps.setString(3, v.getLicensePlate());
        ps.setString(4, v.getCustomerPhone());
        ps.setString(5, v.getVehicleType());
        ps.setString(6, v.getBrand());
        ps.setString(7, v.getModel());
        ps.setInt(8, v.getYear());
        ps.setString(9, v.getColor());
        ps.setString(10, DatabaseService.toJson(om, v.getBaselinePhotoPaths()));
        ps.setString(11, v.getCreatedAt() == null ? Instant.now().toString() : v.getCreatedAt().toString());
    }

    private CustomerVehicle mapRow(ResultSet rs) throws Exception {
        CustomerVehicle v = new CustomerVehicle();
        v.setId(rs.getString("id"));
        v.setOwnerName(rs.getString("owner_name"));
        String dob = rs.getString("owner_dob");
        v.setOwnerDateOfBirth(dob == null || dob.isBlank() ? null : LocalDate.parse(dob));
        v.setLicensePlate(rs.getString("license_plate"));
        v.setCustomerPhone(rs.getString("customer_phone"));
        v.setVehicleType(rs.getString("vehicle_type"));
        v.setBrand(rs.getString("brand"));
        v.setModel(rs.getString("model"));
        v.setYear(rs.getInt("year"));
        v.setColor(rs.getString("color"));
        v.setBaselinePhotoPaths(DatabaseService.stringList(om, rs.getString("baseline_photo_paths")));
        String created = rs.getString("created_at");
        v.setCreatedAt(created == null || created.isBlank() ? Instant.now() : Instant.parse(created));
        return v;
    }
}
