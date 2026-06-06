package com.spectrum_reclamation.spectrum_reclamation;

import com.spectrum_reclamation.spectrum_reclamation.registry.SRCreativeModeTabs;
import com.spectrum_reclamation.spectrum_reclamation.registry.SRTrimMaterials;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 模组主类 —— Spectrum Reclamation 的入口。
 * 负责将所有 DeferredRegister 注册到模组事件总线（MOD_BUS），
 * 确保物品栏、纹饰材料等注册项在正确的生命周期阶段被 NeoForge 处理。
 *
 * @Mod 注解标记此类为模组入口，NeoForge 加载器在发现该注解后会实例化此类。
 */
@Mod(SpectrumReclamation.MOD_ID)
public class SpectrumReclamation {

    /** 模组 ID，全局唯一标识符，与 gradle.properties 和 mods.toml 中的 mod_id 保持一致 */
    public static final String MOD_ID = "spectrum_reclamation";

    /** 日志记录器，输出调试和运行信息 */
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /**
     * 模组构造器 —— 由 NeoForge 在模组初始化阶段自动调用。
     * 参数 IEventBus 由 NeoForge 注入，即 MOD_BUS（模组事件总线）。
     *
     * NeoForge 有两条事件总线：
     * - MOD_BUS：处理注册类事件（DeferredRegister、FMLCommonSetupEvent 等）
     * - GAME_BUS：处理运行时事件（玩家交互、实体事件等）
     * 所有 DeferredRegister 必须注册到 MOD_BUS，否则注册项不会生效。
     *
     * @param modEventBus 模组事件总线（MOD_BUS），由 NeoForge 自动注入
     */
    public SpectrumReclamation(IEventBus modEventBus) {
        // 注册创造模式物品栏到 MOD_BUS
        SRCreativeModeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        // 注册纹饰材料到 MOD_BUS
        SRTrimMaterials.TRIM_MATERIALS.register(modEventBus);

        LOGGER.info("Spectrum Reclamation 模组初始化完成");
    }
}
