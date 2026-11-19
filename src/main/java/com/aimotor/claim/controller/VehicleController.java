package com.aimotor.claim.controller;

import com.aimotor.claim.model.CustomerVehicle;
import com.aimotor.claim.service.VehicleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** API Ho so xe goc (dang ky xe + anh goc luc mua bao hiem). */
@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

    private final VehicleService service;

    public VehicleController(VehicleService service) {
        this.service = service;
    }

    @GetMapping
    public List<CustomerVehicle> list() {
        return service.listAll();
    }

    @GetMapping("/{plate}")
    public ResponseEntity<?> get(@PathVariable String plate) {
        return service.findByPlate(plate)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(java.util.Map.of("error", "Khong tim thay xe bien so " + plate)));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CustomerVehicle> register(
            @RequestParam("licensePlate") String licensePlate,
            @RequestParam("customerPhone") String customerPhone,
            @RequestParam("vehicleType") String vehicleType,
            @RequestParam(value = "brand", required = false) String brand,
            @RequestParam(value = "model", required = false) String model,
            @RequestParam(value = "year", required = false, defaultValue = "0") int year,
            @RequestParam(value = "color", required = false) String color,
            @RequestParam(value = "baselinePhotos", required = false) MultipartFile[] baselinePhotos) throws Exception {
        CustomerVehicle saved = service.register(licensePlate, customerPhone, vehicleType,
                brand, model, year, color, baselinePhotos);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }
}
