# -*- coding: utf-8 -*-
"""
SproutPet 获取路径与周期测算

目的：把「普通宠物 → 灵兽 / 异兽 / 神兽」的获取成本（配对次数、代数、天数）算清楚。

数值来源全部对齐 core 层代码：
  - core/domain/pet/Rarity.kt
  - core/domain/pet/GrowthRules.kt   (BreedingRules / ExoticBloodRules / PairingRules)
  - core/domain/pet/PetSpecies.kt

用法： python acquisition_sim.py
"""

import random
import statistics
from collections import Counter

# ─────────────────────────── 数值常量（与 Kotlin 对齐） ───────────────────────────

RARITY_NAME = ["普通", "稀有", "史诗", "传说", "神话", "灵兽"]
COMMON, RARE, EPIC, LEGENDARY, MYTHIC, MUTANT = range(6)

# BreedingRules.RARITY_DISTRIBUTION —— key = 父母中较高的稀有度
DIST = {
    COMMON:    {COMMON: 0.60, RARE: 0.33, EPIC: 0.06, LEGENDARY: 0.01},
    RARE:      {RARE: 0.50, EPIC: 0.35, LEGENDARY: 0.13, MYTHIC: 0.02},
    EPIC:      {EPIC: 0.45, LEGENDARY: 0.38, MYTHIC: 0.15, MUTANT: 0.02},
    LEGENDARY: {LEGENDARY: 0.45, MYTHIC: 0.45, MUTANT: 0.10},
    MYTHIC:    {LEGENDARY: 0.05, MYTHIC: 0.85, MUTANT: 0.10},
    MUTANT:    {MYTHIC: 0.10, MUTANT: 0.90},
}

# PairingKind 参数
SAME_FAMILY_BONUS, CROSS_FAMILY_BONUS = 1.00, 1.35
EXOTIC_MIX_BONUS, EXOTIC_PAIR_BONUS = 1.80, 2.00
SAME_FAMILY_SUCCESS, CROSS_FAMILY_SUCCESS = 1.00, 0.85
EXOTIC_MIX_SUCCESS, EXOTIC_PAIR_SUCCESS = 0.90, 1.00
EXOTIC_RATE = {  # 产出「不入十类之种」的概率
    "SAME_FAMILY": 0.01,
    "CROSS_FAMILY": 0.04,
    "EXOTIC_MIX": 0.12,
    "EXOTIC_PAIR": 0.40,
}

# ExoticBloodRules
MAX_BLOOD = 3
AWAKEN_RATE = {0: 0.00, 1: 0.03, 2: 0.08, 3: 0.15}

# MutantPityRules（S15 冷启动保底）
# ⚠️ 参数是**实测调出来的**：初版 30/1%/25% 在实测里几乎不触发
#    （命中灵兽时 EPIC+ 连击数中位仅 10 次/单线、16 次/三线），
#    改为 20/2%/40% 后 p99 从 54→46（单线）、71→66（三线）。
PITY_THRESHOLD = 20      # 连续这么多次「史诗及以上」配对未出灵兽后开始补偿
PITY_STEP = 0.02         # 每次 +2%
MAX_PITY_RATE = 0.40     # 封顶 40%

# PairingRules
COOLDOWN_DAYS = 3          # 每只宠物两次配对之间的冷却（v2.1：7 → 3 天）
MAX_OFFSPRING = 5          # 每只宠物最多后代数（搭档为真人）
MAX_NPC_OFFSPRING = 3      # 搭档为 NPC 时的上限（S16：让真人配对明显更划算）
REQUIRED_INTIMACY = 70
PAIR_COST_GOLD = 3000

# GrowthRules 等级曲线
MAX_LEVEL = 60
def exp_to_next(level):
    if level >= MAX_LEVEL:
        return float("inf")
    return 50 + level * level * 8 + level * 30

def cumulative_exp(target_level):
    return sum(exp_to_next(l) for l in range(0, target_level))

# 各阶段门槛（stageLevelRange）
ADULT_LEVEL = 26
ELDER_LEVEL = 46

# 互动每日经验上限（InteractionType：exp × dailyLimit）
# v2.1：全部 exp 上调为 3 倍（ADR-024 / 08 §六 方案 B）
INTERACTION_EXP_MULTIPLIER = 3
DAILY_EXP_FROM_INTERACTIONS_OLD = (
    5 * 50 +    # 抚摸
    20 * 20 +   # 喂食
    35 * 15 +   # 玩耍
    50 * 8 +    # 训练
    10 * 10 +   # 清洁
    2 * 40      # 桌面互动
)
DAILY_EXP_FROM_INTERACTIONS = DAILY_EXP_FROM_INTERACTIONS_OLD * INTERACTION_EXP_MULTIPLIER
DAILY_EXP_CAP = 6000

# 旧值，用于对比
COOLDOWN_DAYS_OLD = 7
DAILY_EXP_CAP_OLD = 5000


# ─────────────────────────── 概率工具 ───────────────────────────

def table_of(base, bonus):
    """把分布表按向上档位加成后归一化。"""
    d = DIST[base]
    w = {r: (p * (bonus if r > base else 1.0)) for r, p in d.items()}
    t = sum(w.values())
    return {r: p / t for r, p in w.items()}

def sample(table, rnd):
    x = rnd.random()
    acc = 0.0
    for r in sorted(table):
        acc += table[r]
        if x < acc:
            return r
    return max(table)

def blood_adjusted_rates(rates, blood):
    """先漂移、后觉醒：P(灵兽) = p + (1-p) * awaken(blood)。"""
    a = AWAKEN_RATE[min(blood, MAX_BLOOD)]
    return {r: (p + (1 - p) * a if r == MUTANT else p) for r, p in rates.items()}


def pity_bonus(fail_streak):
    """MutantPityRules.pityBonusOf —— 未达门槛返回 0。"""
    if fail_streak < PITY_THRESHOLD:
        return 0.0
    return min(MAX_PITY_RATE, (fail_streak - PITY_THRESHOLD + 1) * PITY_STEP)


# ─────────────────────────── 一、概率表 ───────────────────────────

def report_tables():
    print("=" * 78)
    print("一、单次生育的后代稀有度分布（代码直出，已含向上加成）")
    print("=" * 78)
    print(f"{'父母最高档':<8}{'同虫 ×1.00':<26}{'跨虫 ×1.35':<26}{'×1.00→灵兽':>10}{'×1.35→灵兽':>11}")
    for base in range(6):
        t_same = table_of(base, SAME_FAMILY_BONUS)
        t_cross = table_of(base, CROSS_FAMILY_BONUS)

        def fmt(t):
            return " ".join(f"{RARITY_NAME[r]}{t[r]*100:.2f}%" for r in sorted(t))
        print(f"{RARITY_NAME[base]:<8}{fmt(t_same):<26}{fmt(t_cross):<26}"
              f"{t_same.get(5, 0)*100:>9.2f}%{t_cross.get(5, 0)*100:>10.2f}%")

    print()
    print("── 含「灵血觉醒」后的灵兽概率（神话 × 神话）──")
    base_table = table_of(MYTHIC, SAME_FAMILY_BONUS)
    for b in range(MAX_BLOOD + 1):
        adj = blood_adjusted_rates(base_table, b)
        print(f"   灵血 {b} 级 → 灵兽概率 {adj[MUTANT]*100:5.1f}%"
              f"   （50% 把握需 {_n_for_half(adj[MUTANT]):.1f} 次配对）")
    print()


def _n_for_half(p):
    if p <= 0:
        return float("inf")
    return 0.5 if p >= 1 else (0.6931 / -__import__("math").log(1 - p)) / 1.0


# ─────────────────────────── 二、马尔可夫期望（无后代上限） ───────────────────────────

def markov_expected_steps(bonus, start=COMMON):
    """E[成功生育次数] 从 start 到首次出现灵兽。线性方程组回代求解。"""
    n = 6
    P = [table_of(r, bonus) for r in range(6)]
    # E[i] = 1 + sum_j P[i][j] * E[j];  E[5] = 0
    A = [[0.0] * n for _ in range(n)]
    b = [1.0] * n
    for i in range(n):
        A[i][i] = 1.0
        for j, p in P[i].items():
            if j != 5:
                A[i][j] -= p
    b[5] = 0.0
    A[5] = [0.0] * n
    A[5][5] = 1.0
    # 高斯消元
    for c in range(n):
        piv = max(range(c, n), key=lambda r: abs(A[r][c]))
        A[c], A[piv] = A[piv], A[c]
        b[c], b[piv] = b[piv], b[c]
        for r in range(c + 1, n):
            f = A[r][c] / A[c][c]
            for k in range(c, n):
                A[r][k] -= f * A[c][k]
            b[r] -= f * b[c]
    E = [0.0] * n
    for i in range(n - 1, -1, -1):
        s = b[i] - sum(A[i][j] * E[j] for j in range(i + 1, n))
        E[i] = s / A[i][i]
    return E


# ─────────────────────────── 三、蒙卡：灵兽链（含 5 后代上限） ───────────────────────────

def sim_mutant_chain(trials=200000, bonus=CROSS_FAMILY_BONUS, success=CROSS_FAMILY_SUCCESS,
                     lanes=1, start=COMMON, seed=20261016, use_pity=True):
    """
    从 start 出发，模拟到首次获得灵兽。

    含两个真实约束：
      - 每只宠物最多 MAX_OFFSPRING 个后代（超了要换宠接棒）
      - S15 冷启动保底：连续 >=30 次「史诗及以上」配对未出灵兽后，
        每次额外 +1% 灵兽概率，封顶 25%
    """
    rnd = random.Random(seed)
    pairing_counts, gen_counts = [], []
    for _ in range(trials):
        r = start
        fail_streak = 0
        pairings = 0
        gens = 0
        hit_gen = None
        while gens < 500:
            gens += 1
            children = []
            for _lane in range(lanes):
                for _slot in range(MAX_OFFSPRING):
                    # 失败不占后代名额，只吃冷却
                    while True:
                        pairings += 1
                        if rnd.random() < success:
                            break
                    c = sample(table_of(r, bonus), rnd)
                    # 保底：只在前两步都没给出灵兽时才轮到它
                    if use_pity and c != MUTANT and pity_bonus(fail_streak) > 0:
                        if rnd.random() < pity_bonus(fail_streak):
                            c = MUTANT
                    # 推进/重置计数器
                    if c == MUTANT:
                        fail_streak = 0
                    elif r >= EPIC:          # 只有「史诗及以上」的配对才算数
                        fail_streak += 1
                    children.append(c)
                    if c == MUTANT:
                        hit_gen = gens
                        break
                if hit_gen:
                    break
            if hit_gen:
                break
            r = max(children) if children else r
        pairing_counts.append(pairings)
        gen_counts.append(gens)
    return pairing_counts, gen_counts


# ─────────────────────────── 四、蒙卡：异兽链（物种轴） ───────────────────────────

def sim_exotic_species_chain(trials=200000, kind="CROSS_FAMILY", lanes=1, seed=777):
    rate = EXOTIC_RATE[kind]
    success = {"SAME_FAMILY": SAME_FAMILY_SUCCESS, "CROSS_FAMILY": CROSS_FAMILY_SUCCESS,
               "EXOTIC_MIX": EXOTIC_MIX_SUCCESS, "EXOTIC_PAIR": EXOTIC_PAIR_SUCCESS}[kind]
    rnd = random.Random(seed)
    pairings_list, gens_list = [], []
    for _ in range(trials):
        pairings = 0
        gens = 0
        hit = False
        while gens < 500 and not hit:
            gens += 1
            for _lane in range(lanes):
                for _slot in range(MAX_OFFSPRING):
                    while True:
                        pairings += 1
                        if rnd.random() < success:
                            break
                        # 简化：失败视为不产蛋，重试
                    if rnd.random() < rate:
                        hit = True
                        break
                if hit:
                    break
        pairings_list.append(pairings)
        gens_list.append(gens)
    return pairings_list, gens_list


# ─────────────────────────── 三之二、保底参数校准 ───────────────────────────

def sim_streak_at_hit(trials=100000, bonus=CROSS_FAMILY_BONUS, success=CROSS_FAMILY_SUCCESS,
                      lanes=1, start=COMMON, seed=20261017):
    """
    命中灵兽那一刻，「史诗及以上配对」已连续积累了多少次。

    这个分布决定保底阈值该设在哪 —— 阈值若高于分布的 p95，保底就等于没配。
    """
    rnd = random.Random(seed)
    streaks = []
    for _ in range(trials):
        r = start
        streak = 0
        gens = 0
        hit = False
        while gens < 500 and not hit:
            gens += 1
            children = []
            for _lane in range(lanes):
                for _slot in range(MAX_OFFSPRING):
                    while True:
                        if rnd.random() < success:
                            break
                    c = sample(table_of(r, bonus), rnd)
                    if c == MUTANT:
                        hit = True
                        break
                    if r >= EPIC:
                        streak += 1
                    children.append(c)
                if hit:
                    break
            if hit:
                break
            r = max(children) if children else r
        streaks.append(streak)
    return streaks


def report_pity_calibration():
    print("=" * 78)
    print("三之二、保底阈值校准（为什么是 20 / 2% / 40%）")
    print("=" * 78)
    for lanes, label in ((1, "单线"), (3, "三线")):
        s = sim_streak_at_hit(lanes=lanes)
        print(f"   [{label}] 命中时 EPIC+ 连击数：中位 {pct(s,0.5):3d} | p75 {pct(s,0.75):3d} | "
              f"p90 {pct(s,0.90):3d} | p95 {pct(s,0.95):3d} | p99 {pct(s,0.99):3d}")
    print("   → 若阈值设 30，只有最倒霉的一小撮会触发，几乎等于没配。")
    print()
    print(f"   {'阈值/步长/封顶':<18}{'线':<4}{'均值':>7}{'中位':>6}{'p90':>6}{'p95':>6}{'p99':>6}")
    global PITY_THRESHOLD, PITY_STEP, MAX_PITY_RATE
    saved = (PITY_THRESHOLD, PITY_STEP, MAX_PITY_RATE)
    for th, step, cap in ((30, 0.01, 0.25), (20, 0.02, 0.40), (12, 0.03, 0.50)):
        PITY_THRESHOLD, PITY_STEP, MAX_PITY_RATE = th, step, cap
        for lanes in (1, 3):
            p, _ = sim_mutant_chain(lanes=lanes, use_pity=True, trials=60000)
            mark = "  ★ 已采纳" if th == 20 else ""
            print(f"   {f'{th}/{step}/{cap}':<18}{lanes:<4}{statistics.mean(p):>7.1f}"
                  f"{pct(p,0.5):>6}{pct(p,0.90):>6}{pct(p,0.95):>6}{pct(p,0.99):>6}{mark}")
        print()
    PITY_THRESHOLD, PITY_STEP, MAX_PITY_RATE = saved


# ─────────────────────────── 五、天数换算 ───────────────────────────

def mature_days(exp_per_day, adult_level=ADULT_LEVEL):
    return cumulative_exp(adult_level) / exp_per_day


def wall_clock_days(generations, exp_per_day, cooldown=COOLDOWN_DAYS, adult_level=ADULT_LEVEL):
    """
    墙钟天数。

    关键洞察：一代之内要把 5 个后代名额用完，需要 (5-1)×冷却 = 28 天；
    而培养到成年需要 T 天。两者**并行**，所以单代周期 ≈ max(T, 4×冷却)。
    末代还要额外跑完自己的配对。
    """
    T = mature_days(exp_per_day, adult_level)
    per_gen = max(T, (MAX_OFFSPRING - 1) * cooldown)
    return max(0.0, generations - 1) * per_gen + T + (MAX_OFFSPRING - 1) * cooldown, T, per_gen


# ─────────────────────────── 输出 ───────────────────────────

def pct(xs, q):
    s = sorted(xs)
    return s[min(len(s) - 1, int(len(s) * q))]

def main():
    report_tables()

    print("=" * 78)
    print("二、马尔可夫理论期望（不含「每只最多 5 个后代」限制，是理论下界）")
    print("=" * 78)
    for name, bonus in (("同虫 ×1.00", SAME_FAMILY_BONUS), ("跨虫 ×1.35", CROSS_FAMILY_BONUS)):
        E = markov_expected_steps(bonus)
        chain = " → ".join(f"{RARITY_NAME[i]}:{E[i]:.2f}" for i in range(6))
        print(f"{name}  从各档出发到灵兽的期望成功生育次数")
        print(f"   {chain}")
    print()

    print("=" * 78)
    print(f"三、蒙特卡洛：从「普通」出发，首次获得灵兽")
    print(f"    （含 {MAX_OFFSPRING} 后代上限 + {COOLDOWN_DAYS} 天冷却 + S15 冷启动保底）")
    print("=" * 78)
    rows = {}
    for lanes, label in ((1, "单线（1 只同时繁育）"), (3, "三线（桌面 3 只并行）")):
        for use_pity in (False, True):
            pairings, gens = sim_mutant_chain(lanes=lanes, use_pity=use_pity)
            tag = "含保底" if use_pity else "无保底"
            if use_pity:
                rows[lanes] = (label, pairings, gens)
            print(f"[{label} · {tag}]")
            print(f"   成功生育次数  均值 {statistics.mean(pairings):6.1f} | 中位 {pct(pairings,0.5):4d} | "
                  f"p90 {pct(pairings,0.90):4d} | p99 {pct(pairings,0.99):4d}")
            print(f"   代数（换宠次数）均值 {statistics.mean(gens):5.1f} | 中位 {pct(gens,0.5):4d} | "
                  f"p90 {pct(gens,0.90):4d}")
            print()

    print("=" * 78)
    print("四、蒙特卡洛：首次获得「异兽」（物种轴，不入十类之种）")
    print("=" * 78)
    for kind in ("SAME_FAMILY", "CROSS_FAMILY", "EXOTIC_MIX", "EXOTIC_PAIR"):
        pairings, gens = sim_exotic_species_chain(kind=kind)
        print(f"[{kind}] 单次概率 {EXOTIC_RATE[kind]*100:.0f}%")
        print(f"   成功生育次数 均值 {statistics.mean(pairings):6.1f} | 中位 {pct(pairings,0.5):4d} | "
              f"p90 {pct(pairings,0.90):4d}")
        print(f"   代数        均值 {statistics.mean(gens):5.1f} | 中位 {pct(gens,0.5):4d}")
        print()

    report_pity_calibration()

    print("=" * 78)
    print("五、天数换算（单代周期 ≈ max(成熟天数, (后代上限-1)×冷却)，两者并行）")
    print("=" * 78)
    need = cumulative_exp(ADULT_LEVEL)
    T_now = mature_days(DAILY_EXP_FROM_INTERACTIONS)
    T_old = mature_days(DAILY_EXP_FROM_INTERACTIONS_OLD)
    print(f"   升到成年({ADULT_LEVEL} 级) 累计经验 = {need:,}")
    print(f"   互动实际可得经验/天：调参前 {DAILY_EXP_FROM_INTERACTIONS_OLD:,}"
          f" → 调参后 {DAILY_EXP_FROM_INTERACTIONS:,}（×{INTERACTION_EXP_MULTIPLIER}）")
    print(f"   日经验上限 DAILY_EXP_CAP：{DAILY_EXP_CAP_OLD:,} → {DAILY_EXP_CAP:,}"
          f"（旧值低于实际可得，是个够不着的空配置）")
    print(f"   成熟天数：{T_old:.1f} 天/代 → {T_now:.1f} 天/代")
    print(f"   单代周期：{max(T_old, (MAX_OFFSPRING-1)*COOLDOWN_DAYS_OLD):.1f} 天"
          f" → {max(T_now, (MAX_OFFSPRING-1)*COOLDOWN_DAYS):.1f} 天")
    print(f"   （旧冷却瓶颈 {(MAX_OFFSPRING-1)*COOLDOWN_DAYS_OLD} 天，"
          f"新冷却跨度 {(MAX_OFFSPRING-1)*COOLDOWN_DAYS} 天）")
    print(f"   1000 抽池里的传说概率 = {30/1555*100:.2f}% / 神话 = {5/1555*100:.2f}%")
    print(f"   P(抽到指定某虫的传说) = {30/1555*3/15*100:.3f}% → 期望 {1555/30*5:.0f} 抽")
    print()

    same_p, same_g = sim_mutant_chain(bonus=SAME_FAMILY_BONUS, success=SAME_FAMILY_SUCCESS)
    cross_p, cross_g = sim_mutant_chain(bonus=CROSS_FAMILY_BONUS, success=CROSS_FAMILY_SUCCESS)
    p_cross, g_cross = sim_exotic_species_chain(kind="CROSS_FAMILY", lanes=3)
    p_pair, g_pair = sim_exotic_species_chain(kind="EXOTIC_PAIR")
    targets = [
        ("灵兽·同虫线（1 线）", statistics.mean(same_g)),
        ("灵兽·跨虫线（1 线）", statistics.mean(cross_g)),
        (f"灵兽·{rows[3][0]}", statistics.mean(rows[3][2])),
        ("异兽·跨虫（3 线）", statistics.mean(g_cross)),
        ("异兽·异兽互配（已有异兽后）", statistics.mean(g_pair)),
    ]

    pacing = [
        ("调参前（v2.1 之前）", DAILY_EXP_FROM_INTERACTIONS_OLD, COOLDOWN_DAYS_OLD, ADULT_LEVEL),
        ("★ 已采纳：方案B（经验×3 + 冷却3天）", DAILY_EXP_FROM_INTERACTIONS, COOLDOWN_DAYS, ADULT_LEVEL),
        ("备选：方案C（再降成年到 16 级）", DAILY_EXP_FROM_INTERACTIONS, COOLDOWN_DAYS, 16),
    ]
    for label, exp_day, cd, lvl in pacing:
        T = mature_days(exp_day, lvl)
        print(f"── {label}：{exp_day:,} 经验/天、冷却 {cd} 天、成年 {lvl} 级 "
              f"→ 成熟 {T:.1f} 天/代")
        for tname, gens in targets:
            d, _, pg = wall_clock_days(gens, exp_day, cd, lvl)
            print(f"       {tname:<28} {gens:4.1f} 代 → {d:5.0f} 天"
                  f"  (单代 {pg:.1f} 天)")
        print()

    print("=" * 78)
    print("六、单只宠物的配对容量")
    print("=" * 78)
    print(f"   真人搭档：最多后代数 {MAX_OFFSPRING} | 冷却 {COOLDOWN_DAYS} 天")
    print(f"   → 一只宠物在 第 0/{COOLDOWN_DAYS}/{2*COOLDOWN_DAYS}/"
          f"{3*COOLDOWN_DAYS}/{(MAX_OFFSPRING-1)*COOLDOWN_DAYS} 天各配对一次，"
          f"共 {MAX_OFFSPRING} 次，生涯横跨 {(MAX_OFFSPRING-1)*COOLDOWN_DAYS} 天")
    print(f"   NPC 搭档：最多后代数 {MAX_NPC_OFFSPRING}（S16 —— 比真人少 "
          f"{MAX_OFFSPRING - MAX_NPC_OFFSPRING} 个名额，确保「找真人配」更划算）")
    print(f"   → 想连续配对 25 次（跨虫异兽期望值）至少需要 {25/MAX_OFFSPRING:.1f} 只宠物接棒"
          f"（纯 NPC 则需 {25/MAX_NPC_OFFSPRING:.1f} 只）")
    print(f"   → 其他门槛：{PetStageAdultNote()}")
    print()

    print("=" * 78)
    print("七、神兽形态（ADR-026 之后，不再是抽卡赠品）")
    print("=" * 78)
    print("   旧规则：稀有度 >= 传说 → 自动带完整神兽形态（无门槛、无动作、无获得感）")
    print("   新规则：完整形态层 = 灵兽 × 同虫，即「灵兽·同虫线」的产物")
    print(f"   → 与「灵兽·同虫线」同一条路径，"
          f"期望 {statistics.mean(same_g):.1f} 代 / 中位 {pct(same_g, 0.5)} 代")
    print("   → 神兽特征层（翎羽描边等部分特征）仍由 稀有度 >= 传说 提供，保持抽卡回报")
    print()


def PetStageAdultNote():
    return (f"成年档({ADULT_LEVEL}级) + 亲密度≥{REQUIRED_INTIMACY} + "
            f"{PAIR_COST_GOLD} 金币 + 必须为不同主人")



if __name__ == "__main__":
    main()
