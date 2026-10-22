#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
萌芽宠物 — 宠物矢量占位资源生成器
================================================================

用途
----
生成 Phase 1「帧动画 + 属性动画」过渡实现所需的矢量占位宠物资源
（`docs/04-开发计划.md` §1.2 的「占位美术资源」任务），产出：

    app/src/main/res/drawable/pet_*.xml     Android VectorDrawable（交付物）
    docs/art/*.svg                          SVG 源文件（交给美术继续加工）
    docs/art/*.html                         预览页（人眼验收用）
    docs/art/*.png                          预览图（便于快速比对）

为什么用脚本而不是手搓 XML
--------------------------
`docs/02-宠物渲染方案.md` §2.3 的核心原则是「**同一骨骼，阶段靠缩放 + 部件显隐**」，
而不是每个阶段单独画一张。用脚本表达这条原则，阶段差异就是一组参数；
想调体型比例只改参数表，八个资源同时对齐 —— 手绘 XML 做不到这点。

设计约束（来自渲染方案）
------------------------
- 画布统一 200×200 viewport，地平线 y=176 —— 阶段切换时**不能跳位**，
  上下留白是给属性动画（挤压回弹 / 纵向拉伸）留的形变空间（02 §八）
- 描边统一深褐 #5D4037：既保证低分辨率下的识别度，也不与 Rive 版本冲突
- 纯扁平色块，**不用渐变** —— 占位资源刻意保持"可被替换"的简单度，
  且 Gradients 在 VectorDrawable 里要嵌套 aapt:attr，不利于美术手工接手
- 不画接地阴影：这套资源会被悬浮窗桌宠（02 §3.5）复用，
  在透明全屏窗里阴影会变成"糊在桌面上的一块黑"

阶段差异（对应 PetStage）
------------------------
| 阶段 | 体型 | 头顶芽 | 尾羽 | 附加 |
|------|------|--------|------|------|
| BABY(幼崽) | 最小最圆，头身比≈1:1 | 单叶 | 无 | 顶着蛋壳碎片 |
| JUVENILE(少年) | 略高 | 双叶 | 2 根 | — |
| ADULT(成年) | 基准体型 | 三叶 | 3 根 | — |
| ELDER(长者) | 与成年同骨骼，略宽 | 四叶下垂 | 4 根更长 | 眉羽 + 喙下胡羽 |

用法
----
    python tools/gen_pet_placeholder_vectors.py

重新生成后请同步 `docs/04-开发计划.md` §1.2 的资源清单。
"""

from __future__ import annotations

import math
import os

# ─────────────────────────── 基础配置 ───────────────────────────

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DRAWABLE_DIR = os.path.join(ROOT, "app", "src", "main", "res", "drawable")
ART_DIR = os.path.join(ROOT, "docs", "art")

CANVAS = 200          # viewport 正方形边长
GROUND = 176          # 地平线：脚底所在
DP_SIZE = 160         # 渲染尺寸（dp）；悬浮窗缩放由 setScale 负责

# 调色板 —— 与 app/src/main/res/values/colors.xml 对齐
OUTLINE      = "#FF5D4037"   # 统一描边（深褐）
BODY         = "#FFFFD54F"   # 体羽主色（暖黄）
BODY_DARK    = "#FFFFC107"   # 尾羽/暗部
BODY_LIGHT   = "#FFFFE082"   # 高光
BEAK         = "#FFFF9800"   # 喙与脚
SPROUT       = "#FF4CAF50"   # 嫩芽叶（= brand_primary）
SPROUT_STEM  = "#FF7CB342"   # 芽茎
SPROUT_DARK  = "#FF2E7D32"   # 芽叶暗部
EYE          = "#FF4E342E"   # 眼睛
WHITE        = "#FFFFFFFF"
CHEEK        = "#FFFFAB91"   # 腮红
HEART        = "#FFFF8A80"   # 爱心（Petted）
SWEAT        = "#FF4FC3F7"   # 汗滴（Grabbed）
SLEEP_Z      = "#FF90A4AE"   # Zzz 气泡
SHELL        = "#FFFFF8E1"   # 蛋壳
SHELL_SPOT   = "#FFFFE082"   # 蛋壳斑点
SHELL_GLOW   = "#FFFFF59D"   # 破壳漏光
SHELL_GAP    = "#FF2B2118"   # 蛋壳裂缝里的暗部（到点态：缝隙 + 一双眼睛）
NEST         = "#FFC8E6C9"   # 草窝（= brand_primary_light）

W_MAIN = 3.6    # 主体描边宽
W_DETAIL = 3.0  # 细节描边宽

# ─────────────────────────── 几何工具 ───────────────────────────
# 内部统一用「子路径列表」表达图形，再做仿射变换与多目标导出。
# 子路径 = [("M",(x,y)), ("L",(x,y)), ("C",c1,c2,p), ("Z",)]

K = 0.5522847498307936  # 三次贝塞尔逼近 1/4 圆的控制点系数


def m_mul(a, b):
    """矩阵相乘：返回 a·b（即先应用 b，再应用 a）。"""
    a1, b1, c1, d1, e1, f1 = a
    a2, b2, c2, d2, e2, f2 = b
    return (a1 * a2 + c1 * b2, b1 * a2 + d1 * b2,
            a1 * c2 + c1 * d2, b1 * c2 + d1 * d2,
            a1 * e2 + c1 * f2 + e1, b1 * e2 + d1 * f2 + f1)


def m_translate(tx, ty):
    return (1.0, 0.0, 0.0, 1.0, tx, ty)


def m_rotate(deg, cx=0.0, cy=0.0):
    r = math.radians(deg)
    cos, sin = math.cos(r), math.sin(r)
    rot = (cos, sin, -sin, cos, 0.0, 0.0)
    return m_mul(m_mul(m_translate(cx, cy), rot), m_translate(-cx, -cy))


def m_apply(m, p):
    a, b, c, d, e, f = m
    x, y = p
    return (a * x + c * y + e, b * x + d * y + f)


def transform(segments, m):
    out = []
    for seg in segments:
        if seg[0] == "Z":
            out.append(("Z",))
        else:
            out.append((seg[0],) + tuple(m_apply(m, p) for p in seg[1:]))
    return out


# ── 图元 ──

def ellipse(cx, cy, rx, ry, rot=0.0):
    m = m_rotate(rot, cx, cy)
    pts = [
        ("M", (cx + rx, cy)),
        ("C", (cx + rx, cy + K * ry), (cx + K * rx, cy + ry), (cx, cy + ry)),
        ("C", (cx - K * rx, cy + ry), (cx - rx, cy + K * ry), (cx - rx, cy)),
        ("C", (cx - rx, cy - K * ry), (cx - K * rx, cy - ry), (cx, cy - ry)),
        ("C", (cx + K * rx, cy - ry), (cx + rx, cy - K * ry), (cx + rx, cy)),
        ("Z",),
    ]
    return [s if s[0] == "Z" else (s[0],) + tuple(m_apply(m, p) for p in s[1:])
            for s in pts]


def circle(cx, cy, r):
    return ellipse(cx, cy, r, r)


def rounded_rect(x, y, w, h, r):
    r = min(r, w / 2.0, h / 2.0)
    return [
        ("M", (x + r, y)),
        ("L", (x + w - r, y)),
        ("C", (x + w - r + K * r, y), (x + w, y + r - K * r), (x + w, y + r)),
        ("L", (x + w, y + h - r)),
        ("C", (x + w, y + h - r + K * r), (x + w - r + K * r, y + h), (x + w - r, y + h)),
        ("L", (x + r, y + h)),
        ("C", (x + r - K * r, y + h), (x, y + h - r + K * r), (x, y + h - r)),
        ("L", (x, y + r)),
        ("C", (x, y + r - K * r), (x + r - K * r, y), (x + r, y)),
        ("Z",),
    ]


def poly(points, close=True):
    seg = [("M", points[0])]
    seg += [("L", p) for p in points[1:]]
    if close:
        seg.append(("Z",))
    return seg


def curve(p0, c1, c2, p3):
    """开放三次贝塞尔（只描边不填色的线条用）。"""
    return [("M", p0), ("C", c1, c2, p3)]


def arc3(p0, ctrl, p1):
    """过三点式圆弧（用于眼睛的"闭眼弧""笑眼弧"）。"""
    c1 = (p0[0] + (ctrl[0] - p0[0]) * 2 / 3.0, p0[1] + (ctrl[1] - p0[1]) * 2 / 3.0)
    c2 = (p1[0] + (ctrl[0] - p1[0]) * 2 / 3.0, p1[1] + (ctrl[1] - p1[1]) * 2 / 3.0)
    return curve(p0, c1, c2, p1)


def leaf(x, y, length, angle, width):
    """叶子：底端在 (x,y)，沿 angle 方向伸出 length（角度制，0=右，90=下）。"""
    L, W = length, width
    local = [
        ("M", (0.0, 0.0)),
        ("C", (0.2 * L, 1.05 * W), (0.8 * L, 1.05 * W), (L, 0.0)),
        ("C", (0.8 * L, -1.05 * W), (0.2 * L, -1.05 * W), (0.0, 0.0)),
        ("Z",),
    ]
    r = math.radians(angle)
    m = m_mul(m_translate(x, y), (math.cos(r), math.sin(r), -math.sin(r), math.cos(r), 0.0, 0.0))
    return transform(local, m)


def heart(cx, cy, s):
    return [
        ("M", (cx, cy + 0.75 * s)),
        ("C", (cx - 1.45 * s, cy - 0.2 * s), (cx - 0.6 * s, cy - 1.15 * s), (cx, cy - 0.35 * s)),
        ("C", (cx + 0.6 * s, cy - 1.15 * s), (cx + 1.45 * s, cy - 0.2 * s), (cx, cy + 0.75 * s)),
        ("Z",),
    ]


def drop(x, y, s):
    """水滴（汗滴 / 漏光点）。"""
    return [
        ("M", (x, y - s)),
        ("C", (x + 0.72 * s, y - 0.1 * s), (x + 0.72 * s, y + 0.9 * s), (x, y + 0.9 * s)),
        ("C", (x - 0.72 * s, y + 0.9 * s), (x - 0.72 * s, y - 0.1 * s), (x, y - s)),
        ("Z",),
    ]


def z_glyph(x, y, s):
    return poly([(x, y), (x + 0.78 * s, y), (x, y + s), (x + 0.78 * s, y + s)], close=False)


def shell_cap(cx, cy, half_w, height, teeth=4, rot=0.0):
    """蛋壳残片：一段圆弧帽 + 锯齿状裂口边。"""
    seg = [
        ("M", (cx - half_w, cy)),
        ("C", (cx - half_w, cy - 1.2 * height), (cx + half_w, cy - 1.2 * height), (cx + half_w, cy)),
    ]
    steps = teeth * 2
    for i in range(steps):
        x = cx + half_w - (i + 1) * (2 * half_w / steps)
        y = cy - (0.34 * height if i % 2 == 1 else 0.0)
        seg.append(("L", (x, y)))
    seg.append(("Z",))
    if rot:
        seg = transform(seg, m_rotate(rot, cx, cy))
    return seg


def crack(x, y, path_offsets):
    """折线裂纹。"""
    pts = [(x, y)]
    for dx, dy in path_offsets:
        pts.append((pts[-1][0] + dx, pts[-1][1] + dy))
    return poly(pts, close=False)


# ─────────────────────────── 形状记录 ───────────────────────────

def S(segments, fill=None, stroke=OUTLINE, w=W_MAIN):
    return {"segs": segments, "fill": fill, "stroke": stroke, "w": w}


# ── 可复用部件 ──

def eyes(cx, y, dx, r, style="open"):
    """style: open(圆眼+高光) / shock(受惊大眼) / happy(笑眼弯弧) / closed(闭眼下弧)"""
    out = []
    if style == "open":
        for sx in (-1, 1):
            out.append(S(circle(cx + sx * dx, y, r), EYE, None, 0))
            out.append(S(circle(cx + sx * dx - r * 0.35, y - r * 0.38, r * 0.32), WHITE, None, 0))
    elif style == "shock":
        for sx in (-1, 1):
            out.append(S(circle(cx + sx * dx, y, r), WHITE, OUTLINE, W_DETAIL))
            out.append(S(circle(cx + sx * dx, y - r * 0.18, r * 0.42), EYE, None, 0))
    elif style == "happy":
        for sx in (-1, 1):
            out.append(S(arc3((cx + sx * dx - r, y + r * 0.35),
                              (cx + sx * dx, y - r * 1.1),
                              (cx + sx * dx + r, y + r * 0.35)),
                         None, EYE, W_DETAIL))
    elif style == "closed":
        for sx in (-1, 1):
            out.append(S(arc3((cx + sx * dx - r, y - r * 0.25),
                              (cx + sx * dx, y + r * 0.85),
                              (cx + sx * dx + r, y - r * 0.25)),
                         None, EYE, W_DETAIL))
    return out


def beak(cx, cy, w, style="tri"):
    if style == "tri":
        return [S(poly([(cx - w * 0.62, cy - w * 0.42), (cx + w * 0.62, cy - w * 0.42),
                        (cx, cy + w * 0.66)]), BEAK, OUTLINE, W_DETAIL)]
    if style == "o":  # 受惊的小圆嘴
        return [S(ellipse(cx, cy, w * 0.5, w * 0.62), BEAK, OUTLINE, W_DETAIL)]
    return []


def cheeks(cx, y, dx, rx, ry):
    return [S(ellipse(cx + sx * dx, y, rx, ry, sx * 12), CHEEK, None, 0) for sx in (-1, 1)]


def wings(cx, cy, dx, rx, ry, rot, high=False):
    out = []
    for sx in (-1, 1):
        out.append(S(ellipse(cx + sx * dx, cy, rx, ry, sx * rot), BODY_DARK, OUTLINE, W_MAIN))
    return out


def feet(cx, y, dx, w, h):
    out = []
    for sx in (-1, 1):
        out.append(S(rounded_rect(cx + sx * dx - w / 2.0, y, w, h, h / 2.0), BEAK, OUTLINE, W_DETAIL))
    return out


def tail(x, y, spec, color=BODY_DARK):
    return [S(leaf(x, y, L, a, W), color, OUTLINE, W_DETAIL) for (a, L, W) in spec]


def sprout(cx, head_top, stem_len, leaves_spec):
    """头顶嫩芽：一根茎 + 若干叶片。"""
    base = (cx, head_top + 3)
    top = (cx, base[1] - stem_len)
    out = [S(arc3(base, (cx + 1.5, (base[1] + top[1]) / 2.0), top), None, SPROUT_STEM, W_DETAIL)]
    for (angle, L, W) in leaves_spec:
        out.append(S(leaf(top[0], top[1], L, angle, W), SPROUT, OUTLINE, W_DETAIL))
    return out


# ─────────────────────────── 萌芽鸡装配 ───────────────────────────

def build_chicken(p):
    """
    按参数装配一只萌芽鸡。所有阶段共用同一套装配顺序 ——
    这正是「同一骨骼」的体现在代码里的样子。
    """
    out = []
    bcx, bcy, brx, bry = p["body"]
    hcx, hcy, hrx, hry = p["head"]

    # 尾羽（最底层，被身体压住只露尖端）
    if p.get("tail"):
        out += tail(bcx + brx * 0.92, bcy + bry * 0.45, p["tail"])

    # 翅膀
    if p.get("wings"):
        wcx_off, wy, wrx, wry, wrot = p["wings"]
        out += wings(bcx, bcy + wy, wcx_off, wrx, wry, wrot)

    # 身体 / 头
    out.append(S(ellipse(bcx, bcy, brx, bry), BODY, OUTLINE, W_MAIN))
    out.append(S(ellipse(hcx, hcy, hrx, hry), BODY, OUTLINE, W_MAIN))

    # 蛋壳帽（仅幼崽）
    if p.get("shell"):
        cap_cy, cap_w, cap_h = p["shell"]
        out.append(S(shell_cap(hcx, cap_cy, cap_w, cap_h), SHELL, OUTLINE, W_MAIN))

    # 头顶嫩芽
    out += sprout(hcx, p["sprout_top"], p["stem_len"], p["leaves"])

    # 脚
    if p.get("feet"):
        fx, fy, fw, fh = p["feet"]
        out += feet(bcx, fy, fx, fw, fh)

    # 五官
    ex, ey, er, es = p["eyes"]
    out += beak(hcx, p["beak"][0], p["beak"][1], p.get("beak_style", "tri"))
    out += eyes(hcx, ey, ex, er, es)
    if p.get("cheeks"):
        out += cheeks(hcx, p["cheeks"][0], p["cheeks"][1], p["cheeks"][2], p["cheeks"][3])
    if p.get("brows"):  # 长者眉羽：抬高、放平，避免读成"生气眉"
        for sx in (-1, 1):
            out.append(S(arc3((hcx + sx * (ex + er) - 7, ey - er - 10),
                              (hcx + sx * (ex + er), ey - er - 17),
                              (hcx + sx * (ex + er) + 7, ey - er - 10)),
                         None, OUTLINE, W_DETAIL))
    if p.get("chin"):  # 长者喙下胡羽：垂在喙下方两侧，呈"山羊胡"
        pass  # v1.1 移除：浅色叶形贴在喙旁会读成"獠牙"，长者辨识改由眉羽 + 下垂芽 + 长尾羽承担

    out += p.get("extras", [])
    return out


STAGES = {
    "baby": dict(
        body=(100, 142, 34, 29), head=(100, 102, 32, 30),
        eyes=(13.0, 106, 8.2, "open"), beak=(120, 9.0), cheeks=(121, 23, 7.0, 5.0),
        wings=None, feet=(11, 168, 13, 8),
        tail=[], leaves=[(-40, 17, 9)], stem_len=14, sprout_top=44,
        shell=(76, 30, 26),
    ),
    "juvenile": dict(
        body=(100, 136, 40, 35), head=(100, 92, 35, 33),
        eyes=(14.0, 96, 7.6, "open"), beak=(112, 9.5), cheeks=(113, 25, 8.0, 5.5),
        wings=(42, 2, 13, 22, 16), feet=(13, 168, 14, 8.5),
        tail=[(30, 20, 9), (-12, 22, 10)], leaves=[(200, 17, 9), (-20, 17, 9)],
        stem_len=20, sprout_top=57,
    ),
    "adult": dict(
        body=(100, 128, 46, 43), head=(100, 76, 38, 35),
        eyes=(15.0, 80, 8.0, "open"), beak=(94, 10.5), cheeks=(96, 27, 8.5, 6.0),
        wings=(46, 2, 15, 26, 18), feet=(15, 167, 15, 9),
        tail=[(34, 28, 11), (0, 30, 12), (-26, 26, 11)],
        leaves=[(212, 19, 10), (256, 15, 9), (-32, 19, 10)],
        stem_len=16, sprout_top=37,
    ),
    "elder": dict(
        body=(100, 128, 49, 42), head=(100, 76, 38, 34),
        eyes=(15.0, 80, 7.0, "open"), beak=(94, 10.0), cheeks=(97, 28, 8.5, 6.0),
        wings=(49, 3, 15, 27, 20), feet=(16, 167, 16, 9),
        tail=[(38, 34, 12), (8, 36, 13), (-22, 32, 12)],
        leaves=[(150, 20, 10), (30, 19, 10), (200, 16, 9), (-20, 15, 9)],
        stem_len=16, sprout_top=40, brows=True,
    ),
}

# 动作变体：复用成年骨骼，只改姿态参数（对应 PetRenderState）
ACTIONS = {
    # Petted（被抚摸）：整体上弹 + 笑眼 + 冒爱心
    "happy": dict(
        body=(100, 122, 47, 43), head=(100, 70, 38, 35),
        eyes=(15.0, 74, 8.0, "happy"), beak=(88, 10.5), cheeks=(90, 27, 8.5, 6.0),
        wings=(46, 0, 15, 25, 32), feet=(15, 162, 15, 9),
        tail=[(34, 28, 11), (0, 30, 12), (-26, 26, 11)],
        leaves=[(212, 19, 10), (196, 17, 9), (-32, 19, 10), (-16, 17, 9)],
        stem_len=16, sprout_top=31,
        extras=[S(heart(44, 46, 11), HEART, OUTLINE, W_DETAIL),
                S(heart(158, 34, 8.5), HEART, OUTLINE, W_DETAIL)],
    ),
    # Grabbed（被拎起）：纵向拉伸 + 翅膀上扬 + 脚下垂 + 受惊 + 汗滴
    "grabbed": dict(
        body=(100, 126, 40, 52), head=(100, 68, 34, 39),
        eyes=(15.0, 72, 9.0, "shock"), beak=(88, 11.0), cheeks=None,
        wings=(44, -18, 14, 26, 58), feet=(13, 176, 13, 8),
        tail=[(46, 24, 10), (-34, 22, 10)],
        leaves=[(206, 19, 10), (-34, 19, 10)],
        stem_len=16, sprout_top=25, beak_style="o",
        extras=[S(drop(154, 58, 10), SWEAT, OUTLINE, W_DETAIL)],
    ),
    # Sleeping（睡着）：蹲坐压扁 + 闭眼 + Zzz + 芽下垂
    "sleep": dict(
        body=(100, 140, 49, 36), head=(100, 88, 36, 32),
        eyes=(14.0, 92, 7.4, "closed"), beak=(105, 10.0), cheeks=(107, 26, 8.0, 5.5),
        wings=(47, 8, 15, 20, 26), feet=None,
        tail=[(20, 26, 11), (-6, 24, 11)],
        leaves=[(160, 18, 9), (20, 17, 9)],
        stem_len=12, sprout_top=54,
        extras=[S(z_glyph(138, 60, 12), None, SLEEP_Z, W_DETAIL),
                S(z_glyph(155, 42, 15), None, SLEEP_Z, W_DETAIL),
                S(z_glyph(172, 22, 18), None, SLEEP_Z, W_DETAIL)],
    ),
}


# ─────────────────────────── L1 类群层（docs/15 §2.1 六类群母题） ───────────────────────────
#
# 2026-10-17 拍板（docs/15 §1.4 / §八）：形象先做 L1 类群层 —— 6 类群 × 3 可画阶段 = 18 个。
# 每个类群一个装配函数，参数按 §2.1 的「轮廓母题 / 体块比例 / 材质」写死；
# 阶段差异 = 体积缩放 + 头占比调整（§四 阶段形变表）。
# ⚠️ 美术替换时只需同名覆盖 app/src/main/res/drawable/pet_{family}_{stage}.xml，
#    渲染器（PetAssetResolver 四级回落）与测试一行都不用改。

FAMILY_COLORS = {
    # 类群 → (主色, 暗部, 亮部)。同屏两类群主色相间隔 ≥ 40°（docs/15 §2.2）
    "luo":   ("#7FD1C1", "#4DB6AC", "#B2EBDF"),   # 蠃虫：水感青
    "lin":   ("#64B5F6", "#42A5F5", "#BBDEFB"),   # 鳞虫：冷调蓝
    "mao":   ("#FFB74D", "#FFA726", "#FFE0B2"),   # 毛虫：暖橙
    "yu":    ("#FFD54F", "#FFC107", "#FFF2B8"),   # 羽虫：暖黄（沿萌芽鸡）
    "kun":   ("#B39DDB", "#9575CD", "#E1D7F2"),   # 昆虫：甲壳紫
    "uncat": ("#F48FB1", "#EC407A", "#FCE4EC"),   # 异兽：荧光粉（自发光）
}

# 阶段形变（docs/15 §四）：体积 / 头占比 / 特征
STAGE_FORM = {
    "baby":     dict(s=0.72, head_up=1.00, note="头占比最大（眼 ≥ 头 30%）"),
    "juvenile": dict(s=0.88, head_up=0.92, note="开始抽长，出现类群母题"),
    "adult":    dict(s=1.00, head_up=0.85, note="母题完整，记忆点最清晰"),
}


def _face(cx, head_cy, span, r, body_light, stage, mouth=None):
    """共享五官：同一双眼睛 + 腮红（docs/15 §2.2 允许共享的部分）。眼睛占比随阶段变大。"""
    baby_boost = 1.25 if stage == "baby" else (1.1 if stage == "juvenile" else 1.0)
    out = eyes(cx, head_cy, span, r * baby_boost, "open")
    out += cheeks(cx, head_cy + r * baby_boost * 1.5, span * 1.35, 7.5, 5.0)
    if mouth == "smile":
        out.append(S(arc3((cx - 6, head_cy + r * 2.1), (cx, head_cy + r * 2.9), (cx + 6, head_cy + r * 2.1)),
                     None, OUTLINE, W_DETAIL))
    return out


def build_luo(stage):
    """蠃虫 LUO：圆 · 软 · 黏 —— 头身一体（零硬边、无颈），宽:高 ≈ 1:1.15，湿润半透。"""
    body, dark, light = FAMILY_COLORS["luo"]
    st = STAGE_FORM[stage]
    rx, ry = 40 * st["s"], 46 * st["s"]
    cy = 176 - ry
    out = []
    # 湿润高光（材质：湿润半透 → 亮部大块）
    out.append(S(ellipse(100, cy, rx, ry), body, OUTLINE, W_MAIN))
    out.append(S(ellipse(100 - rx * 0.32, cy - ry * 0.42, rx * 0.34, ry * 0.26, rot=-24),
                 light, None, 0))
    # 头身一体：五官直接长在身体上部
    out += _face(100, cy - ry * 0.42, 14 * st["s"], 8.0, light, stage, mouth="smile")
    # 黏液光泽弧（身体下部一条弧线，与轮廓同色系）
    out.append(S(arc3((100 - rx * 0.55, cy + ry * 0.45), (100, cy + ry * 0.62), (100 + rx * 0.55, cy + ry * 0.45)),
                 None, dark, W_DETAIL))
    # 小短手（圆软，贴身）
    for sx in (-1, 1):
        out.append(S(ellipse(100 + sx * (rx + 7 * st["s"]), cy + ry * 0.22, 10 * st["s"], 13 * st["s"], rot=sx * 24),
                     body, OUTLINE, W_MAIN))
    return out


def build_lin(stage):
    """鳞虫 LIN：长条 · S 形 · 有鳞 —— 身长为高的 2.2~2.6 倍，冷调鳞片（隔行明暗，非写实）。"""
    body, dark, light = FAMILY_COLORS["lin"]
    st = STAGE_FORM[stage]
    s = st["s"]
    out = []
    # S 形长身：三段圆叠出弯曲（底大顶小），总高 ~150 × s（比例感靠"细长"而不是绝对长）
    out.append(S(ellipse(100, 140, 24 * s, 34 * s), body, OUTLINE, W_MAIN))            # 尾段（卷）
    out.append(S(ellipse(88 * s + 100 * (1 - s), 104, 20 * s, 30 * s, rot=-14), body, OUTLINE, W_MAIN))  # 中段
    out.append(S(ellipse(112 * s + 100 * (1 - s), 68, 18 * s, 26 * s, rot=12), body, OUTLINE, W_MAIN))   # 上段
    # 鳞纹：每段 2 条弧（隔行明暗，非网格）
    for ex, ey, erx, ery in ((100, 140, 16 * s, 24 * s), (88 * s + 100 * (1 - s), 104, 13 * s, 20 * s),
                             (112 * s + 100 * (1 - s), 68, 11 * s, 17 * s)):
        out.append(S(arc3((ex - erx * 0.8, ey), (ex, ey + ery * 0.35), (ex + erx * 0.8, ey)),
                     None, dark, 2.2))
    # 头（小，长在 S 顶）
    hx, hy = 112 * s + 100 * (1 - s) + 4, 34
    out.append(S(ellipse(hx, hy, 19 * s, 16 * s), body, OUTLINE, W_MAIN))
    out += _face(hx, hy - 2, 10 * s, 6.0, light, stage, mouth="smile")
    # 分叉舌（小青蛇记忆点的类群基础版）
    out.append(S(poly([(hx, hy + 12 * s), (hx, hy + 19 * s)], close=False), None, OUTLINE, 2.4))
    out.append(S(poly([(hx, hy + 19 * s), (hx - 4, hy + 24 * s)], close=False), None, OUTLINE, 2.4))
    out.append(S(poly([(hx, hy + 19 * s), (hx + 4, hy + 24 * s)], close=False), None, OUTLINE, 2.4))
    return out


def build_mao(stage):
    """毛虫 MAO：蓬松 · 尖耳/尾 —— 头径 ≈ 体宽 × 1.1（大头），毛边轮廓（锯齿状柔化）。"""
    body, dark, light = FAMILY_COLORS["mao"]
    st = STAGE_FORM[stage]
    s = st["s"]
    out = []
    # 蓬松尾（3 个叠圆，先画被身体压住）
    for i, (tx, ty, tr) in enumerate(((146, 150, 12 * s), (158, 136, 10 * s), (163, 121, 8 * s))):
        out.append(S(circle(tx, ty, tr), dark if i == 1 else body, OUTLINE, W_DETAIL))
    # 小身
    out.append(S(ellipse(100, 148, 30 * s, 24 * s), body, OUTLINE, W_MAIN))
    # 大头（头径 ≈ 体宽 × 1.1 → 40 × 36）
    out.append(S(ellipse(100, 94, 40 * s, 36 * s), body, OUTLINE, W_MAIN))
    # 毛边（外缘锯齿状柔化：一圈短线）
    for i in range(10):
        ang = math.radians(-200 + i * 22)
        x0, y0 = 100 + 40 * s * math.cos(ang), 94 + 36 * s * math.sin(ang)
        x1, y1 = 100 + (40 * s + 7) * math.cos(ang), 94 + (36 * s + 7) * math.sin(ang)
        out.append(S(poly([(x0, y0), (x1, y1)], close=False), None, dark, 2.2))
    # 尖耳 2 只
    for sx in (-1, 1):
        out.append(S(poly([(100 + sx * 26 * s, 64), (100 + sx * 34 * s, 38), (100 + sx * 10 * s, 58)]),
                     body, OUTLINE, W_MAIN))
        out.append(S(poly([(100 + sx * 25 * s, 60), (100 + sx * 29 * s, 47), (100 + sx * 17 * s, 57)]),
                     light, None, 0))
    out += _face(100, 96, 13 * s, 7.6, light, stage, mouth="smile")
    # 鼻头（兽感小圆鼻）
    out.append(S(ellipse(100, 108, 5.5 * s, 4.2 * s), dark, None, 0))
    return out


def build_yu(stage):
    """羽虫 YU：卵形身 + 小翅 —— 高:宽 ≈ 1.15:1，无颈；羽片分层（2~3 层）。头顶一撮羽（非嫩芽）。"""
    body, dark, light = FAMILY_COLORS["yu"]
    st = STAGE_FORM[stage]
    s = st["s"]
    out = []
    # 尾羽 3 根（分层）
    out += tail(100 + 30 * s, 148, [(26 * s, 22, 9), (0, 24, 10), (-26 * s, 20, 9)], color=dark)
    # 身体（卵形）
    out.append(S(ellipse(100, 132, 36 * s, 42 * s), body, OUTLINE, W_MAIN))
    # 翅膀 2 层（分层羽片）
    for sx in (-1, 1):
        out.append(S(ellipse(100 + sx * 34 * s, 124, 12 * s, 20 * s, rot=sx * 34), dark, OUTLINE, W_DETAIL))
        out.append(S(ellipse(100 + sx * 30 * s, 126, 10 * s, 16 * s, rot=sx * 30), light, None, 0))
    # 头（无颈，融在身上）
    out.append(S(ellipse(100, 86, 30 * s, 27 * s), body, OUTLINE, W_MAIN))
    # 头顶一撮小羽（3 根短线，区别于萌芽鸡的嫩芽）
    for dx, ang in ((-8, -24), (0, 0), (8, 24)):
        out.append(S(poly([(100 + dx * s, 62), (100 + dx * s + 10 * math.sin(math.radians(ang)), 48)],
                          close=False), None, dark, 2.6))
    out += _face(100, 88, 11.5 * s, 7.4, light, stage, mouth=None)
    # 喙（小三角）
    out.append(S(poly([(95, 98), (105, 98), (100, 106)]), "#FFB300", OUTLINE, 2.4))
    # 爪 2 只
    for sx in (-1, 1):
        out.append(S(poly([(100 + sx * 12 * s, 170), (100 + sx * 12 * s, 176)], close=False), None, "#FFB300", 3.0))
    return out


def build_kun(stage):
    """昆虫 KUN：甲壳 · 分节 · 中轴对称 —— 明显背中缝，硬质锐利小高光，壳缘清晰。"""
    body, dark, light = FAMILY_COLORS["kun"]
    st = STAGE_FORM[stage]
    s = st["s"]
    out = []
    # 足 4 条（短，中轴对称）
    for sx in (-1, 1):
        for fy in (136, 152):
            out.append(S(poly([(100 + sx * 34 * s, fy * (0.9 + 0.1 * s)),
                               (100 + sx * 46 * s, fy * (0.9 + 0.1 * s) + 6)], close=False),
                         None, OUTLINE, 2.8))
    # 甲壳（大椭圆）+ 分节弧
    out.append(S(ellipse(100, 134, 40 * s, 36 * s), body, OUTLINE, W_MAIN))
    out.append(S(arc3((100 - 34 * s, 126), (100, 116), (100 + 34 * s, 126)), None, dark, 2.4))
    out.append(S(arc3((100 - 38 * s, 144), (100, 136), (100 + 38 * s, 144)), None, dark, 2.4))
    # 背中缝（明显，中轴对称的标志）
    out.append(S(poly([(100, 98), (100, 170)], close=False), None, dark, 2.8))
    # 硬质小高光（锐利点，不弥散）
    out.append(S(ellipse(74 * s + 100 * (1 - s), 112, 6 * s, 4 * s, rot=-30), "#FFFFFF", None, 0))
    # 头 + 触角（末端小圆）
    out.append(S(ellipse(100, 82, 24 * s, 20 * s), body, OUTLINE, W_MAIN))
    for sx in (-1, 1):
        out.append(S(poly([(100 + sx * 14 * s, 66), (100 + sx * 26 * s, 46)], close=False), None, OUTLINE, 2.6))
        out.append(S(circle(100 + sx * 27 * s, 44, 3.5), dark, OUTLINE, 2.0))
    out += _face(100, 84, 9.5 * s, 5.8, light, stage)
    return out


def build_uncat(stage):
    """异兽 UNCAT：发光 · 悬浮 · 不落地 —— 底部离地 4~10px（唯一不触地），自发光外晕。"""
    body, dark, light = FAMILY_COLORS["uncat"]
    st = STAGE_FORM[stage]
    s = st["s"]
    out = []
    # 自发光外晕（≥ 8px 羽化：用两层同心半透明圆表达）
    out.append(S(circle(100, 124, 64 * s), "#28F48FB1", None, 0))
    out.append(S(circle(100, 124, 52 * s), "#33F48FB1", None, 0))
    # 身体（头身圆润相融）
    out.append(S(ellipse(100, 134, 32 * s, 30 * s), body, OUTLINE, W_MAIN))
    out.append(S(ellipse(100, 94, 28 * s, 26 * s), body, OUTLINE, W_MAIN))
    # 独角（发光尖角）
    out.append(S(poly([(100, 70), (94, 48), (103, 58), (108, 46), (106, 70)]), light, OUTLINE, W_DETAIL))
    # 小翅翼（异兽的"不属十类"感：一对小浮翼）
    for sx in (-1, 1):
        out.append(S(ellipse(100 + sx * 38 * s, 118, 8 * s, 14 * s, rot=sx * 40),
                     "#8CF48FB1", OUTLINE, 2.2))
    # 悬浮：脚下无脚，留空 4~10px，下方只有一团淡影
    out.append(S(ellipse(100, 172, 18 * s, 4 * s), "#231E371E", None, 0))
    out += _face(100, 94, 10.5 * s, 7.0, light, stage, mouth="smile")
    # 星屑 2 颗（发光感的注脚）
    out.append(S(poly([(52, 84), (54, 90), (60, 92), (54, 94), (52, 100), (50, 94), (44, 92), (50, 90)]),
                 "#FFF3C4", None, 0))
    out.append(S(poly([(158, 60), (159.5, 64.5), (164, 66), (159.5, 67.5), (158, 72), (156.5, 67.5), (152, 66), (156.5, 64.5)]),
                 "#FFF3C4", None, 0))
    return out


FAMILY_BUILDERS = {
    "luo": build_luo, "lin": build_lin, "mao": build_mao,
    "yu": build_yu, "kun": build_kun, "uncat": build_uncat,
}

FAMILY_NAMES = {
    "luo": "蠃虫", "lin": "鳞虫", "mao": "毛虫", "yu": "羽虫", "kun": "昆虫", "uncat": "异兽",
}

STAGE_NAMES = {"baby": "幼崽", "juvenile": "少年", "adult": "成年"}


def build_family(family, stage):
    """按类群与阶段装配（docs/15 §1.4 L1 交付清单）。"""
    return FAMILY_BUILDERS[family](stage)


# ─────────────────────────── 蛋 ───────────────────────────

def build_egg(cracked: bool):
    out = [S(ellipse(100, 168, 52, 10), NEST, None, 0)]
    if not cracked:
        out.append(S(ellipse(100, 108, 46, 58), SHELL, OUTLINE, W_MAIN))
        out.append(S(ellipse(80, 88, 9, 7, -20), SHELL_SPOT, None, 0))
        out.append(S(ellipse(122, 124, 11, 8, 14), SHELL_SPOT, None, 0))
        out.append(S(ellipse(104, 152, 7, 6, 0), SHELL_SPOT, None, 0))
        out.append(S(ellipse(84, 74, 7, 11, -18), WHITE, None, 0))
        return out

    # 破壳：下半蛋壳 + 漏光 + 飞起的碎片
    crack_zag = []
    half_w = 44
    steps = 8
    seg = [("M", (100 - half_w, 106)),
           ("C", (100 - half_w, 148), (100 + half_w, 148), (100 + half_w, 106))]
    for i in range(steps):
        x = 100 + half_w - (i + 1) * (2 * half_w / steps)
        y = 106 - (7 if i % 2 == 1 else 0)
        seg.append(("L", (x, y)))
    seg.append(("Z",))
    out.append(S(seg, SHELL, OUTLINE, W_MAIN))
    # ⚠️ 不画「漏光」黄斑（2026-10-22 用户反馈原话：「长着像蛋壳还有俩眼这个是什么鬼」）：
    # 两个 SHELL_GLOW 水滴在破壳构图里读起来就是一双悬空的眼睛，惊悚不惊喜。
    # 破壳感由锯齿裂缝 + 两片飞壳承担，够了。
    out.append(S(crack(96, 88, [(10, -14), (-6, -16), (12, -16), (-4, -18)]), None, OUTLINE, W_DETAIL))
    out.append(S(shell_cap(128, 54, 22, 20, teeth=3, rot=-24), SHELL, OUTLINE, W_MAIN))
    out.append(S(shell_cap(74, 40, 15, 14, teeth=3, rot=32), SHELL, OUTLINE, W_MAIN))
    return out


def build_egg_ready():
    """蛋 · 到点可破壳（docs/14 §1.6 · 2026-10-22 用户拍板保留）。

    壳还完整，横向裂开一道缝，**缝隙里有一双眼睛在看你** ——
    用户能确认"里面有东西、是活的"，但看不出是什么品种。
    刻意只画眼睛：不画喙、不画耳朵、不画犄角、不画绒毛，任何物种特征都会提前泄底。

    ⚠️ 2026-10-22 二次澄清：用户要删的是**出壳演出**（破壳动画遮罩那张裂壳碎片图），
    不是本资源 —— 「到点缝隙露眼」原样保留。
    """
    out = [S(ellipse(100, 168, 52, 10), NEST, None, 0)]
    # 壳还完整
    out.append(S(ellipse(100, 108, 46, 58), SHELL, OUTLINE, W_MAIN))
    out.append(S(ellipse(74, 76, 8, 6, -20), SHELL_SPOT, None, 0))
    out.append(S(ellipse(126, 140, 10, 7, 14), SHELL_SPOT, None, 0))

    # 横向缝隙：透镜形（两端窄、中间宽），带锯齿抖动 —— 裂开的口子不是一个规整的椭圆。
    gap_top = [(64, 110), (72, 106), (81, 103), (90, 105), (99, 102),
               (108, 104), (117, 103), (126, 105), (136, 110)]
    gap_bot = [(136, 118), (127, 121), (118, 123), (109, 121), (100, 125),
               (91, 123), (82, 121), (73, 122), (64, 118)]
    out.append(S(poly(gap_top + gap_bot), SHELL_GAP, None, 0))

    # 缝隙里的眼睛：只给眼睛，不给任何物种线索
    gap_eye_y = 113
    for sx in (-1, 1):
        ex = 100 + sx * 15
        out.append(S(circle(ex, gap_eye_y, 7.5), WHITE, None, 0))
        out.append(S(circle(ex, gap_eye_y + 0.8, 3.9), EYE, None, 0))
        out.append(S(circle(ex - 1.4, gap_eye_y - 1.8, 1.6), WHITE, None, 0))

    # 缝隙两端延出去的细裂纹（刻意短 —— 长了会读成"长角"）
    out.append(S(crack(64, 112, [(-5, -12), (4, -10), (-3, -9)]), None, OUTLINE, W_DETAIL))
    out.append(S(crack(136, 112, [(5, -12), (-4, -10), (3, -9)]), None, OUTLINE, W_DETAIL))
    out.append(S(crack(100, 125, [(-2, 12), (6, 9)]), None, OUTLINE, W_DETAIL))
    return out



# ─────────────────────────── 导出 ───────────────────────────

def fmt_pt(p):
    return f"{p[0]:.2f} {p[1]:.2f}"


def path_data(segs):
    parts = []
    for s in segs:
        if s[0] == "M":
            parts.append("M" + fmt_pt(s[1]))
        elif s[0] == "L":
            parts.append("L" + fmt_pt(s[1]))
        elif s[0] == "C":
            parts.append("C" + " ".join(fmt_pt(p) for p in s[1:]))
        else:
            parts.append("Z")
    return " ".join(parts)


def hex_to_rgb(h):
    """内部色值兼容 #RRGGBB 与 #AARRGGBB；位图预览需要 (R,G,B)。"""
    h = h.lstrip("#")
    if len(h) == 6:
        return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16))
    return (int(h[2:4], 16), int(h[4:6], 16), int(h[6:8], 16))


def hex_to_rgba(h):
    """兼容 6 位（#RRGGBB，alpha=255）与 8 位（#AARRGGBB）两种写法。"""
    h = h.lstrip("#")
    if len(h) == 6:
        r, g, b = int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)
        return (r, g, b, 255)
    r, g, b, a = int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), int(h[6:8], 16)
    return (r, g, b, a)


def svg_color(h):
    r, g, b, a = hex_to_rgba(h)
    if a == 255:
        return f"#{r:02X}{g:02X}{b:02X}"
    return f"rgba({r},{g},{b},{a / 255.0:.3f})"


def to_vector_drawable(name, shapes, title, comment_lines):
    lines = ['<?xml version="1.0" encoding="utf-8"?>']
    lines.append("<!--")
    lines.append(f"  {title}")
    lines.append("")
    for c in comment_lines:
        lines.append(f"  {c}")
    lines.append("")
    lines.append("  占位资源：由 tools/gen_pet_placeholder_vectors.py 生成，")
    lines.append("  美术交付 Rive 版本（docs/02-宠物渲染方案.md §5.1）后可整体替换。")
    lines.append("  手改本文件会在下次重新生成时被覆盖 —— 请改脚本。")
    lines.append("-->")
    lines.append('<vector xmlns:android="http://schemas.android.com/apk/res/android"')
    lines.append(f'    android:width="{DP_SIZE}dp"')
    lines.append(f'    android:height="{DP_SIZE}dp"')
    lines.append(f'    android:viewportWidth="{CANVAS}"')
    lines.append(f'    android:viewportHeight="{CANVAS}">')
    for sh in shapes:
        attrs = []
        attrs.append(f'        android:fillColor="{sh["fill"] if sh["fill"] else "#00000000"}"')
        if sh["stroke"]:
            attrs.append(f'        android:strokeColor="{sh["stroke"]}"')
            attrs.append(f'        android:strokeWidth="{sh["w"]}"')
            attrs.append('        android:strokeLineCap="round"')
            attrs.append('        android:strokeLineJoin="round"')
        attrs.append(f'        android:pathData="{path_data(sh["segs"])}" />')
        lines.append("    <path")
        lines.extend(attrs)
    lines.append("</vector>")
    lines.append("")
    return "\n".join(lines)


def to_svg(shapes):
    parts = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {CANVAS} {CANVAS}" '
             f'width="{CANVAS}" height="{CANVAS}">']
    for sh in shapes:
        fill = svg_color(sh["fill"]) if sh["fill"] else "none"
        if sh["stroke"]:
            stroke = f' stroke="{svg_color(sh["stroke"])}" stroke-width="{sh["w"]}" ' \
                     f'stroke-linecap="round" stroke-linejoin="round"'
        else:
            stroke = ""
        parts.append(f'  <path d="{path_data(sh["segs"])}" fill="{fill}"{stroke} />')
    parts.append("</svg>")
    return "\n".join(parts)


# ── 位图预览（自检用：把几何按同一套坐标画出来看） ──

def flatten(segs, steps=18):
    """把子路径展平成点列，便于位图绘制与自检。"""
    subs, cur = [], []
    last = None
    for s in segs:
        if s[0] == "M":
            if cur:
                subs.append(cur)
            cur = [s[1]]
            last = s[1]
        elif s[0] == "L":
            cur.append(s[1])
            last = s[1]
        elif s[0] == "C":
            p0 = last
            c1, c2, p3 = s[1], s[2], s[3]
            for i in range(1, steps + 1):
                t = i / steps
                mt = 1 - t
                x = (mt ** 3) * p0[0] + 3 * (mt ** 2) * t * c1[0] + 3 * mt * (t ** 2) * c2[0] + (t ** 3) * p3[0]
                y = (mt ** 3) * p0[1] + 3 * (mt ** 2) * t * c1[1] + 3 * mt * (t ** 2) * c2[1] + (t ** 3) * p3[1]
                cur.append((x, y))
            last = p3
        elif s[0] == "Z":
            pass
    if cur:
        subs.append(cur)
    return subs


def render_preview(items, path, ss=4, cols=3):
    from PIL import Image, ImageDraw
    rows = math.ceil(len(items) / cols)
    W, H = CANVAS * cols * ss, CANVAS * rows * ss
    img = Image.new("RGB", (W, H), (247, 248, 250))
    drw = ImageDraw.Draw(img)
    for idx, (_, shapes) in enumerate(items):
        ox = (idx % cols) * CANVAS * ss
        oy = (idx // cols) * CANVAS * ss
        # 地平线参考
        drw.line([(ox, oy + GROUND * ss), (ox + CANVAS * ss, oy + GROUND * ss)],
                 fill=(228, 228, 228), width=2)
        for sh in shapes:
            subs = flatten(sh["segs"])
            closed = any(s[0] == "Z" for s in sh["segs"])
            rgba = hex_to_rgb(sh["fill"]) if sh["fill"] else None
            for pts in subs:
                pix = [(ox + x * ss, oy + y * ss) for (x, y) in pts]
                if rgba and closed and len(pix) > 2:
                    drw.polygon(pix, fill=rgba)
                if sh["stroke"]:
                    line_pts = pix + ([pix[0]] if closed and len(pix) > 2 else [])
                    if len(line_pts) > 1:
                        drw.line(line_pts, fill=hex_to_rgb(sh["stroke"]),
                                 width=max(1, int(sh["w"] * ss)), joint="curve")
    img = img.resize((CANVAS * cols, CANVAS * rows), Image.LANCZOS)
    img.save(path)
    return path


# ─────────────────────────── 主流程 ───────────────────────────

SPEC = [
    # (文件名, 显示名, 用途说明, 构建函数)
    ("pet_sprout_chicken_baby", "萌芽鸡 · 幼崽",
     ["对应 PetStage.BABY（id=2）：刚破壳，体型最小、头身比≈1:1，",
      "顶着蛋壳碎片，头顶只有单叶嫩芽。"],
     lambda: build_chicken(STAGES["baby"])),
    ("pet_sprout_chicken_juvenile", "萌芽鸡 · 少年",
     ["对应 PetStage.JUVENILE（id=3）：开始有独立性格表现，双叶嫩芽、短尾羽。"],
     lambda: build_chicken(STAGES["juvenile"])),
    ("pet_sprout_chicken_adult", "萌芽鸡 · 成年",
     ["对应 PetStage.ADULT（id=4）：外观最完整，三叶嫩芽 + 完整尾羽。",
      "也是三个阶段动作变体的基准骨骼。"],
     lambda: build_chicken(STAGES["adult"])),
    ("pet_sprout_chicken_elder", "萌芽鸡 · 长者",
     ["对应 PetStage.ELDER（id=5）：与成年同骨骼、略宽，",
      "四叶下垂嫩芽 + 更长尾羽 + 眉羽/喙下胡羽，表达「沉稳」。（id=1 HATCHING 为保留位，不出资源）"],
     lambda: build_chicken(STAGES["elder"])),
    ("pet_sprout_chicken_happy", "萌芽鸡 · 开心",
     ["对应 PetRenderState.Petted（被抚摸）：笑眼弯弧 + 整体上弹 + 冒爱心。",
      "配合 FramePetRenderer 的挤压回弹属性动画使用。"],
     lambda: build_chicken(ACTIONS["happy"])),
    ("pet_sprout_chicken_grabbed", "萌芽鸡 · 被拎起",
     ["对应 PetRenderState.Grabbed（长按）/ Dragging（拖动中）：",
      "纵向拉伸、翅膀上扬、双脚下垂、受惊圆眼 + 汗滴。"],
     lambda: build_chicken(ACTIONS["grabbed"])),
    ("pet_sprout_chicken_sleep", "萌芽鸡 · 睡着",
     ["对应 PetRenderState.Sleeping：蹲坐压扁 + 闭眼 + Zzz 气泡 + 嫩芽下垂。"],
     lambda: build_chicken(ACTIONS["sleep"])),
]

# ── L1 类群层（docs/15 §1.4 · 2026-10-17 拍板交付）：6 类群 × 3 可画阶段 = 18 个 ──
FAMILY_MOTIF = {
    "luo": "圆软黏（头身一体、无颈）", "lin": "长条S形（有鳞、分叉舌）",
    "mao": "蓬松大头（尖耳/尾）", "yu": "卵形身小翅（无颈、羽片分层）",
    "kun": "甲壳分节（背中缝、触角）", "uncat": "发光悬浮（不落地、独角）",
}
SPEC += [
    (f"pet_{family}_{stage}", f"{FAMILY_NAMES[family]} · {STAGE_NAMES[stage]}",
     [f"L1 类群层（docs/15 §1.4）：{FAMILY_NAMES[family]}的 {STAGE_NAMES[stage]}形态。",
      f"类群母题 = {FAMILY_MOTIF[family]}（docs/15 §2.1）。",
      "⚠️ 美术替换时同名覆盖本文件即可，渲染器（PetAssetResolver 四级回落）零改动。"],
     lambda family=family, stage=stage: build_family(family, stage))
    for family in ("luo", "lin", "mao", "yu", "kun", "uncat")
    for stage in ("baby", "juvenile", "adult")
]

SPEC += [
    ("pet_egg_common", "蛋 · 孵化中",
     ["对应 PetRenderState.Incubating：普通蛋（rarity=COMMON）。",
      "外形对全部物种通用 —— 蛋里是什么虫，破壳时才知道。"],
     lambda: build_egg(False)),
    ("pet_egg_cracked", "蛋 · 破壳中",
     ["对应 PetRenderState.Hatching：锯齿裂纹 + 崩飞的蛋壳碎片（不画漏光斑 —— 读成眼睛，2026-10-22 用户拍板去掉）。",
      "刻意不画幼崽探头 —— 这一帧对全部物种通用，具体幼崽由 stage 资源接续。"],
     lambda: build_egg(True)),
    ("pet_egg_ready", "蛋 · 到点可破壳",
     ["docs/14 §1.6：倒计时归零后蛋壳横向裂开一道缝，缝里有一双眼睛（2026-10-22 用户拍板保留 —— 删的是出壳演出，不是它）。"],
     lambda: build_egg_ready()),
]


def main():
    os.makedirs(DRAWABLE_DIR, exist_ok=True)
    os.makedirs(ART_DIR, exist_ok=True)

    written_xml, written_svg = [], []
    items = []
    for name, title, comment, builder in SPEC:
        shapes = builder()
        items.append((title, shapes))

        xml = to_vector_drawable(name, shapes, title, comment)
        xml_path = os.path.join(DRAWABLE_DIR, name + ".xml")
        with open(xml_path, "w", encoding="utf-8", newline="\n") as f:
            f.write(xml)
        written_xml.append(xml_path)

        svg_path = os.path.join(ART_DIR, name + ".svg")
        with open(svg_path, "w", encoding="utf-8", newline="\n") as f:
            f.write(to_svg(shapes))
        written_svg.append(svg_path)

    png_path = os.path.join(ART_DIR, "sprout-chicken-preview.png")
    render_preview(items, png_path)

    html_path = os.path.join(ART_DIR, "sprout-chicken-preview.html")
    with open(html_path, "w", encoding="utf-8", newline="\n") as f:
        f.write(build_html(items))

    print(f"[OK] VectorDrawable × {len(written_xml)} -> {DRAWABLE_DIR}")
    for p in written_xml:
        print("     -", os.path.basename(p))
    print(f"[OK] SVG × {len(written_svg)} -> {ART_DIR}")
    print("[OK] PNG 预览 ->", png_path)
    print("[OK] HTML 预览 ->", html_path)


def build_html(items):
    palette = [("体羽", BODY), ("体羽暗部", BODY_DARK), ("体羽高光", BODY_LIGHT),
               ("喙/脚", BEAK), ("嫩芽", SPROUT), ("描边", OUTLINE),
               ("腮红", CHEEK), ("蛋壳", SHELL), ("草窝", NEST)]
    usage = {
        "pet_sprout_chicken_baby": "PetStage.BABY",
        "pet_sprout_chicken_juvenile": "PetStage.JUVENILE",
        "pet_sprout_chicken_adult": "PetStage.ADULT",
        "pet_sprout_chicken_elder": "PetStage.ELDER",
        "pet_sprout_chicken_happy": "PetRenderState.Petted",
        "pet_sprout_chicken_grabbed": "PetRenderState.Grabbed / Dragging",
        "pet_sprout_chicken_sleep": "PetRenderState.Sleeping",
        "pet_egg_common": "PetRenderState.Incubating",
        "pet_egg_cracked": "PetRenderState.Hatching",
    }
    cards = []
    for (name, title, comment, _), (_, shapes) in zip(SPEC, items):
        svg = to_svg(shapes).replace("<svg ", '<svg class="art" ')
        cards.append(f"""
    <div class="card">
      <div class="art-wrap">{svg}</div>
      <div class="meta">
        <div class="title">{title}</div>
        <code>{name}.xml</code>
        <div class="state">{usage.get(name, "")}</div>
      </div>
    </div>""")

    swatches = "".join(
        f'<div class="sw"><div class="chip" style="background:{svg_color(h)}"></div>'
        f'<span>{n}<br><code>{h[3:] if h.startswith("#FF") else h}</code></span></div>'
        for n, h in palette)

    return f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<title>萌芽鸡 · 矢量占位资源</title>
<style>
  :root {{ color-scheme: light; }}
  * {{ box-sizing: border-box; }}
  body {{ margin:0; padding:40px 32px 64px; background:#F7F8FA; color:#212121;
         font:14px/1.6 -apple-system,"Segoe UI","PingFang SC","Microsoft YaHei",sans-serif; }}
  h1 {{ font-size:24px; margin:0 0 6px; }}
  .sub {{ color:#757575; margin-bottom:8px; }}
  .note {{ max-width:920px; color:#757575; font-size:13px; }}
  h2 {{ font-size:16px; margin:36px 0 14px; padding-left:10px; border-left:3px solid #4CAF50; }}
  .grid {{ display:grid; grid-template-columns:repeat(auto-fill,minmax(220px,1fr)); gap:18px; }}
  .card {{ background:#fff; border-radius:14px; padding:14px 14px 16px;
           box-shadow:0 1px 3px rgba(0,0,0,.06),0 8px 24px rgba(0,0,0,.04); }}
  .art-wrap {{ background:#F7F8FA; border-radius:10px; padding:6px; }}
  svg.art {{ display:block; width:100%; height:auto; }}
  .meta {{ margin-top:10px; }}
  .title {{ font-weight:600; }}
  .state {{ color:#4CAF50; font-size:12px; margin-top:2px; }}
  code {{ font:12px/1.5 ui-monospace,Consolas,monospace; color:#616161; }}
  .sw {{ display:flex; align-items:center; gap:10px; }}
  .chips {{ display:grid; grid-template-columns:repeat(auto-fill,minmax(180px,1fr)); gap:12px;
            background:#fff; border-radius:14px; padding:16px; box-shadow:0 1px 3px rgba(0,0,0,.06); }}
  .chip {{ width:34px; height:34px; border-radius:9px; border:1px solid rgba(0,0,0,.08); flex:none; }}
  table {{ border-collapse:collapse; background:#fff; border-radius:12px; overflow:hidden;
           box-shadow:0 1px 3px rgba(0,0,0,.06); font-size:13px; }}
  th,td {{ padding:9px 14px; text-align:left; border-bottom:1px solid #EEEEEE; }}
  th {{ background:#FAFAFA; font-weight:600; }}
  tr:last-child td {{ border-bottom:none; }}
</style>
</head>
<body>
  <h1>萌芽鸡 · 矢量占位资源</h1>
  <div class="sub">Phase 1 帧动画占位（VectorDrawable）· 画布 200×200 · 地平线 y={GROUND}</div>
  <p class="note">依据《02-宠物渲染方案》§2.3「同一骨骼，阶段靠缩放与部件显隐」与 §八 Phase 1 过渡方案绘制。
  这九张是<strong>占位资源</strong>：轮廓刻意保持扁平、无渐变，便于美术在 Rive 里重建骨骼后整体替换。</p>

  <h2>资源清单</h2>
  <div class="grid">{''.join(cards)}
  </div>

  <h2>阶段差异（同一骨骼）</h2>
  <table>
    <tr><th>阶段</th><th>体型</th><th>头顶芽</th><th>尾羽</th><th>附加</th></tr>
    <tr><td>幼崽 BABY</td><td>最小最圆，头身比≈1:1</td><td>单叶</td><td>无</td><td>顶着蛋壳碎片</td></tr>
    <tr><td>少年 JUVENILE</td><td>略高</td><td>双叶</td><td>2 根</td><td>—</td></tr>
    <tr><td>成年 ADULT</td><td>基准体型</td><td>三叶</td><td>3 根</td><td>—</td></tr>
    <tr><td>长者 ELDER</td><td>与成年同骨骼、略宽</td><td>四叶下垂</td><td>4 根更长</td><td>眉羽 + 喙下胡羽</td></tr>
  </table>

  <h2>色板</h2>
  <div class="chips">{swatches}</div>

  <h2>接入说明</h2>
  <p class="note">
    文件已落在 <code>app/src/main/res/drawable/</code>，命名遵循《02-宠物渲染方案》§5.1 的
    <code>pet_{{speciesId}}_{{stage}}</code> 规则，可直接被 <code>PetAssetResolver</code> 解析。<br>
    重新生成：<code>python tools/gen_pet_placeholder_vectors.py</code>
    —— 手工改 XML 会在下次生成时被覆盖，比例调整请改脚本里的 <code>STAGES</code> / <code>ACTIONS</code> 参数表。
  </p>
</body>
</html>
"""


if __name__ == "__main__":
    main()
