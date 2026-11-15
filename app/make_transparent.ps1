Add-Type -AssemblyName System.Drawing
$inputFile = "C:\Users\JAIZ\AndroidStudioProjects\PaperParrot\app\src\main\res\drawable\ic_paper_parrot_logo.png"
$outputFile = "C:\Users\JAIZ\AndroidStudioProjects\PaperParrot\app\src\main\res\drawable\ic_paper_parrot_nobg.png"

$bmp = [System.Drawing.Bitmap]::FromFile($inputFile)
$newBmp = New-Object System.Drawing.Bitmap($bmp.Width, $bmp.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)

for ($x = 0; $x -lt $bmp.Width; $x++) {
    for ($y = 0; $y -lt $bmp.Height; $y++) {
        $pixel = $bmp.GetPixel($x, $y)
        # Background is white/light or purple gradient background outside the parrot
        # Parrot has vibrant colors: eye has high contrast, yellow feather, purple feather
        # In the white background image: white/near white pixels (R>220, G>220, B>220)
        # In the purple background image: purple background pixels (R: 60-110, G: 30-70, B: 120-180)
        # Let's check distance to outer edge or check white/light background
        if ($pixel.R -gt 215 -and $pixel.G -gt 215 -and $pixel.B -gt 215) {
            $newBmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(0, 0, 0, 0))
        } else {
            $newBmp.SetPixel($x, $y, $pixel)
        }
    }
}

$newBmp.Save($outputFile, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
$newBmp.Dispose()
Write-Host "Transparent image created successfully."