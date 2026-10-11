# Atlas 512 — strefy zgodne z U.* w generatorze (+ szum dla bogactwa koloru)
$out = Join-Path $PSScriptRoot "..\ModelEngine\blueprints\goblin\goblin.png"
$srcAtlas = "C:\Users\bonie\.cursor\projects\c-Users-bonie-Desktop-FLAMEHUB-CORE\assets\goblin_atlas_512.jpg"

Add-Type -AssemblyName System.Drawing
$bmp = New-Object System.Drawing.Bitmap 512, 512
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic

if (Test-Path $srcAtlas) {
  $img = [System.Drawing.Image]::FromFile($srcAtlas)
  $g.DrawImage($img, 0, 0, 512, 512)
  $img.Dispose()
} else {
  $g.Clear([System.Drawing.Color]::FromArgb(28, 26, 24))
}

function Patch($x, $y, $w, $h, $r, $gr, $b) {
  $brush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb($r, $gr, $b))
  $g.FillRectangle($brush, $x, $y, $w, $h)
  $brush.Dispose()
}

# Wzmocnienie stref UV (pod box mapping)
Patch 8 8 56 56 95 168 82
Patch 72 8 56 56 118 195 98
Patch 136 8 56 56 58 112 54
Patch 200 8 56 56 118 78 48
Patch 264 8 56 56 88 58 38
Patch 328 8 56 56 155 155 165
Patch 392 8 56 56 140 88 52
Patch 8 104 56 56 108 72 40
Patch 72 104 56 56 72 48 28
Patch 136 104 56 56 168 58 48
Patch 448 8 48 48 255 228 72
Patch 448 56 48 24 235 235 220

$rand = New-Object System.Random 4242
for ($i = 0; $i -lt 12000; $i++) {
  $px = $rand.Next(0, 512)
  $py = $rand.Next(0, 512)
  $d = $rand.Next(-22, 22)
  $base = $bmp.GetPixel($px, $py)
  $nr = [Math]::Max(0, [Math]::Min(255, [int]$base.R + $d))
  $ng = [Math]::Max(0, [Math]::Min(255, [int]$base.G + $d))
  $nb = [Math]::Max(0, [Math]::Min(255, [int]$base.B + $d))
  $bmp.SetPixel($px, $py, [System.Drawing.Color]::FromArgb($nr, $ng, $nb))
}

$bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose()
$bmp.Dispose()
Write-Host "Texture: $out"
