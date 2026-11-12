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
import java.util.UUID;
import java.util.WeakHashMap;

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

    /** 注册表是否已冻结（初始化完成后冻结，防止运行时意外修改） */
    private static boolean frozen = false;

    /**
     * 纹饰效果缓存：实体 UUID → (trim 组合哈希, 处理器列表)。
     * WeakHashMap 确保玩家离线后自动清理，避免内存泄漏。
     */
    private static final Map<UUID, CachedEffects> LOOKUP_CACHE = new WeakHashMap<>();

    /** 缓存条目：记录上次的 trim 组合哈希值和对应的处理器列表 */
    private record CachedEffects(int trimHash, List<TrimEffectHandler> handlers) {}

    /**
     * 清除指定实体的纹饰效果缓存。
     * 在装备变化时调用，确保下次 lookupFromArmor 重新计算。
     *
     * @param entityUuid 实体 UUID
     */
    public static void clearCache(UUID entityUuid) {
        LOOKUP_CACHE.remove(entityUuid);
    }

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
        // 冻结后不允许注册，防止运行时意外修改
        if (frozen) {
            throw new IllegalStateException(
                    "TrimEffectRegistry is frozen. Cannot register after initialization. Material: " + materialId);
        }
        // 使用 computeIfAbsent 确保同一材料的多个处理器被聚合到同一个 List 中
        REGISTRY.computeIfAbsent(materialId, k -> new ArrayList<>()).add(handler);
    }

    /**
     * 冻结注册表，防止初始化完成后的意外修改。
     * 应在 VanillaTrimEffects.register() 完成后立即调用。
     * 冻结后 register() 会抛出 IllegalStateException。
     */
    public static void freeze() {
        frozen = true;
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
     * 使用缓存机制避免每 tick 重建处理器列表：
     * 计算当前盔甲 trim 组合的哈希值，与缓存比对，相同则直接返回缓存结果。
     *
     * @param entity 需要查询纹饰效果的实体（通常为玩家）
     * @return 所有盔甲纹饰对应的处理器聚合列表（去重但保留所有实例），
     *         若无任何纹饰则返回空列表
     */
    public static List<TrimEffectHandler> lookupFromArmor(LivingEntity entity) {
        // 计算当前盔甲 trim 组合的哈希值
        int currentHash = computeTrimHash(entity);
        UUID entityUuid = entity.getUUID();

        // 检查缓存
        CachedEffects cached = LOOKUP_CACHE.get(entityUuid);
        if (cached != null && cached.trimHash() == currentHash) {
            return cached.handlers();
        }

        // 缓存未命中，重新计算
        List<TrimEffectHandler> result = new ArrayList<>();

        EquipmentSlot[] armorSlots = {
                EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET
        };

        for (EquipmentSlot slot : armorSlots) {
            ItemStack armorStack = entity.getItemBySlot(slot);
            if (armorStack.isEmpty()) continue;

            ArmorTrim trim = armorStack.get(DataComponents.TRIM);
            if (trim == null) continue;

            trim.material().unwrapKey().ifPresent(materialKey -> {
                ResourceLocation materialId = materialKey.location();
                result.addAll(lookup(materialId));
            });
        }

        // 写入缓存
        LOOKUP_CACHE.put(entityUuid, new CachedEffects(currentHash, result));
        return result;
    }

    /**
     * 计算实体盔甲 trim 组合的哈希值。
     * 基于 4 个槽位的纹饰材料 ResourceLocation 字符串拼接，
     * 确保不同 trim 组合产生不同哈希。
     *
     * @param entity 实体
     * @return trim 组合哈希值
     */
    private static int computeTrimHash(LivingEntity entity) {
        EquipmentSlot[] armorSlots = {
                EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET
        };
        int hash = 0;
        for (EquipmentSlot slot : armorSlots) {
            ItemStack armorStack = entity.getItemBySlot(slot);
            if (armorStack.isEmpty()) continue;

            ArmorTrim trim = armorStack.get(DataComponents.TRIM);
            if (trim == null) continue;

            var key = trim.material().unwrapKey();
            if (key.isPresent()) {
                hash = hash * 31 + key.get().location().toString().hashCode();
            }
        }
        return hash;
    }
}
