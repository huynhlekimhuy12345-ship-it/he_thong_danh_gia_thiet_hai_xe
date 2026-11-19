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

    public CustomerVehicle register(String licensePlate, String customerPhone, String vehicleType,
                                    String brand, String model, int year, String color,
                                    MultipartFile[] baselinePhotos) throws IOException {
        String plate = normalizePlate(licensePlate);
        String phone = normalizePhone(customerPhone);
        if (plate.isBlank() || phone.isBlank() || vehicleType == null || vehicleType.isBlank()) {
            throw new IllegalArgumentException("Thieu bien so, SDT hoac loai xe.");
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

    public List<CustomerVehicle> listAll() {
        return repository.findAll();
    }
}
