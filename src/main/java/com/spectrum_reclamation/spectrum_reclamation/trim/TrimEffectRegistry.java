package com.spectrum_reclamation.spectrum_reclamation.trim;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.armortrim.ArmorTrim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 纹饰效果注册表 —— 公开 API 层。
 *
 * 职责：
 * 1. 存储每种纹饰材料对应的 TrimEffectHandler 列表
 * 2. 提供注册方法供各效果模块调用
 * 3. 提供查询方法供事件总线分发器使用
 *
 * 存储结构：
 * - 键：ResourceLocation（纹饰材料的注册名，如 "minecraft:quartz"）
 * - 值：该材料关联的所有 TrimEffectHandler 列表（一种材料可有多个处理器）
 *
 * 线程安全说明：
 * - register() 仅在模组初始化阶段调用（MOD_BUS 事件），此时尚未有游戏线程访问
 * - lookup() 在游戏运行时（GAME_BUS 事件）被调用，此时 register() 不再执行
 * - 因此 HashMap 无需额外同步措施（典型的初始化-读取模式）
 *
 * 未来扩展：
 * - V2 可增加 JSON 数据驱动注册接口
 * - 可增加优先级排序机制
 */
public class TrimEffectRegistry {

    /**
     * 纹饰效果注册表。
     * 键：纹饰材料的 ResourceLocation（如 "minecraft:quartz"、"minecraft:diamond"）
     * 值：该材料关联的所有 TrimEffectHandler 列表
     */
    private static final Map<ResourceLocation, List<TrimEffectHandler>> REGISTRY = new HashMap<>();

    /**
     * 注册一个纹饰效果处理器。
     *
     * 公开 API —— 在模组初始化阶段调用，将特定纹饰材料的效果处理器注册到注册表。
     * 一种材料可以注册多个处理器（如铁锭既有属性修饰器效果，也可能有事件效果）。
     *
     * 调用时机：必须在 MOD_BUS 事件中调用（模组加载阶段），
     * 不可在游戏运行时调用。
     *
     * @param materialId 纹饰材料的 ResourceLocation（如 ResourceLocation.parse("minecraft:quartz")）
     * @param handler    效果处理器实例
     */
    public static void register(ResourceLocation materialId, TrimEffectHandler handler) {
        // 使用 computeIfAbsent 确保同一材料的多个处理器被聚合到同一个 List 中
        REGISTRY.computeIfAbsent(materialId, k -> new ArrayList<>()).add(handler);
    }

    /**
     * 根据纹饰材料 ID 查询所有注册的处理器。
     *
     * @param materialId 纹饰材料的 ResourceLocation
     * @return 该材料关联的处理器列表；若未注册则返回空列表（不会返回 null）
     */
    public static List<TrimEffectHandler> lookup(ResourceLocation materialId) {
        // getOrDefault 确保返回空列表而非 null，避免 NPE
        return REGISTRY.getOrDefault(materialId, List.of());
    }

    /**
     * 从实体的盔甲纹饰中查找所有关联的处理器，并聚合返回。
     *
     * 遍历 4 个盔甲槽位（头盔、胸甲、护腿、靴子），
     * 读取每件盔甲上的 DataComponents.TRIM（纹饰数据），
     * 提取纹饰材料的 ResourceLocation，查找对应的处理器列表。
     *
     * 纹饰读取原理（1.21.x Data Components）：
     * - 每件盔甲物品可携带 DataComponents.TRIM 数据组件
     * - ArmorTrim 对象包含材料（Holder<TrimMaterial>）和图案信息
     * - 通过 Holder.unwrapKey() 获取材料在注册表中的 ResourceLocation
     *
     * @param entity 需要查询纹饰效果的实体（通常为玩家）
     * @return 所有盔甲纹饰对应的处理器聚合列表（去重但保留所有实例），
     *         若无任何纹饰则返回空列表
     */
    public static List<TrimEffectHandler> lookupFromArmor(LivingEntity entity) {
        List<TrimEffectHandler> result = new ArrayList<>();

        // 4 个盔甲槽位：头盔、胸甲、护腿、靴子
        EquipmentSlot[] armorSlots = {
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        };

        // 遍历每个盔甲槽位
        for (EquipmentSlot slot : armorSlots) {
            ItemStack armorStack = entity.getItemBySlot(slot);

            // 跳过空槽位
            if (armorStack.isEmpty()) {
                continue;
            }

            // 读取盔甲上的纹饰数据组件（1.21.x 使用 DataComponents 替代旧版 NBT）
            ArmorTrim trim = armorStack.get(DataComponents.TRIM);

            // 若该盔甲没有纹饰，跳过
            if (trim == null) {
                continue;
            }

            // 从 ArmorTrim 中获取纹饰材料的 Holder，再提取 ResourceLocation
            // unwrapKey() 返回 Optional<ResourceKey<TrimMaterial>>，
            // 其 location() 即为材料的注册名（如 "minecraft:quartz"）
            trim.material().unwrapKey().ifPresent(materialKey -> {
                ResourceLocation materialId = materialKey.location();
                // 查找该材料注册的所有处理器，聚合到结果列表
                result.addAll(lookup(materialId));
            });
        }

        return result;
    }
}
