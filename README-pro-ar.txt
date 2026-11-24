✨ ميديا برو — قسم جديد جوه نفس التطبيق (القديم ماتلمسش)

التحديث (أمر واحد في Termux بعد ما تفك الزيب الجديد فوق القديم):
  unzip -oq ~/storage/downloads/reels-maker-pro.zip -d ~ && bash ~/reels-maker-main/termux-update.sh

اللي اتضاف:
• السيرفر: .github/workflows/pro.yml + pro/pro_engine.py + pro/pro_core.py (مستقلين عن reels.yml)
• التطبيق: ProActivity (الرئيسية) • ProCreateActivity (إنشاء) • ProService (متابعة وتنزيل وإعادة محاولة)
  ProAccountsActivity + ProWebActivity (المنصات والحسابات) • ProShareActivity (النشر والجدولة)
  ProLibActivity (السجل والإحصائيات) • ProToolsActivity (الفحص والنسخة الاحتياطية) • ProUpdate (زر التحديث)
• تعديلات صغيرة على القديم: زر «ميديا برو» في MainActivity، تصاريح/مكوّنات في AndroidManifest،
  رقم النسخة + RUN_ID في build.gradle، وكتابة رقم البناء في ملاحظات apk.yml (عشان زر التحديث).

ملاحظات:
• الترجمة التلقائية بتشتغل بـ faster-whisper (النموذج small) والترجمة لغة تانية بـ deep-translator؛
  لو أي حاجة منهم ماتثبّتتش الريل بيطلع من غيرها وبيتسجّل تنبيه.
• تتبّع الوش بيحتاج OpenCV على السيرفر؛ لو مش متاح بيقص من النص.
• موسيقى المكتبة مولّدة بـ FFmpeg (من غير حقوق). ممكن تحط رابط موسيقى بدلها.
• الدخول للمنصات بموقعها الرسمي جوه التطبيق: التطبيق مابيحفظش كلمات سر، بيحفظ جلسة الدخول على موبايلك بس.

أجهزة ضعيفة / أندرويد 6:
• الحد الأدنى للتطبيق أندرويد 6 (API 23)، والمعالجة كلها على السيرفر، والفيديو بيتصدّر بـ H.264 Main عشان يشتغل على أجهزة قديمة.
• تسجيل الدخول للمنصات بيعتمد على WebView الجهاز: حدّث «Android System WebView» من Google Play لو متاح، ولو منصة ماتفتحتش استخدم زر 🌐 (فتح بالمتصفح).
