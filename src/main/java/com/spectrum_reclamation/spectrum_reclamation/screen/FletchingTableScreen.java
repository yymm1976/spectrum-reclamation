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
     * 布局说明（基于 FletchingTableMenu 的 2×3 网格槽位坐标）：
     * 左侧 2×3 网格：
     *   槽 0 (箭杆)  x=26, y=20    槽 1 (箭头)  x=62, y=20
     *   槽 2 (翎羽)  x=26, y=46    槽 3 (试剂1) x=62, y=46
     *   槽 4 (试剂2) x=26, y=72    槽 5 (试剂3) x=62, y=72
     * 右侧居中：
     *   槽 6 (输出)  x=124, y=42（金色边框区域）
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

        // === 2. 箭杆/箭头/翎羽区域（棕色背景，覆盖前 3 个槽位） ===
        // 槽 0: x=26,y=20  槽 1: x=62,y=20  槽 2: x=26,y=46
        // 区域范围：x=17~89, y=12~64
        guiGraphics.fill(x + 17, y + 12, x + 89, y + 64, COLOR_SHAFT_AREA);

        // === 3. 试剂区域（浅灰色背景，覆盖后 3 个槽位） ===
        // 槽 3: x=62,y=46  槽 4: x=26,y=72  槽 5: x=62,y=72
        // 区域范围：x=17~89, y=38~90
        guiGraphics.fill(x + 17, y + 38, x + 89, y + 90, COLOR_REAGENT_AREA);

        // === 4. 输出区域（金色边框 + 深金色背景） ===
        // 槽 6: x=124, y=42
        // 区域范围：x=115~149, y=33~67
        guiGraphics.fill(x + 115, y + 33, x + 149, y + 67, COLOR_OUTPUT_BORDER);
        guiGraphics.fill(x + 117, y + 35, x + 147, y + 65, COLOR_OUTPUT_BG);

        // === 5. 各槽位内部背景（稍亮的灰色方块，让槽位位置更清晰） ===
        int[][] inputSlots = {
                {25, 19}, {61, 19},  // 第一行：箭杆、箭头
                {25, 45}, {61, 45},  // 第二行：翎羽、试剂1
                {25, 71}, {61, 71}   // 第三行：试剂2、试剂3
        };
        for (int[] slot : inputSlots) {
            guiGraphics.fill(x + slot[0], y + slot[1],
                    x + slot[0] + 18, y + slot[1] + 18, COLOR_SLOT_BG);
        }

        // === 6. 区域标签 ===
        // 箭杆/箭头/翎羽标签
        guiGraphics.drawString(font, "Arrow", x + 33, y + 5, 0xFFAAAAAA, false);
        // 试剂标签
        guiGraphics.drawString(font, "Reagent", x + 30, y + 37, 0xFFAAAAAA, false);
        // 输出标签
        guiGraphics.drawString(font, "Out", x + 124, y + 25, 0xFFFFD700, false);
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
