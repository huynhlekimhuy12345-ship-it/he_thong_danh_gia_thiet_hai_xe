package com.aimotor.claim.controller;

import com.aimotor.claim.model.ClaimAssessment;
import com.aimotor.claim.service.ClaimAssessmentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** API Tham dinh ho so boi thuong + lich su tham dinh. */
@RestController
@RequestMapping("/api/assessments")
public class AssessmentController {

    private final ClaimAssessmentService service;

    public AssessmentController(ClaimAssessmentService service) {
        this.service = service;
    }

    /**
     * Gui ho so tham dinh: bien so + SDT + loai xe + nhieu anh thiet hai.
     * He thong tu xac minh danh tinh xe, kiem tra anh, goi Gemini, luu lich su.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ClaimAssessment> assess(
            @RequestParam("licensePlate") String licensePlate,
            @RequestParam("customerPhone") String customerPhone,
            @RequestParam("vehicleType") String vehicleType,
            @RequestParam("photos") MultipartFile[] photos) throws Exception {
        ClaimAssessment result = service.assess(licensePlate, customerPhone, vehicleType, photos);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping
    public List<ClaimAssessment> history() {
        return service.history();
    }

    @GetMapping("/plate/{plate}")
    public List<ClaimAssessment> historyByPlate(@PathVariable String plate) {
        return service.historyByPlate(plate);
    }
}
