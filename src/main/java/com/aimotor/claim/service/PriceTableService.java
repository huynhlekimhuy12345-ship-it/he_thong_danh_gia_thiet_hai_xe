package com.aimotor.claim.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.text.NumberFormat;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Bang gia tham chieu sua chua / thay the theo BO PHAN x MUC DO (xe pho thong,
 * KHONG phan biet hang/model xe), tong hop tu 12 nguon uy tin (MoMo, cac gara
 * lon, bao chi). File goc: src/main/resources/price-table.json - sua file do
 * roi khoi dong lai app la bang gia moi co hieu luc.
 *
 * Vai tro trong he thong: day la CAN CU DUY NHAT de AI dinh gia tung vet.
 * Anh mau REF_xx chi dung de nhan dien bo phan + muc do (so sanh hinh anh);
 * gia ghi tren anh mau la TONG TIEN CA VU cua case MoMo, KHONG phai gia cua
 * rieng bo phan. Neu doc file loi, dung ban rut gon de he thong van chay.
 */
@Service
public class PriceTableService {

    private static final Logger log = LoggerFactory.getLogger(PriceTableService.class);
    private static final NumberFormat VND = NumberFormat.getInstance(new Locale("vi", "VN"));

    private final ObjectMapper objectMapper;
    private volatile String promptBlock;

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
            sb.append("BANG GIA THAM CHIEU SUA CHUA / THAY THE (xe pho thong, VND, chua VAT");
            if (!updated.isBlank()) sb.append(", cap nhat ").append(updated);
            sb.append(", tong hop tu 12 nguon uy tin):\n");
            Map<String, StringBuilder> byPart = new LinkedHashMap<>();
            for (JsonNode item : root.path("items")) {
                String part = unaccent(item.path("part").asText());
                String line = String.format("Muc %d (%s) %s-%s",
                        item.path("severity").asInt(),
                        unaccent(item.path("action").asText()),
                        VND.format(item.path("minVnd").asLong()),
                        VND.format(item.path("maxVnd").asLong()));
                byPart.computeIfAbsent(part, k -> new StringBuilder()).append(line).append("; ");
            }
            for (Map.Entry<String, StringBuilder> e : byPart.entrySet()) {
                String lines = e.getValue().toString().replaceAll("; $", "");
                sb.append("- ").append(e.getKey()).append(": ").append(lines).append("\n");
            }
            sb.append("MOC KIEM TRA TONG: ");
            boolean first = true;
            for (JsonNode a : root.path("caseLevelAnchors")) {
                if (!first) sb.append("; ");
                first = false;
                sb.append(unaccent(a.path("label").asText())).append(" ")
                        .append(VND.format(a.path("minVnd").asLong())).append("-")
                        .append(VND.format(a.path("maxVnd").asLong()));
            }
            sb.append(".\n");
            for (JsonNode n : root.path("notes")) {
                sb.append("LUU Y: ").append(unaccent(n.asText())).append("\n");
            }
            log.info("Da nap bang gia tham chieu: {} muc, cap nhat {}",
                    root.path("items").size(), updated);
            return sb.toString();
        } catch (Exception e) {
            log.warn("Khong doc duoc price-table.json, dung bang gia rut gon: {}", e.getMessage());
            return fallbackBlock();
        }
    }

    /** Ban rut gon dung khi khong doc duoc file JSON - he thong van dinh gia duoc. */
    private String fallbackBlock() {
        return "BANG GIA THAM CHIEU RUT GON (MoMo, bao hiem than vo): "
                + "va quet nho 2.000.000-3.000.000; va cham nang 30.000.000-80.000.000; "
                + "thuy kich 20.000.000-60.000.000. "
                + "Son dam 1 chi tiet 500.000-1.500.000; go + son 1 vung mop 800.000-3.000.000; "
                + "thay can + son 2.000.000-5.000.000; thay capo + son 3.700.000-8.000.000; "
                + "thay vo cua + son 4.000.000-10.000.000; thay kinh chan gio 5.000.000-10.000.000.\n"
                + "LUU Y: gia KHONG phu thuoc hang/model xe. Vuot bang thi chuyen tham dinh vien, KHONG tu nhan he so.\n";
    }

    /** Bo dau tieng Viet de khoi van ban dong nhat voi phan con lai cua System Prompt. */
    private static String unaccent(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return n.replace('đ', 'd').replace('Đ', 'D');
    }
}
