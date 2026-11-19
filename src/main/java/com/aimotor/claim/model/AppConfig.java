package com.aimotor.claim.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Cau hinh ung dung luu trong MongoDB (id co dinh: "gemini"). */
@Document(collection = "app_config")
public class AppConfig {

    @Id
    private String id;

    /** Gemini API key nhap qua giao dien Cai dat (uu tien cao hon application.properties) */
    private String apiKey;

    private String model;

    public AppConfig() {
    }

    public AppConfig(String id, String apiKey, String model) {
        this.id = id;
        this.apiKey = apiKey;
        this.model = model;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
}
