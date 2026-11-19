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
                        12_000_000, "storage/reference-damages/ref-04-hood-crushed.jpg")
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
