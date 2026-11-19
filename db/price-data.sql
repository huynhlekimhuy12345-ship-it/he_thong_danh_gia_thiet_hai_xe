-- SQLite luu thong tin gia - AI Motor Claim Assessor
-- Sinh tu: python3 db/build-price-db.py (nguon: price-table.json + 14 case MoMo)
-- Dung: sqlite3 price-data.db < price-data.sql

BEGIN TRANSACTION;
CREATE TABLE case_level_anchors (
    id      INTEGER PRIMARY KEY AUTOINCREMENT,
    label   TEXT NOT NULL,
    min_vnd INTEGER NOT NULL,
    max_vnd INTEGER NOT NULL,
    source  TEXT NOT NULL
);
INSERT INTO "case_level_anchors" VALUES(1,'Va quẹt nhỏ',2000000,3000000,'MoMo');
INSERT INTO "case_level_anchors" VALUES(2,'Va chạm nặng',30000000,80000000,'MoMo');
INSERT INTO "case_level_anchors" VALUES(3,'Thủy kích (động cơ ngập nước)',20000000,60000000,'MoMo');
INSERT INTO "case_level_anchors" VALUES(4,'Sơn toàn xe (phổ thông)',12000000,35000000,'Thành Nam Auto / GTX / GoGara');
CREATE TABLE meta (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);
INSERT INTO "meta" VALUES('version','1.0');
INSERT INTO "meta" VALUES('updated','2026-11-19');
INSERT INTO "meta" VALUES('currency','VND');
INSERT INTO "meta" VALUES('scope','Xe ô tô phổ thông tại Việt Nam. Giá KHÔNG phân biệt hãng/model xe. Chưa gồm VAT. Giá gara tư nhân; hãng chính hãng thường cao hơn 30-50%.');
CREATE TABLE price_items (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    part           TEXT NOT NULL,
    severity       INTEGER NOT NULL CHECK (severity IN (1, 2, 3)),
    severity_label TEXT NOT NULL,
    action         TEXT NOT NULL,
    min_vnd        INTEGER NOT NULL CHECK (min_vnd > 0),
    max_vnd        INTEGER NOT NULL CHECK (max_vnd > min_vnd),
    confidence     TEXT NOT NULL CHECK (confidence IN ('cao', 'trung bình')),
    note           TEXT
);
INSERT INTO "price_items" VALUES(1,'Cản trước / Cản sau',1,'Mức 1: Xước nhẹ','Sơn dặm',500000,1200000,'cao',NULL);
INSERT INTO "price_items" VALUES(2,'Cản trước / Cản sau',2,'Mức 2: Móp vừa','Gò + sơn',1200000,3000000,'cao',NULL);
INSERT INTO "price_items" VALUES(3,'Cản trước / Cản sau',3,'Mức 3: Vỡ/Biến dạng nặng','Thay mới + sơn',2000000,5000000,'cao',NULL);
INSERT INTO "price_items" VALUES(4,'Cánh cửa',1,'Mức 1: Xước nhẹ','Sơn dặm',400000,1000000,'cao',NULL);
INSERT INTO "price_items" VALUES(5,'Cánh cửa',2,'Mức 2: Móp vừa','Gò + sơn',1000000,3000000,'cao',NULL);
INSERT INTO "price_items" VALUES(6,'Cánh cửa',3,'Mức 3: Vỡ/Biến dạng nặng','Thay vỏ cửa + sơn',4000000,10000000,'cao',NULL);
INSERT INTO "price_items" VALUES(7,'Nắp capo',1,'Mức 1: Xước nhẹ','Sơn dặm',800000,1500000,'cao',NULL);
INSERT INTO "price_items" VALUES(8,'Nắp capo',2,'Mức 2: Móp vừa','Gò + sơn',1500000,3500000,'cao',NULL);
INSERT INTO "price_items" VALUES(9,'Nắp capo',3,'Mức 3: Vỡ/Biến dạng nặng','Thay capo + sơn',3700000,8000000,'cao',NULL);
INSERT INTO "price_items" VALUES(10,'Nắp cốp / Đuôi xe',1,'Mức 1: Xước nhẹ','Sơn dặm',500000,1500000,'cao',NULL);
INSERT INTO "price_items" VALUES(11,'Nắp cốp / Đuôi xe',2,'Mức 2: Móp vừa','Gò + sơn',1200000,3000000,'cao',NULL);
INSERT INTO "price_items" VALUES(12,'Nắp cốp / Đuôi xe',3,'Mức 3: Vỡ/Biến dạng nặng','Thay mới + sơn',3000000,7000000,'trung bình',NULL);
INSERT INTO "price_items" VALUES(13,'Tai xe / Vè (dè)',1,'Mức 1: Xước nhẹ','Sơn dặm',400000,1000000,'cao',NULL);
INSERT INTO "price_items" VALUES(14,'Tai xe / Vè (dè)',2,'Mức 2: Móp vừa','Gò + sơn',800000,2500000,'cao',NULL);
INSERT INTO "price_items" VALUES(15,'Tai xe / Vè (dè)',3,'Mức 3: Vỡ/Biến dạng nặng','Thay mới + sơn',2500000,6000000,'trung bình',NULL);
INSERT INTO "price_items" VALUES(16,'Đèn pha',1,'Mức 1: Xước nhẹ','Đánh bóng / phục hồi thấu kính',500000,1500000,'trung bình','Nứt thấu kính thường phải thay cả cụm (VietnamNet)');
INSERT INTO "price_items" VALUES(17,'Đèn pha',2,'Mức 2: Móp vừa','Thay cụm đèn',2000000,6000000,'cao',NULL);
INSERT INTO "price_items" VALUES(18,'Đèn pha',3,'Mức 3: Vỡ/Biến dạng nặng','Thay cụm + sửa khung đèn',6000000,12000000,'trung bình','Đèn LED/laser/adaptive vượt bảng -> chuyển thẩm định viên');
INSERT INTO "price_items" VALUES(19,'Đèn hậu',1,'Mức 1: Xước nhẹ','Sơn / phục hồi',300000,800000,'trung bình',NULL);
INSERT INTO "price_items" VALUES(20,'Đèn hậu',2,'Mức 2: Móp vừa','Thay cụm đèn',1000000,3000000,'trung bình',NULL);
INSERT INTO "price_items" VALUES(21,'Đèn hậu',3,'Mức 3: Vỡ/Biến dạng nặng','Thay cụm + sửa khung',3000000,6000000,'trung bình',NULL);
INSERT INTO "price_items" VALUES(22,'Kính chắn gió',1,'Mức 1: Xước nhẹ','Hàn kính (rạn nhỏ)',500000,1500000,'trung bình',NULL);
INSERT INTO "price_items" VALUES(23,'Kính chắn gió',2,'Mức 2: Móp vừa','Thay kính + công lắp',5000000,10000000,'cao',NULL);
INSERT INTO "price_items" VALUES(24,'Kính chắn gió',3,'Mức 3: Vỡ/Biến dạng nặng','Thay kính + hiệu chuẩn ADAS',10000000,20000000,'trung bình','Có cảm biến/camera sau kính -> cần hiệu chuẩn, chuyển thẩm định viên');
INSERT INTO "price_items" VALUES(25,'Gương chiếu hậu',1,'Mức 1: Xước nhẹ','Thay ốp gương',450000,1500000,'cao',NULL);
INSERT INTO "price_items" VALUES(26,'Gương chiếu hậu',2,'Mức 2: Móp vừa','Thay cụm gương',1500000,4000000,'trung bình',NULL);
INSERT INTO "price_items" VALUES(27,'Gương chiếu hậu',3,'Mức 3: Vỡ/Biến dạng nặng','Thay cụm + sửa chân gương',4000000,8000000,'trung bình',NULL);
INSERT INTO "price_items" VALUES(28,'Khung sườn / Kết cấu',2,'Mức 2: Móp vừa','Nắn khung nhẹ',5000000,15000000,'cao',NULL);
INSERT INTO "price_items" VALUES(29,'Khung sườn / Kết cấu',3,'Mức 3: Vỡ/Biến dạng nặng','Nắn khung nặng / hàn vá kết cấu',15000000,40000000,'trung bình','Luôn chuyển thẩm định viên kiểm tra trực tiếp');
INSERT INTO "price_items" VALUES(30,'Động cơ (thủy kích)',3,'Mức 3: Vỡ/Biến dạng nặng','Sửa chữa / thay thế động cơ ngập nước',20000000,60000000,'cao','Case Seltos MoMo: 42.000.000đ');
CREATE TABLE price_sources (
    id       INTEGER PRIMARY KEY AUTOINCREMENT,
    name     TEXT NOT NULL,
    url      TEXT NOT NULL,
    used_for TEXT
);
INSERT INTO "price_sources" VALUES(1,'MoMo - Bảo hiểm thân vỏ ô tô (14 case thực tế + mốc giá)','https://www.momo.vn/bao-hiem-o-to/than-vo','Case thực tế, mốc va quẹt nhỏ 2-3tr, va chạm nặng 30-80tr, thủy kích 20-60tr');
INSERT INTO "price_sources" VALUES(2,'GTX Auto Care - Bảng giá sơn xe ô tô 2026','https://gtxautocare.vn/son-xe-o-to','Sơn từng bộ phận (cửa, cản, capo, cốp) xe sedan/SUV');
INSERT INTO "price_sources" VALUES(3,'Gara Ô Tô Hoàng Gia Phát - Sơn ô tô Hải Phòng','https://otohoanggiaphat.com/son-o-to-tai-hai-phong/','Sơn tỉa, sơn cản, sơn capo theo dòng xe');
INSERT INTO "price_sources" VALUES(4,'Thiên Minh Auto Safety - Giá sơn dặm xe ô tô Hà Nội','https://thienminh-autosafety.com/gia-son-dam-xe-o-to-ha-noi-het-bao-nhieu-tien/','Sơn dặm cản/cửa/capo theo phân khúc A-B, C-D, hạng sang');
INSERT INTO "price_sources" VALUES(5,'Gia Đình Auto - Bảng giá sơn xe ô tô','https://giadinhauto.com/tin-tuc/mau-son-xe-o-to.html','Sơn từng bộ phận, sơn toàn xe');
INSERT INTO "price_sources" VALUES(6,'Ô tô Mỹ Đình THC - Bảng giá gò, hàn, phục hồi 8/2026','https://otomydinhthc.com/bang-gia-dich-vu-go-han-phuc-hoi-o-to-moi-nhat/','Tiền công gò móp capo/cửa/tai xe/cốp, tháo lắp thay thế');
INSERT INTO "price_sources" VALUES(7,'GoGara - Sửa móp xe ô tô, bảng chi phí 2026','https://www.gogara.vn/blog/chi-phi-sua-mop-son-o-to','Sơn dặm, gò + sơn vùng móp, sơn lại 1 chi tiết, sơn cả xe');
INSERT INTO "price_sources" VALUES(8,'Thành Nam Auto - Bảng giá sửa chữa ô tô 2026','https://www.thanhnamoto.com/tin-tuc/bang-gia-sua-chua-o-to-xuan-loc-dong-nai','Sơn dặm 1 panel, sơn toàn xe, mặt bằng gara tư nhân rẻ hơn hãng 30-50%');
INSERT INTO "price_sources" VALUES(9,'1CAR Gara - Bảng giá phụ tùng thân vỏ ô tô','https://1cargara.vn/phu-tung-than-vo-o-to.html','Giá thay cản/capo/cửa/đèn pha (chưa sơn) xe phổ thông');
INSERT INTO "price_sources" VALUES(10,'Cao Sang Auto - Chi phí thay kính chắn gió ô tô','https://caosangauto.com/kinh-chan-gio-xe-hoi-o-to-xe-tai-gia-tot-hang-chinh-hang/','Thay kính chắn gió trọn gói từ 5 triệu');
INSERT INTO "price_sources" VALUES(11,'VietnamNet - Đèn pha ô tô ngày càng đắt đỏ','https://vietnamnet.vn/den-pha-o-to-ngay-cang-dat-do-chi-nut-thau-kinh-cung-co-the-phai-thay-ca-cum-2539244.html','Đèn pha hiện đại thường phải thay cả cụm; LED/laser/adaptive rất đắt (dữ liệu Allianz)');
INSERT INTO "price_sources" VALUES(12,'Phúc Việt Auto - Làm đồng sơn ô tô','https://phucvietauto.com/lam-dong-son-o-to-gia-re-quan-12-uy-tin-chat-luong-hang-dau/','Làm đồng + sơn 1 cửa, 1 cản, toàn xe');
CREATE TABLE reference_cases (
    ref_id           TEXT PRIMARY KEY,
    insurer          TEXT NOT NULL,
    vehicle          TEXT NOT NULL,
    part             TEXT NOT NULL,
    severity         INTEGER NOT NULL CHECK (severity IN (1, 2, 3)),
    severity_label   TEXT NOT NULL,
    description      TEXT NOT NULL,
    compensation_vnd INTEGER NOT NULL,
    premium_vnd      INTEGER  -- phi bao hiem / nam, NULL neu khong ro
);
INSERT INTO "reference_cases" VALUES('REF_05','PVI','Kia Seltos 2024','Động cơ (thủy kích)',3,'Mức 3: Vỡ/Biến dạng nặng','Đi qua đoạn đường ngập nước, động cơ bị thủy kích.',42000000,7110000);
INSERT INTO "reference_cases" VALUES('REF_06','PVI','Kia Carnival 2024','Đuôi xe (cản sau)',3,'Mức 3: Vỡ/Biến dạng nặng','Lùi xe trong bãi đỗ đông, va chạm xe phía sau, hư hỏng đuôi xe.',32000000,11300000);
INSERT INTO "reference_cases" VALUES('REF_07','PVI','Honda HR-V 2024','Thân xe (trầy xước, móp nhẹ)',2,'Mức 2: Móp vừa','Va chạm nhẹ với xe máy trong hẻm, thân xe trầy xước, móp nhẹ.',14000000,7100000);
INSERT INTO "reference_cases" VALUES('REF_08','PJICO','Mitsubishi Xpander 2024','Đầu xe + Đèn pha phải',3,'Mức 3: Vỡ/Biến dạng nặng','Va chạm với xe máy ban đêm, đầu xe móp + vỡ đèn pha phải.',13200000,5006000);
INSERT INTO "reference_cases" VALUES('REF_09','PJICO','Toyota Fortuner 2022','Toàn thân + Gương chiếu hậu',2,'Mức 2: Móp vừa','Bị phá hoại qua đêm, trầy xước toàn thân + vỡ gương chiếu hậu.',22500000,7790000);
INSERT INTO "reference_cases" VALUES('REF_10','PJICO','Toyota Camry 2023','Cửa xe',2,'Mức 2: Móp vừa','Va chạm khi chuyển làn, hư hỏng cửa xe.',25000000,18200000);
INSERT INTO "reference_cases" VALUES('REF_11','Bảo Việt','Honda CR-V 2023','Cản sau + Đèn hậu',2,'Mức 2: Móp vừa','Lùi xe trong bãi đỗ, va chạm xe phía sau, cản sau + đèn hậu.',28000000,11400000);
INSERT INTO "reference_cases" VALUES('REF_12','Bảo Việt','VinFast VF5 2024','Đầu xe (trầy xước, móp)',2,'Mức 2: Móp vừa','Va chạm cột bê tông trong bãi đỗ xe, đầu xe trầy xước, móp.',8500000,6050000);
INSERT INTO "reference_cases" VALUES('REF_13','Bảo Việt','VinFast VF9 2024','Đầu xe + Thân xe',3,'Mức 3: Vỡ/Biến dạng nặng','Va chạm mạnh trên cao tốc, hư hỏng nặng đầu xe + thân xe.',58000000,18600000);
INSERT INTO "reference_cases" VALUES('REF_14','GIC','Toyota Innova 2021','Nắp capo',2,'Mức 2: Móp vừa','Vật rơi từ trên cao, nắp capo móp + trầy xước nhẹ.',12000000,7400000);
INSERT INTO "reference_cases" VALUES('REF_15','DBV','Mazda 2 2022','Thân xe (trầy xước nhẹ)',1,'Mức 1: Xước nhẹ','Trầy xước nhẹ khi đỗ xe trên vỉa hè.',4500000,5094000);
INSERT INTO "reference_cases" VALUES('REF_16','DBV','VinFast VF3 2024','Cản trước (trầy xước, bong sơn)',2,'Mức 2: Móp vừa','MoMo không có mô tả cho case này - mô tả theo ảnh: cản trước trầy xước, bong sơn.',6800000,3300000);
INSERT INTO "reference_cases" VALUES('REF_17','Liberty','Kia Cerato 2022','Thân xe (trầy xước)',1,'Mức 1: Xước nhẹ','Trầy xước thân xe trong bãi đỗ xe trung tâm thương mại.',6800000,5800000);
INSERT INTO "reference_cases" VALUES('REF_18','Bảo Long','Ford Everest 2022','Đầu xe (hư hỏng nghiêm trọng)',3,'Mức 3: Vỡ/Biến dạng nặng','Va chạm mạnh trên cao tốc với xe phía trước, đầu xe hư hỏng nghiêm trọng.',75000000,7079000);
INSERT INTO "reference_cases" VALUES('REF_19','MIC + OPES','Mercedes-Benz GLC200','Thân xe (trầy xước nhiều vết)',1,'Mức 1: Xước nhẹ','Bị cào xước 7 vị trí trên thân xe (cửa, hông sau). Chi phí sửa chữa thực tế 8.200.000đ; giám định thiệt hại 6.862.000đ. Báo giá hãng 127,6 triệu và yêu cầu 470 triệu đã bị tòa bác.',8200000,NULL);
CREATE TABLE reference_photos (
    ref_id   TEXT PRIMARY KEY,
    case_ref TEXT NOT NULL REFERENCES reference_cases (ref_id),
    file_name TEXT NOT NULL,
    angle_no INTEGER NOT NULL CHECK (angle_no IN (1, 2, 3))
);
INSERT INTO "reference_photos" VALUES('REF_05','REF_05','ref-05-seltos-thuy-kich.jpg',1);
INSERT INTO "reference_photos" VALUES('REF_05b','REF_05','ref-05b-seltos-thuy-kich.jpg',2);
INSERT INTO "reference_photos" VALUES('REF_05c','REF_05','ref-05c-seltos-thuy-kich.jpg',3);
INSERT INTO "reference_photos" VALUES('REF_06','REF_06','ref-06-carnival-duoi-xe.jpg',1);
INSERT INTO "reference_photos" VALUES('REF_06b','REF_06','ref-06b-carnival-duoi-xe.jpg',2);
INSERT INTO "reference_photos" VALUES('REF_06c','REF_06','ref-06c-carnival-duoi-xe.jpg',3);
INSERT INTO "reference_photos" VALUES('REF_07','REF_07','ref-07-hrv-tray-xuoc.jpg',1);
INSERT INTO "reference_photos" VALUES('REF_07b','REF_07','ref-07b-hrv-tray-xuoc.jpg',2);
INSERT INTO "reference_photos" VALUES('REF_07c','REF_07','ref-07c-hrv-tray-xuoc.jpg',3);
INSERT INTO "reference_photos" VALUES('REF_08','REF_08','ref-08-xpander-den-pha.jpg',1);
INSERT INTO "reference_photos" VALUES('REF_08b','REF_08','ref-08b-xpander-den-pha.jpg',2);
INSERT INTO "reference_photos" VALUES('REF_08c','REF_08','ref-08c-xpander-den-pha.jpg',3);
INSERT INTO "reference_photos" VALUES('REF_09','REF_09','ref-09-fortuner-toan-than.jpg',1);
INSERT INTO "reference_photos" VALUES('REF_09b','REF_09','ref-09b-fortuner-toan-than.jpg',2);
INSERT INTO "reference_photos" VALUES('REF_09c','REF_09','ref-09c-fortuner-toan-than.jpg',3);
INSERT INTO "reference_photos" VALUES('REF_10','REF_10','ref-10-camry-cua-xe.jpg',1);
INSERT INTO "reference_photos" VALUES('REF_10b','REF_10','ref-10b-camry-cua-xe.jpg',2);
INSERT INTO "reference_photos" VALUES('REF_10c','REF_10','ref-10c-camry-cua-xe.jpg',3);
INSERT INTO "reference_photos" VALUES('REF_11','REF_11','ref-11-crv-can-sau.jpg',1);
INSERT INTO "reference_photos" VALUES('REF_11b','REF_11','ref-11b-crv-can-sau.jpg',2);
INSERT INTO "reference_photos" VALUES('REF_11c','REF_11','ref-11c-crv-can-sau.jpg',3);
INSERT INTO "reference_photos" VALUES('REF_12','REF_12','ref-12-vf5-dau-xe.jpg',1);
INSERT INTO "reference_photos" VALUES('REF_12b','REF_12','ref-12b-vf5-dau-xe.jpg',2);
INSERT INTO "reference_photos" VALUES('REF_12c','REF_12','ref-12c-vf5-dau-xe.jpg',3);
INSERT INTO "reference_photos" VALUES('REF_13','REF_13','ref-13-vf9-dau-than.jpg',1);
INSERT INTO "reference_photos" VALUES('REF_13b','REF_13','ref-13b-vf9-dau-than.jpg',2);
INSERT INTO "reference_photos" VALUES('REF_13c','REF_13','ref-13c-vf9-dau-than.jpg',3);
INSERT INTO "reference_photos" VALUES('REF_14','REF_14','ref-14-innova-capo.jpg',1);
INSERT INTO "reference_photos" VALUES('REF_14b','REF_14','ref-14b-innova-capo.jpg',2);
INSERT INTO "reference_photos" VALUES('REF_14c','REF_14','ref-14c-innova-capo.jpg',3);
INSERT INTO "reference_photos" VALUES('REF_15','REF_15','ref-15-mazda2-tray-nhe.jpg',1);
INSERT INTO "reference_photos" VALUES('REF_15b','REF_15','ref-15b-mazda2-tray-nhe.jpg',2);
INSERT INTO "reference_photos" VALUES('REF_15c','REF_15','ref-15c-mazda2-tray-nhe.jpg',3);
INSERT INTO "reference_photos" VALUES('REF_16','REF_16','ref-16-vf3.jpg',1);
INSERT INTO "reference_photos" VALUES('REF_16b','REF_16','ref-16b-vf3.jpg',2);
INSERT INTO "reference_photos" VALUES('REF_16c','REF_16','ref-16c-vf3.jpg',3);
INSERT INTO "reference_photos" VALUES('REF_17','REF_17','ref-17-cerato-tray-xuoc.jpg',1);
INSERT INTO "reference_photos" VALUES('REF_17b','REF_17','ref-17b-cerato-tray-xuoc.jpg',2);
INSERT INTO "reference_photos" VALUES('REF_17c','REF_17','ref-17c-cerato-tray-xuoc.jpg',3);
INSERT INTO "reference_photos" VALUES('REF_18','REF_18','ref-18-everest-dau-xe.jpg',1);
INSERT INTO "reference_photos" VALUES('REF_18b','REF_18','ref-18b-everest-dau-xe.jpg',2);
INSERT INTO "reference_photos" VALUES('REF_18c','REF_18','ref-18c-everest-dau-xe.jpg',3);
INSERT INTO "reference_photos" VALUES('REF_19','REF_19','ref-19-merc-glc200-tray-xuoc.jpg',1);
DELETE FROM "sqlite_sequence";
INSERT INTO "sqlite_sequence" VALUES('price_sources',12);
INSERT INTO "sqlite_sequence" VALUES('price_items',30);
INSERT INTO "sqlite_sequence" VALUES('case_level_anchors',4);
COMMIT;
