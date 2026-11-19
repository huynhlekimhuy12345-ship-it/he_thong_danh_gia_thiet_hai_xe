package com.aimotor.claim.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bang gia tham chieu sua chua / thay the theo BO PHAN x MUC DO (xe pho thong,
 * KHONG phan biet hang/model xe), tong hop tu 12 nguon uy tin (MoMo, cac gara
 * lon, bao chi). File goc: src/main/resources/price-table.json - sua file do
 * roi khoi dong lai app la bang gia moi co hieu luc.
 *
 * Vai tro trong he thong: day la CAN CU DUY NHAT de AI dinh gia tung vet.
 * Ảnh mau REF_xx chi dung de nhan dien bo phan + muc do (so sanh hinh anh);
 * gia ghi tren anh mau la TONG TIEN CA VU cua case MoMo, KHONG phai gia cua
 * rieng bo phan. Neu doc file loi, dung ban rut gon de he thong van chay.
 */
@Service
public class PriceTableService {

    private static final Logger log = LoggerFactory.getLogger(PriceTableService.class);
    private static final NumberFormat VND = NumberFormat.getInstance(new Locale("vi", "VN"));

    private final ObjectMapper objectMapper;
    private volatile String promptBlock;
    private volatile List<PriceRow> priceRows;

    /** 1 dong gia: bo phan x muc do x khoang gia (doc tu price-table.json). */
    public record PriceRow(String part, int severity, long minVnd, long maxVnd) {
    }

    public PriceTableService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Khoi van ban bang gia de chen vao System Prompt cho Gemini. */
    public String formatForPrompt() {
        String block = promptBlock;
        if (block == null) {
            synchronized (this) {
                block = promptBlock;
                if (block == null) {
                    block = load();
                    promptBlock = block;
                }
            }
        }
        return block;
    }

    private String load() {
        try (InputStream in = getClass().getResourceAsStream("/price-table.json")) {
            if (in == null) {
                log.warn("Khong tim thay price-table.json tren classpath, dung bang gia rut gon.");
                return fallbackBlock();
            }
            JsonNode root = objectMapper.readTree(in);
            String updated = root.path("updated").asText("");
            StringBuilder sb = new StringBuilder();
            sb.append("BẢNG GIÁ THAM CHIẾU SỬA CHỮA / THAY THẾ (xe phổ thông, VND, chưa VAT");
            if (!updated.isBlank()) sb.append(", cập nhật ").append(updated);
            sb.append(", tổng hợp từ 12 nguồn uy tín):\n");
            Map<String, StringBuilder> byPart = new LinkedHashMap<>();
            for (JsonNode item : root.path("items")) {
                String part = item.path("part").asText();
                String line = String.format("Mức %d (%s) %s-%s",
                        item.path("severity").asInt(),
                        item.path("action").asText(),
                        VND.format(item.path("minVnd").asLong()),
                        VND.format(item.path("maxVnd").asLong()));
                byPart.computeIfAbsent(part, k -> new StringBuilder()).append(line).append("; ");
            }
            for (Map.Entry<String, StringBuilder> e : byPart.entrySet()) {
                String lines = e.getValue().toString().replaceAll("; $", "");
                sb.append("- ").append(e.getKey()).append(": ").append(lines).append("\n");
            }
            sb.append("MỐC KIỂM TRA TỔNG: ");
            boolean first = true;
            for (JsonNode a : root.path("caseLevelAnchors")) {
                if (!first) sb.append("; ");
                first = false;
                sb.append(a.path("label").asText()).append(" ")
                        .append(VND.format(a.path("minVnd").asLong())).append("-")
                        .append(VND.format(a.path("maxVnd").asLong()));
            }
            sb.append(".\n");
            for (JsonNode n : root.path("notes")) {
                sb.append("LƯU Ý: ").append(n.asText()).append("\n");
            }
            log.info("Da nap bang gia tham chieu: {} muc, cap nhat {}",
                    root.path("items").size(), updated);
            return sb.toString();
        } catch (Exception e) {
            log.warn("Khong doc duoc price-table.json, dung bang gia rut gon: {}", e.getMessage());
            return fallbackBlock();
        }
    }

    /** Doc cac dong gia (bo phan x muc do) de chot gia theo nac. Doc loi -> danh sach rong, giu nguyen gia AI. */
    private List<PriceRow> rows() {
        List<PriceRow> rows = priceRows;
        if (rows == null) {
            synchronized (this) {
                rows = priceRows;
                if (rows == null) {
                    rows = loadRows();
                    priceRows = rows;
                }
            }
        }
        return rows;
    }

    private List<PriceRow> loadRows() {
        List<PriceRow> rows = new ArrayList<>();
        try (InputStream in = getClass().getResourceAsStream("/price-table.json")) {
            if (in == null) return rows;
            JsonNode root = objectMapper.readTree(in);
            for (JsonNode item : root.path("items")) {
                rows.add(new PriceRow(item.path("part").asText(), item.path("severity").asInt(),
                        item.path("minVnd").asLong(), item.path("maxVnd").asLong()));
            }
        } catch (Exception e) {
            log.warn("Khong doc duoc bang gia de chot nac: {}", e.getMessage());
        }
        return rows;
    }

    /**
     * Chot gia AI dua ve nac gan nhat cua o (bo phan x muc do): min / giua / max.
     * Gia van len xuong theo muc do hu hai that (nhe -> min, nang -> max), chi
     * khong cho chon so le giua cac nac nua. Khong khop o nao (bo phan la, muc
     * do khong co trong bang) -> tra nguyen gia AI.
     */
    public long snapToAnchor(String partName, int severityLevel, long price) {
        if (partName == null || partName.isBlank() || price <= 0) return price;
        String target = normalize(partName);
        for (PriceRow row : rows()) {
            if (row.severity() != severityLevel) continue;
            if (!matchesPart(target, row.part())) continue;
            long mid = (row.minVnd() + row.maxVnd()) / 2;
            long[] anchors = {row.minVnd(), mid, row.maxVnd()};
            long best = anchors[0];
            long bestDist = Math.abs(price - best);
            for (int i = 1; i < anchors.length; i++) {
                long d = Math.abs(price - anchors[i]);
                if (d < bestDist) { bestDist = d; best = anchors[i]; }
            }
            return best;
        }
        return price;
    }

    /** Ten bo phan trong bang co the gom nhieu phuong an cach nhau bang '/' (VD 'Can truoc / Can sau'). */
    private boolean matchesPart(String normalizedAiPart, String tablePart) {
        for (String alt : tablePart.split("/")) {
            String a = normalize(alt.replaceAll("\\(.*?\\)", ""));
            if (a.isBlank()) continue;
            if (normalizedAiPart.contains(a) || a.contains(normalizedAiPart)) return true;
        }
        return false;
    }

    /** Bo dau tieng Viet + chu thuong + chi giu chu cai/so/khoang trang (de so ten bo phan). */
    private static String normalize(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").replaceAll(" +", " ").trim();
    }

    /** Ban rut gon dung khi khong doc duoc file JSON - he thong van dinh gia duoc. */
    private String fallbackBlock() {
        return "BẢNG GIÁ THAM CHIẾU RÚT GỌN (MoMo, bảo hiểm thân vỏ): "
                + "va quẹt nhỏ 2.000.000-3.000.000; va chạm nặng 30.000.000-80.000.000; "
                + "thủy kích 20.000.000-60.000.000. "
                + "Sơn dặm 1 chi tiết 500.000-1.500.000; gò + sơn 1 vùng móp 800.000-3.000.000; "
                + "thay cản + sơn 2.000.000-5.000.000; thay capo + sơn 3.700.000-8.000.000; "
                + "thay vỏ cửa + sơn 4.000.000-10.000.000; thay kính chắn gió 5.000.000-10.000.000.\n"
                + "LƯU Ý: giá KHÔNG phụ thuộc hãng/model xe. Vượt bảng thì chuyển thẩm định viên, KHÔNG tự nhân hệ số.\n";
    }
}
