#Requires -Version 5.1
Set-StrictMode -Version 2.0

function ConvertTo-DemoProcessArgument {
    param([AllowEmptyString()][string]$Value)
    $escaped = [regex]::Replace($Value, '(\\*)"', '$1$1\"')
    $escaped = [regex]::Replace($escaped, '(\\+)$', '$1$1')
    return '"' + $escaped + '"'
}

function Invoke-DemoProcess {
    param([string]$Command, [string[]]$Arguments, [string]$Directory, [int]$TimeoutSeconds = 30, [switch]$Progress)
    $info = New-Object Diagnostics.ProcessStartInfo
    $info.FileName = $Command
    $info.Arguments = (@($Arguments | ForEach-Object { ConvertTo-DemoProcessArgument $_ }) -join ' ')
    $info.WorkingDirectory = $Directory
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.StandardOutputEncoding = [Text.Encoding]::UTF8
    $info.StandardErrorEncoding = [Text.Encoding]::UTF8
    $process = New-Object Diagnostics.Process
    $process.StartInfo = $info
    try {
        $null = $process.Start()
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        $clock = [Diagnostics.Stopwatch]::StartNew()
        $next = 15
        while (-not $process.WaitForExit(250)) {
            if ($clock.Elapsed.TotalSeconds -gt $TimeoutSeconds) {
                $process.Kill()
                $null = $process.WaitForExit(3000)
                throw 'Lệnh Docker quá thời gian chờ. Kiểm tra Docker Desktop và kết nối mạng rồi chạy lại.'
            }
            if ($Progress -and $clock.Elapsed.TotalSeconds -ge $next) {
                Write-Host ('  Docker đang xử lý... ' + [int]$clock.Elapsed.TotalSeconds + ' giây')
                $next += 15
            }
        }
        return [pscustomobject]@{ ExitCode = $process.ExitCode; Out = $stdout.GetAwaiter().GetResult(); Error = $stderr.GetAwaiter().GetResult() }
    } finally { $process.Dispose() }
}

function Find-DemoDocker {
    $command = Get-Command docker -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($command) { return $command.Source }
    if ($env:ProgramFiles) {
        $path = Join-Path $env:ProgramFiles 'Docker\Docker\resources\bin\docker.exe'
        if ([IO.File]::Exists($path)) { return $path }
    }
    throw 'Chưa tìm thấy Docker. Cài và mở Docker Desktop (Linux containers), hoàn tất WSL 2 nếu được yêu cầu, rồi bấm CHAY_DEMO.bat lần nữa. Hướng dẫn: https://docs.docker.com/desktop/setup/install/windows-install/'
}

function Start-DemoDesktop {
    if ($env:OS -ne 'Windows_NT' -or -not $env:ProgramFiles) { return $false }
    $desktop = Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'
    if (-not [IO.File]::Exists($desktop)) { return $false }
    $null = Start-Process -FilePath $desktop
    return $true
}

function Wait-DemoDocker {
    param([string]$Command, [string]$Directory, [int]$TimeoutSeconds = 150)
    $version = Invoke-DemoProcess $Command @('compose', 'version') $Directory
    if ($version.ExitCode -ne 0) { throw 'Docker chưa có Compose V2. Hãy cài hoặc cập nhật Docker Desktop rồi chạy lại.' }
    $engine = Invoke-DemoProcess $Command @('info', '--format', '{{.OSType}}') $Directory
    if ($engine.ExitCode -ne 0) {
        if (-not (Start-DemoDesktop)) { throw 'Docker Engine chưa chạy. Mở Docker Desktop, đợi Engine running rồi chạy lại.' }
        Write-Host 'Đang mở Docker Desktop và chờ Engine sẵn sàng...'
        $clock = [Diagnostics.Stopwatch]::StartNew()
        do {
            Start-Sleep -Seconds 2
            $engine = Invoke-DemoProcess $Command @('info', '--format', '{{.OSType}}') $Directory
        } while ($engine.ExitCode -ne 0 -and $clock.Elapsed.TotalSeconds -lt $TimeoutSeconds)
    }
    if ($engine.ExitCode -ne 0) { throw 'Docker Desktop chưa sẵn sàng. Kiểm tra WSL 2/ảo hóa và trạng thái Engine running, rồi chạy lại.' }
    if ($engine.Out.Trim() -ne 'linux') { throw 'Dự án cần Linux containers. Trong Docker Desktop, chuyển sang Linux containers rồi chạy lại.' }
}

function Get-DemoComposeArguments {
    param([string]$Directory)
    return @('compose', '--project-directory', $Directory, '--env-file', (Join-Path $Directory '.env'), '-f', (Join-Path $Directory 'compose.yaml'))
}

function Get-DemoComposePlan {
    param([string]$Command, [string]$Directory, [System.Collections.IDictionary]$Settings)
    $arguments = @(Get-DemoComposeArguments $Directory) + @('config', '--format', 'json')
    $result = Invoke-DemoProcess $Command $arguments $Directory
    if ($result.ExitCode -ne 0) { throw 'Compose không đọc được cấu hình. Kiểm tra cú pháp .env và compose.yaml; không chạy config công khai nếu có khóa bí mật.' }
    try { $plan = $result.Out | ConvertFrom-Json -ErrorAction Stop }
    catch { throw 'Không đọc được kết quả Compose. Hãy dùng Docker Desktop với Compose V2.' }
    if (-not $plan.services.PSObject.Properties['seminar-app'] -or -not $plan.volumes.PSObject.Properties['seminar_data']) {
        throw 'compose.yaml không đúng cấu trúc bản Seminar 2.4: cần service seminar-app và volume seminar_data.'
    }
    $service = $plan.services.'seminar-app'
    if ([string]$service.environment.SERVER_PORT -ne [string]$Settings['SERVER_PORT']) {
        throw 'SERVER_PORT mà Compose nhận không khớp .env. Kiểm tra biến môi trường đang đặt trong terminal.'
    }
    $matched = @($service.ports | Where-Object { [string]$_.published -eq [string]$Settings['SERVER_PORT'] -and [string]$_.target -eq [string]$Settings['SERVER_PORT'] })
    if ($matched.Count -eq 0) { throw 'Ánh xạ cổng Docker không khớp SERVER_PORT. Giữ cấu trúc compose.yaml của dự án hoặc sửa cổng cho đồng bộ.' }
    return $plan
}

function Test-DemoExistingData {
    param([string]$Command, [string]$Directory, $Plan)
    $data = Join-Path $Directory 'data'
    if ([IO.Directory]::Exists($data) -and @(Get-ChildItem -LiteralPath $data -Filter '*.mv.db' -File).Count -gt 0) { return $true }
    $volume = [string]$Plan.volumes.seminar_data.name
    if (-not $volume) { throw 'Không xác định được volume dữ liệu từ Compose; chưa sinh khóa để tránh làm mất kết nối Drive cũ.' }
    $result = Invoke-DemoProcess $Command @('volume', 'ls', '--format', '{{.Name}}') $Directory
    if ($result.ExitCode -ne 0) { throw 'Không kiểm tra được vùng dữ liệu Docker. Kiểm tra Engine rồi chạy lại; chưa thay đổi khóa Drive.' }
    return $volume -cin @($result.Out -split '\r?\n' | ForEach-Object { $_.Trim() })
}

function Confirm-DemoKeyRecovery {
    Add-Type -AssemblyName System.Windows.Forms
    $message = "Đã có database hoặc Docker volume, nhưng .env đang thiếu khóa Drive.`r`n`r`nNếu đã kết nối Drive trước đây, hãy chọn No, khôi phục DRIVE_ADMIN_TOKEN_KEY từ bản sao lưu rồi chạy lại.`r`n`r`nChỉ chọn Yes nếu đây là dữ liệu chưa kết nối Drive hoặc bạn chấp nhận đăng nhập chủ thư mục để kết nối lại Drive sau khi tạo khóa mới."
    $answer = [Windows.Forms.MessageBox]::Show($message, 'Kiểm tra khóa Drive trước khi tạo', [Windows.Forms.MessageBoxButtons]::YesNo, [Windows.Forms.MessageBoxIcon]::Warning, [Windows.Forms.MessageBoxDefaultButton]::Button2)
    return $answer -eq [Windows.Forms.DialogResult]::Yes
}

function Test-DemoHealth {
    param([string]$Url)
    try {
        $response = Invoke-WebRequest -Uri ($Url + '/health') -UseBasicParsing -TimeoutSec 3 -MaximumRedirection 0 -ErrorAction Stop
        $body = $response.Content | ConvertFrom-Json -ErrorAction Stop
        return $response.StatusCode -eq 200 -and $body.PSObject.Properties['status'] -and $body.status -ceq 'ok' -and $body.PSObject.Properties['version']
    } catch { return $false }
}

function Wait-DemoApplication {
    param([string]$Command, [string]$Directory, [string]$Url, [int]$TimeoutSeconds = 180)
    $clock = [Diagnostics.Stopwatch]::StartNew()
    $next = 0
    do {
        if ($clock.Elapsed.TotalSeconds -ge $next) {
            $result = Invoke-DemoProcess $Command (@(Get-DemoComposeArguments $Directory) + @('ps', '--all', '--quiet', 'seminar-app')) $Directory
            if ($result.ExitCode -ne 0 -or -not $result.Out.Trim()) { throw 'Không tìm thấy container seminar-app sau khi khởi chạy. Kiểm tra docker compose ps và README mục xử lý lỗi.' }
            $containerId = ($result.Out.Trim() -split '\r?\n')[0]
            $state = Invoke-DemoProcess $Command @('inspect', '--format', '{{.State.Status}}', $containerId) $Directory
            if ($state.ExitCode -ne 0 -or $state.Out.Trim() -in @('exited', 'dead')) { throw 'Backend đã dừng khi khởi động. Xem docker compose logs --tail=80 seminar-app để xử lý.' }
            Write-Host 'Đang chờ Backend phản hồi /health...'
            $next += 10
        }
        if (Test-DemoHealth $Url) { return }
        Start-Sleep -Seconds 2
    } while ($clock.Elapsed.TotalSeconds -lt $TimeoutSeconds)
    throw 'Backend chưa sẵn sàng sau thời gian chờ. Kiểm tra docker compose logs --tail=80 seminar-app và cổng SERVER_PORT; chưa mở trình duyệt.'
}

function Open-DemoBrowser {
    param([string]$Url)
    try { $null = Start-Process -FilePath $Url }
    catch { Write-Host ('Ứng dụng đã chạy. Mở trình duyệt thủ công: ' + $Url) }
}

function Start-SeminarDemo {
    param([string]$ProjectDirectory, [switch]$Configure, [switch]$NoBrowser, [string]$DockerCommand)
    $directory = [IO.Path]::GetFullPath($ProjectDirectory)
    foreach ($file in @('compose.yaml', 'Dockerfile', 'pom.xml')) {
        if (-not [IO.File]::Exists((Join-Path $directory $file))) { throw 'Chép bản cập nhật vào đúng thư mục có compose.yaml, Dockerfile và pom.xml rồi chạy lại.' }
    }
    $lock = $null
    try {
        try { $lock = [IO.File]::Open((Join-Path $directory '.demo-launch.lock'), [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None) }
        catch { throw 'Bộ khởi chạy đang được mở ở cửa sổ khác, hoặc thư mục không cho phép ghi. Đóng cửa sổ cũ/kiểm tra quyền rồi thử lại.' }
        $document = Initialize-DemoEnv $directory
        $settings = Get-DemoSettings $document
        $errors = @(Get-DemoConfigErrors $settings)
        if ($Configure -or $errors.Count -gt 0) {
            $candidate = Show-DemoSetup $settings $errors
            if ($null -eq $candidate) { Write-Host 'Đã hủy thiết lập. Chưa chạy ứng dụng.'; return }
            $document = Save-DemoEnv $document $candidate
            $settings = Get-DemoSettings $document
        }
        # Only persist defaults needed to run; preserve all other existing settings.
        $defaults = @{}
        foreach ($name in @('SERVER_PORT', 'APP_BASE_URL', 'GOOGLE_REDIRECT_URI', 'DRIVE_ACCESS_MODE', 'COOKIE_SECURE')) {
            if (-not $document.Values.ContainsKey($name) -or -not $document.Values[$name]) { $defaults[$name] = $settings[$name] }
        }
        if ($defaults.Count) { $document = Save-DemoEnv $document $defaults }
        Write-Host 'Cấu hình đã đủ. Đang kiểm tra Docker...'
        if (-not $DockerCommand) { $DockerCommand = Find-DemoDocker }
        Wait-DemoDocker $DockerCommand $directory
        $plan = Get-DemoComposePlan $DockerCommand $directory $settings
        if (-not $settings.DRIVE_ADMIN_TOKEN_KEY) {
            if ((Test-DemoExistingData $DockerCommand $directory $plan) -and -not (Confirm-DemoKeyRecovery)) {
                Write-Host 'Chưa tạo khóa mới. Khôi phục khóa Drive cũ trong .env rồi chạy lại.'
                return
            }
            $document = Save-DemoEnv $document @{ DRIVE_ADMIN_TOKEN_KEY = New-DemoDriveKey }
            $settings = Get-DemoSettings $document
            Write-Host 'Đã tạo và lưu khóa Drive mới; không hiển thị khóa trên màn hình.'
        }
        Write-Host 'Đang build và chạy Docker. Lần đầu cần tải image/thư viện; vui lòng giữ cửa sổ này...'
        $result = Invoke-DemoProcess $DockerCommand (@(Get-DemoComposeArguments $directory) + @('up', '-d', '--build', 'seminar-app')) $directory -TimeoutSeconds 1800 -Progress
        if ($result.ExitCode -ne 0) {
            throw 'Docker build/khởi chạy thất bại. Kiểm tra Docker Desktop, mạng và cổng; chạy docker compose up -d --build seminar-app tại thư mục dự án để xem lỗi chi tiết.'
        }
        $url = 'http://localhost:' + $settings.SERVER_PORT
        Wait-DemoApplication $DockerCommand $directory $url
        Write-Host ('Ứng dụng đã sẵn sàng: ' + $url)
        if (-not $NoBrowser) { Open-DemoBrowser $url }
    } finally { if ($null -ne $lock) { $lock.Dispose() } }
}
