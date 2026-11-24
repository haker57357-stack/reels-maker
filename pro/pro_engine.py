#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""ميديا برو — معالجة على سيرفر GitHub. مستقل عن محرك الريلز القديم (بيستعير منه التحميل واختيار أفضل اللحظات بس).
كل ميزة اختيارية معزولة: لو فشلت، الريل بيطلع من غيرها وبيتسجّل تنبيه (مابتوقّفش الباقي)."""
import json, os, re, shutil, subprocess, sys, time, zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)
sys.path.insert(0, ROOT)
import pro_core as C  # noqa: E402

try:
    import engine as E  # التحميل (yt-dlp + بدائل) واختيار أفضل اللحظات
except Exception as _e:  # pragma: no cover
    E = None
    print("تحذير: محرك التحميل مش متاح:", _e, flush=True)

WORK = os.path.join(ROOT, "pro_work")
OUT = os.path.join(ROOT, "pro_out")
FONTS = os.path.join(ROOT, "fonts")
T0 = time.time()


class UserError(Exception):
    pass


def say(m):
    print(m, flush=True)


# ───────── التقرير للتطبيق عن طريق ملاحظات الإصدار (الـ pct والرسالة والملفات) ─────────
class Reporter:
    def __init__(self, job, local):
        self.job, self.local = job, local
        self.tag = "pro-" + job
        self.state = {"v": 1, "stage": "run", "pct": 0, "msg": "بدأنا", "files": [], "warn": [], "mins": 0}
        self.last = 0.0
        self.created = False

    def _gh(self, args):
        if self.local or not os.environ.get("GH_TOKEN"):
            return True
        for _ in range(3):
            r = subprocess.run(["gh"] + args, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
            if r.returncode == 0:
                return True
            time.sleep(2)
        say("gh فشل: " + " ".join(args[:3]) + " " + (r.stderr or "")[-160:])
        return False

    def _notes(self):
        self.state["mins"] = round((time.time() - T0) / 60.0, 1)
        return json.dumps(self.state, ensure_ascii=False)

    def push(self, force=False):
        if self.local:
            with open(os.path.join(OUT, "status.json"), "w", encoding="utf-8") as f:
                f.write(self._notes())
            return
        if not force and time.time() - self.last < 8:
            return
        self.last = time.time()
        if not self.created:
            self.created = self._gh(["release", "create", self.tag, "--title", "pro", "--notes", self._notes()])
        else:
            self._gh(["release", "edit", self.tag, "--notes", self._notes()])

    def update(self, pct, msg, force=False):
        self.state["pct"] = int(max(0, min(99, pct)))
        self.state["msg"] = msg
        say(f"[{self.state['pct']}%] {msg}")
        self.push(force)

    def warn(self, m):
        say("⚠️ " + m)
        if m not in self.state["warn"]:
            self.state["warn"].append(m)

    def add_file(self, path):
        name = os.path.basename(path)
        if not self.local and self.created is False:
            self.push(True)
        if self._gh(["release", "upload", self.tag, path, "--clobber"]):
            self.state["files"].append(name)
        self.push(True)

    def finish(self, ok, msg):
        self.state["stage"] = "done" if ok else "fail"
        self.state["pct"] = 100 if ok else self.state["pct"]
        self.state["msg"] = msg
        self.push(True)


# ───────── أدوات ─────────
def probe(path):
    if E:
        return E.probe(path)
    raise UserError("محرك التحميل مش متاح")


def ffrun(cmd, timeout=1500):
    p = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, errors="replace", timeout=timeout)
    return p


def must(cmd, what, timeout=1500):
    p = ffrun(cmd, timeout)
    if p.returncode != 0:
        raise RuntimeError(f"{what}: {(p.stderr or '')[-260:]}")
    return p


def enc_args(cfg):
    if cfg["preview"]:
        return ["-c:v", "libx264", "-preset", "ultrafast", "-crf", "31", "-profile:v", "main", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "64k", "-movflags", "+faststart"]
    if cfg["saver"]:
        return ["-c:v", "libx264", "-preset", "veryfast", "-crf", "27", "-profile:v", "main", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "96k", "-movflags", "+faststart"]
    return ["-c:v", "libx264", "-preset", "veryfast", "-crf", "20", "-profile:v", "main", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "160k", "-movflags", "+faststart"]


def mid_enc():
    return ["-c:v", "libx264", "-preset", "ultrafast", "-crf", "17", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "192k"]


# ───────── تحليل ─────────
def scene_points(src, dur):
    p = ffrun(["ffmpeg", "-hide_banner", "-nostats", "-i", src, "-an", "-vf",
               "fps=4,scale=320:-2,select='gt(scene,0.32)',metadata=print:file=-", "-f", "null", "-"], timeout=900)
    return C.parse_scene_times(p.stdout or "")


def silence_points(src, dur):
    p = ffrun(["ffmpeg", "-hide_banner", "-nostats", "-i", src, "-vn", "-af", "silencedetect=noise=-32dB:d=0.45", "-f", "null", "-"], timeout=900)
    sil = C.parse_silences(p.stderr or "")
    return [(a + b) / 2 for a, b in sil]


_WHISPER = {"m": None, "bad": False}


def transcribe(path, lang):
    """بيرجّع (words, segments, lang) أو None. words=[(بداية,نهاية,كلمة)] • segments=[(بداية,نهاية,نص)]."""
    if _WHISPER["bad"]:
        return None
    try:
        if _WHISPER["m"] is None:
            from faster_whisper import WhisperModel
            _WHISPER["m"] = WhisperModel("small", device="cpu", compute_type="int8")
        segs, info = _WHISPER["m"].transcribe(path, language=None if lang == "auto" else lang, word_timestamps=True,
                                             vad_filter=True, beam_size=1)
        words, sg = [], []
        for s in segs:
            sg.append((float(s.start), float(s.end), s.text.strip()))
            for w in (s.words or []):
                t = (w.word or "").strip()
                if t:
                    words.append((float(w.start), float(w.end), t))
        return words, sg, getattr(info, "language", "") or ""
    except Exception as e:
        _WHISPER["bad"] = True
        say("التفريغ الصوتي مش متاح: " + str(e)[:200])
        return None


def translate_segments(sg, target):
    try:
        from deep_translator import GoogleTranslator
        tr = GoogleTranslator(source="auto", target=target)
        return [(a, b, tr.translate(t) or t) for a, b, t in sg]
    except Exception as e:
        say("الترجمة مش متاحة: " + str(e)[:200])
        return None


# ───────── الموسيقى ─────────
_MUSIC = {}


def music_file(cfg, rep):
    key = (cfg["mstyle"], cfg["murl"])
    if key in _MUSIC:
        return _MUSIC[key]
    out = os.path.join(WORK, "music.m4a")
    path = None
    if cfg["murl"]:
        try:
            if E:
                path = E.fetch_audio_direct(cfg["murl"], WORK)
            if path:
                conv = os.path.join(WORK, "music_conv.m4a")
                if ffrun(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", path, "-vn", "-ac", "2", "-ar", "44100", conv]).returncode == 0:
                    path = conv
                else:
                    path = None
        except Exception:
            path = None
        if not path:
            rep.warn("مقدرتش أحمّل رابط الموسيقى، استخدمت موسيقى المكتبة.")
    if not path:
        p = ffrun(C.music_cmd(cfg["mstyle"], out, 60))
        path = out if p.returncode == 0 and os.path.exists(out) else None
    _MUSIC[key] = path
    return path


# ───────── ريل واحد ─────────
def render_reel(src, info, win, idx, cfg, W, H, rep, base_pct, span):
    st, en, label = win
    ln = max(1.0, en - st)
    speed = cfg["speed"]
    tmp = os.path.join(WORK, f"r{idx:02d}")
    os.makedirs(tmp, exist_ok=True)
    sw, sh = info.get("w") or W, info.get("h") or H

    def step(msg, f):
        rep.update(base_pct + span * f, msg)

    # 1) تثبيت الصورة (اختياري)
    stab = None
    if cfg["stab"]:
        step(f"ريل {idx}: تثبيت الصورة", 0.05)
        try:
            trf = os.path.join(tmp, "stab.trf")
            must(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-ss", f"{st}", "-t", f"{ln}", "-i", src, "-an",
                  "-vf", f"vidstabdetect=shakiness=5:accuracy=9:result={C.esc_path(trf)}", "-f", "null", "-"], "stab-detect")
            if os.path.exists(trf):
                stab = trf
        except Exception as e:
            rep.warn("تثبيت الصورة فشل وتخطيته.")
            say(str(e))
    # 2) تتبّع الوش (اختياري)
    track_expr = None
    if cfg["track"]:
        step(f"ريل {idx}: تتبّع الوش", 0.12)
        try:
            if E and E.faces_available():
                tr = E.face_track(src, st, ln, info, W, H)
                track_expr = E.crop_x_expr(tr) if tr else None
            if not track_expr:
                rep.warn("التتبّع مالقاش وش واضح، اتقصّ من النص.")
        except Exception as e:
            rep.warn("التتبّع فشل، اتقصّ من النص.")
            say(str(e))
    # 3) المرحلة أ: قص + ملء + ألوان + سرعة + مرآة + صوت
    step(f"ريل {idx}: تجهيز الصورة والصوت", 0.2)
    a = os.path.join(tmp, "a.mp4")
    for attempt in (0, 1):
        vf = C.video_chain(cfg, sw, sh, W, H, track_expr if attempt == 0 else None, stab if attempt == 0 else None, speed)
        cmd = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-ss", f"{st}", "-t", f"{ln}", "-i", src]
        if info.get("has_audio"):
            cmd += ["-filter_complex", vf, "-map", "[vout]", "-map", "0:a:0", "-af", C.audio_chain(cfg, speed)]
        else:
            cmd += ["-f", "lavfi", "-t", f"{ln}", "-i", "anullsrc=r=48000:cl=stereo", "-filter_complex", vf, "-map", "[vout]", "-map", "1:a:0",
                    "-af", C.audio_chain(cfg, speed)]
        cmd += mid_enc() + ["-shortest", a]
        p = ffrun(cmd)
        if p.returncode == 0:
            break
        if attempt == 0 and (stab or track_expr):
            rep.warn("التثبيت/التتبّع سبّبوا مشكلة، كملت من غيرهم.")
            continue
        raise RuntimeError("render-a: " + (p.stderr or "")[-260:])
    cur = a
    dur = (probe(cur) or {}).get("dur") or ln / speed

    # 4) حذف الصمت + توحيد الصوت (اختياري)
    if cfg["nosil"] or cfg["norm"]:
        step(f"ريل {idx}: تنظيف الصوت", 0.45)
        try:
            b = os.path.join(tmp, "b.mp4")
            keep = None
            if cfg["nosil"]:
                sp = ffrun(["ffmpeg", "-hide_banner", "-nostats", "-i", cur, "-vn", "-af", "silencedetect=noise=-35dB:d=0.7", "-f", "null", "-"])
                keep = C.keep_intervals(C.parse_silences(sp.stderr or ""), dur)
            if keep:
                ex = C.select_exprs(keep)
                af = f"aselect='{ex}',asetpts=N/SR/TB" + (",loudnorm=I=-16:TP=-1.5:LRA=11" if cfg["norm"] else "")
                must(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", cur, "-filter_complex",
                      f"[0:v]select='{ex}',setpts=N/FRAME_RATE/TB[v];[0:a]{af}[a]", "-map", "[v]", "-map", "[a]"] + mid_enc() + [b], "nosil")
            elif cfg["norm"]:
                must(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", cur, "-c:v", "copy", "-af", "loudnorm=I=-16:TP=-1.5:LRA=11,aresample=48000",
                      "-c:a", "aac", "-b:a", "192k", b], "norm")
            if os.path.exists(b):
                cur = b
                dur = (probe(cur) or {}).get("dur") or dur
        except Exception as e:
            rep.warn("تنظيف الصوت فشل وتخطيته.")
            say(str(e))

    # 5) نصوص + علامة مائية + ترجمة + موسيقى → مخرج بالجودة النهائية
    step(f"ريل {idx}: النصوص والموسيقى", 0.6)
    styles, events = C.overlay_events(cfg, W, H, dur, label)
    if cfg["cap"]:
        tr = transcribe(cur, cfg["cap_lang"])
        if not tr:
            rep.warn("الترجمة التلقائية مش متاحة دلوقتي، الريل طلع من غيرها.")
        else:
            words, segs, lang = tr
            use_tr = cfg["cap_tr"] != "none" and bool(lang) and lang != cfg["cap_tr"]
            if use_tr:
                ts = translate_segments(segs, cfg["cap_tr"])
                if ts:
                    cs, _ = C.caption_events([], cfg, W, H, dur)
                    styles += cs
                    events += [(a_, b_, "Cap", C.ass_escape(t_), int(H * 0.16)) for a_, b_, t_ in ts if t_.strip()]
                else:
                    rep.warn("الترجمة مش متاحة دلوقتي، الكلام ظهر باللغة الأصلية.")
                    use_tr = False
            if not use_tr:
                if words:
                    cs, ce = C.caption_events(words, cfg, W, H, dur)
                    styles += cs
                    events += ce
                else:
                    rep.warn("مفيش كلام واضح في الريل ده للتفريغ.")
    music = music_file(cfg, rep) if cfg["music"] else None
    final = os.path.join(tmp, "final.mp4")
    cmd = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", cur]
    vfilter = None
    if events:
        # نضمن وجود كل الأنماط المستخدمة
        seen, uniq = set(), []
        for s_ in styles:
            nm = s_.split(":")[1].split(",")[0].strip()
            if nm not in seen:
                seen.add(nm)
                uniq.append(s_)
        assf = os.path.join(tmp, "o.ass")
        with open(assf, "w", encoding="utf-8") as f:
            f.write(C.build_ass(W, H, events, uniq))
        vfilter = f"[0:v]ass='{C.esc_path(assf)}':fontsdir='{C.esc_path(FONTS)}'[v]"
    fc = []
    vmap = "0:v"
    if vfilter:
        fc.append(vfilter)
        vmap = "[v]"
    amap = "0:a"
    if music:
        vol = cfg["mvol"] / 100.0
        cmd += ["-stream_loop", "-1", "-i", music]
        if cfg["duck"]:
            fc.append(f"[1:a]volume={vol:.3f}[m];[0:a]asplit=2[v1][v2];[m][v2]sidechaincompress=threshold=0.03:ratio=8:attack=20:release=400[md];"
                      f"[v1][md]amix=inputs=2:duration=first:dropout_transition=0:normalize=0[am]")
        else:
            fc.append(f"[1:a]volume={vol:.3f}[m];[0:a][m]amix=inputs=2:duration=first:dropout_transition=0:normalize=0[am]")
        amap = "[am]"
    if fc:
        cmd += ["-filter_complex", ";".join(fc)]
    cmd += ["-map", vmap, "-map", amap, "-t", f"{dur:.2f}"] + enc_args(cfg) + [final]
    p = ffrun(cmd)
    if p.returncode != 0:
        # نحاول من غير الموسيقى/النصوص بدل ما نفشل الريل كله
        rep.warn("النصوص أو الموسيقى سبّبوا مشكلة، الريل طلع من غيرهم.")
        say((p.stderr or "")[-300:])
        must(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", cur] + enc_args(cfg) + [final], "final")
    return final, (probe(final) or {}).get("dur") or dur


def make_card(text, style, sec, W, H, out, cfg):
    ass = os.path.join(WORK, "card.ass")
    with open(ass, "w", encoding="utf-8") as f:
        f.write(C.card_ass(W, H, text, style, float(sec)))
    bg = "0x1d2b33" if style != "gold" else "0x0f1b22"
    cmd = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-f", "lavfi", "-i", f"color=c={bg}:s={W}x{H}:d={sec}:r=30",
           "-f", "lavfi", "-i", f"anullsrc=r=48000:cl=stereo", "-t", str(sec),
           "-vf", f"ass='{C.esc_path(ass)}':fontsdir='{C.esc_path(FONTS)}',format=yuv420p,setsar=1",
           "-c:v", "libx264", "-preset", "ultrafast", "-crf", "20", "-c:a", "aac", "-b:a", "128k", "-shortest", out]
    must(cmd, "card")


def add_cards(final, cfg, W, H, tmp, rep):
    parts = []
    try:
        if cfg["intro"]:
            ci = os.path.join(tmp, "intro.mp4")
            make_card(cfg["intro_text"], cfg["intro_style"], cfg["intro_sec"], W, H, ci, cfg)
            parts.append(ci)
        parts.append(final)
        if cfg["outro"]:
            co = os.path.join(tmp, "outro.mp4")
            make_card(cfg["outro_text"], cfg["outro_style"], cfg["outro_sec"], W, H, co, cfg)
            parts.append(co)
        if len(parts) == 1:
            return final
        joined = os.path.join(tmp, "joined.mp4")
        cmd = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y"]
        fc = ""
        for i, p_ in enumerate(parts):
            cmd += ["-i", p_]
            fc += f"[{i}:v]fps=30,scale={W}:{H},setsar=1,format=yuv420p[v{i}];[{i}:a]aresample=48000,aformat=channel_layouts=stereo[a{i}];"
        fc += "".join(f"[v{i}][a{i}]" for i in range(len(parts))) + f"concat=n={len(parts)}:v=1:a=1[v][a]"
        must(cmd + ["-filter_complex", fc, "-map", "[v]", "-map", "[a]"] + enc_args(cfg) + [joined], "concat")
        return joined
    except Exception as e:
        rep.warn("المقدمة أو الخاتمة فشلوا، الريل طلع من غيرهم.")
        say(str(e))
        return final


# ───────── التشغيل الرئيسي ─────────
def get_windows(cfg, src, info, rep):
    dur = info["dur"]
    style = cfg["cut"]
    pts, best = None, None
    if style == "scene":
        pts = scene_points(src, dur)
        if len(pts) < 2:
            rep.warn("مالقيتش تغيير مشاهد كفاية، قصيت بالطول.")
            style = "len"
    elif style == "silence":
        pts = silence_points(src, dur)
        if len(pts) < 2:
            rep.warn("مالقيتش وقفات كفاية، قصيت بالطول.")
            style = "len"
    elif style == "sentence":
        tr = transcribe(src, cfg["cap_lang"]) if dur <= 1800 else None
        if tr and len(tr[1]) >= 2:
            pts = [e for _, e, _ in tr[1]]
        else:
            rep.warn("تفريغ الجمل مش متاح، قصيت عند وقفات الكلام.")
            pts = silence_points(src, dur)
            if len(pts) < 2:
                style = "len"
    elif style == "best":
        try:
            L = int((cfg["min"] + cfg["max"]) / 2)
            w = E.best_windows(src, dur, cfg["n"], L, cfg["min"], cfg["max"], info, False, "all") if E else None
            if not w and E:
                w = E.smart_windows(src, dur, cfg["n"], L, cfg["min"], cfg["max"])
            best = [(a, a + b) for a, b in (w or [])]
        except Exception as e:
            say("أفضل اللحظات: " + str(e)[:200])
            best = []
        if not best:
            rep.warn("تحليل أفضل اللحظات مش متاح، قصيت بالطول.")
            style = "len"
    if style == "len" and cfg["cut"] != "len":
        cfg = dict(cfg, len=int((cfg["min"] + cfg["max"]) / 2))
    return C.plan_windows(style, dur, cfg, pts, best)


def run(cfgraw, rep):
    cfg, notes = C.normalize(cfgraw)
    os.makedirs(OUT, exist_ok=True)
    shutil.rmtree(WORK, ignore_errors=True)
    os.makedirs(WORK)
    for n_ in notes:
        rep.warn(n_)
    rep.update(1, "بجهّز الطلب", True)
    urls = [u.strip() for u in re.split(r"[\r\n]+", cfg["url"]) if u.strip()]
    if not urls:
        raise UserError("مفيش رابط فيديو.")
    urls = urls[:8]
    cookies = os.path.join(WORK, "cookies.txt")
    ck = os.environ.get("YT_COOKIES", "")
    if ck.strip():
        with open(cookies, "w", encoding="utf-8") as f:
            f.write(ck)
    else:
        cookies = ""
    # الأقصر الأول: أول ريلز تخلص بسرعة
    if len(urls) > 1 and cfg["shortest"] and E:
        rep.update(3, "بحدد الأقصر الأول")
        durs = []
        for u in urls:
            try:
                d = E.ytdlp_duration(E.normalize_url(E.extract_url(u) or u), cookies, 40) or 1e9
            except Exception:
                d = 1e9
            durs.append(d)
        urls = [u for _, u in sorted(zip(durs, urls), key=lambda x: x[0])]
    W, H = C.dims(cfg["ratio"], cfg["res"], cfg["preview"])
    maxh = 480 if cfg["preview"] else (1080 if cfg["res"] == "1080" else 720)
    opts = {"referer": "", "cookie": "", "extra": "", "cookies_file": cookies, "maxh": maxh}
    made = 0
    total_urls = len(urls)
    for ui, u in enumerate(urls):
        lo = 5 + 90.0 * ui / total_urls
        span = 90.0 / total_urls
        rep.update(lo, f"تحميل الفيديو {ui + 1}/{total_urls}", True)
        try:
            src, info, _ = E.fetch_media(u, WORK, f"source{ui}", opts, E.PROG, need_video=True)
        except Exception as e:
            msg = str(getattr(e, "args", [""])[0] or e)[:300]
            if total_urls == 1:
                raise UserError("مقدرتش أحمّل الفيديو: " + msg)
            rep.warn(f"الفيديو {ui + 1} ماتحمّلش.")
            continue
        info = info or probe(src)
        if not info or not info.get("dur"):
            rep.warn(f"الفيديو {ui + 1} مش صالح.")
            continue
        wins = get_windows(cfg, src, info, rep)
        if cfg["preview"]:
            wins = [(0.0, min(8.0, info["dur"]), "")] if not wins else [(wins[0][0], min(wins[0][1], wins[0][0] + 8.0), wins[0][2])]
        if not wins:
            rep.warn(f"الفيديو {ui + 1} قصير على الإعدادات دي.")
            continue
        wspan = span * 0.8 / len(wins)
        for wi, win in enumerate(wins):
            made += 1
            base = lo + span * 0.1 + wspan * wi
            try:
                final, rdur = render_reel(src, info, win, made, cfg, W, H, rep, base, wspan * 0.85)
                final = add_cards(final, cfg, W, H, os.path.dirname(final), rep)
                name = "preview.mp4" if cfg["preview"] else f"reel_{made:02d}.mp4"
                outp = os.path.join(OUT, name)
                shutil.copy(final, outp)
                if not cfg["preview"]:
                    cov = os.path.join(OUT, f"cover_{made:02d}.jpg")
                    ffrun(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-ss", f"{min(max(rdur * 0.3, 0.5), max(rdur - 0.3, 0.1)):.2f}",
                           "-i", outp, "-frames:v", "1", "-q:v", "3", cov])
                    if os.path.exists(cov):
                        rep.add_file(cov)
                rep.add_file(outp)
                rep.update(base + wspan * 0.9, f"خلص ريل {made}")
            except UserError:
                raise
            except Exception as e:
                made -= 1
                rep.warn(f"ريل من الفيديو {ui + 1} فشل: " + str(e)[:140])
                say(str(e))
        for f_ in os.listdir(WORK):
            if f_.startswith(f"source{ui}"):
                try:
                    os.remove(os.path.join(WORK, f_))
                except Exception:
                    pass
    if made == 0:
        raise UserError("ماطلعش ولا ريل. راجع التنبيهات أو جرّب رابط تاني.")
    rep.finish(True, f"خلصنا: {made} " + ("معاينة" if cfg["preview"] else "ريل"))
    return made


def main():
    job = os.environ.get("IN_JOB", "").strip() or "local"
    local = "--local" in sys.argv
    raw = {}
    try:
        path = None
        if "--cfg" in sys.argv:
            path = sys.argv[sys.argv.index("--cfg") + 1]
        txt = open(path, encoding="utf-8").read() if path else os.environ.get("IN_CFG", "{}")
        raw = json.loads(txt or "{}")
    except Exception as e:
        print("إعدادات غير صالحة:", e)
        sys.exit(2)
    if not re.match(r"^[a-z0-9]{6,32}$", job) and not local:
        print("رقم عملية غير صالح")
        sys.exit(2)
    os.makedirs(OUT, exist_ok=True)
    rep = Reporter(job, local)
    try:
        run(raw, rep)
    except UserError as e:
        rep.finish(False, str(e))
        print("❌", e)
        sys.exit(1)
    except Exception as e:  # خطأ غير متوقع: نبلّغ التطبيق بسبب مفهوم
        rep.finish(False, "خطأ غير متوقع: " + str(e)[:240])
        print("❌", repr(e))
        sys.exit(1)


if __name__ == "__main__":
    main()
