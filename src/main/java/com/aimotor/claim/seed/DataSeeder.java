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
 * Seed Kho anh mau du tru khi MongoDB con trong.
 * Anh mau nam trong ./storage/reference-damages/ (da kem san 4 anh test).
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
                new ReferenceDamage("REF_01", "Can truoc", 1, "Muc 1: Xuoc nhe",
                        "Vet xuoc dai nhe tren lop son can truoc, khong mop, khong vo. Chi can son lai cuc bo.",
                        1_500_000, "storage/reference-damages/ref-01-bumper-scratch.jpg"),
                new ReferenceDamage("REF_02", "Cua sau ben phu", 2, "Muc 2: Mop vua",
                        "Cua sau ben phu mop vao trong mot vung lon, son con nguyen ven. Can nan keo + son lai.",
                        4_500_000, "storage/reference-damages/ref-02-door-dent.jpg"),
                new ReferenceDamage("REF_03", "Den pha trai", 3, "Muc 3: Vo/Bien dang nang",
                        "Den pha trai vo nat hoan toan, kinh vo vun, vo den rach. Bat buoc thay moi cum den.",
                        3_800_000, "storage/reference-damages/ref-03-headlight-broken.jpg"),
                new ReferenceDamage("REF_04", "Nap capo", 3, "Muc 3: Vo/Bien dang nang",
                        "Nap capo bi dap nat, bien dang nang sau va cham manh phia truoc. Thay moi nap capo + kiem tra khung gam.",
                        12_000_000, "storage/reference-damages/ref-04-hood-crushed.jpg"),
                // --- 14 case thuc te MoMo (nguon: momo.vn/bao-hiem-o-to/than-vo) ---
                // Luu y: "phi boi thuong" la TONG tien ca vu (toan bo thiet hai cua vu do),
                // gan cho vung hu hai chinh, khong phai bang gia theo tung mon.
                new ReferenceDamage("REF_05", "Dong co (thuy kich)", 3, "Muc 3: Vo/Bien dang nang",
                        "Case thuc te MoMo - Kia Seltos 2024 (PVI): xe bi ngap nuoc khi qua doan duong ngap sau mua lon, hu hong dong co. Phi bao hiem 7.110.000d/nam, boi thuong 42.000.000d.",
                        42_000_000, "storage/reference-damages/ref-05-seltos-thuy-kich.jpg"),
                new ReferenceDamage("REF_06", "Duoi xe (can sau)", 3, "Muc 3: Vo/Bien dang nang",
                        "Case thuc te MoMo - Kia Carnival 2024 (PVI): va cham khi lui xe trong bai do dong, hu hong phan duoi xe. Phi bao hiem 11.300.000d/nam, boi thuong 32.000.000d.",
                        32_000_000, "storage/reference-damages/ref-06-carnival-duoi-xe.jpg"),
                new ReferenceDamage("REF_07", "Than xe (tray xuoc, mop nhe)", 2, "Muc 2: Mop vua",
                        "Case thuc te MoMo - Honda HR-V 2024 (PVI): va cham nhe voi xe may trong hem nho, tray xuoc va mop nhe. Phi bao hiem 7.100.000d/nam, boi thuong 14.000.000d.",
                        14_000_000, "storage/reference-damages/ref-07-hrv-tray-xuoc.jpg"),
                new ReferenceDamage("REF_08", "Dau xe + Den pha phai", 3, "Muc 3: Vo/Bien dang nang",
                        "Case thuc te MoMo - Mitsubishi Xpander 2024 (PJICO): va cham voi xe may trong hem nho buoi toi, mop dau xe va vo den pha ben phai. Phi bao hiem 5.006.000d/nam, boi thuong 13.200.000d.",
                        13_200_000, "storage/reference-damages/ref-08-xpander-den-pha.jpg"),
                new ReferenceDamage("REF_09", "Toan than + Guong chieu hau", 2, "Muc 2: Mop vua",
                        "Case thuc te MoMo - Toyota Fortuner 2022 (PJICO): xe bi pha hoai khi do qua dem, tray xuoc toan bo than xe va vo guong chieu hau. Phi bao hiem 7.790.000d/nam, boi thuong 22.500.000d.",
                        22_500_000, "storage/reference-damages/ref-09-fortuner-toan-than.jpg"),
                new ReferenceDamage("REF_10", "Cua xe", 2, "Muc 2: Mop vua",
                        "Case thuc te MoMo - Toyota Camry 2023 (PJICO): va cham voi xe khac khi chuyen lan tren duong lon, hu hong cua xe. Phi bao hiem 18.200.000d/nam, boi thuong 25.000.000d.",
                        25_000_000, "storage/reference-damages/ref-10-camry-cua-xe.jpg"),
                new ReferenceDamage("REF_11", "Can sau + Den hau", 2, "Muc 2: Mop vua",
                        "Case thuc te MoMo - Honda CR-V 2023 (Bao Viet): va cham khi lui xe trong bai do, hu hong can sau va den hau. Phi bao hiem 11.400.000d/nam, boi thuong 28.000.000d.",
                        28_000_000, "storage/reference-damages/ref-11-crv-can-sau.jpg"),
                new ReferenceDamage("REF_12", "Dau xe (tray xuoc, mop)", 2, "Muc 2: Mop vua",
                        "Case thuc te MoMo - VinFast VF5 2024 (Bao Viet): va cham voi tru be tong trong bai do, tray xuoc va mop dau xe. Phi bao hiem 6.050.000d/nam, boi thuong 8.500.000d.",
                        8_500_000, "storage/reference-damages/ref-12-vf5-dau-xe.jpg"),
                new ReferenceDamage("REF_13", "Dau xe + Than xe", 3, "Muc 3: Vo/Bien dang nang",
                        "Case thuc te MoMo - VinFast VF9 2024 (Bao Viet): va cham manh voi xe khac tren quoc lo, hu hong phan dau va than xe. Phi bao hiem 18.600.000d/nam, boi thuong 58.000.000d.",
                        58_000_000, "storage/reference-damages/ref-13-vf9-dau-than.jpg"),
                new ReferenceDamage("REF_14", "Nap capo", 2, "Muc 2: Mop vua",
                        "Case thuc te MoMo - Toyota Innova 2021 (GIC): bi roi vat tu tren cao khi do xe, mop capo va tray xuoc nhe. Phi bao hiem 7.400.000d/nam, boi thuong 12.000.000d.",
                        12_000_000, "storage/reference-damages/ref-14-innova-capo.jpg"),
                new ReferenceDamage("REF_15", "Than xe (tray xuoc nhe)", 1, "Muc 1: Xuoc nhe",
                        "Case thuc te MoMo - Mazda 2 2022 (DBV): bi tray xuoc nhe khi do xe tren via he, khong xac dinh duoc ben gay ra. Phi bao hiem 5.094.000d/nam, boi thuong 4.500.000d.",
                        4_500_000, "storage/reference-damages/ref-15-mazda2-tray-nhe.jpg"),
                new ReferenceDamage("REF_16", "Can truoc (tray xuoc, bong son)", 2, "Muc 2: Mop vua",
                        "Case thuc te MoMo - VinFast VF3 2024 (DBV): can truoc tray xuoc, bong son (anh thuc te; MoMo khong ghi mo ta chi tiet). Phi bao hiem 3.300.000d/nam, boi thuong 6.800.000d.",
                        6_800_000, "storage/reference-damages/ref-16-vf3.jpg"),
                new ReferenceDamage("REF_17", "Than xe (tray xuoc)", 1, "Muc 1: Xuoc nhe",
                        "Case thuc te MoMo - Kia Cerato 2022 (Liberty): bi tray xuoc khi do xe trong trung tam thuong mai do va quet voi xe khac. Phi bao hiem 5.800.000d/nam, boi thuong 6.800.000d.",
                        6_800_000, "storage/reference-damages/ref-17-cerato-tray-xuoc.jpg"),
                new ReferenceDamage("REF_18", "Dau xe (hu hong nghiem trong)", 3, "Muc 3: Vo/Bien dang nang",
                        "Case thuc te MoMo - Ford Everest 2022 (Bao Long): va cham manh tren cao toc voi xe phia truoc, hu hong phan dau xe nghiem trong. Phi bao hiem 7.079.000d/nam, boi thuong 75.000.000d.",
                        75_000_000, "storage/reference-damages/ref-18-everest-dau-xe.jpg"),
                new ReferenceDamage("REF_05b", "Dong co (thuy kich)", 3, "Muc 3: Vo/Bien dang nang",
                        "Case thuc te MoMo - Kia Seltos 2024 (PVI): xe bi ngap nuoc khi qua doan duong ngap sau mua lon, hu hong dong co. Phi bao hiem 7.110.000d/nam, boi thuong 42.000.000d. (Anh 2/3 cua case nay - cung muc gia boi thuong.)",
                        42_000_000, "storage/reference-damages/ref-05b-seltos-thuy-kich.jpg"),
                new ReferenceDamage("REF_05c", "Dong co (thuy kich)", 3, "Muc 3: Vo/Bien dang nang",
                        "Case thuc te MoMo - Kia Seltos 2024 (PVI): xe bi ngap nuoc khi qua doan duong ngap sau mua lon, hu hong dong co. Phi bao hiem 7.110.000d/nam, boi thuong 42.000.000d. (Anh 3/3 cua case nay - cung muc gia boi thuong.)",
                        42_000_000, "storage/reference-damages/ref-05c-seltos-thuy-kich.jpg"),
                new ReferenceDamage("REF_06b", "Duoi xe (can sau)", 3, "Muc 3: Vo/Bien dang nang",
                        "Case thuc te MoMo - Kia Carnival 2024 (PVI): va cham khi lui xe trong bai do dong, hu hong phan duoi xe. Phi bao hiem 11.300.000d/nam, boi thuong 32.000.000d. (Anh 2/3 cua case nay - cung muc gia boi thuong.)",
                        32_000_000, "storage/reference-damages/ref-06b-carnival-duoi-xe.jpg"),
                new ReferenceDamage("REF_06c", "Duoi xe (can sau)", 3, "Muc 3: Vo/Bien dang nang",
                        "Case thuc te MoMo - Kia Carnival 2024 (PVI): va cham khi lui xe trong bai do dong, hu hong phan duoi xe. Phi bao hiem 11.300.000d/nam, boi thuong 32.000.000d. (Anh 3/3 cua case nay - cung muc gia boi thuong.)",
                        32_000_000, "storage/reference-damages/ref-06c-carnival-duoi-xe.jpg"),
                new ReferenceDamage("REF_07b", "Than xe (tray xuoc, mop nhe)", 2, "Muc 2: Mop vua",
                        "Case thuc te MoMo - Honda HR-V 2024 (PVI): va cham nhe voi xe may trong hem nho, tray xuoc va mop nhe. Phi bao hiem 7.100.000d/nam, boi thuong 14.000.000d. (Anh 2/3 cua case nay - cung muc gia boi thuong.)",
                        14_000_000, "storage/reference-damages/ref-07b-hrv-tray-xuoc.jpg"),
                new ReferenceDamage("REF_07c", "Than xe (tray xuoc, mop nhe)", 2, "Muc 2: Mop vua",
                        "Case thuc te MoMo - Honda HR-V 2024 (PVI): va cham nhe voi xe may trong hem nho, tray xuoc va mop nhe. Phi bao hiem 7.100.000d/nam, boi thuong 14.000.000d. (Anh 3/3 cua case nay - cung muc gia boi thuong.)",
                        14_000_000, "storage/reference-damages/ref-07c-hrv-tray-xuoc.jpg"),
                new ReferenceDamage("REF_08b", "Dau xe + Den pha phai", 3, "Muc 3: Vo/Bien dang nang",
                        "Case thuc te MoMo - Mitsubishi Xpander 2024 (PJICO): va cham voi xe may trong hem nho buoi toi, mop dau xe va vo den pha ben phai. Phi bao hiem 5.006.000d/nam, boi thuong 13.200.000d. (Anh 2/3 cua case nay - cung muc gia boi thuong.)",
                        13_200_000, "storage/reference-damages/ref-08b-xpander-den-pha.jpg"),
                new ReferenceDamage("REF_08c", "Dau xe + Den pha phai", 3, "Muc 3: Vo/Bien dang nang",
                        "Case thuc te MoMo - Mitsubishi Xpander 2024 (PJICO): va cham voi xe may trong hem nho buoi toi, mop dau xe va vo den pha ben phai. Phi bao hiem 5.006.000d/nam, boi thuong 13.200.000d. (Anh 3/3 cua case nay - cung muc gia boi thuong.)",
                        13_200_000, "storage/reference-damages/ref-08c-xpander-den-pha.jpg"),
                new ReferenceDamage("REF_09b", "Toan than + Guong chieu hau", 2, "Muc 2: Mop vua",
                        "Case thuc te MoMo - Toyota Fortuner 2022 (PJICO): xe bi pha hoai khi do qua dem, tray xuoc toan bo than xe va vo guong chieu hau. Phi bao hiem 7.790.000d/nam, boi thuong 22.500.000d. (Anh 2/3 cua case nay - cung muc gia boi thuong.)",
                        22_500_000, "storage/reference-damages/ref-09b-fortuner-toan-than.jpg"),
                new ReferenceDamage("REF_09c", "Toan than + Guong chieu hau", 2, "Muc 2: Mop vua",
                        "Case thuc te MoMo - Toyota Fortuner 2022 (PJICO): xe bi pha hoai khi do qua dem, tray xuoc toan bo than xe va vo guong chieu hau. Phi bao hiem 7.790.000d/nam, boi thuong 22.500.000d. (Anh 3/3 cua case nay - cung muc gia boi thuong.)",
                        22_500_000, "storage/reference-damages/ref-09c-fortuner-toan-than.jpg"),
                new ReferenceDamage("REF_10b", "Cua xe", 2, "Muc 2: Mop vua",
                        "Case thuc te MoMo - Toyota Camry 2023 (PJICO): va cham voi xe khac khi chuyen lan tren duong lon, hu hong cua xe. Phi bao hiem 18.200.000d/nam, boi thuong 25.000.000d. (Anh 2/3 cua case nay - cung muc gia boi thuong.)",
                        25_000_000, "storage/reference-damages/ref-10b-camry-cua-xe.jpg"),
                new ReferenceDamage("REF_10c", "Cua xe", 2, "Muc 2: Mop vua",
                        "Case thuc te MoMo - Toyota Camry 2023 (PJICO): va cham voi xe khac khi chuyen lan tren duong lon, hu hong cua xe. Phi bao hiem 18.200.000d/nam, boi thuong 25.000.000d. (Anh 3/3 cua case nay - cung muc gia boi thuong.)",
                        25_000_000, "storage/reference-damages/ref-10c-camry-cua-xe.jpg"),
                new ReferenceDamage("REF_11b", "Can sau + Den hau", 2, "Muc 2: Mop vua",
                        "Case thuc te MoMo - Honda CR-V 2023 (Bao Viet): va cham khi lui xe trong bai do, hu hong can sau va den hau. Phi bao hiem 11.400.000d/nam, boi thuong 28.000.000d. (Anh 2/3 cua case nay - cung muc gia boi thuong.)",
                        28_000_000, "storage/reference-damages/ref-11b-crv-can-sau.jpg"),
                new ReferenceDamage("REF_11c", "Can sau + Den hau", 2, "Muc 2: Mop vua",
                        "Case thuc te MoMo - Honda CR-V 2023 (Bao Viet): va cham khi lui xe trong bai do, hu hong can sau va den hau. Phi bao hiem 11.400.000d/nam, boi thuong 28.000.000d. (Anh 3/3 cua case nay - cung muc gia boi thuong.)",
                        28_000_000, "storage/reference-damages/ref-11c-crv-can-sau.jpg"),
                new ReferenceDamage("REF_12b", "Dau xe (tray xuoc, mop)", 2, "Muc 2: Mop vua",
                        "Case thuc te MoMo - VinFast VF5 2024 (Bao Viet): va cham voi tru be tong trong bai do, tray xuoc va mop dau xe. Phi bao hiem 6.050.000d/nam, boi thuong 8.500.000d. (Anh 2/3 cua case nay - cung muc gia boi thuong.)",
                        8_500_000, "storage/reference-damages/ref-12b-vf5-dau-xe.jpg"),
                new ReferenceDamage("REF_12c", "Dau xe (tray xuoc, mop)", 2, "Muc 2: Mop vua",
                        "Case thuc te MoMo - VinFast VF5 2024 (Bao Viet): va cham voi tru be tong trong bai do, tray xuoc va mop dau xe. Phi bao hiem 6.050.000d/nam, boi thuong 8.500.000d. (Anh 3/3 cua case nay - cung muc gia boi thuong.)",
                        8_500_000, "storage/reference-damages/ref-12c-vf5-dau-xe.jpg"),
                new ReferenceDamage("REF_13b", "Dau xe + Than xe", 3, "Muc 3: Vo/Bien dang nang",
                        "Case thuc te MoMo - VinFast VF9 2024 (Bao Viet): va cham manh voi xe khac tren quoc lo, hu hong phan dau va than xe. Phi bao hiem 18.600.000d/nam, boi thuong 58.000.000d. (Anh 2/3 cua case nay - cung muc gia boi thuong.)",
                        58_000_000, "storage/reference-damages/ref-13b-vf9-dau-than.jpg"),
                new ReferenceDamage("REF_13c", "Dau xe + Than xe", 3, "Muc 3: Vo/Bien dang nang",
                        "Case thuc te MoMo - VinFast VF9 2024 (Bao Viet): va cham manh voi xe khac tren quoc lo, hu hong phan dau va than xe. Phi bao hiem 18.600.000d/nam, boi thuong 58.000.000d. (Anh 3/3 cua case nay - cung muc gia boi thuong.)",
                        58_000_000, "storage/reference-damages/ref-13c-vf9-dau-than.jpg"),
                new ReferenceDamage("REF_14b", "Nap capo", 2, "Muc 2: Mop vua",
                        "Case thuc te MoMo - Toyota Innova 2021 (GIC): bi roi vat tu tren cao khi do xe, mop capo va tray xuoc nhe. Phi bao hiem 7.400.000d/nam, boi thuong 12.000.000d. (Anh 2/3 cua case nay - cung muc gia boi thuong.)",
                        12_000_000, "storage/reference-damages/ref-14b-innova-capo.jpg"),
                new ReferenceDamage("REF_14c", "Nap capo", 2, "Muc 2: Mop vua",
                        "Case thuc te MoMo - Toyota Innova 2021 (GIC): bi roi vat tu tren cao khi do xe, mop capo va tray xuoc nhe. Phi bao hiem 7.400.000d/nam, boi thuong 12.000.000d. (Anh 3/3 cua case nay - cung muc gia boi thuong.)",
                        12_000_000, "storage/reference-damages/ref-14c-innova-capo.jpg"),
                new ReferenceDamage("REF_15b", "Than xe (tray xuoc nhe)", 1, "Muc 1: Xuoc nhe",
                        "Case thuc te MoMo - Mazda 2 2022 (DBV): bi tray xuoc nhe khi do xe tren via he, khong xac dinh duoc ben gay ra. Phi bao hiem 5.094.000d/nam, boi thuong 4.500.000d. (Anh 2/3 cua case nay - cung muc gia boi thuong.)",
                        4_500_000, "storage/reference-damages/ref-15b-mazda2-tray-nhe.jpg"),
                new ReferenceDamage("REF_15c", "Than xe (tray xuoc nhe)", 1, "Muc 1: Xuoc nhe",
                        "Case thuc te MoMo - Mazda 2 2022 (DBV): bi tray xuoc nhe khi do xe tren via he, khong xac dinh duoc ben gay ra. Phi bao hiem 5.094.000d/nam, boi thuong 4.500.000d. (Anh 3/3 cua case nay - cung muc gia boi thuong.)",
                        4_500_000, "storage/reference-damages/ref-15c-mazda2-tray-nhe.jpg"),
                new ReferenceDamage("REF_16b", "Can truoc (tray xuoc, bong son)", 2, "Muc 2: Mop vua",
                        "Case thuc te MoMo - VinFast VF3 2024 (DBV): can truoc tray xuoc, bong son (anh thuc te; MoMo khong ghi mo ta chi tiet). Phi bao hiem 3.300.000d/nam, boi thuong 6.800.000d. (Anh 2/3 cua case nay - cung muc gia boi thuong.)",
                        6_800_000, "storage/reference-damages/ref-16b-vf3.jpg"),
                new ReferenceDamage("REF_16c", "Can truoc (tray xuoc, bong son)", 2, "Muc 2: Mop vua",
                        "Case thuc te MoMo - VinFast VF3 2024 (DBV): can truoc tray xuoc, bong son (anh thuc te; MoMo khong ghi mo ta chi tiet). Phi bao hiem 3.300.000d/nam, boi thuong 6.800.000d. (Anh 3/3 cua case nay - cung muc gia boi thuong.)",
                        6_800_000, "storage/reference-damages/ref-16c-vf3.jpg"),
                new ReferenceDamage("REF_17b", "Than xe (tray xuoc)", 1, "Muc 1: Xuoc nhe",
                        "Case thuc te MoMo - Kia Cerato 2022 (Liberty): bi tray xuoc khi do xe trong trung tam thuong mai do va quet voi xe khac. Phi bao hiem 5.800.000d/nam, boi thuong 6.800.000d. (Anh 2/3 cua case nay - cung muc gia boi thuong.)",
                        6_800_000, "storage/reference-damages/ref-17b-cerato-tray-xuoc.jpg"),
                new ReferenceDamage("REF_17c", "Than xe (tray xuoc)", 1, "Muc 1: Xuoc nhe",
                        "Case thuc te MoMo - Kia Cerato 2022 (Liberty): bi tray xuoc khi do xe trong trung tam thuong mai do va quet voi xe khac. Phi bao hiem 5.800.000d/nam, boi thuong 6.800.000d. (Anh 3/3 cua case nay - cung muc gia boi thuong.)",
                        6_800_000, "storage/reference-damages/ref-17c-cerato-tray-xuoc.jpg"),
                new ReferenceDamage("REF_18b", "Dau xe (hu hong nghiem trong)", 3, "Muc 3: Vo/Bien dang nang",
                        "Case thuc te MoMo - Ford Everest 2022 (Bao Long): va cham manh tren cao toc voi xe phia truoc, hu hong phan dau xe nghiem trong. Phi bao hiem 7.079.000d/nam, boi thuong 75.000.000d. (Anh 2/3 cua case nay - cung muc gia boi thuong.)",
                        75_000_000, "storage/reference-damages/ref-18b-everest-dau-xe.jpg"),
                new ReferenceDamage("REF_18c", "Dau xe (hu hong nghiem trong)", 3, "Muc 3: Vo/Bien dang nang",
                        "Case thuc te MoMo - Ford Everest 2022 (Bao Long): va cham manh tren cao toc voi xe phia truoc, hu hong phan dau xe nghiem trong. Phi bao hiem 7.079.000d/nam, boi thuong 75.000.000d. (Anh 3/3 cua case nay - cung muc gia boi thuong.)",
                        75_000_000, "storage/reference-damages/ref-18c-everest-dau-xe.jpg"),
                new ReferenceDamage("REF_19", "Than xe (tray xuoc nhieu vet)", 1, "Muc 1: Xuoc nhe",
                        "Case thuc te Tuoi Tre (10/2026) - Mercedes-Benz GLC200: bi cao xuoc 7 vi tri tren than xe (cua, hong sau). Chi phi sua chua thuc te 8.200.000d (chu xe tra 2.000.000d, bao hiem MIC + OPES tra 6.200.000d); giam dinh thiet hai 6.862.000d. Bao gia hang 127,6 trieu va yeu cau boi thuong 470 trieu da bi toa bac - KHONG dung 2 so do. 7 vi tri ~ 1,17 trieu/vi tri, khop khoang son dam 1 chi tiet trong bang gia tham chieu.",
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
