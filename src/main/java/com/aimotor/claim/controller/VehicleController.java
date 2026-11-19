package com.aimotor.claim.controller;

import com.aimotor.claim.model.CustomerVehicle;
import com.aimotor.claim.service.VehicleService;
import com.aimotor.claim.service.VerificationAuditService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** API Ho so xe goc (mua bao hiem / dang ky xe + anh goc luc mua bao hiem). */
@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

    private final VehicleService service;
    private final VerificationAuditService auditService;

    public VehicleController(VehicleService service, VerificationAuditService auditService) {
        this.service = service;
        this.auditService = auditService;
    }

    @GetMapping
    public List<CustomerVehicle> list() {
        return service.listAll().stream().map(VehicleService::maskedCopy).toList();
    }

    @GetMapping("/{plate}")
    public ResponseEntity<?> get(@PathVariable String plate) {
        return service.findByPlate(plate)
                .<ResponseEntity<?>>map(v -> ResponseEntity.ok(VehicleService.maskedCopy(v)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Không tìm thấy xe biển số " + plate)));
    }

    /**
     * Xac nhan xe goc truoc khi chup anh hu hai: nhap dung bien so + SDT + loai xe
     * da dang ky khi mua bao hiem -> he thong hien xe goc (thong tin + anh goc)
     * de nguoi dung xac nhan dung xe cua minh. Sai thong tin -> 403 chung chung,
     * khong tiet lo bien so co ton tai hay khong.
     */
    @GetMapping("/confirm")
    public ResponseEntity<?> confirm(@RequestParam("licensePlate") String licensePlate,
                                     @RequestParam("customerPhone") String customerPhone,
                                     @RequestParam("vehicleType") String vehicleType) {
        var result = service.checkIdentity(licensePlate, customerPhone, vehicleType);
        auditService.log("CONFIRM", VehicleService.normalizePlate(licensePlate),
                VehicleService.normalizePhone(customerPhone), vehicleType,
                result.plateMatch(), result.phoneMatch(), result.typeMatch(), result.passed(),
                result.passed() ? "Xác nhận xe gốc thành công." : "Thông tin không khớp với hồ sơ xe gốc.", null);
        if (!result.passed()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Thông tin không khớp với hồ sơ xe. Kiểm tra lại biển số, loại xe và SĐT."));
        }
        return ResponseEntity.ok(VehicleConfirmDto.from(result.vehicle()));
    }

    /** Ảnh goc cua xe (chi dung id xe + so thu tu, khong de bien so lo trong URL). */
    @GetMapping("/photo/{vehicleId}/{index}")
    public ResponseEntity<Resource> baselinePhoto(@PathVariable String vehicleId,
                                                  @PathVariable int index) throws Exception {
        var vehicleOpt = service.findById(vehicleId);
        if (vehicleOpt.isEmpty()) return ResponseEntity.notFound().build();
        List<String> paths = vehicleOpt.get().getBaselinePhotoPaths();
        if (paths == null || index < 0 || index >= paths.size()) return ResponseEntity.notFound().build();
        Path file = Paths.get(paths.get(index));
        if (!Files.exists(file)) return ResponseEntity.notFound().build();
        String name = file.getFileName().toString().toLowerCase();
        MediaType type = name.endsWith(".png") ? MediaType.IMAGE_PNG
                : name.endsWith(".webp") ? MediaType.parseMediaType("image/webp")
                : MediaType.IMAGE_JPEG;
        byte[] bytes = Files.readAllBytes(file);
        return ResponseEntity.ok().contentType(type).body(new ByteArrayResource(bytes));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CustomerVehicle> register(
            @RequestParam("ownerName") String ownerName,
            @RequestParam(value = "ownerDateOfBirth", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate ownerDateOfBirth,
            @RequestParam("licensePlate") String licensePlate,
            @RequestParam("customerPhone") String customerPhone,
            @RequestParam("vehicleType") String vehicleType,
            @RequestParam(value = "brand", required = false) String brand,
            @RequestParam(value = "model", required = false) String model,
            @RequestParam(value = "year", required = false, defaultValue = "0") int year,
            @RequestParam(value = "color", required = false) String color,
            @RequestParam(value = "baselinePhotos", required = false) MultipartFile[] baselinePhotos) throws Exception {
        CustomerVehicle saved = service.register(ownerName, ownerDateOfBirth, licensePlate, customerPhone, vehicleType,
                brand, model, year, color, baselinePhotos);
        return ResponseEntity.status(HttpStatus.CREATED).body(VehicleService.maskedCopy(saved));
    }

    /** Xe goc tra ve cho buoc xac nhan: bien so + SDT da che, kem link anh goc. */
    public record VehicleConfirmDto(String vehicleId, String ownerName, String ownerDateOfBirth,
                                    String licensePlate,
                                    String customerPhone, String vehicleType, String brand, String model,
                                    int year, String color, List<String> baselinePhotoUrls,
                                    String registeredAt) {
        static VehicleConfirmDto from(CustomerVehicle v) {
            List<String> urls = new ArrayList<>();
            List<String> paths = v.getBaselinePhotoPaths() == null ? List.of() : v.getBaselinePhotoPaths();
            for (int i = 0; i < paths.size(); i++) {
                urls.add("/api/vehicles/photo/" + v.getId() + "/" + i);
            }
            return new VehicleConfirmDto(v.getId(), v.getOwnerName(),
                    v.getOwnerDateOfBirth() == null ? null
                            : v.getOwnerDateOfBirth().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    VehicleService.maskPlate(v.getLicensePlate()),
                    VehicleService.maskPhone(v.getCustomerPhone()),
                    v.getVehicleType(), v.getBrand(), v.getModel(), v.getYear(), v.getColor(),
                    urls, v.getCreatedAt() == null ? null : v.getCreatedAt().toString());
        }
    }
}
