package com.aimotor.claim.model;

/** Cau hinh ung dung luu trong SQLite (bang app_config, id co dinh: "gemini"). */
public class AppConfig {

    private String id;

    /** Gemini API key nhap qua giao dien Cai dat (uu tien cao hon application.properties) */
    private String apiKey;

    private String model;

    /**
     * Muc mien thuong (VND/vu) tru vao tong boi thuong truoc khi tra tien.
     * Tham chieu MoMo: thi truong VN thuong 500.000-2.000.000d/vu. Null = chua
     * dat, dung mac dinh 500.000d. Dat 0 = hop dong khong co mien thuong.
     */
    private Long deductibleVnd;

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
    public Long getDeductibleVnd() { return deductibleVnd; }
    public void setDeductibleVnd(Long deductibleVnd) { this.deductibleVnd = deductibleVnd; }
}
