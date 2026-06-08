package com.spectrum_reclamation.spectrum_reclamation.client;

import com.spectrum_reclamation.spectrum_reclamation.entity.ThrownHeavySpear;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * 沉重之矛弹射物渲染器。
 *
 * 继承 ArrowRenderer（AbstractArrow 的专用渲染器基类），
 * 以箭矢外观渲染沉重之矛弹射物。暂用原版箭矢纹理，
 * 后续可替换为自定义的矛纹理（textures/entity/heavy_spear.png）。
 */
public class HeavySpearRenderer extends ArrowRenderer<ThrownHeavySpear> {

    /** 暂用原版箭矢纹理 */
    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/projectiles/arrow.png");

    public HeavySpearRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(ThrownHeavySpear entity) {
        return TEXTURE;
    }
}
