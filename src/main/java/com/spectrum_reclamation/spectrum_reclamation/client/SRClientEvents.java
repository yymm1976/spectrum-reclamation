package com.spectrum_reclamation.spectrum_reclamation.client;

import com.spectrum_reclamation.spectrum_reclamation.SpectrumReclamation;
import com.spectrum_reclamation.spectrum_reclamation.registry.SREntities;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

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
 * - bus = MOD_BUS：监听模组加载阶段事件（如渲染器注册）
 * - value = CLIENT：仅在物理客户端加载此类
 * - 静态方法 + @SubscribeEvent：自动发现并注册事件监听
 */
@EventBusSubscriber(modid = SpectrumReclamation.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
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
    }
}
