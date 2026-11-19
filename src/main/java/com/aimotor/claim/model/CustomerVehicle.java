package com.aimotor.claim.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Ho so xe goc cua khach hang (1 xe = 1 ho so).
 * Anh goc cua xe duoc luu o day ngay khi mua bao hiem, lam "mau" de doi chieu
 * khi co thiet hai: dung loai xe, dung SDT, dung bien so moi duoc tham dinh.
 */
@Document(collection = "customer_vehicles")
public class CustomerVehicle {

    @Id
    private String id;

    /** Ten chu xe (ghi day du khi mua bao hiem) */
    private String ownerName;

    /** Ngay sinh chu xe (ghi ro khi mua bao hiem, dang yyyy-MM-dd) */
    private LocalDate ownerDateOfBirth;

    /** Bien so da chuan hoa: viet hoa, bo khoang trang / dau gach / dau cham */
    private String licensePlate;

    /** SDT khach hang da chuan hoa: chi giu chu so */
    private String customerPhone;

    /** Loai xe: Sedan, SUV, Ban tai, Hatchback... */
    private String vehicleType;

    private String brand;
    private String model;
    private int year;

    /** Mau xe goc, VD: Trang, Den, Bac */
    private String color;

    /** Duong dan cac anh goc cua xe (chup luc mua bao hiem) */
    private List<String> baselinePhotoPaths = new ArrayList<>();

    private Instant createdAt = Instant.now();

    public CustomerVehicle() {
    }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public LocalDate getOwnerDateOfBirth() { return ownerDateOfBirth; }
    public void setOwnerDateOfBirth(LocalDate ownerDateOfBirth) { this.ownerDateOfBirth = ownerDateOfBirth; }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getLicensePlate() { return licensePlate; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }
    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }
    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public List<String> getBaselinePhotoPaths() { return baselinePhotoPaths; }
    public void setBaselinePhotoPaths(List<String> baselinePhotoPaths) { this.baselinePhotoPaths = baselinePhotoPaths; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
