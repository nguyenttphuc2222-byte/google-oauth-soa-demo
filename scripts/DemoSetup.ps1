#Requires -Version 5.1
Set-StrictMode -Version 2.0

function Show-DemoSetup {
    param([System.Collections.IDictionary]$Settings, [string[]]$Errors)
    Add-Type -AssemblyName System.Windows.Forms
    Add-Type -AssemblyName System.Drawing
    [Windows.Forms.Application]::EnableVisualStyles()
    $form = New-Object Windows.Forms.Form
    $form.Text = 'Seminar OAuth — Thiết lập và chạy'
    $form.StartPosition = 'CenterScreen'
    $form.Size = New-Object Drawing.Size(900, 760)
    $form.MinimumSize = New-Object Drawing.Size(820, 680)
    $form.Font = New-Object Drawing.Font('Segoe UI', 10)
    $form.AutoScaleMode = [Windows.Forms.AutoScaleMode]::Dpi
    $form.MaximizeBox = $false

    $layout = New-Object Windows.Forms.TableLayoutPanel
    $layout.Dock = 'Fill'
    $layout.Padding = New-Object Windows.Forms.Padding(16)
    $layout.ColumnCount = 1
    $layout.RowCount = 4
    $null = $layout.RowStyles.Add((New-Object Windows.Forms.RowStyle([Windows.Forms.SizeType]::Absolute, 70)))
    $null = $layout.RowStyles.Add((New-Object Windows.Forms.RowStyle([Windows.Forms.SizeType]::Percent, 100)))
    $null = $layout.RowStyles.Add((New-Object Windows.Forms.RowStyle([Windows.Forms.SizeType]::Absolute, 88)))
    $null = $layout.RowStyles.Add((New-Object Windows.Forms.RowStyle([Windows.Forms.SizeType]::Absolute, 48)))
    $form.Controls.Add($layout)

    $intro = New-Object Windows.Forms.Label
    $intro.Text = "Điền cấu hình Google của bạn, rồi chọn Lưu và chạy.`r`nThông tin đã có được giữ lại. Khóa Drive được tạo tự động khi chưa có khóa."
    $intro.Dock = 'Fill'
    $layout.Controls.Add($intro, 0, 0)
    $tabs = New-Object Windows.Forms.TabControl
    $tabs.Dock = 'Fill'
    $layout.Controls.Add($tabs, 0, 1)
    $fields = @{}

    function Add-DemoTab {
        param([string]$Title)
        $page = New-Object Windows.Forms.TabPage
        $page.Text = $Title
        $page.Padding = New-Object Windows.Forms.Padding(12)
        $page.AutoScroll = $true
        $table = New-Object Windows.Forms.TableLayoutPanel
        $table.Dock = 'Top'
        $table.AutoSize = $true
        $table.ColumnCount = 2
        $null = $table.ColumnStyles.Add((New-Object Windows.Forms.ColumnStyle([Windows.Forms.SizeType]::Percent, 42)))
        $null = $table.ColumnStyles.Add((New-Object Windows.Forms.ColumnStyle([Windows.Forms.SizeType]::Percent, 58)))
        $page.Controls.Add($table)
        $null = $tabs.TabPages.Add($page)
        return $table
    }

    function Add-DemoField {
        param($Table, [string]$Name, [string]$Description, [switch]$Secret)
        $row = $Table.RowCount
        $Table.RowCount++
        $null = $Table.RowStyles.Add((New-Object Windows.Forms.RowStyle([Windows.Forms.SizeType]::AutoSize)))
        $label = New-Object Windows.Forms.Label
        $label.Text = $Name + "`r`n" + $Description
        $label.AutoSize = $true
        $label.MaximumSize = New-Object Drawing.Size(310, 0)
        $label.Margin = New-Object Windows.Forms.Padding(0, 5, 8, 16)
        $box = New-Object Windows.Forms.TextBox
        $box.Dock = 'Top'
        $box.Margin = New-Object Windows.Forms.Padding(0, 6, 0, 16)
        $box.UseSystemPasswordChar = [bool]$Secret
        if ($Settings.Contains($Name)) { $box.Text = [string]$Settings[$Name] }
        if (Test-DemoPlaceholder $box.Text) { $box.Text = '' }
        $fields[$Name] = $box
        $Table.Controls.Add($label, 0, $row)
        $Table.Controls.Add($box, 1, $row)
    }

    function Add-DemoNote {
        param($Table, [string]$Text)
        $row = $Table.RowCount
        $Table.RowCount++
        $note = New-Object Windows.Forms.Label
        $note.Text = $Text
        $note.AutoSize = $true
        $note.MaximumSize = New-Object Drawing.Size(730, 0)
        $note.Margin = New-Object Windows.Forms.Padding(0, 10, 0, 14)
        $Table.Controls.Add($note, 0, $row)
        $Table.SetColumnSpan($note, 2)
    }

    $google = Add-DemoTab '1. Google Cloud'
    Add-DemoNote $google 'Tạo project, bật Drive/Gmail/Picker API và tạo OAuth client Web application theo README. Các thông tin dưới đây cần thuộc cùng project.'
    Add-DemoField $google 'GOOGLE_CLIENT_ID' 'Client ID của OAuth client Web application'
    Add-DemoField $google 'GOOGLE_CLIENT_SECRET' 'Client Secret của cùng OAuth client' -Secret
    Add-DemoField $google 'GOOGLE_API_KEY' 'API key cho Google Picker và Drive API' -Secret
    Add-DemoField $google 'GOOGLE_PROJECT_NUMBER' 'Project number dạng số, không phải Project ID'
    $import = New-Object Windows.Forms.Button
    $import.Text = 'Nhập JSON OAuth...'
    $import.AutoSize = $true
    $google.Controls.Add($import, 1, $google.RowCount)
    $google.RowCount++
    Add-DemoNote $google 'API key cần giới hạn theo website local và https://docs.google.com/*, đồng thời chỉ bật quyền API cần thiết như README.'

    $drive = Add-DemoTab '2. Google Drive'
    Add-DemoField $drive 'DRIVE_FOLDER_ID' 'ID thư mục Seminar - Minh chứng, không nhập cả URL'
    Add-DemoField $drive 'DRIVE_ADMIN_EMAIL' 'Email TDTU thực sự sở hữu thư mục (ISOLATED/SHARED)'
    $modeLabel = New-Object Windows.Forms.Label
    $modeLabel.Text = 'DRIVE_ACCESS_MODE'
    $modeLabel.AutoSize = $true
    $modeLabel.Margin = New-Object Windows.Forms.Padding(0, 6, 8, 16)
    $mode = New-Object Windows.Forms.ComboBox
    $mode.DropDownStyle = [Windows.Forms.ComboBoxStyle]::DropDownList
    $mode.Dock = 'Top'
    $mode.Items.AddRange(@('ISOLATED', 'MANUAL', 'SHARED'))
    $mode.SelectedItem = [string]$Settings['DRIVE_ACCESS_MODE']
    $drive.Controls.Add($modeLabel, 0, $drive.RowCount)
    $drive.Controls.Add($mode, 1, $drive.RowCount)
    $drive.RowCount++
    Add-DemoNote $drive "ISOLATED: thư mục riêng cho từng sinh viên; giảng viên xem từng file được giao.`r`nMANUAL: bạn chuẩn bị quyền Drive thủ công.`r`nSHARED: các tài khoản được cấp quyền trên thư mục chung, có thể xem nội dung của nhau qua Drive. Chỉ chọn nếu bạn chấp nhận phạm vi này."
    Add-DemoField $drive 'DRIVE_ADMIN_TOKEN_KEY' 'Để trống khi cài mới. Nếu có dữ liệu cũ, giữ hoặc khôi phục đúng khóa cũ.' -Secret
    Add-DemoNote $drive 'Bộ thiết lập chỉ lưu cấu hình. Sau khi app mở, chủ thư mục vẫn phải đăng nhập Google và kết nối Drive một lần trong trang Thiết lập Drive.'

    $local = Add-DemoTab '3. Địa chỉ local'
    Add-DemoField $local 'SERVER_PORT' 'Mặc định 8080; cổng phải còn dùng được trên máy'
    Add-DemoField $local 'APP_BASE_URL' 'Mặc định http://localhost:8080'
    Add-DemoField $local 'GOOGLE_REDIRECT_URI' 'Đăng ký chính xác địa chỉ này trên Google Cloud'
    $secure = New-Object Windows.Forms.CheckBox
    $secure.Text = 'COOKIE_SECURE — chỉ bật khi truy cập bằng HTTPS'
    $secure.AutoSize = $true
    $secure.Checked = $Settings['COOKIE_SECURE'] -eq 'true'
    $local.Controls.Add($secure, 0, $local.RowCount)
    $local.SetColumnSpan($secure, 2)
    $local.RowCount++
    Add-DemoNote $local 'Khi đổi cổng localhost, địa chỉ mặc định được đổi theo. Bạn vẫn cần cập nhật OAuth origin/callback và API key referrer trong Google Cloud. Bộ khởi chạy mở trang HTTP localhost trên cổng đã chọn.'
    $state = @{ PreviousPort = $fields.SERVER_PORT.Text; Candidate = $null }
    $fields.SERVER_PORT.Add_TextChanged({
        $previousOrigin = 'http://localhost:' + $state.PreviousPort
        $newOrigin = 'http://localhost:' + $fields.SERVER_PORT.Text
        if ($fields.APP_BASE_URL.Text -eq $previousOrigin) { $fields.APP_BASE_URL.Text = $newOrigin }
        if ($fields.GOOGLE_REDIRECT_URI.Text -eq ($previousOrigin + '/login/oauth2/code/google')) { $fields.GOOGLE_REDIRECT_URI.Text = $newOrigin + '/login/oauth2/code/google' }
        $state.PreviousPort = $fields.SERVER_PORT.Text
    })

    $errorBox = New-Object Windows.Forms.TextBox
    $errorBox.Multiline = $true
    $errorBox.ReadOnly = $true
    $errorBox.ScrollBars = [Windows.Forms.ScrollBars]::Vertical
    $errorBox.BackColor = $form.BackColor
    $errorBox.ForeColor = [Drawing.Color]::Firebrick
    $errorBox.BorderStyle = [Windows.Forms.BorderStyle]::None
    $errorBox.Dock = 'Fill'
    $errorBox.Text = (@($Errors | Select-Object -First 5) -join "`r`n")
    $layout.Controls.Add($errorBox, 0, 2)
    $footer = New-Object Windows.Forms.FlowLayoutPanel
    $footer.Dock = 'Fill'
    $footer.FlowDirection = [Windows.Forms.FlowDirection]::RightToLeft
    $layout.Controls.Add($footer, 0, 3)
    $save = New-Object Windows.Forms.Button
    $save.Text = 'Lưu và chạy'
    $save.AutoSize = $true
    $cancel = New-Object Windows.Forms.Button
    $cancel.Text = 'Hủy'
    $cancel.AutoSize = $true
    $cancel.DialogResult = [Windows.Forms.DialogResult]::Cancel
    $showSecrets = New-Object Windows.Forms.CheckBox
    $showSecrets.Text = 'Hiện khóa / Client Secret'
    $showSecrets.AutoSize = $true
    $showSecrets.Margin = New-Object Windows.Forms.Padding(12, 7, 12, 0)
    $footer.Controls.Add($save)
    $footer.Controls.Add($cancel)
    $footer.Controls.Add($showSecrets)
    $form.AcceptButton = $save
    $form.CancelButton = $cancel
    $showSecrets.Add_CheckedChanged({
        foreach ($name in @('GOOGLE_CLIENT_SECRET', 'GOOGLE_API_KEY', 'DRIVE_ADMIN_TOKEN_KEY')) { $fields[$name].UseSystemPasswordChar = -not $showSecrets.Checked }
    })
    $import.Add_Click({
        $picker = New-Object Windows.Forms.OpenFileDialog
        $picker.Title = 'Chọn JSON OAuth Web application'
        $picker.Filter = 'JSON files (*.json)|*.json'
        try {
            if ($picker.ShowDialog($form) -eq [Windows.Forms.DialogResult]::OK) {
                $imported = Get-DemoOAuthImport $picker.FileName $fields.GOOGLE_REDIRECT_URI.Text.Trim()
                $fields.GOOGLE_CLIENT_ID.Text = $imported.GOOGLE_CLIENT_ID
                $fields.GOOGLE_CLIENT_SECRET.Text = $imported.GOOGLE_CLIENT_SECRET
                $errorBox.Text = 'Đã nhập Client ID và Client Secret. Điền các trường còn lại rồi chọn Lưu và chạy.'
            }
        } catch { $errorBox.Text = $_.Exception.Message }
        finally { $picker.Dispose() }
    })
    $save.Add_Click({
        $candidate = @{}
        foreach ($name in $fields.Keys) { $candidate[$name] = $fields[$name].Text.Trim() }
        $candidate.DRIVE_ACCESS_MODE = [string]$mode.SelectedItem
        $candidate.COOKIE_SECURE = $secure.Checked.ToString().ToLowerInvariant()
        $validation = @(Get-DemoConfigErrors $candidate)
        if ($validation.Count -gt 0) { $errorBox.Text = $validation -join "`r`n"; return }
        $state.Candidate = $candidate
        $form.DialogResult = [Windows.Forms.DialogResult]::OK
        $form.Close()
    })
    try {
        if ($form.ShowDialog() -eq [Windows.Forms.DialogResult]::OK) { return $state.Candidate }
        return $null
    } finally { $form.Dispose() }
}
