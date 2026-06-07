package com.spectrum_reclamation.spectrum_reclamation.mixin;

import com.spectrum_reclamation.spectrum_reclamation.inventory.FletchingTableMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FletchingTableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 制箭台方块 Mixin —— 拦截原版交互，打开自定义 GUI。
 *
 * ===== 注入方案选择理由 =====
 *
 * 方案对比：
 * 1. @Overwrite：替换整个 useWithoutItem 方法 → 侵入性极强，
 *    其他模组无法再对此方法注入，且 Mojang 更新时容易冲突。
 * 2. @Redirect：重定向方法内的某次调用 → 需要精确匹配目标调用，
 *    且同一目标只能有一个 @Redirect（多模组兼容性差）。
 * 3. @Inject(at = @At("HEAD"), cancellable = true)：★ 最终选择
 *    - 在方法最开头注入逻辑，不修改原方法体
 *    - 通过设置返回值取消原方法执行
 *    - 其他模组也可以注入同一方法（多模组友好）
 *    - 失败时不影响原版行为（可移除性好）
 *
 * 注入目标：
 * FletchingTableBlock.useWithoutItem(BlockState, Level, BlockPos, Player, BlockHitResult)
 *   → 玩家右键制箭台时触发的交互方法
 *   → 原版行为：将物品转换为箭矢（粒子效果），无 GUI
 *
 * 注入逻辑：
 * 1. 仅在服务端执行（!level.isClientSide），客户端不做任何处理
 * 2. 通过 player.openMenu() 打开自定义制箭台 GUI
 * 3. 设置返回值为 InteractionResult.SUCCESS，取消原版行为
 *    （阻止原版的箭矢转换粒子效果）
 * 4. 客户端收到 OpenScreenPacket 后，
 *    NeoForge 根据 IMenuTypeExtension.create() 注册的工厂方法
 *    自动创建 FletchingTableMenu 实例，
 *    RegisterMenuScreensEvent 注册的 ScreenConstructor
 *    自动创建 FletchingTableScreen 渲染 GUI
 */
@Mixin(FletchingTableBlock.class)
public class FletchingTableMixin {

    /**
     * 注入 useWithoutItem 方法的 HEAD 位置。
     *
     * @param state     制箭台方块状态
     * @param level     世界实例
     * @param pos       方块坐标
     * @param player    交互的玩家
     * @param hitResult 射线检测结果（点击位置和方向）
     * @param cir       回调返回值容器，用于设置返回值并取消原方法
     */
    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true)
    private void onUseWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        // 仅在服务端处理，客户端不执行任何逻辑
        // NeoForge 的 Side 安全规则：GUI 打开必须由服务端发起，
        // 服务端通过 OpenScreenPacket 通知客户端创建对应的 Menu 和 Screen
        if (!level.isClientSide) {
            player.openMenu(new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("container.spectrum_reclamation.fletching_table");
                }

                @Override
                public AbstractContainerMenu createMenu(int windowId, Inventory playerInv, Player player) {
                    return new FletchingTableMenu(windowId, playerInv, pos);
                }
            }, buf -> buf.writeBlockPos(pos));
        }

        // 取消原版方法执行（无论客户端还是服务端都取消）
        // 服务端：已打开自定义 GUI，不需要原版行为
        // 客户端：原版行为（粒子效果）也不需要
        // 使用 sidedSuccess 确保客户端返回 PASS（不吞掉交互），
        // 服务端返回 SUCCESS（正常完成交互）
        cir.setReturnValue(InteractionResult.sidedSuccess(level.isClientSide));
    }
}
