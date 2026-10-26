# Kịch bản demo Seminar OAuth Hub 2.4.0 — khoảng 5 phút

## Chuẩn bị

- App Docker chạy, /health báo version 2.4.0.
- Hai hồ sơ trình duyệt riêng cho A (STUDENT) và B (LECTURER).
- AUTO_DOMAIN_ROLES=true và LECTURER_OVERRIDE_EMAIL=524h0133@student.tdtu.edu.vn. A là một tài khoản @student.tdtu.edu.vn khác email ngoại lệ; B là 524h0133@student.tdtu.edu.vn hoặc một tài khoản @tdtu.edu.vn.
- Không cần điền từng người vào STUDENT_EMAILS/LECTURER_EMAILS. Giữ ALLOWED_WORKSPACE_DOMAINS=student.tdtu.edu.vn,tdtu.edu.vn để hỗ trợ cả hd là miền chính của Organization.
- Chủ thư mục đã kết nối theo DRIVE_SETUP.md; Picker hoạt động; chuẩn bị một file Word/PDF và một ZIP thật, không đặt mật khẩu. Nếu dùng MANUAL thì giữ quyền chia sẻ thủ công.
- APP_BASE_URL dùng được ở thiết bị mở email. Nếu dùng localhost, mở cả hai hồ sơ trên cùng laptop.
- Dùng nhóm/đề tài mới cho mỗi lần demo để dễ tìm thư.

## Lời giới thiệu

“Ứng dụng của nhóm em hỗ trợ nộp và xét duyệt báo cáo seminar. Sinh viên đăng nhập Google, upload file lên Drive và ủy quyền cho app gửi email đến giảng viên. Giảng viên mở link trong thư, đăng nhập, nhận xét và xét duyệt; app dùng quyền Gmail của chính giảng viên để gửi kết quả về sinh viên. Thông tin quy trình lưu trong H2 để mở lại bài bằng link, không còn bước Google Sheets.”

## Thao tác

1. A đăng nhập bằng email @student.tdtu.edu.vn khác email ngoại lệ. App tự nhận STUDENT; chỉ xem bài của A.
2. A vào Nộp báo cáo, cấp Drive + Gmail, chọn thư mục, nhập nhóm/đề tài và email B, upload Word/PDF hoặc ZIP. Có thể chọn giảng viên từ gợi ý hoặc nhập B trước lần đầu B đăng nhập. Chọn có/không nhận Cc trước khi nộp.
3. Cho lớp xem file đã có trên Drive và thư trong Sent của A: To B, chỉ có Cc A nếu A tích ô nhận bản sao.
4. Chuyển hồ sơ B, mở email, bấm link Xem và xét duyệt.
5. B đăng nhập; app tự nhận LECTURER và quay lại đúng bài. Với Word/PDF, B bấm “Xem tài liệu”; với ZIP, B bấm “Tải file ZIP” rồi giải nén để kiểm tra.
6. B cấp Gmail nếu cần, nhập nhận xét hoặc để trống, chọn Hợp lệ/Không hợp lệ, chọn có/không nhận Cc, rồi xác nhận.
7. Mở email của A: thư kết quả From B, To A; Cc B nếu B tích ô nhận bản sao. Dashboard A cũng hiển thị kết quả.

Có thể nộp hai bài để minh họa hai trường hợp: bài thứ nhất là Word/PDF, bài thứ hai là ZIP.
Ô Cc mặc định bỏ chọn, hiển thị địa chỉ của chính người gửi khi tích. Nếu thư lỗi, nút Gửi lại dùng người nhận đã lưu, không đổi lựa chọn Cc.

## Minh họa OAuth sau luồng chính

Mở OAuth Lab:
- Lần đăng nhập đầu dùng openid/email/profile.
- A cần drive.file và gmail.send; B chỉ cần gmail.send để gửi kết quả.
- App hiển thị scope thực nhận, không suy đoán đã cấp đủ.
- state, PKCE S256 và OIDC nonce liên kết đúng phiên/authorization response.
- Refresh gọi token endpoint bằng refresh token.
- Ngắt kết nối gọi revoke; app vẫn có thể hiển thị hồ sơ đã lưu trong H2 nhưng gọi Google API cần cấp quyền lại.

Có thể cho thấy A bị chặn khi gọi endpoint xét duyệt, hoặc một giảng viên không được giao không mở được link bài.

## Những câu nên nói chính xác

- “Google OAuth 2.0 ủy quyền gọi API; OpenID Connect xác thực người đăng nhập.”
- “Sau khi Google xác thực email và miền Workspace, backend ưu tiên tài khoản demo 524h0133@student.tdtu.edu.vn làm giảng viên, rồi phân vai theo chính xác miền email: tdtu.edu.vn là giảng viên, student.tdtu.edu.vn là sinh viên.”
- “Vai trò giảng viên trong mã nguồn tên là LECTURER. Mỗi giảng viên chỉ xem và duyệt bài được gửi cho email của mình.”
- “Internal giới hạn thành viên tổ chức, không thay thế phân quyền trong app.”
- “Drive giữ file; H2 giữ thông tin và trạng thái; Gmail chuyển thông báo.”
- “Khi gửi lỗi không chắc chắn, app yêu cầu kiểm tra Sent; không cam kết gửi email đúng một lần trong mọi sự cố.”
- “Quyền thư mục chung ngoài Drive có phạm vi riêng; không tuyên bố file đã được cô lập giữa tất cả sinh viên.”

## Minh họa tự cấp quyền Drive

Chuẩn bị theo DRIVE_SETUP.md: chủ thư mục kết nối một lần, backend lưu refresh token mã hóa.
Đăng nhập bằng sinh viên mới, cấp Drive/Gmail. App tự chuẩn bị Editor trên folder riêng và thử kết nối theo ID; khi cần Picker, popup chỉ có đúng folder đích.
Nộp bài cho giảng viên mới: Viewer chỉ được cấp trên file được giao; mở email bằng giảng viên để duyệt và gửi kết quả.
Chủ thư mục có thể đăng xuất trong quá trình này. Không demo SHARED như thể các bài được cô lập; quyền của thư mục chung kế thừa xuống các bài.
