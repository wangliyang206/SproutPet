#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
萌芽宠物 — AI 生成 3D 形象的落地脚本（抠底 + 裁切 + 缩放 + 改名）
================================================================

用途
----
把 ImageGen 产出的 18 张 3D 渲染原图（1024×1024，**画出来的棋盘格底**，
alpha 全 255）处理成 Android 可直接用的透明底 PNG，并按
`docs/15-宠物形象规范.md` §1.4 的命名规则落到：

    app/src/main/res/drawable-nodpi/pet_{familyId}_{stage}.png

替换 `tools/gen_pet_placeholder_vectors.py` 生成的纯色矢量占位。

为什么需要这个脚本
------------------
`ImageGen` 的 `background=transparent` 参数在当前模型上不生效 —— 所谓"透明"
是模型**画出来的棋盘格图案**，像素 alpha 仍是 255。直接塞进 APK 会在场景里
显示成一块灰白格子布。所以必须自己抠。

抠图算法（为什么不是"删白色像素"）
----------------------------------
不能按"接近白色 → 删"来做：羽虫/毛虫本体就是浅色，会被啃掉半个身子。
本脚本用 **边缘连通洪水填充**：

1. 边缘像素的颜色（16 级量化）构成背景色集合 —— 棋盘格的两色、纯色底的色、
   渐变底的每个色阶都必然出现在四条边上；
2. 颜色落在该集合内的像素形成候选区，只保留**与图像边界连通**的部分作为背景；
3. 宠物本体自身的浅色即使与底色同色，也会因为被深色轮廓包围而不连通，
   从而安全保留。

结果再用连通域清理（丢掉 <1% 面积的孤立小岛 = 水印残渣），最后等比缩放。

用法
----
    python tools/prepare_pet_png.py            # 落地到 res/
    python tools/prepare_pet_png.py --preview  # 只出预览拼图，不写 res/

依赖：Pillow / numpy / scipy（已装在 WorkBuddy 托管 venv 里）。
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

ROOT = Path(__file__).resolve().parent.parent
SRC_DIR = ROOT / "generated-images"
RES_DIR = ROOT / "app" / "src" / "main" / "res"
OUT_DIR = RES_DIR / "drawable-nodpi"
ART_DIR = ROOT / "docs" / "art"

# ── 生成原图 → 资源名 映射（文件名取生成结果的唯一可辨前缀）────────────────
MANIFEST = {
    # ADULT：成年体，要求更帅更酷（docs/15 §四 ADULT = 母题完整、记忆点最清晰）
    "pet_luo_adult": "v3/Adorable_elegant_3D_rendered_g_2026-10-22T07-48-00.png",
    "pet_lin_adult": "v3/Majestic_heroic_3D_rendered_ga_2026-10-22T07-47-58.png",
    "pet_mao_adult": "v3/Proud_handsome_3D_rendered_gam_2026-10-22T07-47-59.png",
    "pet_yu_adult": "v3/Elegant_graceful_3D_rendered_g_2026-10-22T07-48-04.png",
    "pet_kun_adult": "v3/Heroic_mighty_3D_rendered_game_2026-10-22T07-48-06.png",
    "pet_uncat_adult": "v3/Mystical_elegant_3D_rendered_g_2026-10-22T07-48-02.png",
    # BABY：眼镜占比最大（docs/15 §四 BABY 头大眼大）
    "pet_luo_baby": "v3/Adorable_tiny_3D_rendered_game_2026-10-22T07-49-27.png",
    "pet_lin_baby": "v3/Adorable_tiny_3D_rendered_game_2026-10-22T07-49-29.png",
    "pet_mao_baby": "v3fix/Fluffy_kitten_cub_toy_figure___2026-10-22T07-54-23.png",
    "pet_yu_baby": "v3fix/Round_baby_bluebird_toy_figure_2026-10-22T07-54-26.png",
    "pet_kun_baby": "v3/Adorable_tiny_3D_rendered_game_2026-10-22T07-49-34.png",
    "pet_uncat_baby": "v3/Adorable_tiny_3D_rendered_game_2026-10-22T07-49-28.png",
    # JUVENILE：开始长出类群母题（鳞/羽/毛/甲）
    "pet_luo_juvenile": "v3/Soft_young_pet_character__a_yo_2026-10-22T07-50-58.png",
    "pet_lin_juvenile": "v3/Sleek_young_pet_character__a_y_2026-10-22T07-50-51.png",
    "pet_mao_juvenile": "v3/Fluffy_young_pet_character__a__2026-10-22T07-50-57.png",
    "pet_yu_juvenile": "v3/Feathered_young_pet_character__2026-10-22T07-50-59.png",
    "pet_kun_juvenile": "v3fix/Young_scarab_beetle_toy_figure_2026-10-22T07-54-25.png",
    "pet_uncat_juvenile": "v3fix/Young_spirit_beast_toy_figure__2026-10-22T07-54-28.png",
}

CANVAS = 512          # 输出画布边长（px，nodpi → 1px = 1dp，512dp 足以覆盖最大显示）
SUBJECT_RATIO = 0.86  # 主体占画布比例（统一视觉大小，场景里不会一大一小）
QUANT_BITS = 4        # 背景色量化位数：色差容差 = 2^4 = 16/255
MIN_ISLAND = 0.01     # 小于主体面积 1% 的连通域视为残渣（水印碎点）


def background_mask(rgb: np.ndarray) -> np.ndarray:
    """边缘连通洪水填充，返回 True=背景。"""
    h, w, _ = rgb.shape
    q = (rgb >> QUANT_BITS).astype(np.int16)
    key = (q[..., 0] << 8) | (q[..., 1] << 4) | q[..., 2]

    border = np.concatenate([key[0, :], key[-1, :], key[:, 0], key[:, -1]])
    # 边界上出现 ≥ 4 次（约 0.1% 边长）的色阶才算背景色，避免宠物贴边时被误收
    values, counts = np.unique(border, return_counts=True)
    bg_keys = set(values[counts >= max(4, border.size // 200)].tolist())

    candidate = np.isin(key, list(bg_keys))
    labels, n = ndimage.label(candidate)
    if n == 0:
        return np.zeros((h, w), bool)

    edge_labels = np.unique(
        np.concatenate([labels[0, :], labels[-1, :], labels[:, 0], labels[:, -1]])
    )
    edge_labels = edge_labels[edge_labels != 0]
    return np.isin(labels, edge_labels)


def cutout(img: Image.Image) -> Image.Image:
    """抠底 → 连通域清理 → 裁 bbox → 归一化到 CANVAS 画布。"""
    img = img.convert("RGBA")
    rgb = np.asarray(img)[..., :3]

    bg = background_mask(rgb)
    alpha = np.where(bg, 0, 255).astype(np.uint8)

    # α 边缘羽化 1px：棋盘格硬边留在轮廓上会变成一圈白毛刺
    soft = ndimage.uniform_filter(alpha.astype(np.float32), size=3)
    alpha = np.clip(soft + 30, 0, 255).astype(np.uint8)
    alpha[bg] = 0

    # 连通域清理：只留面积 > 1% 的块（丢掉水印残渣）
    solid, n = ndimage.label(alpha > 40)
    if n > 1:
        areas = ndimage.sum(alpha > 40, solid, range(1, n + 1))
        keep = {i + 1 for i, a in enumerate(areas) if a >= areas.max() * MIN_ISLAND}
        alpha = np.where(np.isin(solid, list(keep)), alpha, 0).astype(np.uint8)

    rgba = np.dstack([rgb, alpha])
    ys, xs = np.where(alpha > 8)
    if len(ys) == 0:
        raise RuntimeError("抠底后主体为空 —— 背景色判定吃掉了整只宠物")
    crop = rgba[ys.min(): ys.max() + 1, xs.min(): xs.max() + 1]

    sub = Image.fromarray(crop, "RGBA")
    target = int(CANVAS * SUBJECT_RATIO)
    scale = target / max(sub.width, sub.height)
    sub = sub.resize(
        (max(1, round(sub.width * scale)), max(1, round(sub.height * scale))),
        Image.LANCZOS,
    )

    canvas = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    canvas.paste(sub, ((CANVAS - sub.width) // 2, (CANVAS - sub.height) // 2), sub)
    return canvas


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", action="store_true", help="只生成预览拼图，不写 res/")
    args = ap.parse_args()

    missing = [f for f in MANIFEST.values() if not (SRC_DIR / f).exists()]
    if missing:
        print("缺少原图:\n  " + "\n  ".join(missing), file=sys.stderr)
        return 1

    if not args.preview:
        OUT_DIR.mkdir(parents=True, exist_ok=True)

    done = []
    for name, filename in MANIFEST.items():
        out = cutout(Image.open(SRC_DIR / filename))
        if not args.preview:
            out.save(OUT_DIR / f"{name}.png", optimize=True)
            # 同名 VectorDrawable 占位必须删掉，否则两个资源同名 → aapt 报重复
            stale = RES_DIR / "drawable" / f"{name}.xml"
            if stale.exists():
                stale.unlink()
                print(f"  删除占位 {stale.relative_to(ROOT)}")
        done.append((name, out))
        print(f"  {name:22s} ← {filename[:34]}")

    # 预览拼图（人眼验收：36 格 6×6，便于检查类群辨识度与风格一致性）
    cols, cell = 6, 200
    rows = (len(done) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell, rows * cell), (247, 248, 250, 255))
    for i, (_, im) in enumerate(done):
        thumb = im.copy()
        thumb.thumbnail((cell - 16, cell - 16), Image.LANCZOS)
        sheet.paste(thumb, ((i % cols) * cell + 8, (i // cols) * cell + 8), thumb)
    ART_DIR.mkdir(parents=True, exist_ok=True)
    sheet.convert("RGB").save(ART_DIR / "pet-v3-preview.png")
    print(f"\n预览 → docs/art/pet-v3-preview.png（{len(done)} 个形态）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
