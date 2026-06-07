package com.spectrum_reclamation.spectrum_reclamation.client;

import com.spectrum_reclamation.spectrum_reclamation.SpectrumReclamation;
import com.spectrum_reclamation.spectrum_reclamation.registry.SREntities;
import com.spectrum_reclamation.spectrum_reclamation.registry.SRMenuTypes;
import com.spectrum_reclamation.spectrum_reclamation.screen.FletchingTableScreen;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * 客户端专属事件处理器。
 *
 * 使用 @EventBusSubscriber 注解自动注册到 MOD_BUS，
 * 并通过 Dist.CLIENT 限制仅在客户端加载此类。
 *
 * 这确保了服务端永远不会加载客户端专属类（如渲染器），
 * 避免 ClassNotFoundException 或 NoClassDefFoundError。
 *
 * NeoForge 的 @EventBusSubscriber 注解：
 * - NeoForge 1.21.1 起 bus 参数已废弃，系统根据事件类型自动判断总线
 * - IModBusEvent 子类（如 EntityRenderersEvent、RegisterMenuScreensEvent）→ MOD_BUS
 * - 其他事件 → GAME_BUS
 * - value = CLIENT：仅在物理客户端加载此类
 * - 静态方法 + @SubscribeEvent：自动发现并注册事件监听
 */
@EventBusSubscriber(modid = SpectrumReclamation.MOD_ID, value = Dist.CLIENT)
public class SRClientEvents {

    /**
     * 注册实体渲染器。
     *
     * EntityRenderersEvent.RegisterRenderers 是 MOD_BUS 事件，
     * 在客户端初始化阶段触发，用于绑定实体类型与对应的渲染器。
     *
     * ThrownItemRenderer 是原版提供的通用物品弹射物渲染器，
     * 它根据 ThrowableItemProjectile.getDefaultItem() 返回的物品进行渲染。
     * 雪球、经验瓶等原版弹射物均使用此渲染器。
     *
     * @param event 渲染器注册事件
     */
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(SREntities.BLAZING_BOMB.get(), ThrownItemRenderer::new);
        // 沉重之矛弹射物继承 AbstractArrow，必须使用 ArrowRenderer 子类渲染。
        // HeavySpearRenderer 暂用原版箭矢纹理，后续可替换为自定义纹理。
        event.registerEntityRenderer(SREntities.THROWN_HEAVY_SPEAR.get(), HeavySpearRenderer::new);
    }

    /**
     * 注册容器菜单屏幕。
     *
     * RegisterMenuScreensEvent 是 NeoForge 1.21.1 提供的 MOD_BUS 事件，
     * 用于将 MenuType 与对应的 Screen 类绑定。
     * 当客户端收到服务端发来的 OpenScreenPacket 时，
     * Minecraft 会查找此 MenuType 对应的 ScreenConstructor 来创建 GUI 实例。
     *
     * 此方法替代了旧版的 MenuScreens.register() 调用方式，
     * 符合 NeoForge 1.21.x 的事件驱动注册规范。
     *
     * @param event 屏幕注册事件
     */
    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        // 将制箭台菜单类型与制箭台 GUI 屏幕绑定
        // ScreenConstructor.create() 会在客户端收到 OpenScreenPacket 时被调用，
        // 创建 FletchingTableScreen 实例并打开 GUI
        event.register(SRMenuTypes.FLETCHING_TABLE.get(), FletchingTableScreen::new);
    }
}
