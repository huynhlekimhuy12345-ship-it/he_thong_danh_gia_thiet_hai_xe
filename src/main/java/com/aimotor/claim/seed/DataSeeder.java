package com.aimotor.claim.seed;

import com.aimotor.claim.model.ReferenceDamage;
import com.aimotor.claim.repository.ReferenceDamageRepository;
import com.aimotor.claim.service.ReferenceLibraryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Seed Kho anh mau du tru khi SQLite con trong.
 * Ảnh mau nam trong ./storage/reference-damages/ (da kem san 4 anh test).
 * Muon them anh mau: copy file anh vao thu muc do roi bam "Quet folder" o tab
 * Kho anh mau, hoac them bang trang Quan tri — app cung tu quet moi lan khoi dong.
 */
@Component
@Order(1)
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final ReferenceDamageRepository repository;
    private final ReferenceLibraryService referenceService;

    @Value("${app.storage.reference-dir:./storage/reference-damages}")
    private String referenceDir;

    @Value("${app.storage.vehicle-dir:./storage/vehicles}")
    private String vehicleDir;

    @Value("${app.storage.claim-dir:./storage/claims}")
    private String claimDir;

    public DataSeeder(ReferenceDamageRepository repository, ReferenceLibraryService referenceService) {
        this.repository = repository;
        this.referenceService = referenceService;
    }

    @Override
    public void run(String... args) throws Exception {
        Files.createDirectories(Paths.get(referenceDir));
        Files.createDirectories(Paths.get(vehicleDir));
        Files.createDirectories(Paths.get(claimDir));

        if (repository.count() == 0) {
            seedInitialReferences();
        } else {
            log.info("Kho anh mau da co {} anh, bo qua seed.", repository.count());
        }

        // Luon quet folder: anh nguoi dung tu tha vao folder se duoc nhan vao kho
        // o dang nhap (can bo sung metadata sau).
        try {
            int imported = referenceService.scanFolder().size();
            if (imported > 0) {
                log.info("Tu dong nhan {} anh mau moi tu folder {}.", imported, referenceDir);
            }
        } catch (Exception e) {
            log.warn("Khong quet duoc folder anh mau {}: {}", referenceDir, e.getMessage());
        }
    }

    private void seedInitialReferences() {
        List<ReferenceDamage> seeds = List.of(
                new ReferenceDamage("REF_01", "Cản trước", 1, "Mức 1: Xước nhẹ",
                        "Vết xước dài nhẹ trên lớp sơn cản trước, không móp, không vỡ. Chỉ cần sơn lại cục bộ.",
                        1_500_000, "storage/reference-damages/ref-01-bumper-scratch.jpg"),
                new ReferenceDamage("REF_02", "Cửa sau bên phụ", 2, "Mức 2: Móp vừa",
                        "Cửa sau bên phụ móp vào trong một vùng lớn, sơn còn nguyên vẹn. Cần nắn kéo + sơn lại.",
                        4_500_000, "storage/reference-damages/ref-02-door-dent.jpg"),
                new ReferenceDamage("REF_03", "Đèn pha trái", 3, "Mức 3: Vỡ/Biến dạng nặng",
                        "Đèn pha trái vỡ nát hoàn toàn, kính vỡ vụn, vỏ đèn rách. Bắt buộc thay mới cụm đèn.",
                        3_800_000, "storage/reference-damages/ref-03-headlight-broken.jpg"),
                new ReferenceDamage("REF_04", "Nắp capo", 3, "Mức 3: Vỡ/Biến dạng nặng",
                        "Nắp capo bị dập nát, biến dạng nặng sau va chạm mạnh phía trước. Thay mới nắp capo + kiểm tra khung gầm.",
                        12_000_000, "storage/reference-damages/ref-04-hood-crushed.jpg"),
                // --- 14 case thuc te MoMo (nguon: momo.vn/bao-hiem-o-to/than-vo) ---
                // Luu y: "phi boi thuong" la TONG tien ca vu (toan bo thiet hai cua vu do),
                // gan cho vung hu hai chinh, khong phai bang gia theo tung mon.
                new ReferenceDamage("REF_05", "Động cơ (thủy kích)", 3, "Mức 3: Vỡ/Biến dạng nặng",
                        "Case thực tế MoMo - Kia Seltos 2024 (PVI): xe bị ngập nước khi qua đoạn đường ngập sau mưa lớn, hư hỏng động cơ. Phí bảo hiểm 7.110.000đ/năm, bồi thường 42.000.000đ.",
                        42_000_000, "storage/reference-damages/ref-05-seltos-thuy-kich.jpg"),
                new ReferenceDamage("REF_06", "Đuôi xe (cản sau)", 3, "Mức 3: Vỡ/Biến dạng nặng",
                        "Case thực tế MoMo - Kia Carnival 2024 (PVI): va chạm khi lùi xe trong bãi đỗ đông, hư hỏng phần đuôi xe. Phí bảo hiểm 11.300.000đ/năm, bồi thường 32.000.000đ.",
                        32_000_000, "storage/reference-damages/ref-06-carnival-duoi-xe.jpg"),
                new ReferenceDamage("REF_07", "Thân xe (trầy xước, móp nhẹ)", 2, "Mức 2: Móp vừa",
                        "Case thực tế MoMo - Honda HR-V 2024 (PVI): va chạm nhẹ với xe máy trong hẻm nhỏ, trầy xước và móp nhẹ. Phí bảo hiểm 7.100.000đ/năm, bồi thường 14.000.000đ.",
                        14_000_000, "storage/reference-damages/ref-07-hrv-tray-xuoc.jpg"),
                new ReferenceDamage("REF_08", "Đầu xe + Đèn pha phải", 3, "Mức 3: Vỡ/Biến dạng nặng",
                        "Case thực tế MoMo - Mitsubishi Xpander 2024 (PJICO): va chạm với xe máy trong hẻm nhỏ buổi tối, móp đầu xe và vỡ đèn pha bên phải. Phí bảo hiểm 5.006.000đ/năm, bồi thường 13.200.000đ.",
                        13_200_000, "storage/reference-damages/ref-08-xpander-den-pha.jpg"),
                new ReferenceDamage("REF_09", "Toàn thân + Gương chiếu hậu", 2, "Mức 2: Móp vừa",
                        "Case thực tế MoMo - Toyota Fortuner 2022 (PJICO): xe bị phá hoại khi đỗ qua đêm, trầy xước toàn bộ thân xe và vỡ gương chiếu hậu. Phí bảo hiểm 7.790.000đ/năm, bồi thường 22.500.000đ.",
                        22_500_000, "storage/reference-damages/ref-09-fortuner-toan-than.jpg"),
                new ReferenceDamage("REF_10", "Cua xe", 2, "Mức 2: Móp vừa",
                        "Case thực tế MoMo - Toyota Camry 2023 (PJICO): va chạm với xe khác khi chuyển làn trên đường lớn, hư hỏng cửa xe. Phí bảo hiểm 18.200.000đ/năm, bồi thường 25.000.000đ.",
                        25_000_000, "storage/reference-damages/ref-10-camry-cua-xe.jpg"),
                new ReferenceDamage("REF_11", "Cản sau + Đèn hậu", 2, "Mức 2: Móp vừa",
                        "Case thực tế MoMo - Honda CR-V 2023 (Bảo Việt): va chạm khi lùi xe trong bãi đỗ, hư hỏng cản sau và đèn hậu. Phí bảo hiểm 11.400.000đ/năm, bồi thường 28.000.000đ.",
                        28_000_000, "storage/reference-damages/ref-11-crv-can-sau.jpg"),
                new ReferenceDamage("REF_12", "Đầu xe (trầy xước, móp)", 2, "Mức 2: Móp vừa",
                        "Case thực tế MoMo - VinFast VF5 2024 (Bảo Việt): va chạm với trụ bê tông trong bãi đỗ, trầy xước và móp đầu xe. Phí bảo hiểm 6.050.000đ/năm, bồi thường 8.500.000đ.",
                        8_500_000, "storage/reference-damages/ref-12-vf5-dau-xe.jpg"),
                new ReferenceDamage("REF_13", "Đầu xe + Thân xe", 3, "Mức 3: Vỡ/Biến dạng nặng",
                        "Case thực tế MoMo - VinFast VF9 2024 (Bảo Việt): va chạm mạnh với xe khác trên quốc lộ, hư hỏng phần đầu và thân xe. Phí bảo hiểm 18.600.000đ/năm, bồi thường 58.000.000đ.",
                        58_000_000, "storage/reference-damages/ref-13-vf9-dau-than.jpg"),
                new ReferenceDamage("REF_14", "Nắp capo", 2, "Mức 2: Móp vừa",
                        "Case thực tế MoMo - Toyota Innova 2021 (GIC): bị rơi vật từ trên cao khi đỗ xe, móp capo và trầy xước nhẹ. Phí bảo hiểm 7.400.000đ/năm, bồi thường 12.000.000đ.",
                        12_000_000, "storage/reference-damages/ref-14-innova-capo.jpg"),
                new ReferenceDamage("REF_15", "Thân xe (trầy xước nhẹ)", 1, "Mức 1: Xước nhẹ",
                        "Case thực tế MoMo - Mazda 2 2022 (DBV): bị trầy xước nhẹ khi đỗ xe trên vỉa hè, không xác định được bên gây ra. Phí bảo hiểm 5.094.000đ/năm, bồi thường 4.500.000đ.",
                        4_500_000, "storage/reference-damages/ref-15-mazda2-tray-nhe.jpg"),
                new ReferenceDamage("REF_16", "Cản trước (trầy xước, bong sơn)", 2, "Mức 2: Móp vừa",
                        "Case thực tế MoMo - VinFast VF3 2024 (DBV): cản trước trầy xước, bong sơn (ảnh thực tế; MoMo không ghi mô tả chi tiết). Phí bảo hiểm 3.300.000đ/năm, bồi thường 6.800.000đ.",
                        6_800_000, "storage/reference-damages/ref-16-vf3.jpg"),
                new ReferenceDamage("REF_17", "Thân xe (trầy xước)", 1, "Mức 1: Xước nhẹ",
                        "Case thực tế MoMo - Kia Cerato 2022 (Liberty): bị trầy xước khi đỗ xe trong trung tâm thương mại do va quẹt với xe khác. Phí bảo hiểm 5.800.000đ/năm, bồi thường 6.800.000đ.",
                        6_800_000, "storage/reference-damages/ref-17-cerato-tray-xuoc.jpg"),
                new ReferenceDamage("REF_18", "Đầu xe (hư hỏng nghiêm trọng)", 3, "Mức 3: Vỡ/Biến dạng nặng",
                        "Case thực tế MoMo - Ford Everest 2022 (Bảo Long): va chạm mạnh trên cao tốc với xe phía trước, hư hỏng phần đầu xe nghiêm trọng. Phí bảo hiểm 7.079.000đ/năm, bồi thường 75.000.000đ.",
                        75_000_000, "storage/reference-damages/ref-18-everest-dau-xe.jpg"),
                new ReferenceDamage("REF_05b", "Động cơ (thủy kích)", 3, "Mức 3: Vỡ/Biến dạng nặng",
                        "Case thực tế MoMo - Kia Seltos 2024 (PVI): xe bị ngập nước khi qua đoạn đường ngập sau mưa lớn, hư hỏng động cơ. Phí bảo hiểm 7.110.000đ/năm, bồi thường 42.000.000đ. (Ảnh 2/3 của case này - cùng mức giá bồi thường.)",
                        42_000_000, "storage/reference-damages/ref-05b-seltos-thuy-kich.jpg"),
                new ReferenceDamage("REF_05c", "Động cơ (thủy kích)", 3, "Mức 3: Vỡ/Biến dạng nặng",
                        "Case thực tế MoMo - Kia Seltos 2024 (PVI): xe bị ngập nước khi qua đoạn đường ngập sau mưa lớn, hư hỏng động cơ. Phí bảo hiểm 7.110.000đ/năm, bồi thường 42.000.000đ. (Ảnh 3/3 của case này - cùng mức giá bồi thường.)",
                        42_000_000, "storage/reference-damages/ref-05c-seltos-thuy-kich.jpg"),
                new ReferenceDamage("REF_06b", "Đuôi xe (cản sau)", 3, "Mức 3: Vỡ/Biến dạng nặng",
                        "Case thực tế MoMo - Kia Carnival 2024 (PVI): va chạm khi lùi xe trong bãi đỗ đông, hư hỏng phần đuôi xe. Phí bảo hiểm 11.300.000đ/năm, bồi thường 32.000.000đ. (Ảnh 2/3 của case này - cùng mức giá bồi thường.)",
                        32_000_000, "storage/reference-damages/ref-06b-carnival-duoi-xe.jpg"),
                new ReferenceDamage("REF_06c", "Đuôi xe (cản sau)", 3, "Mức 3: Vỡ/Biến dạng nặng",
                        "Case thực tế MoMo - Kia Carnival 2024 (PVI): va chạm khi lùi xe trong bãi đỗ đông, hư hỏng phần đuôi xe. Phí bảo hiểm 11.300.000đ/năm, bồi thường 32.000.000đ. (Ảnh 3/3 của case này - cùng mức giá bồi thường.)",
                        32_000_000, "storage/reference-damages/ref-06c-carnival-duoi-xe.jpg"),
                new ReferenceDamage("REF_07b", "Thân xe (trầy xước, móp nhẹ)", 2, "Mức 2: Móp vừa",
                        "Case thực tế MoMo - Honda HR-V 2024 (PVI): va chạm nhẹ với xe máy trong hẻm nhỏ, trầy xước và móp nhẹ. Phí bảo hiểm 7.100.000đ/năm, bồi thường 14.000.000đ. (Ảnh 2/3 của case này - cùng mức giá bồi thường.)",
                        14_000_000, "storage/reference-damages/ref-07b-hrv-tray-xuoc.jpg"),
                new ReferenceDamage("REF_07c", "Thân xe (trầy xước, móp nhẹ)", 2, "Mức 2: Móp vừa",
                        "Case thực tế MoMo - Honda HR-V 2024 (PVI): va chạm nhẹ với xe máy trong hẻm nhỏ, trầy xước và móp nhẹ. Phí bảo hiểm 7.100.000đ/năm, bồi thường 14.000.000đ. (Ảnh 3/3 của case này - cùng mức giá bồi thường.)",
                        14_000_000, "storage/reference-damages/ref-07c-hrv-tray-xuoc.jpg"),
                new ReferenceDamage("REF_08b", "Đầu xe + Đèn pha phải", 3, "Mức 3: Vỡ/Biến dạng nặng",
                        "Case thực tế MoMo - Mitsubishi Xpander 2024 (PJICO): va chạm với xe máy trong hẻm nhỏ buổi tối, móp đầu xe và vỡ đèn pha bên phải. Phí bảo hiểm 5.006.000đ/năm, bồi thường 13.200.000đ. (Ảnh 2/3 của case này - cùng mức giá bồi thường.)",
                        13_200_000, "storage/reference-damages/ref-08b-xpander-den-pha.jpg"),
                new ReferenceDamage("REF_08c", "Đầu xe + Đèn pha phải", 3, "Mức 3: Vỡ/Biến dạng nặng",
                        "Case thực tế MoMo - Mitsubishi Xpander 2024 (PJICO): va chạm với xe máy trong hẻm nhỏ buổi tối, móp đầu xe và vỡ đèn pha bên phải. Phí bảo hiểm 5.006.000đ/năm, bồi thường 13.200.000đ. (Ảnh 3/3 của case này - cùng mức giá bồi thường.)",
                        13_200_000, "storage/reference-damages/ref-08c-xpander-den-pha.jpg"),
                new ReferenceDamage("REF_09b", "Toàn thân + Gương chiếu hậu", 2, "Mức 2: Móp vừa",
                        "Case thực tế MoMo - Toyota Fortuner 2022 (PJICO): xe bị phá hoại khi đỗ qua đêm, trầy xước toàn bộ thân xe và vỡ gương chiếu hậu. Phí bảo hiểm 7.790.000đ/năm, bồi thường 22.500.000đ. (Ảnh 2/3 của case này - cùng mức giá bồi thường.)",
                        22_500_000, "storage/reference-damages/ref-09b-fortuner-toan-than.jpg"),
                new ReferenceDamage("REF_09c", "Toàn thân + Gương chiếu hậu", 2, "Mức 2: Móp vừa",
                        "Case thực tế MoMo - Toyota Fortuner 2022 (PJICO): xe bị phá hoại khi đỗ qua đêm, trầy xước toàn bộ thân xe và vỡ gương chiếu hậu. Phí bảo hiểm 7.790.000đ/năm, bồi thường 22.500.000đ. (Ảnh 3/3 của case này - cùng mức giá bồi thường.)",
                        22_500_000, "storage/reference-damages/ref-09c-fortuner-toan-than.jpg"),
                new ReferenceDamage("REF_10b", "Cua xe", 2, "Mức 2: Móp vừa",
                        "Case thực tế MoMo - Toyota Camry 2023 (PJICO): va chạm với xe khác khi chuyển làn trên đường lớn, hư hỏng cửa xe. Phí bảo hiểm 18.200.000đ/năm, bồi thường 25.000.000đ. (Ảnh 2/3 của case này - cùng mức giá bồi thường.)",
                        25_000_000, "storage/reference-damages/ref-10b-camry-cua-xe.jpg"),
                new ReferenceDamage("REF_10c", "Cua xe", 2, "Mức 2: Móp vừa",
                        "Case thực tế MoMo - Toyota Camry 2023 (PJICO): va chạm với xe khác khi chuyển làn trên đường lớn, hư hỏng cửa xe. Phí bảo hiểm 18.200.000đ/năm, bồi thường 25.000.000đ. (Ảnh 3/3 của case này - cùng mức giá bồi thường.)",
                        25_000_000, "storage/reference-damages/ref-10c-camry-cua-xe.jpg"),
                new ReferenceDamage("REF_11b", "Cản sau + Đèn hậu", 2, "Mức 2: Móp vừa",
                        "Case thực tế MoMo - Honda CR-V 2023 (Bảo Việt): va chạm khi lùi xe trong bãi đỗ, hư hỏng cản sau và đèn hậu. Phí bảo hiểm 11.400.000đ/năm, bồi thường 28.000.000đ. (Ảnh 2/3 của case này - cùng mức giá bồi thường.)",
                        28_000_000, "storage/reference-damages/ref-11b-crv-can-sau.jpg"),
                new ReferenceDamage("REF_11c", "Cản sau + Đèn hậu", 2, "Mức 2: Móp vừa",
                        "Case thực tế MoMo - Honda CR-V 2023 (Bảo Việt): va chạm khi lùi xe trong bãi đỗ, hư hỏng cản sau và đèn hậu. Phí bảo hiểm 11.400.000đ/năm, bồi thường 28.000.000đ. (Ảnh 3/3 của case này - cùng mức giá bồi thường.)",
                        28_000_000, "storage/reference-damages/ref-11c-crv-can-sau.jpg"),
                new ReferenceDamage("REF_12b", "Đầu xe (trầy xước, móp)", 2, "Mức 2: Móp vừa",
                        "Case thực tế MoMo - VinFast VF5 2024 (Bảo Việt): va chạm với trụ bê tông trong bãi đỗ, trầy xước và móp đầu xe. Phí bảo hiểm 6.050.000đ/năm, bồi thường 8.500.000đ. (Ảnh 2/3 của case này - cùng mức giá bồi thường.)",
                        8_500_000, "storage/reference-damages/ref-12b-vf5-dau-xe.jpg"),
                new ReferenceDamage("REF_12c", "Đầu xe (trầy xước, móp)", 2, "Mức 2: Móp vừa",
                        "Case thực tế MoMo - VinFast VF5 2024 (Bảo Việt): va chạm với trụ bê tông trong bãi đỗ, trầy xước và móp đầu xe. Phí bảo hiểm 6.050.000đ/năm, bồi thường 8.500.000đ. (Ảnh 3/3 của case này - cùng mức giá bồi thường.)",
                        8_500_000, "storage/reference-damages/ref-12c-vf5-dau-xe.jpg"),
                new ReferenceDamage("REF_13b", "Đầu xe + Thân xe", 3, "Mức 3: Vỡ/Biến dạng nặng",
                        "Case thực tế MoMo - VinFast VF9 2024 (Bảo Việt): va chạm mạnh với xe khác trên quốc lộ, hư hỏng phần đầu và thân xe. Phí bảo hiểm 18.600.000đ/năm, bồi thường 58.000.000đ. (Ảnh 2/3 của case này - cùng mức giá bồi thường.)",
                        58_000_000, "storage/reference-damages/ref-13b-vf9-dau-than.jpg"),
                new ReferenceDamage("REF_13c", "Đầu xe + Thân xe", 3, "Mức 3: Vỡ/Biến dạng nặng",
                        "Case thực tế MoMo - VinFast VF9 2024 (Bảo Việt): va chạm mạnh với xe khác trên quốc lộ, hư hỏng phần đầu và thân xe. Phí bảo hiểm 18.600.000đ/năm, bồi thường 58.000.000đ. (Ảnh 3/3 của case này - cùng mức giá bồi thường.)",
                        58_000_000, "storage/reference-damages/ref-13c-vf9-dau-than.jpg"),
                new ReferenceDamage("REF_14b", "Nắp capo", 2, "Mức 2: Móp vừa",
                        "Case thực tế MoMo - Toyota Innova 2021 (GIC): bị rơi vật từ trên cao khi đỗ xe, móp capo và trầy xước nhẹ. Phí bảo hiểm 7.400.000đ/năm, bồi thường 12.000.000đ. (Ảnh 2/3 của case này - cùng mức giá bồi thường.)",
                        12_000_000, "storage/reference-damages/ref-14b-innova-capo.jpg"),
                new ReferenceDamage("REF_14c", "Nắp capo", 2, "Mức 2: Móp vừa",
                        "Case thực tế MoMo - Toyota Innova 2021 (GIC): bị rơi vật từ trên cao khi đỗ xe, móp capo và trầy xước nhẹ. Phí bảo hiểm 7.400.000đ/năm, bồi thường 12.000.000đ. (Ảnh 3/3 của case này - cùng mức giá bồi thường.)",
                        12_000_000, "storage/reference-damages/ref-14c-innova-capo.jpg"),
                new ReferenceDamage("REF_15b", "Thân xe (trầy xước nhẹ)", 1, "Mức 1: Xước nhẹ",
                        "Case thực tế MoMo - Mazda 2 2022 (DBV): bị trầy xước nhẹ khi đỗ xe trên vỉa hè, không xác định được bên gây ra. Phí bảo hiểm 5.094.000đ/năm, bồi thường 4.500.000đ. (Ảnh 2/3 của case này - cùng mức giá bồi thường.)",
                        4_500_000, "storage/reference-damages/ref-15b-mazda2-tray-nhe.jpg"),
                new ReferenceDamage("REF_15c", "Thân xe (trầy xước nhẹ)", 1, "Mức 1: Xước nhẹ",
                        "Case thực tế MoMo - Mazda 2 2022 (DBV): bị trầy xước nhẹ khi đỗ xe trên vỉa hè, không xác định được bên gây ra. Phí bảo hiểm 5.094.000đ/năm, bồi thường 4.500.000đ. (Ảnh 3/3 của case này - cùng mức giá bồi thường.)",
                        4_500_000, "storage/reference-damages/ref-15c-mazda2-tray-nhe.jpg"),
                new ReferenceDamage("REF_16b", "Cản trước (trầy xước, bong sơn)", 2, "Mức 2: Móp vừa",
                        "Case thực tế MoMo - VinFast VF3 2024 (DBV): cản trước trầy xước, bong sơn (ảnh thực tế; MoMo không ghi mô tả chi tiết). Phí bảo hiểm 3.300.000đ/năm, bồi thường 6.800.000đ. (Ảnh 2/3 của case này - cùng mức giá bồi thường.)",
                        6_800_000, "storage/reference-damages/ref-16b-vf3.jpg"),
                new ReferenceDamage("REF_16c", "Cản trước (trầy xước, bong sơn)", 2, "Mức 2: Móp vừa",
                        "Case thực tế MoMo - VinFast VF3 2024 (DBV): cản trước trầy xước, bong sơn (ảnh thực tế; MoMo không ghi mô tả chi tiết). Phí bảo hiểm 3.300.000đ/năm, bồi thường 6.800.000đ. (Ảnh 3/3 của case này - cùng mức giá bồi thường.)",
                        6_800_000, "storage/reference-damages/ref-16c-vf3.jpg"),
                new ReferenceDamage("REF_17b", "Thân xe (trầy xước)", 1, "Mức 1: Xước nhẹ",
                        "Case thực tế MoMo - Kia Cerato 2022 (Liberty): bị trầy xước khi đỗ xe trong trung tâm thương mại do va quẹt với xe khác. Phí bảo hiểm 5.800.000đ/năm, bồi thường 6.800.000đ. (Ảnh 2/3 của case này - cùng mức giá bồi thường.)",
                        6_800_000, "storage/reference-damages/ref-17b-cerato-tray-xuoc.jpg"),
                new ReferenceDamage("REF_17c", "Thân xe (trầy xước)", 1, "Mức 1: Xước nhẹ",
                        "Case thực tế MoMo - Kia Cerato 2022 (Liberty): bị trầy xước khi đỗ xe trong trung tâm thương mại do va quẹt với xe khác. Phí bảo hiểm 5.800.000đ/năm, bồi thường 6.800.000đ. (Ảnh 3/3 của case này - cùng mức giá bồi thường.)",
                        6_800_000, "storage/reference-damages/ref-17c-cerato-tray-xuoc.jpg"),
                new ReferenceDamage("REF_18b", "Đầu xe (hư hỏng nghiêm trọng)", 3, "Mức 3: Vỡ/Biến dạng nặng",
                        "Case thực tế MoMo - Ford Everest 2022 (Bảo Long): va chạm mạnh trên cao tốc với xe phía trước, hư hỏng phần đầu xe nghiêm trọng. Phí bảo hiểm 7.079.000đ/năm, bồi thường 75.000.000đ. (Ảnh 2/3 của case này - cùng mức giá bồi thường.)",
                        75_000_000, "storage/reference-damages/ref-18b-everest-dau-xe.jpg"),
                new ReferenceDamage("REF_18c", "Đầu xe (hư hỏng nghiêm trọng)", 3, "Mức 3: Vỡ/Biến dạng nặng",
                        "Case thực tế MoMo - Ford Everest 2022 (Bảo Long): va chạm mạnh trên cao tốc với xe phía trước, hư hỏng phần đầu xe nghiêm trọng. Phí bảo hiểm 7.079.000đ/năm, bồi thường 75.000.000đ. (Ảnh 3/3 của case này - cùng mức giá bồi thường.)",
                        75_000_000, "storage/reference-damages/ref-18c-everest-dau-xe.jpg"),
                new ReferenceDamage("REF_19", "Thân xe (trầy xước nhiều vết)", 1, "Mức 1: Xước nhẹ",
                        "Case thực tế Tuổi Trẻ (10/2026) - Mercedes-Benz GLC200: bị cào xước 7 vị trí trên thân xe (cửa, hông sau). Chi phí sửa chữa thực tế 8.200.000đ (chủ xe trả 2.000.000đ, bảo hiểm MIC + OPES trả 6.200.000đ); giám định thiệt hại 6.862.000đ. Báo giá hãng 127,6 triệu và yêu cầu bồi thường 470 triệu đã bị tòa bác - KHÔNG dùng 2 số đó. 7 vị trí ~ 1,17 triệu/vị trí, khớp khoảng sơn dặm 1 chi tiết trong bảng giá tham chiếu.",
                        8_200_000, "storage/reference-damages/ref-19-merc-glc200-tray-xuoc.jpg")
        );

        int seeded = 0;
        for (ReferenceDamage ref : seeds) {
            Path imagePath = Paths.get(ref.getImagePath());
            if (Files.exists(imagePath)) {
                repository.save(ref);
                seeded++;
            } else {
                log.warn("Bo qua seed {}: khong tim thay file anh {}", ref.getReferenceId(), ref.getImagePath());
            }
        }
        log.info("Da seed {} anh mau vao Kho anh du tru.", seeded);
    }
}
