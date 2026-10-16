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
    <div class="chipctx abs" style="left:14px;top:50px">在家 <i>6 / 6</i> · 待命 3</div>
    {bubble("肚子有点饿了…", 30, 162, act="把食物拖给它", width=186)}
    {pet("小豆芽", 26, 28, 292, 84, "chick", "happy", badge="主宠")}
    {pet("瞌睡猫", 31, 150, 288, 84, "cat", "sleep")}
    {zzz(226, 288)}
    {pet("绒绒犬", 18, 272, 294, 84, "dog", "happy")}
    {pet("慢慢", 22, 22, 452, 100, "dog", "happy")}
    {pet("小蓝", 9, 145, 456, 100, "chick", "happy")}
    {pet("团子", 14, 268, 454, 100, "cat", "happy")}
    <div class="eggtag abs" style="left:296px;top:626px">{egg_svg(72, 0)}<span class="lb">新蛋 · 轻点看看</span></div>
    <div class="foot-hint">摸摸它 · 把食物<b>拖过去</b> · 长按看档案　·　家最多站 <b>6</b> 只</div>
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
        ("🎒", "#FFF3E0", "仓库", "道具 23 件 · 盲盒 ×3", ""),
        ("🛍️", "#FFF8E1", "商店 · 盲盒", "今日免费 2 抽 · 金币 300 日限 1", '<span class="cnt">2</span>'),
        ("📱", "#E3F2FD", "桌面宠物", "桌面 3 只 · 主宠恒占 1 号 · 可换可收回", '<span class="cnt">3/3</span>'),
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


# ---------------------------------------------------------------- 11 家庭成员 · 名册
def s11():
    css = """
    .seg{font-size:10.5px;font-weight:700;color:#9AA79A;letter-spacing:1.4px;margin:12px 2px 6px}
    .prow{display:flex;align-items:center;gap:10px;padding:8px 8px;border-radius:14px;background:#F5F9F4;margin-bottom:5px}
    .prow.off{background:#F8F9F8}
    .prow .av{flex:0 0 32px;height:32px;border-radius:11px;display:flex;align-items:center;justify-content:center;font-size:16px}
    .prow.off .av{background:#EFF2EF;filter:grayscale(.45)}
    .prow .tx{flex:1;min-width:0}
    .prow .tx b{display:block;font-size:12.5px;font-weight:700;color:#2A3A28}
    .prow.off .tx b{color:#6B7F68}
    .prow .tx i{font-style:normal;font-size:10px;color:#94A490}
    .prow .go{flex:0 0 auto;padding:5px 11px;border-radius:11px;font-size:10.5px;font-weight:700;background:#EEF5EE;color:#2E7D32}
    .prow .go.crown{background:#FFF3D6;color:#9A6A00}
    .cnt3{display:flex;gap:7px;margin:8px 0 2px}
    .cnt3 .c{flex:1;padding:8px 10px;border-radius:14px;background:#F4F7F4}
    .cnt3 .c.hi{background:#EAF6EA}
    .cnt3 .c i{font-style:normal;font-size:9.5px;color:#7C8E79}
    .cnt3 .c b{display:block;font-size:16.5px;font-weight:800;color:#5B6B58}
    .cnt3 .c.hi b{color:#2E7D32}
    .cnt3 .c b s{font-size:10.5px;font-weight:600;color:#7C8E79;text-decoration:none}
    """
    home = "".join(
        f'<div class="prow"><span class="av" style="background:#FFF3E0">{em}</span>'
        f'<span class="tx"><b>{n}</b><i>{sub}</i></span>{act}</div>'
        for em, n, sub, act in (
            ("🐣", "小豆芽", "Lv.26 · 羽虫 · 主宠 · 桌面 1 号", '<span class="go crown">👑</span>'),
            ("🐱", "瞌睡猫", "Lv.31 · 毛虫 · 桌面 2 号", '<span class="go">收起</span>'),
            ("🐶", "绒绒犬", "Lv.18 · 毛虫 · 桌面 3 号", '<span class="go">收起</span>'),
            ("🐢", "慢慢", "Lv.22 · 鳞虫 · 在家", '<span class="go">收起</span>'),
            ("🦋", "小蓝", "Lv.9 · 昆虫 · 在家", '<span class="go">收起</span>'),
            ("🐲", "团子", "Lv.14 · 鳞虫 · 在家", '<span class="go">收起</span>'),
        ))
    bench = "".join(
        f'<div class="prow off"><span class="av">{em}</span>'
        f'<span class="tx"><b>{n}</b><i>{sub}</i></span>'
        f'<span class="go">放出</span></div>'
        for em, n, sub in (
            ("🥚", "糖球", "Lv.4 · 羽虫 · 待命中"),
            ("🐭", "芝麻", "Lv.11 · 毛虫 · 待命中"),
            ("🐸", "青豆", "Lv.7 · 鳞虫 · 待命中"),
        ))
    return css, f"""
    <img class="bg" src="{BG_DAY}"><div class="modal-mask"></div>
    <div class="drawer">
      <div class="dtitle">家庭成员</div>
      <div class="cnt3">
        <div class="c hi"><i>在家</i><b>6 <s>/ 6</s></b></div>
        <div class="c"><i>待命</i><b>3</b></div>
        <div class="c"><i>全部拥有</i><b>9</b></div>
      </div>
      <div class="seg">在家 · 6 / 6（家最多同时站 6 只）</div>
      {home}
      <div class="seg">待命 · 3（数据完整保留，随时可放出）</div>
      {bench}
      <div style="margin-top:10px;padding:9px 11px;border-radius:14px;background:#FFF7E6;
        font-size:10.5px;color:#8A6100;line-height:1.55">
        在家满 6 只时点「放出」—— <b>不报错</b>，而是问「<b>收起谁</b>」。<br>
        拥有不限 · 收藏永远不会因为"放不下"被拒绝。
      </div>
    </div>
    """


# ---------------------------------------------------------------- 12 上桌 · 3 个位置
def s12():
    css = """
    .drawer.snug{bottom:auto;top:18px;max-height:calc(100% - 36px);overflow:hidden}
    .slot{display:flex;align-items:center;gap:10px;padding:10px 11px;border-radius:17px;
      background:#F5F9F4;border:1.5px solid #E4EFE2;margin-bottom:8px}
    .slot.lock{background:#FFFBF0;border-color:#F1E3C2}
    .slot .no{flex:0 0 24px;height:24px;border-radius:8px;background:#E8F5E9;color:#2E7D32;
      font-size:11px;font-weight:800;display:flex;align-items:center;justify-content:center}
    .slot.lock .no{background:#FFE9B8;color:#8A6100}
    .slot .av{flex:0 0 34px;height:34px;border-radius:12px;background:#FFF3E0;display:flex;
      align-items:center;justify-content:center;font-size:17px}
    .slot .tx{flex:1;min-width:0}
    .slot .tx b{display:block;font-size:12.5px;font-weight:700;color:#2A3A28}
    .slot .tx i{font-style:normal;font-size:10px;color:#94A490}
    .slot .go{flex:0 0 auto;padding:5px 11px;border-radius:11px;font-size:10.5px;font-weight:700;
      background:#EEF5EE;color:#2E7D32}
    .pick{display:flex;align-items:center;gap:10px;padding:9px 11px;border-radius:16px;
      background:#fff;border:2px solid #FFB300;box-shadow:0 6px 16px rgba(255,179,0,.2)}
    .mini{display:inline-block;padding:5px 9px;border-radius:10px;background:#fff;
      font-size:10px;font-weight:700;color:#5B6B58;margin:6px 5px 0 0}
    """
    return css, f"""
    <img class="bg" src="{BG_NIGHT}"><div class="modal-mask"></div>
    <div class="drawer snug">
      <div class="dtitle">桌面宠物</div>
      <div style="font-size:11.5px;color:#5B6B58;margin:7px 2px 11px;line-height:1.55">
        手机桌面最多 <b>3 只</b>（每只 = 一个系统悬浮窗）<br>
        <span style="font-size:10px;color:#94A490">只能从「在家」的 6 只里选 —— <b>桌面 ⊂ 在家</b></span>
      </div>
      <div class="slot lock"><span class="no">1</span><span class="av">🐣</span>
        <span class="tx"><b>小豆芽</b><i>Lv.26 · 主宠，恒定占 1 号</i></span>
        <span class="go">👑</span></div>
      <div class="slot"><span class="no">2</span><span class="av">🐱</span>
        <span class="tx"><b>瞌睡猫</b><i>Lv.31 · 正在桌面上</i></span>
        <span class="go">回家</span></div>
      <div class="slot"><span class="no">3</span><span class="av">🐶</span>
        <span class="tx"><b>绒绒犬</b><i>Lv.18 · 正在桌面上</i></span>
        <span class="go">回家</span></div>

      <div class="seg" style="font-size:10.5px;font-weight:700;color:#9AA79A;letter-spacing:1.4px;margin:16px 2px 6px">想让它也上桌 · 从家里的另外 3 只里挑</div>
      <div class="pick"><span style="font-size:18px">🐢</span>
        <span style="flex:1;min-width:0"><b style="display:block;font-size:12.5px;color:#2A3A28">慢慢</b>
          <i style="font-style:normal;font-size:10px;color:#94A490">Lv.22 · 鳞虫 · 在家</i></span>
        <span style="padding:6px 12px;border-radius:12px;background:#FFB300;color:#5D3F00;
          font-size:11px;font-weight:800">上桌</span></div>

      <div style="margin-top:13px;padding:11px;border-radius:15px;background:#FFF7E6;
        font-size:10.5px;color:#8A6100;line-height:1.55">
        3 个位置都满了 —— 不问"已达上限"，问：<br>
        <b style="font-size:12.5px">「换谁下来？」</b><br>
        <span class="mini">换下 瞌睡猫</span><span class="mini">换下 绒绒犬</span>
        <span class="mini" style="color:#9AA79A">算了</span>
      </div>
    </div>
    """


# ---------------------------------------------------------------- 13 抽蛋 · 宠物叼回来
def s13():
    css = '''
    .foot-hint.solid{background:rgba(255,255,255,.86);backdrop-filter:blur(6px);
      border-radius:16px;left:14px;right:14px;bottom:16px;padding:9px 12px;
      color:#33502F;text-shadow:none}
    .foot-hint.solid b{color:#2E5B2A}
    '''
    box = ('<svg viewBox="0 0 100 100" width="106" height="106">'
           '<ellipse cx="50" cy="94" rx="25" ry="5" fill="rgba(30,55,30,.18)"/>'
           '<path d="M20 44 h60 v42 a6 6 0 0 1 -6 6 H26 a6 6 0 0 1 -6 -6 z" fill="#F2DCB8"/>'
           '<path d="M20 44 l9 -19 h42 l9 19 z" fill="#E6CB9E"/>'
           '<rect x="15" y="58" width="70" height="12" rx="3" fill="#DBB57F"/>'
           '<path d="M50 33 v52" stroke="#C79E6A" stroke-width="3"/>'
           '<circle cx="50" cy="64" r="8" fill="#FFF3C4" stroke="#E0A93C" stroke-width="2.5"/>'
           '<path d="M50 58 v12 M44 64 h12" stroke="#E0A93C" stroke-width="2.5" stroke-linecap="round"/>'
           '</svg>')
    return css, f'''
    <img class="bg" src="{BG_DAY}">{DAYCHIP}{drawer_handle(pct=42)}
    <div class="chipctx abs" style="left:14px;top:58px">今日免费抽数<i>2 / 2</i></div>
    {rays(150, 118, 150, "rgba(255,236,170,.45)")}
    {pet("瞌睡猫", 31, 34, 210, 104, "cat", "joy")}
    <div class="abs" style="left:150px;top:262px">{box}</div>
    <div class="eggtag abs" style="left:126px;top:372px"><span class="lb">神秘盲盒 · 仓库 3 个</span></div>
    {bubble("我今天叼到一个盒子回来！", 122, 150, tail="left", act="打开看看")}
    {pet("绒绒犬", 18, 250, 560, 96, "dog", "happy")}
    {bubble("我也想看看里面是什么", 160, 552, tail="right", done=True)}
    <div class="foot-hint solid">
      抽蛋入口<b>就在家里</b> —— 没有抽奖页、没有菜单二级入口<br>
      <span style="font-size:10.5px">宠物叼回来 / 角落的快递箱长按即开；签到与任务全清各送 1 抽</span></div>
    '''


# ---------------------------------------------------------------- 14 开箱台（底部升起）
def s14():
    css = '''
    .meter{height:7px;border-radius:4px;background:#EDF3EC;overflow:hidden;margin:7px 0 3px}
    .meter b{display:block;height:100%;border-radius:4px;background:linear-gradient(90deg,#FFB300,#FF8F00)}
    .pityrow{padding:11px 13px;border-radius:16px;background:#FFFBF0;border:1.5px solid #F1E3C2;margin-bottom:4px}
    .pityrow .t{display:flex;align-items:baseline;gap:7px;font-size:12.5px;font-weight:700;color:#8A6100}
    .pityrow .t i{font-style:normal;font-weight:500;font-size:10.5px;color:#B08A3A}
    .pityrow .d{font-size:10px;color:#A08341;line-height:1.5}
    .lawline{display:flex;align-items:center;gap:7px;padding:10px 13px;border-radius:14px;
      background:#F6F8F6;font-size:11px;color:#6B7F68;margin-top:9px}
    .lawline b{color:#44553F}
    '''
    return css, f'''
    <img class="bg" src="{BG_DAY}"><div class="modal-mask"></div>
    {pet("小豆芽", 26, 40, 150, 116, "chick", "joy", badge="主宠")}
    {rays(96, 116, 160, "rgba(255,236,170,.4)")}
    <div class="sheet">
      <div class="grip"></div>
      <h3>神秘盲盒 <i>今日免费还剩 1 抽</i></h3>
      <div style="font-size:11px;color:{C["hint"]};margin:4px 0 11px">签到 1 抽 + 任务全清 1 抽 = 每天 2 抽免费；金币只是补票口</div>

      <div class="pityrow">
        <div class="t">距「史诗」保底还差 12 抽 <i>最多 30 抽必出</i></div>
        <div class="meter"><b style="width:60%"></b></div>
        <div class="d">⚠️ 保底只到史诗 · 「传说 / 神话」没有保底通道 · 综合概率（含保底）10.42%</div>
      </div>

      <div class="srow"><span class="ic" style="background:#E8F5E9">🎁</span>
        <span class="tx"><b>免费开 1 次</b><i>今日签到额度 · 0 金币</i></span>
        <span class="go">开箱</span></div>
      <div class="srow"><span class="ic" style="background:#FFF3E0">💰</span>
        <span class="tx"><b>金币补 1 个</b><i>300 金币 · 今日 0 / 1（日限 1，钱买不来无限抽）</i></span>
        <span class="go warn">补票</span></div>
      <div class="srow"><span class="ic" style="background:#E0F2F1">💎</span>
        <span class="tx"><b>钻石开 10 次</b><i>600 钻石 · 一次开完，结果横排呈现</i></span>
        <span class="go">十连</span></div>
      <div class="srow"><span class="ic" style="background:#F6EBFA">📦</span>
        <span class="tx"><b>仓库囤的盲盒 ×3</b><i>可囤券 —— 攒够 10 个再抽，一次开完更热闹</i></span>
        <span class="go">十连</span></div>

      <div class="lawline">🛡️ <span><b>概率公示</b> · 各档位概率与保底规则，含保底综合概率 10.42%</span>
        <span style="margin-left:auto;color:#B9C6B6">›</span></div>
    </div>
    '''


# ---------------------------------------------------------------- 15 十连 · 结果横排
def s15():
    css = '''
    .rgrid{display:grid;grid-template-columns:repeat(5,1fr);gap:7px;margin:12px 0 6px}
    .rc{border-radius:14px;padding:9px 2px 7px;text-align:center;background:#F5F9F4;
      border:1.5px solid #E4EFE2}
    .rc .em{font-size:21px;line-height:1.05}
    .rc .lb{display:block;font-size:8.5px;font-weight:800;margin-top:5px}
    .rc.up{background:#FFF7E6;border-color:#F1D79B}
    .sum{display:flex;gap:6px;margin:8px 0 0}
    .sum .c{flex:1;padding:8px 6px;border-radius:13px;background:#F4F7F4;text-align:center}
    .sum .c i{display:block;font-style:normal;font-size:9.5px;color:#7C8E79}
    .sum .c b{font-size:15px;font-weight:800;color:#3A4A36}
    '''
    cards = "".join(
        f'<div class="rc{" up" if up else ""}"><span class="em">{em}</span>'
        f'<span class="lb" style="color:{col}">{lb}</span></div>'
        for em, lb, col, up in (
            ("🐣", "史诗", "#9C27B0", False), ("🐱", "普通", "#9E9E9E", False),
            ("🐶", "稀有", "#2196F3", False), ("🐢", "普通", "#9E9E9E", False),
            ("🐛", "稀有", "#2196F3", False), ("🐝", "普通", "#9E9E9E", False),
            ("🐣", "普通", "#9E9E9E", False), ("🐱", "稀有", "#2196F3", False),
            ("🐶", "普通", "#9E9E9E", False), ("🐢", "稀有", "#2196F3", True),
        ))
    return css, f'''
    <img class="bg" src="{BG_DAY}"><div class="modal-mask"></div>
    {rays(96, 92, 150, "rgba(255,236,170,.4)")}
    <div class="card abs" style="bottom:34px;padding:20px 20px 18px">
      <div style="display:flex;align-items:baseline;gap:9px;margin-bottom:3px">
        <span style="font-size:19px;font-weight:800;color:#26382A">十连结果</span>
        <span style="font-size:11.5px;color:{C["hint"]}">结果横排一次呈现，不用一张张点</span>
      </div>
      <div class="rgrid">{cards}</div>
      <div class="sum">
        <div class="c"><i>史诗及以上</i><b style="color:#9C27B0">1</b></div>
        <div class="c"><i>稀有</i><b style="color:#2196F3">4</b></div>
        <div class="c"><i>普通</i><b>5</b></div>
        <div class="c"><i>保底计数</i><b>0</b></div>
      </div>
      <div style="margin-top:11px;padding:10px 12px;border-radius:14px;background:#FFF7E6;
        font-size:10.5px;color:#8A6100;line-height:1.55">
        第 10 只被「<b>十连保底</b>」提升为<b>稀有</b> —— 这一批本来会是全普通。<br>
        兜底<b>只抬到稀有</b>：保「史诗」会让史诗+ 产出率涨 35%，把稀缺感抹平，所以不做。
      </div>
      <div style="margin-top:13px"><span class="btn">10 只都带回家</span></div>
      <div style="margin-top:9px;text-align:center;font-size:10.5px;color:{C["hint"]}">
        拿到重复宠？去家族里<b>融合</b>—— 抽卡是素材，生育才是上限（灵兽不可抽）</div>
    </div>
    '''


# ---------------------------------------------------------------- 渲染 + 归档

# ---------------------------------------------------------------- 16 桌宠引导 · 宠物自己开口
def s16():
    css = '''
    .ask{position:absolute;left:14px;right:14px;bottom:20px;background:rgba(255,255,255,.95);
      backdrop-filter:blur(8px);border-radius:20px;padding:14px 15px 13px;
      box-shadow:0 10px 26px rgba(30,60,30,.16)}
    .ask .from{font-size:10.5px;font-weight:800;color:#2E7D32;letter-spacing:.5px}
    .ask .q{font-size:14px;font-weight:700;color:#24391F;line-height:1.55;margin:6px 0 12px}
    .ask .q i{font-style:normal;font-size:10.5px;font-weight:500;color:#8CA089;display:block;
      margin-top:6px;line-height:1.5}
    .ask .btns{display:flex;gap:8px}
    .ask .btns b{flex:1;text-align:center;padding:11px;border-radius:14px;font-size:12.5px}
    .ask .yes{background:#4CAF50;color:#fff;box-shadow:0 5px 14px rgba(76,175,80,.32)}
    .ask .no{background:#EEF5EE;color:#5B6B58}
    .notpage{position:absolute;left:14px;top:56px;right:14px;background:rgba(255,255,255,.88);
      border-radius:14px;padding:9px 12px;font-size:10.5px;color:#5B6B58;line-height:1.55}
    .notpage b{color:#2E7D32}
    '''
    return css, f'''
    <img class="bg" src="{BG_DAY}">{DAYCHIP}{drawer_handle(pct=48)}
    <div class="notpage">⭐ 这一步<b>没有权限清单页</b>、没有「下一步」向导 ——
      是<b>宠物自己开口请求</b>（复用 00 §4.4 主动搭话）</div>
    {rays(146, 168, 156, "rgba(255,236,170,.42)")}
    {pet("小豆芽", 26, 132, 252, 118, "chick", "joy", badge="👑")}
    {bubble("我想去你的桌面上住～", 92, 206, tail="left")}
    <div class="ask">
      <div class="from">小豆芽 · 主宠</div>
      <div class="q">「我想去你的桌面上住，这样你刷手机的时候也能看到我。」
        <i>桌面是它的第二个家 —— 你在别的 App 里忙，它也在旁边待着</i></div>
      <div class="btns"><b class="yes">好啊，带它去</b><b class="no">以后再说</b></div>
    </div>
    '''


# ---------------------------------------------------------------- 17 桌宠授权 · 跳系统设置页
def s17():
    css = '''
    .sys{position:absolute;left:12px;right:12px;top:58px;bottom:126px;background:#F7F8F7;
      border-radius:22px;padding:14px;box-shadow:0 14px 34px rgba(20,40,20,.24);overflow:hidden}
    .sbar{display:flex;align-items:center;gap:7px;font-size:11px;color:#7A8A78;margin-bottom:12px}
    .sbar b{color:#2A3A28;font-size:12px}
    .srow{display:flex;align-items:center;gap:10px;padding:13px 12px;background:#fff;border-radius:14px;
      border:1px solid #E8EDE7;margin-bottom:9px}
    .srow .tx{flex:1;min-width:0}
    .srow .tx b{display:block;font-size:12.5px;color:#24391F}
    .srow .tx i{font-style:normal;font-size:10px;color:#94A490;line-height:1.5;display:block;margin-top:3px}
    .wico{flex:0 0 22px;height:17px;border:2.5px solid #5B6B58;border-radius:4px;position:relative}
    .wico::before{content:"";position:absolute;left:-2.5px;right:-2.5px;top:-2.5px;height:6px;
      background:#5B6B58;border-radius:2px 2px 0 0}
    .srow .tx i .em{color:#2E7D32;font-weight:700;font-style:normal}
    .sw{flex:0 0 42px;height:24px;border-radius:13px;background:#4CAF50;position:relative}
    .sw::after{content:"";position:absolute;top:3px;right:3px;width:18px;height:18px;border-radius:50%;
      background:#fff}
    .rom{position:absolute;left:12px;right:12px;bottom:16px;background:rgba(255,255,255,.95);
      border-radius:18px;padding:12px 13px;font-size:10.5px;color:#5B6B58;line-height:1.6;
      box-shadow:0 8px 22px rgba(20,40,20,.14)}
    .rom b{color:#2E7D32}
    .rom .tag{display:inline-block;padding:3px 8px;border-radius:9px;background:#EEF5EE;font-size:10px;
      font-weight:700;color:#2E7D32;margin:5px 4px 0 0}
    '''
    return css, f'''
    <img class="bg" src="{BG_DAY}"><div class="modal-mask" style="background:rgba(18,34,20,.42)"></div>
    <div class="sys">
      <div class="sbar">⚙️ <b>系统设置</b> · 应用 · 萌芽宠物</div>
      <div class="srow"><span class="wico"></span>
        <span class="tx"><b>显示在其他应用的上层</b>
          <i>允许萌芽宠物显示在别的 App 上方 —— 这是宠物能站在桌面上的<span class='em'>唯一</span>办法</i></span>
        <span class="sw"></span></div>
      <div class="srow"><span style="font-size:19px">🔔</span>
        <span class="tx"><b>通知权限</b>
          <i>Android 8+ 要求桌宠挂一条常驻通知才不被系统杀掉。内容就是「小豆芽在你桌面上陪着你」
          —— <span class='em'>不推送广告、不打扰</span></i></span>
        <span class="sw"></span></div>
      <div style="font-size:10.5px;color:#8A968A;padding:2px 4px;line-height:1.6">
        ⚠️ 这里是<b>真的系统设置页</b>，不是我们造的假页面 —— 悬浮窗权限<b>给不了普通弹窗</b>，
        必须跳出去由用户手动打开
      </div>
    </div>
    <div class="rom">
      <b>四家 ROM 的额外一关（按机型分别引导，不写死一套文案）</b><br>
      <span class="tag">小米 · 自启动</span><span class="tag">华为 · 应用启动管理</span>
      <span class="tag">OPPO · 允许后台运行</span><span class="tag">vivo · 后台高耗电</span><br>
      <span style="color:#8A968A">另外还有 Doze 电池优化白名单（不然系统会定期清掉悬浮窗）；
      拒绝也没关系 —— 见下一屏的兜底</span>
    </div>
    '''


# ---------------------------------------------------------------- 18 桌宠 · 跳出去 + 拒绝兜底
def s18():
    css = '''
    .edge{position:absolute;top:0;bottom:0;right:0;width:3px;
      background:linear-gradient(180deg,rgba(76,175,80,0),rgba(76,175,80,.6),rgba(76,175,80,0))}
    .go{position:absolute;right:14px;top:132px;width:158px;border-radius:18px;
      background:rgba(255,255,255,.92);padding:10px 12px;font-size:10.5px;color:#5B6B58;line-height:1.6}
    .go b{color:#2E7D32;font-size:12px}
    .fallback{position:absolute;left:14px;right:180px;bottom:104px;border-radius:15px;
      background:rgba(255,251,240,.95);border:1.5px solid #F1E3C2;padding:10px 12px;
      font-size:10.5px;color:#8A6100;line-height:1.55}
    .steps{position:absolute;left:14px;right:14px;bottom:16px;display:flex;gap:7px}
    .steps .s{flex:1;background:rgba(255,255,255,.9);border-radius:15px;padding:9px 8px;
      font-size:9.5px;color:#5B6B58;line-height:1.45;text-align:center}
    .steps .s b{display:block;font-size:10.5px;color:#2E7D32;margin-bottom:3px}
    .steps .s.cur{background:#4CAF50}
    .steps .s.cur b,.steps .s.cur{color:#fff}
    '''
    return css, f'''
    <img class="bg" src="{BG_DAY}">{DAYCHIP}{drawer_handle(pct=48)}
    <div class="edge"></div>
    {rays(296, 244, 134, "rgba(255,236,170,.5)")}
    {pet("小豆芽", 26, 228, 268, 112, "chick", "joy", badge="👑")}
    <div class="go"><b>它走出去了</b><br>从「家」走向屏幕边缘 → 从边缘消失 → 出现在你的手机桌面。
      <b>是同一个它</b>，不是复制品（数据同一份、状态实时同步）</div>
    <div class="fallback">若刚才点了「<b>以后再说</b>」：宠物会说「<b>那我先在家里陪你</b>」——
      不惩罚、不反复弹窗，但抽屉里的「桌面宠物」入口<b>一直留着</b>，随时能再来一次</div>
    <div class="steps">
      <div class="s"><b>1 请求</b>宠物开口<br>（16 屏）</div>
      <div class="s"><b>2 授权</b>跳系统设置<br>（17 屏）</div>
      <div class="s cur"><b>3 跳出去</b>过渡仪式<br>（本屏）</div>
      <div class="s"><b>4 管理</b>抽屉「桌面宠物」<br>（12 屏）</div>
    </div>
    '''


SCREENS = [
    ("01", "家 · 主界面（无 Tab）", "打开 App = 家。场景即信息架构：宠物生活、搭话气泡、蛋在角落；唯一的常驻 HUD 是右缘抽屉把手（环 = 今日经验进度）。⭐ **在家最多同时站 6 只**（这里画满：两排各 3），左下角 chip 显示「在家 6/6 · 待命 3」。", "s01", "旧 01 首页（5-Tab + 功能宫格）整体作废"),
    ("02", "手势 · 抚摸", "在宠物身上长按/滑动 = 抚摸。涟漪 + 爱心 + 经验飘字就地反馈，没有「互动面板」页。", "s02", "旧 06 互动面板 → 删除"),
    ("03", "手势 · 拖拽喂食", "托盘按住拖到宠物身上 = 喂食/玩耍/训练。玩具拖过去即消耗耐久（G-08 的落点）。", "s03", "旧 06 互动面板 → 删除"),
    ("04", "蛋 · 就地揭晓", "蛋长在家里，轻点即开箱；揭晓卡浮在家的场景上。保底与概率公示仍在场。", "s04", "旧 02 盲盒开箱页 → 场景化"),
    ("05", "孵化 · 裂纹即进度", "蛋壳裂纹就是进度，四档文案随进度换。轻点蛋从底部升起孵化片，三种加速不离开家。", "s05", "旧 03 孵化中页 → 场景化"),
    ("06", "破壳 · 起名仪式", "唯一保留的模态：起名是仪式，值得一次全屏中断。档案卡与六维初值在仪式内完成。", "s06", "旧 04 破壳起名 → 保留"),
    ("07", "搭话任务", "任务不做成进度条清单：宠物开口要，你回应就算数。今日进度只在把手环与一个淡色 chip。", "s07", "旧 08 今日任务页 → 删除"),
    ("08", "长按 · 档案浮卡", "长按宠物，档案从它身上浮起；左右滑切换家人。等级经验用修正后的 6,238。", "s08", "旧 05 宠物详情页 → 浮卡化"),
    ("09", "抽屉 · 家", "图鉴/族谱/仓库/**商店 · 盲盒**/成就/后山/设置收进右缘抽屉，货币也收在这里 —— 主界面不挂金币。", "s09", "旧 09 我的 + 旧 10 设置 → 合并收纳"),
    ("10", "夜晚 · 家是活的", "昼夜随真实时间变。夜里的家自己运转：离线收益一句说明，蛋在夜里发光。", "s10", "新增屏（旧稿无对应）"),
    ("11", "家庭成员 · 名册", "「谁在家、谁待命」一目了然：**在家 6/6** 与**待命 N** 分两段，逐只可「收起 / 放出」。主入口在图鉴 / 族谱的宠物卡片上，这里只是汇总。⭐ 在家满 6 时点「放出」不问「已达上限」，问「收起谁」。", "s11", "新增屏（U-12 拍板后补）"),
    ("12", "上桌 · 3 个位置", "桌面只有 **3 个位置**，且**只能从在家的 6 只里选**（桌面 ⊂ 在家）。主宠恒定占 1 号槽位。满了不问「已达上限」，问「**换谁下来**」（槽位模型，不是布尔开关）。", "s12", "新增屏（U-12 拍板后补）"),
    ("13", "抽蛋 · 宠物叼回来", "**抽蛋入口就在家里**：宠物叼着一个盲盒走过来（或场景角落的快递箱长按即开）。没有抽奖页、没有二级菜单 —— 口袋里永远有「今天还有 2 抽免费」的确定感。", "s13", "新增屏（U-13 拍板后补）"),
    ("14", "开箱台 · 底部升起", "开箱从底部升起而不是跳页：**保底进度**（距史诗还差 12 抽）· 免费抽 · 金币补票（300 · 日限 1）· **十连**（可囤券攒够再开）· **概率公示入口**。⚠️ 公示写明「保底只到史诗，传说/神话无保底通道」。", "s14", "新增屏（U-13 拍板后补）"),
    ("15", "十连 · 结果横排", "只做十连：结果横排一次呈现。**十连保「稀有及以上」**（这里第 10 只就是被兜底抬上来的）—— 刻意**不保「史诗」**（那会让史诗+ 涨 35%）。拿到重复宠去融合，生育才是上限路径。", "s15", "新增屏（U-13 拍板后补）"),
    ("16", "桌宠引导 · 宠物自己开口", "**⭐ 这一步没有权限清单页、没有「下一步」向导** —— 是宠物自己开口请求（复用 00 §4.4 主动搭话）。它说「我想去你的桌面上住，这样你刷手机的时候也能看到我」，你只需要回答「好啊」或「以后再说」。**把系统权限翻译成一只宠物的请求**，是 U-11 拍板的核心。", "s16", "新增屏（U-11 拍板后补）"),
    ("17", "桌宠授权 · 跳系统设置页", "悬浮窗权限（`SYSTEM_ALERT_WINDOW`）**给不了普通弹窗**，必须真的跳到系统设置页由用户手动打开。同时解释**用户最反感的那件事**：Android 8+ 要求桌宠挂一条常驻通知才不被杀 —— 产品语言说清楚「它就是那句话，不是广告」。四家 ROM 各自还有自启动 / 后台白名单 / 高耗电 / Doze，**按机型分别引导**。", "s17", "新增屏（U-11 拍板后补）"),
    ("18", "桌宠 · 跳出去 + 拒绝兜底", "授权后播放「**跳出去**」过渡仪式：宠物从「家」走向屏幕边缘 → 从边缘消失 → 出现在手机桌面，**是同一个它**（数据同一份、状态实时同步）。⭐ 同屏给出**拒绝授权后的兜底**：宠物说「那我先在家里陪你」，不惩罚、不反复弹，但抽屉里的入口**一直留着** —— 不能一次拒绝就永久静默。", "s18", "新增屏（U-11 拍板后补）"),
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
            ("P9 数量有层次", "拥有不限（收藏）⊃ 在家 6（注意力）⊃ 桌面 3（技术）。上桌用「3 个位置」而不是开关 —— 满了问「换谁下来」。", False),
            ("P10 抽蛋不是页", "抽蛋是「宠物叼回来一个盒子」＋从底部升起的开箱台，不是抽奖页。门票走陪伴换抽（每天 2 抽免费），金币只是补票口且日限 1。", False),
            ("P11 只做十连", "十连保「稀有及以上」（几乎免费），刻意不保「史诗」（那会让史诗+ 涨 35%）。结果横排一次呈现，不让人一张张点。", False),
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
            ("（无）", "11 家庭成员 · 12 上桌", "U-12 拍板后补：数量上限过去只写在文档表格里，**没有任何一屏能管「谁在家 / 谁上桌」**；这两屏把它变成看得见的动作。"),
            ("（无）", "13 抽蛋入口 · 14 开箱台 · 15 十连", "U-13 拍板后补：抽蛋过去**只有算法、没有生意**（`blind_box` 甚至没有价格，也没有任何一屏能抽）；这三屏把「陪伴换抽 · 只做十连 · 公示口径」变成看得见的动作。"),
        ))

    notes = "".join(f"<li>{x}</li>" for x in (
        "<b>数值全部对齐代码</b>：互动经验 抚摸15 / 喂食60 / 玩耍105 / 训练150 / 清洁30；每日上限 50/20/15/8/10；任务 8 项 = EconomyRules.DailyTask。旧稿「全勤 735 金币」已修正 —— 金币合计 710（签到 100 + 任务 610），735 是经验。旧稿「Lv.26 需 18,370」已修正 —— expToNextLevel(26) = 6,238。",
        "<b>任务口径</b>：每日签到 / 互动达人 / 大厨时间 / 玩伴时光 / 健身教练 / 干干净净 / 桌面陪伴 / 串门拜访（与代码逐一对应，不再自造「专注陪伴」任务）。",
        "<b>心情与玩具</b>：02 屏没有心情条（U-02 未拍板，不再擅自定 0~100）；03 屏毛线球标注「耐久会掉」对应 G-08 待排期，仅为方向示意。",
        "<b>今日进度</b>：唯一常驻 HUD 是抽屉把手上的进度环（07 屏 71% = 4,265/6,000）；顶部只有日期天气，没有货币。",
        "<b>合规红线不变</b>：不使用无障碍服务、不读取其他应用信息、不模拟点击；概率公示入口保留在 04 屏。",
        "<b>配对门槛</b>：Lv.26+ 且亲密度 ≥ 70（08 屏「亲密 78 ≥ 70」即为达标示意）。",
        "<b>对接工程</b>：Phase 1.1 需按本稿重构 —— 单 Activity 场景壳 + 手势层 + 抽屉，Tab 布局废弃（docs/09 U-10 拍板稿）。",
        "<b>抽蛋（U-13 拍板 · 2026-10-16）</b>：门票 = <b>陪伴换抽</b> —— 每日免费 <b>2 抽</b>（签到 1 + 任务 8/8 全清 1）· 金币 <b>300/个 · 日限 1</b> · 钻石 60/个 · 可囤券。只做<b>十连</b>（二十/五十连不做），<b>十连保「稀有及以上」</b>、刻意<b>不保史诗</b>。30 天 60 抽 ≈ 6.25 只史诗+ ≈ 刚好填满「家在册 6 只」。公示写死「保底只到史诗 · 传说与神话没有保底通道 · 综合概率（含保底）10.42%」。数值中枢 = <b>GachaRules</b>。",
        "<b>数量口径（U-12 拍板 · 2026-10-16）</b>：<b>拥有不限 ⊃ 在家 6 ⊃ 桌面 3</b>，是<b>含包关系</b>（上桌的宠必须先在册）。「上桌」= <b>3 个槽位的名单</b>而非每只一个布尔；主宠 = 「在任何地方都优先表达的那只」、恒定占桌面 1 号槽位；入/出家动作叫「<b>放出 / 收起</b>」（与 ADR-007 的「隐居·召回」严格区分）。11 / 12 两屏就是这套口径的界面落点。",
    ))

    html = f"""<!DOCTYPE html><html lang="zh-CN"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>萌芽宠物 SproutPet · 倒置 IA 设计稿 v2</title><style>{ARCH_CSS}</style></head><body>
<div class="wrap">
  <header class="hero">
    <h1>萌芽宠物 SproutPet · 倒置 IA 设计稿 v2</h1>
    <p>按 U-10 拍板方向重做：桌面场景即主界面，无 Tab 栏、手势直触、蛋长在家里、任务由宠物搭话驱动、管理收进抽屉。旧稿（design-export/）原样保留，可逐屏对照。数值全部取自代码，修正旧稿 2 处漂移。<br>⭐ <b>v2.2 补 3 屏</b>：U-12 拍板「<b>拥有不限 ⊃ 在家 6 ⊃ 桌面 3</b>」后，01 屏改为在家 6 只，并新增 11（家庭成员名册）· 12（上桌 3 个位置）；U-13 拍板「陪伴换抽 · 只做十连 · 公示口径」后再补 13（抽蛋入口）· 14（开箱台）· 15（十连结果）。</p>
    <div class="chips"><span class="chip">15 屏界面</span><span class="chip">画布 390 × 844</span>
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
