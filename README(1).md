# Seminar OAuth Hub

Ứng dụng Spring Boot hỗ trợ sinh viên nộp báo cáo, lưu minh chứng trên Google Drive, gửi email cho giảng viên và nhận kết quả xét duyệt. Google xác thực danh tính qua OpenID Connect; OAuth 2.0 cho phép ứng dụng gọi Drive API và Gmail API với quyền người dùng đã đồng ý. Backend kiểm soát vai trò và quyền đối với từng bài.

**Cách cài đặt được dùng trong hướng dẫn:** chạy ở `http://localhost:8080`, phân vai theo email TDTU và dùng chế độ Drive `ISOLATED`. Có hướng dẫn riêng cho `SHARED`, `MANUAL` và cách chạy không dùng Docker.

Người triển khai cấu hình Google Cloud **một lần cho một bản cài đặt**. Sinh viên và giảng viên sử dụng website đã triển khai không cần tự tạo Cloud project hoặc tự điền Client ID.

## Mục lục

2. [Chuẩn bị tài khoản và phần mềm](#2-chuẩn-bị-tài-khoản-và-phần-mềm)
3. [Tải mã nguồn từ Git](#3-tải-mã-nguồn-từ-git)
4. [Tạo Google Cloud project](#4-tạo-google-cloud-project)
5. [Bật các API cần thiết](#5-bật-các-api-cần-thiết)
6. [Cấu hình Google Auth Platform](#6-cấu-hình-google-auth-platform)
7. [Tạo OAuth client cho ứng dụng web](#7-tạo-oauth-client-cho-ứng-dụng-web)
8. [Tạo API key cho Picker và lấy Project number](#8-tạo-api-key-cho-picker-và-lấy-project-number)
9. [Chuẩn bị thư mục Drive và chọn chế độ chia sẻ](#9-chuẩn-bị-thư-mục-drive-và-chọn-chế-độ-chia-sẻ)
10. [Tạo và điền file env](#10-tạo-và-điền-file-env)
11. [Chạy bằng Docker](#11-chạy-bằng-docker)
12. [Chạy bằng Java và Maven Wrapper](#12-chạy-bằng-java-và-maven-wrapper)
13. [Chủ thư mục kết nối Drive một lần](#13-chủ-thư-mục-kết-nối-drive-một-lần)
14. [Sinh viên và giảng viên sử dụng app](#14-sinh-viên-và-giảng-viên-sử-dụng-app)
15. [Kiểm tra lần chạy đầu tiên](#15-kiểm-tra-lần-chạy-đầu-tiên)
16. [Đổi cổng hoặc địa chỉ truy cập](#16-đổi-cổng-hoặc-địa-chỉ-truy-cập)
17. [Dữ liệu và cách cập nhật mã nguồn](#17-dữ-liệu-và-cách-cập-nhật-mã-nguồn)
18. [Xử lý lỗi thường gặp](#18-xử-lý-lỗi-thường-gặp)
19. [Tài liệu tham khảo](#20-tài-liệu-tham-khảo)


## 2. Chuẩn bị tài khoản và phần mềm

### Tài khoản

Chuẩn bị:

- Một tài khoản có quyền cấu hình Google Cloud project và OAuth client.
- Một tài khoản TDTU sở hữu thư mục Drive dùng cho ứng dụng. Tài khoản này sẽ được điền vào `DRIVE_ADMIN_EMAIL`.
- Hai tài khoản khác nhau để chạy thử: một `STUDENT` và một `LECTURER`. Có thể dùng ngoại lệ demo cho giảng viên.
- Trình duyệt và kết nối Internet để truy cập Google, tải thư viện Maven hoặc Docker image.

Người cấu hình Cloud project và chủ thư mục Drive có thể là cùng một người. Quyền quản trị Cloud project và quyền sở hữu/chia sẻ file Drive vẫn là hai loại quyền khác nhau. Không cần cấp Cloud IAM Owner/Editor cho sinh viên hoặc giảng viên chỉ để họ sử dụng app.

### Chọn một cách chạy

| Cách chạy | Cần cài trên máy |
|---|---|
| **Docker**, dùng theo mục 11 | Git và Docker có Compose v2. Trên Windows/macOS có thể dùng Docker Desktop |
| **Java**, dùng theo mục 12 | Git và **JDK 17**; Maven được tải bằng Maven Wrapper trong repo |

Nếu dùng Docker, không cần cài Java/Maven trên máy host chỉ để chạy app. Nếu build bằng Maven Wrapper, cần **JDK**, không chỉ một Java Runtime thiếu bộ biên dịch.

Liên kết cài đặt:

- [Git](https://git-scm.com/downloads)
- [Docker Desktop](https://docs.docker.com/desktop/)
- [Eclipse Temurin JDK 17](https://adoptium.net/temurin/releases/?version=17)

Trên Windows, nếu Docker Desktop yêu cầu WSL 2 hoặc bật ảo hóa, hoàn tất hướng dẫn của Docker rồi khởi động Docker Desktop trước khi chạy Compose.

## 3. Tải mã nguồn từ Git

### 3.1 Clone repository

Mở terminal/PowerShell và chạy:

```bash
git clone "https://github.com/nguyenttphuc2222-byte/google-oauth-soa-demo.git" seminar-oauth
cd seminar-oauth
```

### 3.2 Xác định đúng thư mục chạy

Các lệnh tiếp theo phải chạy tại thư mục có đồng thời:

```text
pom.xml
compose.yaml
Dockerfile
.env.example
mvnw
mvnw.cmd
.mvn/wrapper/maven-wrapper.properties
src/
```

Nếu repository bọc thêm một thư mục `seminar-oauth-app`, chạy thêm:

```bash
cd seminar-oauth-app
```

Đây được gọi là **thư mục ứng dụng** trong các bước bên dưới.

### 3.3 Chưa có JAR sau khi clone là bình thường

`seminar-app.jar`, `target/`, `.env` và `data/` được loại khỏi Git theo `.gitignore`. Người tải source từ Git cần tự cấu hình `.env`, rồi build bằng Docker hoặc Maven Wrapper. Không cần tìm một file JAR đã commit sẵn.

## 4. Tạo Google Cloud project

1. Mở [Google Cloud Console](https://console.cloud.google.com/) bằng tài khoản được phép cấu hình project.
2. Bấm bộ chọn project ở thanh trên cùng, chọn **New project / Create project**.
3. Đặt tên, ví dụ `Seminar OAuth Hub`.
4. Nếu triển khai OAuth Internal của trường, chọn đúng **Organization `tdtu.edu.vn`** và vị trí/folder mà bạn được phép tạo project.
5. Bấm **Create**, đợi tạo xong rồi chọn đúng project đó trước khi làm các bước tiếp theo.

Ghi lại các thông tin khác nhau sau:

| Thông tin | Ví dụ minh họa | Dùng ở đâu |
|---|---|---|
| Project name | `Seminar OAuth Hub` | Tên hiển thị trong Console |
| Project ID | `seminar-oauth-demo-xxxxx` | Định danh project trên Google Cloud |
| **Project number** | Một dãy số | Điền vào `GOOGLE_PROJECT_NUMBER` cho Picker |

Không dùng Project ID dạng chữ thay cho Project number.

**Nếu không tạo được project trong Organization:** tài khoản có thể thiếu quyền `resourcemanager.projects.create` hoặc bị chính sách trường giới hạn. Cần dùng project đã được cấp quyền hoặc liên hệ người quản trị. App không thể tự cấp quyền Google Cloud cho bạn. Xem [tài liệu tạo project của Google](https://cloud.google.com/resource-manager/docs/creating-managing-projects).

## 5. Bật các API cần thiết

Trong đúng Cloud project, mở **APIs & Services → Library**. Tìm và bấm **Enable** cho cả ba:

1. **Google Drive API**: upload, kiểm tra folder/file và cấp quyền chia sẻ.
2. **Gmail API**: gửi thông báo nộp bài và kết quả.
3. **Google Picker API**: chọn/xác nhận đúng thư mục trên trình duyệt.

Có thể kiểm tra lại tại **APIs & Services → Enabled APIs & services**. Việc chỉ tạo OAuth client hoặc API key chưa tự bật đủ các API.

Phần gửi thư dùng Gmail API qua OAuth; không cần cấu hình SMTP hoặc tạo mật khẩu ứng dụng Gmail cho luồng này.

## 6. Cấu hình Google Auth Platform

Tên menu có thể thay đổi nhẹ theo giao diện Console. Các mục cần tìm là **Branding**, **Audience**, **Data Access** và **Clients** trong **Google Auth Platform**. Giao diện cũ có thể hiển thị **OAuth consent screen**.

### 6.1 Khai báo ứng dụng

1. Mở **Google Auth Platform → Branding**.
2. Nếu chưa cấu hình, bấm **Get started**.
3. Điền tên app, email hỗ trợ và email liên hệ của người triển khai.
4. Ở bước **Audience**, chọn kiểu người dùng phù hợp bên dưới.
5. Hoàn tất thông tin và lưu cấu hình.

### 6.2 Internal cho tổ chức TDTU

Chọn **Internal** khi project thuộc Organization của trường và app phục vụ tài khoản trong tổ chức đó.

- Cả email `@student.tdtu.edu.vn` và `@tdtu.edu.vn` phải được tổ chức quản lý/chấp nhận tương ứng.
- Không cần thêm từng người vào danh sách Google test users theo cơ chế External Testing.
- Internal giới hạn đối tượng ở phía Google; backend vẫn kiểm tra danh tính, miền và vai trò.
- Chính sách Workspace có thể tiếp tục chặn một app hoặc một scope. Nếu bị chặn, cần người quản trị tổ chức xem xét.

Nếu không có lựa chọn Internal, kiểm tra lại Organization của project. Có email trường không tự bảo đảm project đang thuộc Organization hoặc bạn có quyền cấu hình nó.

### 6.3 External Testing khi Internal không phù hợp

Nếu bạn chủ động dùng **External** ở trạng thái **Testing**:

1. Mở **Audience → Test users → Add users**.
2. Thêm tất cả tài khoản tham gia thử, bao gồm chủ thư mục, sinh viên và giảng viên.
3. Lưu và kiểm tra lại màn hình consent bằng đúng tài khoản.

Danh sách test users của Google là lớp kiểm soát riêng với `STUDENT_EMAILS`/`LECTURER_EMAILS` của app. Bỏ allowlist trong backend không bỏ được giới hạn External Testing.

Với External Testing có scope Drive/Gmail, Google áp dụng thời hạn refresh token 7 ngày theo chính sách hiện hành. Khi grant không còn hợp lệ, cần kết nối lại; đây không phải mốc hết quyền Viewer của file. Xem [quy tắc hết hạn refresh token](https://developers.google.com/identity/protocols/oauth2#expiration).

Chuyển sang External cũng không làm Gmail cá nhân tự được backend TDTU chấp nhận. Để dùng rộng ngoài tổ chức, cần xử lý thêm chính sách tài khoản trong code và các yêu cầu xuất bản/xác minh OAuth tương ứng; không thuộc cách cài đặt local này.

### 6.4 Khai báo scope cần dùng

Trong **Data Access → Add or remove scopes**, khai báo bộ scope của ứng dụng nếu Console yêu cầu hoặc bạn dùng External:

| Scope | Mục đích |
|---|---|
| `openid` | Xác thực danh tính bằng OpenID Connect |
| `https://www.googleapis.com/auth/userinfo.email` | Thông tin email; tương ứng nhóm quyền `email` trong yêu cầu OIDC |
| `https://www.googleapis.com/auth/userinfo.profile` | Thông tin hồ sơ cơ bản; tương ứng nhóm quyền `profile` |
| `https://www.googleapis.com/auth/drive.file` | File/folder do app tạo hoặc được cho phép truy cập |
| `https://www.googleapis.com/auth/gmail.send` | Gửi thư thay mặt tài khoản đã đồng ý |

Backend xin quyền tăng dần: đăng nhập với nhóm danh tính; nộp bài cần Drive/Gmail; gửi kết quả cần Gmail; chủ thư mục thiết lập tự động cần Drive. Scope đã cấp trước đó có thể được Google trả kèm lại. App kiểm tra quyền thực nhận.

Không cần thêm các scope toàn bộ Drive, đọc toàn bộ hộp thư hoặc Sheets chỉ để chạy các chức năng đang có. Tham khảo [hướng dẫn cấu hình consent](https://developers.google.com/workspace/guides/configure-oauth-consent).

## 7. Tạo OAuth client cho ứng dụng web

1. Vào **Google Auth Platform → Clients → Create client**. Giao diện cũ có thể dùng **APIs & Services → Credentials → Create credentials → OAuth client ID**.
2. Chọn loại **Web application**.
3. Đặt tên, ví dụ `Seminar local web client`.
4. Với cấu hình local mặc định, điền:

| Trường trong Console | Giá trị |
|---|---|
| Authorized JavaScript origins | `http://localhost:8080` |
| Authorized redirect URIs | `http://localhost:8080/login/oauth2/code/google` |

5. Bấm **Create**.
6. Sao chép **Client ID** và **Client Secret** để điền vào `.env` ở mục 10.

Quy tắc cần nhớ:

- Origin là địa chỉ gốc, không có `/login/...`.
- Redirect URI là địa chỉ callback đầy đủ. Scheme, hostname, cổng và path phải khớp cấu hình app.
- `localhost` và `127.0.0.1` là hai hostname khác nhau đối với việc khớp cấu hình. Hướng dẫn này dùng nhất quán `localhost`.
- Backend Spring Boot sử dụng Client Secret. Không đưa secret này vào JavaScript, screenshot công khai hoặc Git.
- Mã nguồn hiện tại đọc Client ID/Secret từ `.env`; không yêu cầu tạo service account hoặc chép một file khóa service account vào app.

## 8. Tạo API key cho Picker và lấy Project number

### 8.1 Tạo API key

1. Vào **APIs & Services → Credentials → Create credentials → API key**.
2. Mở phần chỉnh sửa key vừa tạo.
3. Ở **Application restrictions**, chọn **Websites / HTTP referrers**.
4. Trong **Website restrictions**, thêm:

```text
http://localhost:8080/*
https://docs.google.com/*
```

5. Ở **API restrictions**, chọn **Restrict key**, cho phép **Google Picker API** và **Google Drive API**.
6. Lưu rồi sao chép key để điền `GOOGLE_API_KEY`.

Picker có iframe trên `docs.google.com`; cấu hình referrer cần cả địa chỉ app và địa chỉ này. API key xác định project của phần Picker, còn access token của người dùng cho phép truy cập dữ liệu riêng. Tham khảo [hướng dẫn tích hợp Picker](https://developers.google.com/workspace/drive/picker/guides/web-picker).

API key dùng trong trình duyệt nên có thể được nhìn thấy ở phía trình duyệt; cần giới hạn nơi và API được dùng. Nó không thay thế Client Secret hoặc quyền chia sẻ Drive.

### 8.2 Lấy Project number

Mở **IAM & Admin → Settings**, hoặc phần thông tin project trên Dashboard, và sao chép **Project number** dạng số.

Điền dãy số đó vào `GOOGLE_PROJECT_NUMBER`. Dùng cùng một Cloud project cho OAuth client, API key và Project number.

## 9. Chuẩn bị thư mục Drive và chọn chế độ chia sẻ

### 9.1 Chọn chế độ

| `DRIVE_ACCESS_MODE` | STUDENT | LECTURER | Khi dùng |
|---|---|---|---|
| **`ISOLATED`**, khuyến nghị | Editor trên thư mục riêng của mình | Viewer trên từng file được gửi cho mình | Bài nộp cần phạm vi riêng |
| `SHARED` | Editor trên thư mục lớp | Viewer trên thư mục lớp | Chủ đích dùng chung toàn bộ nội dung |
| `MANUAL` | Chủ thư mục chuẩn bị quyền thủ công | Quyền được chuẩn bị thủ công hoặc bổ sung trên từng file khi nộp | Giữ cơ chế cũ, không lưu credential tự cấp quyền |

`.env.example` của bản mới đề xuất `ISOLATED`. Nếu một `.env` cũ chưa có `DRIVE_ACCESS_MODE`, mặc định runtime giữ `MANUAL` để không tự thay phạm vi chia sẻ.

Trong `SHARED`, quyền thư mục kế thừa xuống nội dung: sinh viên có thể xem/chỉnh sửa bài khác theo quyền Editor; giảng viên có thể xem bài khác trực tiếp trên Drive. Backend vẫn giới hạn bài được xem/xét duyệt trong website, nhưng điều đó không thu hẹp quyền đã cấp ở Drive. Xem [cơ chế kế thừa quyền](https://developers.google.com/workspace/drive/api/guides/manage-sharing).

### 9.2 Tạo root cho ISOLATED

1. Đăng nhập Google Drive bằng tài khoản sẽ làm chủ thư mục.
2. Trong **My Drive**, tạo thư mục tên **Seminar - Minh chứng**.
3. Chọn **Share → General access → Restricted**.
4. Thư mục gốc chỉ có chủ sở hữu; không đặt trong thư mục cha đã chia sẻ rộng.
5. Mở thư mục và lấy ID trong URL:

```text
https://drive.google.com/drive/folders/ID_THU_MUC
```

6. Điền phần `ID_THU_MUC`, không phải cả URL, vào `DRIVE_FOLDER_ID`.
7. Điền email sở hữu thư mục vào `DRIVE_ADMIN_EMAIL`.

Không chia sẻ Editor cho cả lớp trên root khi dùng ISOLATED. App sẽ tự tạo thư mục con và chuẩn bị quyền cho từng sinh viên sau khi chủ thư mục kết nối.

Trong bản này, cơ chế tự động yêu cầu thư mục **My Drive do đúng tài khoản cấu hình sở hữu**; không áp dụng cho Google Shared drives. Chế độ tên `SHARED` của app cũng không có nghĩa là Google Shared drive.

Nếu thư mục cũ đã chia sẻ cho nhiều người, nên giữ nó và tạo root riêng mới để dùng ISOLATED. App không tự xóa quyền cũ hoặc di chuyển bài đã nộp.

### 9.3 Nếu chọn SHARED hoặc MANUAL

- **SHARED:** dùng root do tài khoản cấu hình sở hữu, đặt `DRIVE_ACCESS_MODE=SHARED`. Khi kết nối ở mục 13, chủ thư mục cần tích xác nhận hiểu phạm vi thư mục chung. App không chấp nhận root đang có quyền `anyone`, `domain`, `group` hoặc email ngoài TDTU cho cơ chế tự động.
- **MANUAL:** đặt `DRIVE_ACCESS_MODE=MANUAL`. Chủ thư mục cấp Editor cho từng sinh viên cần nộp vào root. App vẫn cần sinh viên cấp Drive/Gmail, xác nhận đúng folder và có khả năng chia sẻ file cho giảng viên. Không cần bước lưu credential chủ thư mục ở mục 13.

App không tự chuyển Owner hoặc hạ các quyền mạnh hơn đã có. Trong SHARED, giảng viên đã có Editor từ trước không tự bị hạ xuống Viewer; chủ thư mục cần xem xét quyền cũ nếu muốn mức truy cập chính xác.

## 10. Tạo và điền file env

### 10.1 Tạo file cấu hình

Tại thư mục ứng dụng, sao chép file mẫu **cho lần cài đặt đầu tiên**.

Windows PowerShell:

```powershell
Copy-Item .env.example .env
notepad .env
```

macOS/Linux:

```bash
cp .env.example .env
```

Mở `.env` bằng trình soạn thảo văn bản. File phải tên đúng `.env`, không phải `.env.txt`. Nếu đã có cấu hình đang dùng, giữ file đó và sửa những biến cần thiết thay vì chép đè mẫu.

App nhập file này như Java properties. Mỗi dòng dùng `TEN_BIEN=gia_tri`, không thêm `export` và không bọc toàn bộ giá trị trong dấu nháy. Lưu văn bản UTF-8.

### 10.2 Tạo khóa mã hóa cho Drive tự động

Với `ISOLATED` hoặc `SHARED`, tạo **một khóa ngẫu nhiên 32 byte, biểu diễn Base64** cho bản cài đặt. Trên PowerShell:

```powershell
$seminarDriveKeyBytes = New-Object byte[] 32
$seminarDriveRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$seminarDriveRng.GetBytes($seminarDriveKeyBytes)
$seminarDriveRng.Dispose()
[Convert]::ToBase64String($seminarDriveKeyBytes)
```

Hoặc trên macOS/Linux nếu có OpenSSL:

```bash
openssl rand -base64 32
```

Sao chép chuỗi kết quả vào `DRIVE_ADMIN_TOKEN_KEY`. Dấu `=` ở cuối chuỗi Base64 là hợp lệ. Không dùng một mật khẩu ngắn hoặc khóa có sẵn trong test thay cho kết quả ngẫu nhiên này.

Giữ nguyên khóa qua các lần khởi động và cập nhật app. Không tạo khóa mới cho mỗi lần chạy: credential chủ thư mục lưu trong H2 cần đúng khóa để giải mã. Khóa này không phải Google Client Secret hoặc API key.

### 10.3 Cấu hình đầy đủ cho local

Các dòng `configure-...` và chỗ trống dưới đây cần được thay bằng dữ liệu thực theo hướng dẫn. Khối này tương ứng các biến của bản 2.4.0:

```properties
# OAuth client loai Web application cua Cloud project
GOOGLE_CLIENT_ID=configure-client-id
GOOGLE_CLIENT_SECRET=configure-client-secret
GOOGLE_REDIRECT_URI=http://localhost:8080/login/oauth2/code/google

# Dia chi goc dung tao link trong email
APP_BASE_URL=http://localhost:8080
SERVER_PORT=8080

# API key va project NUMBER cua cung Cloud project
GOOGLE_API_KEY=
GOOGLE_PROJECT_NUMBER=

# ID root My Drive vua tao, khong dan ca URL
DRIVE_FOLDER_ID=
DRIVE_ACCESS_MODE=ISOLATED

# THAY email demo bang chu so huu root thuc te cua ban
DRIVE_ADMIN_EMAIL=523h0080@student.tdtu.edu.vn
# Dan chuoi Base64 ngau nhien 32 byte vua tao
DRIVE_ADMIN_TOKEN_KEY=

# Phan vai tu dong cua TDTU
AUTO_DOMAIN_ROLES=true
LECTURER_OVERRIDE_EMAIL=524h0133@student.tdtu.edu.vn
ALLOWED_WORKSPACE_DOMAINS=student.tdtu.edu.vn,tdtu.edu.vn

# Cac bien co che cu, khong quyet dinh vai tro khi AUTO_DOMAIN_ROLES=true
AUTO_STUDENT_LOGIN=true
STUDENT_EMAILS=
LECTURER_EMAILS=

MAX_UPLOAD_MB=15
DISPLAY_TIMEZONE=Asia/Ho_Chi_Minh
COOKIE_SECURE=false

# H2 tu khoi tao, khong can cai database server
DATABASE_URL=jdbc:h2:file:./data/seminar;DB_CLOSE_ON_EXIT=FALSE
DATABASE_USER=sa
DATABASE_PASSWORD=
```

### 10.4 Kiểm tra các giá trị cần thay

| Biến | Lấy từ đâu hoặc cần làm gì |
|---|---|
| `GOOGLE_CLIENT_ID` | OAuth client Web application, mục 7 |
| `GOOGLE_CLIENT_SECRET` | Secret của chính client đó |
| `GOOGLE_API_KEY` | API key đã giới hạn cho Picker, mục 8 |
| `GOOGLE_PROJECT_NUMBER` | Project number dạng số, cùng project |
| `DRIVE_FOLDER_ID` | ID root trong My Drive |
| `DRIVE_ADMIN_EMAIL` | Email thực sự sở hữu root; không giữ email demo nếu bạn dùng thư mục của tài khoản khác |
| `DRIVE_ADMIN_TOKEN_KEY` | Khóa Base64 32 byte, mục 10.2; bắt buộc cho chế độ tự động |
| `LECTURER_OVERRIDE_EMAIL` | Giữ tài khoản demo nếu dùng nó; đổi sang email TDTU phù hợp hoặc để trống để tắt ngoại lệ |

Các email sinh viên thường không cần điền vào `.env` khi `AUTO_DOMAIN_ROLES=true`. Muốn demo xét duyệt bằng một email sinh viên khác, phải cấu hình ngoại lệ tương ứng; tự điền vào `LECTURER_EMAILS` không thay đổi vai trò trong chế độ tự động này.

Để dùng lại danh sách cho phép cũ, đặt `AUTO_DOMAIN_ROLES=false`. Khi đó `LECTURER_EMAILS` được ưu tiên; `AUTO_STUDENT_LOGIN=true` vẫn có thể cho sinh viên TDTU đăng nhập tự động. Muốn chỉ nhận người trong danh sách, đặt thêm `AUTO_STUDENT_LOGIN=false` và điền hai danh sách bằng email phân cách dấu phẩy, không trùng nhau.

## 11. Chạy bằng Docker

Chỉ cần thực hiện mục 11 **hoặc** mục 12. Không chạy hai bản cùng chiếm cổng 8080.

### 11.1 Kiểm tra Docker

Khởi động Docker Desktop nếu dùng nó, rồi chạy:

```bash
docker --version
docker compose version
docker info
```

`docker info` phải kết nối được Docker Engine. Trên Windows, dùng Linux containers cho Dockerfile của dự án.

### 11.2 Build và khởi động

Từ thư mục có `compose.yaml` và `.env`:

```bash
docker compose up -d --build
```

Lần đầu cần tải image và thư viện Maven nên có thể lâu hơn các lần sau. Dockerfile build source thành JAR, rồi chạy bằng Java 17 trong container.

### 11.3 Kiểm tra trạng thái

```bash
docker compose ps
docker compose logs --tail=100 seminar-app
```

Mở:

- [Trang đăng nhập](http://localhost:8080/login)
- [Health check](http://localhost:8080/health)

Health của bản này phải tương đương:

```json
{"status":"ok","version":"2.4.0"}
```

Health thành công xác nhận app đã khởi động; cần làm tiếp mục 13 và 15 để xác nhận cấu hình Google/Drive hoạt động.

Để xem log liên tục, dùng `docker compose logs -f seminar-app`; bấm `Ctrl+C` chỉ thoát việc xem log. App chạy nền vẫn còn.

### 11.4 Dừng hoặc áp dụng thay đổi cấu hình

Dừng tạm, giữ container và dữ liệu:

```bash
docker compose stop
```

Khởi động lại container đã dừng mà không đổi cấu hình:

```bash
docker compose start
```

Sau khi sửa `.env`, tạo lại container để nhận biến mới:

```bash
docker compose up -d --build --force-recreate
```

`docker compose restart` không nạp lại các biến môi trường của container từ `.env` đã sửa.

## 12. Chạy bằng Java và Maven Wrapper

### 12.1 Kiểm tra JDK

```bash
java -version
```

Dùng JDK 17 để khớp môi trường build/chạy của dự án. Nếu Maven Wrapper không tìm được Java, kiểm tra `JAVA_HOME` trỏ tới thư mục JDK và `PATH` chứa thư mục `bin` của JDK.

### 12.2 Build và chạy trên Windows

Trong PowerShell tại thư mục ứng dụng:

```powershell
.\mvnw.cmd -v
.\mvnw.cmd clean package
java -jar target/seminar-app.jar
```

### 12.3 Build và chạy trên macOS/Linux

```bash
sh mvnw -v
sh mvnw clean package
java -jar target/seminar-app.jar
```

Maven Wrapper tải Maven theo cấu hình repo; không cần cài Maven toàn cục. `package` chạy bộ kiểm thử rồi tạo JAR trong `target/`. Không cần đăng nhập Google thật cho bộ test tự động cục bộ.

Mở [http://localhost:8080](http://localhost:8080) và [http://localhost:8080/health](http://localhost:8080/health). Bấm `Ctrl+C` tại terminal chạy JAR để dừng.

Chạy lệnh `java -jar ...` từ **thư mục ứng dụng**, không `cd target` trước khi chạy: app tìm `.env` theo thư mục làm việc hiện tại, và URL H2 tương đối cũng phụ thuộc nơi đó.

Các script `start.bat`/`start.sh` ưu tiên `seminar-app.jar` ở thư mục gốc nếu có. Sau khi tự build hoặc sửa source, dùng trực tiếp `java -jar target/seminar-app.jar` để chắc chắn chạy JAR mới build.

## 13. Chủ thư mục kết nối Drive một lần

Thực hiện sau khi app đã chạy, áp dụng cho `ISOLATED` hoặc `SHARED`.

1. Mở website và đăng nhập bằng **đúng email `DRIVE_ADMIN_EMAIL`**.
2. Vào **Thiết lập Drive** trên thanh điều hướng. Tài khoản này có thể mang vai trò STUDENT hoặc LECTURER; quyền thiết lập dựa trên email chủ thư mục được cấu hình.
3. Bấm **Cho phép quản lý thư mục Drive** và đồng ý quyền Drive bằng chính tài khoản đó.
4. Quay lại trang thiết lập. Nếu chưa có refresh token, bấm cấp lại quyền Drive rồi tiếp tục.
5. Nếu dùng SHARED, đọc và tích xác nhận phạm vi của thư mục chung.
6. Bấm **Kết nối thư mục lớp**. Picker chỉ hiển thị đúng root đã cấu hình; chọn thư mục đó.
7. Backend kiểm tra chủ sở hữu, quyền quản lý, root ID và phạm vi chia sẻ. Khi hợp lệ, trang hiển thị **Đã sẵn sàng**.

Sau đó chủ thư mục có thể đăng xuất. Backend lưu refresh token quản lý **mã hóa AES-256-GCM** trong H2, dùng khóa cấu hình để làm mới quyền khi cần. Không cần đăng nhập giữ phiên của chủ thư mục trong suốt thời gian người khác nộp bài.

Đây vẫn là sự ủy quyền có thể hết hiệu lực hoặc bị thu hồi. Nếu đổi Google client, root, chế độ hoặc khóa mã hóa, cần chủ thư mục kiểm tra/kết nối lại cho cấu hình mới. Giữ đúng database và khóa để duy trì kết nối qua lần khởi động lại.

Nút **Ngừng tự cấp quyền** xóa credential quản lý đã lưu; không tự xóa file, folder hay các quyền Drive đã cấp. Thu hồi quyền Google trong OAuth Lab của chủ thư mục cũng xóa credential tự động của app.

Chi tiết về phạm vi và xử lý dữ liệu cũ: [DRIVE_SETUP.md](DRIVE_SETUP.md).

## 14. Sinh viên và giảng viên sử dụng app

### 14.1 Sinh viên nộp bài

1. Đăng nhập bằng email TDTU nhận vai trò STUDENT.
2. Vào **Nộp báo cáo**. Trong chế độ tự động đã sẵn sàng, backend chuẩn bị quyền theo tài khoản đăng nhập.
3. Bấm **Kết nối Drive & Gmail** nếu chưa đủ scope. Chọn đúng tài khoản đang đăng nhập khi Google hỏi lại.
4. App thử kết nối folder theo ID. Nếu Google cần xác nhận file/folder lần đầu cho `drive.file`, bấm **Kết nối thư mục nộp bài** và chọn đúng thư mục duy nhất được hiển thị.
5. Nhập tên nhóm, đề tài và email giảng viên. Có thể nhập email trước lần đăng nhập đầu tiên của giảng viên; backend vẫn kiểm tra email nhận bài.
6. Chọn PDF/Word/PowerPoint/ZIP hợp lệ. Mặc định tối đa 15 MB, có thể cấu hình `MAX_UPLOAD_MB` từ 1 đến 50.
7. Tích **Gửi bản sao email cho tôi (Cc)** nếu cần; bỏ chọn thì thư không có Cc.
8. Bấm **Nộp bài & gửi thông báo**. Kiểm tra chi tiết bài, trạng thái gửi thư và Sent của sinh viên.

Trong ISOLATED, đích là thư mục con tên có email sinh viên; không cần đưa ID từng thư mục con vào `.env`. Picker chọn folder, còn file trên máy được backend upload qua Drive API.

### 14.2 Giảng viên xét duyệt

1. Mở email thông báo, bấm **Xem và xét duyệt bài nộp**.
2. Đăng nhập app bằng đúng email đã được sinh viên chọn. Nếu đang dùng tài khoản khác, đăng xuất/chuyển hồ sơ trình duyệt.
3. Với Word/PDF/slide, bấm **Xem tài liệu**. Với ZIP, dùng nút tải; nếu Drive yêu cầu, đăng nhập đúng tài khoản được chia sẻ.
4. Bấm **Cho phép gửi Gmail** nếu chưa cấp quyền gửi kết quả.
5. Nhập nhận xét nếu cần, chọn Hợp lệ/Không hợp lệ, chọn Cc độc lập với lựa chọn của sinh viên.
6. Bấm **Xác nhận & gửi kết quả**. Sinh viên nhận email từ giảng viên và có thể xem lại kết quả trong app.

Giảng viên ghi nhận xét/quyết định trong H2; Viewer áp dụng cho file trên Drive. Không cần Editor trên tài liệu sinh viên chỉ để xét duyệt trong website.

### 14.3 Các điểm cần biết khi sử dụng

- Không có giới hạn mặc định “sau 3 tiếng mất quyền xem/tải”. App không đặt thời hạn Viewer; file còn được truy cập theo quyền Drive đang có và chính sách tổ chức.
- Phiên app mặc định hết sau 90 phút không hoạt động; khi cần, đăng nhập lại. Hết phiên hoặc access token không tự xóa quyền Viewer.
- Nếu upload/chia sẻ chưa hoàn tất, tiếp tục đúng lượt nộp chưa hoàn tất thay vì tạo thêm lượt ngay.
- Với thông báo lỗi đã lưu, dùng chức năng gửi lại bằng đúng tài khoản người gửi. Trạng thái UNKNOWN cần đối chiếu Sent trước khi xác nhận gửi lại.
- Không chỉnh email Cc tùy ý: Cc là chính người gửi đã xác thực khi chọn ô nhận bản sao.

## 15. Kiểm tra lần chạy đầu tiên

Nên dùng hai hồ sơ Chrome/Edge riêng cho sinh viên và giảng viên. Đăng nhập tài khoản thứ hai trong cùng một phiên app có thể thay đổi tài khoản đang thao tác.

| Kiểm tra | Kết quả mong đợi |
|---|---|
| Mở `/health` | `status=ok`, `version=2.4.0` |
| Đăng nhập tài khoản chủ thư mục | Thấy Thiết lập Drive và kết nối thành Đã sẵn sàng |
| Sinh viên hợp lệ chưa có trong allowlist đăng nhập | Nhận STUDENT; ở ISOLATED có folder riêng và Editor trên folder đó |
| Giảng viên hoặc tài khoản ngoại lệ đăng nhập | Nhận LECTURER |
| Bấm kết nối thư mục nộp | Chỉ có đúng thư mục đích của tài khoản/chế độ |
| Nộp một PDF hoặc Word | Có file trên Drive và thư thông báo gửi từ sinh viên |
| Giảng viên mở link | Chỉ đúng giảng viên được giao xem/duyệt được bài trong app |
| Giảng viên xác nhận | Có kết quả H2 và email từ giảng viên về sinh viên |
| Nộp thử ZIP | Có nút tải hoặc mở Drive để tải |
| Chọn/bỏ chọn Cc | Header Cc có/không có theo lựa chọn |

Mở **OAuth Lab** khi muốn xem scope thực nhận, thời hạn token, khả năng refresh và nhật ký các lời gọi Google của phiên. Màn hình này không hiển thị token thô hoặc Client Secret.

Để kiểm tra sự riêng tư ở ISOLATED, dùng hai sinh viên **không phải chủ thư mục**, với dữ liệu demo. Chủ thư mục vẫn có quyền quản lý các thư mục; dùng tài khoản chủ sở hữu làm sinh viên kiểm tra sẽ không chứng minh được sự cô lập giữa sinh viên thường.

## 16. Đổi cổng hoặc địa chỉ truy cập

### 16.1 Ví dụ đổi từ 8080 sang 8000

Đổi đồng bộ trong `.env`:

```properties
SERVER_PORT=8000
APP_BASE_URL=http://localhost:8000
GOOGLE_REDIRECT_URI=http://localhost:8000/login/oauth2/code/google
COOKIE_SECURE=false
```

Sau đó cập nhật Cloud project:

- OAuth client JavaScript origin: `http://localhost:8000`.
- OAuth client redirect URI: `http://localhost:8000/login/oauth2/code/google`.
- API key Website restrictions: thêm `http://localhost:8000/*`, tiếp tục giữ `https://docs.google.com/*`.

Khởi động lại JAR, hoặc dùng `docker compose up -d --build --force-recreate`. Compose hiện tại dùng `SERVER_PORT` cho cả hai phía của port mapping nên không phải sửa cứng số 8080 trong YAML.

### 16.2 Khi người nhận email mở app trên máy khác

`localhost` luôn trỏ về chính máy đang mở trình duyệt. Link email `http://localhost:8080/...` chỉ phù hợp khi mở bằng các hồ sơ trình duyệt trên cùng máy đang chạy app.

Muốn nhiều người dùng từ máy khác, cần triển khai một địa chỉ HTTPS mà họ truy cập được, rồi cập nhật đồng bộ `APP_BASE_URL`, `GOOGLE_REDIRECT_URI`, origin/redirect của OAuth client và referrer của API key. Với HTTPS, dùng `COOKIE_SECURE=true`.

Compose mặc định chỉ bind cổng host ở `127.0.0.1`. Triển khai ra mạng cần cấu hình máy chủ/reverse proxy và callback phù hợp; chỉ thay `APP_BASE_URL` không tự công khai app. Hướng dẫn local này không tự thiết lập hosting hoặc proxy HTTPS.

## 17. Dữ liệu và cách cập nhật mã nguồn

### 17.1 H2 lưu ở đâu

- **Chạy JAR:** mặc định `data/seminar.mv.db` dưới thư mục làm việc của app.
- **Chạy Docker:** database nằm trong `/app/data`, được giữ bằng named volume `seminar_data` của Compose; tên volume thực tế thường có tiền tố project Compose.
- H2 tự tạo schema khi khởi động. Không cần cài MySQL/PostgreSQL hoặc chạy SQL thủ công để bắt đầu.
- Cách chạy JAR và Docker mặc định không dùng chung vị trí dữ liệu. Chuyển cách chạy có thể cho một database mới; không coi Dashboard trống là bằng chứng database cũ đã bị xóa.

H2 giữ bài, quyết định, trạng thái email, gợi ý giảng viên, mapping folder riêng và credential quản lý đã mã hóa. Token dùng nộp bài/Gmail của từng người nằm trong phiên server; người dùng có thể cần đăng nhập/cấp quyền lại sau restart.

File minh chứng trên Drive là dữ liệu riêng với H2. Xóa database không tự xóa file Drive, và giữ file Drive không tự khôi phục được toàn bộ hồ sơ xét duyệt H2.

### 17.2 Cập nhật khi dùng Docker

Giữ nguyên `.env`, khóa mã hóa, thư mục/project name Compose và volume đang dùng. Sao lưu trước khi cập nhật. Trong đúng thư mục ứng dụng:

```bash
git pull
docker compose up -d --build --force-recreate
```

Không dùng `docker compose down -v` để cập nhật thông thường: tùy chọn `-v` xóa volume dữ liệu. Nếu chuyển thư mục hoặc đổi project name Compose, kiểm tra volume cũ thay vì khởi tạo nhầm database mới.

### 17.3 Cập nhật khi dùng JAR

Dừng JAR, sao lưu dữ liệu khi không còn tiến trình ghi database, cập nhật source, build lại rồi chạy `target/seminar-app.jar`. Giữ đúng `.env`, database và thư mục làm việc.

Chỉ chạy một instance trên database H2 này. Không mở đồng thời bản Docker/JAR khác vào cùng một file H2.

### 17.4 Thông tin cần giữ riêng

Không commit `.env`, Client Secret, khóa mã hóa, token hoặc database thực lên Git. `.gitignore` đã loại `.env`/`data`/JAR build; kiểm tra trước khi commit nếu bạn đổi vị trí lưu.

Giữ bản sao database và khóa cấu hình qua các nơi được bảo vệ phù hợp. Nếu mất/đổi khóa, chủ thư mục phải kết nối lại; app không có cơ chế giải mã credential cũ bằng khóa mới.

## 18. Xử lý lỗi thường gặp

| Hiện tượng | Cần kiểm tra hoặc xử lý |
|---|---|
| Không thấy `compose.yaml`, `.env.example` hoặc `pom.xml` | Đang đứng sai thư mục; quay lại mục 3.2 |
| Báo `.env` không tồn tại hoặc nút Google bị khóa | Tạo đúng `.env`, kiểm tra không thành `.env.txt`, điền Client ID/Secret thật và khởi động lại |
| Docker không kết nối được daemon | Mở Docker Desktop/Engine; kiểm tra `docker info` và Linux containers |
| Cổng 8080 đã được dùng | Dừng bản app khác hoặc đổi cổng đồng bộ theo mục 16 |
| `JAVA_HOME`/Java không tìm thấy, `release version 17 not supported` | Cài/chọn đúng JDK 17 và mở terminal mới; kiểm tra `java -version` cùng Maven Wrapper `-v` |
| Maven/Docker không tải được dependency/image | Kiểm tra Internet, proxy hoặc chính sách mạng; lỗi tải dependency không được sửa bằng cách đổi Google OAuth credentials |
| `redirect_uri_mismatch` | So sánh URI callback app gửi với Authorized redirect URIs, bao gồm http/https, hostname, port và path |
| `invalid_client` | Client ID và Secret phải cùng Web client, cùng project dự định sử dụng; lưu lại `.env` và tạo lại container/khởi động lại JAR |
| `org_internal`, tài khoản không thuộc tổ chức hoặc không có Internal | Kiểm tra Organization của project và tài khoản Workspace; Internal không dành cho Gmail cá nhân bên ngoài |
| Google yêu cầu test user hoặc từ chối External Testing | Thêm đúng tài khoản vào Audience → Test users; đây không phải allowlist của backend |
| `admin_policy_enforced`, ứng dụng/scope bị Workspace chặn | Cần người quản trị tổ chức xem xét chính sách; đổi code frontend không bỏ qua được hạn chế này |
| Đã xác thực Google nhưng app từ chối tài khoản | Kiểm tra đúng email, miền, trạng thái xác minh và `hd`; role tự động của bản này chỉ nhận phạm vi TDTU đã nêu |
| Email sinh viên demo vẫn nhận STUDENT | Kiểm tra `LECTURER_OVERRIDE_EMAIL`; `LECTURER_EMAILS` không ghi đè vai trò khi bật `AUTO_DOMAIN_ROLES` |
| Không thấy Thiết lập Drive | Đăng nhập đúng `DRIVE_ADMIN_EMAIL`; vai trò LECTURER tự nó không cho phép thiết lập chủ thư mục |
| Chưa có khóa mã hóa hoặc khóa không hợp lệ | Tạo đúng 32 byte dạng Base64; dán toàn bộ vào `DRIVE_ADMIN_TOKEN_KEY`, không thêm dấu nháy |
| Kết nối root bị từ chối trong ISOLATED | Kiểm tra đúng chủ sở hữu My Drive, đúng ID và quyền chia sẻ của root/cha; dùng root riêng mới nếu root cũ đã chia sẻ rộng |
| Picker báo developer key invalid hoặc không mở | Kiểm tra Google Picker API/Drive API đã bật, API key cùng project, Project number dạng số và cả hai referrer ở mục 8 |
| Picker không hiện folder | Kiểm tra ID đúng, tài khoản có ACL truy cập, chủ thư mục đã kết nối, đúng project/client; không mở rộng danh sách Drive để chữa sai ID |
| Nút nộp bị khóa, thiếu scope hoặc chưa kết nối folder | Hoàn tất Drive/Gmail của sinh viên và xác nhận đúng folder. Đăng nhập thành công chưa đủ quyền gọi API |
| `account_changed` khi đồng ý thêm quyền | Google đang chọn tài khoản khác người đăng nhập app; cấp lại bằng đúng tài khoản, nên dùng hồ sơ trình duyệt riêng |
| Drive trả 403, không cấp được Viewer | Kiểm tra scope, quyền người gọi và chính sách chia sẻ của trường. Tiếp tục đúng lượt sau khi xử lý quyền |
| Không có refresh token hoặc `invalid_grant` của chủ thư mục | Cấp lại quyền và kết nối trên Thiết lập Drive. Kiểm tra grant bị thu hồi/hết hiệu lực, đổi client/root/mode/key, hoặc External Testing |
| Mở file/ZIP yêu cầu quyền | Google Drive đang dùng tài khoản khác giảng viên được chia sẻ, hoặc ACL/chính sách đã đổi; chọn đúng tài khoản trên Drive |
| Thư đến nhưng link không mở trên máy khác | `APP_BASE_URL` đang là localhost hoặc địa chỉ chưa được triển khai tới máy nhận; xem mục 16.2 |
| Không thấy email kết quả | Kiểm tra đúng người nhận, trạng thái trong app, Sent của đúng người gửi và Spam của người nhận. Với UNKNOWN, đối chiếu Sent trước khi gửi lại |
| Sau một thời gian app yêu cầu đăng nhập | Phiên mặc định 90 phút không hoạt động; đăng nhập lại. Điều này không tự xóa dữ liệu H2 hoặc quyền Viewer |
| `Database may be already in use` | Dừng tiến trình khác đang dùng cùng file H2, chỉ giữ một instance |
| Sửa `.env` mà Docker vẫn dùng giá trị cũ | Tạo lại container bằng `docker compose up -d --build --force-recreate`; chỉ restart container là chưa đủ |
| Sửa source nhưng ứng dụng vẫn là bản cũ | Build lại và chạy `target/seminar-app.jar`, hoặc build lại Docker; `start.bat` có thể đang ưu tiên JAR ở gốc |

Khi nhờ hỗ trợ, cung cấp thông báo lỗi, bước gặp lỗi và log đã che thông tin riêng. Không gửi nguyên Client Secret, token, khóa mã hóa hoặc `.env` chứa bí mật.


## 19. Tài liệu tham khảo

- [Tạo và quản lý Cloud project](https://cloud.google.com/resource-manager/docs/creating-managing-projects)
- [Cấu hình OAuth consent và scope](https://developers.google.com/workspace/guides/configure-oauth-consent)
- [Tạo credentials cho Google Workspace APIs](https://developers.google.com/workspace/guides/create-credentials)
- [Tích hợp Google Picker vào ứng dụng web](https://developers.google.com/workspace/drive/picker/guides/web-picker)
- [Scope Google Drive](https://developers.google.com/workspace/drive/api/guides/api-specific-auth)
- [Scope Gmail API](https://developers.google.com/workspace/gmail/api/auth/scopes)
- [OAuth cho ứng dụng web server và offline access](https://developers.google.com/identity/protocols/oauth2/web-server)
- [Thời hạn refresh token](https://developers.google.com/identity/protocols/oauth2#expiration)
- [OpenID Connect của Google](https://developers.google.com/identity/openid-connect/openid-connect)
- [Chia sẻ và kế thừa quyền Drive](https://developers.google.com/workspace/drive/api/guides/manage-sharing)
- [DocsView.setFileIds](https://developers.google.com/workspace/drive/picker/reference/picker.docsview.setfileids)
