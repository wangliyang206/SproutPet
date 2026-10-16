# -*- coding: utf-8 -*-
"""倒置 IA 设计稿 v2 —— 10 屏定义 + 渲染 + 归档页构建。

原则（docs/09 U-10 拍板稿）：
  P1 打开 App = 家（全屏场景，无 Tab 栏）
  P2 互动 = 在宠物身上的手势（删掉「互动面板」页）
  P3 盲盒 = 场景物件（蛋长在家里）
  P4 孵化进度 = 蛋壳裂纹（删掉「孵化中」独立页的进度条）
  P5 管理功能 → 边缘抽屉（图鉴/族谱/仓库/设置）
  P6 任务由宠物搭话驱动（删掉「今日任务」清单页）
  P7 货币不挂标题栏（收进抽屉）
  P8 详情 = 长按浮卡，不跳页
"""
import os
import subprocess
import base64

from kit import W, H, C, CSS, page, pet_svg, egg_svg, bubble, pet, drawer_handle

BASE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(BASE, "assets")
SCR = os.path.join(BASE, "screens")
CHROME = r"C:/Users/Administrator/AppData/Local/Google/Chrome/Application/chrome.exe"
BG_DAY = "../assets/bg-home-day.png"
BG_NIGHT = "../assets/bg-home-night.png"


# ---------------------------------------------------------------- 公共小件
def rays(x, y, size, color="rgba(255,235,150,.4)"):
    return (f'<div class="abs" style="left:{x}px;top:{y}px;width:{size}px;height:{size}px;'
            f'background:repeating-conic-gradient(from 8deg,{color} 0 7deg,transparent 7deg 15deg);'
            f'border-radius:50%;-webkit-mask-image:radial-gradient(closest-side,#000 18%,transparent 72%)"></div>')


def expbar(w, pct, h=8):
    return (f'<div style="height:{h}px;border-radius:{h/2}px;background:#E8F0E7;overflow:hidden;width:{w}px">'
            f'<div style="height:100%;border-radius:{h/2}px;width:{pct}%;'
            f'background:linear-gradient(90deg,#7DC47F,#4CAF50)"></div></div>')


def hearts(pts):
    return "".join(f'<span class="heart" style="left:{x}px;top:{y}px;font-size:{s}px;opacity:{o}">\u2764\uFE0F</span>'
                   for x, y, s, o in pts)


def glowdots(pts, color="rgba(255,240,170,.9)"):
    return "".join(f'<span class="glowdot" style="left:{x}px;top:{y}px;width:{s}px;height:{s}px;'
                   f'background:radial-gradient(circle,{color} 0 40%,transparent 75%)"></span>'
                   for x, y, s in pts)


def dashes(pts, w, h, color="rgba(76,175,80,.65)"):
    d = "M " + " L ".join(f"{x} {y}" for x, y in pts)
    end = pts[-1]
    return (f'<svg class="abs" style="left:0;top:0" width="{w}" height="{h}">'
            f'<path d="{d}" stroke="{color}" stroke-width="3" fill="none" stroke-dasharray="1 10" stroke-linecap="round"/>'
            f'<circle cx="{end[0]}" cy="{end[1]}" r="5" fill="{color}"/></svg>')


def zzz(x, y):
    return (f'<span class="zzz" style="left:{x}px;top:{y}px;font-size:15px">z</span>'
            f'<span class="zzz" style="left:{x+10}px;top:{y-12}px;font-size:19px">Z</span>')


DAYCHIP = '<div class="daychip abs">🏡 家<i>10月16日 · 晴</i></div>'
NIGHTCHIP = '<div class="daychip abs" style="background:rgba(28,42,60,.6);color:#EAF2FF">🌙 晚安<i style="color:#B9C8E8">22:14</i></div>'


# ---------------------------------------------------------------- 01 家 · 主界面
def s01():
    body = f"""
    <img class="bg" src="{BG_DAY}">{DAYCHIP}{drawer_handle()}
    {bubble("肚子有点饿了…", 34, 296, act="把食物拖给它", width=190)}
    {pet("小豆芽", 26, 86, 386, 124, "chick", "happy", badge="主宠")}
    {pet("瞌睡猫", 31, 244, 236, 96, "cat", "sleep")}
    {zzz(322, 238)}
    {pet("绒绒犬", 18, 46, 592, 92, "dog", "happy")}
    <div class="eggtag abs" style="left:292px;top:648px">{egg_svg(74, 0)}<span class="lb">新蛋 · 轻点看看</span></div>
    <div class="foot-hint">摸摸它 · 把食物<b>拖过去</b> · 长按看档案</div>
    """
    return "", body


# ---------------------------------------------------------------- 02 手势 · 抚摸
def s02():
    rip = "".join(f'<div class="ripple g abs" style="left:{206-r}px;top:{408-r}px;width:{2*r}px;height:{2*r}px;'
                  f'opacity:{max(0, .85 - i*.24)}"></div>' for i, r in enumerate((30, 52, 74)))
    finger = (f'<div class="abs" style="left:188px;top:388px;width:38px;height:38px;border-radius:50%;'
              f'background:rgba(255,255,255,.9);box-shadow:0 4px 14px rgba(30,60,30,.3)"></div>'
              f'<div class="abs" style="left:197px;top:397px;width:20px;height:20px;border-radius:50%;'
              f'background:rgba(255,255,255,.65)"></div>')
    return "", f"""
    <img class="bg" src="{BG_DAY}"><div class="vig"></div>{drawer_handle()}
    <div class="chipctx abs" style="left:14px;top:14px">抚摸<i>今日 32/50</i></div>
    {bubble("叽～好舒服，再摸一下嘛！", 44, 160, width=200)}
    {pet("小豆芽", 26, 96, 322, 150, "chick", "joy", badge="主宠")}
    {rip}{finger}
    {hearts([(150,250,15,.9),(196,214,20,1),(242,246,14,.8),(272,300,11,.6),(170,196,10,.55)])}
    <div class="floaty exp abs" style="left:64px;top:318px">+15</div>
    <div class="floaty inti abs" style="left:72px;top:352px">亲密 +1</div>
    <div class="foot-hint">反馈发生在它身上 —— <b>没有「互动面板」这一页</b></div>
    """


# ---------------------------------------------------------------- 03 手势 · 拖拽喂食
def s03():
    return "", f"""
    <img class="bg" src="{BG_DAY}"><div class="vig"></div>{drawer_handle()}
    <div class="chipctx abs" style="left:14px;top:14px">喂食<i>今日 18/20 · 谷物饲料 ×12</i></div>
    {bubble("开饭啦！", 236, 236, tail="right")}
    {pet("绒绒犬", 18, 128, 292, 146, "dog", "joy")}
    {dashes([(88,742),(120,600),(162,486),(196,392)], 390, 844)}
    <span class="ghostitem" style="left:180px;top:344px">🌾</span>
    <div class="floaty exp abs" style="left:60px;top:318px">+60</div>
    <div class="floaty inti abs" style="left:70px;top:352px">亲密 +3</div>
    <div class="tray">
      <span class="tip">按住，拖给它 —— 用完的耐久自己会掉</span>
      <div class="it"><span class="em">🌾</span><b>谷物饲料</b><i>×12</i></div>
      <div class="it"><span class="em">🧶</span><b>毛线球</b><i>×5</i></div>
      <div class="it"><span class="em">🥊</span><b>训练沙袋</b><i>×2</i></div>
    </div>
    """


# ---------------------------------------------------------------- 04 蛋 · 就地揭晓
def s04():
    kv = ('<div class="kv"><span>羽虫</span><span>公</span><span>世代 1</span>'
          '<span style="background:#FFF7E6;color:#A66F00">距史诗保底还差 12 次</span></div>')
    return "", f"""
    <img class="bg" src="{BG_DAY}"><div class="modal-mask"></div>
    {rays(105, 130, 180)}
    <div class="abs" style="left:120px;top:140px">{egg_svg(150, 3, glow=True)}</div>
    {hearts([(120,160,12,.8),(268,182,14,.9),(150,130,9,.6),(246,286,10,.7)])}
    <div class="card abs" style="bottom:38px;padding:22px 22px 20px">
      <div style="display:flex;align-items:center;gap:9px;margin-bottom:10px">
        <span class="rarity" style="background:{C['epic']}">史诗</span>
        <span class="rarity" style="background:{C['yu']}">羽虫</span>
        <span style="margin-left:auto;font-size:11px;color:{C['hint']}">概率公示 ›</span>
      </div>
      <div style="font-size:24px;font-weight:800;color:#26382A">萌芽鸡</div>
      <div style="font-size:12px;color:{C['sub']};margin:5px 0 12px">初生 · 蛋期 —— 初始基因已生成，六维将随成长逐步显化</div>
      {kv}
      <div style="margin-top:16px"><span class="btn">带它回家 · 继续孵化</span></div>
      <div style="margin-top:9px;text-align:center;font-size:11px;color:{C['hint']}">蛋就长在家里 —— 开箱不必离开家</div>
    </div>
    """


# ---------------------------------------------------------------- 05 孵化 · 裂纹即进度
def s05():
    rows = "".join(f'<div class="srow"><span class="ic" style="background:{bg}">{em}</span>'
                   f'<span class="tx"><b>{t}</b><i>{s}</i></span><span class="go{" warn" if warn else ""}">{go}</span></div>'
                   for em, bg, t, s, go, warn in (
        ("⏱", "#E8F5E9", "专注陪伴", "专注时长 ×50% 抵扣孵化 · 最多抵 50%", "开启", False),
        ("🧪", "#FFF3E0", "道具加速", "沙漏碎片 ×3 · 每个加速 1 小时", "用道具", True),
        ("🤝", "#E3F2FD", "好友助力", "已助力 2/5 人 · 每人减 5 分钟", "邀请", False),
    ))
    return "", f"""
    <img class="bg" src="{BG_DAY}">{DAYCHIP}{drawer_handle()}
    {bubble("快了吧？我一直在听。", 46, 236, width=170)}
    {pet("小豆芽", 26, 60, 322, 108, "chick", "happy", badge="主宠")}
    <div class="eggtag abs" style="left:236px;top:262px">{egg_svg(104, 2, glow=True)}
      <span class="lb">剩 18 分 24 秒 · 今晚破壳</span></div>
    <div class="sheet">
      <div class="grip"></div>
      <h3 style="white-space:nowrap">孵化中 <i>剩余 18 分 24 秒</i></h3>
      <div style="font-size:11px;color:{C['hint']};margin:2px 0 10px">四档文案随进度换：还在睡 → 有动静 → 裂纹蔓延 → 今晚破壳</div>
      <div>{rows}</div>
    </div>
    """


# ---------------------------------------------------------------- 06 破壳 · 起名仪式
def s06():
    stats = "".join(f'<div class="stat{" pw" if k == "综合" else ""}"><i>{k}</i><b>{v}</b></div>'
                    for k, v in (("体力", 12), ("力量", 9), ("敏捷", 14), ("智慧", 8), ("魅力", 13), ("综合", 62)))
    return "", f"""
    <img class="bg" src="{BG_NIGHT}"><div class="modal-mask" style="background:rgba(18,36,22,.5)"></div>
    {rays(115, 96, 160, "rgba(255,236,170,.42)")}
    {glowdots([(70,120,10),(300,110,12),(52,300,8),(330,262,9),(90,96,7)])}
    <div class="abs" style="left:135px;top:108px">{pet_svg("chick", "joy", 1.2)}</div>
    {hearts([(150,120,11,.8),(244,140,13,.9),(210,104,9,.6)])}
    <div class="card abs" style="bottom:40px;padding:20px 22px 18px">
      <div style="font-size:12px;font-weight:700;color:{C['brand']};letter-spacing:2px;margin-bottom:8px">宠物档案 · 世代 1</div>
      <div class="kv" style="margin-bottom:12px"><span>羽虫</span><span style="background:#F6EBFA;color:{C['epic']}">史诗</span><span>公</span></div>
      <div class="stats" style="margin-bottom:14px">{stats}</div>
      <div style="font-size:11px;color:{C['sub']};margin-bottom:6px">给它起个名字 —— 这是你们故事的第一步</div>
      <div style="padding:12px 14px;border:2px solid {C['brand']};border-radius:15px;font-size:17px;font-weight:800;color:#26382A;margin-bottom:12px">小豆芽<span style="display:inline-block;width:2px;height:17px;background:#4CAF50;vertical-align:-3px;margin-left:3px"></span></div>
      <span class="btn">确认，欢迎回家</span>
    </div>
    """


# ---------------------------------------------------------------- 07 搭话任务
def s07():
    return "", f"""
    <img class="bg" src="{BG_DAY}">{DAYCHIP}{drawer_handle(pct=71)}
    <div class="chipctx abs" style="left:14px;top:14px">今日经验<i>4,265 / 6,000</i></div>
    {pet("瞌睡猫", 31, 34, 196, 98, "cat", "happy")}
    {bubble("想玩毛线球…", 128, 176, tail="right", act="拖给它")}
    {pet("绒绒犬", 18, 232, 400, 102, "dog", "happy")}
    {bubble("想出门串门！", 128, 384, tail="right", act="走，串门")}
    {pet("小豆芽", 26, 76, 580, 100, "chick", "happy", badge="主宠")}
    {bubble("已陪它玩了毛线球", 180, 566, tail="right", done=True)}
    <div class="foot-hint">任务不用打卡 —— <b>回应它们，就算数</b></div>
    """


# ---------------------------------------------------------------- 08 长按 · 档案浮卡
def s08():
    stats = "".join(f'<div class="stat{" pw" if k == "综合" else ""}"><i>{k}</i><b>{v}</b></div>'
                    for k, v in (("体力", 58), ("力量", 44), ("敏捷", 61), ("智慧", 37), ("魅力", 55), ("综合", 62)))
    return "", f"""
    <img class="bg" src="{BG_DAY}"><div class="modal-mask" style="background:rgba(22,42,24,.4)"></div>
    {drawer_handle()}
    <div class="chipctx abs" style="left:14px;top:14px">长按它 · 档案浮上来</div>
    <div class="card abs" style="bottom:26px;padding:20px 22px 16px">
      <div style="display:flex;gap:14px;align-items:center;margin-bottom:12px">
        <div style="width:74px;height:74px">{pet_svg("chick", "happy", 0.74)}</div>
        <div style="flex:1">
          <div style="font-size:19px;font-weight:800;color:#26382A">小豆芽
            <span style="font-size:10px;background:#FFB300;color:#5D3F00;border-radius:8px;padding:2px 7px;vertical-align:3px;margin-left:4px">主宠</span></div>
          <div style="font-size:11.5px;color:{C['sub']};margin-top:3px">公 · 羽虫 · 史诗 · 世代 1</div>
          <div style="font-size:12px;font-weight:700;color:#33422F;margin-top:6px">Lv.26 成年
            <i style="font-style:normal;font-weight:400;color:{C['hint']};margin-left:6px">3,840 / 6,238</i></div>
          <div style="margin-top:5px">{expbar(200, 62)}</div>
        </div>
      </div>
      <div class="stats" style="margin-bottom:12px">{stats}</div>
      <div style="padding:9px 12px;border-radius:13px;background:#F0F7EF;font-size:11.5px;color:#3C4F39;margin-bottom:10px">
        成年已解锁 · 配对生育（亲密 78 ≥ 70）· 族谱 · 排行榜 · 共同生活动作</div>
      <div style="text-align:center;font-size:11px;color:{C['hint']}">‹ 左右滑动卡片，看家里其他成员 ›&nbsp;&nbsp;●○○</div>
    </div>
    """


# ---------------------------------------------------------------- 09 抽屉 · 家
def s09():
    rows = "".join(f'<div class="mrow"><span class="ic" style="background:{bg}">{em}</span>'
                   f'<span class="tx"><b>{t}</b><i>{s}</i></span>{cnt}<span class="chev">›</span></div>'
                   for em, bg, t, s, cnt in (
        ("📖", "#E8F5E9", "宠物图鉴", "五虫十八种 · 已收集", '<span class="cnt">8/18</span>'),
        ("🌳", "#EAF4EA", "族谱", "三代血缘 · 称谓已生成", ""),
        ("🎒", "#FFF3E0", "仓库", "道具 23 件 · 玩具 5", ""),
        ("🏆", "#F6EBFA", "成就", "已点亮 12 枚", ""),
        ("⛰️", "#E0F2F1", "后山小屋", "1 位长辈隐居 · 可随时召回", ""),
        ("⚙️", "#ECEFF1", "设置 · 隐私与合规", "悬浮窗声明 · 概率公示", ""),
    ))
    return "", f"""
    <img class="bg" src="{BG_DAY}"><div class="modal-mask" style="background:rgba(22,42,24,.34)"></div>
    <div class="drawer">
      <div class="me">
        <span class="av">🌱</span>
        <span class="tx"><b>芽农小王</b><i>Lv.12 养宠人 · 已陪伴 46 天</i></span>
        <span style="margin-left:auto;color:#B9C6B6;font-size:16px">›</span>
      </div>
      <div class="wallet">
        <div class="w"><i>💰 金币</i><b>12,480</b></div>
        <div class="w"><i>💎 钻石</i><b>86</b></div>
      </div>
      <div class="dtitle">家里</div>
      {rows}
      <div style="margin-top:12px;text-align:center;font-size:10.5px;color:{C['hint']}">低频的管理都住在这里 —— 主界面留给生活</div>
    </div>
    """


# ---------------------------------------------------------------- 10 夜晚 · 家是活的
def s10():
    return "", f"""
    <img class="bg" src="{BG_NIGHT}">{NIGHTCHIP}{drawer_handle(pct=71)}
    {glowdots([(60,150,9),(320,180,11),(36,420,8),(356,360,9),(150,240,7),(258,150,8),(300,540,8)],
              "rgba(255,244,180,.95)")}
    {pet("瞌睡猫", 31, 46, 268, 92, "cat", "sleep")}{zzz(122, 268)}
    {pet("小豆芽", 26, 152, 400, 100, "chick", "sleep", badge="主宠")}{zzz(236, 398)}
    {pet("绒绒犬", 18, 62, 552, 88, "dog", "sleep")}
    <div class="eggtag abs" style="left:286px;top:600px">{egg_svg(70, 3, glow=True)}<span class="lb">夜里也在长大</span></div>
    <div class="foot-hint" style="color:rgba(220,232,255,.75);text-shadow:none">
      晚安后，家自己运转 · 离线 25 金币/小时（上限 12 小时，只发金币）</div>
    """


# ---------------------------------------------------------------- 渲染 + 归档
SCREENS = [
    ("01", "家 · 主界面（无 Tab）", "打开 App = 家。场景即信息架构：宠物生活、搭话气泡、蛋在角落；唯一的常驻 HUD 是右缘抽屉把手（环 = 今日经验进度）。", "s01", "旧 01 首页（5-Tab + 功能宫格）整体作废"),
    ("02", "手势 · 抚摸", "在宠物身上长按/滑动 = 抚摸。涟漪 + 爱心 + 经验飘字就地反馈，没有「互动面板」页。", "s02", "旧 06 互动面板 → 删除"),
    ("03", "手势 · 拖拽喂食", "托盘按住拖到宠物身上 = 喂食/玩耍/训练。玩具拖过去即消耗耐久（G-08 的落点）。", "s03", "旧 06 互动面板 → 删除"),
    ("04", "蛋 · 就地揭晓", "蛋长在家里，轻点即开箱；揭晓卡浮在家的场景上。保底与概率公示仍在场。", "s04", "旧 02 盲盒开箱页 → 场景化"),
    ("05", "孵化 · 裂纹即进度", "蛋壳裂纹就是进度，四档文案随进度换。轻点蛋从底部升起孵化片，三种加速不离开家。", "s05", "旧 03 孵化中页 → 场景化"),
    ("06", "破壳 · 起名仪式", "唯一保留的模态：起名是仪式，值得一次全屏中断。档案卡与六维初值在仪式内完成。", "s06", "旧 04 破壳起名 → 保留"),
    ("07", "搭话任务", "任务不做成进度条清单：宠物开口要，你回应就算数。今日进度只在把手环与一个淡色 chip。", "s07", "旧 08 今日任务页 → 删除"),
    ("08", "长按 · 档案浮卡", "长按宠物，档案从它身上浮起；左右滑切换家人。等级经验用修正后的 6,238。", "s08", "旧 05 宠物详情页 → 浮卡化"),
    ("09", "抽屉 · 家", "图鉴/族谱/仓库/成就/后山/设置收进右缘抽屉，货币也收在这里 —— 主界面不挂金币。", "s09", "旧 09 我的 + 旧 10 设置 → 合并收纳"),
    ("10", "夜晚 · 家是活的", "昼夜随真实时间变。夜里的家自己运转：离线收益一句说明，蛋在夜里发光。", "s10", "新增屏（旧稿无对应）"),
]


def render():
    os.makedirs(SCR, exist_ok=True)
    for num, _t, _d, fn, _old in SCREENS:
        css, body = globals()[fn]()
        html = page(css, body)
        hp = os.path.join(SCR, f"{num}.html")
        with open(hp, "w", encoding="utf-8") as f:
            f.write(html)
        out = os.path.join(SCR, f"{num}.png")
        cmd = [CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars",
               "--force-device-scale-factor=2", "--window-size=390,844",
               f"--screenshot={out}", "file:///" + hp.replace("\\", "/")]
        subprocess.run(cmd, capture_output=True, timeout=60)
        print("rendered", num, os.path.getsize(out), "bytes")


def build_archive():
    cards = []
    for num, title, desc, _fn, old in SCREENS:
        with open(os.path.join(SCR, f"{num}.png"), "rb") as f:
            uri = "data:image/png;base64," + base64.b64encode(f.read()).decode()
        cards.append(
            f'<figure class="screen"><div class="screen-hd"><span class="num">{num}</span>'
            f'<div class="screen-tt"><b>{title}</b><i>{desc}</i><em>{old}</em></div></div>'
            f'<img src="{uri}" alt="{title}" loading="lazy"></figure>')

    ARCH_CSS = CSS + """
html,body{width:auto;height:auto;overflow:visible;background:#F2F6F2}
.wrap{max-width:1240px;margin:0 auto;padding:0 28px 72px;font-family:"Microsoft YaHei",sans-serif}
.hero{margin:0 -28px 40px;padding:54px 54px 46px;background:linear-gradient(135deg,#2E7D32 0%,#4CAF50 55%,#81C784 100%);
  border-radius:0 0 40px 40px;color:#fff;position:relative;overflow:hidden}
.hero:after{content:"";position:absolute;right:-70px;top:-70px;width:300px;height:300px;border-radius:50%;background:rgba(255,255,255,.13)}
.hero h1{font-size:36px;font-weight:800;position:relative}
.hero p{margin-top:12px;font-size:15px;opacity:.95;max-width:820px;position:relative}
.chips{margin-top:24px;display:flex;flex-wrap:wrap;gap:10px;position:relative}
.chip{background:rgba(255,255,255,.2);border:1px solid rgba(255,255,255,.4);padding:7px 15px;border-radius:20px;font-size:13px;font-weight:500}
h2.sec{font-size:20px;font-weight:700;margin:46px 0 6px;display:flex;align-items:center;gap:10px}
h2.sec:before{content:"";width:5px;height:20px;border-radius:3px;background:#4CAF50}
.sec-note{font-size:13px;color:#757575;margin-bottom:18px}
.card{background:#fff;border:1px solid #E7EFE8;border-radius:22px;padding:24px 28px;box-shadow:0 6px 22px rgba(23,59,31,.05)}
.pgrid{display:grid;grid-template-columns:repeat(auto-fill,minmax(280px,1fr));gap:14px;margin-top:20px}
.pr{display:flex;gap:12px;align-items:flex-start;padding:14px 16px;border-radius:16px;background:#F7FAF6}
.pr .k{flex:0 0 92px;font-size:12.5px;font-weight:800;color:#2E7D32}
.pr .v{font-size:12.5px;color:#424242;line-height:1.55}
.pr.del .k{color:#C62828}
.screens{display:grid;grid-template-columns:repeat(auto-fill,minmax(216px,1fr));gap:24px;margin-top:22px}
.screen{background:#fff;border:1px solid #E7EFE8;border-radius:22px;padding:14px;box-shadow:0 6px 22px rgba(23,59,31,.06);display:flex;flex-direction:column;gap:12px}
.screen-hd{display:flex;gap:9px;align-items:flex-start}
.num{flex:0 0 26px;height:26px;border-radius:9px;background:#E8F5E9;color:#2E7D32;font-size:12.5px;font-weight:700;display:flex;align-items:center;justify-content:center}
.screen-tt{display:flex;flex-direction:column;line-height:1.45;min-width:0}
.screen-tt b{font-size:13.5px;font-weight:700}
.screen-tt i{font-style:normal;font-size:11.5px;color:#757575}
.screen-tt em{font-style:normal;font-size:10.5px;color:#C62828;background:#FDECEA;border-radius:8px;padding:2px 7px;margin-top:5px;align-self:flex-start}
.screen img{width:100%;height:auto;display:block;border-radius:16px;border:1px solid #EFF3EF;background:#F7F8FA}
table.cmp{width:100%;border-collapse:collapse;font-size:13px}
table.cmp th{background:#F0F6EF;color:#33502F;font-weight:700;padding:10px 12px;text-align:left}
table.cmp td{border-top:1px solid #EEF3EC;padding:10px 12px;vertical-align:top;color:#424242}
table.cmp td.del{color:#C62828;font-weight:600}
table.cmp td.new{color:#2E7D32;font-weight:600}
ul.notes{list-style:none;display:grid;grid-template-columns:repeat(auto-fill,minmax(320px,1fr));gap:10px 26px}
ul.notes li{font-size:13.5px;color:#424242;padding-left:20px;position:relative}
ul.notes li:before{content:"";position:absolute;left:4px;top:9px;width:6px;height:6px;border-radius:50%;background:#81C784}
ul.notes li b{color:#212121}
footer{margin-top:44px;text-align:center;font-size:12.5px;color:#9E9E9E}
"""

    principles = "".join(
        f'<div class="pr{" del" if d else ""}"><span class="k">{k}</span><span class="v">{v}</span></div>'
        for k, v, d in (
            ("P1 场景即主界面", "打开 App = 家。没有底部 Tab、没有功能宫格 —— 宠物的生活本身就是全部一级入口。", False),
            ("P2 手势直触", "互动发生在宠物身上：摸 = 抚摸、把食物拖过去 = 喂食。删掉「互动面板」页。", False),
            ("P3 盲盒是物件", "蛋长在家里角落，轻点开箱；不是菜单里的按钮。", False),
            ("P4 裂纹即进度", "孵化进度画在蛋壳上；四档文案代替百分比。", False),
            ("P5 管理进抽屉", "图鉴/族谱/仓库/成就/后山/设置收进右缘抽屉 —— 低频功能不配占一级入口。", False),
            ("P6 搭话驱动任务", "宠物开口要，你回应就算数。删掉「今日任务」清单页。", False),
            ("P7 货币不挂顶栏", "金币钻石收进抽屉。治愈陪伴的心智，不该被顶栏数字拉回「肝日常」。", False),
            ("P8 浮卡不跳页", "长按宠物，档案从它身上浮起；看完关掉，还在家里。", False),
            ("被删除的页面", "「互动面板」「今日任务」两个整页消失；「我的」「设置」降级为抽屉内条目。", True),
        ))

    cmp_rows = "".join(
        f'<tr><td>{o}</td><td class="{"new" if n.startswith("新增") else ("del" if n.startswith("删除") else "")}">{n}</td>'
        f'<td>{why}</td></tr>'
        for o, n, why in (
            ("01 首页 · 5-Tab + 功能宫格", "01 家 · 全屏场景", "Tab 与宫格是工具型 App 的仪表盘；家不该有仪表盘。"),
            ("06 互动面板 · 五个按钮", "02/03 手势 · 摸与拖", "把互动做成表单，是「以往习惯」最重的一处；手势控制器（Phase 1.3）本来就是为此准备的。"),
            ("08 今日任务 · 进度条清单", "07 搭话任务", "清单把陪伴变成打卡；宠物开口要，回应即完成。"),
            ("02 盲盒开箱 · 独立页", "04 蛋 · 就地揭晓", "蛋是家里的物件，开箱自然发生在家里。"),
            ("03 孵化中 · 进度条 + 加速页", "05 裂纹即进度 + 底部孵化片", "进度从数字变回可看见的东西；加速不必离开家。"),
            ("04 破壳起名", "06 起名仪式", "保留 —— 唯一值得全屏中断的仪式。"),
            ("05 宠物详情 · 独立页", "08 长按浮卡", "看一眼档案不该离开场景；顺手修正旧稿 18,370 的经验错误（实为 6,238）。"),
            ("09 我的 + 10 设置", "09 抽屉 · 家", "低频管理合并收纳；货币一起收进去。"),
            ("（无）", "10 夜晚 · 新增", "昼夜变化证明「家是活的」—— 场景不是壁纸。"),
        ))

    notes = "".join(f"<li>{x}</li>" for x in (
        "<b>数值全部对齐代码</b>：互动经验 抚摸15 / 喂食60 / 玩耍105 / 训练150 / 清洁30；每日上限 50/20/15/8/10；任务 8 项 = EconomyRules.DailyTask。旧稿「全勤 735 金币」已修正 —— 金币合计 710（签到 100 + 任务 610），735 是经验。旧稿「Lv.26 需 18,370」已修正 —— expToNextLevel(26) = 6,238。",
        "<b>任务口径</b>：每日签到 / 互动达人 / 大厨时间 / 玩伴时光 / 健身教练 / 干干净净 / 桌面陪伴 / 串门拜访（与代码逐一对应，不再自造「专注陪伴」任务）。",
        "<b>心情与玩具</b>：02 屏没有心情条（U-02 未拍板，不再擅自定 0~100）；03 屏毛线球标注「耐久会掉」对应 G-08 待排期，仅为方向示意。",
        "<b>今日进度</b>：唯一常驻 HUD 是抽屉把手上的进度环（07 屏 71% = 4,265/6,000）；顶部只有日期天气，没有货币。",
        "<b>合规红线不变</b>：不使用无障碍服务、不读取其他应用信息、不模拟点击；概率公示入口保留在 04 屏。",
        "<b>配对门槛</b>：Lv.26+ 且亲密度 ≥ 70（08 屏「亲密 78 ≥ 70」即为达标示意）。",
        "<b>对接工程</b>：Phase 1.1 需按本稿重构 —— 单 Activity 场景壳 + 手势层 + 抽屉，Tab 布局废弃（docs/09 U-10 拍板稿）。",
    ))

    html = f"""<!DOCTYPE html><html lang="zh-CN"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>萌芽宠物 SproutPet · 倒置 IA 设计稿 v2</title><style>{ARCH_CSS}</style></head><body>
<div class="wrap">
  <header class="hero">
    <h1>萌芽宠物 SproutPet · 倒置 IA 设计稿 v2</h1>
    <p>按 U-10 拍板方向重做：桌面场景即主界面，无 Tab 栏、手势直触、蛋长在家里、任务由宠物搭话驱动、管理收进抽屉。旧稿（design-export/）原样保留，可逐屏对照。数值全部取自代码，修正旧稿 2 处漂移。</p>
    <div class="chips"><span class="chip">10 屏界面</span><span class="chip">画布 390 × 844</span>
      <span class="chip">无 Tab · 无互动面板 · 无任务清单</span><span class="chip">数值对齐 EconomyRules / GrowthRules</span>
      <span class="chip">2026-10-16</span></div>
  </header>

  <h2 class="sec">设计原则</h2>
  <p class="sec-note">「打破以往 APP 操作习惯和思维」的 8 条落点 —— 每条都能在对应屏里指出来。</p>
  <div class="card"><div class="pgrid">{principles}</div></div>

  <h2 class="sec">界面一览</h2>
  <p class="sec-note">每屏右下的红签 = 它替代 / 删除了旧稿的哪一屏。</p>
  <div class="screens">{"".join(cards)}</div>

  <h2 class="sec">新旧对照</h2>
  <p class="sec-note">旧稿 10 屏 → 新稿 10 屏：两屏被删，一屏新增，其余重构为场景层。</p>
  <div class="card"><table class="cmp">
    <tr><th style="width:30%">旧稿（常规 Tab App）</th><th style="width:30%">新稿（倒置 IA）</th><th>为什么</th></tr>
    {cmp_rows}
  </table></div>

  <h2 class="sec">设计规范</h2>
  <p class="sec-note">沿用已实现的 colors.xml：品牌绿 #4CAF50 / 深 #388E3C / 浅 #C8E6C9 / 强调 #FFB300；稀有度 6 档（普通 #9E9E9E · 稀有 #2196F3 · 史诗 #9C27B0 · 传说 #FF9800 · 神话 #F44336 · 灵兽 #00E5FF）；五虫类群色与旧稿一致。</p>

  <h2 class="sec">关键设计口径</h2>
  <div class="card"><ul class="notes">{notes}</ul></div>

  <footer>萌芽宠物 SproutPet · 倒置 IA 设计稿 v2（旧稿原样保留于 design-export/）　|　2026-10-16</footer>
</div></body></html>"""

    out = os.path.join(BASE, "萌芽宠物 SproutPet · 倒置IA设计稿 v2.html")
    with open(out, "w", encoding="utf-8") as f:
        f.write(html)
    print("archive:", out, len(html), "chars")


if __name__ == "__main__":
    render()
    build_archive()
