package com.aimotor.claim.controller;

import com.aimotor.claim.model.AppConfig;
import com.aimotor.claim.service.AppConfigService;
import com.aimotor.claim.service.GeminiService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** API Cau hinh Gemini (nhap API key qua giao dien, luu MongoDB). */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final AppConfigService appConfigService;
    private final GeminiService geminiService;

    @Value("${gemini.api.key:}")
    private String apiKeyProperty;

    @Value("${gemini.model:gemini-3.8-flash}")
    private String modelProperty;

    public ConfigController(AppConfigService appConfigService, GeminiService geminiService) {
        this.appConfigService = appConfigService;
        this.geminiService = geminiService;
    }

    @GetMapping
    public Map<String, Object> get() {
        String key = geminiService.resolveApiKey();
        return Map.of(
                "model", geminiService.resolveModel(),
                "apiKeyConfigured", key != null && !key.isBlank(),
                // Chi hien 4 ky tu cuoi de nguoi dung biet key nao dang dung, khong lo key.
                "apiKeyHint", (key != null && key.length() > 4) ? "****" + key.substring(key.length() - 4) : "",
                "apiKeyFromProperties", apiKeyProperty != null && !apiKeyProperty.isBlank(),
                "defaultModel", modelProperty,
                "deductibleVnd", appConfigService.getDeductibleVnd());
    }

    @PostMapping("/gemini-key")
    public Map<String, Object> save(@RequestBody Map<String, String> body) {
        String apiKey = body.get("apiKey");
        String model = body.get("model");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("API key khong duoc de trong.");
        }
        AppConfig saved = appConfigService.save(apiKey, model);
        return Map.of("saved", true, "model", saved.getModel() != null ? saved.getModel() : modelProperty);
    }

    /** Luu muc mien thuong (VND/vu) tru vao boi thuong. */
    @PostMapping("/deductible")
    public Map<String, Object> saveDeductible(@RequestBody Map<String, Object> body) {
        long deductible = body.get("deductibleVnd") == null
                ? 500_000L : ((Number) body.get("deductibleVnd")).longValue();
        AppConfig saved = appConfigService.saveDeductible(deductible);
        return Map.of("saved", true, "deductibleVnd", saved.getDeductibleVnd());
    }
}
