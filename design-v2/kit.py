# -*- coding: utf-8 -*-
"""倒置 IA 设计稿 · 共享设计系统（CSS token + 场景组件）。"""

W, H = 390, 844

# 设计 token —— 全部取自已实现的 colors.xml
C = dict(
    brand="#4CAF50", brand_dark="#388E3C", brand_light="#C8E6C9", accent="#FFB300",
    text="#2A3328", sub="#5B6B58", hint="#9AA79A",
    common="#9E9E9E", rare="#2196F3", epic="#9C27B0", legendary="#FF9800",
    mythic="#F44336", mutant="#00E5FF",
    luo="#4FC3F7", lin="#26A69A", mao="#BCAAA4", yu="#FF8A65", kun="#9CCC65",
)

CSS = """
*{box-sizing:border-box;margin:0;padding:0}
html,body{width:390px;height:844px;overflow:hidden;background:#DFF0DF}
.stage{position:relative;width:390px;height:844px;overflow:hidden;
  font-family:"Microsoft YaHei","PingFang SC",sans-serif;color:#2A3328;
  -webkit-font-smoothing:antialiased}
.bg{position:absolute;inset:0;width:100%;height:100%;object-fit:cover;transform:scale(1.04)}
.abs{position:absolute}

/* ---- 顶部：极简，不挂货币 ---- */
.daychip{top:14px;left:14px;display:flex;align-items:center;gap:7px;padding:7px 13px;
  background:rgba(255,255,255,.72);backdrop-filter:blur(8px);border-radius:18px;
  font-size:12.5px;font-weight:600;color:#33502F;box-shadow:0 2px 10px rgba(30,60,30,.10)}
.daychip i{font-style:normal;font-weight:400;color:#6B7F68}

/* ---- 抽屉把手：右缘，唯一的常驻 HUD ---- */
.handle{top:50%;right:0;transform:translateY(-50%);width:30px;padding:14px 0 12px;
  background:rgba(255,255,255,.82);backdrop-filter:blur(8px);
  border-radius:16px 0 0 16px;box-shadow:-2px 2px 12px rgba(30,60,30,.14);
  display:flex;flex-direction:column;align-items:center;gap:5px}
.handle .bar{width:13px;height:2.4px;border-radius:2px;background:#4E6B4A}
.handle .lb{font-size:8.5px;color:#6B7F68;writing-mode:vertical-rl;letter-spacing:2px;margin-top:2px}
.ring{position:relative;width:20px;height:20px;margin-bottom:1px}
.ring svg{position:absolute;inset:0}
.ring b{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;
  font-size:8px;color:#2E7D32}

/* ---- 搭话气泡 ---- */
.bubble{position:absolute;background:rgba(255,255,255,.94);border-radius:16px;
  padding:9px 13px;font-size:13px;font-weight:500;color:#2E3B2C;line-height:1.45;
  box-shadow:0 6px 18px rgba(30,60,30,.16);max-width:200px}
.bubble:after{content:"";position:absolute;bottom:-6px;left:26px;width:12px;height:12px;
  background:inherit;transform:rotate(45deg);border-radius:2px}
.bubble.r:after{left:auto;right:26px}
.bubble .act{display:inline-flex;align-items:center;gap:4px;margin-top:7px;padding:5px 11px;
  background:#4CAF50;color:#fff;border-radius:12px;font-size:11.5px;font-weight:600}
.bubble .act.ghost{background:rgba(76,175,80,.12);color:#2E7D32}
.bubble.done{color:#8FA08C}
.bubble.done .ok{display:inline-flex;width:15px;height:15px;border-radius:50%;background:#4CAF50;
  color:#fff;font-size:10px;align-items:center;justify-content:center;margin-right:5px}

/* ---- 宠物名牌 ---- */
.plate{position:absolute;display:flex;flex-direction:column;align-items:center;gap:3px}
.plate .nm{display:flex;align-items:center;gap:5px;padding:4px 10px;background:rgba(255,255,255,.85);
  backdrop-filter:blur(6px);border-radius:12px;font-size:11px;font-weight:600;color:#33502F;
  box-shadow:0 2px 8px rgba(30,60,30,.12)}
.plate .nm i{font-style:normal;font-weight:400;color:#7C8E79}
.plate .badge{padding:2px 7px;background:#FFB300;color:#5D3F00;border-radius:8px;
  font-size:9px;font-weight:700}

/* ---- 飘字 / 反馈 ---- */
.floaty{position:absolute;font-weight:800;
  text-shadow:0 2px 6px rgba(255,255,255,.95),0 0 14px rgba(255,255,255,.9),0 1px 3px rgba(255,255,255,.9)}
.floaty.exp{font-size:21px;color:#2E7D32}
.floaty.inti{font-size:14px;color:#E76A6A}
.chipctx{position:absolute;padding:7px 13px;background:rgba(255,255,255,.88);
  backdrop-filter:blur(8px);border-radius:16px;font-size:12px;font-weight:600;color:#33502F;
  box-shadow:0 3px 12px rgba(30,60,30,.14)}
.chipctx i{font-style:normal;font-weight:400;color:#7C8E79}

/* ---- 蛋 ---- */
.eggtag{position:absolute;display:flex;flex-direction:column;align-items:center;gap:4px}
.eggtag .lb{padding:4px 10px;background:rgba(255,255,255,.85);border-radius:11px;
  font-size:10.5px;font-weight:600;color:#33502F;box-shadow:0 2px 8px rgba(30,60,30,.14)}

/* ---- 底部提示（onboarding，一周后消失） ---- */
.foot-hint{position:absolute;bottom:20px;left:0;right:0;text-align:center;font-size:11.5px;
  color:rgba(38,60,35,.62);text-shadow:0 1px 4px rgba(255,255,255,.8);padding:0 14px;line-height:1.4}
.foot-hint b{color:#2E5B2A}

/* ---- 底部抽层（孵化片） ---- */
.sheet{position:absolute;left:10px;right:10px;bottom:10px;background:rgba(255,255,255,.96);
  backdrop-filter:blur(14px);border-radius:26px;padding:10px 18px 20px;
  box-shadow:0 -8px 40px rgba(20,45,20,.22)}
.sheet .grip{width:40px;height:4.5px;border-radius:3px;background:#D8E2D6;margin:0 auto 12px}
.sheet h3{font-size:16.5px;font-weight:800;color:#26382A;display:flex;align-items:baseline;gap:8px}
.sheet h3 i{font-style:normal;font-size:12px;font-weight:500;color:#7C8E79}
.srow{display:flex;align-items:center;gap:12px;padding:12px 2px;border-bottom:1px solid #EDF3EC}
.srow:last-child{border-bottom:none}
.srow .ic{flex:0 0 40px;height:40px;border-radius:13px;display:flex;align-items:center;
  justify-content:center;font-size:19px}
.srow .tx{flex:1;min-width:0}
.srow .tx b{display:block;font-size:13.5px;font-weight:700;color:#2A3A28}
.srow .tx i{font-style:normal;font-size:11px;color:#8A9A87}
.srow .go{flex:0 0 auto;padding:7px 14px;border-radius:14px;background:#E8F5E9;color:#2E7D32;
  font-size:12px;font-weight:700}
.srow .go.warn{background:#FFF3E0;color:#B26A00}

/* ---- 模态卡（揭晓 / 起名 / 档案浮卡） ---- */
.modal-mask{position:absolute;inset:0;background:rgba(22,42,24,.46);backdrop-filter:blur(3px)}
.card{position:absolute;left:18px;right:18px;background:#fff;border-radius:26px;
  box-shadow:0 24px 60px rgba(15,40,18,.35)}
.rarity{display:inline-flex;align-items:center;gap:5px;padding:4px 11px;border-radius:12px;
  font-size:11.5px;font-weight:800;color:#fff}
.dot{width:7px;height:7px;border-radius:50%}
.kv{display:flex;flex-wrap:wrap;gap:7px}
.kv span{padding:5px 10px;border-radius:11px;background:#F2F7F1;font-size:11.5px;
  font-weight:600;color:#44553F}
.kv span i{font-style:normal;color:#93A48F;margin-left:3px;font-weight:500}
.btn{display:block;width:100%;padding:13px;border-radius:18px;background:#4CAF50;color:#fff;
  font-size:15.5px;font-weight:800;text-align:center;letter-spacing:1px;
  box-shadow:0 8px 20px rgba(76,175,80,.35)}
.btn.ghost{background:#EEF5EE;color:#2E7D32;box-shadow:none;font-size:13px;font-weight:600}

/* ---- 抽屉（右缘拉出） ---- */
.drawer{position:absolute;top:0;right:0;bottom:0;width:318px;background:rgba(252,254,251,.97);
  backdrop-filter:blur(16px);border-radius:26px 0 0 26px;
  box-shadow:-14px 0 44px rgba(15,40,18,.28);padding:22px 20px 18px}
.drawer .scrim-l{position:absolute;top:0;bottom:0;left:-390px;width:390px;background:rgba(22,42,24,.38)}
.me{display:flex;align-items:center;gap:12px;margin-bottom:16px}
.me .av{width:46px;height:46px;border-radius:16px;background:linear-gradient(135deg,#A5D6A7,#4CAF50);
  display:flex;align-items:center;justify-content:center;font-size:22px;
  box-shadow:0 4px 12px rgba(76,175,80,.3)}
.me .tx b{display:block;font-size:15px;font-weight:800;color:#26382A}
.me .tx i{font-style:normal;font-size:11px;color:#8A9A87}
.wallet{display:flex;gap:9px;margin-bottom:16px}
.wallet .w{flex:1;padding:10px 12px;border-radius:16px;background:#F3F8F2}
.wallet .w i{display:block;font-style:normal;font-size:10px;color:#8A9A87;margin-bottom:2px}
.wallet .w b{font-size:15.5px;font-weight:800;color:#3A4A36}
.mrow{display:flex;align-items:center;gap:12px;padding:12.5px 4px;border-bottom:1px solid #EEF3EC}
.mrow:last-of-type{border-bottom:none}
.mrow .ic{flex:0 0 36px;height:36px;border-radius:12px;background:#EAF4EA;display:flex;
  align-items:center;justify-content:center;font-size:17px}
.mrow .tx{flex:1;min-width:0}
.mrow .tx b{display:block;font-size:13.5px;font-weight:700;color:#2A3A28}
.mrow .tx i{font-style:normal;font-size:10.5px;color:#94A490}
.mrow .chev{color:#B9C6B6;font-size:15px}
.mrow .cnt{padding:2px 8px;border-radius:9px;background:#E8F5E9;color:#2E7D32;font-size:10px;font-weight:700}
.dtitle{font-size:11px;font-weight:700;color:#9AA79A;letter-spacing:2px;margin:4px 2px 2px}

/* ---- 六维 ---- */
.stats{display:grid;grid-template-columns:1fr 1fr 1fr;gap:8px}
.stat{padding:9px 4px 7px;border-radius:13px;background:#F4F8F3;text-align:center}
.stat i{display:block;font-style:normal;font-size:10px;color:#8FA08B;margin-bottom:1px}
.stat b{font-size:16px;font-weight:800;color:#33422F}
.stat.pw i{color:#C98F00}
.stat.pw{background:#FFF7E6}
.stat.pw b{color:#A66F00}

/* ---- 手势（涟漪 + 指尖） ---- */
.ripple{position:absolute;border-radius:50%;border:2.5px solid rgba(255,255,255,.9);
  box-shadow:0 0 18px rgba(255,255,255,.5), inset 0 0 12px rgba(255,255,255,.35)}
.ripple.g{border-color:rgba(76,175,80,.75);box-shadow:0 0 18px rgba(76,175,80,.45)}
.heart{position:absolute;font-size:16px;filter:drop-shadow(0 2px 4px rgba(200,60,60,.25))}
.zzz{position:absolute;font-weight:800;color:rgba(90,110,160,.75);text-shadow:0 1px 4px rgba(255,255,255,.6)}

/* ---- 托盘（拖拽源） ---- */
.tray{position:absolute;left:12px;right:64px;bottom:14px;display:flex;gap:8px;
  padding:10px;background:rgba(255,255,255,.9);backdrop-filter:blur(10px);border-radius:20px;
  box-shadow:0 10px 30px rgba(25,55,25,.2)}
.tray .it{flex:1;display:flex;flex-direction:column;align-items:center;gap:3px;padding:8px 2px;
  border-radius:14px;background:#F5F9F4}
.tray .it .em{font-size:21px;line-height:1}
.tray .it b{font-size:9.5px;color:#44553F;font-weight:700}
.tray .it i{font-style:normal;font-size:8.5px;color:#94A490}
.tray .tip{position:absolute;top:-30px;left:12px;padding:5px 11px;background:rgba(46,125,50,.92);
  color:#fff;border-radius:12px;font-size:10.5px;font-weight:600}
.ghostitem{position:absolute;font-size:30px;filter:drop-shadow(0 6px 10px rgba(40,70,40,.35))}

/* ---- 光效 ---- */
.glowdot{position:absolute;border-radius:50%}
.spark{position:absolute;color:#FFE082;text-shadow:0 0 8px rgba(255,224,130,.9)}
.vig{position:absolute;inset:0;background:radial-gradient(120% 90% at 50% 42%,transparent 55%,rgba(20,40,20,.28))}
"""


def pet_svg(species="chick", mood="happy", scale=1.0):
    """场景小宠：blob 身体 + 物种特征。mood: happy|sleep|joy|dizzy"""
    eye = {
        "happy": '<circle cx="40" cy="53" r="3.4" fill="#33261F"/><circle cx="60" cy="53" r="3.4" fill="#33261F"/>'
                 '<circle cx="41.2" cy="51.6" r="1.15" fill="#fff"/><circle cx="61.2" cy="51.6" r="1.15" fill="#fff"/>',
        "joy":   '<path d="M35 52 q5 -6 10 0" stroke="#33261F" stroke-width="2.4" fill="none" stroke-linecap="round"/>'
                 '<path d="M55 52 q5 -6 10 0" stroke="#33261F" stroke-width="2.4" fill="none" stroke-linecap="round"/>',
        "sleep": '<path d="M36 54 q4 3.5 8 0" stroke="#33261F" stroke-width="2.2" fill="none" stroke-linecap="round"/>'
                 '<path d="M56 54 q4 3.5 8 0" stroke="#33261F" stroke-width="2.2" fill="none" stroke-linecap="round"/>',
        "dizzy": '<path d="M36 50 l8 7 M44 50 l-8 7 M55 50 l8 7 M63 50 l-8 7" stroke="#33261F" stroke-width="2" stroke-linecap="round"/>',
    }[mood]
    mouth = {
        "happy": '<path d="M46.5 61 q3.5 3.6 7 0" stroke="#33261F" stroke-width="1.8" fill="none" stroke-linecap="round"/>',
        "joy":   '<path d="M44 60 q6 7 12 0 z" fill="#7A4A3A"/>',
        "sleep": '<path d="M48 62 q2 2 4 0" stroke="#33261F" stroke-width="1.6" fill="none" stroke-linecap="round"/>',
        "dizzy": '<ellipse cx="50" cy="62" rx="3.4" ry="2.6" fill="#7A4A3A"/>',
    }[mood]
    if species == "chick":
        head = ('<circle cx="42" cy="26" r="6" fill="#FF8A65"/><circle cx="50" cy="22.5" r="6.5" fill="#FF8A65"/>'
                '<circle cx="58" cy="26" r="6" fill="#FF8A65"/>')
        extra = ('<path d="M62 55 l10 -2 -8.5 7 z" fill="#FFB300"/><path d="M64 61 l8 -0.5 -6.5 5 z" fill="#FFA000"/>'
                 '<ellipse cx="20" cy="58" rx="9" ry="13" fill="#FFCA28" transform="rotate(14 20 58)"/>'
                 '<ellipse cx="80" cy="58" rx="9" ry="13" fill="#FFCA28" transform="rotate(-14 80 58)"/>')
        body, belly = "#FFD54F", "#FFF8E1"
    elif species == "cat":
        head = ('<path d="M24 34 l6 -15 12 9 z" fill="#C9B8AE"/><path d="M76 34 l-6 -15 -12 9 z" fill="#C9B8AE"/>'
                '<path d="M28 31 l3.5 -8 6 4.6 z" fill="#F2C9C4"/><path d="M72 31 l-3.5 -8 -6 4.6 z" fill="#F2C9C4"/>')
        extra = ('<path d="M30 40 q4 -3 8 0" stroke="#BCAAA4" stroke-width="3" fill="none" stroke-linecap="round"/>'
                 '<path d="M62 38 q4 -3 8 0" stroke="#BCAAA4" stroke-width="3" fill="none" stroke-linecap="round"/>'
                 '<path d="M84 62 q14 -2 10 -16" stroke="#C9B8AE" stroke-width="7" fill="none" stroke-linecap="round"/>')
        body, belly = "#D7CCC8", "#F1EBE8"
    else:  # dog
        head = ('<ellipse cx="22" cy="46" rx="11" ry="17" fill="#D9A05B" transform="rotate(10 22 46)"/>'
                '<ellipse cx="78" cy="46" rx="11" ry="17" fill="#D9A05B" transform="rotate(-10 78 46)"/>')
        extra = ('<ellipse cx="50" cy="72" rx="7" ry="5" fill="#FFFDF8"/>'
                 '<path d="M36 40 q6 -4 12 0 M52 40 q6 -4 12 0" stroke="#E8C79A" stroke-width="3" fill="none" stroke-linecap="round"/>')
        body, belly = "#FFE7C2", "#FFFDF8"
    blush = ('<ellipse cx="31" cy="61" rx="5" ry="3" fill="rgba(255,138,128,.5)"/>'
             '<ellipse cx="69" cy="61" rx="5" ry="3" fill="rgba(255,138,128,.5)"/>')
    return (f'<svg viewBox="0 0 100 100" width="{100*scale}" height="{100*scale}">'
            f'<ellipse cx="50" cy="93" rx="27" ry="5.5" fill="rgba(30,55,30,.18)"/>'
            f'{head}<ellipse cx="50" cy="57" rx="31" ry="29" fill="{body}"/>'
            f'<ellipse cx="50" cy="66" rx="19" ry="16" fill="{belly}"/>{extra}{blush}{eye}{mouth}</svg>')


def egg_svg(size=76, crack=0, glow=False):
    """蛋。crack 0~3 裂纹级；glow 夜光。"""
    cracks = {
        1: '<path d="M40 36 l9 8 -8 6 10 8" stroke="#B0876A" stroke-width="2.2" fill="none" stroke-linecap="round" stroke-linejoin="round"/>',
        2: '<path d="M40 36 l9 8 -8 6 10 8 M63 32 l-8 10 7 7 -9 10" stroke="#B0876A" stroke-width="2.2" fill="none" stroke-linecap="round" stroke-linejoin="round"/>',
        3: '<path d="M40 36 l9 8 -8 6 10 8 M63 32 l-8 10 7 7 -9 10 M40 70 l8 -6 7 6 8 -5" stroke="#B0876A" stroke-width="2.4" fill="none" stroke-linecap="round" stroke-linejoin="round"/>',
    }
    halo = (f'<defs><radialGradient id="eg"><stop offset="0" stop-color="rgba(255,236,160,.55)"/>'
            f'<stop offset=".55" stop-color="rgba(255,232,150,.32)"/>'
            f'<stop offset="1" stop-color="rgba(255,232,150,0)"/></radialGradient></defs>'
            f'<circle cx="50" cy="52" r="46" fill="url(#eg)"/>') if glow else ""
    star = '<path d="M20 26 l1.8 4.4 4.4 1.8 -4.4 1.8 -1.8 4.4 -1.8 -4.4 -4.4 -1.8 4.4 -1.8 z" fill="#FFF3C4"/>' if glow else ""
    return (f'<svg viewBox="0 0 100 100" width="{size}" height="{size}">{halo}{star}'
            f'<ellipse cx="50" cy="90" rx="20" ry="4.5" fill="rgba(30,55,30,.18)"/>'
            f'<path d="M50 16 C70 16 80 36 80 56 C80 76 66 88 50 88 C34 88 20 76 20 56 C20 36 30 16 50 16 z" fill="#FDF6E8"/>'
            f'<ellipse cx="40" cy="34" rx="9" ry="13" fill="rgba(255,255,255,.75)" transform="rotate(-18 40 34)"/>'
            f'<circle cx="62" cy="70" r="4" fill="#F3E3C6"/><circle cx="38" cy="74" r="3" fill="#F3E3C6"/>'
            f'<circle cx="64" cy="34" r="3.4" fill="#F3E3C6"/>{cracks.get(crack, "")}</svg>')


def bubble(text, x, y, tail="left", act=None, done=False, width=None):
    a = f'<span class="act">{act}</span>' if act else ""
    cls = "bubble" + (" r" if tail == "right" else "") + (" done" if done else "")
    st = f"left:{x}px;top:{y}px;" + (f"max-width:{width}px;" if width else "")
    ok = '<span class="ok">✓</span>' if done else ""
    return f'<div class="{cls}" style="{st}">{ok}{text}{a}</div>'


def pet(name, lvl, x, y, size, species="chick", mood="happy", badge=None, plate_dx=0):
    badge_html = f'<span class="badge">{badge}</span>' if badge else ""
    return (f'<div class="abs" style="left:{x}px;top:{y}px;width:{size}px;height:{size}px">{pet_svg(species, mood)}</div>'
            f'<div class="plate" style="left:{x + size/2 - 70 + plate_dx}px;top:{y + size*0.94}px;width:140px">'
            f'<span class="nm">{name}<i>Lv.{lvl}</i>{badge_html}</span></div>')


def drawer_handle(ring=True, pct=71):
    """右缘抽屉把手；ring=True 时把『今日经验进度环』挂把手上（唯一常驻 HUD）。"""
    if ring:
        r, cir = 8.5, 2 * 3.1416 * 8.5
        dash = cir * pct / 100.0
        ring_html = (f'<span class="ring"><svg viewBox="0 0 20 20">'
                     f'<circle cx="10" cy="10" r="{r}" fill="none" stroke="#DDE8DB" stroke-width="2.6"/>'
                     f'<circle cx="10" cy="10" r="{r}" fill="none" stroke="#4CAF50" stroke-width="2.6"'
                     f' stroke-linecap="round" stroke-dasharray="{dash:.1f} {cir:.1f}" transform="rotate(-90 10 10)"/>'
                     f'</svg><b>{pct}</b></span>')
    else:
        ring_html = ""
    return (f'<div class="handle abs">{ring_html}'
            f'<span class="bar"></span><span class="bar"></span><span class="bar"></span>'
            f'<span class="lb">更多</span></div>')


def page(screen_css, body):
    """整页 HTML 壳。"""
    return (f'<!DOCTYPE html><html lang="zh-CN"><head><meta charset="utf-8">'
            f'<style>{CSS}{screen_css}</style></head>'
            f'<body><div class="stage">{body}</div></body></html>')

