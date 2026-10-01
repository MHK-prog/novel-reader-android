# امضای ثابت برای نصب به‌روزرسانی‌ها

اندروید فقط APKای را روی برنامهٔ نصب‌شده به‌روزرسانی می‌کند که با همان شناسهٔ برنامه و همان کلید امضا ساخته شده باشد. شناسهٔ برنامهٔ این پروژه `com.novelreader.app` است. کلید JKS را امن نگه دار و آن را داخل GitHub یا مخزن پروژه قرار نده.

## ساخت کلید در ویندوز

PowerShell را باز کن و این فرمان را اجرا کن:

```powershell
$keytool = (Get-Command keytool.exe -ErrorAction Stop).Source
$keyDir = Join-Path $env:USERPROFILE '.novel-reader-signing'
$keyPath = Join-Path $keyDir 'novel-reader-debug.jks'
New-Item -ItemType Directory -Path $keyDir -Force | Out-Null
& $keytool -genkeypair -v -keystore $keyPath -alias novelreader -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Novel Reader'
if ($LASTEXITCODE -ne 0) { throw 'ساخت کلید ناموفق بود.' }
[Convert]::ToBase64String([IO.File]::ReadAllBytes($keyPath)) | Set-Clipboard
Write-Host 'مقدار Base64 در Clipboard کپی شد. فایل JKS و رمزهایش را امن نگه دار.'
```

رمز مخزن را انتخاب و نگه‌داری کن. برای رمز کلید هم همان رمز را وارد کن. Alias باید `novelreader` باشد.

## ثبت Secretها در GitHub

در مخزن برو به **Settings → Secrets and variables → Actions** و این Secretها را بساز:

| Secret | مقدار |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | مقدار Base64 کپی‌شده |
| `ANDROID_KEYSTORE_PASSWORD` | رمز JKS |
| `ANDROID_KEY_ALIAS` | `novelreader` |
| `ANDROID_KEY_PASSWORD` | رمز کلید |

پس از آن، GitHub Actions از همین کلید برای ساخت نسخه‌های بعدی استفاده می‌کند. اگر APK قبلی با کلید دیگری امضا شده باشد، اندروید به‌روزرسانی را نمی‌پذیرد و نصب نسخهٔ قبلی باید حذف شود.
