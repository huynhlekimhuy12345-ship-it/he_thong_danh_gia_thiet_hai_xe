# Folder ảnh xe gửi thẩm định (hồ sơ bồi thường)

Folder này lưu **ảnh gốc khách gửi khi yêu cầu thẩm định** (ảnh xe bị hư hại),
để đối soát lại sau này: mỗi hồ sơ thẩm định có 1 folder riêng chứa đúng bộ
ảnh khách đã gửi.

## Cấu trúc

```
storage/claims/<MÃ-HỒ-SƠ>/
    <uuid>.jpg
    <uuid>.jpg
    ...
```

- Mỗi hồ sơ 1 folder con, tên folder là mã hồ sơ (VD: `CLM-20261119-4821`).
- Ảnh do app tự lưu khi nhận yêu cầu thẩm định (tab **Thẩm định**), sau bước
  xác minh danh tính (đúng biển số + đúng SĐT + đúng loại xe).
- Đường dẫn các ảnh được ghi trong bản ghi thẩm định (`claimPhotoPaths`) để
  tra lại theo mã hồ sơ.
- Ảnh gửi cho AI chỉ là bản resize (tối đa 1024px); ảnh gốc chất lượng đầy đủ
  lưu ở đây.

## Lưu ý

- Ảnh trong folder này là **dữ liệu thật của khách hàng, KHÔNG commit lên git**
  (chỉ file README này được commit để giữ folder). Ảnh chỉ nằm trên máy chạy app.
- Hồ sơ bị từ chối ở bước xác minh danh tính thì ảnh không được lưu (chưa tới
  bước kiểm tra ảnh).
- Xóa folder hồ sơ = mất ảnh gốc đối chứng của hồ sơ đó. Sao lưu trước khi xóa.
