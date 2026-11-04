# Hướng dẫn triển khai Kho Nhà Mình 2.0.0 để đồng bộ máy A và máy B

## Trước khi bắt đầu

**Lưu ý:** ZIP này là mã nguồn để triển khai máy chủ, không phải bộ cài Windows đã kết nối đồng bộ. Nếu chỉ build/cài EXE theo cách desktop, ứng dụng vẫn chạy H2 riêng trên máy đó. Chỉ sau khi deploy thành công, cả máy A và B mới dùng cùng một địa chỉ HTTPS để chia sẻ dữ liệu.

- Giữ lại bộ cài và file nguồn Kho Nhà Mình 1.9.3 để có thể quay lại.
- Trên máy đang có dữ liệu, mở bản 1.9.3, kiểm tra tồn kho rồi vào **Cài đặt → Xuất file chuyển máy**. Cất file `.json` ở một nơi riêng; file này chứa dữ liệu kinh doanh, không gửi công khai.
- Bản 2.0.0 chưa được triển khai vào tài khoản của bạn. Không gỡ hoặc xóa dữ liệu bản cũ cho tới khi đã kiểm tra dữ liệu trên hệ thống cloud.

## Kiến trúc được dùng

- **Ứng dụng:** Render Web Service chạy Java 21 bằng Docker.
- **Database:** PostgreSQL trên Neon hoặc một dịch vụ PostgreSQL tương đương.
- **Đồng bộ:** hai máy mở cùng một địa chỉ HTTPS; giao diện hỏi máy chủ mỗi 5 giây. Khi đang mở biểu mẫu hoặc nhập liệu, việc tự tải lại được hoãn để tránh mất dữ liệu đang gõ.
- **Bảo mật:** CSRF bật trong profile cloud, cookie phiên chỉ dùng HTTPS, lần thiết lập đầu cần `APP_SETUP_KEY`, mật khẩu cloud ít nhất 10 ký tự.

## Phần A — Tạo PostgreSQL

1. Tạo project PostgreSQL trên Neon (hoặc dịch vụ PostgreSQL bạn tin cậy). Chọn region gần nơi sử dụng nhất nếu có lựa chọn phù hợp.
2. Tạo/ghi lại các thông tin kết nối: host, port (thường là `5432`), database, user và password.
3. Không dán password database vào chat, README hoặc mã nguồn. Các giá trị sẽ được nhập trực tiếp vào mục Environment của Render.
4. Database mới cần để trống; khi ứng dụng khởi động lần đầu, Flyway sẽ tạo các bảng cần thiết.

## Phần B — Đưa mã nguồn lên GitHub riêng tư

Render Blueprint cần một repository để lấy mã nguồn. Tạo một repository **Private** mới, rồi tải toàn bộ nội dung thư mục này lên repository. Không tải lên `.env`, file backup `.json`, hoặc thông tin đăng nhập database.

Nếu không muốn dùng Git ở máy tính, có thể tải file ZIP lên GitHub bằng giao diện web (Add file → Upload files). Giữ nguyên cấu trúc thư mục ở gốc repository, bao gồm `Dockerfile`, `render.yaml`, `pom.xml` và `src/`.

## Phần C — Tạo dịch vụ Render

1. Trong Render, chọn **New → Blueprint** (hoặc triển khai web service từ repository) và kết nối repository riêng vừa tạo.
2. Dùng `render.yaml` ở thư mục gốc. Bản mẫu chọn gói web `free` để thử nghiệm; dịch vụ miễn phí có thể ngủ sau một thời gian không hoạt động. Nếu cần sẵn sàng liên tục, xem lại gói dịch vụ và chi phí trước khi nâng cấp.
3. Trong Render → dịch vụ `kho-nha-minh-cloud` → **Environment**, điền các biến:
   - `PGHOST`: host PostgreSQL
   - `PGPORT`: `5432` (hoặc port nhà cung cấp đưa)
   - `PGDATABASE`: tên database
   - `PGUSER`: user database
   - `PGPASSWORD`: password database
   - `APP_SETUP_KEY`: tự tạo một chuỗi ngẫu nhiên dài, ví dụ ít nhất 32 ký tự; không dùng mật khẩu đăng nhập Kho Nhà Mình làm khóa này
4. Lưu thay đổi và deploy. Đợi trạng thái service là **Live**. Mở `/actuator/health` trên domain Render để kiểm tra phản hồi sức khỏe.
5. Nếu service không lên, mở **Logs** và kiểm tra biến môi trường, host/credentials database, kết nối SSL và log Flyway. Không nhập dữ liệu thật cho đến khi deploy thành công.

## Phần D — Thiết lập và chuyển dữ liệu lần đầu

1. Mở domain HTTPS của Render. Trình duyệt sẽ hiện trang thiết lập lần đầu.
2. Nhập `APP_SETUP_KEY` đã cấu hình ở Render, tạo mật khẩu Kho Nhà Mình tối thiểu 10 ký tự.
3. Ghi lại mã khôi phục mà ứng dụng hiển thị; cất riêng, không lưu chung với mật khẩu.
4. Vào **Cài đặt → Nhập file từ máy khác**, chọn file JSON đã xuất từ bản 1.9.3. Đọc kỹ số mặt hàng, giao dịch, chi phí và xác nhận hai bước.
5. Kiểm tra kỹ một số mặt hàng đại diện, tồn kho, lịch sử nhập/xuất, chi phí, kiểm kê và báo cáo. File nhập sẽ thay thế dữ liệu hiện có trên database cloud; chỉ nhập vào database mới, trống.
6. Xuất lại một file JSON từ bản cloud sau khi kiểm tra xong và lưu một bản độc lập.

## Phần E — Kết nối máy A và B

1. Trên cả hai máy, mở cùng domain HTTPS của Render trong Edge/Chrome.
2. Đăng nhập bằng cùng mật khẩu Kho Nhà Mình.
3. Thử bằng một mặt hàng kiểm tra: tạo hoặc nhập số lượng nhỏ trên máy A, chờ tối đa khoảng 5–10 giây, xác nhận máy B cập nhật. Sau đó xuất thử trên máy B và xác nhận máy A cập nhật lại.
4. Xóa mặt hàng thử hoặc hoàn tác đúng nghiệp vụ trước khi bắt đầu dùng thật.
5. Không tiếp tục nhập hàng vào bản H2 cũ trên máy A hoặc máy B sau khi chuyển sang cloud. Từ thời điểm chuyển đổi, chỉ dùng domain HTTPS chung; nếu hai máy tiếp tục ghi vào database cục bộ cũ, dữ liệu sẽ tách đôi.

## Sao lưu và khôi phục

- Dùng **Xuất file chuyển máy** thường xuyên để có bản JSON ở ngoài database; giữ file ở nơi riêng tư.
- Trước khi khôi phục, hệ thống lưu tối đa 10 bản sao an toàn trong bảng `backup_snapshots`. Khi đã đăng nhập, `GET /api/backup/snapshots` liệt kê metadata và `GET /api/backup/snapshots/{id}/export` tải một file JSON. Có thể nhập lại file đó bằng chức năng nhập dữ liệu.
- Bản sao lưu nằm trong cùng PostgreSQL vẫn phụ thuộc vào database đó. Không coi nó là bản sao độc lập; hãy giữ file JSON bên ngoài và bật/kiểm tra chính sách backup của nhà cung cấp database.

## Giới hạn và an toàn vận hành

- Đây là đồng bộ thông qua một database trung tâm, không phải đồng bộ offline. Khi mất Internet hoặc server/database không khả dụng, thao tác cần máy chủ sẽ không hoạt động.
- Giao diện làm mới dữ liệu tối đa mỗi 5 giây khi trang đang hiển thị và không có biểu mẫu đang chỉnh sửa; đây không phải cập nhật tức thời từng mili-giây.
- Nếu cả hai máy sửa cùng một mặt hàng, backend khóa hàng khi thay đổi tồn kho; vẫn cần kiểm thử thao tác đồng thời trước khi dùng thật.
- Không chia sẻ `APP_SETUP_KEY`, `PGPASSWORD`, file JSON backup hoặc mã khôi phục.
- Không mở PostgreSQL trực tiếp ra Internet cho máy người dùng. Chỉ ứng dụng máy chủ kết nối database.
- Chưa có tài khoản riêng cho từng người: cả hai máy dùng chung một mật khẩu. Nhật ký hiện có ghi nội dung và thời gian, không phân biệt chính xác người nào thao tác.
