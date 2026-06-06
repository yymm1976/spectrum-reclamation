package com.spectrum_reclamation.spectrum_reclamation.registry;

import com.spectrum_reclamation.spectrum_reclamation.SpectrumReclamation;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.armortrim.TrimMaterial;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Map;

/**
 * 纹饰材料（TrimMaterial）注册类。
 * 纹饰材料用于盔甲纹饰系统，决定纹饰的颜色和外观。
 * 使用 DeferredRegister 延迟注册，确保在注册阶段由 NeoForge 统一处理。
 *
 * 在 1.21.x 中，TrimMaterial 是 record 类型，各字段含义：
 * - assetName: 调色板资源名，对应 trims/color_palettes/<asset_name>.png
 * - ingredient: 锻造所需材料（Holder&lt;Item&gt; 类型）
 * - itemModelIndex: 颜色索引，决定从调色板中选取哪一列颜色
 * - overrideArmorMaterials: 可选的盔甲材质纹理覆盖映射（键为 Holder&lt;ArmorMaterial&gt;）
 * - description: 显示名称（Component 类型）
 *
 * 推荐使用静态工厂方法 TrimMaterial.create(assetName, Item, itemModelIndex, description, overrides)，
 * 它内部会自动将 Item 转为 Holder&lt;Item&gt;，参数顺序更直观。
 */
public class SRTrimMaterials {

    /**
     * 纹饰材料的 DeferredRegister。
     * Registries.TRIM_MATERIAL 是 1.21.x 中纹饰材料的注册表键。
     */
    public static final DeferredRegister<TrimMaterial> TRIM_MATERIALS =
            DeferredRegister.create(Registries.TRIM_MATERIAL, SpectrumReclamation.MOD_ID);

    /**
     * 测试纹饰材料 —— test_trim_material。
     *
     * 参数说明：
     * - assetName = "test_palette"
     *   调色板纹理路径为 assets/spectrum_reclamation/trims/color_palettes/test_palette.png
     *   （注意：1.21.x 的标准路径是 trims/color_palettes/，若实际路径不同需相应调整文件位置）
     * - ingredient = 铁锭（IRON_INGOT），作为锻造材料
     * - itemModelIndex = 0.5f，决定纹饰在调色板中的颜色选择
     * - overrideArmorMaterials = 空映射，不覆盖任何盔甲材质纹理
     * - description = 使用翻译键，实际文本由语言文件中 trim_material.spectrum_reclamation.test_trim_material 决定
     */
    public static final DeferredHolder<TrimMaterial, TrimMaterial> TEST_TRIM_MATERIAL =
            TRIM_MATERIALS.register(
                    "test_trim_material",
                    // 使用 TrimMaterial.create 静态工厂方法，内部自动将 Item 包装为 Holder<Item>
                    // create 参数顺序：assetName, ingredient(Item), itemModelIndex, description, overrides
                    () -> TrimMaterial.create(
                            "test_palette",                                                          // 调色板资源名
                            Items.IRON_INGOT,                                                        // 锻造材料：铁锭
                            0.5f,                                                                    // 模型颜色索引
                            Component.translatable("trim_material.spectrum_reclamation.test_trim_material"), // 显示名称
                            Map.of()                                                                 // 不覆盖盔甲材质纹理
                    )
            );
}
