#Requires -Version 5.1
# No Pester, Python, Docker daemon, Google account or live credentials required.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0
$project = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
. (Join-Path $project 'scripts/DemoConfig.ps1')
. (Join-Path $project 'scripts/DemoRuntime.ps1')
. (Join-Path $project 'scripts/DemoSetup.ps1')
$script:passed = 0
$script:failed = 0
$sandbox = Join-Path ([IO.Path]::GetTempPath()) ('seminar-demo-tests-' + [guid]::NewGuid().ToString('N'))
$null = New-Item -ItemType Directory -Path $sandbox
$enginePath = @('pwsh.exe', 'powershell.exe', 'pwsh') | ForEach-Object { Join-Path $PSHOME $_ } | Where-Object { [IO.File]::Exists($_) } | Select-Object -First 1

function Assert-True {
    param($Condition, [string]$Message = 'Điều kiện kiểm thử chưa đúng')
    if (-not $Condition) { throw $Message }
}
function Assert-Equal {
    param($Expected, $Actual)
    if ([string]$Expected -cne [string]$Actual) { throw 'Kết quả không khớp kỳ vọng (không in giá trị để tránh lộ cấu hình).' }
}
function Assert-Throws {
    param([scriptblock]$Action, [string]$Pattern = '.')
    try { & $Action | Out-Null } catch {
        if ($_.Exception.Message -notmatch $Pattern) { throw 'Có lỗi nhưng không phải lỗi dự kiến.' }
        return
    }
    throw 'Đáng lẽ thao tác phải bị chặn.'
}
function Test-Case {
    param([string]$Name, [scriptblock]$Action)
    try { & $Action; $script:passed++; Write-Host ('PASS ' + $Name) }
    catch { $script:failed++; Write-Host ('FAIL ' + $Name + ': ' + $_.Exception.Message) }
}
function New-TestProject {
    $directory = Join-Path $sandbox ('dự án có dấu ' + [guid]::NewGuid().ToString('N'))
    $null = New-Item -ItemType Directory -Path $directory
    foreach ($name in @('.env.example', 'compose.yaml', 'Dockerfile', 'pom.xml')) { Copy-Item -LiteralPath (Join-Path $project $name) -Destination $directory }
    return $directory
}
function New-TestSettings {
    return @{
        GOOGLE_CLIENT_ID = '123-test.apps.googleusercontent.com'; GOOGLE_CLIENT_SECRET = 'TEST-secret-123456'
        GOOGLE_API_KEY = 'TEST_API_KEY_12345678901234567890'; GOOGLE_PROJECT_NUMBER = '123456789012'
        DRIVE_FOLDER_ID = 'test-folder-123456789'; DRIVE_ADMIN_EMAIL = 'owner@student.tdtu.edu.vn'
        SERVER_PORT = '8080'; APP_BASE_URL = 'http://localhost:8080'; GOOGLE_REDIRECT_URI = 'http://localhost:8080/login/oauth2/code/google'
        DRIVE_ACCESS_MODE = 'ISOLATED'; DRIVE_ADMIN_TOKEN_KEY = ''; COOKIE_SECURE = 'false'
    }
}
function New-TestEnv {
    param([string]$Directory, [switch]$WithKey)
    $doc = Initialize-DemoEnv $Directory
    $settings = New-TestSettings
    if ($WithKey) { $settings.DRIVE_ADMIN_TOKEN_KEY = New-DemoDriveKey }
    return Save-DemoEnv $doc $settings
}

try {
    Test-Case 'Tạo .env từ mẫu; chưa chạy app khi cấu hình còn thiếu' {
        $d = New-TestProject; $doc = Initialize-DemoEnv $d
        Assert-True ([IO.File]::Exists((Join-Path $d '.env')))
        Assert-Equal '' $doc.Values.DRIVE_ADMIN_EMAIL
        Assert-True (@(Get-DemoConfigErrors (Get-DemoSettings $doc)).Count -ge 5)
    }
    Test-Case 'Không ghi đè .env đã tồn tại' {
        $d = New-TestProject; $doc = New-TestEnv $d -WithKey
        $hash = Get-DemoFileHash $doc.Path
        $null = Initialize-DemoEnv $d
        Assert-Equal $hash (Get-DemoFileHash $doc.Path)
    }
    Test-Case 'Cấu hình mẫu configure-* chưa được coi là hoàn tất' {
        $s = New-TestSettings; $s.GOOGLE_CLIENT_SECRET = 'configure-client-secret'
        Assert-True (@(Get-DemoConfigErrors $s) -match 'Chưa điền GOOGLE_CLIENT_SECRET').Count
    }
    Test-Case 'Bảo toàn comment, biến tùy chỉnh và mật khẩu database' {
        $d = New-TestProject; $doc = Initialize-DemoEnv $d
        [IO.File]::AppendAllText($doc.Path, '# ghi chú giữ nguyên' + "`r`n" + 'CUSTOM_VALUE=abc=def' + "`r`n" + 'CUSTOM_SECRET=$keep # same' + "`r`n")
        $doc = Read-DemoEnv $doc.Path
        $doc = Save-DemoEnv $doc @{ SERVER_PORT = '8000'; DRIVE_ADMIN_TOKEN_KEY = New-DemoDriveKey }
        $text = [IO.File]::ReadAllText($doc.Path)
        Assert-True ($text.Contains('CUSTOM_SECRET=$keep # same'))
        Assert-True ($text.Contains('# ghi chú giữ nguyên'))
        Assert-Equal 'abc=def' $doc.Values.CUSTOM_VALUE
        Assert-True ($text.Contains('DATABASE_URL=jdbc:h2:file:./data/seminar;DB_CLOSE_ON_EXIT=FALSE'))
    }
    Test-Case 'Giữ nguyên MANUAL nếu .env cũ chưa có DRIVE_ACCESS_MODE' {
        $d = New-TestProject; $doc = New-TestEnv $d
        [IO.File]::WriteAllText($doc.Path, ([IO.File]::ReadAllText($doc.Path) -replace '(?m)^DRIVE_ACCESS_MODE=.*\r?\n', ''))
        Assert-Equal 'MANUAL' (Get-DemoSettings (Read-DemoEnv $doc.Path)).DRIVE_ACCESS_MODE
    }
    Test-Case 'Giữ chế độ SHARED khi đã cấu hình rõ ràng' {
        $d = New-TestProject; $doc = New-TestEnv $d
        $doc = Save-DemoEnv $doc @{ DRIVE_ACCESS_MODE = 'SHARED' }
        Assert-Equal 'SHARED' (Get-DemoSettings $doc).DRIVE_ACCESS_MODE
    }
    Test-Case 'Khóa được sinh có 32 byte và khác nhau giữa các lần sinh' {
        $keys = @{}
        for ($i = 0; $i -lt 64; $i++) {
            $key = New-DemoDriveKey
            Assert-True (Test-DemoDriveKey $key)
            Assert-True (-not $keys.ContainsKey($key))
            $keys[$key] = $true
        }
    }
    Test-Case 'Đọc/ghi nguyên vẹn dấu bằng cuối khóa Base64' {
        $d = New-TestProject; $doc = New-TestEnv $d -WithKey
        $key = $doc.Values.DRIVE_ADMIN_TOKEN_KEY
        Assert-True ($key.EndsWith('='))
        $doc = Save-DemoEnv $doc @{ SERVER_PORT = '8001' }
        Assert-Equal $key $doc.Values.DRIVE_ADMIN_TOKEN_KEY
    }
    Test-Case 'Không tự thay khóa hiện có nhưng sai định dạng' {
        $s = New-TestSettings; $s.DRIVE_ADMIN_TOKEN_KEY = 'invalid-existing-key'
        Assert-True (@(Get-DemoConfigErrors $s) -match 'không sinh đè').Count
        Assert-Equal 'invalid-existing-key' $s.DRIVE_ADMIN_TOKEN_KEY
    }
    Test-Case 'Chặn biến lặp và export thay vì đoán giá trị' {
        $d = New-TestProject; $path = Join-Path $d '.env'
        [IO.File]::WriteAllText($path, "SERVER_PORT=8080`nSERVER_PORT=8000")
        Assert-Throws { Read-DemoEnv $path } 'bị lặp'
        [IO.File]::WriteAllText($path, 'export SERVER_PORT=8080')
        Assert-Throws { Read-DemoEnv $path } 'export'
    }
    Test-Case 'Không thực thi biểu thức khi đọc .env' {
        $d = New-TestProject; $path = Join-Path $d '.env'
        [IO.File]::WriteAllText($path, 'CUSTOM=$(throw "do-not-execute")')
        $doc = Read-DemoEnv $path
        Assert-Equal '$(throw "do-not-execute")' $doc.Values.CUSTOM
    }
    Test-Case 'Từ chối ký tự xuống dòng/interpolation khi ghi trường giao diện' {
        $d = New-TestProject; $doc = Initialize-DemoEnv $d
        Assert-Throws { Save-DemoEnv $doc @{ GOOGLE_CLIENT_SECRET = "bad`nSERVER_PORT=1" } }
        Assert-Throws { Save-DemoEnv $doc @{ GOOGLE_CLIENT_SECRET = 'bad${secret}' } }
        Assert-Throws { Save-DemoEnv $doc @{ GOOGLE_CLIENT_SECRET = 'bad#comment' } }
    }
    Test-Case 'Phát hiện .env bị sửa song song; không để lại file tạm' {
        $d = New-TestProject; $doc = Initialize-DemoEnv $d
        [IO.File]::AppendAllText($doc.Path, '# external edit')
        Assert-Throws { Save-DemoEnv $doc @{ SERVER_PORT = '8000' } } 'đã được thay đổi'
        Assert-True ([IO.File]::ReadAllText($doc.Path).Contains('# external edit'))
        Assert-Equal 0 @(Get-ChildItem -LiteralPath $d -Filter '.demo-env-*.tmp').Count
    }
    Test-Case 'Kiểm tra Project number, ID thư mục, cổng và cookie local' {
        $s = New-TestSettings; Assert-Equal 0 @(Get-DemoConfigErrors $s).Count
        $s.GOOGLE_PROJECT_NUMBER = 'my-project'; Assert-True (@(Get-DemoConfigErrors $s) -match 'phải là số').Count
        $s = New-TestSettings; $s.DRIVE_FOLDER_ID = 'https://drive.google.com/drive/folders/test'; Assert-True (@(Get-DemoConfigErrors $s) -match 'chỉ nhận ID').Count
        $s = New-TestSettings; $s.SERVER_PORT = '8000'; Assert-True (@(Get-DemoConfigErrors $s) -match 'khớp SERVER_PORT').Count
        $s = New-TestSettings; $s.COOKIE_SECURE = 'true'; Assert-True (@(Get-DemoConfigErrors $s) -match 'COOKIE_SECURE').Count
    }
    Test-Case 'Giới hạn miền email chủ Drive trong chế độ tự động' {
        $s = New-TestSettings; $s.DRIVE_ADMIN_EMAIL = 'owner@example.com'
        Assert-True (@(Get-DemoConfigErrors $s) -match 'email TDTU').Count
        $s.DRIVE_ACCESS_MODE = 'MANUAL'; Assert-Equal 0 @(Get-DemoConfigErrors $s).Count
    }
    Test-Case 'Import JSON OAuth Web đúng callback' {
        $d = New-TestProject; $path = Join-Path $d 'oauth.json'; $s = New-TestSettings
        @{ web = @{ client_id = $s.GOOGLE_CLIENT_ID; client_secret = $s.GOOGLE_CLIENT_SECRET; redirect_uris = @($s.GOOGLE_REDIRECT_URI) } } | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $path -Encoding UTF8
        $imported = Get-DemoOAuthImport $path $s.GOOGLE_REDIRECT_URI
        Assert-Equal $s.GOOGLE_CLIENT_ID $imported.GOOGLE_CLIENT_ID
        Assert-Equal $s.GOOGLE_CLIENT_SECRET $imported.GOOGLE_CLIENT_SECRET
        Assert-Throws { Get-DemoOAuthImport $path 'http://localhost:8000/login/oauth2/code/google' } 'callback'
    }
    Test-Case 'Từ chối JSON Desktop, service account, thiếu trường, hỏng cú pháp' {
        $d = New-TestProject; $path = Join-Path $d 'oauth.json'
        foreach ($json in @('{"installed":{}}', '{"type":"service_account"}', '{"web":{}}', '{invalid')) {
            [IO.File]::WriteAllText($path, $json)
            Assert-Throws { Get-DemoOAuthImport $path 'http://localhost:8080/login/oauth2/code/google' }
        }
    }
    Test-Case 'Đọc UTF-8 BOM và ghi .env UTF-8 không BOM' {
        $d = New-TestProject; $path = Join-Path $d '.env'
        [IO.File]::WriteAllText($path, "# tiếng Việt`r`nSERVER_PORT=8080", (New-Object Text.UTF8Encoding($true)))
        $doc = Save-DemoEnv (Read-DemoEnv $path) @{ SERVER_PORT = '8000' }
        $bytes = [IO.File]::ReadAllBytes($path)
        Assert-True (-not ($bytes[0] -eq 239 -and $bytes[1] -eq 187 -and $bytes[2] -eq 191))
        Assert-True ([IO.File]::ReadAllText($path).Contains('tiếng Việt'))
    }
    Test-Case 'Process runner truyền nguyên tham số có khoảng trắng, Unicode, dấu nháy' {
        $path = Join-Path $sandbox 'process fixture.ps1'
        [IO.File]::WriteAllText($path, '[Console]::OutputEncoding = New-Object Text.UTF8Encoding($false); ConvertTo-Json -InputObject @($args) -Compress', (New-Object Text.UTF8Encoding($true)))
        $exe = $enginePath
        $expected = @('dự án có dấu', 'trailing\', 'a"b', 'x \" y')
        $result = Invoke-DemoProcess $exe (@('-NoLogo', '-NoProfile', '-File', $path) + $expected) $sandbox
        Assert-Equal 0 $result.ExitCode
        $actual = @($result.Out | ConvertFrom-Json)
        Assert-Equal $expected.Count $actual.Count
        for ($i = 0; $i -lt $expected.Count; $i++) { Assert-Equal $expected[$i] $actual[$i] }
    }
    Test-Case 'Process runner trả exit code và thu stdout/stderr không treo' {
        $path = Join-Path $sandbox 'exit.ps1'
        [IO.File]::WriteAllText($path, '[Console]::Out.WriteLine("output"); [Console]::Error.WriteLine("error"); exit 7')
        $exe = $enginePath
        $result = Invoke-DemoProcess $exe @('-NoLogo', '-NoProfile', '-File', $path) $sandbox
        Assert-Equal 7 $result.ExitCode
        Assert-True ($result.Out.Contains('output') -and $result.Error.Contains('error'))
    }
    Test-Case 'Process runner giới hạn thời gian chờ' {
        $path = Join-Path $sandbox 'timeout.ps1'
        [IO.File]::WriteAllText($path, 'Start-Sleep -Seconds 20')
        $exe = $enginePath
        Assert-Throws { Invoke-DemoProcess $exe @('-NoLogo', '-NoProfile', '-File', $path) $sandbox -TimeoutSeconds 1 } 'quá thời gian'
    }

    # Controlled integration fixtures. Native process behavior was tested above;
    # these fixtures verify launcher decisions without starting real containers.
    $script:calls = New-Object 'System.Collections.Generic.List[object]'
    $script:behavior = @{}
    function Reset-Fixture {
        $script:calls.Clear()
        $script:behavior = @{ Setup = 'complete'; ExistingData = $false; Recovery = $false; Docker = 'linux'; Up = 0; Health = $true; Container = 'running'; Config = 0 }
    }
    function Invoke-DemoProcess {
        param([string]$Command, [string[]]$Arguments, [string]$Directory, [int]$TimeoutSeconds = 30, [switch]$Progress)
        $script:calls.Add([pscustomobject]@{ Kind = 'docker'; Arguments = $Arguments; Directory = $Directory })
        $code = 0; $out = ''
        if ($Arguments[0] -eq 'info') { $out = $script:behavior.Docker; if ($out -eq 'offline') { $code = 1 } }
        elseif ($Arguments[0] -eq 'volume') { if ($script:behavior.ExistingData) { $out = 'fixture_seminar_data' } }
        elseif ($Arguments[0] -eq 'inspect') { $out = $script:behavior.Container }
        elseif ($Arguments -contains 'config') {
            $s = Get-DemoSettings (Read-DemoEnv (Join-Path $Directory '.env'))
            $port = $s.SERVER_PORT
            if ($script:behavior.ContainsKey('PlanPort')) { $port = $script:behavior.PlanPort }
            $plan = @{ services = @{ 'seminar-app' = @{ environment = @{ SERVER_PORT = $port }; ports = @(@{ published = $port; target = [int]$port }) } }; volumes = @{ seminar_data = @{ name = 'fixture_seminar_data' } } }
            $out = $plan | ConvertTo-Json -Depth 8 -Compress
            $code = $script:behavior.Config
        } elseif ($Arguments -contains 'up') { $code = $script:behavior.Up }
        elseif ($Arguments -contains 'ps') { $out = 'test-container-id' }
        return [pscustomobject]@{ ExitCode = $code; Out = $out; Error = 'fixture failure' }
    }
    function Show-DemoSetup {
        param($Settings, $Errors)
        $script:calls.Add([pscustomobject]@{ Kind = 'setup' })
        if ($script:behavior.Setup -eq 'cancel') { return $null }
        $candidate = New-TestSettings
        if ($Settings.DRIVE_ADMIN_TOKEN_KEY) { $candidate.DRIVE_ADMIN_TOKEN_KEY = $Settings.DRIVE_ADMIN_TOKEN_KEY }
        return $candidate
    }
    function Start-DemoDesktop { return $false }
    function Confirm-DemoKeyRecovery {
        $script:calls.Add([pscustomobject]@{ Kind = 'recovery' })
        return $script:behavior.Recovery
    }
    function Test-DemoHealth {
        param([string]$Url)
        $script:calls.Add([pscustomobject]@{ Kind = 'health'; Url = $Url })
        return $script:behavior.Health
    }
    function Open-DemoBrowser {
        param([string]$Url)
        $script:calls.Add([pscustomobject]@{ Kind = 'browser'; Url = $Url })
    }

    Test-Case 'Lần đầu: cấu hình -> sinh khóa -> up -> health -> mở browser' {
        Reset-Fixture; $d = New-TestProject
        Start-SeminarDemo $d -DockerCommand 'fixture'
        Assert-True (Test-DemoDriveKey (Read-DemoEnv (Join-Path $d '.env')).Values.DRIVE_ADMIN_TOKEN_KEY)
        Assert-Equal 1 @($script:calls | Where-Object { $_.Kind -eq 'setup' }).Count
        Assert-Equal 'browser' $script:calls[$script:calls.Count - 1].Kind
        Assert-Equal 'health' $script:calls[$script:calls.Count - 2].Kind
        Assert-Equal 'http://localhost:8080' $script:calls[$script:calls.Count - 1].Url
        Assert-True (@($script:calls | Where-Object { $_.Kind -eq 'docker' -and $_.Arguments -contains 'up' }).Count -eq 1)
    }
    Test-Case 'Chạy lại cấu hình đủ: không hiện form, không đổi byte .env/khóa' {
        Reset-Fixture; $d = New-TestProject; $doc = New-TestEnv $d -WithKey; $hash = Get-DemoFileHash $doc.Path
        Start-SeminarDemo $d -DockerCommand 'fixture'
        Start-SeminarDemo $d -DockerCommand 'fixture'
        Assert-Equal 0 @($script:calls | Where-Object { $_.Kind -eq 'setup' }).Count
        Assert-Equal $hash (Get-DemoFileHash $doc.Path)
    }
    Test-Case 'Chế độ -Configure mở form cả khi cấu hình đã đủ' {
        Reset-Fixture; $d = New-TestProject; $null = New-TestEnv $d -WithKey
        Start-SeminarDemo $d -Configure -DockerCommand 'fixture'
        Assert-Equal 1 @($script:calls | Where-Object { $_.Kind -eq 'setup' }).Count
    }
    Test-Case 'Hủy form: chưa sinh khóa, chưa gọi Docker, chưa mở browser' {
        Reset-Fixture; $script:behavior.Setup = 'cancel'; $d = New-TestProject
        Start-SeminarDemo $d -DockerCommand 'fixture'
        Assert-Equal 1 $script:calls.Count
        Assert-Equal '' (Read-DemoEnv (Join-Path $d '.env')).Values.DRIVE_ADMIN_TOKEN_KEY
    }
    Test-Case 'Thiếu một trường: hiện form và giữ khóa cũ' {
        Reset-Fixture; $d = New-TestProject; $doc = New-TestEnv $d -WithKey; $key = $doc.Values.DRIVE_ADMIN_TOKEN_KEY
        $null = Save-DemoEnv $doc @{ GOOGLE_API_KEY = '' }
        Start-SeminarDemo $d -DockerCommand 'fixture'
        Assert-Equal 1 @($script:calls | Where-Object { $_.Kind -eq 'setup' }).Count
        Assert-Equal $key (Read-DemoEnv $doc.Path).Values.DRIVE_ADMIN_TOKEN_KEY
    }
    Test-Case 'Có volume cũ, mất khóa: không tự sinh nếu chưa xác nhận kết nối lại' {
        Reset-Fixture; $script:behavior.ExistingData = $true; $d = New-TestProject; $doc = New-TestEnv $d
        Start-SeminarDemo $d -DockerCommand 'fixture'
        Assert-Equal 1 @($script:calls | Where-Object { $_.Kind -eq 'recovery' }).Count
        Assert-Equal '' (Read-DemoEnv $doc.Path).Values.DRIVE_ADMIN_TOKEN_KEY
        Assert-Equal 0 @($script:calls | Where-Object { $_.Kind -eq 'browser' }).Count
    }
    Test-Case 'Có volume cũ, chủ động chấp nhận kết nối lại: sinh khóa mới' {
        Reset-Fixture; $script:behavior.ExistingData = $true; $script:behavior.Recovery = $true; $d = New-TestProject; $doc = New-TestEnv $d
        Start-SeminarDemo $d -DockerCommand 'fixture'
        Assert-True (Test-DemoDriveKey (Read-DemoEnv $doc.Path).Values.DRIVE_ADMIN_TOKEN_KEY)
    }
    Test-Case 'Phát hiện database chạy Java trong thư mục data' {
        Reset-Fixture; $d = New-TestProject; $doc = New-TestEnv $d
        $null = New-Item -ItemType Directory -Path (Join-Path $d 'data')
        [IO.File]::WriteAllText((Join-Path $d 'data/seminar.mv.db'), 'fixture')
        Start-SeminarDemo $d -DockerCommand 'fixture'
        Assert-Equal 1 @($script:calls | Where-Object { $_.Kind -eq 'recovery' }).Count
    }
    Test-Case 'Docker tắt hoặc sai Windows containers: dừng trước khi build' {
        foreach ($engine in @('offline', 'windows')) {
            Reset-Fixture; $script:behavior.Docker = $engine; $d = New-TestProject; $null = New-TestEnv $d -WithKey
            Assert-Throws { Start-SeminarDemo $d -DockerCommand 'fixture' } 'Docker|Linux'
            Assert-Equal 0 @($script:calls | Where-Object { $_.Kind -eq 'browser' }).Count
        }
    }
    Test-Case 'Compose lỗi hoặc cổng không khớp: không build/mở browser' {
        foreach ($failure in @('Config', 'PlanPort')) {
            Reset-Fixture; if ($failure -eq 'Config') { $script:behavior.Config = 1 } else { $script:behavior.PlanPort = '8000' }
            $d = New-TestProject; $null = New-TestEnv $d -WithKey
            Assert-Throws { Start-SeminarDemo $d -DockerCommand 'fixture' }
            Assert-Equal 0 @($script:calls | Where-Object { $_.Kind -eq 'browser' }).Count
        }
    }
    Test-Case 'Docker build lỗi: không mở browser' {
        Reset-Fixture; $script:behavior.Up = 1; $d = New-TestProject; $null = New-TestEnv $d -WithKey
        Assert-Throws { Start-SeminarDemo $d -DockerCommand 'fixture' } 'thất bại'
        Assert-Equal 0 @($script:calls | Where-Object { $_.Kind -eq 'browser' }).Count
    }
    Test-Case 'Backend dừng hoặc /health chưa sẵn sàng: không mở browser' {
        Reset-Fixture; $d = New-TestProject; $null = New-TestEnv $d -WithKey
        $script:behavior.Container = 'exited'
        Assert-Throws { Wait-DemoApplication 'fixture' $d 'http://localhost:8080' -TimeoutSeconds 0 } 'đã dừng'
        $script:behavior.Container = 'running'; $script:behavior.Health = $false
        Assert-Throws { Wait-DemoApplication 'fixture' $d 'http://localhost:8080' -TimeoutSeconds 0 } 'chưa sẵn sàng'
    }
    Test-Case 'Cổng tùy chỉnh được dùng nhất quán khi health và mở browser' {
        Reset-Fixture; $d = New-TestProject; $doc = New-TestEnv $d -WithKey
        $null = Save-DemoEnv $doc @{ SERVER_PORT = '8000'; APP_BASE_URL = 'http://localhost:8000'; GOOGLE_REDIRECT_URI = 'http://localhost:8000/login/oauth2/code/google' }
        Start-SeminarDemo $d -DockerCommand 'fixture'
        Assert-Equal 'http://localhost:8000' $script:calls[$script:calls.Count - 1].Url
    }
    Test-Case '-NoBrowser vẫn chạy và kiểm tra health' {
        Reset-Fixture; $d = New-TestProject; $null = New-TestEnv $d -WithKey
        Start-SeminarDemo $d -NoBrowser -DockerCommand 'fixture'
        Assert-True (@($script:calls | Where-Object { $_.Kind -eq 'health' }).Count -gt 0)
        Assert-Equal 0 @($script:calls | Where-Object { $_.Kind -eq 'browser' }).Count
    }
    Test-Case 'Chặn hai bộ khởi chạy cùng thư mục; không dùng lệnh xóa dữ liệu' {
        Reset-Fixture; $d = New-TestProject; $null = New-TestEnv $d -WithKey
        $lock = [IO.File]::Open((Join-Path $d '.demo-launch.lock'), [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
        try { Assert-Throws { Start-SeminarDemo $d -DockerCommand 'fixture' } 'cửa sổ khác' } finally { $lock.Dispose() }
        Start-SeminarDemo $d -DockerCommand 'fixture'
        foreach ($call in @($script:calls | Where-Object { $_.Kind -eq 'docker' })) {
            Assert-True ($call.Arguments -notcontains 'down' -and $call.Arguments -notcontains 'rm' -and $call.Arguments -notcontains 'prune')
            Assert-True ($call.Directory -eq $d)
        }
    }
} finally {
    Remove-Item -LiteralPath $sandbox -Recurse -Force
}
Write-Host ("`nKết quả: " + $script:passed + ' PASS; ' + $script:failed + ' FAIL.')
if ($script:failed -gt 0) { exit 1 }
exit 0
