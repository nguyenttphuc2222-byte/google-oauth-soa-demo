# Seminar OAuth Hub

Hướng dẫn tạo Google Cloud project, cấu hình OAuth/Google Drive và chạy chương trình bằng Docker hoặc Java. Cấu hình mặc định dùng `http://localhost:8080`, tài khoản TDTU và chế độ Drive `ISOLATED`.

Người triển khai thực hiện cấu hình một lần cho mỗi môi trường. Sinh viên và giảng viên truy cập website đã triển khai không cần tự tạo Google Cloud project.

## Mục lục

1. [Chuẩn bị và tải mã nguồn](#1-chuẩn-bị-và-tải-mã-nguồn)
2. [Tạo và cấu hình Google Cloud project](#2-tạo-và-cấu-hình-google-cloud-project)
3. [Chuẩn bị thư mục Google Drive](#3-chuẩn-bị-thư-mục-google-drive)
4. [Cấu hình .env](#4-cấu-hình-env)
5. [Chạy bằng Docker](#5-chạy-bằng-docker)
6. [Chạy bằng Java và Maven Wrapper](#6-chạy-bằng-java-và-maven-wrapper)
7. [Kết nối Drive sau khi khởi động](#7-kết-nối-drive-sau-khi-khởi-động)
8. [Đổi cổng hoặc địa chỉ truy cập](#8-đổi-cổng-hoặc-địa-chỉ-truy-cập)
9. [Dữ liệu và cập nhật chương trình](#9-dữ-liệu-và-cập-nhật-chương-trình)
10. [Xử lý lỗi cấu hình và khởi chạy](#10-xử-lý-lỗi-cấu-hình-và-khởi-chạy)

## 1. Chuẩn bị và tải mã nguồn

### Tài khoản và phần mềm

- Tài khoản được phép tạo hoặc quản lý Google Cloud project và OAuth client.
- Tài khoản TDTU thực sự sở hữu thư mục minh chứng trên Google Drive.
- Trình duyệt, kết nối Internet và [Git](https://git-scm.com/downloads).
- Chọn **một** cách chạy:

| Cách chạy | Phần mềm cần cài |
|---|---|
| Docker — khuyến nghị | Docker Engine và Compose V2; Windows/macOS có thể dùng [Docker Desktop](https://docs.docker.com/desktop/) |
| Java | [JDK 17](https://adoptium.net/temurin/releases/?version=17); Maven được tải qua Maven Wrapper trong mã nguồn |

Chạy Docker không yêu cầu cài Java/Maven trên máy host. Không cần cài MySQL/PostgreSQL: ứng dụng dùng H2 và tự tạo bảng khi khởi động.

### Clone repository

```bash
git clone https://github.com/nguyenttphuc2222-byte/google-oauth-soa-demo.git seminar-oauth
```

```bash
cd seminar-oauth
```

Các lệnh trong README phải chạy tại thư mục chứa `pom.xml`, `compose.yaml`, `Dockerfile`, `.env.example`, `mvnw`, `mvnw.cmd`, `.mvn/` và `src/`. 

## 2. Tạo và cấu hình Google Cloud project

### 2.1. Tạo project

1. Mở [Google Cloud Console](https://console.cloud.google.com/).
2. Bấm bộ chọn project ở thanh trên cùng → **New project / Create project**.
3. Đặt tên project, ví dụ `Seminar OAuth Hub`.
4. Nếu dùng OAuth Internal của trường, chọn đúng **Organization `tdtu.edu.vn`** và vị trí mà tài khoản được phép tạo project.
5. Bấm **Create**, đợi hoàn tất và chọn project vừa tạo.

Giữ cùng một project trong toàn bộ các bước tạo OAuth client, API key và lấy Project number. **Project ID dạng chữ khác Project number dạng số.** Ứng dụng cần Project number cho `GOOGLE_PROJECT_NUMBER`.

Nếu không tạo được project trong Organization, dùng project được cấp quyền hoặc liên hệ quản trị viên. Email trường không tự cấp quyền quản trị Google Cloud.

### 2.2. Bật API

Vào **APIs & Services → Library**, tìm và bấm **Enable** cho cả ba:

1. **Google Drive API**.
2. **Gmail API**.
3. **Google Picker API**.

Kiểm tra lại tại **Enabled APIs & services**. Không cần cấu hình SMTP, mật khẩu ứng dụng Gmail, service account hoặc Sheets API cho cách chạy này.

### 2.3. Cấu hình Google Auth Platform

1. Mở **Google Auth Platform → Branding**; bấm **Get started** nếu chưa thiết lập.
2. Nhập tên ứng dụng, email hỗ trợ và email liên hệ.
3. Trong **Audience**, chọn đối tượng phù hợp:

| Lựa chọn | Cách cấu hình |
|---|---|
| **Internal** | Dùng khi project thuộc tổ chức Workspace của trường và các tài khoản sử dụng thuộc tổ chức đó |
| **External**, trạng thái **Testing** | Thêm các tài khoản sử dụng vào **Audience → Test users**, gồm chủ thư mục, sinh viên và giảng viên |

Nếu không thấy Internal, kiểm tra Organization của project. Chính sách Workspace có thể chặn ứng dụng hoặc quyền yêu cầu; cần quản trị viên xử lý nếu gặp hạn chế này.

Backend hiện áp dụng chính sách tài khoản TDTU. Chọn External hoặc thêm Google Test users không tự cho phép Gmail cá nhân đăng nhập vào app.

4. Trong **Data Access → Add or remove scopes**, khai báo các scope khi Console yêu cầu hoặc khi dùng External:

```text
openid
https://www.googleapis.com/auth/userinfo.email
https://www.googleapis.com/auth/userinfo.profile
https://www.googleapis.com/auth/drive.file
https://www.googleapis.com/auth/gmail.send
```

5. Lưu cấu hình. Không thêm quyền toàn bộ Drive hoặc đọc toàn bộ Gmail chỉ để chạy ứng dụng.

### 2.4. Tạo OAuth client

1. Vào **Google Auth Platform → Clients → Create client**.
2. Chọn **Web application**, nhập tên client.
3. Thêm các địa chỉ sau:

| Trường trên Console | Giá trị local mặc định |
|---|---|
| Authorized JavaScript origins | `http://localhost:8080` |
| Authorized redirect URIs | `http://localhost:8080/login/oauth2/code/google` |

4. Bấm **Create**, lưu **Client ID** và **Client Secret** để điền vào `.env`. Tải file cấu hình JSON nếu Console cung cấp khi tạo client và giữ riêng.

Origin chỉ là địa chỉ gốc; redirect URI có đầy đủ đường dẫn callback. Scheme, hostname, cổng và path phải khớp cấu hình ứng dụng. Dùng nhất quán `localhost`; không tự thay bằng `127.0.0.1` nếu chưa đăng ký địa chỉ tương ứng.

### 2.5. Tạo API key và lấy Project number

1. Vào **APIs & Services → Credentials → Create credentials → API key**.
2. Mở chỉnh sửa key vừa tạo.
3. Ở **Application restrictions**, chọn **Websites / HTTP referrers**.
4. Thêm cả hai Website restrictions:

```text
http://localhost:8080/*
https://docs.google.com/*
```

5. Ở **API restrictions**, chọn **Restrict key**, chỉ cho phép **Google Picker API** và **Google Drive API**.
6. Lưu và sao chép key vào `GOOGLE_API_KEY`.
7. Mở **IAM & Admin → Settings** hoặc thông tin project trên Dashboard, sao chép **Project number** dạng số vào `GOOGLE_PROJECT_NUMBER`.

Không bỏ referrer `https://docs.google.com/*`, vì Picker sử dụng iframe tại địa chỉ này. OAuth client, API key và Project number phải thuộc cùng project.

## 3. Chuẩn bị thư mục Google Drive

### 3.1. Chọn phạm vi chia sẻ

| `DRIVE_ACCESS_MODE` | STUDENT | LECTURER | Điều kiện sử dụng |
|---|---|---|---|
| **`ISOLATED` — khuyến nghị** | Editor trên thư mục riêng của mình | Viewer trên từng file được giao | Giữ bài nộp riêng giữa các sinh viên |
| `SHARED` | Editor trên thư mục chung | Viewer trên thư mục chung | Chấp nhận quyền kế thừa và việc truy cập nội dung của người khác qua Drive |
| `MANUAL` | Chủ thư mục chuẩn bị quyền thủ công | Quyền được chuẩn bị thủ công hoặc bổ sung trên file khi nộp | Không dùng kết nối tự cấp quyền của chủ thư mục |

`SHARED` của ứng dụng là chế độ chia sẻ, **không phải Google Shared drive**. Cơ chế tự động yêu cầu thư mục trong **My Drive**, do đúng tài khoản cấu hình sở hữu.

Trong SHARED, giới hạn trên website không thu hẹp quyền đã cấp trên Drive. Ứng dụng không tự hạ quyền mạnh hơn đã có hoặc xóa các quyền chia sẻ cũ.

### 3.2. Tạo thư mục cho ISOLATED

1. Đăng nhập Google Drive bằng tài khoản TDTU sẽ làm chủ thư mục.
2. Trong **My Drive**, tạo thư mục riêng tên **Seminar - Minh chứng**.
3. Mở **Share → General access → Restricted**.
4. Thư mục gốc chỉ có chủ sở hữu; không đặt trong thư mục cha đã chia sẻ rộng và không cấp Editor cho cả lớp trên thư mục gốc.
5. Mở thư mục, sao chép phần ID trong URL:

```text
https://drive.google.com/drive/folders/ID_THU_MUC
```

6. Điền **chỉ ID**, không phải cả URL, vào `DRIVE_FOLDER_ID`.
7. Điền email thực sự sở hữu thư mục vào `DRIVE_ADMIN_EMAIL`.

Nếu thư mục cũ đã chia sẻ rộng, tạo thư mục gốc mới cho ISOLATED và giữ dữ liệu cũ. App sẽ từ chối kết nối ISOLATED nếu thư mục gốc còn quyền của người khác/nhóm/domain/anyone có thể kế thừa xuống nội dung.

### 3.3. Nếu chọn SHARED hoặc MANUAL

- **SHARED:** đặt `DRIVE_ACCESS_MODE=SHARED`; root phải do tài khoản cấu hình sở hữu, không có quyền `anyone`, `domain`, `group` hoặc email ngoài TDTU. Chủ thư mục phải xác nhận phạm vi chia sẻ khi kết nối ở mục 7.
- **MANUAL:** đặt `DRIVE_ACCESS_MODE=MANUAL`; chủ thư mục cấp Editor cho từng sinh viên cần nộp vào root và bảo đảm có thể chia sẻ file cho giảng viên. Không cần lưu kết nối quản lý tự động của chủ thư mục ở mục 7.

## 4. Cấu hình .env

### 4.1. Tạo file

Chỉ sao chép mẫu khi cài đặt mới. Nếu đã có `.env` đang dùng, giữ file và sửa các mục cần thiết.

Windows PowerShell:

```powershell
Copy-Item .env.example .env
notepad .env
```

macOS/Linux:

```bash
cp .env.example .env
```

Lưu đúng tên **`.env`**, không phải `.env.txt`, tại thư mục có `compose.yaml` và `pom.xml`. Mỗi dòng dùng `TEN_BIEN=gia_tri`, không thêm `export`, không bọc giá trị trong dấu nháy; lưu UTF-8.

### 4.2. Sinh khóa mã hóa cho Drive tự động

Với ISOLATED/SHARED, tạo **một khóa ngẫu nhiên 32 byte dạng Base64**.

Windows PowerShell:

```powershell
$seminarDriveKeyBytes = New-Object byte[] 32
$seminarDriveRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$seminarDriveRng.GetBytes($seminarDriveKeyBytes)
$seminarDriveRng.Dispose()
[Convert]::ToBase64String($seminarDriveKeyBytes)
```

macOS/Linux nếu có OpenSSL:

```bash
openssl rand -base64 32
```

Sao chép kết quả vào `DRIVE_ADMIN_TOKEN_KEY`; dấu `=` cuối chuỗi là hợp lệ. **Chỉ sinh khi cài đặt mới/chưa có khóa; giữ nguyên khóa qua các lần chạy và cập nhật.** Không dùng Client Secret hoặc một mật khẩu tùy ý thay cho khóa này.

### 4.3. Nội dung cấu hình local

Điền dữ liệu thật vào các chỗ trống và thay hai giá trị `configure-...` trước khi chạy:

```properties
GOOGLE_CLIENT_ID=configure-client-id
GOOGLE_CLIENT_SECRET=configure-client-secret
GOOGLE_REDIRECT_URI=http://localhost:8080/login/oauth2/code/google
APP_BASE_URL=http://localhost:8080
SERVER_PORT=8080

GOOGLE_API_KEY=
GOOGLE_PROJECT_NUMBER=

DRIVE_FOLDER_ID=
DRIVE_ACCESS_MODE=ISOLATED
DRIVE_ADMIN_EMAIL=
DRIVE_ADMIN_TOKEN_KEY=

AUTO_DOMAIN_ROLES=true
LECTURER_OVERRIDE_EMAIL=524h0133@student.tdtu.edu.vn
ALLOWED_WORKSPACE_DOMAINS=student.tdtu.edu.vn,tdtu.edu.vn
AUTO_STUDENT_LOGIN=true
STUDENT_EMAILS=
LECTURER_EMAILS=

MAX_UPLOAD_MB=15
DISPLAY_TIMEZONE=Asia/Ho_Chi_Minh
COOKIE_SECURE=false
DATABASE_URL=jdbc:h2:file:./data/seminar;DB_CLOSE_ON_EXIT=FALSE
DATABASE_USER=sa
DATABASE_PASSWORD=
```

Các giá trị cần lấy hoặc xác nhận:

| Biến | Cách điền |
|---|---|
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Cùng OAuth client loại Web application ở mục 2.4 |
| `GOOGLE_API_KEY`, `GOOGLE_PROJECT_NUMBER` | API key và số project ở mục 2.5 |
| `DRIVE_FOLDER_ID` | ID thư mục gốc đã chuẩn bị ở mục 3 |
| `DRIVE_ADMIN_EMAIL` | Email TDTU thực sự sở hữu thư mục, không tự giữ email thành viên nhóm khi dùng thư mục của người khác |
| `DRIVE_ADMIN_TOKEN_KEY` | Chuỗi Base64 ở mục 4.2; cần cho ISOLATED/SHARED |
| `LECTURER_OVERRIDE_EMAIL` | Giữ `524h0133@student.tdtu.edu.vn` nếu dùng tài khoản này làm giảng viên; đổi sang email TDTU phù hợp hoặc để trống để tắt ngoại lệ |

Khi `AUTO_DOMAIN_ROLES=true`, email `@student.tdtu.edu.vn` nhận STUDENT, email `@tdtu.edu.vn` nhận LECTURER; tài khoản ngoại lệ được ưu tiên. Không cần thêm từng email vào hai danh sách. `LECTURER_EMAILS` không ghi đè quy tắc này. Muốn dùng allowlist cũ, đặt `AUTO_DOMAIN_ROLES=false` và cấu hình các danh sách phù hợp.

## 5. Chạy bằng Docker

Mở Docker Desktop/Engine trước. Trên Windows, dùng **Linux containers**; hoàn tất WSL 2/ảo hóa nếu Docker yêu cầu.

Tại thư mục ứng dụng, kiểm tra:

```bash
docker compose version
docker info
```

Build và khởi động:

```bash
docker compose up -d --build
```

Lần đầu cần tải image và thư viện Maven. Sau khi hoàn tất, kiểm tra:

```bash
docker compose ps
docker compose logs --tail=100 seminar-app
```

Mở [http://localhost:8080](http://localhost:8080) và [http://localhost:8080/health](http://localhost:8080/health). `/health` phải trả `status=ok` và phiên bản tương ứng với mã nguồn đã build. Sau đó thực hiện kết nối Drive ở mục 7.

| Công việc | Lệnh |
|---|---|
| Dừng, giữ dữ liệu | `docker compose stop` |
| Chạy lại container đã dừng, cấu hình không đổi | `docker compose start` |
| Xem log liên tục | `docker compose logs -f seminar-app` |
| Áp dụng `.env` hoặc mã nguồn mới | `docker compose up -d --build --force-recreate` |

`docker compose restart` không nạp lại biến môi trường từ `.env` đã sửa. **Không dùng `docker compose down -v` để dừng hoặc cập nhật thông thường:** `-v` xóa volume dữ liệu.

## 6. Chạy bằng Java và Maven Wrapper

Đây là lựa chọn thay Docker. Không chạy đồng thời hai bản chiếm cùng cổng hoặc cùng file H2.

Kiểm tra JDK:

```bash
java -version
```

Nếu Maven không tìm được Java, kiểm tra `JAVA_HOME` trỏ tới JDK 17 và `PATH` có thư mục `bin` của JDK.

Windows PowerShell:

```powershell
.\mvnw.cmd clean package
java -jar target/seminar-app.jar
```

macOS/Linux:

```bash
sh mvnw clean package
java -jar target/seminar-app.jar
```

Maven Wrapper tự tải Maven và build JAR. Mở [http://localhost:8080](http://localhost:8080), kiểm tra `/health`, rồi làm mục 7. Bấm `Ctrl+C` tại terminal để dừng.

**Chạy JAR từ thư mục có `.env`, không chuyển vào `target/`.** Ứng dụng tìm cấu hình và dữ liệu tương đối theo thư mục làm việc. Sau khi build, dùng `target/seminar-app.jar` để tránh chạy nhầm JAR cũ ở thư mục gốc mà `start.bat`/`start.sh` có thể ưu tiên.

## 7. Kết nối Drive sau khi khởi động

Áp dụng cho ISOLATED/SHARED, thực hiện một lần cho mỗi môi trường:

1. Mở ứng dụng, đăng nhập Google bằng đúng email `DRIVE_ADMIN_EMAIL`.
2. Vào **Thiết lập Drive** trên thanh điều hướng. Quyền thiết lập dựa trên email chủ thư mục, không chỉ dựa vào vai trò STUDENT/LECTURER.
3. Bấm **Cho phép quản lý thư mục Drive**, đồng ý quyền Drive bằng chính tài khoản đó.
4. Nếu dùng SHARED, đọc và tích xác nhận phạm vi thư mục chung.
5. Bấm **Kết nối thư mục lớp**, chọn đúng thư mục gốc đã cấu hình trong Picker.
6. Chờ kiểm tra quyền sở hữu và chia sẻ; trang phải hiển thị **Đã sẵn sàng**.

Sau đó chủ thư mục có thể đăng xuất. Giữ H2 cùng khóa `DRIVE_ADMIN_TOKEN_KEY` để duy trì kết nối qua lần khởi động lại. Nếu thiếu refresh token, dùng nút cấp lại quyền Drive rồi kết nối lại.

Sinh viên vẫn phải cấp quyền Drive/Gmail của chính mình khi sử dụng chức năng. Nếu app yêu cầu **Kết nối thư mục nộp bài**, chọn thư mục đích được hiển thị. Ở ISOLATED, đây là thư mục con riêng; **không thay `DRIVE_FOLDER_ID` bằng ID của từng thư mục con**.

Khi đổi Google client, root ID, chế độ chia sẻ hoặc thông tin kết nối mất hiệu lực, chủ thư mục cần kết nối lại. Với External Testing có scope Drive/Gmail, refresh token có thể hết hạn theo chính sách Google, thường sau 7 ngày. Không sinh đè khóa mã hóa cũ để xử lý lỗi kết nối.

## 8. Đổi cổng hoặc địa chỉ truy cập

### Đổi cổng local, ví dụ 8080 → 8000

Sửa đồng bộ trong `.env`:

```properties
SERVER_PORT=8000
APP_BASE_URL=http://localhost:8000
GOOGLE_REDIRECT_URI=http://localhost:8000/login/oauth2/code/google
COOKIE_SECURE=false
```

Cập nhật trên Google Cloud:

- OAuth JavaScript origin: `http://localhost:8000`.
- OAuth redirect URI: `http://localhost:8000/login/oauth2/code/google`.
- API key Website restrictions: `http://localhost:8000/*` và `https://docs.google.com/*`.

Tạo lại container bằng `docker compose up -d --build --force-recreate`, hoặc dừng rồi chạy lại JAR.

### Truy cập từ máy khác

`localhost` trỏ về máy đang mở trình duyệt. Muốn người dùng trên nhiều máy cùng truy cập, cần triển khai địa chỉ HTTPS chung và cập nhật `APP_BASE_URL`, `GOOGLE_REDIRECT_URI`, OAuth origins/redirects và referrer của API key. Với HTTPS, đặt `COOKIE_SECURE=true`.

Compose mặc định chỉ bind `127.0.0.1`. Cần cấu hình máy chủ/reverse proxy và mạng phù hợp; chỉ đổi `APP_BASE_URL` không tự công khai ứng dụng.

## 9. Dữ liệu và cập nhật chương trình

| Cách chạy | Vị trí H2 mặc định |
|---|---|
| Java/JAR | `data/seminar.mv.db` dưới thư mục làm việc |
| Docker | `/app/data` trong container, gắn named volume `seminar_data` của project Compose |

Hai cách chạy không tự dùng chung dữ liệu. Chỉ chạy một instance trên một file H2.

Khi cập nhật, giữ nguyên **`.env`, khóa mã hóa, database/volume và thư mục hoặc project name Compose**. Sao lưu khi ứng dụng đã dừng.

Docker:

```bash
docker compose stop
git pull
docker compose up -d --build --force-recreate
```

Java: dừng app, cập nhật source, build lại và chạy `target/seminar-app.jar` trong cùng thư mục cấu hình/dữ liệu.

Nếu `.env` cũ chưa có `DRIVE_ACCESS_MODE`, ứng dụng giữ MANUAL. Chỉ chuyển sang ISOLATED/SHARED sau khi chuẩn bị root và khóa, rồi kết nối chủ thư mục ở mục 7.

Không commit `.env`, Client Secret, token, khóa mã hóa hoặc database thực lên Git. Không xóa volume hay thay khóa để thử sửa lỗi; đổi tên thư mục/project Compose có thể làm app sử dụng một volume mới.

## 10. Xử lý lỗi cấu hình và khởi chạy

| Hiện tượng | Cách kiểm tra/xử lý |
|---|---|
| Không tìm thấy `pom.xml` hoặc `compose.yaml` | Mở terminal tại đúng thư mục ứng dụng ở mục 1 |
| Thiếu `.env` hoặc nút Google bị khóa | Kiểm tra tên file, không để `.env.txt`; điền Client ID/Secret thật rồi khởi động lại |
| Docker không kết nối daemon | Mở Docker Desktop/Engine, kiểm tra `docker info` và Linux containers |
| Không tải được image/dependency | Kiểm tra Internet, DNS, proxy hoặc chính sách mạng của Docker/Maven |
| Cổng bị chiếm | Dừng bản app khác hoặc đổi cổng đồng bộ theo mục 8 |
| Maven không tìm thấy Java hoặc không hỗ trợ release 17 | Chọn đúng JDK 17, kiểm tra `JAVA_HOME`/`PATH` và mở terminal mới |
| `redirect_uri_mismatch` | So khớp chính xác scheme, host, cổng và path callback với OAuth client |
| `invalid_client` | Client ID và Secret phải thuộc cùng Web client; lưu cấu hình và tạo lại container/chạy lại JAR |
| Không có Internal hoặc báo `org_internal` | Kiểm tra Organization của project và tài khoản Workspace |
| External Testing từ chối tài khoản | Thêm đúng email vào Audience → Test users |
| `admin_policy_enforced` | Nhờ quản trị viên Workspace xem xét quyền ứng dụng/scope |
| Google đăng nhập được nhưng app từ chối | Dùng tài khoản TDTU hợp lệ, kiểm tra cấu hình miền và ngoại lệ giảng viên |
| Không thấy Thiết lập Drive | Đăng nhập đúng email `DRIVE_ADMIN_EMAIL` |
| Khóa Drive không hợp lệ | Dùng đúng 32 byte Base64, không thêm dấu nháy; giữ khóa cũ nếu đã có dữ liệu |
| ISOLATED từ chối root | Kiểm tra đúng chủ sở hữu My Drive, đúng ID và root không có quyền chia sẻ cũ/cha chia sẻ rộng |
| Picker báo developer key invalid | Kiểm tra API đã bật, cùng project, Project number dạng số, hai referrer và API restrictions ở mục 2.5 |
| Picker không hiện thư mục | Kiểm tra ID, tài khoản có quyền và chủ thư mục đã hoàn tất mục 7 |
| Không có refresh token / `invalid_grant` | Chủ thư mục cấp lại quyền và kết nối; kiểm tra grant hết hạn/bị thu hồi hoặc client/root/mode đã đổi |
| Database đang được sử dụng | Dừng tiến trình khác dùng cùng H2; chỉ giữ một instance |
| Sửa `.env` nhưng Docker chưa đổi | Chạy `docker compose up -d --build --force-recreate`, không chỉ restart |
| Đã build nhưng vẫn chạy bản cũ | Chạy JAR trong `target/`, hoặc build lại Docker |
| Link app không mở trên máy khác | Dùng địa chỉ HTTPS đã triển khai thay cho localhost, theo mục 8 |

Khi cần hỗ trợ, cung cấp bước gặp lỗi và log đã che thông tin riêng; không gửi nguyên `.env`, Client Secret, token hoặc khóa mã hóa.
