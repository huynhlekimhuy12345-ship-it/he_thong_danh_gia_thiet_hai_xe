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

## API chính

| Method | Endpoint | Mô tả |
|---|---|---|
| GET | `/api/references` | Liệt kê kho ảnh mẫu |
| POST | `/api/references` | Thêm ảnh mẫu (multipart: `image` + các trường metadata) |
| POST | `/api/references/scan` | Quét folder `storage/reference-damages/` — ảnh mới vào kho dạng nháp |
| PUT | `/api/references/{id}` | Bổ sung / sửa metadata ảnh mẫu (bộ phận, mức độ, giá chuẩn) |
| GET | `/api/references/{id}/image` | Xem ảnh mẫu |
| DELETE | `/api/references/{id}` | Xóa ảnh mẫu |
| POST | `/api/vehicles` | Đăng ký hồ sơ xe + ảnh gốc (multipart) |
| GET | `/api/vehicles` | Liệt kê xe đã đăng ký |
| GET | `/api/vehicles/{plate}` | Tra xe theo biển số |
| POST | `/api/assessments` | Gửi hồ sơ thẩm định (multipart: `licensePlate`, `customerPhone`, `vehicleType`, `photos[]`) |
| GET | `/api/assessments` | Lịch sử thẩm định |
| GET | `/api/assessments/plate/{plate}` | Lịch sử theo biển số |
| GET / POST | `/api/config`, `/api/config/gemini-key` | Xem / lưu Gemini API key |

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
  "requiresHumanReview": false,
  "humanReviewReason": ""
}
```

`validation.status` có 3 giá trị: `OK` (đủ điều kiện), `NEEDS_MORE_INFO`
(thiếu/mờ/sai ảnh — kèm danh sách cần bổ sung), `REJECTED` (không phải xe / sai xe).

## Lưu ý quan trọng

- **Kết quả AI chỉ là mức đề xuất.** Giám định viên chốt cuối cùng, nhất là khi
  `requiresHumanReview = true` hoặc độ tin cậy thấp.
- **Bảo mật:** API key không bao giờ commit lên git. Ảnh khách hàng được gửi lên
  Google Gemini API để phân tích — cần có sự đồng ý của khách hàng trong quy trình thật.
- **Bộ ảnh tối thiểu** để AI đánh giá tốt: 4 góc ngoại thất + khoang trước + khoang sau
  + cốp + biển số/VIN + cận cảnh từng vết. Thiếu ảnh → AI trả về danh sách cần bổ sung
  thay vì đoán mò (nguyên tắc: thiếu ảnh = thiếu căn cứ).
- **Chưa biên dịch trên máy agent** (máy agent không có Java/Maven). Code viết theo đúng
  chuẩn Spring Boot 3.3.5 / Java 17; nếu `mvn spring-boot:run` báo lỗi, copy toàn bộ
  log lỗi gửi lại để sửa.
