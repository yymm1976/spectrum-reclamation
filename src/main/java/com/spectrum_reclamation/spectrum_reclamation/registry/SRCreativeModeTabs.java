package com.spectrum_reclamation.spectrum_reclamation.registry;

import com.spectrum_reclamation.spectrum_reclamation.SpectrumReclamation;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 创造模式物品栏注册类。
 * 使用 DeferredRegister 延迟注册创造模式物品栏（CreativeModeTab），
 * 避免在静态初始化时直接操作注册表导致时序问题。
 *
 * DeferredRegister 会在 NeoForge 注册阶段统一处理所有注册项，
 * 确保注册顺序和依赖关系正确。
 */
public class SRCreativeModeTabs {

    /**
     * 创造模式物品栏的 DeferredRegister。
     * Registries.CREATIVE_MODE_TAB 是 1.21.x 中创造模式物品栏的注册表键。
     * 命名空间设为模组 ID，所有在此注册器中注册的物品栏都会使用 spectrum_reclamation 作为命名空间。
     */
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SpectrumReclamation.MOD_ID);

    /**
     * 主创造模式物品栏 —— "Spectrum Reclamation"。
     * 图标暂时使用原版钻石，后续可替换为模组自定义物品。
     * displayItems 回调暂不添加物品，待物品注册完成后再补充。
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SPECTRUM_RECLAMATION_TAB =
            CREATIVE_MODE_TABS.register(
                    "spectrum_reclamation",
                    () -> CreativeModeTab.builder()
                            // 物品栏图标：使用炽光炸弹
                            .icon(() -> new ItemStack(SRItems.BLAZING_BOMB.get()))
                            // 物品栏显示名称，翻译键需在语言文件中定义
                            .title(Component.translatable("itemGroup.spectrum_reclamation"))
                            // 物品展示回调，添加模组物品到创造模式标签
                            .displayItems((parameters, output) -> {
                                // 炽光炸弹
                                output.accept(SRItems.BLAZING_BOMB.get());
                                // 活体陷阱
                                output.accept(SRItems.LIVING_TRAP.get());
                                // 铜管
                                output.accept(SRItems.COPPER_PIPE.get());
                                // 铜管接口
                                output.accept(SRItems.COPPER_PIPE_ENDPOINT.get());
                                // 瞄准镜
                                output.accept(SRItems.SCOPE_ATTACHMENT.get());
                                // 追溯指针
                                output.accept(SRItems.WAYPOINT_COMPASS.get());
                                // 精准追溯指针
                                output.accept(SRItems.PRECISE_WAYPOINT_COMPASS.get());
                                // 沉重之矛
                                output.accept(SRItems.HEAVY_SPEAR.get());
                                // 陨星弩
                                output.accept(SRItems.METEOR_CROSSBOW.get());
                            })
                            .build()
            );
}
