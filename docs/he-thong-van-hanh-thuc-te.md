# AI Motor Claim Assessor — Cách vận hành thực tế

**Tài liệu tổng hợp — lập ngày 19/11/2026.**
Repo: https://github.com/huynhlekimhuy12345-ship-it/he_thong_danh_gia_thiet_hai_xe
Commit tại thời điểm lập tài liệu: `febc670` (đã kiểm chứng remote HEAD = local HEAD ngày 19/11/2026).

## Quy tắc của tài liệu này

Mọi số liệu trong tài liệu đều được đối chiếu trực tiếp với code, database hoặc
trang nguồn vào ngày 19/11/2026. Mỗi mục ghi rõ nguồn ở cuối dòng/mục, dạng:

- `code: <đường dẫn file>` — đọc trực tiếp từ code trong repo.
- `db: db/price-data.db` — truy vấn trực tiếp bằng SQLite.
- `web: <URL>` — trang web đã mở/đọc ngày 19/11/2026.
- `kiểm chứng: <câu lệnh>` — số liệu đếm/truy vấn lại được.

Nội dung nào **chưa** được chạy/kiểm chứng thực tế được ghi riêng ở mục 9,
không trộn với nội dung đã xác minh.

---

## 0. Trạng thái kiểm chứng — đọc trước

- **Code chưa từng biên dịch và chưa từng chạy.** Máy agent không có Java/Maven
  (đã kiểm tra ngày 19/11/2026). Người dùng chưa xác nhận chạy thành công lần đầu.
  Vì vậy mọi mô tả luồng vận hành trong tài liệu này là **theo code đã viết**,
  chưa phải hành vi đã chạy thực tế. Lần chạy đầu tiên (`mvn spring-boot:run`)
  là bước kiểm chứng còn thiếu.
  Nguồn: kiểm tra môi trường máy agent ngày 19/11/2026; xác nhận của người dùng
  (chưa báo chạy thành công).
- **Giá dịch vụ Gemini** tra cứu trên web ngày 19/11/2026 (nhiều nguồn độc lập
  khớp nhau, xem mục 4.4). Giá có hạn: tăng gấp đôi từ 01/01/2027.
- **Mức độ (Mức 1/2/3) của 14 case MoMo là do agent tự gán** theo mô tả + ảnh,
  không phải dữ liệu MoMo công bố. MoMo chỉ công bố: xe, hãng bảo hiểm, mô tả
  tình huống, phí bảo hiểm/năm và phí bồi thường (xem mục 3.3).

---

## 1. Hệ thống là gì

- **Tên:** AI Motor Claim Assessor — hệ thống thẩm định thiệt hại xe bằng AI.
  Nguồn: `README.md` dòng 1; `pom.xml` (`artifactId: ai-motor-claim-assessor`).
- **Mục đích:** nhận ảnh thiệt hại xe → xác minh đúng xe → AI (Gemini) đối chiếu
  với kho ảnh mẫu có giá thật → đề xuất mức bồi thường + độ tin cậy → giám định
  viên chốt cuối. AI không quyết định thay người.
  Nguồn: `README.md` mục "Luồng nghiệp vụ"; `src/main/java/com/aimotor/claim/service/GeminiService.java`.
- **Phạm vi:** 1 xe / 1 hồ sơ. Không cần chụp nội thất (đã chốt ngày 19/11/2026).
  Nguồn: quyết định của người dùng ngày 19/11/2026; `README.md`.
- **Công nghệ:**
  | Thành phần | Giá trị | Nguồn |
  |---|---|---|
  | Ngôn ngữ | Java 17 | `pom.xml` (`java.version: 17`) |
  | Framework | Spring Boot 3.3.5 | `pom.xml` (parent 3.3.5) |
  | Database vận hành | SQLite (1 file, không cần cài thêm database) | `pom.xml` (sqlite-jdbc); `application.properties` (`app.storage.main-db`, mặc định `./storage/aimotor.db`) |
  | Xử lý ảnh | Thumbnailator 0.4.20 | `pom.xml` |
  | Model AI | `gemini-3.8-flash` | `application.properties` (`gemini.model=gemini-3.8-flash`) |
  | Giao diện | 1 file HTML/CSS/JS, 5 tab (Thẩm định, Tra cứu bồi thường, Mua bảo hiểm, Kho ảnh mẫu, Cài đặt) | `src/main/resources/static/index.html` |
  | Chạy ở cổng | 8080 | `application.properties` (`server.port=8080`) |
- **Model Gemini 3.8 Flash — thông tin đã tra cứu ngày 19/11/2026:**
  | Hạng mục | Giá trị | Nguồn |
  |---|---|---|
  | Model ID | `gemini-3.8-flash` (bản ổn định GA) | web: https://apidog.com/blog/what-is-gemini-3-8-flash/ |
  | Ra mắt | 02/09/2026 | web: https://truestandard.ai/blog/gemini-3-8-flash-review |
  | Cửa sổ ngữ cảnh | 1.048.576 token | web: https://models.litellm.ai/models/gemini-3-8-flash |
  | Đầu ra tối đa | 65.536 token/lần gọi | web: https://models.litellm.ai/models/gemini-3-8-flash |
  | Giá đến 31/12/2026 | $0,75 / 1 triệu token đầu vào; $3,75 / 1 triệu token đầu ra | web: https://humanrank.ai/models/gemini-3-8-flash/ (đối chiếu ngày 15/11/2026), https://models.litellm.ai/models/gemini-3-8-flash (kiểm tra 18/11/2026) |
  | Giá từ 01/01/2027 | $1,50 / $7,50 (tăng gấp đôi) | web: https://truestandard.ai/blog/gemini-3-8-flash-review |
  | Gọi API | `POST https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent`, header `x-goog-api-key` | code: `GeminiService.java` dòng 109–114; `application.properties` (`gemini.api.base-url`) |

---

## 2. Luồng vận hành (8 bước)

### Bước 1 — Mua bảo hiểm, đăng ký hồ sơ xe gốc (tab "Mua bảo hiểm")

- Người dùng nhập đầy đủ: **tên chủ xe, ngày sinh chủ xe, biển số, SĐT,
  loại xe, hãng xe, dòng xe, năm sản xuất, màu xe**.
  Nguồn: code: `VehicleController.java` (endpoint `POST /api/vehicles`);
  `CustomerVehicle.java`; `index.html` (form đăng ký).
- Ngày sinh là **bắt buộc**, phải là ngày trong quá khứ (ngày sinh lớn hơn hôm
  nay thì báo lỗi). Thiếu tên chủ xe / ngày sinh / biển số / SĐT / loại xe thì
  báo lỗi tiếng Việt, không lưu.
  Nguồn: code: `VehicleService.java` (hàm `register`, kiểm tra `isBlank()` và
  `isAfter(LocalDate.now())`).
- Chụp **ảnh toàn bộ xe lúc còn nguyên vẹn** (ảnh gốc): checklist 4 góc ngoại
  thất (trước/sau/trái/phải) + biển số/VIN + cốp. Ảnh lưu vào
  `storage/vehicles/<BIỂN-SỐ>/`.
  Nguồn: code: `VehicleService.java` (lưu qua `ImagePreprocessingService.storeFile`);
  `index.html` (checklist ảnh gốc); `storage/vehicles/README.md`.
- Biển số trùng (đã có hồ sơ) thì báo lỗi, không tạo trùng.
  Nguồn: code: `VehicleService.java` (`existsByLicensePlate`).
- Hồ sơ xe lưu vào SQLite, bảng `customer_vehicles`.
  Nguồn: code: `CustomerVehicleRepository.java` (JDBC); `DatabaseService.java` (tạo bảng).

### Bước 2 — Xác nhận xe gốc trước khi chụp ảnh hư hại (tab "Thẩm định")

- Người dùng nhập **biển số + SĐT + loại xe** đã đăng ký, bấm "Tìm xe của tôi".
  Nguồn: code: `VehicleController.java` (`GET /api/vehicles/confirm`);
  `index.html` (nút `btnFindVehicle`).
- Hệ thống hiện xe gốc để người dùng **xác nhận**: tên chủ xe, ngày sinh,
  biển số (đã che), loại xe, hãng/dòng/năm, màu xe, ngày đăng ký, **ảnh gốc lúc
  mua bảo hiểm**.
  Nguồn: code: `VehicleController.java` (record `VehicleConfirmDto`);
  `index.html` (hàm hiển thị `confirmBox`).
- Chỉ sau khi bấm "Đúng xe của tôi" thì phần chụp ảnh hư hại mới mở ra.
  Chưa xác nhận thì không gửi thẩm định được.
  Nguồn: code: `index.html` (`#photoSection` ẩn mặc định; cờ `confirmedVehicle`).
- Nhập sai 1 trong 3 thông tin → báo 403 chung chung
  ("Thông tin không khớp với hồ sơ xe…"), **không tiết lộ** biển số đó có tồn
  tại hay không.
  Nguồn: code: `VehicleController.java` (nhánh `!result.passed()`).

### Bước 3 — Xác minh danh tính (backend, trước mọi xử lý khác)

- Kiểm tra 3 mục: biển số có hồ sơ không, SĐT có khớp không, loại xe có khớp
  không. Cả 3 phải đúng mới qua.
  Nguồn: code: `VehicleService.java` (record `IdentityResult`, hàm
  `checkIdentity`); `ClaimAssessmentService.java` (Bước 1).
- Sai → hồ sơ bị **REJECTED ngay, không gọi AI** (tiết kiệm token, chống gian
  lận), lưu lịch sử với lý do.
  Nguồn: code: `ClaimAssessmentService.java` (dòng 86–106, `saveRejected`).
- Mỗi lần xác minh (thẩm định / tra cứu / xác nhận xe gốc) đều ghi nhật ký vào
  SQLite riêng (xem mục 6.3).
  Nguồn: code: `VerificationAuditService.java`; `ClaimAssessmentService.java`;
  `AssessmentController.java`; `VehicleController.java`.

### Bước 4 — Kiểm tra ảnh đầu vào

- Tối thiểu **2 ảnh**/hồ sơ; mỗi ảnh tối đa **10MB**, tổng request tối đa
  **100MB**; chỉ nhận file ảnh (JPG/PNG/WebP).
  Nguồn: code: `application.properties` (`app.assessment.min-photos=2`,
  `spring.servlet.multipart.max-file-size=10MB`,
  `max-request-size=100MB`); `ClaimAssessmentService.java`.
- Đo độ nét từng ảnh (phương sai Laplacian), ngưỡng **80,0** — dưới ngưỡng coi
  là ảnh mờ.
  Nguồn: code: `application.properties` (`app.image.sharpness-threshold=80.0`);
  `ImageSharpnessService.java`.
- Ảnh gốc gửi AI được resize về **rộng tối đa 1024px**, chất lượng JPEG **85%**.
  Nguồn: code: `application.properties` (`app.image.max-width=1024`,
  `app.image.jpeg-quality=0.85`); `ImagePreprocessingService.java`.
- Bộ ảnh tối thiểu theo checklist: 4 góc ngoại thất + cốp + biển số/VIN +
  cận cảnh từng vết. Không cần ảnh nội thất.
  Nguồn: quyết định của người dùng ngày 19/11/2026; `index.html` (checklist);
  `GeminiService.java` (quy tắc c, Bước 1).

### Bước 5 — Lưu ảnh thẩm định xuống đĩa

- Ảnh gốc khách gửi được lưu vào `storage/claims/<MÃ-HỒ-SƠ>/` ngay sau khi xác
  minh danh tính xong và ảnh qua kiểm tra đầu vào. Đường dẫn ghi trong hồ sơ.
  Nguồn: code: `ClaimAssessmentService.java` (dòng 133, `storeFile` vào
  `claimDir`); `ClaimAssessment.java` (trường `claimPhotoPaths`).
- Hồ sơ bị từ chối ở bước xác minh danh tính thì ảnh **không được lưu**.
  Nguồn: code: `ClaimAssessmentService.java` (lưu ảnh chỉ xảy ra sau khi
  `identity.isPassed()`).

### Bước 6 — Gọi Gemini đánh giá (3 lần kiểm chứng, nhiệt độ 0)

- Gửi 3 lần giống hệt nhau (cùng bộ ảnh + prompt, nhiệt độ 0): ảnh mẫu có nhãn
  + ảnh gốc của xe + ảnh khách gửi + system prompt. Ép trả về
  `application/json` theo `responseSchema` (không có markdown). Hợp nhất bằng
  bỏ phiếu theo từng vết (`mergeConsensus`): gộp vết theo bộ phận chuẩn + bên
  (`PriceTableService.groupKey` — "Cửa sau trái" và "Cánh cửa bên trái" về chung
  1 nhóm); vết đủ 2/3 lần thì mức độ + nấc giá (min/giữa/max) lấy theo đa số,
  chốt lại bằng `snapToAnchor`; vết chỉ thiểu số lần thấy thì GIỮ LẠI ở nấc min
  + gắn cờ để giám định viên xác nhận (không loại vết nào khỏi tổng). Chỉ 1/3 lần
  OK thì chuyển giám định viên.
  Ghi chú kiểm chứng hiện ở kết quả (`consensusNote`).
  Nguồn: code: `GeminiService.java` (`assess` + `mergeConsensus`);
  `PriceTableService.java` (`groupKey`, `canonicalPart`, `anchorsFor`).
- System prompt bắt AI làm 2 bước. **Bước 1 — cổng kiểm tra (validation gate),
  AI phải phản biện thẳng thắn:**
  - a) Ảnh có phải ảnh ô tô không? Không phải → REJECTED.
  - a2) Ảnh chụp giấy tờ tùy thân (CCCD/CMND, giấy phép lái xe, giấy đăng ký
    xe) → REJECTED (hệ thống không thu thập CCCD/CMND).
  - b) Ảnh mờ/tối/chói/chụp xa/rung tay → NEEDS_MORE_INFO, ghi rõ ảnh số mấy.
  - c) Thiếu góc theo checklist → liệt kê vào `missingPhotos`, NEEDS_MORE_INFO.
  - d) Sai xe (khác màu, khác loại, khác biển số so với hồ sơ gốc) → REJECTED.
  - e) Vết đã có trong ảnh gốc thì không phải thiệt hại mới, không tính tiền.
  - Nếu không OK: `damages = []`, tổng tiền = 0, không được đoán mò.
  Nguồn: code: `GeminiService.java` (dòng 309–318, `buildSystemPrompt`).
- **Bước 2 — định giá theo bảng tham chiếu (không theo loại xe):**
  - Ảnh mẫu chỉ dùng để nhận diện bộ phận + mức độ. Giá trên ảnh mẫu là
    **tổng tiền cả vụ**, không lấy làm giá 1 vết.
  - Mỗi vết: xác định bộ phận + mức độ (`severityLevel`) → tra bảng giá →
    chỉ chọn 1 trong 3 nấc: min (nhẹ trong mức), giữa (trung bình), max (nặng
    trong mức); ghi rõ nấc đã chọn trong
    `referenceComparisonNote`.
  - Giá **không phụ thuộc hãng/model xe** (cùng bộ phận + cùng mức độ thì cùng
    giá). Xe sang/nhập khẩu, đèn LED/laser/adaptive, ADAS, khung nặng vượt
    bảng → giải thích + `requiresHumanReview = true`, **không tự nhân hệ số**.
  - Bộ phận không có trong bảng → ước tính từ bộ phận tương đương gần nhất +
    chuyển thẩm định viên. Đối chiếu tổng tiền với 4 mốc cấp vụ để kiểm tra
    tính hợp lý.
  Nguồn: code: `GeminiService.java` (quy tắc 6, `PriceTableService`);
  `src/main/resources/price-table.json` (mục `notes`).
- Chống trùng lặp: cùng 1 vết ở nhiều góc chỉ tính tiền **1 lần**.
  Nguồn: code: `GeminiService.java` (system prompt); `README.md`.
- Hư hại không thấy được qua ảnh (lỗi điện, mùi/mốc sau ngập, cơ cấu ghế) thì
  ghi "cần kiểm tra trực tiếp", không đoán.
  Nguồn: quyết định của người dùng ngày 19/11/2026; `README.md`.
- Ảnh thiếu thì xử lý 3 tầng: (1) checklist hướng dẫn chụp, thiếu thì không cho
  gửi; (2) kiểm tra trùng góc/chất lượng trước khi gọi AI; (3) cố gửi thì AI chỉ
  đánh giá phần nhìn thấy, ghi rõ "vùng mù", hạ độ tin cậy, giá tạm tính, bắt
  buộc thẩm định viên xem.
  Nguồn: quyết định của người dùng ngày 19/11/2026; `GeminiService.java`.

### Bước 7 — Kết quả và lưu trữ

- Kết quả JSON gồm: `assessmentSummary`, `damages[]` (mỗi vết:
  `partName`, `observedCondition`, `matchedReferenceId`,
  `referenceComparisonNote`, `estimatedCostVnd`, `confidenceScore`),
  `totalCompensationVnd`, `requiresHumanReview`, `humanReviewReason`, và khối
  `validation` (`status`: OK | NEEDS_MORE_INFO | REJECTED; `issues`;
  `missingPhotos`). Backend cộng thêm `deductibleVnd`, `netCompensationVnd`.
  Nguồn: code: `GeminiAssessmentResult.java` (DTO); `ClaimAssessment.java`.
- Công thức tiền: tổng thiệt hại = tổng các vết (chỉ khi status OK, nếu không
  thì 0); **thực nhận = max(0, tổng − miễn thường)**.
  Nguồn: code: `ClaimAssessmentService.java` (dòng 188–190).
- Mức miễn thường mặc định **500.000đ/vụ**, chỉnh được ở tab Cài đặt (0 = không
  có miễn thường). Thị trường VN thường 500.000–2.000.000đ/vụ; ví dụ trên trang
  MoMo: thiệt hại 8.000.000đ, miễn thường 500.000đ → bảo hiểm trả 7.500.000đ.
  Nguồn: code: `AppConfigService.java` (dòng 41, `.orElse(500_000L)`);
  web: https://www.momo.vn/bao-hiem-o-to/than-vo (mục miễn thường).
- Kết quả lưu SQLite, bảng `claim_assessments`; tên chủ xe được copy từ
  hồ sơ gốc sang hồ sơ thẩm định để hiển thị.
  Nguồn: code: `ClaimAssessmentRepository.java` (JDBC);
  `ClaimAssessmentService.java` (dòng 109, `setOwnerName`).
- Kết quả chỉ là **mức đề xuất** — giám định viên chốt cuối cùng, nhất là khi
  có cảnh báo kiểm tra trực tiếp.
  Nguồn: quyết định của người dùng ngày 19/11/2026; `README.md`.

### Bước 8 — Tra cứu tình trạng bồi thường (tab "Tra cứu bồi thường")

- Nhập **biển số + loại xe + SĐT** → xem toàn bộ hồ sơ bồi thường của xe mình:
  mã hồ sơ, tên chủ xe, biển số (đã che), trạng thái, tổng thiệt hại, miễn
  thường, tiền thực nhận, có cần giám định viên không, ngày gửi.
  Nguồn: code: `AssessmentController.java` (`GET /api/assessments/lookup`,
  record `ClaimStatusDto`); `index.html` (tab lookup).
- Sai thông tin → 403 chung chung, không tiết lộ biển số có tồn tại hay không.
  Nguồn: code: `AssessmentController.java` (nhánh `!result.passed()`).

---

## 3. Kho ảnh mẫu (dữ liệu thật có giá)

### 3.1. Tổng quan — kiểm chứng ngày 19/11/2026

| Hạng mục | Số liệu | Nguồn |
|---|---|---|
| Tổng số ảnh mẫu | **47 ảnh** | kiểm chứng: `ls storage/reference-damages/*.jpg` = 47; `grep -c "new ReferenceDamage" DataSeeder.java` = 47 |
| Ảnh seed test (AI tạo, cần thay bằng ảnh thật) | 4 ảnh (REF_01–04) | code: `DataSeeder.java` |
| Ảnh case thực tế | 43 ảnh (42 MoMo + 1 Tuổi Trẻ) | kiểm chứng: `ls storage/reference-damages/*.json` = 43 (mỗi ảnh case có 1 file metadata .json) |
| Tổng số case thực tế | **15 case** (chưa tính 4 ảnh seed REF_01–04 ở mục 3.2; tổng cộng 19 mã REF gốc) | db: `SELECT COUNT(*) FROM reference_cases` = 15 |
| Tổng tiền bồi thường 15 case thực tế | **356.500.000đ** (chưa tính 21.800.000đ giá mẫu của 4 ảnh seed) | db: `SELECT SUM(compensation_vnd) FROM reference_cases` = 356500000 |
| Hãng bảo hiểm | 8 (PVI, PJICO, Bảo Việt, GIC, DBV, Liberty, Bảo Long, MIC + OPES) | db: `SELECT DISTINCT insurer FROM reference_cases` |
| Số xe khác nhau | 15 xe (chỉ tính 15 case thực tế; 4 ảnh seed không gắn xe cụ thể) | db: `SELECT COUNT(DISTINCT vehicle) FROM reference_cases` |
| Phân bố mức độ | Mức 1: 3 case; Mức 2: 7 case; Mức 3: 5 case | db: `SELECT severity, COUNT(*) FROM reference_cases GROUP BY severity` |

### 3.2. 4 ảnh seed test (AI tạo — chưa phải ảnh thật)

| Mã | Bộ phận | Mức | Giá (đ) | Nguồn |
|---|---|---|---|---|
| REF_01 | Cản trước (xước nhẹ) | Mức 1 | 1.500.000 | code: `DataSeeder.java` |
| REF_02 | Cửa sau bên phụ (móp vừa) | Mức 2 | 4.500.000 | code: `DataSeeder.java` |
| REF_03 | Đèn pha trái (vỡ nát) | Mức 3 | 3.800.000 | code: `DataSeeder.java` |
| REF_04 | Nắp capo (dập nát) | Mức 3 | 12.000.000 | code: `DataSeeder.java` |

### 3.3. 14 case MoMo (42 ảnh, 3 ảnh/case)

Nguồn gốc: bài "Bảo hiểm thân vỏ ô tô" của MoMo, mục "Một số trường hợp thực
tế" — web: https://www.momo.vn/bao-hiem-o-to/than-vo (đã đọc HTML gốc ngày
19/11/2026; ảnh gốc là WebP trên `static.momocdn.net`, đã chuyển sang JPEG).

**Lưu ý quan trọng:** "Phí bồi thường" của mỗi case là **tổng tiền cả vụ**,
gán cho vùng hư hại chính — không phải bảng giá theo món. Mức độ (Mức 1/2/3)
là do agent tự gán theo mô tả + ảnh, không phải dữ liệu MoMo công bố.
Nguồn: `README.md` mục case MoMo; db: `reference_cases`.

| Mã | Hãng BH | Xe | Bộ phận | Mức | Bồi thường (đ) | Phí BH/năm (đ) |
|---|---|---|---|---|---|---|
| REF_05 | PVI | Kia Seltos 2024 | Động cơ (thủy kích) | M3 | 42.000.000 | 7.110.000 |
| REF_06 | PVI | Kia Carnival 2024 | Đuôi xe (cản sau) | M3 | 32.000.000 | 11.300.000 |
| REF_07 | PVI | Honda HR-V 2024 | Thân xe (trầy xước, móp nhẹ) | M2 | 14.000.000 | 7.100.000 |
| REF_08 | PJICO | Mitsubishi Xpander 2024 | Đầu xe + Đèn pha phải | M3 | 13.200.000 | 5.006.000 |
| REF_09 | PJICO | Toyota Fortuner 2022 | Toàn thân + Gương chiếu hậu | M2 | 22.500.000 | 7.790.000 |
| REF_10 | PJICO | Toyota Camry 2023 | Cửa xe | M2 | 25.000.000 | 18.200.000 |
| REF_11 | Bảo Việt | Honda CR-V 2023 | Cản sau + Đèn hậu | M2 | 28.000.000 | 11.400.000 |
| REF_12 | Bảo Việt | VinFast VF5 2024 | Đầu xe (trầy xước, móp) | M2 | 8.500.000 | 6.050.000 |
| REF_13 | Bảo Việt | VinFast VF9 2024 | Đầu xe + Thân xe | M3 | 58.000.000 | 18.600.000 |
| REF_14 | GIC | Toyota Innova 2021 | Nắp capo | M2 | 12.000.000 | 7.400.000 |
| REF_15 | DBV | Mazda 2 2022 | Thân xe (trầy xước nhẹ) | M1 | 4.500.000 | 5.094.000 |
| REF_16 | DBV | VinFast VF3 2024 | Cản trước (trầy xước, bong sơn) | M2 | 6.800.000 | 3.300.000 |
| REF_17 | Liberty | Kia Cerato 2022 | Thân xe (trầy xước) | M1 | 6.800.000 | 5.800.000 |
| REF_18 | Bảo Long | Ford Everest 2022 | Đầu xe (hư hỏng nghiêm trọng) | M3 | 75.000.000 | 7.079.000 |

Tổng 14 case MoMo: **348.300.000đ**. Mỗi case có 3 ảnh (mã `REF_xx`,
`REF_xxb`, `REF_xxc`) — nhiều góc giúp AI đối chiếu đúng vết; mỗi vết chỉ
khớp 1 ảnh nên tổng tiền không tính trùng.
Nguồn: db: `SELECT * FROM reference_cases WHERE ref_id LIKE 'REF_0%' OR ref_id LIKE 'REF_1%'`
(truy vấn ngày 19/11/2026); file: `storage/reference-damages/ref-05…ref-18*.jpg`
+ 42 file `.json` metadata kèm theo.

Ghi chú riêng từng case (đã đối chiếu bài MoMo ngày 19/11/2026):
- REF_16 (VF3): MoMo **không có mô tả** cho case này — mô tả trong kho là do
  agent tự ghi theo ảnh.
- 2 ảnh infographic trên bài MoMo (bảng phí bảo hiểm theo giá trị xe và công
  thức tính phí) **không đưa vào kho** vì đó là phí bảo hiểm/năm, không phải
  giá sửa xe. Phí bảo hiểm theo giá trị xe trên bài MoMo: xe 400tr →
  4,4–6tr/năm; 600tr (sedan B) → 6–8,4tr; 800tr (SUV C) → 8–11,2tr; 1 tỷ
  (SUV D) → 10–14tr; 1,5 tỷ → 15–21tr. Công thức: Phí = Giá trị xe × Tỷ lệ
  phí (1,4%–2,0%/năm). Nguồn: web: https://www.momo.vn/bao-hiem-o-to/than-vo.

### 3.4. 1 case ngoài MoMo (Tuổi Trẻ)

| Mã | Hãng BH | Xe | Bộ phận | Mức | Chi phí sửa thực tế (đ) | Nguồn |
|---|---|---|---|---|---|---|
| REF_19 | MIC + OPES | Mercedes-Benz GLC200 | Thân xe (trầy xước nhiều vết) | M1 | 8.200.000 | web: https://tuoitre.vn/chu-xe-mercedes-bi-cao-xuoc-doi-gan-nua-ti-vi-sao-toa-chi-cho-nhan-671000-dong-100261009094444438.htm |

Chi tiết đã đối chiếu bài báo ngày 19/11/2026: xe bị cào xước 7 vị trí trên
thân xe (10/2026). Chi phí sửa thực tế 8.200.000đ (chủ xe trả 2.000.000đ, bảo
hiểm MIC + OPES trả 6.200.000đ); giám định thiệt hại 6.862.000đ. Báo giá hãng
127,6 triệu và yêu cầu bồi thường 470 triệu đã bị tòa bác — **không dùng 2 số
đó**. 7 vị trí ≈ 1,17 triệu/vị trí, khớp khoảng sơn dặm 1 chi tiết trong bảng
giá tham chiếu (mục 4). Chỉ lấy 1 ảnh cận cảnh vết xước trên hông sau; 2 ảnh
còn lại của bài báo không lấy vì người chiếm gần hết khung hình, vết xước mờ.
Nguồn: db: `reference_cases` (REF_19, `premium_vnd` = NULL vì bài báo không
nêu phí bảo hiểm/năm); file: `storage/reference-damages/ref-19-merc-glc200-tray-xuoc.jpg`.

### 3.5. Đã kiểm tra nhưng KHÔNG đưa vào kho (nguyên tắc lọc)

Nguyên tắc đã chốt: chỉ ảnh nào thấy rõ vết hư hại thật + có số tiền sửa/bồi
thường xác thực mới vào kho. Ảnh minh họa hoặc ảnh không thấy vết sẽ làm giảm
độ chính xác, không tăng.

| Case đã kiểm tra | Lý do không lấy | Nguồn kiểm tra |
|---|---|---|
| BMW 320i bị tai nạn (Gara Ô tô Mỹ Đình THC) — báo giá chi tiết 41,2tr (phục hồi túi khí) / 65,1tr (thay mới), cản trước 7,5tr | Ảnh duy nhất trong bài là xe đang sửa trong gara, capo mở, **không thấy vết hư hại** | web: https://otomydinhthc.com/chi-phi-sua-chua-xe-bmw-320i/ (đọc ngày 19/11/2026) |
| Hyundai Accent bị cây đổ (VietnamNet) — báo giá sửa 116,5tr, yêu cầu bồi thường 71tr | Ảnh là ảnh minh họa; số tiền đang tranh chấp, tòa bác yêu cầu (thiệt hại do bất khả kháng) | web: https://vietnamnet.vn/cay-xanh-truong-hoc-do-de-len-o-to-chu-xe-bi-bac-yeu-cau-boi-thuong-71-trieu-2565305.html (đọc ngày 19/11/2026; Bản án 676/2026/DS-PT ngày 7/4/2026 của TAND TPHCM) |
| 2 ảnh còn lại của bài Mercedes GLC200 (Tuổi Trẻ) | Người chiếm gần hết khung hình, vết xước mờ | web: Tuổi Trẻ (mục 3.4) |
| Vụ Toyota Hilux (Tuổi Trẻ) — bồi thường 307tr | Ảnh trong bài là người tại tòa, không có ảnh vết hư hại | web: tìm kiếm ngày 19/11/2026 (Tuổi Trẻ) |

---

## 4. Bảng giá tham chiếu (AI định giá theo bộ phận × mức độ, KHÔNG theo loại xe)

### 4.1. Tổng quan

| Hạng mục | Số liệu | Nguồn |
|---|---|---|
| File bảng giá | `src/main/resources/price-table.json` (v1.0, cập nhật 19/11/2026) | code: file |
| Số mức giá | **30 mức** (11 bộ phận × 3 mức độ; riêng Khung sườn chỉ có M2/M3, Động cơ thủy kích chỉ có M3) | db: `SELECT COUNT(*) FROM price_items` = 30 |
| Phạm vi giá | Xe ô tô phổ thông tại Việt Nam. Giá KHÔNG phân biệt hãng/model xe. Chưa gồm VAT. Giá gara tư nhân; hãng chính hãng thường cao hơn 30–50% | code: `price-table.json` (`scope`) |
| Độ tin cậy | 18 mức `cao` (nhiều nguồn khớp) / 12 mức `trung bình` (ước tính thị trường) | db: `SELECT confidence, COUNT(*) FROM price_items GROUP BY confidence` (đếm lại ngày 19/11/2026: 18 cao / 12 trung bình) |
| Số nguồn giá | **12 nguồn** (xem 4.3) | db: `SELECT COUNT(*) FROM price_sources` = 12 |
| Mốc giá cấp vụ | **4 mốc** (xem 4.2) | db: `SELECT COUNT(*) FROM case_level_anchors` = 4 |
| Cách cập nhật giá | Sửa `price-table.json` + khởi động lại app (không sửa code) | code: `PriceTableService.java`; `README.md` |

Mức độ: **Mức 1: Xước nhẹ** (sơn dặm) | **Mức 2: Móp vừa** (gò sơn) |
**Mức 3: Vỡ/Biến dạng nặng** (thay mới).
Nguồn: quyết định của người dùng (spec hệ thống ngày 19/11/2026);
code: `ReferenceDamage.java` (`severityLevel`).

### 4.2. Bảng 30 mức giá (đơn vị: VNĐ)

Nguồn: db: `SELECT part, severity, min_vnd, max_vnd, confidence FROM price_items`
(truy vấn ngày 19/11/2026).

| Bộ phận | Mức 1 (Sơn dặm) | Mức 2 (Gò sơn) | Mức 3 (Thay mới) |
|---|---|---|---|
| Cản trước / Cản sau | 500.000–1.200.000 (cao) | 1.200.000–3.000.000 (cao) | 2.000.000–5.000.000 (cao) |
| Cánh cửa | 400.000–1.000.000 (cao) | 1.000.000–3.000.000 (cao) | 4.000.000–10.000.000 (cao) |
| Nắp capo | 800.000–1.500.000 (cao) | 1.500.000–3.500.000 (cao) | 3.700.000–8.000.000 (cao) |
| Nắp cốp / Đuôi xe | 500.000–1.500.000 (cao) | 1.200.000–3.000.000 (cao) | 3.000.000–7.000.000 (trung bình) |
| Tai xe / Vè (dè) | 400.000–1.000.000 (cao) | 800.000–2.500.000 (cao) | 2.500.000–6.000.000 (trung bình) |
| Đèn pha | 500.000–1.500.000 (trung bình) | 2.000.000–6.000.000 (cao) | 6.000.000–12.000.000 (trung bình) |
| Đèn hậu | 300.000–800.000 (trung bình) | 1.000.000–3.000.000 (trung bình) | 3.000.000–6.000.000 (trung bình) |
| Kính chắn gió | 500.000–1.500.000 (trung bình) | 5.000.000–10.000.000 (cao) | 10.000.000–20.000.000 (trung bình) |
| Gương chiếu hậu | 450.000–1.500.000 (cao) | 1.500.000–4.000.000 (trung bình) | 4.000.000–8.000.000 (trung bình) |
| Khung sườn / Kết cấu | — (không có Mức 1) | 5.000.000–15.000.000 (cao) | 15.000.000–40.000.000 (trung bình) |
| Động cơ (thủy kích) | — (không có Mức 1/2) | — | 20.000.000–60.000.000 (cao) |

**4 mốc giá cấp vụ** (dùng để kiểm tra tính hợp lý của tổng tiền):
Nguồn: db: `SELECT * FROM case_level_anchors`.

| Mốc | Khoảng (đ) | Nguồn mốc |
|---|---|---|
| Va quẹt nhỏ | 2.000.000–3.000.000 | MoMo |
| Va chạm nặng | 30.000.000–80.000.000 | MoMo |
| Thủy kích (động cơ ngập nước) | 20.000.000–60.000.000 | MoMo |
| Sơn toàn xe (phổ thông) | 12.000.000–35.000.000 | Thành Nam Auto / GTX / GoGara |

**3 ghi chú kèm bảng giá** (nguồn: code: `price-table.json`, mục `notes`):
1. Màu sơn đặc biệt (ngọc trai, kim loại, hai tông) cộng thêm 15–30% so với bảng.
2. Xe sang/nhập khẩu, đèn LED/laser/adaptive, cảm biến ADAS, khung sườn biến
   dạng nặng: vượt bảng giá → chuyển thẩm định viên, KHÔNG tự nhân hệ số.
3. Giá ghi trên mỗi ảnh mẫu REF_xx là TỔNG TIỀN CẢ VỤ của case đó, không phải
   giá của riêng bộ phận — chỉ dùng để đối chiếu hình ảnh và mức độ.

### 4.3. 12 nguồn giá (tên + URL — trích nguyên văn từ `price-table.json`)

| # | Nguồn | URL |
|---|---|---|
| 1 | MoMo — Bảo hiểm thân vỏ ô tô (14 case thực tế + mốc giá) | https://www.momo.vn/bao-hiem-o-to/than-vo |
| 2 | GTX Auto Care — Bảng giá sơn xe ô tô 2026 | https://gtxautocare.vn/son-xe-o-to |
| 3 | Gara Ô Tô Hoàng Gia Phát — Sơn ô tô Hải Phòng | https://otohoanggiaphat.com/son-o-to-tai-hai-phong/ |
| 4 | Thiên Minh Auto Safety — Giá sơn dặm xe ô tô Hà Nội | https://thienminh-autosafety.com/gia-son-dam-xe-o-to-ha-noi-het-bao-nhieu-tien/ |
| 5 | Gia Đình Auto — Bảng giá sơn xe ô tô | https://giadinhauto.com/tin-tuc/mau-son-xe-o-to.html |
| 6 | Ô tô Mỹ Đình THC — Bảng giá gò, hàn, phục hồi 8/2026 | https://otomydinhthc.com/bang-gia-dich-vu-go-han-phuc-hoi-o-to-moi-nhat/ |
| 7 | GoGara — Sửa móp xe ô tô, bảng chi phí 2026 | https://www.gogara.vn/blog/chi-phi-sua-mop-son-o-to |
| 8 | Thành Nam Auto — Bảng giá sửa chữa ô tô 2026 | https://www.thanhnamoto.com/tin-tuc/bang-gia-sua-chua-o-to-xuan-loc-dong-nai |
| 9 | 1CAR Gara — Bảng giá phụ tùng thân vỏ ô tô | https://1cargara.vn/phu-tung-than-vo-o-to.html |
| 10 | Cao Sang Auto — Chi phí thay kính chắn gió ô tô | https://caosangauto.com/kinh-chan-gio-xe-hoi-o-to-xe-tai-gia-tot-hang-chinh-hang/ |
| 11 | VietnamNet — Đèn pha ô tô ngày càng đắt đỏ | https://vietnamnet.vn/den-pha-o-to-ngay-cang-dat-do-chi-nut-thau-kinh-cung-co-the-phai-thay-ca-cum-2539244.html |
| 12 | Phúc Việt Auto — Làm đồng sơn ô tô | https://phucvietauto.com/lam-dong-son-o-to-gia-re-quan-12-uy-tin-chat-luong-hang-dau/ |

### 4.4. Giá dịch vụ AI (Gemini 3.8 Flash) — tra cứu web ngày 19/11/2026

| Hạng mục | Đến 31/12/2026 | Từ 01/01/2027 |
|---|---|---|
| Đầu vào | $0,75 / 1 triệu token | $1,50 / 1 triệu token |
| Đầu ra | $3,75 / 1 triệu token | $7,50 / 1 triệu token |
| Cache | $0,075 / 1 triệu token | $0,15 / 1 triệu token |

Nguồn: https://humanrank.ai/models/gemini-3-8-flash/ (đối chiếu tài liệu Google
ngày 15/11/2026); https://models.litellm.ai/models/gemini-3-8-flash (kiểm tra
18/11/2026); https://truestandard.ai/blog/gemini-3-8-flash-review (model card
Google đọc ngày 06/09/2026). Các nguồn độc lập khớp nhau. **Lưu ý:** giá có
thể thay đổi — trước khi báo giá cho khách, kiểm tra lại tại
https://ai.google.dev/gemini-api/docs/pricing.

---

## 5. Cấu hình vận hành

Nguồn: code: `src/main/resources/application.properties` (đọc ngày 19/11/2026);
`AppConfigService.java`.

| Cấu hình | Giá trị | Ghi chú |
|---|---|---|
| Cổng chạy | 8080 | `server.port=8080` |
| SQLite (database vận hành) | `./storage/aimotor.db` (1 file, app tự tạo 4 bảng khi khởi động) | Không cần cài thêm database; mở bằng DB Browser for SQLite |
| Gemini API key | Biến môi trường `GEMINI_API_KEY`, hoặc nhập ở tab Cài đặt (lưu SQLite, ưu tiên cao nhất). **Không gửi key qua chat, không commit key lên git** | `gemini.api.key=${GEMINI_API_KEY:}` |
| Model | `gemini-3.8-flash` | `gemini.model` |
| Ảnh tối thiểu/hồ sơ | 2 ảnh | `app.assessment.min-photos=2` |
| Kích thước file | Tối đa 10MB/ảnh; 100MB/request | `spring.servlet.multipart.*` |
| Resize trước khi gửi AI | Rộng tối đa 1024px, JPEG 85% | `app.image.max-width`, `app.image.jpeg-quality` |
| Ngưỡng độ nét | 80,0 (phương sai Laplacian) | `app.image.sharpness-threshold=80.0` |
| Mức miễn thường mặc định | 500.000đ/vụ (chỉnh ở tab Cài đặt, 0 = không miễn thường) | `AppConfigService.java` dòng 41 |
| Folder ảnh mẫu | `./storage/reference-damages` | `app.storage.reference-dir` |
| Folder ảnh gốc xe | `./storage/vehicles` | `app.storage.vehicle-dir` |
| Folder ảnh thẩm định | `./storage/claims` | `app.storage.claim-dir` |
| Nhật ký xác minh (SQLite) | `./storage/verification-audit.db` | `app.storage.verification-db` |

---

## 6. Bảo mật thông tin cá nhân

### 6.1. Che biển số và SĐT khi hiển thị

- Biển số giữ 3 ký tự đầu + 2 ký tự cuối, giữa thay bằng `*`
  (VD: `51H12345` → `51H***45`). SĐT giữ 3 số đầu + 2 số cuối
  (VD: `0901234567` → `090*****67`). Áp dụng ở **mọi màn hình**: kết quả thẩm
  định, lịch sử, danh sách xe, thẻ xác nhận xe gốc, kết quả tra cứu.
  Nguồn: code: `VehicleService.java` (`maskPlate`, `maskPhone`);
  `index.html` (`maskPlate`, `maskPhone`); `AssessmentController.java`
  (`ClaimStatusDto`); `VehicleController.java` (`maskedCopy`,
  `VehicleConfirmDto`).
- Biển số đầy đủ chỉ dùng nội bộ để đối chiếu danh tính, không hiển thị lại.
  Nguồn: code: `GeminiService.java` (quy tắc a2).

### 6.2. Không thu thập CCCD/CMND

- Hệ thống **không có trường nào thu thập CCCD/CMND**. Ảnh chụp giấy tờ tùy
  thân (CCCD/CMND, giấy phép lái xe, giấy đăng ký xe) bị AI từ chối ngay.
  Nguồn: kiểm tra toàn bộ code ngày 19/11/2026 (tìm `cccd|cmnd` trong `src/`:
  0 kết quả); code: `GeminiService.java` (quy tắc a2).
- Ảnh mẫu trong kho đã kiểm tra: không có biển số đọc được (biển số ngoài
  khung hình hoặc nguồn gốc đã làm mờ/trắng). Đã xem trực tiếp các ảnh mặt
  trước/sau dễ lộ biển số nhất (REF_05, REF_06, REF_08, REF_11, REF_13,
  REF_18, REF_19) ngày 19/11/2026.
  Nguồn: kiểm tra trực quan file `storage/reference-damages/*.jpg`.

### 6.3. Nhật ký xác minh danh tính lưu riêng vào SQLite

- File: `storage/verification-audit.db`, bảng `verification_log` (các cột:
  `created_at`, `source`, `license_plate`, `customer_phone`, `vehicle_type`,
  `plate_match`, `phone_match`, `type_match`, `passed`, `reason`, `claim_id`).
  Nguồn: code: `VerificationAuditService.java`; `application.properties`
  (`app.storage.verification-db`).
- Ghi mỗi lần xác minh: gửi thẩm định (`ASSESSMENT`), tra cứu bồi thường
  (`LOOKUP`), xác nhận xe gốc (`CONFIRM`) — kể cả lần sai (để phát hiện dò
  biển số).
  Nguồn: code: `ClaimAssessmentService.java`; `AssessmentController.java`;
  `VehicleController.java`.
- File này **chỉ ở máy chạy app**: không đưa lên database vận hành, không trả về qua API,
  không commit lên git (đã cho vào `.gitignore`).
  Nguồn: code: `.gitignore` (dòng `storage/verification-audit.db`).
- Xem lại bằng DB Browser for SQLite:
  `SELECT * FROM verification_log ORDER BY created_at DESC LIMIT 50;`
  Nguồn: `README.md` mục "Bảo mật thông tin cá nhân".

---

## 7. Lưu trữ dữ liệu

### 7.1. SQLite (database vận hành) — 4 bảng, 1 file (`storage/aimotor.db`)

| Bảng | Nội dung | Nguồn |
|---|---|---|
| `customer_vehicles` | Hồ sơ xe gốc (tên + ngày sinh chủ xe, biển số, SĐT, loại xe, hãng/dòng/năm, màu, đường dẫn ảnh gốc) | code: `CustomerVehicle.java` |
| `claim_assessments` | Hồ sơ thẩm định (mã hồ sơ, tên chủ xe, biển số, SĐT, kết quả AI, tổng tiền, miễn thường, thực nhận, đường dẫn ảnh thẩm định) | code: `ClaimAssessment.java` |
| `reference_damages` | Kho ảnh mẫu (mã REF_xx, bộ phận, mức độ, mô tả, giá chuẩn, đường dẫn ảnh) | code: `ReferenceDamage.java` |
| `app_config` | Cấu hình (Gemini API key, model, mức miễn thường) | code: `AppConfig.java` |

### 7.2. Folder ảnh trên đĩa (`storage/`)

| Folder | Nội dung | Có commit lên git không |
|---|---|---|
| `storage/reference-damages/` | Kho ảnh mẫu (47 ảnh JPG + 43 file metadata `.json`) | **Có** (ảnh mẫu là dữ liệu chung) |
| `storage/vehicles/<BIỂN-SỐ>/` | Ảnh gốc xe lúc mua bảo hiểm | **Không** (ảnh thật của khách) |
| `storage/claims/<MÃ-HỒ-SƠ>/` | Ảnh gốc khách gửi thẩm định | **Không** (ảnh thật của khách) |
| `storage/verification-audit.db` | Nhật ký xác minh (SQLite, mục 6.3) | **Không** |
| `storage/aimotor.db` | Database vận hành (4 bảng, mục 7.1) | **Không** (dữ liệu thật của khách) |

Nguồn: code: `application.properties` (`app.storage.*`); `.gitignore`;
`storage/vehicles/README.md`; `storage/claims/README.md`.

### 7.3. SQLite lưu giá (`db/`) — bản sao tra cứu chung nguồn dữ liệu

| File | Nội dung | Nguồn |
|---|---|---|
| `db/price-data.db` (45KB) | 6 bảng: `meta`, `price_sources` (12), `price_items` (30), `case_level_anchors` (4), `reference_cases` (15), `reference_photos` (43). Mở bằng DB Browser for SQLite | db: truy vấn trực tiếp ngày 19/11/2026 |
| `db/price-data.sql` | Schema + toàn bộ lệnh INSERT (tạo lại DB từ file này) | code: file |
| `db/build-price-db.py` | Sinh lại cả 2 file trên từ `price-table.json` + 14 case MoMo (+ REF_19). Chạy: `python3 db/build-price-db.py` | code: file |

**Lưu ý:** app khi chạy vẫn đọc giá từ `price-table.json`; SQLite là kho lưu
trữ/tra cứu chung nguồn dữ liệu, chưa nối vào app.
Nguồn: quyết định của người dùng ngày 19/11/2026 (chưa yêu cầu nối SQLite vào
app); `README.md` mục "Lưu trữ giá bằng SQLite".

---

## 8. API chính

Nguồn: code: `AssessmentController.java`, `VehicleController.java`,
`ReferenceController.java`, `ConfigController.java` (đọc ngày 19/11/2026).

| Endpoint | Mục đích |
|---|---|
| `POST /api/vehicles` (multipart) | Đăng ký mua bảo hiểm: tên + ngày sinh chủ xe, biển số, SĐT, loại xe, hãng/dòng/năm, màu, ảnh gốc |
| `GET /api/vehicles` | Danh sách xe đã đăng ký (biển số + SĐT đã che) |
| `GET /api/vehicles/{plate}` | 1 xe theo biển số (biển số + SĐT đã che) |
| `GET /api/vehicles/confirm?licensePlate&customerPhone&vehicleType` | Xác nhận xe gốc trước khi chụp ảnh hư hại |
| `GET /api/vehicles/photo/{vehicleId}/{index}` | Ảnh gốc của xe (theo id xe + số thứ tự, không lộ biển số trong URL) |
| `POST /api/assessments` (multipart) | Gửi hồ sơ thẩm định: biển số + SĐT + loại xe + nhiều ảnh thiệt hại |
| `POST /api/assessments/{id}/save-to-library` | Lưu hồ sơ đã thẩm định vào kho mẫu: mỗi vết → 1 ảnh mẫu (ảnh khách gửi + bộ phận + mức độ + giá ước tính + thông tin xe), để đối chiếu vụ tương tự sau này kể cả khác loại xe |
| `GET /api/assessments` | Lịch sử thẩm định |
| `GET /api/assessments/{id}` | Xem chi tiết 1 hồ sơ (nhận cả id nội bộ lẫn mã hồ sơ CLM-...): đầy đủ vết hư hại, tổng tiền, ghi chú kiểm chứng |
| `GET /api/assessments/plate/{plate}` | Lịch sử theo biển số |
| `GET /api/assessments/lookup?licensePlate&customerPhone&vehicleType` | Tra cứu tình trạng bồi thường |
| `GET /api/references` | Danh sách ảnh mẫu |
| `POST /api/references/scan` | Quét folder `storage/reference-damages/` nhập ảnh mới làm bản nháp |
| `PUT /api/references/{id}` | Bổ sung metadata cho ảnh mẫu |
| `POST /api/references/{id}/suggest-price` | Gợi ý giá cho ảnh mẫu mới theo bảng giá |
| `GET /api/config` | Cấu hình (key đã nhập chưa, model, mức miễn thường) |
| `POST /api/config/gemini-key` | Nhập Gemini API key |
| `POST /api/config/deductible` | Đặt mức miễn thường |

Tính năng quét folder: thả ảnh vào `storage/reference-damages/` → bấm "Quét
folder" hoặc app tự quét lúc khởi động → ảnh mới vào kho dạng nháp
(`needsMetadata=true`, mã REF_xx tự tăng), **nháp bị loại khỏi request
Gemini** (tránh giá 0đ). Kèm file `.json` cùng tên có `partName` +
`standardPriceVnd > 0` thì nhập luôn thành ảnh mẫu hoàn chỉnh. Chống trùng ảnh
bằng SHA-256 nội dung.
Nguồn: code: `ReferenceLibraryService.java` (`scanFolder`); `DataSeeder.java`
(tự quét lúc khởi động); `README.md`.

Tính năng lưu hồ sơ vào kho mẫu: sau khi AI thẩm định + định giá xong 1 hồ
sơ, bấm **"💾 Lưu vào kho mẫu"** ở kết quả thẩm định hoặc ở lịch sử → mỗi vết
hư hại thành 1 ảnh mẫu: ảnh khách gửi của hồ sơ (copy sang
`storage/reference-damages/`, tên `ref-<mã-hồ-sơ>-<n>.jpg`), bộ phận + mức độ
(mức độ lấy theo ảnh mẫu AI đối chiếu gần nhất, mặc định Mức 2) + giá ước tính
của vết đó + mô tả ghi rõ thông tin xe (loại xe, hãng, dòng, năm, màu) và mã
hồ sơ gốc. Vết nào giá 0 thì lưu dạng nháp, cần bổ sung giá sau. Ảnh đã có
trong kho (trùng SHA-256) thì bỏ qua, không lưu trùng. **Không thêm ảnh mẫu
thủ công từng cái trên giao diện nữa** — nguồn ảnh mẫu mới là hồ sơ thẩm định
của khách và folder quét.
Nguồn: code: `ReferenceLibraryService.java` (`saveClaimToLibrary`);
`AssessmentController.java` (`POST /api/assessments/{id}/save-to-library`).

---

## 9. Những gì CHƯA được kiểm chứng / giới hạn đã biết

1. **Chưa biên dịch, chưa chạy lần nào** (mục 0). Lần chạy đầu tiên trên máy
   người dùng (`mvn spring-boot:run`, cần JDK 17, Maven 3.9+, Gemini API key)
   là bước kiểm chứng còn thiếu. Không cần cài thêm database (SQLite là 1 file,
   app tự tạo). Nếu báo lỗi, gửi log để sửa code.
2. **Mức độ 14 case MoMo do agent tự gán**, không phải dữ liệu MoMo công bố
   (mục 0, 3.3).
3. **Giá trên ảnh mẫu là tổng tiền cả vụ**, không phải giá 1 vết (mục 3.3, 4.1).
   AI đã được cấu hình để không lấy số đó làm giá 1 vết (mục 2, Bước 6).
4. **4 ảnh seed REF_01–04 là ảnh AI tạo**, chưa phải ảnh thật, giá là giá mẫu
   (mục 3.2). Người dùng nên thay bằng ảnh thật + giá thật.
5. **Phí bảo hiểm/năm ≠ chi phí sửa chữa.** Phí bảo hiểm chỉ để tham khảo;
   AI định giá sửa chữa theo bảng giá mục 4, không theo phí bảo hiểm.
6. **Giá Gemini có hạn** (tăng gấp đôi từ 01/01/2027, mục 4.4). Kiểm tra lại
   trước khi báo giá.
7. **App chưa có đăng nhập/phân quyền.** Mọi API hiện mở cho bất kỳ ai truy
   cập được máy chạy app (localhost:8080). Đây là giới hạn đã biết, chưa phải
   lỗi — hệ thống thiết kế để chạy nội bộ.
8. **SQLite giá (`db/price-data.db`) chưa nối vào app** — app đọc giá từ
   `price-table.json` (mục 7.3).

---

## Phụ lục — Danh mục nguồn đầy đủ

### A. Code trong repo (đọc ngày 19/11/2026, commit `febc670`)

- `pom.xml` — stack, phiên bản.
- `src/main/resources/application.properties` — cổng, giới hạn ảnh, model, folder.
- `src/main/resources/price-table.json` — bảng giá 30 mức + 12 nguồn + 4 mốc.
- `src/main/resources/static/index.html` — giao diện 5 tab + toàn bộ luồng
  hiển thị (che biển số/SĐT, xác nhận xe gốc, tra cứu).
- `src/main/java/com/aimotor/claim/service/GeminiService.java` — gọi API
  Gemini + system prompt (validation gate, quy tắc định giá).
- `src/main/java/com/aimotor/claim/service/PriceTableService.java` — nạp bảng
  giá vào prompt.
- `src/main/java/com/aimotor/claim/service/ClaimAssessmentService.java` — luồng
  thẩm định (xác minh → kiểm tra ảnh → lưu ảnh → gọi AI → tính tiền).
- `src/main/java/com/aimotor/claim/service/VehicleService.java` — đăng ký xe,
  xác minh danh tính, che biển số/SĐT.
- `src/main/java/com/aimotor/claim/service/VerificationAuditService.java` —
  nhật ký xác minh SQLite.
- `src/main/java/com/aimotor/claim/service/ReferenceLibraryService.java` —
  quét folder, chống trùng SHA-256, gợi ý giá.
- `src/main/java/com/aimotor/claim/service/AppConfigService.java` — key Gemini
  + mức miễn thường mặc định 500.000đ.
- `src/main/java/com/aimotor/claim/service/ImagePreprocessingService.java`,
  `ImageSharpnessService.java` — resize ảnh, đo độ nét.
- `src/main/java/com/aimotor/claim/controller/` — `AssessmentController.java`,
  `VehicleController.java`, `ReferenceController.java`, `ConfigController.java`.
- `src/main/java/com/aimotor/claim/model/` — `CustomerVehicle.java`,
  `ClaimAssessment.java`, `ReferenceDamage.java`, `AppConfig.java`.
- `src/main/java/com/aimotor/claim/seed/DataSeeder.java` — 47 ảnh mẫu seed +
  tự quét folder lúc khởi động.
- `src/main/java/com/aimotor/claim/dto/GeminiAssessmentResult.java` — cấu trúc
  JSON kết quả AI trả về.
- `db/build-price-db.py`, `db/price-data.sql`, `db/price-data.db` — SQLite giá.
- `.gitignore` — file nào không commit (ảnh khách, nhật ký xác minh).

### B. Trang web đã đọc (ngày 19/11/2026)

- MoMo — Bảo hiểm thân vỏ ô tô (14 case thực tế):
  https://www.momo.vn/bao-hiem-o-to/than-vo
- Tuổi Trẻ — Mercedes GLC200 bị cào xước (REF_19):
  https://tuoitre.vn/chu-xe-mercedes-bi-cao-xuoc-doi-gan-nua-ti-vi-sao-toa-chi-cho-nhan-671000-dong-100261009094444438.htm
- Ô tô Mỹ Đình THC — báo giá sửa BMW 320i (đã kiểm tra, không lấy):
  https://otomydinhthc.com/chi-phi-sua-chua-xe-bmw-320i/
- VietnamNet — Hyundai Accent bị cây đổ (đã kiểm tra, không lấy):
  https://vietnamnet.vn/cay-xanh-truong-hoc-do-de-len-o-to-chu-xe-bi-bac-yeu-cau-boi-thuong-71-trieu-2565305.html
- 12 nguồn giá: xem bảng ở mục 4.3 (URL đầy đủ).
- Giá Gemini 3.8 Flash: https://humanrank.ai/models/gemini-3-8-flash/,
  https://models.litellm.ai/models/gemini-3-8-flash,
  https://apidog.com/blog/what-is-gemini-3-8-flash/,
  https://truestandard.ai/blog/gemini-3-8-flash-review
  (tra cứu lại ngày 19/11/2026; các nguồn khớp nhau).

### C. Câu lệnh đã dùng để kiểm chứng số liệu (chạy ngày 19/11/2026)

- `ls storage/reference-damages/*.jpg | wc -l` → 47
- `ls storage/reference-damages/*.json | wc -l` → 43
- `grep -c "new ReferenceDamage" src/main/java/com/aimotor/claim/seed/DataSeeder.java` → 47
- `SELECT COUNT(*) FROM price_sources` → 12; `price_items` → 30;
  `case_level_anchors` → 4; `reference_cases` → 15; `reference_photos` → 43
- `SELECT SUM(compensation_vnd) FROM reference_cases` → 356500000
- `SELECT severity, COUNT(*) FROM reference_cases GROUP BY severity` →
  M1: 3, M2: 7, M3: 5
- `SELECT DISTINCT insurer FROM reference_cases` → 8 hãng
- `git ls-remote origin HEAD` = `git rev-parse HEAD` = `febc670`
