# AI Motor Claim Assessor

Hệ thống thẩm định thiệt hại xe cơ giới tự động bằng AI — tải nhiều ảnh xe bị hư hại,
đối chiếu với **Kho ảnh mẫu dự trữ** bằng Google Gemini 3.8 Flash, đề xuất mức bồi thường hợp lý.

## Luồng nghiệp vụ

```
1. Đăng ký hồ sơ xe gốc (tab Hồ sơ xe)
   Biển số + SĐT + loại xe + màu xe + ảnh gốc lúc mua bảo hiểm
        ↓
2. Xe bị thiệt hại → tải nhiều ảnh lên (tab Thẩm định)
        ↓
3. Backend xác minh danh tính xe: đúng biển số + đúng SĐT + đúng loại xe
   → SAI 1 trong 3 = từ chối hồ sơ ngay, không gọi AI (tiết kiệm token, chống gian lận)
        ↓
4. Kiểm tra ảnh đầu vào: đủ số lượng, đúng định dạng, đo độ nét (Laplacian)
        ↓
5. Gọi Gemini 1 request duy nhất gồm:
   [ảnh mẫu + nhãn] + [ảnh gốc của xe] + [ảnh khách] + system prompt
   - Cổng kiểm tra (validation gate): AI PHẢN BIỆN nếu ảnh mờ, không phải xe,
     thiếu góc, sai xe → trả NEEDS_MORE_INFO / REJECTED + liệt kê cần bổ sung
   - Chống trùng lặp: 1 vết ở nhiều góc chỉ tính tiền 1 lần
   - Đối chiếu từng vết với ảnh mẫu gần nhất → matchedReferenceId
   - Quá nặng / ngoài phạm vi mẫu → requiresHumanReview = true
        ↓
6. Kết quả JSON → lưu MongoDB → hiển thị + giám định viên chốt cuối
```

## Yêu cầu

| Thứ | Phiên bản / ghi chú |
|---|---|
| JDK | 17+ |
| Maven | 3.9+ |
| MongoDB | local (`mongod`) hoặc Atlas — URI qua biến `MONGODB_URI` |
| Gemini API key | Lấy tại https://aistudio.google.com/apikey (miễn phí có hạn mức) |

## Chạy trên máy Windows (Antigravity)

Mở terminal tại thư mục dự án rồi chạy từng lệnh (copy nguyên khối):

```powershell
# 1. Vào thư mục dự án (đổi đường dẫn theo máy của bạn)
cd "C:\Users\Huynh Huy\Downloads\he_thong_danh_gia_thiet_hai_xe"

# 2. Cấu hình Gemini API key (CHỈ 1 lần, key không commit lên git)
$env:GEMINI_API_KEY="AIza...key-cua-ban..."

# 3. Chạy MongoDB local (nếu chưa có thì cài MongoDB Community rồi chạy)
#    Hoặc đặt biến MONGODB_URI trỏ sang Atlas:
#    $env:MONGODB_URI="mongodb+srv://<user>:<pass>@<cluster>/aimotor_claim"

# 4. Build + chạy (lần đầu tải dependency hơi lâu)
mvn spring-boot:run
```

Mở trình duyệt: **http://localhost:8080**

- Lần chạy đầu, hệ thống tự seed **4 ảnh mẫu** (REF_01–REF_04) vào Kho ảnh mẫu.
- API key cũng có thể nhập ở tab **Cài đặt** trên giao diện (lưu vào MongoDB, ưu tiên hơn biến môi trường).

## Cấu trúc dự án

```
ai-motor-claim-assessor/
├── pom.xml                                  Spring Boot 3.3.5, Java 17, MongoDB, Thumbnailator
├── src/main/java/com/aimotor/claim/
│   ├── AiMotorClaimAssessorApplication.java
│   ├── model/          ReferenceDamage, CustomerVehicle, ClaimAssessment, AppConfig
│   ├── repository/     MongoRepository cho 4 collection
│   ├── service/
│   │   ├── GeminiService.java            Gọi Gemini 3.8 Flash, system prompt + responseSchema
│   │   ├── ClaimAssessmentService.java   Điều phối: xác minh xe → kiểm tra ảnh → gọi AI → lưu
│   │   ├── VehicleService.java           Đăng ký xe + chuẩn hóa biển số/SĐT
│   │   ├── ReferenceLibraryService.java  Kho ảnh mẫu
│   │   ├── ImagePreprocessingService.java Resize 1024px + JPG 85% + Base64 (Thumbnailator)
│   │   ├── ImageSharpnessService.java   Đo độ mờ bằng phương sai Laplacian
│   │   └── AppConfigService.java        API key lưu MongoDB
│   ├── controller/     Reference / Vehicle / Assessment / Config + GlobalExceptionHandler
│   ├── dto/            GeminiAssessmentResult (JSON Gemini trả về)
│   └── seed/DataSeeder.java   Seed 4 ảnh mẫu + tạo thư mục storage
├── src/main/resources/
│   ├── application.properties
│   └── static/index.html        Giao diện Dark Tech (nền tối, xanh ngọc #10B981, glassmorphism)
└── storage/reference-damages/   4 ảnh mẫu seed (AI tạo để test ngay)
    ├── ref-01-bumper-scratch.jpg   REF_01 · Cản trước · Mức 1 · 1.500.000 ₫
    ├── ref-02-door-dent.jpg        REF_02 · Cửa sau bên phụ · Mức 2 · 4.500.000 ₫
    ├── ref-03-headlight-broken.jpg REF_03 · Đèn pha trái · Mức 3 · 3.800.000 ₫
    └── ref-04-hood-crushed.jpg     REF_04 · Nắp capo · Mức 3 · 12.000.000 ₫
```

> ⚠️ 4 ảnh mẫu là ảnh AI tạo ra để **test luồng ngay** — khi chạy thật, hãy thay bằng ảnh
> thiệt hại thật (kèm giá sửa chữa thực tế) qua tab **Kho ảnh mẫu**.

## Thêm ảnh mẫu bằng cách thả file vào folder

Ngoài việc upload qua giao diện, có thể copy trực tiếp file ảnh (JPG/PNG/WebP) vào
thư mục `storage/reference-damages/` rồi bấm nút **🔄 Quét folder ảnh mẫu** ở tab
**Kho ảnh mẫu** (app cũng tự quét mỗi lần khởi động). Ảnh mới được nhận vào kho ở
dạng **nháp** với mã `REF_xx` tự tăng, gắn cờ `needsMetadata = true` — ảnh nháp
**tạm thời không được gửi cho AI** (tránh định giá 0 đồng). Bấm **✏️ Sửa** trên thẻ
ảnh để bổ sung bộ phận + mức độ + giá chuẩn, lưu xong ảnh mới được đưa vào thẩm định.

**Nhập đủ giá ngay bằng file metadata kèm theo:** đặt cạnh file ảnh một file
JSON cùng tên (VD: `ref-05-seltos-thuy-kich.jpg` + `ref-05-seltos-thuy-kich.json`)
với nội dung:

```json
{
  "partName": "Động cơ (thủy kích)",
  "severityLevel": 3,
  "severityLabel": "Mức 3: Vỡ/Biến dạng nặng",
  "description": "Xe bị ngập nước, hư hỏng động cơ.",
  "standardPriceVnd": 42000000
}
```

Lúc quét, ảnh có file metadata hợp lệ (đủ `partName` + `standardPriceVnd > 0`)
sẽ vào thẳng kho chính thức, không cần bổ sung tay. 14 case MoMo (REF_05–REF_18,
42 ảnh) đã kèm sẵn file `.json` theo cách này.

## API chính

| Method | Endpoint | Mô tả |
|---|---|---|
| GET | `/api/references` | Liệt kê kho ảnh mẫu |
| POST | `/api/references` | Thêm ảnh mẫu (multipart: `image` + các trường metadata) |
| POST | `/api/references/scan` | Quét folder `storage/reference-damages/` — ảnh mới vào kho dạng nháp |
| PUT | `/api/references/{id}` | Bổ sung / sửa metadata ảnh mẫu (bộ phận, mức độ, giá chuẩn) |
| POST | `/api/references/{id}/suggest-price` | Gemini gợi ý giá chuẩn cho ảnh mẫu mới theo ảnh mẫu cũ |
| GET | `/api/references/{id}/image` | Xem ảnh mẫu |
| DELETE | `/api/references/{id}` | Xóa ảnh mẫu |
| POST | `/api/vehicles` | Đăng ký hồ sơ xe + ảnh gốc (multipart) |
| GET | `/api/vehicles` | Liệt kê xe đã đăng ký |
| GET | `/api/vehicles/{plate}` | Tra xe theo biển số |
| POST | `/api/assessments` | Gửi hồ sơ thẩm định (multipart: `licensePlate`, `customerPhone`, `vehicleType`, `photos[]`) |
| GET | `/api/assessments` | Lịch sử thẩm định |
| GET | `/api/assessments/plate/{plate}` | Lịch sử theo biển số |
| GET / POST | `/api/config`, `/api/config/gemini-key` | Xem / lưu Gemini API key |
| GET / POST | `/api/config/deductible` | Xem / lưu mức miễn thường (VND/vụ) |

## JSON kết quả thẩm định (AI trả về)

```json
{
  "validation": {
    "status": "OK",
    "issues": [],
    "missingPhotos": []
  },
  "assessmentSummary": "Tóm tắt tổng quan tình trạng thiệt hại của chiếc xe",
  "damages": [
    {
      "partName": "Cản trước",
      "observedCondition": "Xước dài nhẹ trên sơn cản trước",
      "matchedReferenceId": "REF_01",
      "referenceComparisonNote": "Mức độ tương đương REF_01: cùng xước sơn, không móp",
      "estimatedCostVnd": 1500000,
      "confidenceScore": 0.92
    }
  ],
  "totalCompensationVnd": 1500000,
  "deductibleVnd": 500000,
  "netCompensationVnd": 1000000,
  "requiresHumanReview": false,
  "humanReviewReason": ""
}
```

`totalCompensationVnd` là tổng thiệt hại ước tính; hệ thống tự trừ mức miễn
thường (`deductibleVnd`, mặc định 500.000đ/vụ) để ra `netCompensationVnd` —
số bồi thường thực nhận đề xuất. Xem mục Mức giá tham chiếu (MoMo) bên dưới.

`validation.status` có 3 giá trị: `OK` (đủ điều kiện), `NEEDS_MORE_INFO`
(thiếu/mờ/sai ảnh — kèm danh sách cần bổ sung), `REJECTED` (không phải xe / sai xe).

## Lưu ý quan trọng

- **Kết quả AI chỉ là mức đề xuất.** Giám định viên chốt cuối cùng, nhất là khi
  `requiresHumanReview = true` hoặc độ tin cậy thấp.
- **Bảo mật:** API key không bao giờ commit lên git. Ảnh khách hàng được gửi lên
  Google Gemini API để phân tích — cần có sự đồng ý của khách hàng trong quy trình thật.
- **Bộ ảnh tối thiểu** để AI đánh giá tốt: 4 góc ngoại thất + cốp + biển số/VIN
  + cận cảnh từng vết. **Không cần chụp nội thất.** Thiếu ảnh → AI trả về danh sách
  cần bổ sung thay vì đoán mò (nguyên tắc: thiếu ảnh = thiếu căn cứ).
- **Chưa biên dịch trên máy agent** (máy agent không có Java/Maven). Code viết theo đúng
  chuẩn Spring Boot 3.3.5 / Java 17; nếu `mvn spring-boot:run` báo lỗi, copy toàn bộ
  log lỗi gửi lại để sửa.

## Mức giá tham chiếu (nguồn MoMo — Bảo hiểm thân vỏ ô tô)

Nguồn: https://www.momo.vn/bao-hiem-o-to/than-vo (truy cập 19/11/2026).

## Bảng giá tham chiếu — AI định giá theo bộ phận × mức độ, KHÔNG theo loại xe

Giá bồi thường **không phụ thuộc hãng/model xe**: cùng bộ phận + cùng mức độ thì
cùng một giá, dù là Seltos hay Everest. Căn cứ định giá duy nhất của AI là
**`src/main/resources/price-table.json`** — bảng giá tổng hợp từ **12 nguồn uy
tín** (MoMo, GTX Auto Care, Hoàng Gia Phát, Thiên Minh, Gia Đình Auto, Ô tô Mỹ
Đình THC, GoGara, Thành Nam Auto, 1CAR Gara, Cao Sang Auto, VietnamNet, Phúc
Việt Auto), gồm **30 mức giá cho 11 bộ phận** (cản, cửa, capo, cốp/đuôi xe, tai
xe, đèn pha, đèn hậu, kính chắn gió, gương, khung sườn, động cơ thủy kích) × 3
mức độ (sơn dặm / gò sơn / thay mới), kèm 4 mốc kiểm tra tổng (va quẹt nhỏ
2–3tr, va chạm nặng 30–80tr, thủy kích 20–60tr, sơn toàn xe 12–35tr). Mỗi mức
ghi rõ độ tin cậy (`cao` = nhiều nguồn khớp nhau, `trung bình` = ước tính theo
mặt bằng chung, cần thẩm định viên xác nhận).

Cách AI dùng bảng này (đã cấu hình trong System Prompt của `GeminiService`,
mục 6 — đọc kỹ trước khi sửa):

1. **Ảnh mẫu REF_xx chỉ để nhận diện** bộ phận + mức độ (so sánh hình ảnh).
   Giá ghi trên ảnh mẫu là **tổng tiền cả vụ** của case MoMo, KHÔNG được lấy
   làm giá của 1 vết.
2. Mỗi vết: xác định bộ phận + mức độ → tra bảng → chọn **mức giá hợp lí
   nhất trong khoảng** (gần min nếu nhẹ, giữa khoảng nếu trung bình, gần max
   nếu nặng), ghi rõ cách chọn trong `referenceComparisonNote`.
3. **Không nhân hệ số theo xe**: xe sang/nhập khẩu, đèn LED/laser/adaptive,
   cảm biến ADAS, khung biến dạng nặng mà vượt bảng thì giải thích và đặt
   `requiresHumanReview = true`, không tự bịa hệ số.
4. Tính năng **Gợi ý giá** (ảnh mẫu mới) cũng dùng bảng này: ảnh mẫu cũ chỉ để
   xác định bộ phận + mức độ, giá lấy từ bảng.

Muốn cập nhật giá: sửa `price-table.json` rồi khởi động lại app là có hiệu lực
(`PriceTableService` nạp file lúc chạy; nếu file lỗi thì tự dùng bảng rút gọn
để hệ thống vẫn chạy). `README` này liệt kê đầy đủ 12 nguồn + URL trong file
JSON (mục `sources`).

## Lưu trữ giá bằng SQLite (`db/`)

Toàn bộ thông tin giá (bảng giá, nguồn, mốc tổng, 14 case MoMo, 42 ảnh mẫu)
còn được lưu trong SQLite để tra cứu, đối chiếu và dùng lại ở nơi khác:

| File | Để làm gì |
|---|---|
| `db/price-data.db` | File SQLite dùng ngay — mở bằng **DB Browser for SQLite** (miễn phí) hoặc `sqlite3 price-data.db` |
| `db/price-data.sql` | Schema + toàn bộ INSERT — xem/sửa bằng tay, nạp lại bằng `sqlite3 price-data.db < price-data.sql` |
| `db/build-price-db.py` | Script sinh lại cả 2 file trên từ `price-table.json` + 14 case MoMo: `python3 db/build-price-db.py` |

Các bảng:

- `meta` — phiên bản, ngày cập nhật, tiền tệ, phạm vi áp dụng.
- `price_sources` — 12 nguồn uy tín (tên, link, dùng cho mục nào).
- `price_items` — 30 mức giá: 11 bộ phận × 3 mức độ, kèm `confidence`
  (`cao` = nhiều nguồn khớp nhau, `trung bình` = ước tính theo mặt bằng chung).
- `case_level_anchors` — 4 mốc kiểm tra tổng (va quẹt nhỏ, va chạm nặng,
  thủy kích, sơn toàn xe).
- `reference_cases` — 14 case thực tế MoMo (hãng bảo hiểm, xe, bộ phận, mức
  độ, mô tả, **tổng tiền bồi thường cả vụ**, phí bảo hiểm/năm).
- `reference_photos` — 42 ảnh mẫu (3 ảnh/case, mã `REF_xx` / `REF_xxb` /
  `REF_xxc`, trỏ tới file thật trong `storage/reference-damages/`).

Ví dụ tra cứu:

```sql
-- Giá sơn/gò/thay cản trước theo mức độ
SELECT severity, action, min_vnd, max_vnd FROM price_items
WHERE part = 'Cản trước / Cản sau' ORDER BY severity;

-- Case bồi thường cao nhất
SELECT ref_id, vehicle, part, compensation_vnd FROM reference_cases
ORDER BY compensation_vnd DESC LIMIT 5;

-- 3 ảnh của 1 case
SELECT ref_id, file_name FROM reference_photos WHERE case_ref = 'REF_05';
```

Lưu ý: app hiện vẫn đọc giá từ `price-table.json` lúc chạy (đơn giản, sửa là
xong); file SQLite này là **kho lưu trữ/tra cứu** dùng chung một nguồn dữ liệu,
không phải database vận hành của app. Nếu muốn app đọc giá thẳng từ SQLite
thay cho JSON thì bảo em nối tiếp.

## Lưu trữ ảnh xe (`storage/`)

Ảnh xe chia 3 folder riêng, mỗi folder có file `README.md` mô tả:

| Folder | Chứa gì | Ai ghi | Có commit không |
|---|---|---|---|
| `storage/reference-damages/` | Ảnh mẫu đối chiếu (46 ảnh: 4 ảnh test + 42 ảnh case MoMo thật) + file `.json` metadata từng ảnh | Agent chuẩn bị sẵn, user thả thêm ảnh vào rồi bấm **Quét folder** | **Có** — đây là dữ liệu mẫu của hệ thống |
| `storage/vehicles/<BIỂN-SỐ>/` | Ảnh gốc của xe lúc mua bảo hiểm (xe chưa hư hại), để đối chiếu vết cũ/mới | App tự lưu khi đăng ký hồ sơ xe | **Không** — dữ liệu thật của khách, chỉ giữ `README.md` |
| `storage/claims/<MÃ-HỒ-SƠ>/` | Ảnh gốc khách gửi khi yêu cầu thẩm định (đúng bộ ảnh của hồ sơ đó) | App tự lưu sau bước xác minh danh tính, đường dẫn ghi trong `claimPhotoPaths` của hồ sơ | **Không** — dữ liệu thật của khách, chỉ giữ `README.md` |

App tự tạo đủ 3 folder khi khởi động (`DataSeeder`). Ảnh gửi cho AI chỉ là
bản resize (tối đa 1024px); ảnh gốc chất lượng đầy đủ luôn nằm trong 2 folder
`vehicles/` và `claims/` để đối soát lại sau này. Hồ sơ bị từ chối ở bước xác
minh danh tính thì ảnh không được lưu.

### 14 case thực tế đã đưa vào kho ảnh mẫu (REF_05–REF_18, 42 ảnh)

Trang MoMo có mục **"Một số trường hợp thực tế"** — mỗi case gồm **3 ảnh** thiệt hại
thật (nhiều góc khác nhau của cùng 1 vụ) + phí bảo hiểm + phí bồi thường. Cả
**42 ảnh** đã được tải về `storage/reference-damages/` (kèm file metadata `.json`
cùng tên để quét folder nhận đủ giá ngay) và seed sẵn trong `DataSeeder`.
3 ảnh của 1 case chia sẻ cùng mức giá bồi thường, đánh mã `REF_xx` (ảnh chính),
`REF_xxb` (ảnh 2), `REF_xxc` (ảnh 3) — nhiều góc giúp AI đối chiếu đúng vết hơn,
và vì mỗi vết chỉ khớp với 1 ảnh mẫu nên tổng tiền không bị tính trùng.

### Case ngoài MoMo (REF_19)

- **REF_19 · Mercedes-Benz GLC200 (MIC + OPES) · "Thân xe (trầy xước nhiều
  vết)" · Mức 1 · 8.200.000đ** — case thực tế Tuổi Trẻ (10/2026): xe bị cào
  xước 7 vị trí trên thân xe. Chi phí sửa chữa thực tế 8.200.000đ (chủ xe trả
  2.000.000đ, bảo hiểm MIC + OPES trả 6.200.000đ); giám định thiệt hại
  6.862.000đ. Báo giá hãng 127,6 triệu và yêu cầu bồi thường 470 triệu đã bị
  tòa bác — không dùng 2 số đó. 7 vị trí ≈ 1,17 triệu/vị trí, khớp khoảng sơn
  dặm 1 chi tiết trong bảng giá tham chiếu. Ảnh là cận cảnh vết xước trên hông
  sau (không lấy 2 ảnh còn lại của bài báo vì người chiếm gần hết khung hình,
  vết xước mờ — ảnh mẫu phải thấy rõ vết thì AI mới đối chiếu đúng).

Đã kiểm tra nhưng **không đưa vào kho**: bài báo giá sửa BMW 320i bị tai nạn
của Gara Mỹ Đình THC (tổng 41,2tr phục hồi / 65,1tr thay mới, có báo giá chi
tiết từng món như cản trước 7,5tr) — ảnh duy nhất trong bài là xe đang sửa
trong gara, capo mở, **không thấy vết hư hại** nên không làm ảnh mẫu được; và
vụ Hyundai Accent bị cây đổ (VietnamNet, báo giá 116,5tr) — ảnh là ảnh minh
họa, số tiền đang tranh chấp tại tòa. Nguyên tắc: chỉ ảnh nào thấy rõ vết hư
hại thật + có số tiền sửa/bồi thường xác thực mới vào kho; ảnh minh họa hoặc
ảnh không thấy vết sẽ làm giảm độ chính xác chứ không tăng.

| Mã | Xe / vụ việc | Vùng hư hại chính | Mức | Bồi thường |
|---|---|---|---|---|
| REF_05 | Kia Seltos 2024 — ngập nước, hư động cơ | Động cơ (thủy kích) | 3 | 42.000.000đ |
| REF_06 | Kia Carnival 2024 — lùi xe bãi đỗ | Đuôi xe (cản sau) | 3 | 32.000.000đ |
| REF_07 | Honda HR-V 2024 — va xe máy, trầy + móp nhẹ | Thân xe | 2 | 14.000.000đ |
| REF_08 | Mitsubishi Xpander 2024 — va xe máy, vỡ đèn pha phải | Đầu xe + đèn pha | 3 | 13.200.000đ |
| REF_09 | Toyota Fortuner 2022 — phá hoại đỗ qua đêm | Toàn thân + gương | 2 | 22.500.000đ |
| REF_10 | Toyota Camry 2023 — chuyển làn va xe khác | Cửa xe | 2 | 25.000.000đ |
| REF_11 | Honda CR-V 2023 — lùi bãi đỗ | Cản sau + đèn hậu | 2 | 28.000.000đ |
| REF_12 | VinFast VF5 2024 — va trụ bê tông | Đầu xe | 2 | 8.500.000đ |
| REF_13 | VinFast VF9 2024 — va mạnh quốc lộ | Đầu xe + thân xe | 3 | 58.000.000đ |
| REF_14 | Toyota Innova 2021 — rơi vật, móp capo | Nắp capo | 2 | 12.000.000đ |
| REF_15 | Mazda 2 2022 — trầy nhẹ vỉa hè | Thân xe | 1 | 4.500.000đ |
| REF_16 | VinFast VF3 2024 — cản trước bong sơn | Cản trước | 2 | 6.800.000đ |
| REF_17 | Kia Cerato 2022 — trầy xước TTTM | Thân xe | 1 | 6.800.000đ |
| REF_18 | Ford Everest 2022 — va mạnh cao tốc | Đầu xe | 3 | 75.000.000đ |

> ⚠️ Lưu ý quan trọng: "phí bồi thường" của mỗi case là **TỔNG tiền cả vụ**
> (toàn bộ thiệt hại của vụ đó), được gán cho vùng hư hại chính — không phải
> bảng giá sửa chữa theo từng món. VD: Seltos 42tr là toàn bộ chi phí sửa động
> cơ ngập nước; Fortuner 22,5tr là sơn lại toàn thân + thay gương. AI đối chiếu
> vết hư hại với case gần nhất và đề xuất theo tổng tiền case đó; giám định viên
> vẫn chốt cuối cùng.

### Mức miễn thường + neo giá thị trường

| Hạng mục | Giá trị tham chiếu | Dùng trong app |
|---|---|---|
| Mức miễn thường | 500.000–2.000.000đ/vụ | Ô **Mức miễn thường** ở tab Cài đặt (mặc định 500.000đ/vụ, đặt 0 nếu hợp đồng không có). Hệ thống tự trừ khỏi tổng thiệt hại: thực nhận = max(0, tổng − miễn thường). VD trên trang: thiệt hại 8.000.000đ, miễn thường 500.000đ → hãng trả 7.500.000đ |
| Va quẹt nhỏ | 2–3 triệu đồng | Neo giá trong system prompt của Gemini (vượt xa khoảng thì phải giải thích + chuyển giám định viên) |
| Va chạm nặng | 30–80 triệu đồng | Như trên |
| Thủy kích (ngập nước động cơ) | 20–60 triệu đồng (VD thực tế: Kia Seltos 2024 — 42.000.000đ) | Như trên |
| Phí bảo hiểm thân vỏ | 1,4%–2,0%/năm giá trị xe (VD: xe 800tr → phí từ 8tr/năm) | Chỉ để tham khảo, KHÔNG dùng để định giá thiệt hại |

> 2 ảnh infographic trên trang MoMo (bảng phí theo giá trị xe, công thức tính
> phí) là **phí bảo hiểm/năm, không phải giá sửa xe** — nên KHÔNG đưa vào kho
> ảnh mẫu.

## Gợi ý giá cho ảnh mẫu mới

Ảnh mẫu mới (thả vào folder rồi quét, hoặc upload) chưa có giá chuẩn: vào tab
**Kho ảnh mẫu** → bấm **✏️ Sửa** → **🤖 Gợi ý giá theo ảnh cũ**. Gemini sẽ so
ảnh mới với các ảnh mẫu cũ đã có giá, trả về mẫu tương đương nhất + giá đề xuất
+ độ giống nhau — kiểm tra rồi bấm **Lưu** mới đưa vào kho chính thức. Nếu không
có ảnh cũ nào tương đương, hệ thống báo rõ và KHÔNG đoán giá. Ảnh trùng nội dung
(cùng ảnh, khác tên file) sẽ bị chặn ngay lúc quét/upload nhờ mã SHA-256.
