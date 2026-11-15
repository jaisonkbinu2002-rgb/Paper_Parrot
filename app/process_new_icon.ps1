Add-Type -AssemblyName System.Drawing
$inputFile = "C:\Users\JAIZ\AndroidStudioProjects\PaperParrot\app\src\main\res\drawable\ic_paper_parrot_logo.png"
$outputFile = "C:\Users\JAIZ\AndroidStudioProjects/PaperParrot/app/src/main/res/drawable/ic_paper_parrot_nobg.png"

$bmp = [System.Drawing.Bitmap]::FromFile($inputFile)
$newBmp = New-Object System.Drawing.Bitmap($bmp.Width, $bmp.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)

for ($x = 0; $x -lt $bmp.Width; $x++) {
    for ($y = 0; $y -lt $bmp.Height; $y++) {
        $pixel = $bmp.GetPixel($x, $y)
        # White or near white background check
        if ($pixel.R -gt 230 -and $pixel.G -gt 230 -and $pixel.B -gt 230) {
            $newBmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(0, 0, 0, 0))
        } else {
            $newBmp.SetPixel($x, $y, $pixel)
        }
    }
}

$newBmp.Save($outputFile, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
$newBmp.Dispose()
Write-Host "New transparent icon saved to ic_paper_parrot_nobg.png"