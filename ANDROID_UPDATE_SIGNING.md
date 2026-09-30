# امضای ثابت برای به‌روزرسانی اندروید

اندروید فقط APKهایی را روی برنامه‌ی نصب‌شده به‌روزرسانی می‌کند که با همان کلید قبلی امضا شده باشند. اگر در GitHub Actions از امضای پیش‌فرض Debug استفاده شود، هر اجرای تازه ممکن است کلید دیگری بسازد؛ در نتیجه نصب APK جدید به حذف نسخه‌ی قبلی نیاز پیدا می‌کند.

## ساخت کلید یک‌باره در ویندوز

PowerShell را باز کن و اجرا کن:

```powershell
$keytool = (Get-Command keytool.exe -ErrorAction Stop).Source
$keyDir = Join-Path $env:USERPROFILE '.novel-reader-signing'
$keyPath = Join-Path $keyDir 'novel-reader-debug.jks'
New-Item -ItemType Directory -Path $keyDir -Force | Out-Null
& $keytool -genkeypair -v -keystore $keyPath -alias novelreader -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Novel Reader'
if ($LASTEXITCODE -ne 0) { throw 'ساخت کلید ناموفق بود.' }
[Convert]::ToBase64String([IO.File]::ReadAllBytes($keyPath)) | Set-Clipboard
Write-Host 'کلید Base64 در Clipboard کپی شد. فایل JKS را امن نگه دار و داخل مخزن Git قرار نده.'
```

هنگام پرسش‌های `keytool` یک رمز انتخاب و نگه‌دار. برای رمز کلید (key password)، همان رمز مخزن را وارد کن. نام alias برابر `novelreader` است.

## ثبت GitHub Secrets

در مخزن GitHub برو به **Settings → Secrets and variables → Actions → New repository secret** و این چهار Secret را بساز:

| نام Secret | مقدار |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | مقدار Base64 که در Clipboard کپی شد |
| `ANDROID_KEYSTORE_PASSWORD` | رمز مخزن JKS |
| `ANDROID_KEY_ALIAS` | `novelreader` |
| `ANDROID_KEY_PASSWORD` | رمز کلید؛ اگر همان رمز مخزن را انتخاب کردی، همان را وارد کن |

از اجرای بعدی Workflow از این کلید پایدار استفاده می‌کند. کلید JKS را گم نکن؛ APKهای نسخه‌های بعدی باید با همین کلید امضا شوند. اگر Secretها ثبت نشده باشند، Workflow همچنان APK می‌سازد اما هشدار می‌دهد که امضای موقت ممکن است نصب نسخه‌ی تازه را به حذف نسخه‌ی قبلی وابسته کند.
