package com.spectrum_reclamation.spectrum_reclamation.trim;

import com.spectrum_reclamation.spectrum_reclamation.trim.effect.AmethystTrimEffect;
import com.spectrum_reclamation.spectrum_reclamation.trim.effect.DiamondTrimEffect;
import com.spectrum_reclamation.spectrum_reclamation.trim.effect.EchoShardTrimEffect;
import com.spectrum_reclamation.spectrum_reclamation.trim.effect.EmeraldTrimEffect;
import com.spectrum_reclamation.spectrum_reclamation.trim.effect.GoldTrimEffect;
import com.spectrum_reclamation.spectrum_reclamation.trim.effect.HoneycombTrimEffect;
import com.spectrum_reclamation.spectrum_reclamation.trim.effect.IronTrimEffect;
import com.spectrum_reclamation.spectrum_reclamation.trim.effect.LapisTrimEffect;
import com.spectrum_reclamation.spectrum_reclamation.trim.effect.MidnightChipTrimEffect;
import com.spectrum_reclamation.spectrum_reclamation.trim.effect.NetheriteTrimEffect;
import com.spectrum_reclamation.spectrum_reclamation.trim.effect.OnyxPowderTrimEffect;
import com.spectrum_reclamation.spectrum_reclamation.trim.effect.QuitoxicPowderTrimEffect;
import com.spectrum_reclamation.spectrum_reclamation.trim.effect.QuartzTrimEffect;
import com.spectrum_reclamation.spectrum_reclamation.trim.effect.RedstoneTrimEffect;
import com.spectrum_reclamation.spectrum_reclamation.trim.effect.TurtleTrimEffect;
import net.minecraft.resources.ResourceLocation;

/**
 * 纹饰效果的批量注册入口。
 *
 * 职责：将所有纹饰材料（12 种原版 + 3 种 Spectrum）的效果处理器统一注册到 TrimEffectRegistry。
 * 调用时机：在模组主类 SpectrumReclamation 的构造器中调用 register()。
 *
 * 为什么单独拆分为此类：
 * - 主类构造器已包含大量注册调用，保持整洁
 * - 注册逻辑与效果实现分离，符合 SRP 原则
 *
 * 纹饰材料 ResourceLocation 对应关系（原版 1.21.x）：
 * - minecraft:quartz     → 石英    （近战伤害 +2%/件）
 * - minecraft:iron       → 铁      （盔甲韧性 +1/件）
 * - minecraft:gold       → 金      （幸运 +1/件）
 * - minecraft:diamond    → 钻石    （暴击伤害加成）
 * - minecraft:netherite  → 下界合金 （击退抗性）
 * - minecraft:emerald    → 绿宝石  （村民交易折扣，待实现）
 * - minecraft:redstone   → 红石    （攻击速度加成）
 * - minecraft:lapis      → 青金石  （经验加成 +8%/件）
 * - minecraft:amethyst   → 紫水晶  （音效增强）
 * - minecraft:turtle     → 海龟    （水下呼吸延长）
 * - minecraft:honeycomb  → 蜜脾    （摔落减免 -1 格/件）
 * - minecraft:echo_shard → 回声碎片（声呐探测）
 * - spectrum_reclamation:onyx_powder      → 黑曜石粉 （对满血目标首击 +8%/件）
 * - spectrum_reclamation:midnight_chip    → 午夜碎片 （攻击无视目标 6%/件 护甲）
 * - spectrum_reclamation:quitoxic_powder  → 毒紫粉   （被攻击时攻击者中毒，每件 +1 等级）
 */
public class VanillaTrimEffects {

    /**
     * 批量注册所有原版纹饰效果到 TrimEffectRegistry。
     *
     * 每次调用 TrimEffectRegistry.register() 将一个效果处理器实例
     * 与对应的纹饰材料 ResourceLocation 绑定。
     *
     * 注意：此方法必须在模组初始化阶段调用（MOD_BUS 事件期间），
     * 不可在游戏运行时调用。
     */
    public static void register() {
        // 石英纹饰 —— 近战攻击伤害 +2%/件
        TrimEffectRegistry.register(
                ResourceLocation.fromNamespaceAndPath("minecraft", "quartz"),
                new QuartzTrimEffect()
        );

        // 铁纹饰 —— 盔甲韧性加成（属性修饰器方式）
        TrimEffectRegistry.register(
                ResourceLocation.fromNamespaceAndPath("minecraft", "iron"),
                new IronTrimEffect()
        );

        // 金纹饰 —— 幸运属性加成
        TrimEffectRegistry.register(
                ResourceLocation.fromNamespaceAndPath("minecraft", "gold"),
                new GoldTrimEffect()
        );

        // 钻石纹饰 —— 暴击伤害加成
        TrimEffectRegistry.register(
                ResourceLocation.fromNamespaceAndPath("minecraft", "diamond"),
                new DiamondTrimEffect()
        );

        // 下界合金纹饰 —— 击退抗性
        TrimEffectRegistry.register(
                ResourceLocation.fromNamespaceAndPath("minecraft", "netherite"),
                new NetheriteTrimEffect()
        );

        // 绿宝石纹饰 —— 村民交易折扣（待实现）
        TrimEffectRegistry.register(
                ResourceLocation.fromNamespaceAndPath("minecraft", "emerald"),
                new EmeraldTrimEffect()
        );

        // 红石纹饰 —— 攻击速度加成
        TrimEffectRegistry.register(
                ResourceLocation.fromNamespaceAndPath("minecraft", "redstone"),
                new RedstoneTrimEffect()
        );

        // 青金石纹饰 —— 击杀经验 +8%/件
        TrimEffectRegistry.register(
                ResourceLocation.fromNamespaceAndPath("minecraft", "lapis"),
                new LapisTrimEffect()
        );

        // 紫水晶纹饰 —— 音效增强
        TrimEffectRegistry.register(
                ResourceLocation.fromNamespaceAndPath("minecraft", "amethyst"),
                new AmethystTrimEffect()
        );

        // 海龟纹饰 —— 水下呼吸延长
        TrimEffectRegistry.register(
                ResourceLocation.fromNamespaceAndPath("minecraft", "turtle"),
                new TurtleTrimEffect()
        );

        // 蜜脾纹饰 —— 摔落有效高度 -1 格/件
        TrimEffectRegistry.register(
                ResourceLocation.fromNamespaceAndPath("minecraft", "honeycomb"),
                new HoneycombTrimEffect()
        );

        // 回声碎片纹饰 —— 声呐探测效果
        TrimEffectRegistry.register(
                ResourceLocation.fromNamespaceAndPath("minecraft", "echo_shard"),
                new EchoShardTrimEffect()
        );

        // ==================== Spectrum 纹饰效果 ====================

        // 黑曜石粉纹饰 —— 对满血目标首击 +8%/件
        TrimEffectRegistry.register(
                ResourceLocation.fromNamespaceAndPath("spectrum_reclamation", "onyx_powder"),
                new OnyxPowderTrimEffect()
        );

        // 午夜碎片纹饰 —— 攻击无视目标 6%/件 护甲
        TrimEffectRegistry.register(
                ResourceLocation.fromNamespaceAndPath("spectrum_reclamation", "midnight_chip"),
                new MidnightChipTrimEffect()
        );

        // 毒紫粉纹饰 —— 被攻击时，攻击者中毒；每件 +1 中毒等级
        TrimEffectRegistry.register(
                ResourceLocation.fromNamespaceAndPath("spectrum_reclamation", "quitoxic_powder"),
                new QuitoxicPowderTrimEffect()
        );
    }
}
