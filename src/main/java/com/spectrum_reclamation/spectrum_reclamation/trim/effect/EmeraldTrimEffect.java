package com.spectrum_reclamation.spectrum_reclamation.trim.effect;

import com.spectrum_reclamation.spectrum_reclamation.trim.TrimEffectHandler;
import net.minecraft.world.entity.LivingEntity;

/**
 * 绿宝石纹饰效果处理器。
 *
 * 材料：minecraft:emerald
 * 效果：村民交易价格 -5%/件
 *
 * 当前为空实现。完整实现需要监听 VillagerTradesEvent 或
 * MerchantOffer 的价格计算，在交易菜单打开时修改价格。
 *
 * NeoForge 1.21.x 中村民交易的修改方式：
 * - 方案 A：监听 VillagerTradesEvent 修改交易列表（静态，不会根据纹饰动态变化）
 * - 方案 B：MixIn MerchantOffer 的 getBaseCostA() 方法（动态，但侵入性强）
 * - 方案 C：通过事件修改最终价格（理想方案，但需要找到合适的事件钩子）
 *
 * // CONCERN: [RISK] 村民交易价格修改在 NeoForge 1.21.x 中缺乏干净的事件钩子。
 * // VillagerTradesEvent 只在交易列表初始化时触发，无法根据纹饰件数动态调整。
 * // 建议在 Phase 7（Spectrum 材料）或后续 Phase 中统一实现，或使用 Mixin 方案。
 */
public class EmeraldTrimEffect implements TrimEffectHandler {

    /**
     * 暂为空实现 —— 村民交易价格修改需要特殊事件处理。
     *
     * @param entity 拥有纹饰效果的实体
     * @param count  绿宝石纹饰的盔甲件数（0-4）
     */
    @Override
    public void onTick(LivingEntity entity, int count) {
        // 暂为空实现：村民交易事件在 NeoForge 1.21.x 中缺乏干净的事件钩子，
        // 需要后续 Phase 确定最佳实现方案后再补充。
    }
}
