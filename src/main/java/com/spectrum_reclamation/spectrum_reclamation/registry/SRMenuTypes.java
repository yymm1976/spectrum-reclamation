package com.spectrum_reclamation.spectrum_reclamation.registry;

import com.spectrum_reclamation.spectrum_reclamation.SpectrumReclamation;
import com.spectrum_reclamation.spectrum_reclamation.inventory.FletchingTableMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 菜单类型（MenuType）注册类。
 * 使用 DeferredRegister 延迟注册所有自定义容器菜单类型，
 * 确保在 NeoForge 注册阶段由引擎统一处理。
 *
 * MenuType 是 NeoForge 用于识别不同容器 GUI 的注册项，
 * 每个 MenuType 对应一个特定的容器菜单（AbstractContainerMenu）。
 */
public class SRMenuTypes {

    /** 菜单类型的 DeferredRegister，注册到 Registries.MENU */
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, SpectrumReclamation.MOD_ID);

    /**
     * 制箭台菜单类型。
     * 对应 FletchingTableMenu 容器，用于制箭台方块的 GUI 交互。
     *
     * IMenuTypeExtension.create() 用于创建自定义菜单类型，
     * 其内部会处理客户端与服务端的容器实例化与数据同步。
     * 服务端通过 NetworkHooks.openScreen() 打开此菜单时，
     * 客户端会根据此 MenuType 自动创建对应的 FletchingTableMenu 实例。
     */
    public static final DeferredHolder<MenuType<?>, MenuType<FletchingTableMenu>> FLETCHING_TABLE =
            MENU_TYPES.register(
                    "fletching_table",
                    () -> IMenuTypeExtension.create(FletchingTableMenu::fromNetwork)
            );

    /**
     * 将菜单类型注册器绑定到模组事件总线（MOD_BUS）。
     * 必须在模组构造器中调用，确保菜单类型在注册阶段被引擎处理。
     *
     * @param modEventBus 模组事件总线
     */
    public static void register(IEventBus modEventBus) {
        MENU_TYPES.register(modEventBus);
    }
}
