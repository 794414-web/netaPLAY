# Generates the NTplay launcher icon set (Nezha logo + "NTplay") from the source logo PNG.
# Run from the repo root:  powershell -ExecutionPolicy Bypass -File scripts\gen_ntplay_icons.ps1
param(
    [Parameter(Mandatory = $true)][string]$SourceLogo
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$repo = Split-Path -Parent $PSScriptRoot
$sharedMipmap = Join-Path $repo 'shared\src\main\res'
$commonRes = Join-Path $repo 'common\src\main\res'

# 1. White-on-transparent cut-out of the black-on-white source logo: A = 255 - luminance,
#    RGB = white. Done in C# with LockBits because GDI+'s ColorMatrix alpha is unreliable
#    on 24bpp (no-alpha) sources.
$logoCutSource = @"
using System;
using System.Drawing;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;
public static class LogoCut {
    public static Bitmap WhiteOnTransparent(string path) {
        using (Bitmap src = new Bitmap(path)) {
            var out_ = new Bitmap(src.Width, src.Height, PixelFormat.Format32bppArgb);
            var rect = new Rectangle(0, 0, src.Width, src.Height);
            var sd = src.LockBits(rect, ImageLockMode.ReadOnly, PixelFormat.Format32bppArgb);
            var dd = out_.LockBits(rect, ImageLockMode.WriteOnly, PixelFormat.Format32bppArgb);
            int bytes = Math.Abs(sd.Stride) * src.Height;
            var buffer = new byte[bytes];
            Marshal.Copy(sd.Scan0, buffer, 0, bytes);
            for (int i = 0; i < bytes; i += 4) {
                byte lum = (byte)((buffer[i] + buffer[i + 1] + buffer[i + 2]) / 3);
                buffer[i] = 255; buffer[i + 1] = 255; buffer[i + 2] = 255;
                buffer[i + 3] = (byte)(255 - lum);
            }
            Marshal.Copy(buffer, 0, dd.Scan0, bytes);
            src.UnlockBits(sd);
            out_.UnlockBits(dd);
            return out_;
        }
    }
}
"@
Add-Type -TypeDefinition $logoCutSource -ReferencedAssemblies System.Drawing
$logo = [LogoCut]::WhiteOnTransparent($SourceLogo)

$bgColor = [System.Drawing.Color]::FromArgb(255, 12, 17, 27)   # app panel #0C111B
$textColor = [System.Drawing.Color]::White

function New-Canvas([int]$size) {
    $bmp = New-Object System.Drawing.Bitmap($size, $size)
    $graphics = [System.Drawing.Graphics]::FromImage($bmp)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAlias
    return @($bmp, $graphics)
}

function Draw-Logo($graphics, [int]$size, [double]$topRatio, [double]$heightRatio) {
    $h = [int]($size * $heightRatio)
    $w = $h  # source logo is square
    $x = [int](($size - $w) / 2)
    $y = [int]($size * $topRatio)
    $graphics.DrawImage($logo, $x, $y, $w, $h)
}

function Draw-Name($graphics, [int]$size, [double]$centerYRatio, [double]$fontRatio) {
    $font = New-Object System.Drawing.Font('Segoe UI', [float]($size * $fontRatio), [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
    $format = New-Object System.Drawing.StringFormat
    $format.Alignment = [System.Drawing.StringAlignment]::Center
    $format.LineAlignment = [System.Drawing.StringAlignment]::Center
    $brush = New-Object System.Drawing.SolidBrush($textColor)
    $cx = [float]($size / 2.0)
    $cy = [float]($size * $centerYRatio)
    $point = New-Object System.Drawing.PointF -ArgumentList $cx, $cy
    $graphics.DrawString('netaPLAY', $font, $brush, $point, $format)
    $brush.Dispose(); $font.Dispose(); $format.Dispose()
}

function Save-Png($bitmap, [string]$path) {
    $dir = Split-Path -Parent $path
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir | Out-Null }
    $bitmap.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
}

# 2. Adaptive icon foreground: logo + name inside the 66/108 safe circle.
foreach ($dpi in @('mdpi', 'hdpi', 'xhdpi', 'xxhdpi', 'xxxhdpi')) {
    $size = @{ mdpi = 108; hdpi = 162; xhdpi = 216; xxhdpi = 324; xxxhdpi = 432 }[$dpi]
    $bmp, $g = New-Canvas $size
    Draw-Logo $g $size 0.16 0.38
    Draw-Name $g $size 0.68 0.15
    Save-Png $bmp (Join-Path $sharedMipmap "mipmap-$dpi\ic_launcher_foreground.png")
    $g.Dispose(); $bmp.Dispose()

    # Solid background.
    $bmp, $g = New-Canvas $size
    $brush = New-Object System.Drawing.SolidBrush($bgColor)
    $g.FillRectangle($brush, 0, 0, $size, $size)
    $brush.Dispose()
    Save-Png $bmp (Join-Path $sharedMipmap "mipmap-$dpi\ic_launcher_background.png")
    $g.Dispose(); $bmp.Dispose()

    # Monochrome: logo silhouette only.
    $bmp, $g = New-Canvas $size
    Draw-Logo $g $size 0.22 0.56
    Save-Png $bmp (Join-Path $sharedMipmap "mipmap-$dpi\ic_launcher_monochrome.png")
    $g.Dispose(); $bmp.Dispose()
}

# 3. Legacy launcher icons (pre-Android 8): rounded square and circle.
function New-LegacyIcon([int]$size, [bool]$round) {
    $bmp, $g = New-Canvas $size
    $brush = New-Object System.Drawing.SolidBrush($bgColor)
    if ($round) {
        $g.FillEllipse($brush, 0, 0, $size, $size)
    } else {
        $radius = $size * 0.22
        $path = New-Object System.Drawing.Drawing2D.GraphicsPath
        $path.AddArc(0, 0, $radius, $radius, 180, 90)
        $path.AddArc($size - $radius, 0, $radius, $radius, 270, 90)
        $path.AddArc($size - $radius, $size - $radius, $radius, $radius, 0, 90)
        $path.AddArc(0, $size - $radius, $radius, $radius, 90, 90)
        $path.CloseFigure()
        $g.FillPath($brush, $path)
        $path.Dispose()
    }
    $brush.Dispose()
    Draw-Logo $g $size 0.14 0.40
    Draw-Name $g $size 0.70 0.16
    return $bmp
}

foreach ($dpi in @('mdpi', 'hdpi', 'xhdpi', 'xxhdpi', 'xxxhdpi')) {
    $size = @{ mdpi = 48; hdpi = 72; xhdpi = 96; xxhdpi = 144; xxxhdpi = 192 }[$dpi]
    $square = New-LegacyIcon $size $false
    Save-Png $square (Join-Path $sharedMipmap "mipmap-$dpi\ic_launcher.png")
    $square.Dispose()
    $circle = New-LegacyIcon $size $true
    Save-Png $circle (Join-Path $sharedMipmap "mipmap-$dpi\ic_launcher_round.png")
    $circle.Dispose()
}

# 4. Full-size copies for preview and module drawables.
$preview = New-LegacyIcon 512 $false
Save-Png $preview (Join-Path $repo 'asset\ntplay_launcher_preview.png')
$preview.Dispose()

# White logo on transparent, for the in-app header on the dark panel.
$bmp, $g = New-Canvas 432
Draw-Logo $g 432 0.06 0.88
Save-Png $bmp (Join-Path $commonRes 'drawable-nodpi\ic_ntplay_logo.png')
$g.Dispose(); $bmp.Dispose()

# Square launcher drawable (used by the mobile module manifest).
$bmp, $g = New-Canvas 432
$brush = New-Object System.Drawing.SolidBrush($bgColor)
$g.FillRectangle($brush, 0, 0, 432, 432)
$brush.Dispose()
Draw-Logo $g 432 0.16 0.38
Draw-Name $g 432 0.68 0.15
Save-Png $bmp (Join-Path $commonRes 'drawable-nodpi\ic_ntplay_launcher.png')
$g.Dispose(); $bmp.Dispose()

Write-Host 'NTplay icons generated.'
