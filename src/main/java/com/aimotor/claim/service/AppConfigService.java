package com.aimotor.claim.service;

import com.aimotor.claim.model.AppConfig;
import com.aimotor.claim.repository.AppConfigRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** Cau hinh Gemini: key nhap qua giao dien (luu MongoDB) uu tien hon application.properties. */
@Service
public class AppConfigService {

    private static final String GEMINI_CONFIG_ID = "gemini";

    private final AppConfigRepository repository;

    public AppConfigService(AppConfigRepository repository) {
        this.repository = repository;
    }

    public Optional<AppConfig> find() {
        return repository.findById(GEMINI_CONFIG_ID);
    }

    public AppConfig save(String apiKey, String model) {
        AppConfig config = repository.findById(GEMINI_CONFIG_ID)
                .orElse(new AppConfig(GEMINI_CONFIG_ID, null, null));
        if (apiKey != null) config.setApiKey(apiKey.trim());
        if (model != null && !model.isBlank()) config.setModel(model.trim());
        return repository.save(config);
    }

    public boolean isConfigured() {
        return find().map(c -> c.getApiKey() != null && !c.getApiKey().isBlank()).orElse(false);
    }

    /** Muc mien thuong (VND/vu). Chua dat thi mac dinh 500.000d (tham chieu MoMo). */
    public long getDeductibleVnd() {
        return find().map(AppConfig::getDeductibleVnd)
                .filter(d -> d != null && d >= 0)
                .orElse(500_000L);
    }

    public AppConfig saveDeductible(long deductibleVnd) {
        AppConfig config = repository.findById(GEMINI_CONFIG_ID)
                .orElse(new AppConfig(GEMINI_CONFIG_ID, null, null));
        config.setDeductibleVnd(Math.max(0, deductibleVnd));
        return repository.save(config);
    }
}
