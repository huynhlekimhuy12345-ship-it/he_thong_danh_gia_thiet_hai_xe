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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
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
     * Goi Gemini tham dinh theo luong kiem chung chat che (de giam sai so giua cac lan chay):
     * chay 3 lan o nhiet do 0 voi cung 1 bo anh + prompt, lay lan chay co tong o giua (trung vi),
     * chot gia tung vet ve nac gan nhat cua bang gia (min/giua/max). Gia van len xuong theo
     * muc do hu hai that, chi khong cho chon so le giua cac nac nua.
     *
     * @param vehicle      ho so xe goc (de doi chieu mau/loai xe)
     * @param references   anh mau du tru (da resize + Base64)
     * @param baselineBase64 anh goc cua xe luc mua bao hiem (co the rong)
     * @param customerBase64 anh khach gui (da resize + Base64)
     * @param blurryHints  canh bao anh mo tu backend, VD: "Ảnh khách 2 có dấu hiệu mờ"
     * @param customerNote mo ta them cua khach ve vu viec (co the rong)
     */
    public GeminiAssessmentResult assess(CustomerVehicle vehicle,
                                         List<ReferencePayload> references,
                                         List<String> baselineBase64,
                                         List<String> customerBase64,
                                         List<String> blurryHints,
                                         String customerNote) {
        List<GeminiAssessmentResult> runs = new ArrayList<>();
        GeminiException lastError = null;
        for (int i = 0; i < 3; i++) {
            try {
                runs.add(assessOnce(vehicle, references, baselineBase64, customerBase64, blurryHints, customerNote));
            } catch (GeminiException e) {
                lastError = e;
                log.warn("Lan chay kiem chung {} that bai: {}", i + 1, e.getMessage());
            }
        }
        if (runs.isEmpty()) {
            throw lastError != null ? lastError : new GeminiException("Gemini không trả về kết quả.");
        }
        return mergeConsensus(runs);
    }

    /**
     * Hop nhat cac lan chay thanh 1 ket qua: lay cac lan OK, chon lan co tong o giua (trung vi),
     * chot gia tung vet ve nac bang gia. Khong co lan OK nao -> lay trang thai nghiem ngat
     * nhat (REJECTED > NEEDS_MORE_INFO) kem cac van de da gom.
     */
    private GeminiAssessmentResult mergeConsensus(List<GeminiAssessmentResult> runs) {
        List<GeminiAssessmentResult> okRuns = new ArrayList<>();
        for (GeminiAssessmentResult r : runs) {
            if ("OK".equals(statusOf(r))) okRuns.add(r);
        }
        if (okRuns.isEmpty()) {
            GeminiAssessmentResult strictest = runs.get(0);
            for (GeminiAssessmentResult r : runs) {
                if ("REJECTED".equals(statusOf(r))) { strictest = r; break; }
                if ("NEEDS_MORE_INFO".equals(statusOf(r))) strictest = r;
            }
            if (strictest.getValidation() == null) {
                GeminiAssessmentResult.Validation v = new GeminiAssessmentResult.Validation();
                v.setStatus("NEEDS_MORE_INFO");
                v.setIssues(List.of("Không nhận được kết quả hợp lệ từ AI."));
                strictest.setValidation(v);
            }
            GeminiAssessmentResult out = new GeminiAssessmentResult();
            out.setValidation(strictest.getValidation());
            out.setAssessmentSummary(strictest.getAssessmentSummary());
            out.setDamages(List.of());
            out.setTotalCompensationVnd(0);
            out.setRequiresHumanReview(false);
            out.setHumanReviewReason(strictest.getHumanReviewReason());
            out.setConsensusNote("Đã chạy kiểm chứng " + runs.size() + "/3 lần ở nhiệt độ 0, không lần nào đủ điều kiện thẩm định.");
            return out;
        }
        okRuns.sort(Comparator.comparingLong(GeminiAssessmentResult::getTotalCompensationVnd));
        GeminiAssessmentResult chosen = okRuns.get((okRuns.size() - 1) / 2);
        boolean weak = okRuns.size() < 3;
        // Chot gia tung vet ve nac bang gia + tinh lai tong.
        long total = 0;
        List<GeminiAssessmentResult.Damage> damages = chosen.getDamages() == null
                ? new ArrayList<>() : new ArrayList<>(chosen.getDamages());
        for (GeminiAssessmentResult.Damage d : damages) {
            long snapped = priceTableService.snapToAnchor(d.getPartName(), d.getSeverityLevel(), d.getEstimatedCostVnd());
            d.setEstimatedCostVnd(snapped);
            total += snapped;
        }
        chosen.setDamages(damages);
        chosen.setTotalCompensationVnd(total);
        if (weak) {
            chosen.setRequiresHumanReview(true);
            String reason = "Chỉ " + okRuns.size() + "/3 lần chạy kiểm chứng cho kết quả OK — cần giám định viên xem lại.";
            chosen.setHumanReviewReason(chosen.getHumanReviewReason() == null || chosen.getHumanReviewReason().isBlank()
                    ? reason : chosen.getHumanReviewReason() + " " + reason);
        }
        List<Long> totals = new ArrayList<>();
        for (GeminiAssessmentResult r : okRuns) totals.add(r.getTotalCompensationVnd());
        totals.sort(Long::compareTo);
        StringBuilder note = new StringBuilder("Đã chạy thẩm định 3 lần ở nhiệt độ 0");
        if (okRuns.size() < 3) note.append(" (").append(okRuns.size()).append(" lần cho kết quả OK)");
        note.append(", lấy kết quả giữa (tổng các lần OK: ");
        for (int i = 0; i < totals.size(); i++) {
            if (i > 0) note.append(" / ");
            note.append(String.format("%,d", totals.get(i))).append(" đ");
        }
        note.append("); giá từng vết đã chốt theo 3 nấc của bảng giá (min/giữa/max).");
        chosen.setConsensusNote(note.toString());
        return chosen;
    }

    private static String statusOf(GeminiAssessmentResult r) {
        return r.getValidation() == null ? null : r.getValidation().getStatus();
    }

    /**
     * 1 lan goi Gemini tham dinh duy nhat (phuc vu luong kiem chung 3 lan o assess()).
     *
     * @param vehicle      ho so xe goc (de doi chieu mau/loai xe)
     * @param references   anh mau du tru (da resize + Base64)
     * @param baselineBase64 anh goc cua xe luc mua bao hiem (co the rong)
     * @param customerBase64 anh khach gui (da resize + Base64)
     * @param blurryHints  canh bao anh mo tu backend, VD: "Ảnh khách 2 có dấu hiệu mờ"
     * @param customerNote mo ta them cua khach ve vu viec (co the rong)
     */
    private GeminiAssessmentResult assessOnce(CustomerVehicle vehicle,
                                         List<ReferencePayload> references,
                                         List<String> baselineBase64,
                                         List<String> customerBase64,
                                         List<String> blurryHints,
                                         String customerNote) {
        String apiKey = resolveApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Chưa cấu hình Gemini API key. "
                    + "Đặt biến môi trường GEMINI_API_KEY hoặc nhập key ở tab Cài đặt.");
        }
        String model = resolveModel();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("system_instruction", Map.of("parts", List.of(
                Map.of("text", buildSystemPrompt(vehicle, blurryHints)))));
        body.put("contents", List.of(Map.of("parts", buildParts(references, baselineBase64, customerBase64, customerNote))));
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("temperature", 0);
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("responseSchema", buildResponseSchema());
        body.put("generationConfig", generationConfig);

        String url = apiBaseUrl + "/" + model + ":generateContent";
        String raw;
        try {
            // Doc dang byte[] thay vi String: Gemini hoac proxy giua duong co the tra ve
            // Content-Type la (VD: application/octet-stream) ma StringHttpMessageConverter
            // khong doc duoc -> loi "Error while extracting response..." kho hieu.
            byte[] bytes = restClient.post()
                    .uri(url)
                    .header("x-goog-api-key", apiKey)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(byte[].class);
            raw = bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
        } catch (RestClientResponseException e) {
            log.error("Gemini API loi {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new GeminiException("Gemini API trả lời " + e.getStatusCode()
                    + ". Kiểm tra API key / model / hạn mức. Chi tiết: " + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            // Loi mang / timeout / phan hoi la: ghi log de doan nguyen nhan that.
            log.error("Khong goi duoc Gemini API: {}", e.getMessage());
            throw new GeminiException("Không gọi được Gemini API (" + e.getMessage()
                    + "). Kiểm tra mạng, API key và thử lại.", e);
        }
        if (raw == null || raw.isBlank()) {
            throw new GeminiException("Gemini trả về phản hồi rỗng.");
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode error = root.get("error");
            if (error != null) {
                throw new GeminiException("Gemini API báo lỗi: " + error.path("message").asText());
            }
            String text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText(null);
            if (text == null || text.isBlank()) {
                throw new GeminiException("Gemini không trả về nội dung đánh giá.");
            }
            String json = stripCodeFences(text);
            return objectMapper.readValue(json, GeminiAssessmentResult.class);
        } catch (GeminiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Khong phan tich duoc JSON tu Gemini: {}", raw, e);
            throw new GeminiException("Không phân tích được kết quả JSON từ Gemini.", e);
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
            throw new IllegalStateException("Chưa cấu hình Gemini API key. "
                    + "Đặt biến môi trường GEMINI_API_KEY hoặc nhập key ở tab Cài đặt.");
        }
        if (references == null || references.isEmpty()) {
            throw new IllegalStateException("Chưa có ảnh mẫu cũ nào có giá chuẩn để đối chiếu.");
        }
        String model = resolveModel();

        Map<String, Object> body = new LinkedHashMap<>();
        String suggestInstruction = "Bạn là chuyên gia định giá sửa chữa xe. Cho 1 ẢNH MỚI (chưa có giá) và KHO ẢNH MẪU CŨ đã có giá chuẩn, "
                + "hãy tìm ảnh mẫu giống nhất về bộ phận + loại hư hại + mức độ. Trả lời HOÀN TOÀN bằng tiếng Việt có dấu, "
                + "chỉ theo đúng JSON schema đã cho.\n"
                + "BẢNG GIÁ THAM CHIẾU (căn cứ định giá, xe phổ thông, không theo hãng/model xe):\n"
                + priceTableService.formatForPrompt()
                + "Cách định giá: tìm ảnh mẫu giống nhất để xác định bộ phận + mức độ, rồi lấy giá từ BẢNG GIÁ trên (chỉ 1 trong 3 nấc: min/giữa/max của khoảng, theo nhẹ/trung bình/nặng trong mức độ đó). "
                + "Giá của ảnh mẫu cũ chỉ dùng để đối chiếu độ lớn, KHÔNG lấy trực tiếp. "
                + "Nếu không xác định được bộ phận/mức độ (ảnh mờ, không phải ô tô, khác hoàn toàn kho mẫu) thì trả matchedReferenceId = \"\", "
                + "suggestedPriceVnd = 0, confidenceScore = 0 và giải thích lý do. KHÔNG được đoán giá khi không có căn cứ. confidenceScore: 0.0-1.0.";
        body.put("system_instruction", Map.of("parts", List.of(Map.of("text", suggestInstruction))));
        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("text", "KHO ẢNH MẪU CŨ (đã có giá chuẩn):"));
        for (ReferencePayload ref : references) {
            ReferenceDamage r = ref.reference();
            String label = String.format("[MẪU - %s - %s - Mức %d (%s) - Giá tham chiếu %,d VND] Mô tả: %s",
                    r.getReferenceId(), r.getPartName(), r.getSeverityLevel(),
                    r.getSeverityLabel(), r.getStandardPriceVnd(), r.getDescription());
            parts.add(Map.of("text", label));
            parts.add(Map.of("inline_data", Map.of("mime_type", "image/jpeg", "data", ref.base64Jpeg())));
        }
        parts.add(Map.of("text", "[ẢNH MỚI CẦN ĐỊNH GIÁ]"));
        parts.add(Map.of("inline_data", Map.of("mime_type", "image/jpeg", "data", newImageBase64)));
        body.put("contents", List.of(Map.of("parts", parts)));
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("temperature", 0);
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("responseSchema", buildSuggestionSchema());
        body.put("generationConfig", generationConfig);

        String url = apiBaseUrl + "/" + model + ":generateContent";
        String raw;
        try {
            // Doc dang byte[] thay vi String: Gemini hoac proxy giua duong co the tra ve
            // Content-Type la (VD: application/octet-stream) ma StringHttpMessageConverter
            // khong doc duoc -> loi "Error while extracting response..." kho hieu.
            byte[] bytes = restClient.post()
                    .uri(url)
                    .header("x-goog-api-key", apiKey)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(byte[].class);
            raw = bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
        } catch (RestClientResponseException e) {
            log.error("Gemini API loi {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new GeminiException("Gemini API trả lời " + e.getStatusCode()
                    + ". Kiểm tra API key / model / hạn mức. Chi tiết: " + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            // Loi mang / timeout / phan hoi la: ghi log de doan nguyen nhan that.
            log.error("Khong goi duoc Gemini API: {}", e.getMessage());
            throw new GeminiException("Không gọi được Gemini API (" + e.getMessage()
                    + "). Kiểm tra mạng, API key và thử lại.", e);
        }
        if (raw == null || raw.isBlank()) {
            throw new GeminiException("Gemini trả về phản hồi rỗng.");
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode error = root.get("error");
            if (error != null) {
                throw new GeminiException("Gemini API báo lỗi: " + error.path("message").asText());
            }
            String text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText(null);
            if (text == null || text.isBlank()) {
                throw new GeminiException("Gemini không trả về nội dung gợi ý giá.");
            }
            PriceSuggestion suggestion = objectMapper.readValue(stripCodeFences(text), PriceSuggestion.class);
            if (suggestion.suggestedPriceVnd() > 0) {
                long snapped = priceTableService.snapToAnchor(
                        suggestion.partName(), suggestion.severityLevel(), suggestion.suggestedPriceVnd());
                suggestion = new PriceSuggestion(suggestion.matchedReferenceId(), suggestion.partName(),
                        suggestion.severityLevel(), snapped, suggestion.confidenceScore(), suggestion.reason());
            }
            return suggestion;
        } catch (GeminiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Khong phan tich duoc JSON goi y gia tu Gemini: {}", raw, e);
            throw new GeminiException("Không phân tích được kết quả JSON gợi ý giá từ Gemini.", e);
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
                                                 List<String> customerBase64,
                                                 String customerNote) {
        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("text", "Dưới đây là KHO ẢNH MẪU DỰ TRỮ, ẢNH GỐC CỦA XE và ẢNH KHÁCH GỬI. "
                + "Hãy tuân thủ đúng System Prompt: kiểm tra hợp lệ trước, rồi mới đánh giá thiệt hại."));

        for (ReferencePayload ref : references) {
            ReferenceDamage r = ref.reference();
            // Nhãn gửi AI KHÔNG kèm giá: giá mẫu (thường là tổng tiền cả vụ) mà lọt vào
            // mắt AI thì vẫn neo giá dù prompt đã cấm dùng. Giá chỉ lấy từ bảng giá.
            String label = String.format("[MẪU - %s - %s - Mức %d (%s)] Mô tả: %s",
                    r.getReferenceId(), r.getPartName(), r.getSeverityLevel(),
                    r.getSeverityLabel(), r.getDescription());
            parts.add(Map.of("text", label));
            parts.add(Map.of("inline_data", Map.of("mime_type", "image/jpeg", "data", ref.base64Jpeg())));
        }

        for (int i = 0; i < baselineBase64.size(); i++) {
            parts.add(Map.of("text", "[ẢNH GỐC CỦA XE " + (i + 1) + "] - Ảnh xe lúc mua bảo hiểm, xe chưa bị hư hại. "
                    + "Dùng để đối chiếu: vết nào đã có sẵn trong ảnh gốc thì KHÔNG phải thiệt hại mới."));
            parts.add(Map.of("inline_data", Map.of("mime_type", "image/jpeg", "data", baselineBase64.get(i))));
        }

        if (customerNote != null && !customerNote.isBlank()) {
            parts.add(Map.of("text", "[MÔ TẢ CỦA KHÁCH HÀNG VỀ VỤ VIỆC]: " + customerNote));
        }

        for (int i = 0; i < customerBase64.size(); i++) {
            parts.add(Map.of("text", "[ẢNH KHÁCH " + (i + 1) + "]"));
            parts.add(Map.of("inline_data", Map.of("mime_type", "image/jpeg", "data", customerBase64.get(i))));
        }
        return parts;
    }

    private String buildSystemPrompt(CustomerVehicle vehicle, List<String> blurryHints) {
        StringBuilder sb = new StringBuilder();
        sb.append("Bạn là chuyên gia giám định thiệt hại xe cơ giới cho công ty bảo hiểm. ");
        sb.append("Nhiệm vụ: đánh giá thiệt hại của 1 chiếc xe từ ảnh khách gửi, đối chiếu với Kho ảnh mẫu dự trữ, ");
        sb.append("đề xuất mức bồi thường hợp lý. Trả lời HOÀN TOÀN bằng tiếng Việt có dấu, chỉ theo đúng JSON schema đã cho, ");
        sb.append("không thêm markdown hay giải thích ngoài JSON. Tất cả các trường văn bản trong JSON (assessmentSummary, observedCondition, ");
        sb.append("referenceComparisonNote, humanReviewReason, issues, missingPhotos) đều phải viết bằng tiếng Việt có dấu đầy đủ.\n\n");

        sb.append("THÔNG TIN XE GỐC (hồ sơ bảo hiểm): biển số ").append(vehicle.getLicensePlate())
                .append(", loại xe ").append(vehicle.getVehicleType());
        if (vehicle.getBrand() != null) sb.append(", hãng ").append(vehicle.getBrand());
        if (vehicle.getModel() != null) sb.append(" ").append(vehicle.getModel());
        if (vehicle.getYear() > 0) sb.append(", năm ").append(vehicle.getYear());
        sb.append(", màu gốc ").append(vehicle.getColor()).append(".\n\n");

        if (blurryHints != null && !blurryHints.isEmpty()) {
            sb.append("CẢNH BÁO TỪ HỆ THỐNG KIỂM TRA ẢNH: ").append(String.join("; ", blurryHints)).append("\n\n");
        }

        sb.append("MÔ TẢ CỦA KHÁCH HÀNG (nếu có, ở phần [MÔ TẢ CỦA KHÁCH HÀNG VỀ VỤ VIỆC]): dùng để hiểu bối cảnh vụ việc ");
        sb.append("(diễn biến, vị trí va chạm), nhưng mức độ hư hại và giá vẫn phải căn cứ vào ẢNH — mô tả không thay thế bằng chứng hình ảnh.\n\n");

        sb.append("BƯỚC 1 - KIỂM TRA HỢP LỆ (VALIDATION GATE, làm trước mọi thứ, phải PHẢN BIỆN thẳng thắn):\n");
        sb.append("a) Ảnh có phải là ảnh ô tô không? Nếu có ảnh không phải ô tô (người, nhà cửa, đồ vật, ảnh mạng...), validation.status = REJECTED.\n");
        sb.append("a2) Ảnh chụp giấy tờ tùy thân (CCCD/CMND, giấy phép lái xe, giấy đăng ký xe) thì validation.status = REJECTED: hệ thống KHÔNG thu thập CCCD/CMND, không cần ảnh giấy tờ. Biển số trong ảnh chỉ dùng để đối chiếu danh tính, không hiển thị lại trong kết quả.\n");
        sb.append("b) Ảnh có đủ nét để nhận diện không? Ảnh mờ, tối, chói sáng, chụp quá xa hoặc rung tay không thấy rõ vết hư hại -> validation.status = NEEDS_MORE_INFO, ghi rõ ảnh số mấy và vấn đề.\n");
        sb.append("c) Ảnh có đủ để đánh giá không? Bộ ảnh tối thiểu: 4 góc ngoại thất (trước, sau, sườn trái, sườn phải) + cốp + biển số/VIN + cận cảnh từng vết. "
                + "KHÔNG cần ảnh nội thất. Thiếu góc nào thì liệt kê vào missingPhotos và validation.status = NEEDS_MORE_INFO.\n");
        sb.append("d) Đúng xe không? So màu xe, loại xe, biển số trong ảnh khách với thông tin xe gốc và ảnh gốc. Khác màu, khác loại xe, biển số khác -> validation.status = REJECTED, ghi lý do.\n");
        sb.append("e) Vết hư hại đã có sẵn trong ẢNH GỐC thì KHÔNG phải thiệt hại mới: không tính tiền cho vết đó, ghi chú trong assessmentSummary.\n");
        sb.append("Nếu validation.status != OK: damages = [], totalCompensationVnd = 0, requiresHumanReview = false (trừ REJECTED thì ghi rõ lý do từ chối). "
                + "KHÔNG được đoán mò hay đánh giá khi ảnh không đủ yêu cầu.\n\n");

        sb.append("BƯỚC 2 - ĐÁNH GIÁ THIỆT HẠI (chỉ làm khi validation.status = OK):\n");
        sb.append("1) CHỐNG TRÙNG LẶP: khách chụp 1 xe từ nhiều góc; cùng 1 vết hư hại xuất hiện ở nhiều ảnh chỉ được tính bồi thường 1 LẦN DUY NHẤT. Gộp các ảnh cùng vết trước khi tính tiền.\n");
        sb.append("2) ĐỐI CHIẾU CHUẨN XÁC: với mỗi vết hư hại, so bộ phận + mức độ với từng ảnh mẫu, chọn matchedReferenceId có mức độ tương đương nhất, "
                + "ghi rõ severityLevel của vết (1 = Xước nhẹ, 2 = Móp vừa, 3 = Vỡ/Biến dạng nặng) đúng với mức độ đã phán, "
                + "giải thích ngắn gọn trong referenceComparisonNote. Nếu không có ảnh mẫu nào tương đương, để matchedReferenceId = null, "
                + "ước lượng giá theo mô tả và đặt requiresHumanReview = true.\n");
        sb.append("3) CƠ CHẾ AN TOÀN: nếu xe hư hại quá nặng, nát khung gầm, túi khí bung nhiều, hoặc vượt ngoài phạm vi bộ ảnh mẫu, "
                + "đặt requiresHumanReview = true và ghi rõ humanReviewReason để giám định viên kiểm tra trực tiếp.\n");
        sb.append("4) confidenceScore: 0.0-1.0 theo độ rõ ràng của vết trên ảnh. Vết mờ hoặc chỉ thấy lờ mờ thì hạ điểm mạnh tay.\n");
        sb.append("5) totalCompensationVnd = tổng estimatedCostVnd của các vết (sau khi đã gộp trùng lặp).\n");
        sb.append("6) ĐỊNH GIÁ THEO BẢNG GIÁ THAM CHIẾU (QUAN TRỌNG NHẤT - đọc kỹ từng dòng):\n");
        sb.append(priceTableService.formatForPrompt());
        sb.append("   a) Ảnh mẫu REF_xx chỉ dùng để NHẬN DIỆN bộ phận + mức độ (so sánh hình ảnh). Nhãn ảnh mẫu KHÔNG kèm giá — "
                + "giá của case mẫu (thường là TỔNG TIỀN CẢ VỤ) không được dùng làm estimatedCostVnd của 1 vết.\n");
        sb.append("   b) Với mỗi vết: xác định bộ phận + mức độ (severityLevel) -> tra BẢNG GIÁ trên -> estimatedCostVnd CHỈ được chọn 1 trong 3 nấc: "
                + "giá MIN của khoảng (hư hại nhẹ trong mức độ), giá GIỮA khoảng (trung bình), giá MAX của khoảng (nặng trong mức độ). "
                + "KHÔNG chọn số khác ngoài 3 nấc này. Ghi rõ nấc đã chọn (min/giữa/max) trong referenceComparisonNote.\n");
        sb.append("   c) Giá KHÔNG phụ thuộc hãng/model xe: cùng bộ phận + cùng mức độ thì cùng giá bảng, dù là Seltos hay Everest. "
                + "Xe sang/nhập khẩu, đèn LED/laser/adaptive, cảm biến ADAS, khung sườn biến dạng nặng: nếu vượt bảng thì giải thích rõ và đặt requiresHumanReview = true, KHÔNG tự nhân hệ số.\n");
        sb.append("   d) Nếu bộ phận không có trong bảng: ước lượng theo mức tương đương gần nhất, ghi rõ trong referenceComparisonNote và đặt requiresHumanReview = true.\n");
        sb.append("   e) Kiểm tra tổng: so totalCompensationVnd với mốc kiểm tra tổng trong bảng. Vượt xa mốc thì giải thích rõ và đặt requiresHumanReview = true. "
                + "Hệ thống sẽ tự trừ mức miễn thường sau khi nhận kết quả, Gemini chỉ cần báo giá tổng thiệt hại gốc.\n");
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
                "severityLevel", Map.of("type", "INTEGER"),
                "estimatedCostVnd", Map.of("type", "NUMBER"),
                "confidenceScore", Map.of("type", "NUMBER")));
        damageItem.put("required", List.of("partName", "observedCondition", "severityLevel", "estimatedCostVnd", "confidenceScore"));

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
