# Folder ảnh xe gốc (hồ sơ xe)

Folder này lưu **ảnh gốc của xe lúc mua bảo hiểm** (ảnh xe chưa bị hư hại),
dùng để đối chiếu khi thẩm định: vết nào đã có sẵn trong ảnh gốc thì KHÔNG
phải thiệt hại mới.

## Cấu trúc

```
storage/vehicles/<BIỂN-SỐ>/
    <uuid>.jpg
    <uuid>.jpg
    ...
```

- Mỗi xe 1 folder con, tên folder là biển số đã chuẩn hóa (không dấu, không
  khoảng trắng, VD: `30A12345`).
- Ảnh do app tự lưu khi đăng ký hồ sơ xe (tab **Hồ sơ xe**), không cần chép tay.
- Muốn thêm ảnh gốc cho xe đã đăng ký: dùng chức năng cập nhật hồ sơ trong app.

## Lưu ý

- Ảnh trong folder này là **dữ liệu thật của khách hàng, KHÔNG commit lên git**
  (chỉ file README này được commit để giữ folder). Ảnh chỉ nằm trên máy chạy app.
- Xóa folder xe = mất ảnh gốc đối chiếu của xe đó. Sao lưu trước khi xóa.
