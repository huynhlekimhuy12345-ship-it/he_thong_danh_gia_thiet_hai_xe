package com.aimotor.claim.service;

import com.aimotor.claim.model.CustomerVehicle;
import com.aimotor.claim.repository.CustomerVehicleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Ho so xe goc + xac minh danh tinh xe (bien so + SDT + loai xe). */
@Service
public class VehicleService {

    private final CustomerVehicleRepository repository;
    private final ImagePreprocessingService imageService;

    @Value("${app.storage.vehicle-dir:./storage/vehicles}")
    private String vehicleDir;

    public VehicleService(CustomerVehicleRepository repository,
                          ImagePreprocessingService imageService) {
        this.repository = repository;
        this.imageService = imageService;
    }

    /** Chuan hoa bien so: viet hoa, bo khoang trang / gach ngang / cham. */
    public static String normalizePlate(String plate) {
        if (plate == null) return "";
        return plate.toUpperCase().replaceAll("[\\s.\\-]", "");
    }

    /** Chuan hoa SDT: chi giu chu so. */
    public static String normalizePhone(String phone) {
        if (phone == null) return "";
        return phone.replaceAll("[^0-9]", "");
    }

    /** Che bien so khi hien thi: giu 3 ky tu dau + 2 ky tu cuoi, giua thay bang *. VD: 30A12345 -> 30A***45. */
    public static String maskPlate(String plate) {
        String p = normalizePlate(plate);
        if (p.isEmpty()) return "";
        if (p.length() <= 2) return "*".repeat(p.length());
        if (p.length() <= 5) return p.charAt(0) + "*".repeat(p.length() - 2) + p.charAt(p.length() - 1);
        return p.substring(0, 3) + "*".repeat(p.length() - 5) + p.substring(p.length() - 2);
    }

    /** Che SDT khi hien thi: giu 3 so dau + 2 so cuoi. VD: 0901234567 -> 090*****67. */
    public static String maskPhone(String phone) {
        String p = normalizePhone(phone);
        if (p.isEmpty()) return "";
        if (p.length() <= 2) return "*".repeat(p.length());
        if (p.length() <= 5) return p.charAt(0) + "*".repeat(p.length() - 2) + p.charAt(p.length() - 1);
        return p.substring(0, 3) + "*".repeat(p.length() - 5) + p.substring(p.length() - 2);
    }

    /** Ket qua kiem tra danh tinh: tung muc khop hay khong + xe goc neu tim thay. */
    public record IdentityResult(boolean plateMatch, boolean phoneMatch, boolean typeMatch,
                                 CustomerVehicle vehicle) {
        public boolean passed() { return plateMatch && phoneMatch && typeMatch; }
    }

    /** Kiem tra danh tinh: dung bien so + dung SDT + dung loai xe moi khop. */
    public IdentityResult checkIdentity(String licensePlate, String customerPhone, String vehicleType) {
        String plate = normalizePlate(licensePlate);
        String phone = normalizePhone(customerPhone);
        var vehicleOpt = findByPlate(plate);
        boolean plateMatch = vehicleOpt.isPresent();
        boolean phoneMatch = vehicleOpt.map(v -> phone.equals(v.getCustomerPhone())).orElse(false);
        boolean typeMatch = vehicleOpt.map(v -> vehicleType != null
                && v.getVehicleType().equalsIgnoreCase(vehicleType.trim())).orElse(false);
        return new IdentityResult(plateMatch, phoneMatch, typeMatch, vehicleOpt.orElse(null));
    }

    /** Ban sao chi de hien thi: bien so + SDT da che, cac truong khac giu nguyen. */
    public static CustomerVehicle maskedCopy(CustomerVehicle v) {
        CustomerVehicle c = new CustomerVehicle();
        c.setId(v.getId());
        c.setOwnerName(v.getOwnerName());
        c.setOwnerDateOfBirth(v.getOwnerDateOfBirth());
        c.setLicensePlate(maskPlate(v.getLicensePlate()));
        c.setCustomerPhone(maskPhone(v.getCustomerPhone()));
        c.setVehicleType(v.getVehicleType());
        c.setBrand(v.getBrand());
        c.setModel(v.getModel());
        c.setYear(v.getYear());
        c.setColor(v.getColor());
        c.setBaselinePhotoPaths(v.getBaselinePhotoPaths());
        c.setCreatedAt(v.getCreatedAt());
        return c;
    }

    public CustomerVehicle register(String ownerName, java.time.LocalDate ownerDateOfBirth,
                                    String licensePlate, String customerPhone, String vehicleType,
                                    String brand, String model, int year, String color,
                                    MultipartFile[] baselinePhotos) throws IOException {
        String plate = normalizePlate(licensePlate);
        String phone = normalizePhone(customerPhone);
        if (ownerName == null || ownerName.isBlank() || ownerDateOfBirth == null
                || plate.isBlank() || phone.isBlank()
                || vehicleType == null || vehicleType.isBlank()) {
            throw new IllegalArgumentException("Thieu ten chu xe, ngay sinh, bien so, SDT hoac loai xe.");
        }
        if (ownerDateOfBirth.isAfter(java.time.LocalDate.now())) {
            throw new IllegalArgumentException("Ngay sinh khong duoc lon hon ngay hien tai.");
        }
        if (repository.existsByLicensePlate(plate)) {
            throw new IllegalArgumentException("Bien so " + plate + " da co ho so xe. Dung chuc nang cap nhat hoac dung bien so khac.");
        }
        List<String> photoPaths = new ArrayList<>();
        if (baselinePhotos != null) {
            for (MultipartFile photo : baselinePhotos) {
                if (photo != null && !photo.isEmpty()) {
                    imageService.validateImage(photo);
                    photoPaths.add(imageService.storeFile(photo, Paths.get(vehicleDir, plate)));
                }
            }
        }
        CustomerVehicle vehicle = new CustomerVehicle();
        vehicle.setOwnerName(ownerName.trim());
        vehicle.setOwnerDateOfBirth(ownerDateOfBirth);
        vehicle.setLicensePlate(plate);
        vehicle.setCustomerPhone(phone);
        vehicle.setVehicleType(vehicleType.trim());
        vehicle.setBrand(brand);
        vehicle.setModel(model);
        vehicle.setYear(year);
        vehicle.setColor(color);
        vehicle.setBaselinePhotoPaths(photoPaths);
        return repository.save(vehicle);
    }

    public Optional<CustomerVehicle> findByPlate(String licensePlate) {
        return repository.findByLicensePlate(normalizePlate(licensePlate));
    }

    public Optional<CustomerVehicle> findById(String id) {
        if (id == null) return Optional.empty();
        return repository.findById(id);
    }

    public List<CustomerVehicle> listAll() {
        return repository.findAll();
    }
}
