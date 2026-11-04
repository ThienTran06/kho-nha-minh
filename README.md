# Kho Nhà Mình — Desktop & Cloud Sync v2.0.0

## Mới trong v1.6
- Sửa lỗi Sửa/Xóa phiếu đôi lúc báo “Không tìm thấy phiếu” bằng cách thao tác theo mã phiếu duy nhất.
- Tạo phiếu nhập/xuất cho ngày hôm qua hoặc ngày trước đó bằng trường **Ngày giờ phiếu**.
- Có thể sửa lại ngày giờ của phiếu đã tạo.
- Báo cáo và Excel theo ngày sử dụng đúng ngày giờ của phiếu.
- Xóa phiếu xuất sẽ cộng trả hàng vào kho; xóa phiếu nhập sẽ trừ số lượng khỏi kho và chặn nếu làm tồn âm.

# Kho Nhà Mình — Desktop Edition

Bản dành cho một người dùng trên Windows. Mẹ chỉ cần nhấp biểu tượng **Kho Nhà Mình** trên Desktop.

## Trải nghiệm của người dùng

1. Cài một lần bằng file `Kho Nha Minh-1.0.0.exe`.
2. Sau khi cài, Desktop có biểu tượng **Kho Nhà Mình**.
3. Nhấp đúp biểu tượng để mở.
4. Lần đầu: tạo mật khẩu. Những lần sau: chỉ nhập mật khẩu.
5. Không cần Docker, MySQL, Java hay mở terminal.

Ứng dụng tự mở ở cửa sổ Microsoft Edge dạng ứng dụng (không có thanh địa chỉ). Nếu Edge không có, ứng dụng mở bằng trình duyệt mặc định.

## Dữ liệu nằm ở đâu?

Dữ liệu KHÔNG nằm trong file EXE và KHÔNG mất khi nâng cấp phần mềm.

- Database: `%USERPROFILE%\KhoNhaMinh\data\kho.mv.db`
- Backup: `%USERPROFILE%\KhoNhaMinh\backups\`
- Log kỹ thuật: `%USERPROFILE%\KhoNhaMinh\logs\`

Mỗi ngày, lần mở đầu tiên sẽ tự sao lưu database trước khi phần mềm truy cập dữ liệu. Hệ thống giữ tối đa 30 bản backup gần nhất.

## Build file EXE trên Windows

Yêu cầu dành cho người phát triển: JDK 21 và WiX Toolset 3.x. **Không cần cài Maven**: project đã có Maven Wrapper và sẽ tự tải Maven 3.9.16 ở lần build đầu tiên.

```powershell
.\installer\build-windows.ps1
```

Build JAR thủ công bằng Maven Wrapper:

```powershell
.\mvnw.cmd clean package -DskipTests
```

Trên macOS/Linux:

```bash
./mvnw clean package -DskipTests
```

File cài đặt `.exe` sẽ xuất hiện trong `dist/`.

## Build bằng GitHub Actions

Repo đã có `.github/workflows/build-windows.yml`. Vào tab Actions > `Build Windows EXE` > Run workflow rồi tải artifact `Kho-Nha-Minh-Windows`.

## Vì sao dùng database nhúng thay vì MySQL?

Chế độ desktop mặc định dùng H2 file để không cần cài database riêng. Khi cần nhiều máy qua Internet, bật profile `cloud` để dùng PostgreSQL trung tâm; xem `HUONG-DAN-DONG-BO-INTERNET.md`.

## Nhập hàng từ Excel

Bản này hỗ trợ trực tiếp file theo mẫu đang sử dụng với các sheet `NVL` và `CCDC`.

- Vào **Hàng trong kho** -> **Nhập từ Excel**.
- Chọn file `.xlsx` hoặc `.xls`.
- Phần mềm kiểm tra trước và báo số dòng sẽ thêm / trùng / chưa hợp lệ.
- Bấm **Thêm hàng từ Excel** để xác nhận.
- `NVL` được xếp vào nhóm **Nguyên liệu / Gia vị**; `CCDC` vào **Công cụ / Dụng cụ**.
- Mã hàng được lấy từ cột **MÃ HÀNG** nếu có.
- Tên và đơn vị lấy từ **Tên NVL** và **ĐVT**.
- Số tồn hiện tại lấy từ **Tồn cuối**. Nếu Tồn cuối không có giá trị, chương trình tính `Tồn đầu + Nhập - Xuất`.
- Ghi chú được giữ từ cột **Ghi chú**.
- Dòng trùng với hàng đã có được bỏ qua, không cộng tồn lần nữa.
- Dòng có tồn cuối âm hoặc thiếu ĐVT sẽ được báo để kiểm tra lại.

Khi thêm thủ công, form cũng dùng cùng cách gọi với file Excel: **Nhóm hàng, Mã hàng, Tên hàng, ĐVT, Tồn hiện tại (Tồn cuối), Ghi chú**. Cảnh báo tồn thấp và giá nhập được đặt trong phần thiết lập thêm vì file Excel hiện tại không có hai cột này.


## v1.2 - Báo cáo Excel
- Báo cáo nhập/xuất theo ngày, tuần hoặc tháng.
- Màn hình báo cáo hiển thị nhập hôm nay, xuất hôm nay và tồn kho hiện tại.
- Có thể chọn từng mặt hàng cần đưa vào báo cáo, không bắt buộc xuất tất cả.
- File Excel tách riêng sheet `Nguyên liệu - Gia vị` và `Dụng cụ`.
- Mỗi sheet có Tồn đầu, Nhập, Xuất, Tồn cuối.

## v1.3 - Nhập file Excel .xls cũ an toàn

Bản 1.3 đọc trực tiếp cả `.xls` và `.xlsx`. Khi một mặt hàng trong Excel đã có trong phần mềm, hãy chọn một trong ba chế độ:

- **Bỏ qua hàng đã có**: mặc định và an toàn nhất.
- **Cập nhật thông tin**: cập nhật nhóm/ghi chú, không đổi tồn kho.
- **Đồng bộ tồn theo Excel**: đưa tồn hiện tại về đúng số trong file. Phần chênh lệch được ghi là giao dịch điều chỉnh Excel và không được cộng vào số Nhập/Xuất của báo cáo.

Importer tự tìm hàng tiêu đề trong 60 dòng đầu, nên phù hợp với file có tiêu đề lớn, ô merge hoặc ngày báo cáo ở đầu sheet như các file kho thực tế.

## v1.4 - Phiếu nhập / xuất Excel theo ngày

Trong **Báo cáo & sổ kho**, chọn một ngày rồi dùng một trong hai nút:

- **Xuất file NHẬP**: tạo `BANG-NHAP-HANG-VI-BIEN-YYYYMMDD.xlsx` theo mẫu Bảng nhập hàng Vị Biển.
- **Xuất file XUẤT**: tạo `PHIEU-XUAT-KHO-VI-BIEN-YYYYMMDD.xlsx` theo mẫu Phiếu xuất kho Vị Biển.

Hai file chỉ lấy giao dịch `IN` hoặc `OUT` đã hoàn tất trong đúng ngày được chọn. Tồn đầu kỳ, điều chỉnh từ Excel, giao dịch ngày khác và phiếu đã sửa sai không xuất hiện.

Khi nhập/xuất hàng, mục **Thông tin cho báo cáo** là không bắt buộc. Có thể nhập nhà cung cấp/người ký nhận/hình hóa đơn khi nhập, hoặc người nhận/bộ phận/tên nhân viên khi xuất để file Excel đầy đủ giống biểu mẫu giấy hơn.

## v1.5 - Thêm hàng thủ công + sửa/xóa phiếu

- Mặt hàng mới không cần có trong Excel. Có thể thêm trực tiếp tại **Hàng trong kho → Thêm mặt hàng** hoặc ngay trong **Nhập hàng → Hàng chưa có? Thêm mới**.
- Phiếu nhập/xuất do người dùng tạo có nút **Sửa** và **Xóa** trong Sổ nhập xuất.
- Sửa số lượng tự điều chỉnh tồn theo chênh lệch.
- Xóa phiếu xuất: trả số lượng đã xuất về kho.
- Xóa phiếu nhập: trừ số lượng đã nhập khỏi kho; phần mềm chặn nếu số tồn hiện tại không đủ để hoàn tác an toàn.
- Phiếu tồn đầu và điều chỉnh Excel không cho sửa/xóa thủ công.


## v1.6.3 - Lịch sử theo mặt hàng
Trong Hàng trong kho, bấm **Lịch sử** cạnh một mặt hàng để xem toàn bộ nhập/xuất, tồn sau từng phiếu, lọc theo loại/ngày và xuất Excel riêng cho mặt hàng đó.

## v1.7.1 - van hanh lau dai

Ban 1.7.1 bo sung cac lop an toan quan trong de su dung lau dai: kiem ke thuc te, danh sach can mua, backup/khoi phuc, nhat ky thay doi va chong tao trung mat hang.

Sau khi giai nen tren Windows:

```powershell
.\mvnw.cmd clean package -DskipTests
java -jar .\target\kho-nha-minh-desktop-1.7.1.jar
```

Tao bo cai EXE:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\installer\build-windows.ps1
```

### Ma khoi phuc mat khau
Lan dau cai moi, phan mem se hien ma `KHO-XXXX-XXXX`. Hay chup anh/ghi ra giay va cat rieng. Neu la du lieu nang cap tu ban cu, vao **Cai dat -> Tao ma khoi phuc** de tao ma lan dau.


## v1.7.1 - Nước / Đồ uống và quy đổi đơn vị

Thêm nhóm Nước / Đồ uống và quy đổi thùng/két/lốc sang lon/chai. Ví dụ khai báo Pepsi với đơn vị tồn kho LON, đơn vị đóng gói THÙNG, 1 THÙNG = 24 LON. Khi nhập 2 THÙNG, kho tăng 48 LON; sau đó vẫn có thể xuất 5 LON. Báo cáo Excel có sheet Nước - Đồ uống riêng.

## v1.8.0 - de dung hon va de bao tri hon

Ban nay giu nguyen luong Nhap / Xuat / Them hang, co che sua sai ton kho, bao mat 1 nguoi dung, backup hang ngay, kiem ke, quy doi don vi va Excel hien co.

Cac thay doi chinh:
- Khoi phuc backup bat buoc xac nhan 2 buoc; truoc khi ghi de, backend tu tao them file JSON `TRUOC-KHOI-PHUC-...json` trong `~/KhoNhaMinh/backups`.
- Sau khi dang nhap, neu co hang sap het/het hang, app hien mot nhac nho ro rang va co nut di thang den danh sach can mua.
- Them tuy chon `Chu binh thuong / Chu lon` trong Cai dat, duoc nho rieng tren may.
- Sau khi Nhap/Xuat thanh cong, toast co nut `Hoan tac` trong 9 giay de sua nham nhanh.
- Trang Bao cao duoc tach thanh 3 tab: `Xuat phieu nhanh`, `Bao cao tong hop`, `So nhap xuat`.
- Danh sach checkbox chon mat hang trong bao cao duoc dua vao muc `Nang cao`; mac dinh van chon tat ca.
- Them tim kiem bang giong noi tren Chrome/Edge neu trinh duyet ho tro; neu khong ho tro thi nut micro tu an.
- Frontend khong con don CSS/JS trong mot file HTML 88KB: tach thanh `index.html`, `app.css`, `app.js` de de bao tri.
- Them test tu dong cho quy doi THUNG -> LON, chan xuat vuot ton, va logic xoa phieu nhap/xuat hoan ton.
- Script build Windows dung `mvnw clean verify`; neu test hong thi khong tao EXE.

Chay thu:
```powershell
.\mvnw.cmd clean verify
java -jar .\target\kho-nha-minh-desktop-1.9.3.jar
```

Tao EXE:
```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\installer\build-windows.ps1
```

Bao mat: ban desktop van chi bind `127.0.0.1`. CSRF hien duoc tat cho workflow localhost. Neu sau nay mo server ra LAN/Internet, can thiet ke lai authentication/CSRF truoc khi doi `server.address`.


## v1.8.2 - giao dien Claude-inspired
Giữ nguyên chức năng v1.8.0, chỉ tinh chỉnh hệ thống typography, màu sắc, sidebar, card, button, modal và trạng thái theo hướng ấm, tối giản, dễ đọc. Không kèm font độc quyền; dùng font có sẵn trên Windows để app hoạt động offline.

## v1.8.2 - Import Nước theo tháng + sửa nhóm + sửa tồn đầu
- Hỗ trợ file dạng `NƯỚC CÁC LOẠI T9.xlsx` với sheet `THEO DÕI THÁNG ...`.
- Có preview, chọn tháng dữ liệu, cảnh báo header sai tháng, bỏ qua ngày tương lai và chống import trùng.
- Sau import, tồn cuối được đồng bộ theo file để không làm lệch tồn hiện tại.
- Nhóm hàng sửa thủ công được giữ nguyên khi import Excel sau này.
- Có nút `Tồn đầu` để sửa số tồn đầu an toàn; tồn hiện tại tự điều chỉnh theo chênh lệch và có audit log.


## v1.8.4 - Import đúng báo cáo Nước theo ngày
- Đọc đúng bảng BÁO CÁO XUẤT NHẬP TỒN theo ngày được chọn, ví dụ NGÀY 7/9.
- Bỏ qua bảng CÂY/TỦ/LẺ bên phải và sheet THEO DÕI THÁNG 9.
- Đọc Tồn đầu, Nhập, Xuất, HT, Tồn cuối và Ghi chú.
- HT được lưu thành điều chỉnh giảm vì công thức file mẫu là Tồn cuối = Tồn đầu + Nhập - Xuất - HT.
- Chống import trùng theo ngày + mặt hàng + loại biến động.
- Không tự ghi đè một mặt hàng nếu đã có lịch sử mới hơn ngày báo cáo.

## v1.8.8 - Import Nước linh hoạt
- Import Nước hỗ trợ thêm file `.xls/.xlsx` dạng đơn giản không có tiêu đề “BÁO CÁO XUẤT NHẬP TỒN”.
- Chỉ cần nhận diện được cột Tên hàng (ví dụ Tên NVL) và Tồn cuối/Còn lại/Tồn kho.
- ĐVT là tùy chọn với hàng đã có; hàng mới cần ĐVT để tạo mặt hàng.
- Với file dạng đơn giản, ngày do người dùng chọn trên màn hình được dùng làm ngày chốt tồn.
- Vẫn ưu tiên cấu trúc báo cáo đầy đủ nếu file có cấu trúc cũ.


## v1.9.3 - Chuyển dữ liệu sang máy khác

- Có nút xuất file chuyển máy và nhập file từ máy khác trong Cài đặt.
- Trước khi nhập, phần mềm kiểm tra file và hiển thị số mặt hàng, giao dịch, khoản chi, kiểm kê và nhật ký.
- Nhập dữ liệu thay thế dữ liệu nghiệp vụ trên máy nhận; tự lưu bản an toàn trước khi khôi phục.
- Mật khẩu không nằm trong file chuyển máy; máy mới giữ mật khẩu riêng.


## v2.0.0 — Đồng bộ nhiều máy qua Internet

Bản 2.0.0 giữ mặc định H2 cục bộ cho bộ cài Windows, đồng thời bổ sung profile `cloud` dùng PostgreSQL để hai máy truy cập cùng một dữ liệu trung tâm.

### Điểm mới
- Profile `cloud` kết nối PostgreSQL bằng biến môi trường, lắng nghe trên `0.0.0.0` và dùng cổng `PORT` do nền tảng cấp.
- CSRF được bật trong profile cloud; cookie phiên chỉ gửi qua HTTPS.
- Lần thiết lập đầu tiên trên Internet yêu cầu `APP_SETUP_KEY`; mật khẩu cloud cần ít nhất 10 ký tự.
- Giao diện kiểm tra phiên bản dữ liệu mỗi 5 giây và tự tải lại khi có thay đổi từ máy khác, nhưng trì hoãn khi đang nhập liệu hoặc mở hộp thoại để tránh ghi đè biểu mẫu.
- Mọi thao tác nhập/xuất/sửa/xóa vẫn xử lý ở máy chủ và dùng khóa hàng khi cập nhật tồn kho.
- Trước khi khôi phục file, hệ thống lưu tối đa 10 bản sao JSON an toàn độc lập với giao dịch khôi phục trong bảng `backup_snapshots`. Có thể liệt kê qua `/api/backup/snapshots` và tải từng file qua `/api/backup/snapshots/{id}/export` khi đã đăng nhập.
- Thêm `Dockerfile` và `render.yaml` làm mẫu triển khai. Cấu hình Render mẫu dùng gói web miễn phí để thử nghiệm; gói miễn phí có thể ngủ khi không dùng và không nên coi là dịch vụ luôn sẵn sàng.

### Chạy local như trước

```powershell
.\mvnw.cmd clean verify
java -jar .\target\kho-nha-minh-desktop-2.0.0.jar
```

Chạy local mặc định vẫn dùng H2 tại `%USERPROFILE%\KhoNhaMinh\data\kho.mv.db`; không đặt profile `cloud` khi muốn mở database cục bộ.

### Biến môi trường cho profile cloud

- `SPRING_PROFILES_ACTIVE=cloud`
- `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`: thông tin PostgreSQL; mật khẩu không được commit vào mã nguồn.
- `APP_SETUP_KEY`: chuỗi bí mật ngẫu nhiên dùng bảo vệ lần thiết lập đầu tiên; ứng dụng cloud không khởi động nếu thiếu khóa này.
- `DB_POOL_SIZE=5` (có thể điều chỉnh theo giới hạn kết nối database).

### Lưu ý

Bản này là mã nguồn sẵn sàng triển khai, chưa tự tạo dịch vụ trong tài khoản của người dùng và chưa được kiểm chứng bằng build Maven/triển khai Internet trong môi trường tạo file. Hãy làm theo `HUONG-DAN-DONG-BO-INTERNET.md`, triển khai trên dữ liệu thử trước, rồi mới nhập file backup thật. Luôn xuất file JSON định kỳ và cất ở nơi riêng; bản sao lưu trong cùng database không thay thế backup độc lập.
