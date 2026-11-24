# -*- coding: utf-8 -*-
"""ميديا برو — القلب: إعدادات متوافقة + تخطيط القص + بناء أوامر FFmpeg (من غير أي لمس لمحرك القديم)."""
import json, math, os, re, subprocess

# ───────── الإعدادات الافتراضية (الوضع البسيط هو الأصل: كل خاصية مقفولة لحد ما تفتحها) ─────────
DEFAULT = {
    "url": "", "project": "", "preview": False, "saver": False, "shortest": True,
    "cut": "auto", "n": 5, "min": 15, "max": 25, "len": 20, "parts": 4, "overlap": 0,
    "ratio": "9:16", "res": "1080", "fill": "crop", "track": True, "stab": False,
    "clean": False, "norm": False, "nosil": False,
    "music": False, "mstyle": "calm", "murl": "", "mvol": 8, "duck": True,
    "look": "none", "speed": 1.0, "rev": False,
    "text": "", "text_on": False, "text_style": "plain",
    "cap": False, "cap_style": "plain", "cap_lang": "auto", "cap_tr": "ar", "desub": True,
    "wm": False, "wm_text": "", "wm_style": "plain",
    "intro": False, "intro_text": "", "intro_style": "plain", "intro_sec": 2,
    "outro": False, "outro_text": "", "outro_style": "plain", "outro_sec": 2,
}
CUTS = ("auto", "len", "scene", "sentence", "silence", "best", "series", "equal")
RATIOS = ("9:16", "4:5", "1:1", "16:9")
FILLS = ("auto", "blur", "crop", "bars")
LOOKS = {
    "none": None,
    "warm": "eq=saturation=1.12:gamma_r=1.05:gamma_b=0.95",
    "cinema": "eq=contrast=1.12:saturation=0.92:gamma=0.96,vignette=PI/6",
    "bright": "eq=brightness=0.04:contrast=1.06:saturation=1.1",
    "bw": "hue=s=0,eq=contrast=1.1",
    "vivid": "eq=saturation=1.35:contrast=1.06",
}
MSTYLES = ("calm", "warm", "pulse", "minimal")
TEXT_STYLES = ("plain", "box", "neon", "gold")
CAP_STYLES = ("plain", "word", "box", "neon")
WM_STYLES = ("plain", "moving")
INTRO_STYLES = ("plain", "box", "gold")


def _i(v, d, lo, hi):
    try:
        x = int(float(v))
    except Exception:
        x = d
    return max(lo, min(hi, x))


def _f(v, d, lo, hi):
    try:
        x = float(v)
    except Exception:
        x = d
    return max(lo, min(hi, x))


def _b(v):
    if isinstance(v, str):
        return v.strip().lower() in ("1", "true", "yes", "on")
    return bool(v)


def _pick(v, allowed, d):
    v = str(v or "").strip().lower()
    return v if v in allowed else d


def normalize(raw):
    """بيرجّع (cfg, notes). كل التعارضات بتتصلّح بقواعد ثابتة وبتتقال بوضوح — مفيش ميزة بتلغي التانية بصمت."""
    notes = []
    r = raw if isinstance(raw, dict) else {}
    c = dict(DEFAULT)
    c["url"] = str(r.get("url", "") or "").strip()
    c["project"] = str(r.get("project", "") or "").strip()[:40]
    for k in ("preview", "saver", "shortest", "track", "stab", "clean", "norm", "nosil", "music", "duck", "rev",
              "text_on", "cap", "desub", "wm", "intro", "outro"):
        c[k] = _b(r.get(k, DEFAULT[k]))
    c["cut"] = _pick(r.get("cut"), CUTS, "auto")
    c["n"] = _i(r.get("n"), 5, 1, 30)
    c["min"] = _i(r.get("min"), 15, 3, 180)
    c["max"] = _i(r.get("max"), 25, 3, 180)
    c["len"] = _i(r.get("len"), 20, 3, 180)
    c["parts"] = _i(r.get("parts"), 4, 2, 30)
    c["overlap"] = _i(r.get("overlap"), 0, 0, 5)
    c["ratio"] = _pick(r.get("ratio"), RATIOS, "9:16")
    c["res"] = "720" if str(r.get("res", "1080")) == "720" else "1080"
    c["fill"] = _pick(r.get("fill"), FILLS, "crop")
    c["mstyle"] = _pick(r.get("mstyle"), MSTYLES, "calm")
    c["murl"] = str(r.get("murl", "") or "").strip()
    c["mvol"] = _i(r.get("mvol"), 8, 2, 40)
    c["look"] = _pick(r.get("look"), tuple(LOOKS), "none")
    c["speed"] = _f(r.get("speed"), 1.0, 0.5, 2.0)
    c["text"] = str(r.get("text", "") or "")[:140]
    c["text_style"] = _pick(r.get("text_style"), TEXT_STYLES, "plain")
    c["cap_style"] = _pick(r.get("cap_style"), CAP_STYLES, "plain")
    c["cap_lang"] = _pick(r.get("cap_lang"), ("auto", "ar", "en"), "auto")
    c["cap_tr"] = _pick(r.get("cap_tr"), ("none", "ar", "en"), "ar")
    c["wm_text"] = str(r.get("wm_text", "") or "")[:60]
    c["wm_style"] = _pick(r.get("wm_style"), WM_STYLES, "plain")
    for p in ("intro", "outro"):
        c[p + "_text"] = str(r.get(p + "_text", "") or "")[:80]
        c[p + "_style"] = _pick(r.get(p + "_style"), INTRO_STYLES, "plain")
        c[p + "_sec"] = _i(r.get(p + "_sec"), 2, 1, 5)

    if c["max"] < c["min"]:
        c["min"], c["max"] = c["max"], c["min"]
        notes.append("أقل مدة كانت أكبر من أقصى مدة، فبدّلتهم.")
    # الميزات اللي محتاجة نص بتتقفل لوحدها لو النص فاضي (بدل ما تطلع فاضية)
    if c["wm"] and not c["wm_text"].strip():
        c["wm"] = False
        notes.append("العلامة المائية اتقفلت: مفيش نص مكتوب لها.")
    for p in ("intro", "outro"):
        if c[p] and not c[p + "_text"].strip():
            c[p] = False
            notes.append(("المقدمة" if p == "intro" else "الخاتمة") + " اتقفلت: مفيش نص مكتوب لها.")
    if c["music"] and c["murl"] and not re.match(r"^https?://", c["murl"]):
        c["murl"] = ""
        notes.append("رابط الموسيقى مش صالح، هستخدم موسيقى المكتبة.")
    if c["cap_tr"] != "none" and not c["cap"]:
        c["cap_tr"] = "none"
    # الأسلوب والتعارضات
    if c["cut"] == "equal":
        c["n"] = c["parts"]
    if c["cut"] == "series" and c["overlap"] and c["overlap"] >= c["len"]:
        c["overlap"] = 0
    if c["track"] and c["ratio"] == "16:9":
        c["track"] = False
        if "track" in r:
            notes.append("التتبّع اتقفل: الشكل الأفقي مفيهوش قص جانبي.")
    if c["max"] > 90:
        notes.append("أطول ريل أكتر من 90 ثانية: بعض المنصات (زي ريلز إنستجرام القديمة) ممكن ما تقبلوش.")
    if c["track"] and c["fill"] in ("blur", "bars"):
        c["fill"] = "crop"
        notes.append("التتبّع محتاج قص يملا الشاشة، فخلّيت الملء «قص».")
    if c["preview"]:
        c["n"] = 1
        c["saver"] = True
        c["res"] = "720"
        if c["stab"]:
            c["stab"] = False
            notes.append("المعاينة بتتخطى تثبيت الصورة عشان تخلص بسرعة.")
        if c["cut"] == "sentence" or c["cap"]:
            pass
    if c["saver"]:
        c["res"] = "720"
    return c, notes


# ───────── الأبعاد ─────────
def dims(ratio, res, preview=False):
    base = 480 if preview else (1080 if str(res) == "1080" else 720)
    if ratio == "16:9":
        h = base
        w = h * 16 // 9
    else:
        w = base
        h = {"9:16": w * 16 // 9, "4:5": w * 5 // 4, "1:1": w}[ratio]
    return w - (w % 2), h - (h % 2)


# ───────── تخطيط القص ─────────
def fit_cuts(points, dur, n, min_l, max_l):
    """نقط القص (مشاهد/جمل/سكتات) → نوافذ [بداية, نهاية] بين min و max. أطول نافذة ممكنة تنتهي عند نقطة قص؛ ولو مفيش نقطة نقص عند max."""
    pts = sorted(p for p in points if 0 < p < dur)
    wins, start = [], 0.0
    while len(wins) < n and dur - start >= min_l:
        lo, hi = start + min_l, min(start + max_l, dur)
        cand = [p for p in pts if lo <= p <= hi]
        end = max(cand) if cand else hi
        if end - start < min_l - 1e-6:
            break
        wins.append((round(start, 2), round(end, 2)))
        start = end
    return wins


def auto_params(dur, cfg):
    """التلقائي: عدد الريلز المطلوب يتوزّع على الفيديو كله؛ طول الريل = المدة ÷ العدد بين أقل وأقصى مدة.
    لو الفيديو أقصر من أقل مدة: ريل واحد بطول الفيديو كله."""
    mn, mx, n_req = cfg["min"], cfg["max"], cfg["n"]
    if dur < mn:
        return 1, max(1.0, float(dur))
    L = max(mn, min(mx, dur / max(1, n_req)))
    L = math.floor(L * 10) / 10
    n = max(1, min(n_req, int(dur // L)))
    return n, L


def uniform_windows(dur, n, L):
    return [(max(0.0, min(dur - L, (i + 0.5) * dur / n - L / 2)), L) for i in range(n)]


def no_overlap(ws, dur):
    """كل ريل من جزء مختلف: مفيش مقطع بيتكرر ولا بيتداخل مع اللي قبله."""
    out, end = [], 0.0
    for st, ln in sorted(ws):
        st = max(st, end)
        ln = min(ln, dur - st)
        if ln < 3 and out:
            continue
        out.append((round(st, 2), round(max(ln, 0.5), 2)))
        end = st + ln
    return out


def plan_windows(style, dur, cfg, points=None, best=None):
    """بيرجّع قايمة (بداية, نهاية, ملصق). `points` نقط القص لـ scene/sentence/silence، و`best` نوافذ جاهزة لأفضل اللحظات."""
    n, mn, mx, L = cfg["n"], cfg["min"], cfg["max"], cfg["len"]
    ws = []
    if style == "len":
        t = 0.0
        while len(ws) < n and dur - t >= min(L, max(3.0, mn)) - 1e-6:
            e = min(t + L, dur)
            ws.append((round(t, 2), round(e, 2), ""))
            t = e
    elif style in ("scene", "sentence", "silence"):
        for a, b in fit_cuts(points or [], dur, n, mn, mx):
            ws.append((a, b, ""))
    elif style == "auto":
        n_, L_ = auto_params(dur, cfg)
        base = [(a, a + b) for a, b in no_overlap([(a, b - a) for a, b in best], dur)] if best else \
               [(a, a + b) for a, b in no_overlap(uniform_windows(dur, n_, L_), dur)]
        ws = [(round(a, 2), round(b, 2), "") for a, b in base]
    elif style == "best":
        for a, b in (best or []):
            ws.append((round(a, 2), round(b, 2), ""))
    elif style == "series":
        step = max(1.0, L - cfg["overlap"])
        t = 0.0
        while len(ws) < n and t < dur - 2.5:
            e = min(t + L, dur)
            ws.append((round(t, 2), round(e, 2), ""))
            t += step
        total = len(ws)
        ws = [(a, b, f"{i + 1}/{total}") for i, (a, b, _) in enumerate(ws)]
    elif style == "equal":
        k = max(2, cfg["parts"])
        seg = dur / k
        ws = [(round(i * seg, 2), round(min(dur, (i + 1) * seg), 2), "") for i in range(k)]
    return ws[:30]


# ───────── ASS: نصوص عربية بتشكيل صحيح (libass) ─────────
ASS_HEAD = """[Script Info]
ScriptType: v4.00+
PlayResX: {w}
PlayResY: {h}
WrapStyle: 0
ScaledBorderAndShadow: yes

[V4+ Styles]
Format: Name,Fontname,Fontsize,PrimaryColour,SecondaryColour,OutlineColour,BackColour,Bold,Italic,Underline,StrikeOut,ScaleX,ScaleY,Spacing,Angle,BorderStyle,Outline,Shadow,Alignment,MarginL,MarginR,MarginV,Encoding
{styles}

[Events]
Format: Layer,Start,End,Style,Name,MarginL,MarginR,MarginV,Effect,Text
"""
FONT = "DejaVu Sans"   # بيتبدّل وقت التشغيل بأجمل خط عربي متاح (Tajawal/Almarai)

# الألوان في ASS بصيغة &HAABBGGRR
_ST = {
    "plain": dict(fg="&H00FFFFFF", sec="&H0000D7FF", out="&H00101010", back="&H80000000", bs=1, ol=4, sh=2),
    "box": dict(fg="&H00FFFFFF", sec="&H0000D7FF", out="&HB4000000", back="&HB4000000", bs=3, ol=10, sh=0),
    "neon": dict(fg="&H00D4EA5E", sec="&H0000D7FF", out="&H004A2F08", back="&H00000000", bs=1, ol=4, sh=0),
    "gold": dict(fg="&H00151C1C", sec="&H00000000", out="&H0A15CAFA", back="&H0A15CAFA", bs=3, ol=12, sh=0),
}


def ass_time(t):
    t = max(0.0, t)
    h = int(t // 3600)
    m = int((t % 3600) // 60)
    s = t % 60
    return f"{h}:{m:02d}:{s:05.2f}"


def ass_escape(s):
    return str(s).replace("\\", "").replace("{", "(").replace("}", ")").replace("\n", "\\N")


def style_line(name, style, w, size, align=2, marginv=0, font=None):
    font = font or FONT
    s = _ST.get(style, _ST["plain"])
    return (f"Style: {name},{font},{size},{s['fg']},{s['sec']},{s['out']},{s['back']},-1,0,0,0,100,100,0,0,"
            f"{s['bs']},{s['ol']},{s['sh']},{align},{int(w * 0.06)},{int(w * 0.06)},{marginv},1")


def build_ass(w, h, events, styles):
    """events: قايمة (start, end, style, text_with_tags, marginv). styles: قايمة سطور Style."""
    out = [ASS_HEAD.format(w=w, h=h, styles="\n".join(styles))]
    for st, en, name, text, mv in events:
        out.append(f"Dialogue: 0,{ass_time(st)},{ass_time(en)},{name},,0,0,{int(mv)},,{text}")
    return "\n".join(out) + "\n"


def overlay_events(cfg, w, h, dur, label=""):
    """نص الريل + العلامة المائية + ملصق السلسلة. بيرجّع (styles, events)."""
    styles, ev = [], []
    size = int(w * 0.058)
    main_txt = cfg["text"].strip() if cfg["text_on"] else ""
    if main_txt or label:
        pieces = []
        if main_txt:
            pieces.append(ass_escape(main_txt))
        if label:
            pieces.append(ass_escape("الجزء " + label))
        styles.append(style_line("Txt", cfg["text_style"], w, size, align=8, marginv=int(h * 0.11)))
        ev.append((0, dur, "Txt", "\\N".join(pieces), int(h * 0.11)))
    if cfg["wm"] and cfg["wm_text"].strip():
        wsz = int(w * 0.035)
        styles.append(style_line("Wm", "plain", w, wsz, align=7, marginv=0))
        t = ass_escape(cfg["wm_text"])
        tag = "{\\alpha&H60&\\bord2\\shad0}"
        if cfg["wm_style"] == "moving":
            pos = [(0.08, 0.1), (0.62, 0.1), (0.62, 0.82), (0.08, 0.82)]
            seg, i, tt = 4.0, 0, 0.0
            while tt < dur:
                a = pos[i % 4]
                b = pos[(i + 1) % 4]
                mv = "{\\an7\\alpha&H60&\\bord2\\shad0\\move(%d,%d,%d,%d,0,%d)}" % (
                    int(a[0] * w), int(a[1] * h), int(b[0] * w), int(b[1] * h), int(seg * 1000))
                ev.append((tt, min(dur, tt + seg), "Wm", mv + t, 0))
                tt += seg
                i += 1
        else:
            ev.append((0, dur, "Wm", "{\\an9}" + tag + t, int(h * 0.04)))
    return styles, ev


CAP_MV = 0.24   # الهامش السفلي كنسبة من الارتفاع (فوق أزرار المنصات)


def chunk_lines(text, max_chars=26):
    """بيقسّم النص لسطور قصيرة تناسب عرض الشاشة (من غير ما يقطع كلمة)."""
    lines, cur = [], []
    for wd in str(text).split():
        if cur and len(" ".join(cur + [wd])) > max_chars:
            lines.append(" ".join(cur))
            cur = [wd]
        else:
            cur.append(wd)
    if cur:
        lines.append(" ".join(cur))
    return lines


def segment_events(segs, cfg, w, h, dur, max_chars=26):
    """جمل مترجمة [(بداية, نهاية, نص)] → أحداث ASS: كل شاشة سطرين بحد أقصى، والوقت موزّع على قد طول النص."""
    styles = [style_line("Cap", "plain" if cfg["cap_style"] == "word" else cfg["cap_style"], w, int(w * 0.064), align=2, marginv=int(h * CAP_MV))]
    ev = []
    mv = int(h * CAP_MV)
    for a, b, t in segs:
        lines = chunk_lines(t, max_chars)
        if not lines:
            continue
        groups = ["\\N".join(ass_escape(x) for x in lines[i:i + 2]) for i in range(0, len(lines), 2)]
        raw = ["".join(lines[i:i + 2]) for i in range(0, len(lines), 2)]
        total = max(1, sum(len(x) for x in raw))
        span = max(0.6, min(b, dur) - a)
        t0 = a
        for g, rw in zip(groups, raw):
            d = span * len(rw) / total
            ev.append((t0, min(dur, t0 + max(0.7, d)), "Cap", g, mv))
            t0 += d
    return styles, ev


def caption_events(words, cfg, w, h, dur):
    """words: [(بداية, نهاية, كلمة)] → أحداث ASS. plain = جمل ثابتة، word = كلمة بكلمة مع تلوين الكلمة الحالية."""
    styles, ev = [], []
    style = cfg["cap_style"]
    size = int(w * (0.07 if style == "word" else 0.064))
    base = "plain" if style in ("plain", "word") else style
    styles.append(style_line("Cap", base, w, size, align=2, marginv=int(h * CAP_MV)))
    if not words:
        return styles, ev
    if style == "word":
        # مجموعات من 3 كلمات؛ الكلمة الحالية بتتلوّن
        grp = [words[i:i + 3] for i in range(0, len(words), 3)]
        for g in grp:
            for k, (a, b, wd) in enumerate(g):
                end = g[k + 1][0] if k + 1 < len(g) else min(dur, b + 0.2)
                parts = []
                for j, (_, _, ww) in enumerate(g):
                    t = ass_escape(ww)
                    parts.append(("{\\c&H0000D7FF&}" + t + "{\\c&HFFFFFF&}") if j == k else t)
                ev.append((a, max(end, a + 0.1), "Cap", " ".join(parts), int(h * CAP_MV)))
    else:
        line, ls = [], None
        for a, b, wd in words:
            if ls is None:
                ls = a
            line.append(ass_escape(wd))
            if len(line) >= 6 or wd.rstrip().endswith((".", "!", "?", "؟", "،", ",")):
                ev.append((ls, min(dur, b + 0.15), "Cap", " ".join(line), int(h * CAP_MV)))
                line, ls = [], None
        if line:
            ev.append((ls, min(dur, words[-1][1] + 0.3), "Cap", " ".join(line), int(h * CAP_MV)))
    return styles, ev


def card_ass(w, h, text, style, sec):
    size = int(w * 0.08)
    styles = [style_line("Card", style, w, size, align=5, marginv=0)]
    t = "{\\fad(250,250)}" + ass_escape(text)
    return build_ass(w, h, [(0, sec, "Card", t, 0)], styles)


# ───────── ffmpeg ─────────
def ff(cmd, timeout=900):
    p = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, errors="replace", timeout=timeout)
    return p.returncode, (p.stderr or "")[-600:], (p.stdout or "")


def esc_path(p):
    """مسار جوه فلتر ffmpeg (subtitles/ass/fontsdir)."""
    return p.replace("\\", "/").replace(":", "\\:").replace("'", "\\'").replace(",", "\\,")


def video_chain(cfg, src_w, src_h, W, H, track_expr=None, stab_file=None, speed=1.0, keep=None):
    """سلسلة فلاتر الصورة: تثبيت → ملء الشاشة (+تتبّع) → مرآة → ألوان → سرعة. بترجّع نص filter_complex بين [0:v] و [vout]."""
    parts = []
    cur = "[0:v]"
    if stab_file:
        parts.append(f"{cur}vidstabtransform=input='{esc_path(stab_file)}':smoothing=15,unsharp=5:5:0.8:3:3:0.4[s]")
        cur = "[s]"
    if keep and 0.5 < keep < 0.99:
        # قص الجزء السفلي اللي فيه ترجمة مطبوعة (الصورة بتتكبّر شوية بدل ما نسيب الترجمة القديمة)
        parts.append(f"{cur}crop=iw:trunc(ih*{keep:.3f}/2)*2:0:0[kc]")
        cur = "[kc]"
        src_h = (src_h or H) * keep
    fill = cfg["fill"]
    sa = (src_w or W) / (src_h or H)
    ta = W / H
    if fill == "auto":
        fill = "crop" if 0.8 <= sa / ta <= 1.25 else "blur"
    if fill == "crop":
        if track_expr and sa >= ta:
            parts.append(f"{cur}scale=-2:{H},crop={W}:{H}:x='{track_expr.replace('ow', str(W))}':y=0,setsar=1[f]")
        else:
            parts.append(f"{cur}scale={W}:{H}:force_original_aspect_ratio=increase,crop={W}:{H},setsar=1[f]")
    elif fill == "bars":
        parts.append(f"{cur}scale={W}:{H}:force_original_aspect_ratio=decrease,pad={W}:{H}:(ow-iw)/2:(oh-ih)/2:color=black,setsar=1[f]")
    else:
        parts.append(f"{cur}split[a][b];[a]scale={W}:{H}:force_original_aspect_ratio=increase,crop={W}:{H},boxblur=24:4[bg];"
                     f"[b]scale={W}:{H}:force_original_aspect_ratio=decrease[fg];[bg][fg]overlay=(W-w)/2:(H-h)/2,setsar=1[f]")
    cur = "[f]"
    post = []
    if cfg["rev"]:
        post.append("hflip")
    lk = LOOKS.get(cfg["look"])
    if lk:
        post.append(lk)
    if abs(speed - 1.0) > 0.01:
        post.append(f"setpts=PTS/{speed:.3f}")
    post.append("fps=30,format=yuv420p")
    parts.append(f"{cur}{','.join(post)}[vout]")
    return ";".join(parts)


def atempo_chain(speed):
    if abs(speed - 1.0) < 0.01:
        return ""
    f, parts = speed, []
    while f > 2.0:
        parts.append("atempo=2.0")
        f /= 2.0
    while f < 0.5:
        parts.append("atempo=0.5")
        f /= 0.5
    parts.append(f"atempo={f:.3f}")
    return ",".join(parts)


def audio_chain(cfg, speed=1.0):
    ch = []
    if cfg["clean"]:
        ch.append("highpass=f=80,afftdn=nf=-25")
    at = atempo_chain(speed)
    if at:
        ch.append(at)
    ch.append("aresample=48000,aformat=sample_fmts=fltp:channel_layouts=stereo")
    return ",".join(ch)


def keep_intervals(silences, dur, pad=0.12, min_keep=0.5):
    """من فترات الصمت [(بداية,نهاية)] لفترات الاحتفاظ. بيرجّع None لو مفيش حاجة تتشال تستاهل."""
    cut = sorted((max(0.0, a + pad), min(dur, b - pad)) for a, b in silences if b - a > 2 * pad + 0.05)
    keep, pos = [], 0.0
    for a, b in cut:
        if a - pos >= min_keep:
            keep.append((pos, a))
        pos = max(pos, b)
    if dur - pos >= min_keep:
        keep.append((pos, dur))
    if not keep:
        return None
    removed = dur - sum(b - a for a, b in keep)
    if removed < 0.6 or len(keep) > 60:
        return None
    return [(round(a, 3), round(b, 3)) for a, b in keep]


def select_exprs(keep):
    e = "+".join(f"between(t,{a},{b})" for a, b in keep)
    return e


def parse_silences(stderr_text):
    starts, out = [], []
    for ln in stderr_text.splitlines():
        m = re.search(r"silence_start:\s*(-?[0-9.]+)", ln)
        if m:
            starts.append(max(0.0, float(m.group(1))))
            continue
        m = re.search(r"silence_end:\s*([0-9.]+)", ln)
        if m and starts:
            out.append((starts.pop(0), float(m.group(1))))
    return out


def parse_scene_times(text):
    return [float(x) for x in re.findall(r"pts_time:([0-9.]+)", text)]


def music_cmd(style, out, secs=60):
    """مكتبة موسيقى مولّدة (تركيب بالـ FFmpeg — من غير حقوق): calm / warm / pulse / minimal."""
    sets = {
        "calm": ([110.0, 220.0, 261.63, 329.63, 392.0], "lowpass=f=1300,tremolo=f=0.1:d=0.3"),
        "warm": ([130.81, 196.0, 261.63, 329.63, 523.25], "lowpass=f=1800,tremolo=f=0.18:d=0.25"),
        "pulse": ([98.0, 196.0, 293.66, 392.0], "lowpass=f=1500,tremolo=f=2.0:d=0.55"),
        "minimal": ([220.0, 329.63], "lowpass=f=1000,tremolo=f=0.08:d=0.2"),
    }
    freqs, fx_ = sets.get(style, sets["calm"])
    cmd = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y"]
    for f in freqs:
        cmd += ["-f", "lavfi", "-i", f"sine=frequency={f}:sample_rate=44100:duration={secs}"]
    ins = "".join(f"[{i}:a]" for i in range(len(freqs)))
    g = (f"{ins}amix=inputs={len(freqs)}:normalize=0,volume=0.6,{fx_},aformat=channel_layouts=stereo,"
         f"aecho=0.8:0.6:600|1000:0.3|0.2,loudnorm=I=-16:TP=-2:LRA=7,aresample=44100,"
         f"afade=t=in:d=2,afade=t=out:st={secs - 3}:d=3")
    return cmd + ["-filter_complex", g, "-ac", "2", out]
