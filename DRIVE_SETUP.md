# Tự động cấp quyền Drive — bản 2.4.0

Sau một lần chủ thư mục cấp quyền cho backend, tài khoản mới được chuẩn bị quyền Drive khi đăng nhập. Sinh viên vẫn cần đồng ý quyền Drive/Gmail của chính mình; Google Picker chỉ mở đúng ID thư mục đích khi ứng dụng cần được xác nhận quyền truy cập file lần đầu.

## 1. Chọn phạm vi chia sẻ

| DRIVE_ACCESS_MODE | Sinh viên | Giảng viên | Phạm vi |
|---|---|---|---|
| ISOLATED — khuyến nghị | Editor trên thư mục riêng của tài khoản nộp bài | Viewer trên từng file được gửi cho mình | Không tự chia sẻ thư mục lớp cho cả lớp |
| SHARED — demo thư mục chung | Editor trên thư mục lớp | Viewer trên thư mục lớp | Quyền kế thừa: có thể truy cập nội dung của người khác qua Drive |
| MANUAL — tương thích bản cũ | Cấp quyền thủ công như trước | Cấp quyền thủ công như trước | Picker vẫn lọc theo đúng DRIVE_FOLDER_ID |

ISOLATED là giá trị trong .env.example. Để bản đang chạy không tự đổi phạm vi ngoài ý muốn, nếu .env cũ chưa khai báo DRIVE_ACCESS_MODE thì ứng dụng giữ MANUAL.

**Thư mục dùng chung không thể được coi là riêng tư chỉ vì backend chặn URL bài nộp.** Editor trên thư mục có quyền với nội dung bên trong; Viewer có thể xem nội dung khác trực tiếp trên Drive. Vì vậy SHARED cần chủ thư mục xác nhận phạm vi trên trang Thiết lập Drive trước khi lưu kết nối.

App chỉ tạo quyền `type=user` cho email TDTU; không tạo quyền `anyone`, `domain` hoặc `group`, không chuyển Owner và không tự xóa/hạ quyền cũ. Quyền Owner hoặc mức quyền cao hơn đã có được giữ nguyên. Chủ thư mục vẫn có quyền quản lý dữ liệu; quyền Google Drive ngoài app có thể được người sở hữu thay đổi riêng.

## 2. Chuẩn bị thư mục cho ISOLATED

1. Dùng tài khoản chủ thư mục, mặc định **523h0080@student.tdtu.edu.vn**.
2. Trong **My Drive**, tạo một thư mục riêng tên **Seminar - Minh chứng**. Không đặt trong một thư mục cha đã chia sẻ rộng.
3. General access đặt Restricted; thư mục gốc chỉ có chủ sở hữu.
4. Sao chép ID từ URL thư mục vào DRIVE_FOLDER_ID.

Nếu thư mục cũ đã chia sẻ Editor/Viewer cho nhiều người, nên tạo thư mục gốc riêng mới cho ISOLATED và giữ nguyên thư mục cũ. Không xóa bài hoặc quyền cũ để nâng cấp. App sẽ từ chối bật ISOLATED nếu còn quyền người dùng khác/nhóm/domain/anyone ở thư mục gốc, do các quyền đó có thể kế thừa xuống dưới.

Bản này hỗ trợ thư mục My Drive do đúng tài khoản cấu hình sở hữu. Không áp dụng cơ chế này cho Google Shared drives. Chính sách Workspace có thể chặn chia sẻ; ứng dụng không vượt qua chính sách của trường.

## 3. Tạo khóa mã hóa một lần trên Windows

Mở PowerShell, chạy lần lượt:

```powershell
$driveKeyBytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($driveKeyBytes)
[Convert]::ToBase64String($driveKeyBytes)
```

Sao chép chuỗi kết quả vào DRIVE_ADMIN_TOKEN_KEY trong .env. Không gửi chuỗi này cho sinh viên, không đưa lên Git hoặc nhúng vào JavaScript. Mỗi cài đặt dùng khóa riêng; không dùng khóa ví dụ trong các test.

Khóa nằm trong cấu hình máy chủ, tách khỏi H2. Refresh token lưu trong H2 được mã hóa bằng AES-256-GCM, kèm nonce ngẫu nhiên và ràng buộc với chủ thư mục, Google client, root ID và chế độ chia sẻ. Access token quản lý được cache ngắn hạn trong bộ nhớ server. Picker của chủ thư mục dùng token ngắn hạn của chính phiên chủ thư mục; API cho sinh viên chỉ trả token của sinh viên, không trả refresh token hay credential quản lý đã lưu.

## 4. Cập nhật .env và chạy

Giữ các Client ID/Secret, API key, project number, cấu hình vai trò, port và database hiện tại. Bổ sung:

```properties
DRIVE_ACCESS_MODE=ISOLATED
DRIVE_ADMIN_EMAIL=523h0080@student.tdtu.edu.vn
DRIVE_ADMIN_TOKEN_KEY=dan-chuoi-Base64-vua-tao
DRIVE_FOLDER_ID=id-thu-muc-goc-rieng
```

Trong đúng thư mục Compose hiện tại:

```powershell
docker compose up -d --build --force-recreate
```

Giữ Docker volume và .env; không chạy `docker compose down -v`. /health phải báo 2.4.0.

## 5. Chủ thư mục kết nối một lần

1. Đăng nhập bằng tài khoản trong DRIVE_ADMIN_EMAIL.
2. Mở **Thiết lập Drive** ở thanh điều hướng. Quyền thiết lập này độc lập với vai trò STUDENT/LECTURER; tài khoản 523h0080 có thể vẫn là STUDENT trong app.
3. Bấm **Cho phép quản lý thư mục Drive**, đồng ý scope drive.file. App vẫn dùng Authorization Code, PKCE và kiểm tra ID token; không xin toàn bộ Drive. Đây là luồng cấp quyền tăng dần của cùng OAuth client: nếu tài khoản từng cấp Gmail cho app, Google có thể trả kèm quyền đó. Backend chỉ dùng credential quản lý đã lưu cho API Drive; token sinh viên/giảng viên để gửi Gmail vẫn lấy từ phiên của chính người gửi.
4. Bấm **Kết nối thư mục lớp**. Picker chỉ hiển thị ID đã cấu hình; chọn thư mục đó.
5. Backend kiểm tra chủ sở hữu, khả năng chia sẻ, loại thư mục và ACL. Chỉ khi phù hợp mới lưu refresh token dưới dạng mã hóa.
6. Trang hiển thị **Đã sẵn sàng**. Chủ thư mục có thể đăng xuất; không cần giữ phiên đăng nhập để chuẩn bị quyền cho người khác.

Nếu không có refresh token, dùng nút cấp lại quyền Drive rồi kết nối. Cấu hình OAuth Internal và drive.file vẫn phải được Workspace cho phép.

## 6. Người dùng mới

### Sinh viên

- Đăng nhập Google hợp lệ. Backend dùng quyền đã được chủ thư mục ủy quyền để tạo/tìm thư mục riêng theo Google subject ổn định và cấp Editor cho chính sinh viên đó.
- Đồng ý Drive/Gmail khi lần đầu dùng chức năng nộp bài.
- App thử kết nối thư mục bằng ID. Nếu Google yêu cầu chọn file cho drive.file lần đầu, bấm **Kết nối thư mục nộp bài**: Picker chỉ có thư mục đích của tài khoản, không có danh sách toàn bộ Drive.
- Ở ISOLATED, tên thư mục đích gồm **Seminar - Minh chứng - email sinh viên**. Đây là thư mục con của thư mục lớp; không cần chép ID thư mục con vào .env.
- Upload vẫn dùng access token của sinh viên. File và đường dẫn nằm trên Drive; thông tin quy trình vẫn nằm trong H2.

### Giảng viên

- Ở ISOLATED, không được tự cấp Viewer cho thư mục gốc hoặc thư mục của mọi sinh viên.
- Khi sinh viên nộp bài, app tự cấp Viewer cho giảng viên được chọn trên chính file đó rồi gửi email.
- Giảng viên mở link, đăng nhập và xét duyệt như trước; không cần scope Drive để mở file bằng giao diện Drive.

Folder riêng đặt writersCanShare=false; quyền cấp trên folder do chủ thư mục quản lý. Sinh viên sở hữu file mình tải lên và người sở hữu vẫn có thể thay đổi chia sẻ ngoài app. App kiểm tra lại root/folder khi chuẩn bị quyền và nộp bài, dừng nếu phát hiện phạm vi không còn phù hợp; không tự thu hồi các quyền thay đổi ngoài app.

## 7. Nếu chủ đích cần đúng thư mục chung cho demo

Đặt DRIVE_ACCESS_MODE=SHARED, tạo lại container rồi để chủ thư mục kết nối lại. Trên Thiết lập Drive, xác nhận rằng sinh viên Editor và giảng viên Viewer sẽ có quyền trên nội dung toàn thư mục. Từ đó tài khoản mới được cấp quyền ngay khi đăng nhập hợp lệ.

App vẫn từ chối thiết lập nếu thư mục có chia sẻ nhóm, domain, anyone hoặc tài khoản ngoài TDTU. Owner không bị đổi; người đã có quyền cao hơn không tự bị hạ xuống Reader. Nếu cần mức Reader chính xác cho tài khoản cũ, chủ thư mục tự xem xét quyền cũ trên Drive.

## 8. Sự cố và dữ liệu cũ

- Google từ chối quyền: đăng nhập app vẫn có thể thành công nhưng chưa nộp được bài; xử lý quyền/chính sách rồi kết nối lại. Không tự mở rộng sang toàn bộ Drive.
- Tạo folder thành công nhưng phản hồi lỗi: lần thử sau tìm lại bằng appProperties để tránh tạo folder lặp. Nếu có nhiều folder cùng khóa, dừng và để chủ thư mục kiểm tra.
- Token bị thu hồi/hết hiệu lực: chủ thư mục kết nối lại. Google vẫn quyết định thời hạn ủy quyền; không cam kết quyền tồn tại vĩnh viễn.
- Mất/đổi khóa mã hóa: khôi phục khóa cũ hoặc để chủ thư mục kết nối lại; không lưu token ở dạng rõ để bỏ qua lỗi.
- Đổi root ID, Google client hoặc chế độ: cần chủ thư mục kết nối lại. Credential cũ không tự được dùng cho root/client/mode khác.
- **Ngừng tự cấp quyền**: xóa credential đã lưu trong H2; không xóa folder, bài nộp hay quyền Drive đã cấp. Thu hồi grant trong OAuth Lab của chủ thư mục cũng xóa credential quản lý đã lưu.
- App không tự di chuyển hay thu hồi chia sẻ của file/bài cũ. Link bài cũ còn theo ACL cũ; lượt nộp dở ở thư mục đích cũ có thể cần nộp thành lượt mới.
- Sao lưu cả dữ liệu H2 và khóa cấu hình qua hai nơi được bảo vệ phù hợp. Không đưa dữ liệu thực hoặc .env vào gói mã nguồn.

## Tài liệu Google

- [Chia sẻ và kế thừa quyền](https://developers.google.com/workspace/drive/api/guides/manage-sharing)
- [DocsView.setFileIds](https://developers.google.com/workspace/drive/picker/reference/picker.docsview.setfileids)
- [OAuth cho web server và offline access](https://developers.google.com/identity/protocols/oauth2/web-server)
- [Quyền drive.file](https://developers.google.com/workspace/drive/api/guides/api-specific-auth)
