# -*- coding: utf-8 -*-
"""倒置 IA 设计稿 v2 —— 31 屏定义 + 渲染 + 归档页构建。

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
    {pet("慢慢", 22, 22, 452, 100, "turtle", "happy")}
    {pet("小蓝", 9, 145, 456, 100, "snake", "happy")}
    {pet("团子", 14, 268, 454, 100, "dragon", "happy")}
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
      <div class="it"><span class="em">🥊</span><b>铁哑铃</b><i>×2</i></div>
    </div>
    """


# ---------------------------------------------------------------- 04 蛋 · 就地揭晓
def s04():
    kv = ('<div class="kv"><span>羽虫</span><span>公</span><span>世代 1</span>'
          '<span style="background:#FFF7E6;color:#A66F00">距史诗保底还差 12 抽</span></div>')
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
    css = '''
    .fe-hint{position:absolute;left:0;right:0;top:206px;text-align:center;font-size:12.5px;
      color:#FFF8E1;letter-spacing:.4px;text-shadow:0 1px 6px rgba(0,0,0,.45)}
    .fe-egg{position:absolute;left:105px;top:238px;width:180px;height:180px;
      filter:drop-shadow(0 18px 34px rgba(0,0,0,.42)) drop-shadow(0 0 26px rgba(255,232,150,.55))}
    .fe-cd{position:absolute;left:0;right:0;top:434px;text-align:center;
      font-size:34px;font-weight:800;letter-spacing:2px;color:#FFFDE7;
      font-variant-numeric:tabular-nums;text-shadow:0 2px 12px rgba(0,0,0,.5)}
    .fe-sub{position:absolute;left:0;right:0;top:486px;text-align:center;font-size:11.5px;color:#E8F0E4}
    .fe-crack{position:absolute;left:0;right:0;top:512px;text-align:center;font-size:10.5px;color:#C8E6C9}
    .fe-acts{position:absolute;left:24px;right:24px;top:562px;display:flex;gap:8px;justify-content:center}
    .fe-act{flex:1;text-align:center;padding:11px 0;border-radius:16px;font-size:11.5px;font-weight:700;
      background:rgba(255,255,255,.94);color:#33502F;box-shadow:0 8px 18px rgba(0,0,0,.22)}
    .fe-note{position:absolute;left:16px;right:16px;bottom:26px;font-size:9.5px;line-height:1.75;
      color:#DCEBD8;background:rgba(20,38,22,.62);border:1px solid rgba(255,255,255,.14);
      border-radius:14px;padding:10px 12px}
    .fe-note b{color:#FFF3C4}
    '''
    return css, f"""
    <img class="bg" src="{BG_DAY}">
    <div class="vig" style="background:rgba(16,32,18,.34)"></div>
    <div class="chipctx abs" style="left:14px;top:14px;background:rgba(255,255,255,.94)">🥚 首蛋 · 第一次孵化<br><i>第 2 颗起回到角落 72dp 小蛋</i></div>
    <div class="fe-hint">敲敲它，它在里面</div>
    <div class="fe-egg">{egg_svg(180, 2, glow=True)}</div>
    <div class="fe-cd">00:47</div>
    <div class="fe-sub">21:40 破壳 · 到点会提醒你</div>
    <div class="fe-crack">敲了 3 次 · 蛋壳留下 3 道细纹（破壳后仍留在场景里）</div>
    <div class="fe-acts">
      <span class="fe-act">起个名字</span>
      <span class="fe-act">专注陪伴</span>
      <span class="fe-act">去看图鉴</span>
    </div>
    <div class="fe-note">
      <b>首蛋聚焦层（14 §1.4）</b>：蛋 <b>180dp</b> · 水平居中 / 垂直 46% · 场景压暗 <b>0.28</b>；
      倒计时 <b>32sp 等宽数字</b>（最后 10 秒转暖橙）+ 绝对时刻；
      动效 = <b>呼吸 2.4s</b> / <b>每 4s 轻晃</b> / <b>数字每秒跳动</b>。
      <b>首蛋恒 1 分钟，与稀有度无关</b>（W-14-1 拍板）—— 稀有度的差异放在<b>破壳演出规格</b>里。
    </div>
    """


# ---------------------------------------------------------------- 06 破壳 · 起名仪式
def s06():
    stats = "".join(f'<div class="stat{" pw" if k == "综合" else ""}"><i>{k}</i><b>{v}</b></div>'
                    for k, v in (("体力", 12), ("力量", 9), ("敏捷", 14), ("智慧", 8), ("魅力", 13), ("综合", 19)))
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
        ("📖", "#E8F5E9", "宠物图鉴", "五虫十八种 · 已收集 8 种 / 5 类群", '<span class="cnt">8/18</span>'),
        ("🌳", "#EAF4EA", "族谱", "三代血缘 · 称谓由血缘推导（§5.1）", ""),
        ("🏡", "#EDE7F6", "家庭成员", "在家 6/6 · 待命 3 · 放出 / 收起在这里", '<span class="cnt">6/6</span>'),
        ("🎒", "#FFF3E0", "仓库", "道具 23 件 · 盲盒 ×3", ""),
        ("🛍️", "#FFF8E1", "商店 · 盲盒", "今日免费 2 抽 · 金币 300 日限 1", '<span class="cnt">2</span>'),
        ("📱", "#E3F2FD", "桌面宠物", "桌面 3 只 · 主宠恒占 1 号 · 可换可收回", '<span class="cnt">3/3</span>'),
        ("🏆", "#F6EBFA", "成就", "已点亮 12 枚", ""),
        ("⛰️", "#E0F2F1", "后山小屋", "1 位长辈隐居 · 可随时召回", ""),
        ("⚙️", "#ECEFF1", "设置 · 隐私与合规", "悬浮窗声明 · 概率公示", ""),
    ))
    return "", f"""
    <img class="bg" src="{BG_DAY}"><div class="modal-mask" style="background:rgba(22,42,24,.34)"></div>
    <style>.drawer{{overflow-y:auto}}</style>
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
            ("🐣", "小豆芽", "Lv.26 · 萌芽鸡 · 羽虫 · 主宠 · 桌面 1 号", '<span class="go crown">👑</span>'),
            ("🐱", "瞌睡猫", "Lv.31 · 瞌睡猫 · 毛虫 · 桌面 2 号", '<span class="go">收起</span>'),
            ("🐶", "绒绒犬", "Lv.18 · 绒绒犬 · 毛虫 · 桌面 3 号", '<span class="go">收起</span>'),
            ("🐢", "慢慢", "Lv.22 · 圆壳龟 · 昆虫 · 在家", '<span class="go">收起</span>'),
            ("🐍", "小蓝", "Lv.9 · 小青蛇 · 鳞虫 · 在家", '<span class="go">收起</span>'),
            ("🐲", "团子", "Lv.14 · 小龙崽 · 鳞虫 · 在家", '<span class="go">收起</span>'),
        ))
    bench = "".join(
        f'<div class="prow off"><span class="av">{em}</span>'
        f'<span class="tx"><b>{n}</b><i>{sub}</i></span>'
        f'<span class="go">放出</span></div>'
        for em, n, sub in (
            ("🐣", "糖球", "Lv.4 · 萌芽鸡 · 羽虫 · 待命中"),
            ("🐱", "芝麻", "Lv.11 · 疾风猫 · 毛虫 · 待命中"),
            ("🐸", "青豆", "Lv.7 · 泡泡蛙 · 蠃虫 · 待命中"),
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


# ---------------------------------------------------------------- 19 首启 · 隐私政策与开场
def s19():
    css = '''
    .gate{position:absolute;left:20px;right:20px;top:50%;transform:translateY(-50%);
      background:rgba(255,255,255,.97);border-radius:28px;padding:24px 22px 20px;
      box-shadow:0 24px 60px rgba(15,40,18,.35);text-align:center}
    .gate .logo{width:60px;height:60px;margin:0 auto 10px;border-radius:19px;
      background:linear-gradient(135deg,#A5D6A7,#4CAF50);display:flex;align-items:center;
      justify-content:center;font-size:30px;box-shadow:0 8px 20px rgba(76,175,80,.35)}
    .gate h1{font-size:20px;font-weight:800;color:#26382A;margin-bottom:4px}
    .gate .sub{font-size:11.5px;color:#7C8E79;margin-bottom:13px}
    .gate .pl{display:flex;flex-direction:column;gap:7px;margin-bottom:14px}
    .gate .pl div{display:flex;gap:9px;padding:9px 11px;border-radius:14px;background:#F5F9F4;
      font-size:10.5px;color:#44553F;line-height:1.55;text-align:left}
    .gate .pl b{color:#2A3A28}
    .gate .pl .no{flex:0 0 19px;height:19px;border-radius:7px;background:#E8F5E9;color:#2E7D32;
      font-weight:800;font-size:10.5px;display:flex;align-items:center;justify-content:center;margin-top:1px}
    .gate .foot{font-size:9.5px;color:#9AA79A;margin-top:9px;line-height:1.55}
    '''
    return css, f'''
    <img class="bg" src="{BG_DAY}"><div class="modal-mask" style="backdrop-filter:blur(8px)"></div>
    {rays(115, 92, 150)}
    <div class="gate">
      <div class="logo">🌱</div>
      <h1>欢迎来到萌芽宠物</h1>
      <div class="sub">在把第一颗蛋交给你之前，有几句实话要说</div>
      <div class="pl">
        <div><span class="no">1</span><span><b>悬浮窗只用来显示宠物。</b>用途声明为「桌面宠物展示」——
          不读取任何其他 App 的内容、包名、图标，也不模拟点击（§3.6 / §十二）</span></div>
        <div><span class="no">2</span><span><b>不用无障碍服务。</b>宠物「踩在图标上」是视觉效果，
          我们并不知道那是什么 App —— 我们只能「演」，不能「读」</span></div>
        <div><span class="no">3</span><span><b>概率全部公开。</b>抽蛋含保底综合概率 10.42%，
          保底只到史诗 —— 公示常驻「设置 · 隐私与合规」里（§6.3.1）</span></div>
      </div>
      <span class="btn">同意并开始</span>
      <div style="margin-top:8px"><span class="btn ghost">先看完整隐私政策</span></div>
      <div class="foot">同意之前不会初始化任何采集类 SDK、不上报任何数据（合规红线第 5 条）<br>
        这是全 App 唯一一次全屏阻断 —— 之后，处处都是「家」</div>
    </div>
    '''


# ---------------------------------------------------------------- 20 图鉴 · 五虫十八种
def s20():
    css = '''
    .dex{position:absolute;left:10px;right:10px;top:158px;bottom:14px;background:rgba(255,255,255,.97);
      backdrop-filter:blur(14px);border-radius:26px;padding:14px 16px 12px;
      box-shadow:0 -8px 40px rgba(20,45,20,.22);overflow-y:auto}
    .dex h3{font-size:16.5px;font-weight:800;color:#26382A;display:flex;align-items:baseline;gap:8px}
    .dex h3 i{font-style:normal;font-size:11px;font-weight:500;color:#7C8E79}
    .whead{display:flex;align-items:center;gap:7px;font-size:11px;font-weight:800;color:#44553F;margin:11px 2px 6px}
    .whead .d{width:9px;height:9px;border-radius:50%}
    .whead i{font-style:normal;font-weight:500;color:#94A490;font-size:9.5px;margin-left:auto}
    .cells{display:grid;grid-template-columns:repeat(3,1fr);gap:7px}
    .cell{border-radius:14px;padding:8px 2px 7px;text-align:center;background:#F5F9F4;border:1.5px solid #E4EFE2}
    .cell .em{font-size:20px;line-height:1.1}
    .cell .nm{display:block;font-size:9.5px;font-weight:700;color:#33422F;margin-top:4px}
    .cell .sp{display:block;font-size:8.5px;color:#94A490;margin-top:1px}
    .cell.no{background:#F4F6F4;border-style:dashed;border-color:#DDE5DC}
    .cell.no .em{color:#C4CEC2;font-size:15px;font-weight:800;line-height:1.55}
    .cell.no .nm{color:#9AA79A}
    '''
    got = {"萌芽鸡", "瞌睡猫", "绒绒犬", "圆壳龟", "小青蛇", "小龙崽", "疾风猫", "泡泡蛙"}
    emap = {"萌芽鸡": "🐣", "瞌睡猫": "🐱", "绒绒犬": "🐶", "圆壳龟": "🐢",
            "小青蛇": "🐍", "小龙崽": "🐲", "疾风猫": "🐱", "泡泡蛙": "🐸"}
    groups = [
        ("蠃虫 · 倮身无毛羽鳞甲", "#4FC3F7", "慢、黏人 / 跳脱 / 聪明", ("蜗蜗", "泡泡蛙", "软软章")),
        ("鳞虫 · 有鳞者", "#26A69A", "悠然好运 / 灵动 / 爱炫", ("锦鲤鲤", "小青蛇", "小龙崽")),
        ("毛虫 · 有毛者", "#BCAAA4", "慵懒傲娇 / 忠诚 / 警觉", ("瞌睡猫", "绒绒犬", "疾风猫")),
        ("羽虫 · 有羽者", "#FF8A65", "憨好奇 / 话多 / 爱看书", ("萌芽鸡", "元气鸡", "学者鸡")),
        ("昆虫 · 有甲者", "#9CCC65", "勤劳 / 沉稳 / 发光神秘", ("蜜蜜蜂", "圆壳龟", "闪闪萤")),
        ("不入十类 · 不可抽", "#78909C", "跨虫混血 4% / 异兽对 40%", ("星狐", "月兔", "荧波")),
    ]
    secs = ""
    for name, color, trait, sps in groups:
        cells = "".join(
            (f'<div class="cell"><span class="em">{emap[s]}</span><span class="nm">{s}</span>'
             f'<span class="sp">已收集</span></div>' if s in got else
             f'<div class="cell no"><span class="em">?</span><span class="nm">{s}</span>'
             f'<span class="sp">未遇见</span></div>')
            for s in sps)
        secs += (f'<div class="whead"><span class="d" style="background:{color}"></span>{name}'
                 f'<i>{trait}</i></div><div class="cells">{cells}</div>')
    return css, f'''
    <img class="bg" src="{BG_DAY}"><div class="modal-mask" style="background:rgba(22,42,24,.30)"></div>
    {drawer_handle()}
    <div class="dex">
      <h3>宠物图鉴 <i>已收集 8 / 18 种 · 5 / 6 类群</i></h3>
      {secs}
      <div style="margin-top:12px;padding:9px 12px;border-radius:14px;background:#FFF7E6;
        font-size:10px;color:#8A6100;line-height:1.6">
        还没见过的，都住在这 18 种里 —— 普通档抽蛋就有；<b>「不入十类」只能靠跨虫混血（4%）与异兽配对（40%）</b>；
        <b>灵兽（第 6 档）不可抽取，只能配对生育</b>（ADR-017 红线）</div>
    </div>
    '''


# ---------------------------------------------------------------- 21 族谱 · 血缘树
def s21():
    css = '''
    .tree{position:absolute;left:10px;right:10px;top:126px;bottom:168px;background:rgba(255,255,255,.97);
      backdrop-filter:blur(14px);border-radius:26px;padding:12px 12px 10px;
      box-shadow:0 -8px 40px rgba(20,45,20,.22)}
    .tree .cv{position:relative;width:100%;height:100%}
    .node{position:absolute;width:88px;text-align:center}
    .node .av{width:44px;height:44px;margin:0 auto;border-radius:16px;display:flex;align-items:center;
      justify-content:center;font-size:22px;background:#FFF3E0;border:2.5px solid #E4EFE2}
    .node.anc .av{border-color:#FFB300;background:#FFF7E6}
    .node.npc .av{border-style:dashed;border-color:#90CAF9;background:#E3F2FD}
    .node .nm{display:block;font-size:10.5px;font-weight:700;color:#2A3A28;margin-top:4px}
    .node .lv{display:block;font-size:8.5px;color:#94A490;margin-top:1px}
    .node .tag{display:inline-block;margin-top:3px;padding:1.5px 7px;border-radius:7px;
      font-size:8px;font-weight:700;background:#EEF5EE;color:#2E7D32}
    .node.anc .tag{background:#FFF3D6;color:#9A6A00}
    .node.npc .tag{background:#E3F2FD;color:#1565C0}
    .tstats{position:absolute;left:14px;right:14px;bottom:88px;display:flex;gap:8px}
    .tstats .c{flex:1;padding:9px 8px;border-radius:14px;background:#F4F7F4;text-align:center}
    .tstats .c i{display:block;font-style:normal;font-size:9.5px;color:#7C8E79}
    .tstats .c b{font-size:14.5px;font-weight:800;color:#3A4A36}
    .tnote{position:absolute;left:14px;right:14px;bottom:148px;padding:8px 11px;border-radius:13px;
      background:#F0F7EF;font-size:9.5px;color:#3C4F39;line-height:1.55}
    '''
    return css, f'''
    <img class="bg" src="{BG_DAY}"><div class="modal-mask" style="background:rgba(22,42,24,.30)"></div>
    {drawer_handle()}
    <div class="tree"><div class="cv">
      <svg width="366" height="440" style="position:absolute;left:0;top:0">
        <path d="M183 106 L96 190" stroke="#B9D8B6" stroke-width="2.5" fill="none"/>
        <path d="M96 224 L276 224" stroke="#FFD54F" stroke-width="2.5" stroke-dasharray="5 6" fill="none"/>
        <path d="M183 234 L52 372 M183 234 L183 372 M183 234 L314 372" stroke="#B9D8B6" stroke-width="2.5" fill="none"/>
      </svg>
      <div class="node anc" style="left:139px;top:14px"><span class="av">🐔</span>
        <span class="nm">老陈</span><span class="lv">Lv.60 · 萌芽鸡 · 传说</span>
        <span class="tag">先辈 · 隐居中</span></div>
      <div class="node" style="left:52px;top:190px"><span class="av">🐣</span>
        <span class="nm">小豆芽</span><span class="lv">Lv.26 · 主宠</span><span class="tag">世代 2</span></div>
      <div class="node npc" style="left:232px;top:190px"><span class="av">🐔</span>
        <span class="nm">小婉</span><span class="lv">Lv.28 · 学者鸡 · NPC</span><span class="tag">生育搭档</span></div>
      <div class="node" style="left:8px;top:372px"><span class="av">🐣</span>
        <span class="nm">豆苗</span><span class="lv">Lv.12 · 羽虫</span><span class="tag">世代 3</span></div>
      <div class="node" style="left:139px;top:372px"><span class="av">🐣</span>
        <span class="nm">豆豆</span><span class="lv">Lv.8 · 羽虫</span><span class="tag">世代 3</span></div>
      <div class="node" style="left:270px;top:372px"><span class="av">🐲</span>
        <span class="nm">小芽芽</span><span class="lv">Lv.5 · 跨虫 4% 彩蛋</span><span class="tag">世代 3</span></div>
    </div></div>
    <div class="tnote">称谓由<b>血缘</b>推导（§5.1）：老陈是小豆芽的「爸爸」；小婉只是<b>「生育搭档」</b>——
      配对关系不进称谓系统，取消配对也不会乱。🟡 虚线 = 配对，实线 = 血缘</div>
    <div class="tstats">
      <div class="c"><i>总世代</i><b>3</b></div>
      <div class="c"><i>总后代</i><b>5</b></div>
      <div class="c"><i>最大贡献血统</i><b style="color:#9A6A00">老陈</b></div>
    </div>
    <div style="position:absolute;left:18px;right:18px;bottom:20px"><span class="btn">导出族谱图片</span></div>
    '''


# ---------------------------------------------------------------- 22 仓库 · 道具与盲盒
def s22():
    css = '''
    .bag{position:absolute;left:10px;right:10px;top:150px;bottom:14px;background:rgba(255,255,255,.97);
      backdrop-filter:blur(14px);border-radius:26px;padding:14px 16px 12px;
      box-shadow:0 -8px 40px rgba(20,45,20,.22);overflow-y:auto}
    .bag h3{font-size:16.5px;font-weight:800;color:#26382A;display:flex;align-items:baseline;gap:8px}
    .bag h3 i{font-style:normal;font-size:11px;font-weight:500;color:#7C8E79}
    .tabs{display:flex;gap:6px;margin:10px 0 4px;flex-wrap:wrap}
    .tabs .t{padding:5px 11px;border-radius:11px;background:#F4F7F4;font-size:10px;font-weight:700;color:#5B6B58}
    .tabs .t.on{background:#4CAF50;color:#fff}
    .dur{height:4.5px;border-radius:3px;background:#EDF3EC;overflow:hidden;margin-top:5px;width:150px}
    .dur b{display:block;height:100%;background:linear-gradient(90deg,#FFB300,#FF8F00)}
    .brow{display:flex;align-items:center;gap:11px;padding:10px 2px;border-bottom:1px solid #EDF3EC}
    .brow:last-of-type{border-bottom:none}
    .brow .ic{flex:0 0 38px;height:38px;border-radius:12px;display:flex;align-items:center;
      justify-content:center;font-size:18px;background:#F5F9F4}
    .brow .tx{flex:1;min-width:0}
    .brow .tx b{display:block;font-size:12.5px;font-weight:700;color:#2A3A28}
    .brow .tx i{font-style:normal;font-size:10px;color:#94A490;line-height:1.45;display:block;margin-top:2px}
    .brow .n{flex:0 0 auto;font-size:12px;font-weight:800;color:#5B6B58}
    '''
    rows = "".join(f'<div class="brow"><span class="ic" style="background:{bg}">{em}</span>'
                   f'<span class="tx"><b>{t}</b><i>{d}</i>{dur}</span><span class="n">×{n}</span></div>'
                   for em, bg, t, d, n, dur in (
        ("⏳", "#E8F5E9", "沙漏碎片", "孵化 · 每个加速 1 小时", 3, ""),
        ("🌾", "#FFF3E0", "谷物饲料", "食物 · 喂食经验 +60 · 亲密 +3", 12, ""),
        ("🍱", "#FFF3E0", "营养便当", "食物 · 大份经验，亲密度加成更高", 2, ""),
        ("🧶", "#F6EBFA", "毛线球", "玩具 · 每次玩耍 −1 耐久（G-08 已落地）", 5,
         '<div class="dur"><b style="width:70%"></b></div>'),
        ("🥊", "#FFEBEE", "铁哑铃", "训练 · 定向提升力量", 1, ""),
        ("📚", "#E3F2FD", "知识之书", "训练 · 定向提升智慧", 1, ""),
        ("🌱", "#E8F5E9", "成长果实", "成长 · 直接加经验", 2, ""),
        ("💍", "#FCE4EC", "定情信物", "配对 · 提高真人配对接受率（NPC 免）", 1, ""),
        ("📦", "#E0F2F1", "神秘盲盒", "抽蛋门票 · 可囤 —— 攒 10 个开十连", 3, ""),
    ))
    return css, f'''
    <img class="bg" src="{BG_DAY}"><div class="modal-mask" style="background:rgba(22,42,24,.30)"></div>
    {drawer_handle()}
    <div class="bag">
      <h3>仓库 <i>道具 23 件 · 盲盒 ×3 · 六类收纳</i></h3>
      <div class="tabs"><span class="t on">全部</span><span class="t">孵化</span><span class="t">食物</span>
        <span class="t">玩具</span><span class="t">训练</span><span class="t">成长</span><span class="t">配对</span></div>
      {rows}
      <div style="margin-top:11px;padding:9px 12px;border-radius:14px;background:#FFF7E6;
        font-size:10px;color:#8A6100;line-height:1.6">
        道具回收 = <b>售价 50%</b>（§6.1）。⚠️ <b>宠物不可售卖</b> —— 重复宠物的去处是融合（26 屏），不是变现</div>
    </div>
    '''


# ---------------------------------------------------------------- 23 商店 · 道具与补给
def s23():
    css = '''
    .shop{position:absolute;left:10px;right:10px;top:140px;bottom:14px;background:rgba(255,255,255,.97);
      backdrop-filter:blur(14px);border-radius:26px;padding:14px 16px 12px;
      box-shadow:0 -8px 40px rgba(20,45,20,.22);overflow-y:auto}
    .shop h3{font-size:16.5px;font-weight:800;color:#26382A;display:flex;align-items:baseline;gap:8px}
    .shop h3 i{font-style:normal;font-size:11px;font-weight:500;color:#7C8E79}
    .ssec{font-size:10.5px;font-weight:800;color:#9AA79A;letter-spacing:1.2px;margin:12px 2px 6px}
    .grow{display:flex;align-items:center;gap:11px;padding:9px 2px;border-bottom:1px solid #EDF3EC}
    .grow:last-of-type{border-bottom:none}
    .grow .ic{flex:0 0 36px;height:36px;border-radius:12px;display:flex;align-items:center;
      justify-content:center;font-size:17px;background:#F5F9F4}
    .grow .tx{flex:1;min-width:0}
    .grow .tx b{display:block;font-size:12.5px;font-weight:700;color:#2A3A28}
    .grow .tx i{font-style:normal;font-size:10px;color:#94A490;display:block;margin-top:2px}
    .buy{flex:0 0 auto;padding:6px 12px;border-radius:12px;font-size:10.5px;font-weight:800;
      background:#E8F5E9;color:#2E7D32}
    .buy.gem{background:#E0F2F1;color:#00838F}
    .gems{display:flex;gap:7px;margin-top:4px}
    .gems .g{flex:1;padding:9px 4px;border-radius:14px;background:#F4F7F4;text-align:center}
    .gems .g i{display:block;font-style:normal;font-size:9px;color:#7C8E79}
    .gems .g b{font-size:12.5px;font-weight:800;color:#00838F}
    '''
    return css, f'''
    <img class="bg" src="{BG_DAY}"><div class="modal-mask" style="background:rgba(22,42,24,.30)"></div>
    {drawer_handle()}
    <div class="shop">
      <h3>商店 <i>钱包：💰 12,480 · 💎 86</i></h3>

      <div class="ssec">抽蛋门票（开箱就在家里 —— 13/14 屏）</div>
      <div class="grow"><span class="ic">📦</span>
        <span class="tx"><b>神秘盲盒</b><i>金币 300 / 个 · 日限 1 —— 钱买不来无限抽</i></span>
        <span class="buy">买入</span></div>
      <div class="grow"><span class="ic">📦</span>
        <span class="tx"><b>神秘盲盒</b><i>钻石 60 / 个 · 无日限 · 可囤券攒十连</i></span>
        <span class="buy gem">60 💎</span></div>

      <div class="ssec">道具（与 §6.2 道具表逐一对应）</div>
      <div class="grow"><span class="ic">🌾</span>
        <span class="tx"><b>谷物饲料</b><i>喂食 · 经验 +60 · 亲密 +3</i></span><span class="buy">20</span></div>
      <div class="grow"><span class="ic">🍱</span>
        <span class="tx"><b>营养便当</b><i>大份经验 · 亲密度加成更高</i></span><span class="buy">120</span></div>
      <div class="grow"><span class="ic">🧶</span>
        <span class="tx"><b>毛线球</b><i>玩具 · 耐久 20 · 每次玩耍 −1</i></span><span class="buy">80</span></div>
      <div class="grow"><span class="ic">🥏</span>
        <span class="tx"><b>飞盘</b><i>玩具 · 耐久 30 · 每次玩耍 −1</i></span><span class="buy">120</span></div>
      <div class="grow"><span class="ic">⏳</span>
        <span class="tx"><b>沙漏碎片</b><i>孵化加速 1 小时 / 个</i></span><span class="buy gem">20 💎</span></div>

      <div class="ssec">钻石充值（可选 · 不影响核心体验）</div>
      <div class="gems">
        <div class="g"><i>6 元</i><b>60 💎</b></div>
        <div class="g"><i>30 元</i><b>328 💎</b></div>
        <div class="g"><i>68 元</i><b>780 💎</b></div>
        <div class="g"><i>128 元</i><b>1,580 💎</b></div>
      </div>
      <div style="margin-top:10px;padding:9px 12px;border-radius:14px;background:#F0F7EF;
        font-size:10px;color:#3C4F39;line-height:1.6">
        🛡️ 概率全公开（§6.3.1）· 未成年人保护 · 正规支付渠道 · 所有道具都能靠任务/活动拿到</div>
    </div>
    '''


# ---------------------------------------------------------------- 24 配对广场 · 找搭档
def s24():
    css = '''
    .plaza{position:absolute;left:10px;right:10px;top:132px;bottom:14px;background:rgba(255,255,255,.97);
      backdrop-filter:blur(14px);border-radius:26px;padding:14px 16px 12px;
      box-shadow:0 -8px 40px rgba(20,45,20,.22);overflow-y:auto}
    .plaza h3{font-size:16.5px;font-weight:800;color:#26382A;display:flex;align-items:baseline;gap:8px}
    .plaza h3 i{font-style:normal;font-size:11px;font-weight:500;color:#7C8E79}
    .mine{display:flex;align-items:center;gap:10px;padding:10px 12px;border-radius:16px;
      background:#EAF6EA;border:1.5px solid #CDE8CC;margin:10px 0 8px}
    .mine .av{flex:0 0 36px;height:36px;border-radius:12px;background:#fff;display:flex;
      align-items:center;justify-content:center;font-size:18px}
    .mine .tx{flex:1;min-width:0}
    .mine .tx b{display:block;font-size:12.5px;color:#2A3A28}
    .mine .tx i{font-style:normal;font-size:10px;color:#5B8A57;display:block;margin-top:2px}
    .chk{display:grid;grid-template-columns:1fr 1fr;gap:4px 10px;padding:9px 12px;border-radius:14px;
      background:#F5F9F4;font-size:9.5px;color:#44553F;line-height:1.7}
    .chk .ok{color:#2E7D32;font-weight:700}
    .fchips{display:flex;gap:6px;margin:9px 0 3px}
    .fchips .f{padding:4px 10px;border-radius:10px;background:#F4F7F4;font-size:9.5px;font-weight:700;color:#5B6B58}
    .cand{display:flex;align-items:center;gap:10px;padding:10px 4px;border-bottom:1px solid #EDF3EC}
    .cand .av{flex:0 0 38px;height:38px;border-radius:13px;display:flex;align-items:center;
      justify-content:center;font-size:18px;background:#FFF3E0}
    .cand .tx{flex:1;min-width:0}
    .cand .tx b{display:block;font-size:12.5px;color:#2A3A28}
    .cand .tx i{font-style:normal;font-size:9.5px;color:#94A490;display:block;margin-top:2px;line-height:1.4}
    .cand .go{flex:0 0 auto;padding:6px 11px;border-radius:12px;font-size:10px;font-weight:800}
    '''
    return css, f'''
    <img class="bg" src="{BG_DAY}"><div class="modal-mask" style="background:rgba(22,42,24,.30)"></div>
    {drawer_handle()}
    <div class="plaza">
      <h3>配对广场 <i>真人优先 · 无真人补 NPC · 我的记录在底部</i></h3>
      <div class="mine"><span class="av">🐣</span>
        <span class="tx"><b>小豆芽 · Lv.26 成年 · 公 · 羽虫</b>
          <i>亲密度 78 / 70 ✓ · 距上次生育 5 天 ✓ · 已有后代 1 / 5 ✓</i></span>
        <span style="font-size:10.5px;font-weight:800;color:#2E7D32">可配对</span></div>
      <div class="chk">
        <span><span class="ok">✓</span> 双方成年（Lv.26+）</span>
        <span><span class="ok">✓</span> 亲密度 ≥ 70</span>
        <span><span class="ok">✓</span> 一公一母（NPC 永远取反）</span>
        <span><span class="ok">✓</span> 双方不同主人</span>
        <span><span class="ok">✓</span> 冷却 ≥ 3 天</span>
        <span><span class="ok">✓</span> 金币 3,000（有 12,480）</span>
      </div>
      <div class="fchips"><span class="f">物种</span><span class="f">稀有度</span>
        <span class="f">世代</span><span class="f">性别</span></div>
      <div class="cand"><span class="av">🐔</span>
        <span class="tx"><b>小婉家的 学者鸡 · Lv.28 · 母 · 羽虫 · 世代 2</b>
          <i>真人玩家「小婉」的宠 —— 发起后需对方点头才算数（§2.5）</i></span>
        <span class="go" style="background:#FFF3D6;color:#9A6A00">邀请</span></div>
      <div class="cand"><span class="av">🐍</span>
        <span class="tx"><b>小溪边的小青蛇 · Lv.24 · 母 · 鳞虫 · 世代 1</b>
          <i>NPC 托管 · 性别永远与你相反（U-09）· 免确认不阻塞</i></span>
        <span class="go" style="background:#E8F5E9;color:#2E7D32">立即配对</span></div>
      <div class="cand"><span class="av">🐸</span>
        <span class="tx"><b>池畔的泡泡蛙 · Lv.30 · 母 · 蠃虫 · 世代 3</b>
          <i>NPC 托管 · 跨虫配对：成功率 85% · 上漂 ×1.35</i></span>
        <span class="go" style="background:#E8F5E9;color:#2E7D32">立即配对</span></div>
      <div style="margin-top:11px;padding:9px 12px;border-radius:14px;background:#FFF7E6;
        font-size:10px;color:#8A6100;line-height:1.6">
        配对成功后<b>双方各得一只后代</b> —— 你的宠不会被拿走（§11.1）。称谓走血缘：对方只是「生育搭档」</div>
    </div>
    '''


# ---------------------------------------------------------------- 25 配对 · 双光效汇合
def s25():
    css = '''
    .beam{position:absolute;left:50%;top:300px;transform:translateX(-50%);width:120px;height:70px;
      background:radial-gradient(ellipse at center,rgba(255,236,160,.85) 0 30%,rgba(255,236,160,0) 72%)}
    .path{position:absolute;left:14px;right:14px;bottom:190px;display:flex;gap:8px}
    .path .p{flex:1;padding:10px 11px;border-radius:16px;font-size:10px;line-height:1.55}
    .path .p b{display:block;font-size:11px;margin-bottom:3px}
    .path .human{background:rgba(255,251,240,.95);border:1.5px solid #F1E3C2;color:#8A6100}
    .path .npc{background:rgba(232,245,233,.95);border:1.5px solid #CDE8CC;color:#2E5B2A}
    .baby{position:absolute;left:18px;right:18px;bottom:26px;background:rgba(255,255,255,.97);
      border-radius:22px;padding:15px 17px 13px;box-shadow:0 18px 44px rgba(15,40,18,.3)}
    .baby .hd{display:flex;align-items:baseline;gap:8px;margin-bottom:6px}
    .baby .hd b{font-size:15px;font-weight:800;color:#26382A}
    .baby .hd i{font-style:normal;font-size:10px;color:#94A490}
    .baby .kv{display:flex;flex-wrap:wrap;gap:6px;margin:7px 0 9px}
    .baby .kv span{padding:4px 9px;border-radius:10px;background:#F2F7F1;font-size:10px;
      font-weight:600;color:#44553F}
    .baby .rule{font-size:9.5px;color:#7C8E79;line-height:1.55}
    '''
    return css, f'''
    <img class="bg" src="{BG_DAY}"><div class="modal-mask" style="background:rgba(22,42,24,.30)"></div>
    <div class="chipctx abs" style="left:14px;top:14px">配对进行中<i>双光效汇合</i></div>
    {rays(36, 250, 130)}{rays(224, 250, 130)}
    <div class="beam"></div>
    {pet("小豆芽", 26, 28, 262, 108, "chick", "joy", badge="主宠")}
    {pet("小婉", 28, 254, 262, 108, "chick", "joy")}
    {hearts([(178,262,15,.95),(206,240,11,.7),(160,300,10,.6)])}
    <div class="path">
      <div class="p human"><b>真人 · 小婉</b>邀请已发出 —— 等对方点头才算数，可随时撤回</div>
      <div class="p npc"><b>NPC · 免确认</b>系统托管，立即开始 —— 不阻塞玩法（§2.5）</div>
    </div>
    <div class="baby">
      <div class="hd"><b>后代预览</b><i>双方各得一只 · 互不干涉</i></div>
      <div class="kv"><span>豆苗 · 羽虫</span><span>世代 2</span>
        <span style="background:#FFF7E6;color:#A66F00">稀有度以 max(父母) 为基准漂移</span></div>
      <div class="rule">基因 = 父母均值 ± 15% 变异 · 物种同虫 99% 继承（跨虫 4% 出「不入十类」）·
        <b>永不降级</b>：跨虫上漂 ×1.35，是刻意奖励社交</div>
      <div style="margin-top:10px"><span class="btn">完成 —— 各自带一只回家</span></div>
    </div>
    '''


# ---------------------------------------------------------------- 26 融合 · 重复宠的去处
def s26():
    css = '''
    .fus{position:absolute;left:10px;right:10px;top:132px;bottom:14px;background:rgba(255,255,255,.97);
      backdrop-filter:blur(14px);border-radius:26px;padding:14px 16px 12px;
      box-shadow:0 -8px 40px rgba(20,45,20,.22);overflow-y:auto}
    .fus h3{font-size:16.5px;font-weight:800;color:#26382A;display:flex;align-items:baseline;gap:8px}
    .fus h3 i{font-style:normal;font-size:11px;font-weight:500;color:#7C8E79}
    .rchips{display:flex;gap:6px;margin:9px 0}
    .rchips .r{padding:4px 10px;border-radius:10px;background:#F4F7F4;font-size:9.5px;font-weight:700;color:#5B6B58}
    .side{display:flex;gap:9px;margin-bottom:8px}
    .side .b{flex:1;padding:10px 12px;border-radius:16px}
    .side .b.keep{background:#EAF6EA;border:1.5px solid #CDE8CC}
    .side .b.feed{background:#FFF7E6;border:1.5px solid #F1E3C2}
    .side .b .lb{font-size:9.5px;font-weight:800;letter-spacing:1px;margin-bottom:5px}
    .side .b.keep .lb{color:#2E7D32}
    .side .b.feed .lb{color:#9A6A00}
    .side .b .who{display:flex;align-items:center;gap:8px}
    .side .b .who .av{flex:0 0 34px;height:34px;border-radius:12px;background:#fff;display:flex;
      align-items:center;justify-content:center;font-size:17px}
    .side .b .who b{display:block;font-size:12.5px;color:#2A3A28}
    .side .b .who i{font-style:normal;font-size:9.5px;color:#94A490}
    .imp{height:6px;border-radius:4px;background:#EDF3EC;overflow:hidden;margin-top:7px}
    .imp b{display:block;height:100%;background:linear-gradient(90deg,#AB47BC,#9C27B0)}
    .flaw{display:flex;gap:8px;margin-top:4px}
    .flaw div{flex:1;padding:9px 11px;border-radius:14px;font-size:9.5px;line-height:1.55}
    '''
    return css, f'''
    <img class="bg" src="{BG_DAY}"><div class="modal-mask" style="background:rgba(22,42,24,.30)"></div>
    {drawer_handle()}
    <div class="fus">
      <h3>融合 <i>重复宠物的去处 —— 挑一只留下，把重复的「喂」给它</i></h3>
      <div class="rchips"><span class="r">同物种</span><span class="r">都已破壳</span><span class="r">消耗 200 金币</span></div>
      <div class="side">
        <div class="b keep"><div class="lb">留下（目标）</div>
          <div class="who"><span class="av">🐣</span>
            <span><b>小豆芽</b><i>萌芽鸡 · 史诗 · 印记 4 / 12</i></span></div>
          <div class="imp"><b style="width:33%"></b></div></div>
        <div class="b feed"><div class="lb">喂给它（副宠）</div>
          <div class="who"><span class="av">🐣</span>
            <span><b>糖球</b><i>萌芽鸡 · 稀有 · 印记 +2</i></span></div>
          <div class="imp"><b style="width:50%;background:linear-gradient(90deg,#FFB300,#FF8F00)"></b></div></div>
      </div>
      <div style="padding:9px 12px;border-radius:14px;background:#F5F9F4;font-size:10px;color:#44553F;line-height:1.6">
        融合后：印记 4 → <b>6 / 12</b>（副宠稀有 = 2，溢出保留到下一档）·
        目标获得经验 = 副宠等级 × 50 = <b>200</b>（只加经验，不加属性 —— 10 §六）
        印记满即升一档：普通→(5)→稀有→(8)→史诗→(12)→传说→(20)→神话</div>
      <div class="flaw">
        <div style="background:#FFF7E6;color:#8A6100">⛰️ 副宠「<b>入后山</b>」—— 不消失，
          退出桌面与配对，仍留在族谱与图鉴；但<b>不可取回</b></div>
        <div style="background:#FFEBEE;color:#B3413C">🚫 融合<b>永远不能产出灵兽</b>（ADR-017）·
          <b>神话</b>不可作为融合目标 —— 直接拒绝，不让你白损失副宠</div>
      </div>
      <div style="margin-top:11px"><span class="btn" style="background:#AB47BC;box-shadow:0 8px 20px rgba(156,39,176,.3)">二次确认 —— 送糖球入后山</span></div>
      <div style="margin-top:7px;text-align:center;font-size:9.5px;color:#9AA79A">
        这是不可逆的操作 —— 弹窗会再确认一次，不会一键完成</div>
    </div>
    '''


# ---------------------------------------------------------------- 27 后山小屋 · 隐居与召回
def s27():
    css = '''
    .hill{position:absolute;border-radius:50%;background:linear-gradient(180deg,#8BC34A,#558B2F)}
    .mtnote{position:absolute;left:14px;right:14px;bottom:130px;background:rgba(255,255,255,.95);
      border-radius:18px;padding:12px 14px;font-size:10.5px;color:#44553F;line-height:1.65;
      box-shadow:0 10px 26px rgba(20,45,20,.18)}
    .mtnote b{color:#2E5B2A}
    .mtnote .u{display:block;margin-top:3px;font-size:9.5px;color:#8A968A}
    .mtbtn{flex:1;text-align:center;padding:13px;border-radius:18px;font-size:15px;font-weight:800}
    .mtbtn.main{background:#4CAF50;color:#fff;box-shadow:0 8px 20px rgba(76,175,80,.35)}
    .mtbtn.ghost{background:#EEF5EE;color:#2E7D32;font-size:13px;font-weight:600}
    '''
    return css, f'''
    <img class="bg" src="{BG_DAY}"><div class="vig" style="background:linear-gradient(180deg,rgba(178,223,219,0) 40%,rgba(104,159,56,.24) 78%,rgba(51,105,30,.34))"></div>
    <div class="chipctx abs" style="left:14px;top:14px">⛰️ 后山小屋<i> · 家的延伸 · 走出边缘即达</i></div>
    <div class="hill abs" style="left:-70px;bottom:-90px;width:290px;height:240px;opacity:.85"></div>
    <div class="hill abs" style="right:-90px;bottom:-110px;width:330px;height:270px;opacity:.92"></div>
    {pet("老陈", 60, 196, 380, 104, "chick", "sleep", badge="先辈")}
    {zzz(316, 392)}
    <div class="mtnote">
      <b>老陈 · Lv.60 · 萌芽鸡 · 传说 · 隐居 12 天</b><br>
      到 Lv.60 只会<b>提示</b>「可以入后山了」—— 是否隐居由你决定，系统绝不替你做（U-07）<br>
      <span class="u">召回后先回到「待命」，要不要放出到家，也由你决定 · 隐居不占「在家 6 只」名额 ·
      「召回」专指隐居回归（ADR-007），与日常「收起 / 放出」是两回事</span>
    </div>
    <div style="position:absolute;left:18px;right:18px;bottom:56px;display:flex;gap:9px">
      <span class="mtbtn main">召回 老陈</span>
      <span class="mtbtn ghost">先让它多歇会儿</span>
    </div>
    <div class="foot-hint">空态（10.3）：后山无人时 —— 这里只有一句「后山很安静，没有谁需要休息」</div>
    '''


# ---------------------------------------------------------------- 28 排行榜 · 五榜
def s28():
    css = '''
    .rank{position:absolute;left:10px;right:10px;top:158px;bottom:14px;background:rgba(255,255,255,.97);
      backdrop-filter:blur(14px);border-radius:26px;padding:14px 16px 12px;
      box-shadow:0 -8px 40px rgba(20,45,20,.22);overflow-y:auto}
    .rank h3{font-size:16.5px;font-weight:800;color:#26382A;display:flex;align-items:baseline;gap:8px}
    .rank h3 i{font-style:normal;font-size:11px;font-weight:500;color:#7C8E79}
    .rtabs{display:flex;gap:6px;margin:10px 0 6px}
    .rtabs .t{flex:1;text-align:center;padding:7px 2px;border-radius:12px;background:#F4F7F4;
      font-size:10.5px;font-weight:700;color:#5B6B58}
    .rtabs .t.on{background:#4CAF50;color:#fff}
    .rrow{display:flex;align-items:center;gap:10px;padding:9px 4px;border-bottom:1px solid #EDF3EC}
    .rrow .no{flex:0 0 26px;height:26px;border-radius:9px;background:#F4F7F4;color:#5B6B58;
      font-size:11px;font-weight:800;display:flex;align-items:center;justify-content:center}
    .rrow.top .no{background:#FFF3D6;color:#9A6A00}
    .rrow .tx{flex:1;min-width:0}
    .rrow .tx b{display:block;font-size:12px;color:#2A3A28}
    .rrow .tx i{font-style:normal;font-size:9.5px;color:#94A490}
    .rrow .v{flex:0 0 auto;font-size:13px;font-weight:800;color:#3A4A36}
    .rrow.me{background:#EAF6EA;border-radius:14px;border-bottom:none;padding:9px 10px;margin-top:8px}
    '''
    rows = "".join(f'<div class="rrow{" top" if i < 3 else ""}"><span class="no">{i + 1}</span>'
                   f'<span class="tx"><b>{n}</b><i>{s}</i></span><span class="v">{v}</span></div>'
                   for i, (n, s, v) in enumerate((
        ("云养猫的人 · 凤翎", "Lv.42 · 羽虫 · 传说", 88),
        ("山涧客 · 锦鲤鲤", "Lv.38 · 鳞虫 · 史诗", 81),
        ("拾穗者 · 蜜蜜蜂", "Lv.35 · 昆虫 · 史诗", 77),
        ("晚风 · 疾风猫", "Lv.31 · 毛虫 · 稀有", 71),
        ("豆花 · 圆壳龟", "Lv.29 · 昆虫 · 稀有", 66),
    )))
    return css, f'''
    <img class="bg" src="{BG_DAY}"><div class="modal-mask" style="background:rgba(22,42,24,.30)"></div>
    {drawer_handle()}
    <div class="rank">
      <h3>排行榜 <i>范围：全服 / 好友（世代榜与繁育榜仅全服）</i></h3>
      <div class="rtabs"><span class="t on">战力</span><span class="t">等级</span><span class="t">亲密</span>
        <span class="t">世代</span><span class="t">繁育</span></div>
      {rows}
      <div class="rrow me"><span class="no">23</span>
        <span class="tx"><b>我 · 小豆芽</b><i>Lv.26 · 羽虫 · 史诗</i></span><span class="v">62</span></div>
      <div style="margin-top:11px;padding:9px 12px;border-radius:14px;background:#F0F7EF;
        font-size:10px;color:#3C4F39;line-height:1.6">
        「综合战力」是<b>智慧 / 魅力唯一参与计算的地方</b>（U-06：二者是纯展示属性，
        不影响养成产出，只进这个排行分）—— 类群与性别同样不制造数值优劣（ADR-021）</div>
    </div>
    '''


# ---------------------------------------------------------------- 29 成就 · 已点亮
def s29():
    css = '''
    .ach{position:absolute;left:10px;right:10px;top:158px;bottom:14px;background:rgba(255,255,255,.97);
      backdrop-filter:blur(14px);border-radius:26px;padding:14px 16px 12px;
      box-shadow:0 -8px 40px rgba(20,45,20,.22);overflow-y:auto}
    .ach h3{font-size:16.5px;font-weight:800;color:#26382A;display:flex;align-items:baseline;gap:8px}
    .ach h3 i{font-style:normal;font-size:11px;font-weight:500;color:#7C8E79}
    .agrid{display:grid;grid-template-columns:repeat(4,1fr);gap:8px;margin-top:11px}
    .ac{border-radius:16px;padding:10px 4px 8px;text-align:center;background:#F5F9F4;border:1.5px solid #E4EFE2}
    .ac .em{font-size:21px;line-height:1.1}
    .ac .nm{display:block;font-size:9px;font-weight:700;color:#33422F;margin-top:5px}
    .ac .rd{display:block;font-size:8px;color:#94A490;margin-top:2px}
    .ac.no{background:#F4F6F4;border-style:dashed;border-color:#DDE5DC}
    .ac.no .em{filter:grayscale(1);opacity:.45}
    .ac.no .nm{color:#9AA79A}
    '''
    lit = [("🥚", "第一颗蛋", "+100💰"), ("🐣", "初次破壳", "+100💰"),
           ("🧶", "玩具之友", "+80💰"), ("📱", "桌面全勤 7 天", "+30💎"),
           ("🌳", "三代同堂", "+200💰"), ("🧬", "跨虫混血", "+150💰"),
           ("⛰️", "后山访客", "+80💰"), ("🔟", "十连保底", "+100💰"),
           ("🏡", "家在册满员", "+120💰"), ("⏱", "专注 100 分", "+30💎"),
           ("💰", "小金库", "+80💰"), ("🛡️", "合规同行", "+50💰")]
    dark = [("🦄", "首只灵兽"), ("🐉", "完整神兽形态"), ("👑", "全服前十"), ("🤝", "串门达人")]
    cells = "".join(f'<div class="ac"><span class="em">{e}</span><span class="nm">{n}</span>'
                    f'<span class="rd">{r}</span></div>' for e, n, r in lit)
    dcells = "".join(f'<div class="ac no"><span class="em">{e}</span><span class="nm">{n}</span>'
                     f'<span class="rd">未点亮</span></div>' for e, n in dark)
    return css, f'''
    <img class="bg" src="{BG_DAY}"><div class="modal-mask" style="background:rgba(22,42,24,.30)"></div>
    {drawer_handle()}
    <div class="ach">
      <h3>成就 <i>已点亮 12 / 16 · 奖励只发金币与钻石（§6.1）</i></h3>
      <div class="agrid">{cells}{dcells}</div>
      <div style="margin-top:12px;padding:9px 12px;border-radius:14px;background:#F0F7EF;
        font-size:10px;color:#3C4F39;line-height:1.6">
        成就的稀缺感来自<b>陪伴时长与家族厚度</b>，而不是抽卡运气 ——
        所以「首只灵兽」「完整神兽形态」在这里只是目标，不是门槛</div>
    </div>
    '''


# ---------------------------------------------------------------- 30 设置 · 隐私与合规
def s30():
    css = '''
    .set{position:absolute;left:10px;right:10px;top:150px;bottom:14px;background:rgba(255,255,255,.97);
      backdrop-filter:blur(14px);border-radius:26px;padding:14px 16px 12px;
      box-shadow:0 -8px 40px rgba(20,45,20,.22);overflow-y:auto}
    .set h3{font-size:16.5px;font-weight:800;color:#26382A;display:flex;align-items:baseline;gap:8px}
    .set h3 i{font-style:normal;font-size:11px;font-weight:500;color:#7C8E79}
    .sgrp{font-size:10px;font-weight:800;color:#9AA79A;letter-spacing:1.2px;margin:12px 2px 5px}
    .srow2{display:flex;align-items:center;gap:10px;padding:10px 4px;border-bottom:1px solid #EDF3EC}
    .srow2:last-of-type{border-bottom:none}
    .srow2 .tx{flex:1;min-width:0}
    .srow2 .tx b{display:block;font-size:12.5px;font-weight:700;color:#2A3A28}
    .srow2 .tx i{font-style:normal;font-size:9.5px;color:#94A490;display:block;margin-top:2px;line-height:1.45}
    .srow2 .st{flex:0 0 auto;padding:4px 10px;border-radius:10px;font-size:9.5px;font-weight:800}
    .srow2 .chev{color:#B9C6B6;font-size:14px}
    '''
    return css, f'''
    <img class="bg" src="{BG_DAY}"><div class="modal-mask" style="background:rgba(22,42,24,.30)"></div>
    {drawer_handle()}
    <div class="set">
      <h3>设置 <i>芽农小王 · Lv.12 养宠人 · 已陪伴 46 天</i></h3>

      <div class="sgrp">桌宠（核心体验）</div>
      <div class="srow2"><span class="tx"><b>悬浮窗权限</b>
        <i>用途声明：桌面宠物展示 —— 不读取、不模拟、不越界（§3.6）</i></span>
        <span class="st" style="background:#E8F5E9;color:#2E7D32">已开启 ✓</span></div>
      <div class="srow2"><span class="tx"><b>常驻通知（保活）</b>
        <i>Android 8+ 桌宠必须挂一条常驻通知才不被杀 —— 内容只有一句话，不推广告</i></span>
        <span class="st" style="background:#E8F5E9;color:#2E7D32">已开启 ✓</span></div>
      <div class="srow2"><span class="tx"><b>重新走一遍桌宠引导</b>
        <i>拒绝过也没关系 —— 宠物开口 → 跳系统设置 → 跳出去（16~18 屏）随时可重看</i></span>
        <span class="chev">›</span></div>

      <div class="sgrp">声音</div>
      <div class="srow2"><span class="tx"><b>音效</b>
        <i>破壳声 · 蹦跳声 · 升级音（不做语音，不做对话 —— §3.5）</i></span>
        <span class="st" style="background:#E8F5E9;color:#2E7D32">开</span></div>

      <div class="sgrp">隐私与合规</div>
      <div class="srow2"><span class="tx"><b>隐私政策</b>
        <i>首启那份 —— 悬浮窗用途、不采集清单，随时可回看</i></span><span class="chev">›</span></div>
      <div class="srow2"><span class="tx"><b>抽蛋概率公示</b>
        <i>含保底综合概率 10.42% · 保底只到史诗 · 传说/神话无保底通道（§6.3.1）</i></span><span class="chev">›</span></div>
      <div class="srow2"><span class="tx"><b>我的数据</b>
        <i>可导出 · 可删除（合规红线第 10 条）· 全部数据本地存储</i></span><span class="chev">›</span></div>

      <div class="sgrp">关于</div>
      <div class="srow2"><span class="tx"><b>版本与用户协议</b></span><span class="chev">›</span></div>
    </div>
    '''


# ---------------------------------------------------------------- 31 十连 · 出壳队列
def s31():
    css = '''
    .q{position:absolute;left:10px;right:10px;top:112px;bottom:14px;background:rgba(255,255,255,.97);
      backdrop-filter:blur(14px);border-radius:26px;padding:14px 14px 12px;
      box-shadow:0 -8px 40px rgba(20,45,20,.24)}
    .q h3{font-size:16.5px;font-weight:800;color:#26382A;display:flex;align-items:baseline;gap:8px}
    .q h3 i{font-style:normal;font-size:11px;font-weight:500;color:#7C8E79}
    .qsum{display:flex;align-items:center;gap:8px;margin:8px 0 10px;font-size:10.5px;color:#5C6B58}
    .qbar{flex:1;height:6px;border-radius:4px;background:#E6EDE4;overflow:hidden}
    .qbar span{display:block;height:100%;width:0%;background:linear-gradient(90deg,#8BC34A,#558B2F)}
    .grid{display:flex;flex-wrap:wrap;gap:6px 4px}
    .qe{width:66px;text-align:center;position:relative}
    .qe .no{position:absolute;left:3px;top:0;font-size:8.5px;font-weight:800;color:#9AA89A;
      background:rgba(255,255,255,.9);border-radius:6px;padding:0 3px}
    .qe .dot{display:inline-block;width:6px;height:6px;border-radius:50%;margin-right:3px;vertical-align:1px}
    .qe .st{font-size:8.5px;color:#7C8E79;margin-top:1px}
    .qe.cur{background:rgba(139,195,74,.14);border-radius:14px;box-shadow:0 0 0 1.5px rgba(139,195,74,.5) inset}
    .qe.cur .st{color:#2E7D32;font-weight:800}
    .qe.done{opacity:.42}
    .qfoot{position:absolute;left:14px;right:14px;bottom:14px}
    .qbtn{display:block;text-align:center;padding:13px 0;border-radius:18px;font-size:12.5px;font-weight:800;
      background:linear-gradient(135deg,#7CB342,#558B2F);color:#fff;box-shadow:0 10px 22px rgba(85,139,47,.32)}
    .qnote{margin-top:8px;font-size:9.5px;line-height:1.7;color:#7C8E79;background:#F4F8F2;
      border:1px solid #E3EDE1;border-radius:12px;padding:8px 10px}
    .qnote b{color:#33502F}
    '''
    rar = [
        (1, "#E53935", "神话", "破壳中", "cur"),
        (2, "#FB8C00", "传说", "待出壳", ""),
        (3, "#FB8C00", "传说", "待出壳", ""),
        (4, "#8E24AA", "史诗", "待出壳", ""),
        (5, "#8E24AA", "史诗", "待出壳", ""),
        (6, "#8E24AA", "史诗", "待出壳", ""),
        (7, "#8E24AA", "史诗", "待出壳", ""),
        (8, "#1E88E5", "稀有", "待出壳", ""),
        (9, "#1E88E5", "稀有", "待出壳", ""),
        (10, "#90A4AE", "普通", "待出壳", ""),
    ]
    cells = "".join(
        f'<div class="qe {cls}"><span class="no">{no:02d}</span>'
        f'{egg_svg(46, 3 if cls == "cur" else 1, glow=(cls == "cur"))}'
        f'<div class="st"><span class="dot" style="background:{col}"></span>{name}</div></div>'
        for no, col, name, st, cls in rar
    )
    return css, f"""
    <img class="bg" src="{BG_DAY}"><div class="vig" style="background:rgba(20,38,22,.22)"></div>
    <div class="chipctx abs" style="left:14px;top:14px">🥚 十连 · 出壳队列<br><i>10 颗蛋 · 按稀有度降序排列</i></div>
    <div class="q">
      <h3>出壳队列 <i>0 / 10 已破壳</i></h3>
      <div class="qsum"><span>全部出壳约 3:12 后完成</span><span class="qbar"><span style="width:4%"></span></span></div>
      <div class="grid">{cells}</div>
      <div class="qfoot">
        <span class="qbtn">全部出壳（零成本）</span>
        <div class="qnote">
          <b>节奏（14 §二定稿）</b>：第 1 颗 <b>T+60s</b>，之后<b>每 20s 一颗</b>，第 10 颗在 <b>T+240s</b>；
          <b>总长 ≤ 4 分钟</b>。10 颗蛋<b>共用一套队列时长</b>，<b>不按稀有度各走 5~60 分钟</b> —— 这正是旧实现的缺陷。
          <b>稀有度降序</b>出壳：把最好的惊喜前置，避免等 4 分钟才看到最值钱那只。
          <b>每颗都走 3 秒破壳</b>（3s × 10 = 30s，占队列 1/8）；不耐烦时点「全部出壳」，剩余蛋 0.4s 间隔全破。
        </div>
      </div>
    </div>
    """


SCREENS = [
    ("01", "家 · 主界面（无 Tab）", "打开 App = 家。场景即信息架构：宠物生活、搭话气泡、蛋在角落；唯一的常驻 HUD 是右缘抽屉把手（环 = 今日经验进度）。⭐ **在家最多同时站 6 只**（这里画满：两排各 3），左下角 chip 显示「在家 6/6 · 待命 3」。", "s01", "旧 01 首页（5-Tab + 功能宫格）整体作废"),
    ("02", "手势 · 抚摸", "在宠物身上长按/滑动 = 抚摸。涟漪 + 爱心 + 经验飘字就地反馈，没有「互动面板」页。", "s02", "旧 06 互动面板 → 删除"),
    ("03", "手势 · 拖拽喂食", "托盘按住拖到宠物身上 = 喂食/玩耍/训练。玩具拖过去即消耗耐久（G-08 的落点）。", "s03", "旧 06 互动面板 → 删除"),
    ("04", "蛋 · 就地揭晓", "蛋长在家里，轻点即开箱；揭晓卡浮在家的场景上。保底与概率公示仍在场。", "s04", "旧 02 盲盒开箱页 → 场景化"),
    ("05", "孵化 · 首蛋聚焦层", "**v2.5 改：首蛋不再是小角落** —— 蛋 180dp 居中、场景压暗，下方 00:47 大字倒计时 + 「21:40 破壳」，上方教手势「敲敲它」。敲一次多一道永久细纹。三组动效：呼吸 2.4s / 每 4s 轻晃 / 数字每秒跳动。**首蛋恒 1 分钟，与稀有度无关**；第 2 颗起回到角落 72dp 小蛋 + 底部孵化片（旧稿形态）。", "s05", "旧 03 孵化中页 → 首蛋聚焦层（v2.5）"),
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
    ("17", "桌宠授权 · 跳系统设置页", "悬浮窗权限（<code>SYSTEM_ALERT_WINDOW</code>）**给不了普通弹窗**，必须真的跳到系统设置页由用户手动打开。同时解释**用户最反感的那件事**：Android 8+ 要求桌宠挂一条常驻通知才不被杀 —— 产品语言说清楚「它就是那句话，不是广告」。四家 ROM 各自还有自启动 / 后台白名单 / 高耗电 / Doze，**按机型分别引导**。", "s17", "新增屏（U-11 拍板后补）"),
    ("18", "桌宠 · 跳出去 + 拒绝兜底", "授权后播放「**跳出去**」过渡仪式：宠物从「家」走向屏幕边缘 → 从边缘消失 → 出现在手机桌面，**是同一个它**（数据同一份、状态实时同步）。⭐ 同屏给出**拒绝授权后的兜底**：宠物说「那我先在家里陪你」，不惩罚、不反复弹，但抽屉里的入口**一直留着** —— 不能一次拒绝就永久静默。", "s18", "新增屏（U-11 拍板后补）"),
    ("19", "首启 · 隐私政策与开场", "**全 App 唯一一次全屏阻断**（合规红线第 5 条：同意隐私政策前不初始化任何 SDK）。三条实话：悬浮窗只用于展示 / 不用无障碍服务（只能「演」不能「读」）/ 概率全公开。之后处处都是「家」。", "s19", "新增屏（补 00 §八「首次启动」）"),
    ("20", "图鉴 · 五虫十八种", "抽屉「宠物图鉴」的落点（底部升起）：**18 种按五虫 + 不入十类分六组**，未收集显示灰格。⚠️ 「不入十类」不可抽（跨虫 4% / 异兽对 40%）；**灵兽不可抽**（ADR-017）。空态守 10.3：指向抽蛋，不放空白插画。", "s20", "新增屏（抽屉 8 条目落点补齐 · 1/6）"),
    ("21", "族谱 · 血缘树", "抽屉「族谱」+ 档案浮卡「族谱」的落点（就地展开）：实线 = 血缘、虚线 = 配对；**称谓由血缘推导**，NPC 只标「生育搭档」不进称谓（§5.1）。高亮最大贡献血统 · 统计行 · 导出图片（§2.7 / §7.3）。", "s21", "新增屏（抽屉 8 条目落点补齐 · 2/6）"),
    ("22", "仓库 · 道具与盲盒", "抽屉「仓库」的落点（底部升起）：六类收纳，与 §6.2 道具表逐一对应；玩具显示**耐久条**（G-08）。⚠️ 道具回收 = 售价 50%，**宠物不可售卖** —— 重复宠去融合（26 屏）。", "s22", "新增屏（抽屉 8 条目落点补齐 · 3/6）"),
    ("23", "商店 · 道具与补给", "抽屉「商店 · 盲盒」的落点（底部升起）：门票（金币 300 日限 1 / 钻石 60）+ 道具 + **钻石充值档位**（§6.3 可选付费，含合规三件套）。所有道具都能靠任务拿到 —— 不付费不影响核心体验。", "s23", "新增屏（抽屉 8 条目落点补齐 · 4/6）"),
    ("24", "配对广场 · 找搭档", "档案浮卡「配对生育」+ 00 §八「成年引导」的落点（底部升起）：**§2.5 七条前置条件做成 ✓ 清单**；真人优先（需对方确认）、NPC 补位（免确认，性别永远取反 —— U-09）。配对成功**双方各得一只后代**，宠不被拿走。", "s24", "新增屏（s08 断链补齐 · 配对 1/2）"),
    ("25", "配对 · 双光效汇合", "§2.5 流程 3~4 的落点（就地浮现）：双光效汇合 → **双方各得一只后代**。基因 = 父母均值 ±15%；稀有度以 max(父母) 为基准漂移、**永不降级**；跨虫上漂 ×1.35 是刻意奖励社交。", "s25", "新增屏（s08 断链补齐 · 配对 2/2）"),
    ("26", "融合 · 重复宠的去处", "十连结果页「去家族里融合」的落点（底部升起）：同物种 / 都已破壳 / 200 金币；印记满升档（5/8/12/20），溢出保留。⚠️ 副宠**入后山不可取回**（二次确认）· **永不产出灵兽**（ADR-017）· 神话不可作目标。", "s26", "新增屏（s15 断链补齐）"),
    ("27", "后山小屋 · 隐居与召回", "抽屉「后山小屋」的落点（**走出边缘**而来 —— 全稿唯一离开「家」的场景）：到 Lv.60 **只提示不自动隐居**（U-07）；召回后先回「待命」；「召回」专指隐居回归（ADR-007），与「收起 / 放出」严格区分。空态守 10.3。", "s27", "新增屏（抽屉 8 条目落点补齐 · 5/6）"),
    ("28", "排行榜 · 五榜", "档案浮卡「排行榜」的落点（底部升起）：战力 / 等级 / 亲密 / 世代 / 繁育五榜（§7.1），全服 / 好友切换。⚠️ **综合战力是智慧 / 魅力唯一参与计算的地方**（U-06），类群与性别不制造数值优劣（ADR-021）。", "s28", "新增屏（s08 断链补齐）"),
    ("29", "成就 · 已点亮", "抽屉「成就」的落点（底部升起）：成就墙点亮态 / 未点亮灰格；**奖励只发金币与钻石**（§6.1 产出侧）。稀缺感来自陪伴时长与家族厚度，不来自抽卡运气。", "s29", "新增屏（抽屉 8 条目落点补齐 · 6/6）"),
    ("30", "设置 · 隐私与合规", "抽屉「设置 · 隐私与合规」的落点（底部升起）：悬浮窗 / 常驻通知（保活解释）/**重走 16~18 引导**（U-11 第⑤条「保留再次引导」）/ 隐私政策 / 概率公示 / 数据可导出可删除（合规红线第 10 条）。", "s30", "新增屏（抽屉 8 条目落点补齐 · 设置）"),
    ("31", "十连 · 出壳队列", "**v2.5 新增**：十连不是「10 只幼体砸下来」，也不是「10 份独立倒计时」—— 是**一个队列**。10 颗蛋横排（按稀有度降序，最值钱的在最前），第 1 颗 `T+60s`、之后每 `20s` 一颗、**总长 ≤ 4 分钟**；每颗都走 3 秒破壳；不耐烦可点「**全部出壳**」（零成本，0.4s 间隔）。", "s31", "新增屏（W-14-2 拍板后补）"),
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
.src{display:inline-block;font-family:Consolas,"Courier New",monospace;font-size:11.5px;color:#2E7D32;background:#EDF7EE;border:1px solid #C8E6C9;border-radius:5px;padding:0 5px;margin:0 2px}
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
            ("（无）", "13 抽蛋入口 · 14 开箱台 · 15 十连", "U-13 拍板后补：抽蛋过去**只有算法、没有生意**（<code>blind_box</code> 甚至没有价格，也没有任何一屏能抽）；这三屏把「陪伴换抽 · 只做十连 · 公示口径」变成看得见的动作。"),
        ))

    notes = "".join(f"<li>{x}</li>" for x in (
        "<b>数值全部对齐代码（R2 出处标注）</b>：互动经验 抚摸15 / 喂食60 / 玩耍105 / 训练150 / 清洁30 <span class='src'>= GrowthRules.AttributeRules.interactionGain 的对应经验表</span>；每日上限 50/20/15/8/10 <span class='src'>= 各互动类型日限常量</span>；任务 8 项 <span class='src'>= EconomyRules.DailyTask</span>。旧稿「全勤 735 金币」已修正 —— 金币合计 710 <span class='src'>= EconomyRules 签到 100 + 任务 610</span>，735 是经验。旧稿「Lv.26 需 18,370」已修正 —— <span class='src'>= GrowthRules.expToNextLevel(26)</span> = 6,238。⚠️ <b>本稿所有数字均为示意</b>，改上游数值时以代码常量为准（R2：设计稿不复述真值，只标注出处）。",
        "<b>任务口径</b>：每日签到 / 互动达人 / 大厨时间 / 玩伴时光 / 健身教练 / 干干净净 / 桌面陪伴 / 串门拜访（<span class='src'>= EconomyRules.DailyTask 的 8 项，逐一对应</span>，不再自造「专注陪伴」任务）。",
        "<b>心情与玩具（L3 冻结后已更新）</b>：<b>全稿不放心情条</b> —— 理由已从「口径未定」变成「<b>刻意不显示</b>」：<code>mood</code> 是 <b>0~100 持久属性、只影响行为表现权重</b>（<code>U-02</code> 拍板），<b>不影响任何数值产出</b>，所以不该被做成一根让玩家盯着刷的条（守 ADR-021）。<b>毛线球「耐久会掉」已从「方向示意」变为事实</b> —— <code>G-08</code> 已落地：毛线球 <span class='src'>= ItemRegistry.toy_yarn</span>（80 金币 · 耐久 20）· 飞盘 <span class='src'>= ItemRegistry.toy_frisbee</span>（120 · 耐久 30），<code>ToyRules.wear</code> 每次玩耍 −1。",
        "<b>今日进度</b>：唯一常驻 HUD 是抽屉把手上的进度环（07 屏 71% = 4,265/6,000 <span class='src'>= EconomyRules.DAILY_EXP_CAP</span>）；顶部只有日期天气，没有货币。",
        "<b>合规红线不变</b>：不使用无障碍服务、不读取其他应用信息、不模拟点击；概率公示入口保留在 04 屏。",
        "<b>配对门槛</b>：Lv.26+ 且亲密度 ≥ 70 <span class='src'>= GrowthRules.PairingRules.requiredIntimacy</span>（08 屏「亲密 78 ≥ 70」即为达标示意）。",
        "<b>对接工程</b>：Phase 1.1 需按本稿重构 —— 单 Activity 场景壳 + 手势层 + 抽屉，Tab 布局废弃（docs/09 U-10 拍板稿）。",
        "<b>抽蛋（U-13 拍板 · 2026-10-16）</b>：门票 = <b>陪伴换抽</b> —— 每日免费 <b>2 抽</b>（签到 1 + 任务 8/8 全清 1）· 金币 <b>300/个 · 日限 1</b> · 钻石 60/个 · 可囤券 <span class='src'>= GachaRules</span>。只做<b>十连</b>（二十/五十连不做），<b>十连保「稀有及以上」</b>、刻意<b>不保史诗</b>。30 天 60 抽 ≈ 6.25 只史诗+ ≈ 刚好填满「家在册 6 只」。公示写死「保底只到史诗 · 传说与神话没有保底通道 · 综合概率（含保底）10.42%」→ <span class='src'>= GachaRules.Disclosure.MUST_STATE</span>。",
        "<b>数量口径（U-12 拍板 · 2026-10-16）</b>：<b>拥有不限 ⊃ 在家 6 ⊃ 桌面 3</b>，是<b>含包关系</b>（上桌的宠必须先在册）<span class='src'>= HomeSlots.MAX_HOME_PETS / MAX_DESKTOP_PETS</span>。「上桌」= <b>3 个槽位的名单</b>而非每只一个布尔；主宠 = 「在任何地方都优先表达的那只」、恒定占桌面 1 号槽位；入/出家动作叫「<b>放出 / 收起</b>」（与 ADR-007 的「隐居·召回」严格区分）。11 / 12 两屏就是这套口径的界面落点。",
        "<b>落点闭环（v2.3 · 2026-10-16）</b>：抽屉 8 条目此前只有入口没有落点（断链）—— 本轮补齐 19~30 共 12 屏：19 首启隐私政策 <span class='src'>= 00 §八 + §十二红线 5</span> · 20 图鉴 <span class='src'>= 00 §2.1.1 五虫十八种</span> · 21 族谱 <span class='src'>= 00 §2.7/§7.3/§5.1</span> · 22 仓库 · 23 商店 <span class='src'>= 00 §6.2/§6.3</span> · 24/25 配对 <span class='src'>= 00 §2.5 七条前置 + §7.2</span> · 26 融合 <span class='src'>= 00 §2.6.1 + docs/10</span> · 27 后山小屋 <span class='src'>= 00 §2.8 + ADR-007</span> · 28 排行榜 <span class='src'>= 00 §7.1 + U-06</span> · 29 成就 · 30 设置。新增屏全部守 <b>02 §10.2 过渡规范</b>：底部升起 / 就地浮现，仅后山小屋用「走出边缘」。",
        "<b>细节修正（v2.3 · 2026-10-16）</b>：① 01/11 两屏的「慢慢 / 小蓝 / 团子」物种与类群此前矛盾（01 画成狗/鸡/猫、11 标成鳞/昆/鳞）—— 现统一为 <b>慢慢=圆壳龟·昆虫 · 小蓝=小青蛇·鳞虫 · 团子=小龙崽·鳞虫</b>，kit 新增 turtle/snake/dragon 三种造型，图鉴「已收集 8 种 / 5 类群」与在册 9 只逐一对应；② 04 屏「还差 12 次」→「12 抽」（与 14 屏及 GachaRules 单位一致）；③ 06 屏新生「综合 62」→ <b>19</b>（62 是 08 屏 Lv.26 成年值，新生沿用属笔误）；④ 03 屏「训练沙袋」→ <b>铁哑铃</b>（§6.2 道具表里没有沙袋）；⑤ 09 屏图鉴补「8 种 / 5 类群」单位、新增「家庭成员」入口行（§4.1.2 要求的抽屉入口）。",
        "<b>十连保底出处（R2）</b>：15 屏「保史诗会让史诗+ 涨 35%」<span class='src'>= GachaRules.kt L106：9.97% → 13.47%（+35%），tools/acquisition_sim.py 40 万次蒙卡</span>；「第 10 只兜底抬到稀有」<span class='src'>= GachaRules.TEN_PULL_FLOOR = RARE</span>。",
    ))

    html = f"""<!DOCTYPE html><html lang="zh-CN"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>萌芽宠物 SproutPet · 倒置 IA 设计稿 v2</title><style>{ARCH_CSS}</style></head><body>
<div class="wrap">
  <header class="hero">
    <h1>萌芽宠物 SproutPet · 倒置 IA 设计稿 v2</h1>
    <p>按 U-10 拍板方向重做：桌面场景即主界面，无 Tab 栏、手势直触、蛋长在家里、任务由宠物搭话驱动、管理收进抽屉。旧稿（design-export/）原样保留，可逐屏对照。数值全部取自代码，修正旧稿 2 处漂移。<br>⭐ <b>v2.3 补 12 屏（对齐 L1 冻结版 00 §五/§七 与抽屉 8 条目）</b>：抽屉里「宠物图鉴 / 族谱 / 家庭成员 / 仓库 / 商店 / 成就 / 后山小屋 / 设置」此前只有入口没有落点（断链），配对与融合两条核心动线也只有浮卡文案没有界面 —— 现补 19（首启隐私政策）· 20（图鉴）· 21（族谱）· 22（仓库）· 23（商店）· 24/25（配对广场与双光效汇合）· 26（融合）· 27（后山小屋）· 28（排行榜）· 29（成就）· 30（设置）。<br>⭐ v2.2 补 3 屏：U-12 拍板「<b>拥有不限 ⊃ 在家 6 ⊃ 桌面 3</b>」后，01 屏改为在家 6 只，并新增 11（家庭成员名册）· 12（上桌 3 个位置）；U-13 拍板「陪伴换抽 · 只做十连 · 公示口径」后再补 13（抽蛋入口）· 14（开箱台）· 15（十连结果）。</p>
    <div class="chips"><span class="chip">31 屏界面</span><span class="chip">画布 390 × 844</span>
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
  <p class="sec-note">旧稿 10 屏（design-export/）→ 新稿 31 屏：互动 / 任务 / 详情 / 设置全部化为场景层，抽屉 8 条目逐一落点。</p>
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
