package com.spectrum_reclamation.spectrum_reclamation.screen;

import com.spectrum_reclamation.spectrum_reclamation.inventory.FletchingTableMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 制箭台 GUI 屏幕渲染类。
 * 负责绘制制箭台的容器界面背景、标题和槽位标签。
 *
 * 继承 AbstractContainerScreen<FletchingTableMenu>：
 * - AbstractContainerScreen 是 Minecraft 提供的容器 GUI 基类，
 *   内置了鼠标点击、物品拖拽、Shift+点击等标准交互逻辑。
 * - 泛型参数 FletchingTableMenu 指定了关联的容器菜单类型。
 *
 * 当前使用纯色矩形作为临时背景，后续替换为自定义 GUI 纹理。
 */
public class FletchingTableScreen extends AbstractContainerScreen<FletchingTableMenu> {

    /**
     * 构造方法。
     * 由 RegisterMenuScreensEvent 的 ScreenConstructor.create() 调用，
     * 在客户端收到服务端的 OpenScreenPacket 后自动创建。
     *
     * @param menu       制箭台容器菜单实例（包含槽位和配方逻辑）
     * @param playerInv  玩家背包（用于显示背包槽位和快捷栏）
     * @param title      容器标题文本组件
     */
    public FletchingTableScreen(FletchingTableMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        // 标准 GUI 宽度（176 像素），与原版容器 GUI 保持一致
        this.imageWidth = 176;
        // 标准 GUI 高度（166 像素）：顶部区域 + 3 行背包 + 1 行快捷栏 + 边距
        this.imageHeight = 166;
    }

    /**
     * 绘制 GUI 背景层。
     * 每帧调用一次，在物品和文字之前渲染。
     *
     * 当前实现：绘制深灰色半透明矩形作为临时背景。
     * 后续替换为 blit(guiTexture, x, y, u, v, w, h) 绘制自定义纹理。
     *
     * @param guiGraphics  GUI 图形上下文（NeoForge 1.21.x 封装的渲染工具）
     * @param mouseX       鼠标 X 坐标（未使用，预留）
     * @param mouseY       鼠标 Y 坐标（未使用，预留）
     * @param partialTick  帧间插值（未使用，预留）
     */
    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        // 绘制临时背景矩形（深灰色，RGB 0x303030，不透明）
        // leftPos / topPos 是 AbstractContainerScreen 自动计算的 GUI 左上角坐标
        guiGraphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF303030);

        // 绘制输入槽区域背景（稍浅的灰色，区分输入区和背包区）
        guiGraphics.fill(leftPos + 7, topPos + 12, leftPos + 169, topPos + 68, 0xFF404040);
    }

    /**
     * 主渲染入口。
     * 按顺序渲染：背景 → 物品/槽位 → 标题/标签 → 悬浮提示。
     *
     * @param guiGraphics  GUI 图形上下文
     * @param mouseX       鼠标 X 坐标
     * @param mouseY       鼠标 Y 坐标
     * @param partialTick  帧间插值
     */
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 先渲染半透明黑色遮罩（背景层）
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        // 再渲染容器 GUI（背景 + 物品 + 标题）
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        // 最后渲染物品悬浮提示（鼠标悬停在物品上时显示名称）
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
