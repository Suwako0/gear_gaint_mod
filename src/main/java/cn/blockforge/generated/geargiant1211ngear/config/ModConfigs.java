package cn.blockforge.generated.geargiant1211ngear.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 模组配置（NeoForge COMMON 类型，写入 config/gear_giant.toml）。
 * 属性类参数在巨人下一次生成/读档时生效；技能倍率、回血与音乐参数实时读取。
 */
public final class ModConfigs {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue GIANT_MAX_HEALTH;
    public static final ModConfigSpec.IntValue GIANT_ATTACK_DAMAGE;
    public static final ModConfigSpec.IntValue GIANT_ARMOR;
    public static final ModConfigSpec.IntValue GIANT_ARMOR_TOUGHNESS;
    public static final ModConfigSpec.DoubleValue GIANT_MOVEMENT_SPEED;
    public static final ModConfigSpec.DoubleValue SKILL_DAMAGE_SCALE;
    public static final ModConfigSpec.IntValue GIANT_PHASE_INVULN_TICKS;
    public static final ModConfigSpec.BooleanValue GIANT_HOOK_ENABLED;
    public static final ModConfigSpec.IntValue GIANT_HOOK_DAMAGE;
    public static final ModConfigSpec.IntValue GIANT_HOOK_RANGE;
    public static final ModConfigSpec.IntValue GIANT_HOOK_MIN_DISTANCE;
    public static final ModConfigSpec.IntValue GIANT_HOOK_COOLDOWN;
    public static final ModConfigSpec.BooleanValue GEARLING_HEALING;
    public static final ModConfigSpec.DoubleValue GEARLING_HEAL_PER_SECOND;
    public static final ModConfigSpec.IntValue GEARLING_HEAL_LINK_CAP;
    public static final ModConfigSpec.BooleanValue BATTLE_MUSIC;
    public static final ModConfigSpec.DoubleValue BATTLE_MUSIC_VOLUME;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("齿轮巨人 BOSS 战斗参数").push("boss");
        GIANT_MAX_HEALTH = builder
                .comment("齿轮巨人的最大生命值（点）。Boss 条与 50%/25% 阶段阈值都按它折算。",
                        "默认 1000 点；调小则整场战斗更快结束。")
                .defineInRange("giantMaxHealth", 1000, 20, 10_000_000);
        GIANT_ATTACK_DAMAGE = builder
                .comment("齿轮巨人的基础攻击力（点）：徒手砸击与冲撞撞击的伤害都从它出发。",
                        "在巨人下一次生成/读档时生效。")
                .defineInRange("giantAttackDamage", 14, 1, 1000);
        GIANT_ARMOR = builder
                .comment("齿轮巨人的护甲值（点）。16 点约等效 32% 物理减伤，请勿调得过高。",
                        "在巨人下一次生成/读档时生效。")
                .defineInRange("giantArmor", 12, 0, 30);
        GIANT_ARMOR_TOUGHNESS = builder
                .comment("齿轮巨人的护甲韧性（点），削弱高等级穿透附魔的破甲效果。")
                .defineInRange("giantArmorToughness", 6, 0, 20);
        GIANT_MOVEMENT_SPEED = builder
                .comment("齿轮巨人的基础移动速度（原版属性单位，铁傀儡约 0.25）。",
                        "在巨人下一次生成/读档时生效。")
                .defineInRange("giantMovementSpeed", 0.30D, 0.05D, 1.0D);
        SKILL_DAMAGE_SCALE = builder
                .comment("技能伤害倍率：作用于砸击震波、冲撞冲击波、蒸汽喷发、过载激光、",
                        "地裂齿轮、齿轮弹幕、跳跃砸地、旋风横扫等所有招式附加伤害。")
                .defineInRange("skillDamageScale", 1.75D, 0.0D, 10.0D);
        GIANT_PHASE_INVULN_TICKS = builder
                .comment("转阶段无敌窗口时长（刻，20 刻 = 1 秒）。默认 40 = 2 秒；填 0 关闭。",
                        "阶段切换瞬间起，巨人完全免疫伤害该时长；结束后仍进入 3 秒硬直窗口（受击 ×1.5）。")
                .defineInRange("giantPhaseInvulnTicks", 40, 0, 200);
        GIANT_HOOK_ENABLED = builder
                .comment("是否启用机械钩爪：巨人向远处放风筝的玩家掷爪，命中后拖到脚下甩摔。")
                .define("giantHookEnabled", true);
        GIANT_HOOK_DAMAGE = builder
                .comment("机械钩爪的基础伤害（命中一下 + 甩摔 ×0.8），仍会再乘 skillDamageScale。")
                .defineInRange("giantHookDamage", 5, 0, 40);
        GIANT_HOOK_RANGE = builder
                .comment("机械钩爪的出手距离上限（格）。目标太远时不会尝试出爪。")
                .defineInRange("giantHookRange", 30, 16, 64);
        GIANT_HOOK_MIN_DISTANCE = builder
                .comment("机械钩爪的出手距离下限（格）。目标比这更近时钩爪不出手。")
                .defineInRange("giantHookMinDistance", 12, 4, 32);
        GIANT_HOOK_COOLDOWN = builder
                .comment("机械钩爪冷却（刻，20 刻 = 1 秒）。三阶段自动缩短 25%。")
                .defineInRange("giantHookCooldown", 300, 60, 1200);
        GEARLING_HEALING = builder
                .comment("是否启用“齿轮仆从充能回血”：仆从存活时会持续把应力回灌给巨人。")
                .define("gearlingHealing", true);
        GEARLING_HEAL_PER_SECOND = builder
                .comment("每个与巨人保持连接的仆从，为巨人提供的每秒回血量（点/秒）。")
                .defineInRange("gearlingHealPerSecond", 1.0D, 0.0D, 20.0D);
        GEARLING_HEAL_LINK_CAP = builder
                .comment("回血计算最多计入的仆从数量（防止后期 6 只叠满过于夸张）。")
                .defineInRange("gearlingHealLinkCap", 4, 1, 12);
        builder.pop();

        builder.comment("BOSS 战音乐（客户端）").push("music");
        BATTLE_MUSIC = builder
                .comment("靠近存活的齿轮巨人时，是否自动播放专属战斗音乐。")
                .define("battleMusic", true);
        BATTLE_MUSIC_VOLUME = builder
                .comment("战斗音乐音量（0.0~1.0），仍受游戏“音乐”总音量控制。")
                .defineInRange("battleMusicVolume", 0.85D, 0.0D, 1.0D);
        builder.pop();

        SPEC = builder.build();
    }

    private ModConfigs() {
    }
}
