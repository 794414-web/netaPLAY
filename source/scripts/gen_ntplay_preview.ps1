# Draws a preview of the modified netaPLAY host page plus the launcher icon.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$repo = Split-Path -Parent $PSScriptRoot
$logo = [System.Drawing.Bitmap]::FromFile((Join-Path $repo 'common\src\main\res\drawable-nodpi\ic_ntplay_logo.png'))
$launcher = [System.Drawing.Bitmap]::FromFile((Join-Path $repo 'asset\ntplay_launcher_preview.png'))

$W = 1680; $H = 780
$bmp = New-Object System.Drawing.Bitmap($W, $H)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = 'AntiAlias'
$g.TextRenderingHint = 'AntiAlias'

$panel = [System.Drawing.Color]::FromArgb(255, 12, 17, 27)
$desk = [System.Drawing.Color]::FromArgb(255, 5, 7, 13)
$white = [System.Drawing.Color]::FromArgb(255, 241, 245, 252)
$gray = [System.Drawing.Color]::FromArgb(255, 168, 182, 202)
$faint = [System.Drawing.Color]::FromArgb(255, 120, 134, 156)

$g.Clear($desk)

function Font([int]$size, [bool]$bold = $false) {
    $style = if ($bold) { [System.Drawing.FontStyle]::Bold } else { [System.Drawing.FontStyle]::Regular }
    return New-Object System.Drawing.Font('Microsoft YaHei', [float]$size, $style, [System.Drawing.GraphicsUnit]::Pixel)
}
function Center([int]$cx, [int]$cy) {
    $f = New-Object System.Drawing.StringFormat
    $f.Alignment = 'Center'; $f.LineAlignment = 'Center'
    return $f
}

# ---- left: the modified host page (panel 1220x780) ----
$pageX = 0
$g.FillRectangle((New-Object System.Drawing.SolidBrush($panel)), $pageX, 0, 1220, $H)

$cx = $pageX + 610
function DrawCenterText([string]$text, [int]$y, [int]$size, [System.Drawing.Color]$color, [bool]$bold = $false) {
    $script:g.DrawString($text, (Font $size $bold), (New-Object System.Drawing.SolidBrush($color)), [float]$script:cx, [float]$y, (Center 0 0)) | Out-Null
}
# measure-free vertical rhythm mirroring buildContentView
$logoSize = 120
$g.DrawImage($logo, ($cx - $logoSize/2), 46, $logoSize, $logoSize)
$y = 46 + $logoSize + 24
DrawCenterText 'netaPLAY' $y 48 $white $true
$y += 70
DrawCenterText '车机无法启动 CarPlay Wi-Fi。请检查 Wi-Fi 并关闭其他投屏应用,正在重试…' $y 26 $white
$y += 60
DrawCenterText '请将 iPhone 放在附近并开启蓝牙和 Wi-Fi。' ($y) 21 $gray
$y += 32
DrawCenterText 'iPhone 出现提示时允许使用 CarPlay。' $y 21 $gray
$y += 56
# buttons
$bw = 380; $bh = 78
$bx = $cx - $bw/2
$btn1 = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 62, 76, 96))
$g.FillRectangle($btn1, $bx, $y, $bw, $bh)
$fmt = Center 0 0
$g.DrawString('我要手动指定 WiFi', (Font 24), (New-Object System.Drawing.SolidBrush($white)), [float]($cx), [float]($y + $bh/2), $fmt) | Out-Null
$y += $bh + 16
$btn2 = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 166, 200, 255))
$g.FillRectangle($btn2, $bx, $y, $bw, $bh)
$dark = New-Object System.Drawing.SolidBrush($panel)
$g.DrawString('返回 DiPlay', (Font 24), $dark, [float]($cx), [float]($y + $bh/2), $fmt) | Out-Null
$y += $bh + 26
DrawCenterText 'CarPlay 中用 3 指下滑打开 netaPLAY 设置。' $y 18 $gray
$y += 44
# NEW footer lines
DrawCenterText '哪吒汽车专用' $y 17 $faint
$y += 28
DrawCenterText '来自 DiPlay 项目:github.com/shihabal3amri/DiPlay' $y 17 $faint

# ---- right: launcher icon on a mock home screen ----
$iconX = 1320; $iconY = 160; $iconSize = 320
$g.FillRectangle((New-Object System.Drawing.SolidBrush($desk)), 1220, 0, $W - 1220, $H)
$g.DrawImage($launcher, $iconX, $iconY, $iconSize, $iconSize)
$g.DrawString('netaPLAY', (Font 30 $true), (New-Object System.Drawing.SolidBrush($white)), [float]($iconX + $iconSize/2), [float]($iconY + $iconSize + 26), (Center 0 0)) | Out-Null
$cap = New-Object System.Drawing.SolidBrush($faint)
$g.DrawString('修改后的页面效果', (Font 22), $cap, [float]$cx, 20, (Center 0 0)) | Out-Null
$g.DrawString('车机桌面图标效果', (Font 22), $cap, [float]($iconX + $iconSize/2), $iconY - 40, (Center 0 0)) | Out-Null

$out = Join-Path $repo 'asset\ntplay_ui_preview.png'
$bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
Write-Host "saved $out"
