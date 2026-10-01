# کتاب‌خوان اندروید — نسخهٔ 1.0.2

این نسخه رابط کاربری را با Viewهای بومی اندروید اجرا می‌کند و هیچ WebView یا چارچوب رابط کاربری بیرونی ندارد. تم سیاه و بنفش، فونت وزیرمتن، فهرست کتاب‌ها و Chapters، فیلتر تگ، پسند/نپسند، ذخیرهٔ خودکار، درصد مطالعه و نشان قرمز مطالعه در نسخهٔ بومی حفظ شده‌اند.

## ساخت APK

پیش‌نیازها: Android Studio یا Android SDK با Platform 36 و JDK 17.

در پوشهٔ پروژه اجرا کن:

```powershell
.\gradlew.bat assembleDebug
```

در Linux یا GitHub Actions:

```bash
./gradlew --no-daemon assembleDebug
```

APK خروجی:

```text
app/build/outputs/apk/debug/app-debug.apk
```

شناسهٔ برنامه `com.novelreader.app` حفظ شده و شمارهٔ نسخهٔ Android برابر 102 است؛ در نتیجه نسخهٔ 1.0.2 قابلیت نصب روی نسخه‌های قبلی با همان امضای پایدار را دارد. برای انتشار در فروشگاه، APK/AAB را با کلید انتشار خودت امضا کن.

## ذخیره‌سازی و انتقال داده

در اجرای اول پوشهٔ **Documents** را از انتخاب‌گر Android انتخاب کن؛ برنامه پوشهٔ `NovelReader` را در آن می‌سازد. اجازهٔ دسترسی فقط به همین پوشه و به‌شکل ماندگار ثبت می‌شود و مجوز کلی حافظه درخواست نمی‌شود.

- `library.json`: کتاب‌ها، نویسنده، تگ‌ها، وضعیت مطالعه و جای آخرین مطالعه
- `chapter-<شناسه>.md`: متن Markdown هر Chapter

قالب فایل‌ها با نسخهٔ قبلی سازگار است و Chapterهای `.txt` قدیمی نیز هنگام خواندن پشتیبانی می‌شوند. نسخهٔ جدید متن را در فایل `.md` ذخیره می‌کند.

## انتشار خودکار APK در GitHub

Workflow موجود در `.github/workflows/android-apk.yml` با هر Push پروژه را می‌سازد. فایل APK را از بخش **Actions → Build Android APK → Artifacts** دریافت کن.

برای اینکه APKهای بعدی روی نسخهٔ قبلی نصب شوند، کلید امضای ثابت را یک‌بار بساز و مقادیرش را در GitHub Secrets با نام‌های زیر قرار بده:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

راهنمای ساخت و ثبت کلید در [ANDROID_UPDATE_SIGNING.md](ANDROID_UPDATE_SIGNING.md) است. فایل JKS را در Git قرار نده.

## فایل‌های تصویری و فونت

آیکن نصب برنامه همان تصویر اصلی `app/src/main/res/drawable-nodpi/novel_reader_icon.png` است. فایل‌های قابل‌ویرایش SVG در پوشهٔ `icons/` نگه‌داری شده‌اند؛ رابط بومی برای رسم کنترل‌هایش از Canvas اندروید استفاده می‌کند. فونت وزیرمتن و مجوز آن داخل `app/src/main/assets/fonts/` قرار دارند.
