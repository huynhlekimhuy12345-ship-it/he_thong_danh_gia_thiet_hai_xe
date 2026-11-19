package com.aimotor.claim.service;

import com.aimotor.claim.model.ReferenceDamage;
import com.aimotor.claim.repository.ReferenceDamageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** Quan ly Kho anh mau du tru: them/xoa/lay danh sach + nap anh gui Gemini. */
@Service
public class ReferenceLibraryService {

    private final ReferenceDamageRepository repository;
    private final ImagePreprocessingService imageService;

    @Value("${app.storage.reference-dir:./storage/reference-damages}")
    private String referenceDir;

    public ReferenceLibraryService(ReferenceDamageRepository repository,
                                   ImagePreprocessingService imageService) {
        this.repository = repository;
        this.imageService = imageService;
    }

    public List<ReferenceDamage> listAll() {
        return repository.findAll();
    }

    public ReferenceDamage add(String referenceId, String partName, int severityLevel,
                               String severityLabel, String description, long standardPriceVnd,
                               MultipartFile image) throws IOException {
        if (repository.existsByReferenceId(referenceId)) {
            throw new IllegalArgumentException("Ma mau " + referenceId + " da ton tai.");
        }
        imageService.validateImage(image);
        String imagePath = imageService.storeFile(image, Paths.get(referenceDir));
        ReferenceDamage ref = new ReferenceDamage(referenceId, partName, severityLevel,
                severityLabel, description, standardPriceVnd, imagePath);
        return repository.save(ref);
    }

    public void delete(String id) {
        repository.findById(id).ifPresent(ref -> {
            repository.delete(ref);
            try {
                Files.deleteIfExists(Paths.get(ref.getImagePath()));
            } catch (IOException ignored) {
                // Xoa DB duoc la chinh, file rac co the don sau.
            }
        });
    }

    /** Nap toan bo anh mau (resize + Base64) de gui kem trong 1 request Gemini. */
    public List<GeminiService.ReferencePayload> loadPayloads() throws IOException {
        List<GeminiService.ReferencePayload> payloads = new ArrayList<>();
        for (ReferenceDamage ref : repository.findAll()) {
            Path path = Paths.get(ref.getImagePath());
            if (Files.exists(path)) {
                payloads.add(new GeminiService.ReferencePayload(ref, imageService.fileToBase64(path)));
            }
        }
        return payloads;
    }
}
