package com.spectrum_reclamation.spectrum_reclamation.registry;

import com.mojang.serialization.Codec;
import com.spectrum_reclamation.spectrum_reclamation.SpectrumReclamation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.component.DataComponentType;

/**
 * 数据组件（DataComponentType）注册类。
 * 在 1.21.x 中，物品的自定义数据不再使用 NBT，而是通过 DataComponentType 注册。
 * 每个 DataComponentType 需要：
 * - persistent(Codec)：用于序列化/反序列化（存档、数据包）
 * - networkSynchronized(StreamCodec)：用于客户端-服务端网络同步
 *
 * 注册方式与物品/方块相同，使用 DeferredRegister 绑定到 MOD_BUS。
 */
public class SRDataComponents {

    /** 数据组件的 DeferredRegister，注册到 DATA_COMPONENT_TYPE 注册表 */
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, SpectrumReclamation.MOD_ID);

    /**
     * 瞄准镜附着标记 —— 标记弓/弩是否已安装瞄准镜。
     * 值类型：Boolean
     * 用途：当弓/弩获得此组件后，拉弓时触发 FOV 缩放，发射时降低箭矢重力。
     * 持久化：是（存档时保留），网络同步：是（客户端需要读取来计算 FOV）
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> SCOPE_ATTACHED =
            DATA_COMPONENTS.register("scope_attached",
                    () -> DataComponentType.<Boolean>builder()
                            .persistent(Codec.BOOL)
                            .networkSynchronized(ByteBufCodecs.BOOL)
                            .build()
            );

    /**
     * 追溯指针目标坐标 —— 存储玩家记录的 waypoint 位置。
     * 值类型：BlockPos
     * 用途：追溯指针的指南针指针指向此坐标。
     * 持久化：是，网络同步：是。
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockPos>> WAYPOINT_POS =
            DATA_COMPONENTS.register("waypoint_pos",
                    () -> DataComponentType.<BlockPos>builder()
                            .persistent(BlockPos.CODEC)
                            .networkSynchronized(BlockPos.STREAM_CODEC)
                            .build()
            );

    /**
     * 追溯指针目标维度 —— 存储 waypoint 所在的维度。
     * 值类型：ResourceLocation（如 "minecraft:overworld"）
     * 用途：跨维度时指针不工作（随机旋转），仅在相同维度时指向目标。
     * 持久化：是，网络同步：是。
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> WAYPOINT_DIMENSION =
            DATA_COMPONENTS.register("waypoint_dimension",
                    () -> DataComponentType.<ResourceLocation>builder()
                            .persistent(ResourceLocation.CODEC)
                            .networkSynchronized(ResourceLocation.STREAM_CODEC)
                            .build()
            );

    /**
     * 沉重之矛涂装颜色 —— 存储墨水涂装的颜色 ID（如 "red"、"blue"）。
     * 值类型：String
     * 用途：击中目标时读取此组件，触发对应颜色的特殊效果。
     * 持久化：是（存档时保留），网络同步：是（弹射物需要在客户端显示效果）。
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> SPEAR_COATING =
            DATA_COMPONENTS.register("spear_coating",
                    () -> DataComponentType.<String>builder()
                            .persistent(Codec.STRING)
                            .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                            .build()
            );

    /**
     * 将数据组件注册器绑定到模组事件总线（MOD_BUS）。
     *
     * @param modEventBus 模组事件总线
     */
    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }
}
