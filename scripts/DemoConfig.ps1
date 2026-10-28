#Requires -Version 5.1
Set-StrictMode -Version 2.0

function Get-DemoFileHash {
    param([string]$Path)
    if (-not [IO.File]::Exists($Path)) { return '' }
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return [Convert]::ToBase64String($sha.ComputeHash([IO.File]::ReadAllBytes($Path))) }
    finally { $sha.Dispose() }
}

function Read-DemoEnv {
    param([string]$Path)
    $values = @{}
    $text = ''
    $hash = Get-DemoFileHash $Path
    if ($hash) { $text = [IO.File]::ReadAllText($Path) }
    $lines = @($text -split '\r?\n')
    foreach ($line in $lines) {
        if ($line -match '^\s*(?:#|!|$)') { continue }
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
            $name = $Matches[1]
            $value = $Matches[2].Trim()
            if ($values.ContainsKey($name)) { throw "Biến $name bị lặp trong .env. Hãy giữ một dòng cho biến này rồi chạy lại." }
            if ($value.StartsWith('"') -or $value.StartsWith("'")) {
                $quote = $value.Substring(0, 1)
                if ($value.Length -lt 2 -or -not $value.EndsWith($quote)) {
                    throw "Biến $name có dấu nháy không khép kín. Bộ thiết lập chỉ nhận mỗi giá trị trên một dòng."
                }
                $value = $value.Substring(1, $value.Length - 2)
            } else {
                $value = ($value -replace '\s+#.*$', '').Trim()
            }
            $values[$name] = $value
        } elseif ($line -match '^\s*export\s+') {
            throw '.env không dùng cú pháp export. Hãy dùng TEN_BIEN=gia_tri rồi chạy lại.'
        } else {
            throw '.env có dòng không đúng cú pháp TEN_BIEN=gia_tri. Hãy sửa dòng đó rồi chạy lại.'
        }
    }
    return [pscustomobject]@{ Path = $Path; Hash = $hash; Lines = $lines; Values = $values }
}

function Save-DemoEnv {
    param($Document, [System.Collections.IDictionary]$Updates)
    if ((Get-DemoFileHash $Document.Path) -ne $Document.Hash) {
        throw '.env đã được thay đổi bởi chương trình khác. Đóng cửa sổ này và chạy lại để đọc cấu hình mới.'
    }
    $pending = @{}
    foreach ($name in $Updates.Keys) {
        $value = [string]$Updates[$name]
        if ($name -notmatch '^[A-Z][A-Z0-9_]*$' -or $value -match '[\r\n\x00]') {
            throw 'Tên biến hoặc giá trị cấu hình không hợp lệ.'
        }
        # Fields written by the wizard use the common literal syntax understood
        # by both Compose and Spring's .properties reader. Existing other lines
        # (including custom database settings) are preserved verbatim.
        if ($value -match '[\s\x22\x27$#\\]') {
            throw "Biến $name chứa ký tự không được hỗ trợ khi tự ghi .env. Hãy dùng giá trị trên một dòng, không có dấu nháy hoặc khoảng trắng."
        }
        $pending[$name] = $value
    }
    $lines = New-Object 'System.Collections.Generic.List[string]'
    foreach ($line in $Document.Lines) {
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=' -and $pending.ContainsKey($Matches[1])) {
            $name = $Matches[1]
            $lines.Add($name + '=' + $pending[$name])
            $pending.Remove($name)
        } else { $lines.Add($line) }
    }
    foreach ($name in @($pending.Keys | Sort-Object)) { $lines.Add($name + '=' + $pending[$name]) }
    $body = ([string]::Join("`r`n", $lines)).TrimEnd("`r", "`n") + "`r`n"
    $temp = Join-Path ([IO.Path]::GetDirectoryName($Document.Path)) ('.demo-env-' + [guid]::NewGuid().ToString('N') + '.tmp')
    try {
        [IO.File]::WriteAllText($temp, $body, (New-Object Text.UTF8Encoding($false)))
        if ((Get-DemoFileHash $Document.Path) -ne $Document.Hash) {
            throw '.env đã thay đổi trong khi lưu. Chạy lại để dùng cấu hình mới.'
        }
        if ($Document.Hash) { [IO.File]::Replace($temp, $Document.Path, [System.Management.Automation.Language.NullString]::Value) }
        else { [IO.File]::Move($temp, $Document.Path) }
    } finally {
        if ([IO.File]::Exists($temp)) { [IO.File]::Delete($temp) }
    }
    return Read-DemoEnv $Document.Path
}

function Initialize-DemoEnv {
    param([string]$ProjectDirectory)
    $path = Join-Path $ProjectDirectory '.env'
    if ([IO.File]::Exists($path)) { return Read-DemoEnv $path }
    $template = Join-Path $ProjectDirectory '.env.example'
    if (-not [IO.File]::Exists($template)) { throw 'Thiếu .env.example. Hãy chép đầy đủ các file cập nhật vào thư mục dự án.' }
    $example = Read-DemoEnv $template
    $empty = Read-DemoEnv $path
    $empty.Lines = $example.Lines
    # A new installation must choose its actual folder owner.
    return Save-DemoEnv $empty @{ DRIVE_ADMIN_EMAIL = '' }
}

function Get-DemoSettings {
    param($Document)
    $values = @{}
    foreach ($name in $Document.Values.Keys) { $values[$name] = $Document.Values[$name] }
    if (-not $values.ContainsKey('SERVER_PORT') -or -not $values.SERVER_PORT) { $values.SERVER_PORT = '8080' }
    if (-not $values.ContainsKey('APP_BASE_URL') -or -not $values.APP_BASE_URL) { $values.APP_BASE_URL = 'http://localhost:' + $values.SERVER_PORT }
    if (-not $values.ContainsKey('GOOGLE_REDIRECT_URI') -or -not $values.GOOGLE_REDIRECT_URI) {
        $values.GOOGLE_REDIRECT_URI = $values.APP_BASE_URL.TrimEnd('/') + '/login/oauth2/code/google'
    }
    # Match the existing backend's MANUAL fallback; never broaden sharing on upgrade.
    if (-not $values.ContainsKey('DRIVE_ACCESS_MODE') -or -not $values.DRIVE_ACCESS_MODE) { $values.DRIVE_ACCESS_MODE = 'MANUAL' }
    if (-not $values.ContainsKey('COOKIE_SECURE') -or -not $values.COOKIE_SECURE) { $values.COOKIE_SECURE = 'false' }
    foreach ($name in @('GOOGLE_CLIENT_ID', 'GOOGLE_CLIENT_SECRET', 'GOOGLE_API_KEY', 'GOOGLE_PROJECT_NUMBER', 'DRIVE_FOLDER_ID', 'DRIVE_ADMIN_EMAIL', 'DRIVE_ADMIN_TOKEN_KEY')) {
        if (-not $values.ContainsKey($name)) { $values[$name] = '' }
    }
    return $values
}

function Test-DemoPlaceholder {
    param([string]$Value)
    return [string]::IsNullOrWhiteSpace($Value) -or $Value -match '^(configure-|your[_-]|<)'
}

function Test-DemoDriveKey {
    param([string]$Value)
    if (-not $Value) { return $false }
    try { return [Convert]::FromBase64String($Value).Length -eq 32 }
    catch { return $false }
}

function New-DemoDriveKey {
    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes); return [Convert]::ToBase64String($bytes) }
    finally { $rng.Dispose(); [Array]::Clear($bytes, 0, $bytes.Length) }
}

function Get-DemoConfigErrors {
    param([System.Collections.IDictionary]$Settings)
    foreach ($name in @('GOOGLE_CLIENT_ID', 'GOOGLE_CLIENT_SECRET', 'GOOGLE_API_KEY', 'GOOGLE_PROJECT_NUMBER', 'DRIVE_FOLDER_ID')) {
        if (Test-DemoPlaceholder ([string]$Settings[$name])) { "Chưa điền $name." }
    }
    if ($Settings['GOOGLE_CLIENT_ID'] -and $Settings['GOOGLE_CLIENT_ID'] -notmatch '^[A-Za-z0-9_-]+\.apps\.googleusercontent\.com$') { 'GOOGLE_CLIENT_ID phải thuộc OAuth client Web application.' }
    if ($Settings['GOOGLE_CLIENT_SECRET'] -and $Settings['GOOGLE_CLIENT_SECRET'] -notmatch '^[A-Za-z0-9._~+/=-]{8,}$') { 'GOOGLE_CLIENT_SECRET không đúng định dạng trên một dòng.' }
    if ($Settings['GOOGLE_API_KEY'] -and $Settings['GOOGLE_API_KEY'] -notmatch '^[A-Za-z0-9_-]{20,}$') { 'GOOGLE_API_KEY không đúng định dạng.' }
    if ($Settings['GOOGLE_PROJECT_NUMBER'] -and $Settings['GOOGLE_PROJECT_NUMBER'] -notmatch '^\d{1,30}$') { 'GOOGLE_PROJECT_NUMBER phải là số project, không phải Project ID dạng chữ.' }
    if ($Settings['DRIVE_FOLDER_ID'] -and $Settings['DRIVE_FOLDER_ID'] -notmatch '^[A-Za-z0-9_-]{8,200}$') { 'DRIVE_FOLDER_ID chỉ nhận ID, không nhận cả đường dẫn Google Drive.' }
    $port = 0
    if (-not [int]::TryParse([string]$Settings['SERVER_PORT'], [ref]$port) -or $port -lt 1 -or $port -gt 65535) { 'SERVER_PORT phải từ 1 đến 65535.' }
    $base = $null
    $baseOk = [Uri]::TryCreate([string]$Settings['APP_BASE_URL'], [UriKind]::Absolute, [ref]$base)
    if (-not $baseOk -or $base.Scheme -notin @('http', 'https') -or $base.UserInfo -or $base.Query -or $base.Fragment -or $base.AbsolutePath -ne '/' -or $Settings['APP_BASE_URL'] -match '[\s$#\x22\x27\\]') {
        'APP_BASE_URL phải là địa chỉ gốc http(s), không có đường dẫn, query hoặc thông tin đăng nhập.'
    } else {
        if ($base.IsLoopback -and $base.Port -ne $port) { 'Cổng của APP_BASE_URL phải khớp SERVER_PORT khi chạy localhost.' }
        if ($Settings['GOOGLE_REDIRECT_URI'] -cne ([string]$Settings['APP_BASE_URL']).TrimEnd('/') + '/login/oauth2/code/google') {
            'GOOGLE_REDIRECT_URI phải bằng APP_BASE_URL + /login/oauth2/code/google và khớp Google Cloud.'
        }
        if ($base.Scheme -eq 'http' -and $Settings['COOKIE_SECURE'] -eq 'true') { 'Chạy HTTP local cần bỏ chọn COOKIE_SECURE để trình duyệt gửi cookie đăng nhập.' }
    }
    if ($Settings['COOKIE_SECURE'] -notin @('true', 'false')) { 'COOKIE_SECURE chỉ nhận true hoặc false.' }
    if ($Settings['DRIVE_ACCESS_MODE'] -notin @('MANUAL', 'ISOLATED', 'SHARED')) { 'DRIVE_ACCESS_MODE phải là MANUAL, ISOLATED hoặc SHARED.' }
    if ($Settings['DRIVE_ACCESS_MODE'] -ne 'MANUAL' -and $Settings['DRIVE_ADMIN_EMAIL'] -notmatch '^[A-Za-z0-9._%+-]+@(student\.tdtu\.edu\.vn|tdtu\.edu\.vn)$') {
        'DRIVE_ADMIN_EMAIL cần là email TDTU thực sự sở hữu thư mục (bắt buộc với ISOLATED/SHARED).'
    }
    if ($Settings['DRIVE_ADMIN_TOKEN_KEY'] -and -not (Test-DemoDriveKey $Settings['DRIVE_ADMIN_TOKEN_KEY'])) {
        'DRIVE_ADMIN_TOKEN_KEY đã có nhưng không phải Base64 32 byte. Hãy khôi phục đúng khóa cũ; chương trình không sinh đè.'
    }
}

function Get-DemoOAuthImport {
    param([string]$Path, [string]$RedirectUri)
    try { $data = [IO.File]::ReadAllText($Path) | ConvertFrom-Json -ErrorAction Stop }
    catch { throw 'Không đọc được JSON OAuth. Hãy chọn file cấu hình tải từ Google Cloud.' }
    if (-not $data.PSObject.Properties['web']) { throw 'Cần OAuth client loại Web application (JSON có mục web), không phải Desktop hoặc service account.' }
    $web = $data.web
    if (-not $web.PSObject.Properties['client_id'] -or -not $web.PSObject.Properties['client_secret'] -or -not $web.PSObject.Properties['redirect_uris']) {
        throw 'JSON Web application thiếu Client ID, Client Secret hoặc redirect_uris.'
    }
    if ($RedirectUri -notin @($web.redirect_uris)) { throw 'JSON OAuth chưa đăng ký callback đang hiển thị trong tab Địa chỉ local. Hãy cập nhật Google Cloud và tải lại JSON.' }
    return @{ GOOGLE_CLIENT_ID = [string]$web.client_id; GOOGLE_CLIENT_SECRET = [string]$web.client_secret }
}
