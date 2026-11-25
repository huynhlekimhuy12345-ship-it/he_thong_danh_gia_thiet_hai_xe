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
     * Tu khoa nhan dien tung bo phan trong bang gia (da bo dau, tach theo tu).
     * AI goi ten bo phan kieu gi cung duoc ("Cua sau trai", "Canh cua ben phai"...)
     * mien la chua dung tu khoa — de gop dung vet cua cung 1 bo phan qua cac lan chay.
     * So theo TU (khong so chuoi con): "canh" khong chua "can", tranh nhan nham.
     */
    private static final Map<String, List<String>> PART_KEYWORDS = Map.ofEntries(
            Map.entry("Cản trước / Cản sau", List.of("can truoc", "can sau", "can")),
            Map.entry("Cánh cửa", List.of("canh cua", "cua")),
            Map.entry("Nắp capo", List.of("nap capo", "capo")),
            Map.entry("Nắp cốp / Đuôi xe", List.of("nap cop", "cop", "duoi xe")),
            Map.entry("Tai xe / Vè (dè)", List.of("tai xe", "ve", "de")),
            Map.entry("Đèn pha", List.of("den pha")),
            Map.entry("Đèn hậu", List.of("den hau")),
            Map.entry("Kính chắn gió", List.of("kinh chan gio", "kinh")),
            Map.entry("Gương chiếu hậu", List.of("guong")),
            Map.entry("Khung sườn / Kết cấu", List.of("khung suon", "khung", "ket cau")),
            Map.entry("Động cơ (thủy kích)", List.of("dong co", "thuy kich")));

    /** Ten bo phan trong bang gia gan nhat voi ten AI goi (VD "Cua sau trai" -> "Cánh cửa"); khong khop -> null. */
    public String canonicalPart(String aiPartName) {
        if (aiPartName == null || aiPartName.isBlank()) return null;
        List<String> tokens = List.of(normalize(aiPartName).split(" "));
        for (Map.Entry<String, List<String>> e : PART_KEYWORDS.entrySet()) {
            for (String kw : e.getValue()) {
                if (containsTokens(tokens, List.of(kw.split(" ")))) return e.getKey();
            }
        }
        return null;
    }

    /**
     * Khoa gop vet qua cac lan chay: bo phan chuan + ben (trai/phai/truoc/sau).
     * "Canh cua ben phai (truoc va sau)" va "Cua phai (truoc va sau)" ve chung 1 khoa;
     * "Cua truoc phai" va "Cua sau phai" khac nhau nho ben.
     */
    public String groupKey(String aiPartName) {
        String canon = canonicalPart(aiPartName);
        String base = canon != null ? normalize(canon)
                : normalize(aiPartName == null ? "" : aiPartName);
        List<String> tokens = aiPartName == null ? List.of()
                : List.of(normalize(aiPartName).split(" "));
        StringBuilder sides = new StringBuilder();
        for (String s : List.of("trai", "phai", "truoc", "sau")) {
            if (tokens.contains(s)) {
                if (sides.length() > 0) sides.append(',');
                sides.append(s);
            }
        }
        return base + "|" + sides;
    }

    /** 3 nac gia {min, giua, max} cua o (bo phan x muc do); khong co o tuong ung -> null. */
    public long[] anchorsFor(String partName, int severityLevel) {
        if (partName == null || partName.isBlank()) return null;
        String canon = canonicalPart(partName);
        for (PriceRow row : rows()) {
            if (row.severity() != severityLevel) continue;
            if (canon != null ? row.part().equals(canon) : matchesPart(normalize(partName), row.part())) {
                long mid = (row.minVnd() + row.maxVnd()) / 2;
                return new long[]{row.minVnd(), mid, row.maxVnd()};
            }
        }
        return null;
    }

    private static boolean containsTokens(List<String> tokens, List<String> keyword) {
        if (keyword.isEmpty() || keyword.size() > tokens.size()) return false;
        for (int i = 0; i + keyword.size() <= tokens.size(); i++) {
            if (tokens.subList(i, i + keyword.size()).equals(keyword)) return true;
        }
        return false;
    }

    /**
     * Chot gia AI dua ve nac gan nhat cua o (bo phan x muc do): min / giua / max.
     * Gia van len xuong theo muc do hu hai that (nhe -> min, nang -> max), chi
     * khong cho chon so le giua cac nac nua. Khong khop o nao (bo phan la, muc
     * do khong co trong bang) -> tra nguyen gia AI.
     */
    public long snapToAnchor(String partName, int severityLevel, long price) {
        if (partName == null || partName.isBlank() || price <= 0) return price;
        long[] anchors = anchorsFor(partName, severityLevel);
        if (anchors == null) return price;
        long best = anchors[0];
        long bestDist = Math.abs(price - best);
        for (int i = 1; i < anchors.length; i++) {
            long d = Math.abs(price - anchors[i]);
            if (d < bestDist) { bestDist = d; best = anchors[i]; }
        }
        return best;
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

    /** Bo dau tieng Viet + chu thuong + chi giu chu cai/so/khoang trang (de so ten bo phan). Chu y: 'đ' khong tach duoc bang NFD nen phai doi thanh 'd' truoc (neu khong "Đèn pha" thanh "en pha" va khong khop tu khoa nao). */
    private static String normalize(String s) {
        String n = s.replace('đ', 'd').replace('Đ', 'd');
        n = Normalizer.normalize(n, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
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
