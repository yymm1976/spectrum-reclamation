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
 * 布局（3 列 × 2 行 + 右下输出）：
 * 第一行（y=17）：槽 0 箭杆(x=30)、槽 1 箭头(x=66)、槽 2 翎羽(x=102)
 * 第二行（y=51）：槽 3 试剂1(x=30)、槽 4 试剂2(x=66)、槽 5 试剂3(x=102)
 * 输出（x=143, y=73）：槽 6
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
     * 布局说明（基于 FletchingTableMenu 的 3 列 × 2 行槽位坐标）：
     * 第一行（y=17）：
     *   槽 0 (箭杆)  x=30     槽 1 (箭头)  x=66     槽 2 (翎羽)  x=102
     * 第二行（y=51）：
     *   槽 3 (试剂1) x=30     槽 4 (试剂2) x=66     槽 5 (试剂3) x=102
     * 右下输出：
     *   槽 6 (输出)  x=143, y=73（金色边框区域）
     *
     * @param guiGraphics  GUI 图形上下文
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

        // === 2. 箭杆/箭头/翎羽区域（棕色背景，覆盖第一行 3 个槽位） ===
        // 槽 0: x=30,y=17  槽 1: x=66,y=17  槽 2: x=102,y=17
        // 区域范围：x=21~120, y=9~35
        guiGraphics.fill(x + 21, y + 9, x + 120, y + 35, COLOR_SHAFT_AREA);

        // === 3. 试剂区域（浅灰色背景，覆盖第二行 3 个槽位） ===
        // 槽 3: x=30,y=51  槽 4: x=66,y=51  槽 5: x=102,y=51
        // 区域范围：x=21~120, y=43~68
        guiGraphics.fill(x + 21, y + 43, x + 120, y + 68, COLOR_REAGENT_AREA);

        // === 4. 输出区域（金色边框 + 深金色背景） ===
        // 槽 6: x=143, y=73
        // 边框范围：x=134~162, y=64~92
        guiGraphics.fill(x + 134, y + 64, x + 162, y + 92, COLOR_OUTPUT_BORDER);
        // 背景范围：x=136~160, y=66~90
        guiGraphics.fill(x + 136, y + 66, x + 160, y + 90, COLOR_OUTPUT_BG);

        // === 5. 各槽位内部背景（稍亮的灰色方块，让槽位位置更清晰） ===
        int[][] inputSlots = {
                {29, 16}, {65, 16}, {101, 16},  // 第一行：箭杆、箭头、翎羽
                {29, 50}, {65, 50}, {101, 50}   // 第二行：试剂1、试剂2、试剂3
        };
        for (int[] slot : inputSlots) {
            guiGraphics.fill(x + slot[0], y + slot[1],
                    x + slot[0] + 18, y + slot[1] + 18, COLOR_SLOT_BG);
        }

        // === 6. 区域标签 ===
        // 箭杆/箭头/翎羽标签
        guiGraphics.drawString(font, "Arrow", x + 38, y + 2, 0xFFAAAAAA, false);
        // 试剂标签
        guiGraphics.drawString(font, "Reagent", x + 38, y + 36, 0xFFAAAAAA, false);
        // 输出标签
        guiGraphics.drawString(font, "Out", x + 143, y + 56, 0xFFFFD700, false);
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
