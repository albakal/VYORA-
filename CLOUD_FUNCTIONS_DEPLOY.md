# دليل تفعيل قاعدة بيانات Supabase المحاسبية ونشر Cloud Functions وتصدير APK

تم ربط تطبيق **VYORA** بقاعدة بيانات **Supabase (PostgreSQL)** لإدارة السجل المحاسبي للمحافظ والنقاط والقيود المالية وسجل الفيديوهات، مع إبقاء المصادقة عبر **Google Sign-In / Firebase Auth** والتفاعل اللحظي عبر **Firestore**.

---

## 1. إعداد جداول Supabase (SQL & RLS)

1. افتح لوحة تحكم مشروعك في **Supabase** -> **SQL Editor** -> **New Query**.
2. انسخ محتوى الملف `/supabase/schema.sql` بالكامل وقم بتشغيله (**Run**).
   - يقوم السكربت بإنشاء الجداول المحاسبية: `vyora_users_sync`, `vyora_videos_ledger`, `vyora_wallets`, `vyora_wallet_transactions`, `vyora_withdrawal_requests`.
   - يفعل سياسات الحماية (**Row-Level Security**) والدوال الذرية `record_vyora_view_reward` و `grant_vyora_points_ledger` و `review_vyora_withdrawal`.

---

## 2. إعداد المفاتيح بأمان (بدون وضع الأسرار داخل APK)

### أ) في لوحة Secrets داخل AI Studio (للعميل / التطبيق):
أضف المتغيرات التالية في **Secrets Panel** بـ AI Studio:
- `SUPABASE_URL`: رابط مشروعك (مثال: `https://<project-ref>.supabase.co`).
- `SUPABASE_PUBLISHABLE_KEY`: المفتاح العام لـ Supabase (المخصص للقراءة وفق سياسات RLS).

> ⚠️ **تنبيه أمني هام**: لا تضع أبداً المفتاح السري (`SUPABASE_SECRET_KEY`) داخل `.env` أو `.env.example` أو داخل كود تطبيق الأندرويد.

### ب) في Firebase Secret Manager (للخادم فقط):
نفّذ الأوامر التالية عبر **Firebase CLI** (`npm install -g firebase-tools`) لحفظ مفاتيح Agora و Supabase في الخادم فقط:

```bash
firebase login
firebase use com-aistudio-tokshort-ap-f5c5c

# مفاتيح البث المباشر Agora
firebase functions:secrets:set AGORA_APP_ID
firebase functions:secrets:set AGORA_APP_CERTIFICATE

# مفاتيح السجل المحاسبي Supabase (تعمل على الخادم فقط بصلاحيات كاملة آمنة)
firebase functions:secrets:set SUPABASE_URL
firebase functions:secrets:set SUPABASE_SECRET_KEY
```

---

## 3. بناء ونشر Cloud Functions

```bash
npm --prefix functions install
npm --prefix functions run build
firebase deploy --only functions
```

### الدوال السحابية المنشورة:
- `getAgoraLiveToken`: إصدار رمز بث Agora مؤقت وموقع من الخادم.
- `syncUserAndVideoToSupabase`: مزامنة بيانات المنشئ ومحفظته والفيديوهات الجديدة مع Supabase.
- `recordVideoViewReward`: احتساب المشاهدة وإضافة المكافآت في Firestore و Supabase ذرياً.
- `requestCreatorWithdrawal`: التحقق من الرصيد والحد الأدنى للسحب وتسجيل الطلب في القاعدتين.
- `adminGrantCreatorPoints` / `adminReviewWithdrawal`: تنفيذ قرارات المدير المحاسبية في Firestore و Supabase معاً.
- `getWalletAccountingSummary`: جلب ملخص الرصيد وعدد القيود من Supabase لشاشة المحفظة.
- `sendActivityPushNotification`: إرسال إشعارات الدفع (FCM).

---

## 4. تنزيل وتثبيت حزمة VYORA Debug APK

تم تجميع حزمة التطبيق محلياً داخل مخرجات المشروع في المسار:
```
app/build/outputs/apk/debug/app-debug.apk
```

يمكنك تصدير وتنزيل ملف الـ APK مباشرة من قائمة **Settings / Export / Generate APK** داخل واجهة **Google AI Studio**، أو تثبيته عبر جهاز متصل بـ USB:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 5. الرفع على GitHub والحصول على رابط ويب مباشر لتحميل الـ APK

نظراً لأن حجم ملف `app-debug.apk` يتجاوز حد GitHub المسموح به للملفات داخل الالتزامات المباشرة (100 ميجابايت)، تمت إضافة ملف بناء تلقائي:
```
.github/workflows/build-apk.yml
```
1. ارفع المشروع إلى مستودعك عبر زر **Push to GitHub** في قائمة الإعدادات بـ Google AI Studio (أو عبر `git push`).
2. سيعمل **GitHub Actions** تلقائياً على تجميع أحدث نسخة APK وإصدار **GitHub Release** عام.
3. رابط التحميل المباشر على الويب بعد اكتمال البناء سيكون:
   `https://github.com/albakal/Tik-tok/releases/latest`

> ⚠️ **ملاحظة أمنية**: لا تحفظ رمز GitHub (`ghp_...`) داخل ملفات المشروع، وإذا تمت مشاركة الرمز في المحادثة قم بإلغائه وتجديده من إعدادات GitHub فوراً.

