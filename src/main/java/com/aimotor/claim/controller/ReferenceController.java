package com.aimotor.claim.controller;

import com.aimotor.claim.model.ReferenceDamage;
import com.aimotor.claim.service.GeminiService;
import com.aimotor.claim.service.ReferenceLibraryService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/** API Kho anh mau du tru. */
@RestController
@RequestMapping("/api/references")
public class ReferenceController {

    private final ReferenceLibraryService service;

    public ReferenceController(ReferenceLibraryService service) {
        this.service = service;
    }

    @GetMapping
    public List<ReferenceDamage> list() {
        return service.listAll();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> add(@RequestParam("referenceId") String referenceId,
                                 @RequestParam("partName") String partName,
                                 @RequestParam("severityLevel") int severityLevel,
                                 @RequestParam("severityLabel") String severityLabel,
                                 @RequestParam("description") String description,
                                 @RequestParam("standardPriceVnd") long standardPriceVnd,
                                 @RequestParam("image") MultipartFile image) throws Exception {
        ReferenceDamage saved = service.add(referenceId, partName, severityLevel,
                severityLabel, description, standardPriceVnd, image);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /** Xem truc tiep anh mau (de frontend hien thi). */
    @GetMapping("/{id}/image")
    public ResponseEntity<Resource> image(@PathVariable String id) throws Exception {
        ReferenceDamage ref = service.listAll().stream()
                .filter(r -> r.getId().equals(id) || r.getReferenceId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Khong tim thay anh mau " + id));
        Path path = Paths.get(ref.getImagePath());
        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new UrlResource(path.toUri());
        String contentType = Files.probeContentType(path);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType != null ? contentType : "image/jpeg"))
                .body(resource);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        service.delete(id);
        return Map.of("deleted", true, "id", id);
    }

    /**
     * Quet thu muc storage/reference-damages: anh moi tha vao folder se duoc
     * nhan vao kho o dang nhap (can bo sung metadata sau).
     */
    @PostMapping("/scan")
    public Map<String, Object> scan() throws Exception {
        var imported = service.scanFolder();
        return Map.of("imported", imported.size(), "references", imported);
    }

    /** Bo sung / sua metadata cho anh mau (dung cho anh nhap sau khi quet folder). */
    @PutMapping("/{id}")
    public ReferenceDamage update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String partName = (String) body.get("partName");
        int severityLevel = body.get("severityLevel") == null ? 2 : ((Number) body.get("severityLevel")).intValue();
        String severityLabel = (String) body.get("severityLabel");
        String description = (String) body.get("description");
        long price = body.get("standardPriceVnd") == null ? 0 : ((Number) body.get("standardPriceVnd")).longValue();
        return service.update(id, partName, severityLevel, severityLabel, description, price);
    }

    /**
     * Goi y gia chuan cho 1 anh mau moi: Gemini so anh nay voi anh mau cu da co
     * gia va tra ve mau tuong duong nhat + gia de xuat. Nguoi dung kiem tra roi
     * bam Luu moi dua vao kho chinh thuc.
     */
    @PostMapping("/{id}/suggest-price")
    public GeminiService.PriceSuggestion suggestPrice(@PathVariable String id) throws Exception {
        return service.suggestPriceFor(id);
    }
}
