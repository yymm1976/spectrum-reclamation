package com.spectrum_reclamation.spectrum_reclamation.event;

import com.spectrum_reclamation.spectrum_reclamation.registry.SRDataComponents;
import com.spectrum_reclamation.spectrum_reclamation.registry.SRMobEffects;
import com.spectrum_reclamation.spectrum_reclamation.registry.SRItems;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ArrowLooseEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Spectrum Reclamation 事件处理器。
 * 监听游戏运行时事件（GAME_BUS），实现以下功能：
 * 1. 卸负效果的附加机制（受击脱落盔甲、死亡掉落）
 * 2. 瞄准镜附着逻辑（右键合并、箭矢重力降低）
 *
 * 注册方式：NeoForge.EVENT_BUS.register(SREventHandler.class)，
 * 使用类注册静态事件方法，无需实例化。
 */
public class SREventHandler {

    /**
     * 记录刚发射了瞄准镜箭矢的玩家 UUID。
     * 用于在 EntityJoinLevelEvent 中识别并修改箭矢重力。
     *
     * 为什么用 Set 而不是直接在 ArrowLooseEvent 中修改箭矢：
     * ArrowLooseEvent 触发时箭矢尚未创建（在 BowItem.shoot() 中创建），
     * 因此需要通过此中转机制，在 EntityJoinLevelEvent 中捕获刚生成的箭矢。
     *
     * 线程安全：ArrowLooseEvent 和 EntityJoinLevelEvent 都在主线程同步执行，
     * 同一玩家不可能在同一 tick 发射两支箭，因此 Set 是安全的。
     */
    private static final Set<UUID> PLAYERS_WITH_SCOPED_SHOT = new HashSet<>();

    // ==================== 瞄准镜附着逻辑 ====================

    /**
     * 监听 PlayerInteractEvent.RightClickItem —— 检测瞄准镜附着交互。
     *
     * 触发条件：玩家主手持弓/弩、副手持瞄准镜，右键使用。
     * 执行效果：
     * 1. 弓/弩获得 scope_attached 数据组件（Boolean = true）
     * 2. 瞄准镜物品被消耗（减少 1 个）
     * 3. 播放附着音效
     * 4. action bar 提示"瞄准镜已附着"
     *
     * NeoForge 事件机制：RightClickItem 在玩家右键使用物品时触发，
     * 位于 Item.use() 调用之前。我们在此处处理附着逻辑，
     * 并通过 setCancellationResult 返回 SUCCESS 阻止后续的 use() 调用。
     *
     * @param event 右键物品事件
     */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        Level level = event.getLevel();

        if (player == null || level.isClientSide()) {
            return;
        }

        ItemStack mainHand = player.getItemInHand(InteractionHand.MAIN_HAND);
        ItemStack offHand = player.getItemInHand(InteractionHand.OFF_HAND);

        // 判断主手是否为弓/弩（BowItem 或 CrossbowItem）
        boolean mainHandIsWeapon = mainHand.getItem() instanceof BowItem || mainHand.getItem() instanceof CrossbowItem;
        // 判断副手是否为瞄准镜
        boolean offHandIsScope = offHand.is(SRItems.SCOPE_ATTACHMENT.get());

        // 判断主手是否已有瞄准镜
        boolean mainHandHasScope = mainHand.has(SRDataComponents.SCOPE_ATTACHED.get());

        if (mainHandIsWeapon && offHandIsScope && !mainHandHasScope) {
            // === 附着瞄准镜 ===

            // 给弓/弩添加 scope_attached 数据组件
            mainHand.set(SRDataComponents.SCOPE_ATTACHED.get(), true);

            // 消耗副手的瞄准镜（非创造模式）
            if (!player.getAbilities().instabuild) {
                offHand.shrink(1);
            }

            // 播放铁砧修复音效（表示"安装"）
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 1.0F, 1.0F);

            // 在 action bar 提示
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable(
                            "message.spectrum_reclamation.scope_attached"
                    ),
                    true
            );

            // 返回 CONSUME，阻止后续的 Item.use() 调用
            event.setCancellationResult(net.minecraft.world.InteractionResult.CONSUME);
            event.setCanceled(true);
        }
    }

    /**
     * 监听 ArrowLooseEvent —— 记录发射瞄准镜箭矢的玩家。
     *
     * 当玩家释放弓/弩时触发。如果弓/弩附着了瞄准镜，
     * 将玩家 UUID 加入 PLAYERS_WITH_SCOPED_SHOT 集合，
     * 以便在 EntityJoinLevelEvent 中识别并修改箭矢重力。
     *
     * @param event 箭矢释放事件
     */
    @SubscribeEvent
    public static void onArrowLoose(ArrowLooseEvent event) {
        Player player = event.getEntity();
        ItemStack bow = event.getBow();

        // 仅在服务端处理
        if (player.level().isClientSide()) {
            return;
        }

        // 检查弓/弩是否附着了瞄准镜
        if (bow.has(SRDataComponents.SCOPE_ATTACHED.get())) {
            // 记录玩家 UUID，供 EntityJoinLevelEvent 识别
            PLAYERS_WITH_SCOPED_SHOT.add(player.getUUID());
        }
    }

    /**
     * 监听 EntityJoinLevelEvent —— 修改瞄准镜箭矢的重力。
     *
     * 当实体加入世界时触发。如果实体是箭矢（AbstractArrow），
     * 且其所有者在 PLAYERS_WITH_SCOPED_SHOT 集合中，
     * 则降低箭矢重力（使弹道更直）。
     *
     * 重力修改原理：
     * - 原版箭矢受重力影响，远距离射击时弹道下坠明显
     * - setDeltaMovement() 设置箭矢的速度向量
     * - 通过增大速度向量的水平分量（乘以 1.5），同时减小垂直分量的衰减
     * - 最简单的方式：使用 setNoGravity(true) 完全消除重力
     *
     * @param event 实体加入世界事件
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();

        // 仅处理箭矢实体
        if (!(entity instanceof AbstractArrow arrow)) {
            return;
        }

        // 仅在服务端处理
        if (entity.level().isClientSide()) {
            return;
        }

        // 获取箭矢所有者（射击者）
        Entity owner = arrow.getOwner();
        if (owner == null) {
            return;
        }

        UUID ownerUUID = owner.getUUID();

        // 检查所有者是否在"刚发射瞄准镜箭矢"集合中
        if (PLAYERS_WITH_SCOPED_SHOT.remove(ownerUUID)) {
            // 设置箭矢无重力，弹道更直（模拟瞄准镜的精准射击效果）
            arrow.setNoGravity(true);
        }
    }

    /**
     * 监听实体加入世界事件 —— 深灰涂装跨重启静音恢复。
     *
     * 深灰涂装使用 TickTask 延迟恢复静音，但服务器重启后 TickTask 丢失。
     * 实体的 setSilent(true) 会持久化到 NBT，导致永久静音。
     * 通过检查 PersistentData 中的 silent_until_tick 键来判断是否需要恢复。
     *
     * @param event 实体加入世界事件
     */
    @SubscribeEvent
    public static void onLivingEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (entity.level().isClientSide()) return;

        var persistentData = entity.getPersistentData();
        String key = "spectrum_reclamation:silent_until_tick";
        if (!persistentData.contains(key)) return;

        long silentUntilTick = persistentData.getLong(key);
        long currentTick = entity.level().getGameTime();

        if (currentTick >= silentUntilTick) {
            // 已过期：恢复声音并清除 NBT 键
            entity.setSilent(false);
            persistentData.remove(key);
        } else {
            // 未过期：保持静音并重新调度清除
            entity.setSilent(true);
            long remainingTicks = silentUntilTick - currentTick;
            if (entity.level() instanceof ServerLevel serverLevel) {
                int targetTick = serverLevel.getServer().getTickCount() + (int) remainingTicks;
                serverLevel.getServer().tell(new net.minecraft.server.TickTask(targetTick, () -> {
                    if (entity.isAlive() && entity.level() == serverLevel) {
                        entity.setSilent(false);
                        entity.getPersistentData().remove(key);
                    }
                }));
            }
        }
    }

    /**
     * 监听玩家登出事件 —— 清理 PLAYERS_WITH_SCOPED_SHOT 中的残留条目。
     *
     * 当玩家断开连接时，如果之前发射的瞄准镜箭矢尚未加入世界
     * （例如箭矢被其他模组取消），UUID 会残留在集合中导致内存泄漏。
     * 在此处确保玩家登出时清除其 UUID。
     *
     * @param event 玩家登出事件
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PLAYERS_WITH_SCOPED_SHOT.remove(event.getEntity().getUUID());
    }

    // ==================== 卸负效果逻辑 ====================

    /**
     * 监听 LivingIncomingDamageEvent —— 已移除受击掉落逻辑。
     *
     * 原设计：受击时按概率脱落盔甲（等级 I 10%、等级 II 20%），
     * 与周期性掉落（每 5 秒）叠加后过于激进，15 秒内可掉光全套。
     *
     * 修复：砍掉受击掉落来源，仅保留 UnburdenMobEffect.applyEffectTick()
     * 中的周期性掉落（等级 I 3%、等级 II 6%，每 5 秒）。
     *
     * 此事件监听保留用于未来扩展（如受击时增加效果持续时间等），
     * 当前不做任何处理。
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingIncomingDamageEvent event) {
        // 受击掉落逻辑已移除，仅保留周期性掉落（见 UnburdenMobEffect）
    }

    /**
     * 监听 LivingDropsEvent，确保卸负效果死亡时所有盔甲都加入掉落物。
     *
     * @param event 死亡掉落事件，包含已死亡实体和掉落物集合
     */
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        // 仅在服务端处理
        if (event.getEntity().level().isClientSide()) {
            return;
        }

        // 检查死亡者是否携带卸负效果
        if (!event.getEntity().hasEffect(SRMobEffects.UNBURDEN)) {
            return;
        }

        // 获取当前已有的掉落物集合
        java.util.Collection<ItemEntity> drops = event.getDrops();

        // 定义 4 个盔甲槽位
        EquipmentSlot[] armorSlots = {
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        };

        // 遍历 4 个盔甲槽位
        for (EquipmentSlot slot : armorSlots) {
            ItemStack itemStack = event.getEntity().getItemBySlot(slot);

            // 跳过空槽位
            if (itemStack.isEmpty()) {
                continue;
            }

            // 去重逻辑：使用 ItemStack.isSameItemSameComponents() 进行严格比较
            boolean alreadyInDrops = drops.stream().anyMatch(
                    existing -> ItemStack.isSameItemSameComponents(existing.getItem(), itemStack)
            );

            // 不在已有掉落物中，才添加
            if (!alreadyInDrops) {
                ItemEntity itemEntity = new ItemEntity(
                        event.getEntity().level(),
                        event.getEntity().getX(),
                        event.getEntity().getY(),
                        event.getEntity().getZ(),
                        itemStack.copy()
                );

                itemEntity.setPickUpDelay(40);

                // 将盔甲加入死亡掉落物集合
                drops.add(itemEntity);
            }
        }
    }
}
