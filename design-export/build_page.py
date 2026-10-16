# -*- coding: utf-8 -*-
"""生成「萌芽宠物 SproutPet · App UI 设计稿」归档页（图片内联，自包含）。"""
import base64
import os

BASE = os.path.dirname(os.path.abspath(__file__))

SCREENS = [
    ("01", "首页 · 桌面家庭", "3 只宠物同屏生活 + 主宠徽标 + 主动搭话气泡 + 今日任务直达", "01-首页-桌面家庭.png"),
    ("02", "盲盒开箱 · 揭晓", "稀有度 / 类群 / 性别揭晓卡，保底进度「还差 12 次」，概率公示入口", "02-盲盒开箱.png"),
    ("03", "孵化中 · 三种加速", "剩余时间 + 四档进度文案，专注陪伴 / 道具 / 好友助力", "03-孵化中.png"),
    ("04", "破壳 · 起名与档案卡", "强制起名是故事的第一步，档案卡展示世代与六维初值", "04-破壳起名.png"),
    ("05", "宠物详情 · 成长与六维", "等级经验条、六维属性 + 综合战力、成年阶段解锁说明", "05-宠物详情.png"),
    ("06", "互动面板 · 五种互动", "抚摸 / 喂食 / 玩耍 / 训练 / 清洁，逐条标注经验与今日上限", "06-互动面板.png"),
    ("07", "仓库 · 道具背包", "孵化 / 食物 / 玩具 / 训练 / 装饰 五类页签与道具卡", "07-仓库.png"),
    ("08", "今日任务 · 离线收益", "任务清单与全勤奖励；离线只发金币、不发经验", "08-今日任务.png"),
    ("09", "我的 · 资产与成就", "金币钻石、养成统计（世代 / 后代 / 图鉴）、后山小屋", "09-我的.png"),
    ("10", "设置 · 隐私与合规", "悬浮窗用途声明、概率公示、未成年人保护、数据导出删除", "10-设置.png"),
]

BRAND = [("萌芽绿 brand_primary", "#4CAF50"), ("深绿 brand_primary_dark", "#388E3C"),
         ("浅绿 brand_primary_light", "#C8E6C9"), ("强调 brand_accent", "#FFB300")]
RARITY = [("普通 common", "#9E9E9E"), ("稀有 rare", "#2196F3"), ("史诗 epic", "#9C27B0"),
          ("传说 legendary", "#FF9800"), ("神话 mythic", "#F44336"), ("灵兽 mutant", "#00E5FF")]
FAMILY = [("蠃虫 luo", "#4FC3F7"), ("鳞虫 lin", "#26A69A"), ("毛虫 mao", "#BCAAA4"),
          ("羽虫 yu", "#FF8A65"), ("昆虫 kun", "#9CCC65"), ("不入十类", "#5C6BC0")]
NEUTRAL = [("主文字 text_primary", "#212121"), ("次文字 text_secondary", "#757575"),
           ("提示文字 text_hint", "#BDBDBD"), ("页面底色 bg_page", "#F7F8FA"),
           ("卡片底色 bg_card", "#FFFFFF"), ("分隔线 divider", "#EEEEEE")]


def img_uri(name):
    with open(os.path.join(BASE, name), "rb") as fh:
        return "data:image/png;base64," + base64.b64encode(fh.read()).decode("ascii")


def swatches(items):
    cards = []
    for label, hexval in items:
        cards.append(
            '<div class="sw">'
            '<span class="sw-dot" style="background:%s"></span>'
            '<span class="sw-txt"><b>%s</b><i>%s</i></span>'
            "</div>" % (hexval, label, hexval)
        )
    return '<div class="sw-grid">' + "".join(cards) + "</div>"


screen_cards = []
for num, title, desc, fname in SCREENS:
    screen_cards.append(
        '<figure class="screen">'
        '<div class="screen-hd"><span class="num">%s</span><div class="screen-tt"><b>%s</b><i>%s</i></div></div>'
        '<img src="%s" alt="%s" loading="lazy">'
        "</figure>" % (num, title, desc, img_uri(fname), title)
    )

CSS = """
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:"PingFang SC","Microsoft YaHei","Noto Sans SC",-apple-system,sans-serif;
  background:#F2F6F2;color:#212121;-webkit-font-smoothing:antialiased;line-height:1.6}
.wrap{max-width:1240px;margin:0 auto;padding:0 28px 72px}
.hero{margin:0 -28px 40px;padding:56px 56px 48px;
  background:linear-gradient(135deg,#4CAF50 0%,#66BB6A 55%,#A5D6A7 100%);
  border-radius:0 0 40px 40px;color:#fff;position:relative;overflow:hidden}
.hero:after{content:"";position:absolute;right:-70px;top:-70px;width:300px;height:300px;
  border-radius:50%;background:rgba(255,255,255,.14)}
.hero:before{content:"";position:absolute;right:110px;bottom:-90px;width:200px;height:200px;
  border-radius:50%;background:rgba(255,255,255,.1)}
.hero h1{font-size:38px;font-weight:800;letter-spacing:.5px;position:relative}
.hero p{margin-top:12px;font-size:15px;opacity:.95;position:relative;max-width:760px}
.chips{margin-top:26px;display:flex;flex-wrap:wrap;gap:10px;position:relative}
.chip{background:rgba(255,255,255,.22);border:1px solid rgba(255,255,255,.4);
  padding:7px 15px;border-radius:20px;font-size:13px;font-weight:500;backdrop-filter:blur(4px)}
.src{display:inline-block;margin-top:24px;position:relative;font-size:13px;color:#fff;
  text-decoration:none;border-bottom:1px solid rgba(255,255,255,.6);padding-bottom:2px}
h2.sec{font-size:20px;font-weight:700;margin:48px 0 6px;display:flex;align-items:center;gap:10px}
h2.sec:before{content:"";width:5px;height:20px;border-radius:3px;background:#4CAF50}
.sec-note{font-size:13px;color:#757575;margin-bottom:20px}
.card{background:#fff;border:1px solid #E7EFE8;border-radius:22px;padding:26px 28px;
  box-shadow:0 6px 22px rgba(23,59,31,.05)}
.sw-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(196px,1fr));gap:12px}
.sw{display:flex;align-items:center;gap:11px;padding:9px 12px;border-radius:14px;background:#F7FAF7}
.sw-dot{width:30px;height:30px;border-radius:9px;flex:0 0 30px;box-shadow:inset 0 0 0 1px rgba(0,0,0,.08)}
.sw-txt{display:flex;flex-direction:column;line-height:1.35;min-width:0}
.sw-txt b{font-size:12.5px;font-weight:600}
.sw-txt i{font-style:normal;font-size:11.5px;color:#757575;font-family:ui-monospace,Menlo,Consolas,monospace}
.grp+.grp{margin-top:22px}
.grp h3{font-size:13px;font-weight:600;color:#388E3C;margin-bottom:10px}
.screens{display:grid;grid-template-columns:repeat(auto-fill,minmax(206px,1fr));gap:24px;margin-top:22px}
.screen{background:#fff;border:1px solid #E7EFE8;border-radius:22px;padding:14px;
  box-shadow:0 6px 22px rgba(23,59,31,.06);display:flex;flex-direction:column;gap:12px}
.screen-hd{display:flex;gap:9px;align-items:flex-start}
.num{flex:0 0 26px;height:26px;border-radius:9px;background:#E8F5E9;color:#2E7D32;
  font-size:12.5px;font-weight:700;display:flex;align-items:center;justify-content:center}
.screen-tt{display:flex;flex-direction:column;line-height:1.4;min-width:0}
.screen-tt b{font-size:13.5px;font-weight:600}
.screen-tt i{font-style:normal;font-size:11.5px;color:#757575}
.screen img{width:100%;height:auto;display:block;border-radius:16px;border:1px solid #EFF3EF;background:#F7F8FA}
ul.notes{list-style:none;display:grid;grid-template-columns:repeat(auto-fill,minmax(300px,1fr));gap:10px 26px}
ul.notes li{font-size:13.5px;color:#424242;padding-left:20px;position:relative}
ul.notes li:before{content:"";position:absolute;left:4px;top:9px;width:6px;height:6px;
  border-radius:50%;background:#81C784}
ul.notes li b{color:#212121;font-weight:600}
footer{margin-top:44px;text-align:center;font-size:12.5px;color:#9E9E9E}
"""

part1 = """<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>萌芽宠物 SproutPet · App UI 设计稿</title>
<style>""" + CSS + """</style>
</head>
<body>
<div class="wrap">
  <header class="hero">
    <h1>萌芽宠物 SproutPet · App UI 设计稿</h1>
    <p>治愈清新风格，以品牌「萌芽绿」为主色，浅底白卡、大圆角柔阴影。共 10 屏，覆盖首日破壳闭环、成长互动与个人设置，可直接与现有 Android 工程对接。</p>
    <div class="chips">
      <span class="chip">10 屏界面</span>
      <span class="chip">画布 390 &#215; 844</span>
      <span class="chip">品牌绿 #4CAF50</span>
      <span class="chip">依据 PRD v2.4</span>
      <span class="chip">2026-10-16</span>
    </div>
    <a class="src" href="https://ardot.tencent.com/file/737016502633803" target="_blank" rel="noopener">在画布中打开设计源文件 →</a>
  </header>

  <h2 class="sec">界面一览</h2>
  <p class="sec-note">底部导航为胶囊式：宠物 / 孵化 / 社交 / 我的；本轮交付前两个分栏与「我的」，社交与传承留待下一轮补齐。</p>
  <div class="screens">
"""

part2 = """
  </div>

  <h2 class="sec">设计规范</h2>
  <p class="sec-note">全部取自已实现的 colors.xml，后续改主题色只需替换这一处定义。</p>
  <div class="card">
    <div class="grp"><h3>品牌色</h3>""" + swatches(BRAND) + """</div>
    <div class="grp"><h3>稀有度（6 档）</h3>""" + swatches(RARITY) + """</div>
    <div class="grp"><h3>五虫类群色</h3>""" + swatches(FAMILY) + """</div>
    <div class="grp"><h3>中性色</h3>""" + swatches(NEUTRAL) + """</div>
  </div>

  <h2 class="sec">关键设计口径</h2>
  <p class="sec-note">界面上的数值全部取自产品需求文档，未做自造。</p>
  <div class="card">
    <ul class="notes">
      <li><b>保底规则</b>：连续 30 次未出史诗及以上，第 31 次必出；保底不含灵兽。</li>
      <li><b>日常任务</b>：全勤奖励 735 金币，任务进度逐条可见。</li>
      <li><b>离线收益</b>：25 金币 / 小时，上限 12 小时，只发金币不发经验。</li>
      <li><b>每日经验上限</b>：6,000，由互动经验与日常任务共同计入。</li>
      <li><b>互动经验</b>：抚摸 15 / 喂食 60 / 玩耍 105 / 训练 150 / 清洁 30。</li>
      <li><b>配对生育门槛</b>：Lv.26 以上且亲密度 ≥ 70，成年阶段方可解锁。</li>
      <li><b>破壳流程</b>：必须完成起名，作为玩家与宠物的第一段故事。</li>
      <li><b>合规红线</b>：不使用无障碍服务、不读取其他应用信息、不模拟点击。</li>
    </ul>
  </div>

  <footer>萌芽宠物 SproutPet · App UI 设计稿　|　整理于 2026-10-16</footer>
</div>
</body>
</html>
"""

out = part1 + "".join(screen_cards) + part2
target = os.path.join(BASE, "萌芽宠物 SproutPet · App UI 设计稿.html")
with open(target, "w", encoding="utf-8") as fh:
    fh.write(out)
print("written:", target, len(out), "chars")
