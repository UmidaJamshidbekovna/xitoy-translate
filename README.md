# Ekran Tarjimon (Screen Translator)

Telefon ekranidagi **xitoycha** yozuvni bir tugma bosish bilan **o'zbekchaga** tarjima qiladigan Android ilova. Alipay, Pinduoduo, Taobao va boshqa xitoy ilovalarida ishlaydi — ayniqsa puzzle/captcha ("so'zni topib rasmni belgilang") uchun qulay.

## Qanday ishlaydi

1. Istalgan ilova ustida **suzuvchi ko'k tugma** (译) turadi.
2. Tugmani bossangiz — ilova ekrandan surat oladi.
3. **ML Kit** xitoycha matnni offline o'qiydi (OCR).
4. Google Translate orqali o'zbekchaga tarjima qiladi (API kalit shart emas).
5. Tarjima asl yozuvning ustiga chiqadi. Yopish uchun ekranga bosing.

## Talablar

- **Android Studio** (bepul: https://developer.android.com/studio)
- Android telefon (Android 7.0 / API 24 va undan yuqori)
- OCR offline ishlaydi, **tarjima uchun internet** kerak.

## Qanday yig'ish (build) va o'rnatish

### Variant A — Android Studio orqali (tavsiya etiladi)

1. Android Studio'ni o'rnating.
2. `File → Open` → shu `D:\translate` papkasini tanlang.
3. Android Studio Gradle'ni avtomatik yuklab, loyihani sozlaydi (bir necha daqiqa).
4. Telefonni USB bilan ulang (Developer options → USB debugging yoqilgan bo'lsin).
5. Yashil **Run (▶)** tugmasini bosing — ilova telefonga o'rnatiladi.

APK fayl olish uchun: `Build → Build Bundle(s) / APK(s) → Build APK(s)`.
Fayl shu yerda paydo bo'ladi: `app/build/outputs/apk/debug/app-debug.apk`

### Variant B — buyruq qatori orqali

Android Studio Gradle wrapper'ni yaratgach:

```
gradlew.bat assembleDebug
```

## Birinchi ishga tushirish

1. Ilovani oching → **Boshlash** tugmasi.
2. **"Boshqa ilovalar ustida ko'rsatish"** ruxsatini yoqing.
3. **"Ekranni yozib olish"** so'roviga — **Boshlash / Start now**.
4. Endi istalgan ilovani oching. Chetdagi ko'k tugmani bosing → tarjima chiqadi.
5. Tugmani xohlagan joyga surib qo'yish mumkin.
6. To'xtatish uchun: ilovadagi **To'xtatish** tugmasi yoki bildirishnomani yoping.

## Muhim eslatmalar

- Tarjima bepul, ochiq Google Translate nuqtasidan foydalanadi — **shaxsiy foydalanish** uchun. Agar ko'p ishlatilsa Google vaqtincha cheklab qo'yishi mumkin.
- O'zbekcha barqaror kerak bo'lsa, keyinchalik `Translator.kt` faylidagi manzilni rasmiy **Google Cloud Translation API** (kalit bilan) ga almashtirish mumkin.
- Xitoychadan boshqa til (masalan yaponcha) kerak bo'lsa, `ScreenTranslatorService.kt` dagi `ChineseTextRecognizerOptions` ni mos til modeliga o'zgartiring.

## Fayllar tuzilishi

```
app/src/main/java/com/example/ekrantarjimon/
  MainActivity.kt              — bosh ekran, ruxsatlar, ishga tushirish
  ScreenTranslatorService.kt   — suzuvchi tugma, ekran surati, OCR, boshqaruv
  Translator.kt                — xitoy → o'zbek tarjima
  OverlayView.kt               — tarjimani ekran ustiga chizish
```
