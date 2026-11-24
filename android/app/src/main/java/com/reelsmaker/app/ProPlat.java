package com.reelsmaker.app;

/** منصات النشر (تسجيل دخول رسمي من موقع المنصة جوه التطبيق + شير بضغطة). */
final class ProPlat {
    final String id, icon, name, desc, upload;
    final String[] cookieUrls;
    final int capMax, soft;

    private ProPlat(String id, String icon, String name, String desc, String upload, int capMax, int soft, String... cookieUrls) {
        this.id = id;
        this.icon = icon;
        this.name = name;
        this.desc = desc;
        this.upload = upload;
        this.capMax = capMax;
        this.soft = soft;
        this.cookieUrls = cookieUrls;
    }

    static final ProPlat[] ALL = {
            new ProPlat("tiktok", "🎵", "تيك توك", "ريلز قصيرة — صفحة الرفع من موقع تيك توك", "https://www.tiktok.com/tiktokstudio/upload", 2200, 10,
                    "https://www.tiktok.com", "https://www.tiktokv.com"),
            new ProPlat("instagram", "📷", "إنستجرام", "ريلز إنستجرام — ادخل بحسابك وانشر من الموقع", "https://www.instagram.com/", 2200, 8,
                    "https://www.instagram.com", "https://i.instagram.com"),
            new ProPlat("facebook", "📘", "فيسبوك", "ريلز فيسبوك لحسابك أو صفحتك", "https://www.facebook.com/reels/create", 2200, 10,
                    "https://www.facebook.com", "https://m.facebook.com"),
            new ProPlat("x", "X", "إكس", "منشور بفيديو (وصف لحد 280 حرف)", "https://x.com/compose/post", 280, 15,
                    "https://x.com", "https://twitter.com"),
            new ProPlat("youtube", "▶️", "يوتيوب شورتس", "استوديو يوتيوب — ارفع الفيديو كـ Short", "https://studio.youtube.com/", 100, 6,
                    "https://www.youtube.com", "https://studio.youtube.com", "https://accounts.google.com"),
            new ProPlat("snapchat", "👻", "سناب شات", "سبوتلايت — صفحة سناب على الويب", "https://www.snapchat.com/", 250, 5,
                    "https://www.snapchat.com", "https://accounts.snapchat.com"),
            new ProPlat("telegram", "✈️", "تيليجرام", "قناة أو جروب — تيليجرام ويب", "https://web.telegram.org/k/", 1024, 30,
                    "https://web.telegram.org"),
            new ProPlat("threads", "🌀", "ثريدز", "منشور بفيديو على ثريدز", "https://www.threads.net/", 500, 10,
                    "https://www.threads.net", "https://www.instagram.com"),
            new ProPlat("pinterest", "📌", "بنترست", "بن فيديو جديد", "https://www.pinterest.com/pin-creation-tool/", 500, 10,
                    "https://www.pinterest.com"),
            new ProPlat("linkedin", "💼", "لينكدإن", "منشور فيديو مهني", "https://www.linkedin.com/feed/", 3000, 3,
                    "https://www.linkedin.com"),
    };

    static ProPlat by(String id) {
        for (ProPlat p : ALL) if (p.id.equals(id)) return p;
        return ALL[0];
    }
}
