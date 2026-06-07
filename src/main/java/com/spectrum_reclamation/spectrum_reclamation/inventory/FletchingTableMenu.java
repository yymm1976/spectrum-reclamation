package com.spectrum_reclamation.spectrum_reclamation.inventory;

import com.spectrum_reclamation.spectrum_reclamation.registry.SRMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.List;

/**
 * 制箭台容器菜单。
 * 实现制箭台的 GUI 容器逻辑，包含 6 个输入槽和 1 个输出槽。
 *
 * 槽位布局（标准 32 像素间距，84 像素玩家背包偏移）：
 * 顶部行（y=20）：[箭杆] [箭头] [翎羽] [试剂1] [试剂2] [试剂3]
 * 底部行（y=56）：               [    输出 ×16    ]
 *
 * 配方系统：
 * - 前三个输入槽（箭杆、箭头、翎羽）为必需材料
 * - 后三个试剂槽可选——不放入试剂也能合成基础箭
 * - 匹配配方后输出对应物品
 *
 * 临时数据存储：使用 SimpleContainer（无需方块实体持久化），
 * 关闭容器时丢弃所有未取出的物品。
 */
public class FletchingTableMenu extends AbstractContainerMenu {

    // ==================== 常量定义 ====================

    /** 槽位数量：6 输入 + 1 输出 = 7 */
    private static final int SLOT_COUNT = 7;
    /** 配方必需材料数量：箭杆、箭头、翎羽 */
    private static final int REQUIRED_SLOTS = 3;
    /** 试剂槽起始索引（第 3 槽开始） */
    private static final int REAGENT_START = 3;
    /** 输出槽索引 */
    private static final int OUTPUT_SLOT = 6;

    // ==================== 箭矢配方注册表 ====================

    /**
     * 箭矢配方列表。
     * 每条配方由必需材料（3 个 Ingredient）和可选试剂（N 个 Ingredient）组成，
     * 匹配成功后输出指定的 ItemStack。
     *
     * 使用静态 List 存储，通过 registerArrowRecipe() 方法添加配方。
     */
    private static final List<ArrowRecipe> ARROW_RECIPES = new ArrayList<>();

    // ==================== 内部数据结构 ====================

    /** 临时物品容器（6 输入 + 1 输出）。
     * SimpleContainer 是 Minecraft 提供的简易容器实现，
     * 仅在内存中保存物品，不与世界数据持久化关联。
     * 容器关闭后数据自动丢失。 */
    private final SimpleContainer container;

    // ==================== 构造方法 ====================

    /**
     * 服务端构造方法。
     * 由方块实体或交互逻辑调用，直接传入玩家背包。
     *
     * @param windowId   窗口 ID，由服务器分配，用于网络同步
     * @param playerInv  玩家背包，用于创建快捷栏和背包槽位
     */
    public FletchingTableMenu(int windowId, Inventory playerInv) {
        this(windowId, playerInv, new SimpleContainer(SLOT_COUNT));
    }

    /**
     * 客户端构造方法。
     * 由客户端网络包处理器调用，通过 FriendlyByteBuf 反序列化额外数据。
     * 当前实现无需额外数据，因此 buf 参数未使用。
     *
     * @param windowId   窗口 ID
     * @param playerInv  玩家背包
     * @param buf        网络数据缓冲区（当前未使用，预留接口）
     */
    public FletchingTableMenu(int windowId, Inventory playerInv, FriendlyByteBuf buf) {
        this(windowId, playerInv, new SimpleContainer(SLOT_COUNT));
    }

    /**
     * 核心构造方法。
     * 初始化容器菜单，注册所有输入槽、输出槽和玩家背包槽位。
     *
     * @param windowId  窗口 ID
     * @param playerInv 玩家背包
     * @param container 临时物品容器
     */
    private FletchingTableMenu(int windowId, Inventory playerInv, SimpleContainer container) {
        super(SRMenuTypes.FLETCHING_TABLE.get(), windowId);
        this.container = container;

        // ---------- 输入槽（顶部行，y=20，32 像素间距） ----------

        // 槽 0：箭杆（必需）
        this.addSlot(new Slot(container, 0, 16, 20) {
            @Override public boolean mayPlace(ItemStack stack) {
                // 箭杆槽：后续可替换为 Tag 过滤
                return true;
            }
        });
        // 槽 1：箭头（必需）
        this.addSlot(new Slot(container, 1, 48, 20) {
            @Override public boolean mayPlace(ItemStack stack) {
                return true;
            }
        });
        // 槽 2：翎羽（必需）
        this.addSlot(new Slot(container, 2, 80, 20) {
            @Override public boolean mayPlace(ItemStack stack) {
                return true;
            }
        });
        // 槽 3：试剂 1（可选）
        this.addSlot(new Slot(container, 3, 128, 20) {
            @Override public boolean mayPlace(ItemStack stack) {
                return true;
            }
        });
        // 槽 4：试剂 2（可选）
        this.addSlot(new Slot(container, 4, 160, 20) {
            @Override public boolean mayPlace(ItemStack stack) {
                return true;
            }
        });
        // 槽 5：试剂 3（可选）
        this.addSlot(new Slot(container, 5, 192, 20) {
            @Override public boolean mayPlace(ItemStack stack) {
                return true;
            }
        });

        // ---------- 输出槽（底部行，y=56） ----------

        // 槽 6：输出（禁止放入，只能取出）
        this.addSlot(new Slot(container, OUTPUT_SLOT, 104, 56) {
            @Override public boolean mayPlace(ItemStack stack) {
                return false; // 输出槽禁止放入物品
            }
        });

        // ---------- 玩家背包槽位（标准偏移：x=8, y=84） ----------

        // 快捷栏（y=142）
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
        }
        // 背包主体（y=84，3 行 9 列）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, 9 + row * 9 + col, 8 + col * 18, 84 + row * 18));
            }
        }
    }

    // ==================== 工厂方法 ====================

    /**
     * 客户端工厂方法。
     * 当 NeoForge 收到服务端发来的 OpenScreenPacket 时，
     * 会调用此方法在客户端创建对应的 Menu 实例。
     * 由 IMenuTypeExtension.create() 注册。
     *
     * @param windowId  窗口 ID
     * @param playerInv 玩家背包
     * @param buf       网络数据缓冲区（可用于传递方块坐标等额外信息）
     * @return          新的 FletchingTableMenu 实例
     */
    public static FletchingTableMenu fromNetwork(int windowId, Inventory playerInv, FriendlyByteBuf buf) {
        return new FletchingTableMenu(windowId, playerInv, buf);
    }

    // ==================== 容器行为 ====================

    /**
     * 每次容器交互后调用（拾取物品、放入物品等）。
     * 负责检测配方匹配并更新输出槽。
     *
     * NeoForge 的容器同步机制：
     * - 客户端修改容器后，通过 ContainerMenuEvent 触发此方法
     * - 服务端每次此方法执行后，会自动同步变更到客户端
     * - 不需要手动标记 dirty，AbstractContainerMenu 已内置此机制
     */
    @Override
    public void slotsChanged(Container container) {
        this.updateOutput();
        super.slotsChanged(container);
    }

    /**
     * 更新输出槽内容。
     * 遍历已注册的箭矢配方，检查输入槽是否匹配任何配方。
     * 匹配成功则设置输出槽物品，否则清空输出槽。
     */
    private void updateOutput() {
        // 查找匹配的配方
        for (ArrowRecipe recipe : ARROW_RECIPES) {
            if (matchesRecipe(recipe)) {
                this.container.setItem(OUTPUT_SLOT, recipe.output().copy());
                return;
            }
        }
        // 无匹配配方，清空输出槽
        this.container.setItem(OUTPUT_SLOT, ItemStack.EMPTY);
    }

    /**
     * 检查当前输入槽是否匹配指定配方。
     *
     * 匹配规则：
     * - 前 3 个必需材料（箭杆、箭头、翎羽）必须全部匹配
     * - 后 3 个试剂槽：配方中列出的试剂必须匹配，多余的试剂槽必须为空
     *
     * @param recipe 要检查的配方
     * @return       是否匹配
     */
    private boolean matchesRecipe(ArrowRecipe recipe) {
        // 检查必需材料（箭杆、箭头、翎羽）
        for (int i = 0; i < REQUIRED_SLOTS; i++) {
            ItemStack slotItem = this.container.getItem(i);
            if (!recipe.requiredIngredients().get(i).test(slotItem)) {
                return false;
            }
        }

        List<Ingredient> reagents = recipe.reagentIngredients();
        // 遍历试剂槽（索引 3-5）
        for (int i = 0; i < 3; i++) {
            ItemStack slotItem = this.container.getItem(REAGENT_START + i);
            if (i < reagents.size()) {
                // 配方有此试剂要求：必须匹配
                if (!reagents.get(i).test(slotItem)) {
                    return false;
                }
            } else {
                // 配方无此试剂要求：槽必须为空
                if (!slotItem.isEmpty()) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * 快速移动物品逻辑（Shift+点击）。
     * 规则：
     * - 点击输出槽：移入玩家背包
     * - 点击输入槽/试剂槽：移入玩家背包
     * - 点击玩家背包：优先移入匹配的输入槽，否则移入试剂槽
     *
     * @param player   玩家
     * @param slotIndex 被点击的槽位索引
     * @return         移动后的剩余物品（空表示全部移出）
     */
    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);

        if (slot != null && slot.hasItem()) {
            ItemStack slotItem = slot.getItem();
            result = slotItem.copy();

            if (slotIndex == OUTPUT_SLOT) {
                // 从输出槽移入玩家背包
                if (!this.moveItemStackTo(slotItem, SLOT_COUNT, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
                slot.onQuickCraft(slotItem, result);
            } else if (slotIndex < SLOT_COUNT) {
                // 从输入槽/试剂槽移入玩家背包
                if (!this.moveItemStackTo(slotItem, SLOT_COUNT, this.slots.size(), false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // 从玩家背包移入输入槽
                if (!this.moveItemStackTo(slotItem, 0, SLOT_COUNT, false)) {
                    return ItemStack.EMPTY;
                }
            }

            // 移动后处理
            if (slotItem.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            // 更新输出槽（输入变化可能导致配方状态改变）
            this.updateOutput();

            // 防止无限循环：如果物品量未变化则停止
            if (slotItem.getCount() == result.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, slotItem);
        }

        return result;
    }

    /**
     * 判断容器是否仍对玩家有效。
     * 返回 true 表示容器保持打开状态。
     *
     * @param player 玩家
     * @return       始终返回 true（使用 SimpleContainer，无方块实体验证需求）
     */
    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    // ==================== 配方注册 API ====================

    /**
     * 注册一条箭矢配方。
     * 在模组初始化阶段调用，将配方添加到静态配方列表中。
     *
     * 示例（基础箭）：
     * registerArrowRecipe(
     *     Ingredient.of(Items.STICK),   // 箭杆
     *     Ingredient.of(Items.FLINT),   // 箭头
     *     Ingredient.of(Items.FEATHER), // 翎羽
     *     List.of(),                    // 无试剂
     *     new ItemStack(Items.ARROW, 16)
     * );
     *
     * @param shaft      箭杆材料（必需）
     * @param head       箭头材料（必需）
     * @param fletching  翎羽材料（必需）
     * @param reagents   试剂材料列表（可选，长度 0-3）
     * @param output     输出物品
     */
    public static void registerArrowRecipe(
            Ingredient shaft,
            Ingredient head,
            Ingredient fletching,
            List<Ingredient> reagents,
            ItemStack output
    ) {
        List<Ingredient> required = List.of(shaft, head, fletching);
        ARROW_RECIPES.add(new ArrowRecipe(required, List.copyOf(reagents), output));
    }

    // ==================== 内部记录类 ====================

    /**
     * 箭矢配方数据记录。
     *
     * @param requiredIngredients 必需材料列表（长度 3：箭杆、箭头、翎羽）
     * @param reagentIngredients  试剂材料列表（长度 0-3，可选）
     * @param output              输出物品
     */
    private record ArrowRecipe(
            List<Ingredient> requiredIngredients,
            List<Ingredient> reagentIngredients,
            ItemStack output
    ) {}
}
