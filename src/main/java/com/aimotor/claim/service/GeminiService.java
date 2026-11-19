package com.aimotor.claim.service;

import com.aimotor.claim.dto.GeminiAssessmentResult;
import com.aimotor.claim.model.AppConfig;
import com.aimotor.claim.model.CustomerVehicle;
import com.aimotor.claim.model.ReferenceDamage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Goi Google Gemini API (multimodal, nhieu anh trong 1 request) de tham dinh
 * thiet hai theo kieu Visual Few-Shot Comparison: anh mau du tru + anh goc
 * cua xe + anh khach gui, ep tra ve application/json theo responseSchema.
 */
@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AppConfigService appConfigService;
    private final PriceTableService priceTableService;

    @Value("${gemini.api.key:}")
    private String apiKeyProperty;

    @Value("${gemini.model:gemini-3.8-flash}")
    private String modelProperty;

    @Value("${gemini.api.base-url:https://generativelanguage.googleapis.com/v1beta/models}")
    private String apiBaseUrl;

    public GeminiService(ObjectMapper objectMapper, AppConfigService appConfigService,
                         PriceTableService priceTableService) {
        this.objectMapper = objectMapper;
        this.appConfigService = appConfigService;
        this.priceTableService = priceTableService;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(60_000);
        factory.setReadTimeout(180_000);
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    public String resolveApiKey() {
        return appConfigService.find()
                .map(AppConfig::getApiKey)
                .filter(k -> k != null && !k.isBlank())
                .orElse(apiKeyProperty);
    }

    public String resolveModel() {
        return appConfigService.find()
                .map(AppConfig::getModel)
                .filter(m -> m != null && !m.isBlank())
                .orElse(modelProperty);
    }

    /** 1 anh mau ke`m nhan de Gemini doi chieu. */
    public record ReferencePayload(ReferenceDamage reference, String base64Jpeg) {
    }

    /**
     * Goi Gemini tham dinh.
     *
     * @param vehicle      ho so xe goc (de doi chieu mau/loai xe)
     * @param references   anh mau du tru (da resize + Base64)
     * @param baselineBase64 anh goc cua xe luc mua bao hiem (co the rong)
     * @param customerBase64 anh khach gui (da resize + Base64)
     * @param blurryHints  canh bao anh mo tu backend, VD: "Anh khach 2 co dau hieu mo"
     */
    public GeminiAssessmentResult assess(CustomerVehicle vehicle,
                                         List<ReferencePayload> references,
                                         List<String> baselineBase64,
                                         List<String> customerBase64,
                                         List<String> blurryHints) {
        String apiKey = resolveApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Chua cau hinh Gemini API key. "
                    + "Dat bien moi truong GEMINI_API_KEY hoac nhap key o tab Cai dat.");
        }
        String model = resolveModel();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("system_instruction", Map.of("parts", List.of(
                Map.of("text", buildSystemPrompt(vehicle, blurryHints)))));
        body.put("contents", List.of(Map.of("parts", buildParts(references, baselineBase64, customerBase64))));
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("temperature", 0.2);
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("responseSchema", buildResponseSchema());
        body.put("generationConfig", generationConfig);

        String url = apiBaseUrl + "/" + model + ":generateContent";
        String raw;
        try {
            raw = restClient.post()
                    .uri(url)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            log.error("Gemini API loi {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new GeminiException("Gemini API tra loi " + e.getStatusCode()
                    + ". Kiem tra API key / model / han muc. Chi tiet: " + e.getResponseBodyAsString(), e);
        }
        if (raw == null || raw.isBlank()) {
            throw new GeminiException("Gemini tra ve phan hoi rong.");
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode error = root.get("error");
            if (error != null) {
                throw new GeminiException("Gemini API bao loi: " + error.path("message").asText());
            }
            String text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText(null);
            if (text == null || text.isBlank()) {
                throw new GeminiException("Gemini khong tra ve noi dung danh gia.");
            }
            String json = stripCodeFences(text);
            return objectMapper.readValue(json, GeminiAssessmentResult.class);
        } catch (GeminiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Khong phan tich duoc JSON tu Gemini: {}", raw, e);
            throw new GeminiException("Khong phan tich duoc ket qua JSON tu Gemini.", e);
        }
    }

    /** Ket qua goi y gia cho 1 anh mau moi dua tren anh mau cu da co gia chuan. */
    public record PriceSuggestion(String matchedReferenceId, String partName, int severityLevel,
                                  long suggestedPriceVnd, double confidenceScore, String reason) {
    }

    /**
     * Goi y gia chuan cho 1 anh mau moi: Gemini so anh moi voi kho anh mau cu
     * (chi anh da co gia chuan) va chon mau tuong duong nhat ve bo phan + loai
     * hu hai + muc do. Khong co mau tuong duong -> matchedReferenceId rong,
     * suggestedPriceVnd = 0, KHONG doan gia.
     */
    public PriceSuggestion suggestPrice(String newImageBase64, List<ReferencePayload> references) {
        String apiKey = resolveApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Chua cau hinh Gemini API key. "
                    + "Dat bien moi truong GEMINI_API_KEY hoac nhap key o tab Cai dat.");
        }
        if (references == null || references.isEmpty()) {
            throw new IllegalStateException("Chua co anh mau cu nao co gia chuan de doi chieu.");
        }
        String model = resolveModel();

        Map<String, Object> body = new LinkedHashMap<>();
        String suggestInstruction = "Ban la chuyen gia dinh gia sua chua xe. Cho 1 ANH MOI (chua co gia) va KHO ANH MAU CU da co gia chuan, "
                + "hay tim anh mau giong nhat ve bo phan + loai hu hai + muc do. Tra loi HOAN TOAN bang tieng Viet, "
                + "chi theo dung JSON schema da cho.\n"
                + "BANG GIA THAM CHIEU (can cu dinh gia, xe pho thong, khong theo hang/model xe):\n"
                + priceTableService.formatForPrompt()
                + "Cach dinh gia: tim anh mau giong nhat de xac dinh bo phan + muc do, roi lay gia tu BANG GIA tren (muc hop li nhat trong khoang cua bo phan + muc do do). "
                + "Gia cua anh mau cu chi dung de doi chieu do lon, KHONG lay truc tiep. "
                + "Neu khong xac dinh duoc bo phan/muc do (anh mo, khong phai o to, khac hoan toan kho mau) thi tra matchedReferenceId = \"\", "
                + "suggestedPriceVnd = 0, confidenceScore = 0 va giai thich ly do. KHONG duoc doan gia khi khong co can cu. confidenceScore: 0.0-1.0.";
        body.put("system_instruction", Map.of("parts", List.of(Map.of("text", suggestInstruction))));
        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("text", "KHO ANH MAU CU (da co gia chuan):"));
        for (ReferencePayload ref : references) {
            ReferenceDamage r = ref.reference();
            String label = String.format("[MAU - %s - %s - Muc %d (%s) - Gia tham chieu %,d VND] Mo ta: %s",
                    r.getReferenceId(), r.getPartName(), r.getSeverityLevel(),
                    r.getSeverityLabel(), r.getStandardPriceVnd(), r.getDescription());
            parts.add(Map.of("text", label));
            parts.add(Map.of("inline_data", Map.of("mime_type", "image/jpeg", "data", ref.base64Jpeg())));
        }
        parts.add(Map.of("text", "[ANH MOI CAN DINH GIA]"));
        parts.add(Map.of("inline_data", Map.of("mime_type", "image/jpeg", "data", newImageBase64)));
        body.put("contents", List.of(Map.of("parts", parts)));
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("temperature", 0.2);
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("responseSchema", buildSuggestionSchema());
        body.put("generationConfig", generationConfig);

        String url = apiBaseUrl + "/" + model + ":generateContent";
        String raw;
        try {
            raw = restClient.post()
                    .uri(url)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            log.error("Gemini API loi {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new GeminiException("Gemini API tra loi " + e.getStatusCode()
                    + ". Kiem tra API key / model / han muc. Chi tiet: " + e.getResponseBodyAsString(), e);
        }
        if (raw == null || raw.isBlank()) {
            throw new GeminiException("Gemini tra ve phan hoi rong.");
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode error = root.get("error");
            if (error != null) {
                throw new GeminiException("Gemini API bao loi: " + error.path("message").asText());
            }
            String text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText(null);
            if (text == null || text.isBlank()) {
                throw new GeminiException("Gemini khong tra ve noi dung goi y gia.");
            }
            return objectMapper.readValue(stripCodeFences(text), PriceSuggestion.class);
        } catch (GeminiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Khong phan tich duoc JSON goi y gia tu Gemini: {}", raw, e);
            throw new GeminiException("Khong phan tich duoc ket qua JSON goi y gia tu Gemini.", e);
        }
    }

    private Map<String, Object> buildSuggestionSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "OBJECT");
        schema.put("properties", Map.of(
                "matchedReferenceId", Map.of("type", "STRING"),
                "partName", Map.of("type", "STRING"),
                "severityLevel", Map.of("type", "INTEGER"),
                "suggestedPriceVnd", Map.of("type", "NUMBER"),
                "confidenceScore", Map.of("type", "NUMBER"),
                "reason", Map.of("type", "STRING")));
        schema.put("required", List.of("matchedReferenceId", "suggestedPriceVnd", "reason"));
        return schema;
    }

    private String stripCodeFences(String text) {
        String t = text.trim();
        if (t.startsWith("```")) {
            int firstNewline = t.indexOf('\n');
            int lastFence = t.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                t = t.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return t;
    }

    private List<Map<String, Object>> buildParts(List<ReferencePayload> references,
                                                 List<String> baselineBase64,
                                                 List<String> customerBase64) {
        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("text", "Duoi day la KHO ANH MAU DU TRU, ANH GOC CUA XE va ANH KHACH GUI. "
                + "Hay tuan thu dung System Prompt: kiem tra hop le truoc, roi moi danh gia thiet hai."));

        for (ReferencePayload ref : references) {
            ReferenceDamage r = ref.reference();
            String label = String.format("[MAU - %s - %s - Muc %d (%s) - Gia tham chieu %,d VND] Mo ta: %s",
                    r.getReferenceId(), r.getPartName(), r.getSeverityLevel(),
                    r.getSeverityLabel(), r.getStandardPriceVnd(), r.getDescription());
            parts.add(Map.of("text", label));
            parts.add(Map.of("inline_data", Map.of("mime_type", "image/jpeg", "data", ref.base64Jpeg())));
        }

        for (int i = 0; i < baselineBase64.size(); i++) {
            parts.add(Map.of("text", "[ANH GOC CUA XE " + (i + 1) + "] - Anh xe luc mua bao hiem, xe chua bi hu hai. "
                    + "Dung de doi chieu: vet nao da co san trong anh goc thi KHONG phai thiet hai moi."));
            parts.add(Map.of("inline_data", Map.of("mime_type", "image/jpeg", "data", baselineBase64.get(i))));
        }

        for (int i = 0; i < customerBase64.size(); i++) {
            parts.add(Map.of("text", "[ANH KHACH " + (i + 1) + "]"));
            parts.add(Map.of("inline_data", Map.of("mime_type", "image/jpeg", "data", customerBase64.get(i))));
        }
        return parts;
    }

    private String buildSystemPrompt(CustomerVehicle vehicle, List<String> blurryHints) {
        StringBuilder sb = new StringBuilder();
        sb.append("Ban la chuyen gia giam dinh thiet hai xe co gioi cho cong ty bao hiem. ");
        sb.append("Nhiem vu: danh gia thiet hai cua 1 chiec xe tu anh khach gui, doi chieu voi Kho anh mau du tru, ");
        sb.append("de xuat muc boi thuong hop ly. Tra loi HOAN TOAN bang tieng Viet, chi theo dung JSON schema da cho, ");
        sb.append("khong them markdown hay giai thich ngoai JSON.\n\n");

        sb.append("THONG TIN XE GOC (ho so bao hiem): bien so ").append(vehicle.getLicensePlate())
                .append(", loai xe ").append(vehicle.getVehicleType());
        if (vehicle.getBrand() != null) sb.append(", hang ").append(vehicle.getBrand());
        if (vehicle.getModel() != null) sb.append(" ").append(vehicle.getModel());
        if (vehicle.getYear() > 0) sb.append(", nam ").append(vehicle.getYear());
        sb.append(", mau goc ").append(vehicle.getColor()).append(".\n\n");

        if (blurryHints != null && !blurryHints.isEmpty()) {
            sb.append("CANH BAO TU HE THONG KIEM TRA ANH: ").append(String.join("; ", blurryHints)).append("\n\n");
        }

        sb.append("BUOC 1 - KIEM TRA HOP LE (VALIDATION GATE, lam truoc moi thu, phai PHAN BIEN thang than):\n");
        sb.append("a) Anh co phai la anh o to khong? Neu co anh khong phai o to (nguoi, nha cua, do vat, anh mang...), validation.status = REJECTED.\n");
        sb.append("a2) Anh chup giay to tuy than (CCCD/CMND, giay phep lai xe, giay dang ky xe) thi validation.status = REJECTED: he thong KHONG thu thap CCCD/CMND, khong can anh giay to. Bien so trong anh chi dung de doi chieu danh tinh, khong hien thi lai trong ket qua.\n");
        sb.append("b) Anh co du net de nhan dien khong? Anh mo, toi, choi sang, chup qua xa hoac rung tay khong thay ro vet hu hai -> validation.status = NEEDS_MORE_INFO, ghi ro anh so may va van de.\n");
        sb.append("c) Anh co du de danh gia khong? Bo anh toi thieu: 4 goc ngoai that (truoc, sau, suon trai, suon phai) + cop + bien so/VIN + can canh tung vet. "
                + "KHONG can anh noi that. Thieu goc nao thi liet ke vao missingPhotos va validation.status = NEEDS_MORE_INFO.\n");
        sb.append("d) Dung xe khong? So mau xe, loai xe, bien so trong anh khach voi thong tin xe goc va anh goc. Khac mau, khac loai xe, bien so khac -> validation.status = REJECTED, ghi ly do.\n");
        sb.append("e) Vet hu hai da co san trong ANH GOC thi KHONG phai thiet hai moi: khong tinh tien cho vet do, ghi chu trong assessmentSummary.\n");
        sb.append("Neu validation.status != OK: damages = [], totalCompensationVnd = 0, requiresHumanReview = false (tru REJECTED thi ghi ro ly do tu choi). "
                + "KHONG duoc doan mo hay danh gia khi anh khong du yeu cau.\n\n");

        sb.append("BUOC 2 - DANH GIA THIET HAI (chi lam khi validation.status = OK):\n");
        sb.append("1) CHONG TRUNG LAP: khach chup 1 xe tu nhieu goc; cung 1 vet hu hai xuat hien o nhieu anh chi duoc tinh boi thuong 1 LAN DUY NHAT. Gop cac anh cung vet truoc khi tinh tien.\n");
        sb.append("2) DOI CHIEU CHUAN XAC: voi moi vet hu hai, so bo phan + muc do voi tung anh mau, chon matchedReferenceId co muc do tuong duong nhat, "
                + "giai thich ngan gon trong referenceComparisonNote. Neu khong co anh mau nao tuong duong, de matchedReferenceId = null, "
                + "uoc luong gia theo mo ta va dat requiresHumanReview = true.\n");
        sb.append("3) CO CHE AN TOAN: neu xe hu hai qua nang, nat khung gam, tui khi bung nhieu, hoac vuot ngoai pham vi bo anh mau, "
                + "dat requiresHumanReview = true va ghi ro humanReviewReason de giam dinh vien kiem tra truc tiep.\n");
        sb.append("4) confidenceScore: 0.0-1.0 theo do ro rang cua vet tren anh. Vet mo hoac chi thay lo mo thi ha diem manh tay.\n");
        sb.append("5) totalCompensationVnd = tong estimatedCostVnd cua cac vet (sau khi da gop trung lap).\n");
        sb.append("6) DINH GIA THEO BANG GIA THAM CHIEU (QUAN TRONG NHAT - doc ky tung dong):\n");
        sb.append(priceTableService.formatForPrompt());
        sb.append("   a) Anh mau REF_xx chi dung de NHAN DIEN bo phan + muc do (so sanh hinh anh). Gia ghi tren moi anh mau la gia tham chieu cua case do, "
                + "co the la TONG TIEN CA VU cua case MoMo - KHONG duoc lay so do lam estimatedCostVnd cua 1 vet.\n");
        sb.append("   b) Voi moi vet: xac dinh bo phan + muc do -> tra BANG GIA tren -> chon MUC GIA HOP LI NHAT trong khoang: gan min neu hu hai nhe trong muc do, "
                + "giua khoang neu trung binh, gan max neu nang. Ghi ro cach chon trong referenceComparisonNote.\n");
        sb.append("   c) Gia KHONG phu thuoc hang/model xe: cung bo phan + cung muc do thi cung gia bang, du la Seltos hay Everest. "
                + "Xe sang/nhap khau, den LED/laser/adaptive, cam bien ADAS, khung suon bien dang nang: neu vuot bang thi giai thich ro va dat requiresHumanReview = true, KHONG tu nhan he so.\n");
        sb.append("   d) Neu bo phan khong co trong bang: uoc luong theo muc tuong duong gan nhat, ghi ro trong referenceComparisonNote va dat requiresHumanReview = true.\n");
        sb.append("   e) Kiem tra tong: so totalCompensationVnd voi moc kiem tra tong trong bang. Vuot xa moc thi giai thich ro va dat requiresHumanReview = true. "
                + "He thong se tu tru muc mien thuong sau khi nhan ket qua, Gemini chi can bao gia tong thiet hai goc.\n");
        return sb.toString();
    }

    /** JSON Schema ep Gemini tra ve dung cau truc, khong markdown thua. */
    private Map<String, Object> buildResponseSchema() {
        Map<String, Object> damageItem = new LinkedHashMap<>();
        damageItem.put("type", "OBJECT");
        damageItem.put("properties", Map.of(
                "partName", Map.of("type", "STRING"),
                "observedCondition", Map.of("type", "STRING"),
                "matchedReferenceId", Map.of("type", "STRING"),
                "referenceComparisonNote", Map.of("type", "STRING"),
                "estimatedCostVnd", Map.of("type", "NUMBER"),
                "confidenceScore", Map.of("type", "NUMBER")));
        damageItem.put("required", List.of("partName", "observedCondition", "estimatedCostVnd", "confidenceScore"));

        Map<String, Object> validation = new LinkedHashMap<>();
        validation.put("type", "OBJECT");
        validation.put("properties", Map.of(
                "status", Map.of("type", "STRING", "enum", List.of("OK", "NEEDS_MORE_INFO", "REJECTED")),
                "issues", Map.of("type", "ARRAY", "items", Map.of("type", "STRING")),
                "missingPhotos", Map.of("type", "ARRAY", "items", Map.of("type", "STRING"))));
        validation.put("required", List.of("status", "issues"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "OBJECT");
        schema.put("properties", Map.of(
                "validation", validation,
                "assessmentSummary", Map.of("type", "STRING"),
                "damages", Map.of("type", "ARRAY", "items", damageItem),
                "totalCompensationVnd", Map.of("type", "NUMBER"),
                "requiresHumanReview", Map.of("type", "BOOLEAN"),
                "humanReviewReason", Map.of("type", "STRING")));
        schema.put("required", List.of("validation", "assessmentSummary", "damages",
                "totalCompensationVnd", "requiresHumanReview", "humanReviewReason"));
        return schema;
    }

    public static class GeminiException extends RuntimeException {
        public GeminiException(String message) { super(message); }
        public GeminiException(String message, Throwable cause) { super(message, cause); }
    }
}
