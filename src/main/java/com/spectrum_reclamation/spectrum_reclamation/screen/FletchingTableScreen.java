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
 * 当前使用彩色矩形区分各槽位区域：
 * - 箭杆/箭头/翎羽区（棕色背景）
 * - 试剂区（浅灰色背景）
 * - 输出区（金色边框）
 */
public class FletchingTableScreen extends AbstractContainerScreen<FletchingTableMenu> {

    // ==================== 颜色常量（ARGB 格式） ====================

    /** 整体背景色（深灰） */
    private static final int COLOR_BG = 0xFF303030;
    /** 箭杆/箭头/翎羽区域背景（棕色） */
    private static final int COLOR_SHAFT_AREA = 0xFF5C3A1E;
    /** 试剂区域背景（浅灰色） */
    private static final int COLOR_REAGENT_AREA = 0xFF505050;
    /** 输出区域边框（金色） */
    private static final int COLOR_OUTPUT_BORDER = 0xFFFFD700;
    /** 输出区域背景（深金色） */
    private static final int COLOR_OUTPUT_BG = 0xFF3D3520;
    /** 槽位内部背景（稍亮的灰色，区分槽位和区域背景） */
    private static final int COLOR_SLOT_BG = 0xFF8B8B8B;

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
     * 布局说明（基于 FletchingTableMenu 的槽位坐标）：
     * - 箭杆/箭头/翎羽槽：x=16/48/80, y=20（棕色区域）
     * - 试剂槽：x=128/160/192, y=20（浅灰色区域）
     * - 输出槽：x=104, y=56（金色边框区域）
     * - 玩家背包：x=8, y=84（标准偏移）
     *
     * 每个槽位为 16x16 像素（标准物品槽），加 1px 边框后为 18x18。
     *
     * @param guiGraphics  GUI 图形上下文（NeoForge 1.21.x 封装的渲染工具）
     * @param partialTick  帧间插值（未使用）
     * @param mouseX       鼠标 X 坐标（未使用）
     * @param mouseY       鼠标 Y 坐标（未使用）
     */
    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        // === 1. 整体背景 ===
        guiGraphics.fill(x, y, x + imageWidth, y + imageHeight, COLOR_BG);

        // === 2. 箭杆/箭头/翎羽区域（棕色背景） ===
        // 覆盖槽 0-2：x=7~97, y=12~38
        guiGraphics.fill(x + 7, y + 12, x + 97, y + 38, COLOR_SHAFT_AREA);

        // === 3. 试剂区域（浅灰色背景） ===
        // 覆盖槽 3-5：x=119~209, y=12~38
        guiGraphics.fill(x + 119, y + 12, x + 209, y + 38, COLOR_REAGENT_AREA);

        // === 4. 输出区域（金色边框 + 深金色背景） ===
        // 槽 6 位置：x=104, y=56，加边框后 x=95~127, y=47~83
        guiGraphics.fill(x + 95, y + 47, x + 127, y + 83, COLOR_OUTPUT_BORDER);
        guiGraphics.fill(x + 97, y + 49, x + 125, y + 81, COLOR_OUTPUT_BG);

        // === 5. 各槽位内部背景（稍亮的灰色方块，让槽位位置更清晰） ===
        // 每个槽位 16x16，从槽位坐标偏移 -1（留 1px 边距）
        int[][] inputSlots = {
                {15, 19}, {47, 19}, {79, 19},  // 箭杆/箭头/翎羽
                {127, 19}, {159, 19}, {191, 19} // 试剂 1/2/3
        };
        for (int[] slot : inputSlots) {
            guiGraphics.fill(x + slot[0], y + slot[1],
                    x + slot[0] + 18, y + slot[1] + 18, COLOR_SLOT_BG);
        }

        // === 6. 区域标签 ===
        // 箭杆/箭头/翎羽标签
        guiGraphics.drawString(font, "Arrow", x + 30, y + 5, 0xFFAAAAAA, false);
        // 试剂标签
        guiGraphics.drawString(font, "Reagent", x + 145, y + 5, 0xFFAAAAAA, false);
        // 输出标签
        guiGraphics.drawString(font, "Out", x + 104, y + 40, 0xFFFFD700, false);
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
