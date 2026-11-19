package com.aimotor.claim.controller;

import com.aimotor.claim.model.ReferenceDamage;
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
}
