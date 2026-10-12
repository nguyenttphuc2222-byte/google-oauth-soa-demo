# Hướng dẫn chạy dự án Google OAuth 2.0

Áp dụng cho bộ mã nguồn `OAuth2.0_final(1).zip`: giao diện Spring Boot đăng nhập Google, phân quyền Sinh viên/Giảng viên và quản lý hồ sơ sinh viên. Các lệnh trong tài liệu dành cho **Windows, terminal PowerShell trong VS Code**.

**Kết quả cần đạt:** mở `http://localhost:8080`, đăng nhập tài khoản Google của TDTU, được chuyển đến `/home` và sử dụng đúng giao diện theo vai trò.

## 1. Chuẩn bị phần mềm và mã nguồn

| Thành phần | Cần làm | Mục đích |
|---|---|---|
| Docker Desktop | Cài từ [trang chính thức](https://docs.docker.com/desktop/setup/install/windows-install/), dùng WSL 2 và Linux containers; mở Docker Desktop, chờ engine sẵn sàng | Build và chạy toàn bộ dịch vụ bằng môi trường có sẵn trong Dockerfile |
| WSL 2 | Nếu Docker yêu cầu, làm theo hướng dẫn cài WSL của trình cài; khởi động lại Windows khi được yêu cầu | Chạy Linux containers trên Windows |
| VS Code | Cài từ [code.visualstudio.com](https://code.visualstudio.com/), dùng để mở thư mục và sửa `.env` | Thao tác cấu hình và chạy lệnh tại đúng thư mục |
| Chrome hoặc Edge | Chuẩn bị trình duyệt và tài khoản Google Workspace của TDTU | Thực hiện đăng nhập và giữ cookie phiên ứng dụng |

Theo cách chạy bằng Docker trong README này, Java, Maven và thư viện Python được cài trong image. Máy Windows không phải cài riêng các thư viện của từng service.

1. Giải nén ZIP vào một thư mục mới.
2. Trong VS Code, chọn **File → Open Folder** và mở thư mục **có trực tiếp `compose.yaml`**.
3. Chọn **Terminal → New Terminal**, dùng PowerShell.
4. Kiểm tra:

```powershell
Get-Location
Test-Path .\compose.yaml
Test-Path .\.env.example
docker --version
docker compose version
docker info
```

Hai lệnh `Test-Path` phải trả `True`. `docker info` phải lấy được thông tin engine; nếu báo không kết nối được daemon, hãy mở Docker Desktop và chờ hoàn tất khởi động.

| File hoặc thư mục | Vai trò khi chạy |
|---|---|
| `compose.yaml` | Cấu hình chạy bình thường |
| `compose.demo.yaml` | Cấu hình bổ sung để bật Giảng viên giả lập |
| `.env.example` | Mẫu cấu hình để tạo `.env` |
| `google-oauth-spring/` | Giao diện, Google OAuth/OIDC, phiên và kiểm tra quyền |
| `student-service/` | Cung cấp dữ liệu hồ sơ; SQLite được khởi tạo tự động |
| `product-service/` | Dịch vụ có sẵn trong dự án; Compose yêu cầu dịch vụ này khỏe trước khi chạy auth-service |

Các thư viện Java được khai báo trong `google-oauth-spring/pom.xml`; thư viện Python nằm trong các file requirements của từng service. Dockerfile tự cài chúng khi build, không cần cài từng gói bằng tay. Không cần cài thêm MySQL hoặc SQL Server để chạy bộ này.

## 2. Tạo hoặc chọn project trong Google Cloud Console

Nếu đã có project/Web client dùng được cho bài seminar, hãy chọn lại project đó và kiểm tra callback ở mục 4. Chỉ tạo project mới khi chưa có cấu hình phù hợp.

1. Mở [Google Cloud Console](https://console.cloud.google.com/), đăng nhập tài khoản có quyền quản lý project.
2. Bấm ô chọn project trên thanh trên cùng → **New Project**.
3. Đặt tên dễ nhận biết, ví dụ `TDTU-OAuth-Demo`.
4. Chọn Organization/Location phù hợp. Nếu muốn dùng **Internal**, project phải thuộc tổ chức Google Cloud của trường và người chạy phải có quyền tạo hoặc sử dụng project ở đó.
5. Bấm **Create**, chờ tạo xong rồi chọn đúng project vừa tạo.

**Ý nghĩa:** project là nơi Google quản lý cấu hình ứng dụng và OAuth client. Có email trường chưa đủ để tự tạo project Internal; quyền và tổ chức chứa project cũng phải phù hợp.

## 3. Cấu hình Google Auth Platform

Trong project đã chọn, mở **Google Auth Platform**. Nếu chưa thiết lập, bấm **Get started** và đi qua các bước:

1. **App information/Branding:** điền tên ứng dụng, ví dụ `TDTU OAuth Demo`, và User support email.
2. **Audience:** chọn nhánh phù hợp dưới đây.
3. **Contact information:** điền email liên hệ của người quản lý ứng dụng.
4. Hoàn tất phần xác nhận rồi bấm **Create**. Nếu project đã cấu hình, chỉnh các mục tương ứng trong Branding/Audience.

| Audience | Khi dùng | Ý nghĩa |
|---|---|---|
| Internal | Project thuộc tổ chức của trường và tài khoản demo thuộc tổ chức đó | Google giới hạn người có thể cấp quyền cho ứng dụng trong tổ chức |
| External | Project cá nhân/không có lựa chọn Internal hoặc dùng nhánh thử được trường cho phép | Google cho phép phạm vi tài khoản ngoài tổ chức; code vẫn áp dụng quy tắc chỉ nhận danh tính TDTU |

Internal là giới hạn ở Google, **không phải quyền Giảng viên** trong code. Lỗi `org_internal` xuất hiện khi tài khoản nằm ngoài tổ chức chứa project; sửa `.env` không đổi được Audience.

Với External để thử cục bộ, có thể giữ trạng thái **Testing**. Hai bộ mã nguồn chỉ yêu cầu phạm vi đăng nhập cơ bản `openid`, `email`, `profile`; theo tài liệu Google, nhóm scope này có ngoại lệ đối với yêu cầu danh sách test user và thời hạn cấp quyền 7 ngày. Nếu project yêu cầu thêm scope khác, phải làm theo quy định Testing tương ứng. Chính sách Google Workspace của trường vẫn có thể hạn chế ứng dụng.

Trong **Data Access → Add or remove scopes**, chọn các scope đăng nhập tương ứng `openid`, `userinfo.email`, `userinfo.profile`, cập nhật và lưu nếu cần. Code Spring yêu cầu `openid,profile,email`. Các scope này cung cấp danh tính, email và thông tin hồ sơ cơ bản; không cần bật Gmail API hoặc Drive API cho demo đăng nhập.

## 4. Tạo Web client và đăng ký callback

1. Mở **Google Auth Platform → Clients → Create client**.
2. Chọn **Application type: Web application**.
3. Đặt tên, ví dụ `TDTU Spring localhost`.
4. Trong **Authorized redirect URIs**, bấm **Add URI** và dán nguyên dòng:

```text
http://localhost:8080/login/oauth2/code/google
```

5. Với luồng web server này, có thể để **Authorized JavaScript origins** trống. Đây không phải nơi thay thế cho redirect URI.
6. Bấm **Create**. Lưu đầy đủ **Client ID** và **Client Secret** khi Google hiển thị; với client mới, secret được hiển thị tại lúc tạo. Nếu tải JSON cấu hình, giữ riêng tại máy để lấy đúng các giá trị.
7. Nếu dùng client đã có, mở client đó → thêm hoặc kiểm tra URI trên → **Save**.

**Vì sao cần client:** Client ID nhận diện ứng dụng với Google; Client Secret được ứng dụng dùng khi đổi authorization code lấy token.

**Vì sao URI phải chính xác:** Google chỉ chuyển kết quả về callback đã đăng ký. `localhost`, cổng `8080`, đường dẫn `/login/oauth2/code/google` và dấu `/` cuối phải khớp. URI ở Google và trong `.env` phải giống nhau. Không dán callback của module Python vào cấu hình Spring.

Nếu vẫn sử dụng cả hai bản, một Web client có thể đăng ký cả hai callback; mỗi ứng dụng dùng URI riêng của mình.

## 5. Tạo `.env` và điền cấu hình

Tại thư mục có `compose.yaml`, chạy:

```powershell
if (-not (Test-Path -LiteralPath .env)) {
    Copy-Item -LiteralPath .env.example -Destination .env
}
```

Lệnh chỉ tạo file khi chưa có `.env`, giúp giữ cấu hình đã điền trước đó. Trong VS Code, mở `.env` và điền ID/Secret đầy đủ; giữ callback đúng như sau:

```dotenv
GOOGLE_CLIENT_ID=YOUR_GOOGLE_CLIENT_ID
GOOGLE_CLIENT_SECRET=YOUR_GOOGLE_CLIENT_SECRET
STUDENT_SERVICE_KEY=PASTE_THE_GENERATED_KEY_HERE
GOOGLE_REDIRECT_URI=http://localhost:8080/login/oauth2/code/google
PRODUCT_SERVICE_URL=http://product-service:8000/products
```

Các giá trị `YOUR_...` và `PASTE_...` là chỗ mẫu cần thay. Không để nguyên, không rút gọn giá trị bằng dấu `...`.

| Biến | Cách điền và ý nghĩa |
|---|---|
| `GOOGLE_CLIENT_ID` | Client ID của Web client đã tạo |
| `GOOGLE_CLIENT_SECRET` | Client Secret của chính Web client đó |
| `GOOGLE_REDIRECT_URI` | Callback của Spring ở cổng 8080 |
| `STUDENT_SERVICE_KEY` | Khóa riêng tối thiểu 32 ký tự, dùng giữa auth-service và student-service; không phải Client Secret của Google |
| `PRODUCT_SERVICE_URL` | Giữ `http://product-service:8000/products` khi chạy Compose; `product-service` là tên service trong mạng Docker |

### Tự tạo STUDENT_SERVICE_KEY trên PowerShell

Nếu `.env` còn dòng khóa mẫu, sao chép **toàn bộ khối lệnh** sau vào PowerShell. Khối này dùng thư viện có sẵn của Windows/.NET, không yêu cầu cài Python trên máy:

```powershell
$keyBytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($keyBytes)
$rng.Dispose()
$serviceKey = [Convert]::ToBase64String($keyBytes)
$envPath = (Resolve-Path -LiteralPath .env).Path
$envText = [System.IO.File]::ReadAllText($envPath)
$marker = 'PASTE_THE_GENERATED_KEY_HERE'
if (-not $envText.Contains($marker)) {
    throw 'Khong thay khoa mau. Neu da co khoa hop le, giu nguyen khoa do.'
}
[System.IO.File]::WriteAllText($envPath, $envText.Replace($marker, $serviceKey))
Write-Host 'Da tao STUDENT_SERVICE_KEY trong .env'
```

Khóa được ghi thẳng vào `.env` và không in ra terminal. Compose chuyển cùng khóa cho hai service để các request hồ sơ được chấp nhận. Nếu đã có khóa riêng hợp lệ, bỏ qua bước tạo khóa.

Sau khi script chạy, mở lại hoặc Reload file `.env` trong VS Code trước khi chỉnh tiếp, tránh lưu đè bằng bản nội dung cũ đang mở.

Mỗi nhóm giữ `.env` riêng và tạo khóa riêng. Khi chuyển mã nguồn cho người khác, gửi `.env.example` thay cho `.env` có secret thật.

## 6. Build và chạy dự án

Trong terminal tại thư mục chứa `compose.yaml`:

```powershell
docker compose --env-file .env -f compose.yaml config --quiet
```

Lệnh kiểm tra Compose và các biến bắt buộc mà không in cấu hình chứa secret. Nếu lỗi, sửa `.env` hoặc thư mục làm việc trước khi tiếp tục. Kiểm tra này không xác nhận ID/Secret được Google chấp nhận.

Chạy toàn bộ dịch vụ:

```powershell
docker compose --env-file .env -f compose.yaml up --build -d
docker compose --env-file .env -f compose.yaml ps
```

Lần đầu cần tải image, thư viện và build Java nên phải có Internet. Chờ `product-service` và `student-service` ở trạng thái khỏe, `auth-service` đang chạy. Nếu cần xem lỗi:

```powershell
docker compose --env-file .env -f compose.yaml logs --tail 100 auth-service student-service
```

| Địa chỉ kiểm tra | Kết quả cần thấy |
|---|---|
| `http://localhost:8080` | Trang đăng nhập của ứng dụng |
| `http://localhost:8001/health` | JSON `{"status":"ok"}` |
| `http://localhost:8000/` | Thông báo Product Service đang chạy |

Mở trang đăng nhập bằng **localhost** và dùng nhất quán hostname này trong toàn bộ lượt đăng nhập. Các cổng được bind về `127.0.0.1` để dùng demo trên máy hiện tại.

## 7. Đăng nhập và kiểm tra quyền Sinh viên

1. Mở `http://localhost:8080`.
2. Bấm nút đăng nhập Google.
3. Chọn tài khoản thật có email dạng `523h0080@student.tdtu.edu.vn` và hoàn tất màn hình Google nếu được yêu cầu.
4. Ứng dụng phải chuyển về `http://localhost:8080/home` và hiển thị **Hồ sơ sinh viên**.
5. Kiểm tra MSSV giữ đúng chuỗi trước `@`, kể cả chữ như `523h0080`.
6. Chỉnh một trường được phép, ví dụ địa chỉ → bấm **Lưu thay đổi** → phải thấy **Đã lưu thông tin.** Làm mới trang để kiểm tra giá trị vẫn được lưu.

Sinh viên được sửa họ tên, tuổi, giới tính, số điện thoại, địa chỉ, email sinh viên trong hồ sơ và email cá nhân. **MSSV và năm nhập học chỉ xem**, không được sửa qua form cập nhật.

Hồ sơ được tạo khi sinh viên đăng nhập lần đầu. Năm nhập học hiện được tạo ngẫu nhiên trong khoảng 2016–2023 để phục vụ demo; đây không phải dữ liệu lấy từ Google. Email có thể sửa trong hồ sơ cũng không thay đổi email đăng nhập hoặc vai trò.

## 8. Đăng nhập và kiểm tra quyền Giảng viên

### Có tài khoản Giảng viên thật

1. Bấm **Đăng xuất**, ở trang xác nhận bấm **Logout**.
2. Bắt đầu đăng nhập Google lại và chọn tài khoản có email `@tdtu.edu.vn`.
3. `/home` phải hiển thị danh sách sinh viên, ô tìm kiếm và liên kết mở chi tiết.
4. Tìm theo tên hoặc MSSV; mở hồ sơ đã được tạo từ lượt đăng nhập sinh viên.
5. Trang chi tiết chỉ cho xem và không có nút lưu sửa hồ sơ.

Nếu danh sách trống, hãy cho ít nhất một sinh viên đăng nhập trước để tạo hồ sơ rồi quay lại tài khoản Giảng viên.

### Chưa có tài khoản Giảng viên thật: dùng giả lập cục bộ

Tại terminal gốc của dự án, chạy:

```powershell
docker compose --env-file .env -f compose.yaml -f compose.demo.yaml up --build -d
```

Đăng xuất tài khoản đang dùng, mở `http://localhost:8080/demo` → bấm **Vào với Giảng viên Demo**. Trang sau đăng nhập hiển thị danh tính Giảng viên Demo và nhãn **Giảng viên giả lập**; có thể thử danh sách, tìm kiếm và xem chi tiết.

Giả lập dùng danh tính cố định do code tạo; không phải hộp thư Google thật và không xác nhận một tài khoản Giảng viên thật đăng nhập Google thành công. Google Client ID/Secret vẫn cần điền để chạy cấu hình Compose bình thường.

Tắt giả lập và tạo lại auth-service bằng cấu hình chính:

```powershell
docker compose --env-file .env -f compose.yaml up -d --force-recreate auth-service
```

Các đường dẫn demo bị chặn trong chế độ bình thường; phiên demo cũ kết thúc khi auth-service khởi động lại.

## 9. Luồng xử lý cần hiểu khi đọc code

| Bước | Code liên quan | Dữ liệu và kết quả |
|---|---|---|
| Bắt đầu đăng nhập | Spring Security, `/oauth2/authorization/google` | Trình duyệt được chuyển đến Google |
| Google trả kết quả | `/login/oauth2/code/google` | Spring xử lý code/token và xác thực danh tính OIDC |
| Gán vai trò | `TdtuOidcUserService`, `TdtuRolePolicy` | Email đã xác minh, `hd` và miền email → `ROLE_STUDENT` hoặc `ROLE_LECTURER` |
| Chọn giao diện | `HomeController.userHome` | Principal trong phiên → hồ sơ của sinh viên hoặc danh sách của giảng viên |
| Đọc/lưu hồ sơ | `StudentApiClient`, `HomeController` | Dùng email và quyền từ danh tính đã đăng nhập; form chỉ gửi trường được phép sửa |
| Chặn thao tác sai quyền | `SecurityConfig` | Sinh viên được POST `/students/me`; Giảng viên được GET `/students/**` |

Quy ước seminar: email `@student.tdtu.edu.vn` là `STUDENT`, `@tdtu.edu.vn` là `LECTURER`. Code còn yêu cầu email đã xác minh và claim `hd` thuộc `tdtu.edu.vn` hoặc `student.tdtu.edu.vn`. `hd` không nhất thiết bằng miền email. Quy ước này không phải nguồn xác minh chức danh nhân sự ngoài thực tế.

Vai trò được tạo từ danh tính Google đã xác thực và lưu trong phiên ứng dụng. Sửa chữ trên HTML hoặc sửa email hồ sơ không tự đổi được quyền. Form ghi dữ liệu và đăng xuất dùng CSRF của Spring Security.

## 10. Dừng, mở lại và sửa cấu hình

Dừng các container, giữ dữ liệu hồ sơ trong volume:

```powershell
docker compose --env-file .env -f compose.yaml down
```

Chạy lại chế độ bình thường:

```powershell
docker compose --env-file .env -f compose.yaml up -d
```

Sau khi sửa `.env`, chạy lại `up -d` để Compose áp dụng cấu hình; sau khi sửa mã nguồn, dùng `up --build -d`. Nếu cần chế độ giả lập, luôn thêm `-f compose.demo.yaml` như mục 8.

Không thêm `-v` vào lệnh `down` trong quá trình demo: tùy chọn đó xóa volume chứa hồ sơ.

## 11. Lỗi thường gặp

| Hiện tượng | Cách xử lý |
|---|---|
| Không có lệnh `docker` hoặc `docker compose` | Cài/mở Docker Desktop, mở lại terminal; kiểm tra Docker Compose đi kèm |
| Không kết nối được Docker daemon | Mở Docker Desktop, chờ engine; xử lý yêu cầu WSL/virtualization được Docker báo |
| Không tìm thấy `compose.yaml` hoặc `.env.example` | Mở thư mục gốc có trực tiếp các file đó, kiểm tra `Get-Location` |
| Báo thiếu biến môi trường | Tạo `.env` đúng tên, điền đủ ID/Secret/khóa; lưu file rồi kiểm tra lại |
| student-service báo khóa quá ngắn | Tạo `STUDENT_SERVICE_KEY` tối thiểu 32 ký tự; chạy lại Compose |
| Build lỗi khi tải image hoặc dependency | Đọc log cụ thể, kiểm tra Internet/proxy và tag ghi trong Dockerfile; không tự đổi ngẫu nhiên các phiên bản |
| Port 8080/8000/8001 đã được dùng | Dừng tiến trình đang dùng cổng rồi chạy lại; nếu đổi cổng auth, phải đồng bộ callback ở Google và `.env` |
| Google báo `redirect_uri_mismatch` | So sánh URI của Web client với `.env`: hostname, 8080, đường dẫn và dấu `/` cuối |
| Google báo `org_internal` | Chọn tài khoản thuộc tổ chức chứa project hoặc dùng project/Audience phù hợp được phép |
| Google Workspace chặn app | Kiểm tra chính sách ứng dụng với quản trị viên trường; ID/Secret đúng chưa đủ để vượt chính sách tổ chức |
| Quay về `/login?error` | Xem log auth-service; kiểm tra tài khoản TDTU, ID/Secret, phiên đăng nhập và bắt đầu lại từ trang login |
| Trang hồ sơ báo không kết nối dịch vụ | Kiểm tra student-service khỏe và khóa đúng ở hai service; khi chạy Compose giữ URL nội bộ do Compose cấu hình |
| Danh sách Giảng viên trống | Cho sinh viên đăng nhập để tạo hồ sơ trước |
| Không mở được `/demo` | Chạy với cả hai file Compose như mục 8; chế độ bình thường chủ động chặn demo |
| Lưu form báo 403 | Đăng nhập đúng Sinh viên, mở lại form để lấy phiên/CSRF hiện tại; Giảng viên không có quyền lưu |

## 12. Tài liệu chính thức tham khảo

- [Docker Desktop trên Windows](https://docs.docker.com/desktop/setup/install/windows-install/)
- [Kiểm tra cấu hình Docker Compose](https://docs.docker.com/reference/cli/docker/compose/config/)
- [Cấu hình màn hình đồng ý và scopes](https://developers.google.com/workspace/guides/configure-oauth-consent)
- [Audience: Internal, External và Testing](https://support.google.com/cloud/answer/15549945?hl=en)
- [Tạo và quản lý OAuth clients](https://support.google.com/cloud/answer/15549257?hl=en)

Các URL, biến cấu hình và quyền ứng dụng trong README được đối chiếu với bộ mã nguồn nêu ở đầu tài liệu. Đăng nhập Google thật cần được kiểm tra bằng tài khoản và project của người chạy.
