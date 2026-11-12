plugins {
    id("java-library")
    id("maven-publish")
    id("net.neoforged.moddev") version "2.0.139"
    id("idea")
}

val parchment_minecraft_version : String by project
val parchment_mappings_version  : String by project
val minecraft_version           : String by project
val minecraft_version_range     : String by project
val neo_version                 : String by project
val neo_version_range           : String by project
val loader_version_range        : String by project
val mod_id                      : String by project
val mod_name                    : String by project
val mod_license                 : String by project
val mod_version                 : String by project
val mod_group_id                : String by project

tasks.wrapper.configure {
    distributionType = Wrapper.DistributionType.BIN
}

version = mod_version
group = mod_group_id

// archivesName 只设基础名，Gradle 自动追加 "-${version}" 后缀
// 最终产物名格式：spectrum_reclamation-1.21.1-0.1.0.jar
base {
    archivesName.set("$mod_id-$minecraft_version")
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(21))

neoForge {
    version = neo_version

    parchment {
        mappingsVersion = parchment_mappings_version
        minecraftVersion = parchment_minecraft_version
    }

    runs {
        create("client") {
            client()
            systemProperty("neoforge.enabledGameTestNamespaces", mod_id)
        }

        create("server") {
            server()
            gameDirectory = file("run-server")
            programArgument("--nogui")
            systemProperty("neoforge.enabledGameTestNamespaces", mod_id)
        }

        create("gameTestServer") {
            type = "gameTestServer"
            gameDirectory = file("run-server")
            systemProperty("neoforge.enabledGameTestNamespaces", mod_id)
        }

        create("data") {
            data()
            gameDirectory = file("run-data")
            programArguments.addAll(
                "--mod",
                mod_id,
                "--all",
                "--output",
                file("src/generated/resources/").absolutePath,
                "--existing",
                file("src/main/resources/").absolutePath
            )
        }

        configureEach {
            systemProperty("forge.logging.markers", "REGISTRIES")
            logLevel = org.slf4j.event.Level.DEBUG
        }
    }

    mods {
        create(mod_id) {
            sourceSet(sourceSets.main.get())
        }
    }
}

sourceSets.main.get().resources {
    srcDir("src/generated/resources")
}

val localRuntime: Configuration by configurations.creating
configurations {
    runtimeClasspath {
        extendsFrom(localRuntime)
    }
}

repositories {
    // CurseMaven 仓库，用于获取 Spectrum 等 CurseForge 上托管的模组依赖
    maven { url = uri("https://cfa2.cursemaven.com") }
}

dependencies {
    // Spectrum 模组依赖（通过 CurseMaven 获取，CurseForge 项目 ID: 556967，文件 ID: 8037133 对应 1.11.8-1.21.1-neo）
    implementation("curse.maven:spectrum-556967:8037133")
}

tasks.named("createMinecraftArtifacts") {
    dependsOn("generateModMetadata")
}

val generateModMetadata = tasks.register<ProcessResources>("generateModMetadata") {
    val replaceProperties = mapOf(
        "minecraft_version"       to minecraft_version,
        "minecraft_version_range" to minecraft_version_range,
        "neo_version"             to neo_version,
        "neo_version_range"       to neo_version_range,
        "loader_version_range"    to loader_version_range,
        "mod_id"                  to mod_id,
        "mod_name"                to mod_name,
        "mod_license"             to mod_license,
        "mod_version"             to mod_version,
    )
    inputs.properties(replaceProperties)

    expand(replaceProperties)
    from("src/main/templates")
    into("build/generated/sources/modMetadata")
}
sourceSets.main.get().resources.srcDir(generateModMetadata)
neoForge.ideSyncTask(generateModMetadata)

tasks.compileJava {
    options.encoding = "UTF-8"
}

/**
 * 审查修复哨兵验证。
 *
 * 当前项目尚未建立 JUnit 或 GameTest 用例集，直接添加 GameTest 会要求启动完整游戏服务端，
 * 成本较高且容易被外部模组依赖状态干扰。因此这里先用 Gradle 原生任务做最小可运行验证：
 * 读取关键源码文件，检查审查修复所依赖的结构是否仍然存在。
 *
 * 这类验证不能替代游戏内行为测试，但能防止最容易回归的关键修复被误删：
 * - 紫水晶纹饰必须使用 removeEffect + addEffect 缩短负面效果，不能回退到 update()
 * - 制箭台必须通过网络同步 BlockPos，并用距离限制 stillValid
 * - 回响碎片与深灰涂装必须具备跨重启静音恢复兜底
 * - 铜管网络必须重校验 networkId、同步端点、保持 transfer 契约一致
 * - 纹饰事件分发必须统一计数，并支持低频 tick 分配
 * - 精准追溯指针必须使用翻译键，中文语言文件不能出现重复键
 */
val reviewFixGuards = tasks.register("reviewFixGuards") {
    group = "verification"
    description = "验证代码审查关键修复点仍被最小覆盖。"

    fun readProjectFile(path: String): String = file(path).readText(Charsets.UTF_8)

    fun requireContains(source: String, expected: String, message: String) {
        if (!source.contains(expected)) {
            throw GradleException(message)
        }
    }

    fun requireNotContains(source: String, unexpected: String, message: String) {
        if (source.contains(unexpected)) {
            throw GradleException(message)
        }
    }

    fun requireUniqueJsonKeys(source: String, message: String) {
        val keyPattern = Regex("""^\s*"([^"]+)"\s*:""")
        val seenKeys = mutableSetOf<String>()
        val duplicateKeys = mutableSetOf<String>()
        source.lineSequence().forEach { line ->
            val key = keyPattern.find(line)?.groupValues?.get(1) ?: return@forEach
            if (!seenKeys.add(key)) {
                duplicateKeys.add(key)
            }
        }
        if (duplicateKeys.isNotEmpty()) {
            throw GradleException("$message 重复键：${duplicateKeys.joinToString(", ")}")
        }
    }

    doLast {
        val amethystTrimEffect = readProjectFile("src/main/java/com/spectrum_reclamation/spectrum_reclamation/trim/effect/AmethystTrimEffect.java")
        requireContains(
            amethystTrimEffect,
            "entity.removeEffect(replacement.getEffect());",
            "紫水晶纹饰缺少 removeEffect，负面效果时长缩短可能被 MobEffectInstance.update() 拒绝。"
        )
        requireContains(
            amethystTrimEffect,
            "entity.addEffect(replacement);",
            "紫水晶纹饰缺少 addEffect，负面效果替换流程不完整。"
        )
        requireNotContains(
            amethystTrimEffect,
            "effect.update(",
            "紫水晶纹饰不应使用 update() 缩短效果时长，NeoForge/Minecraft 会拒绝更短时长。"
        )

        val fletchingTableMenu = readProjectFile("src/main/java/com/spectrum_reclamation/spectrum_reclamation/inventory/FletchingTableMenu.java")
        requireContains(
            fletchingTableMenu,
            "buf.readBlockPos()",
            "制箭台客户端菜单缺少 BlockPos 读取，stillValid 距离校验无法可靠执行。"
        )
        requireContains(
            fletchingTableMenu,
            "distanceToSqr(",
            "制箭台 stillValid 缺少距离校验，玩家可能远距离继续使用 GUI。"
        )
        requireContains(
            fletchingTableMenu,
            "<= 64",
            "制箭台 stillValid 距离阈值应保持 8 格以内（平方距离 64）。"
        )
        requireContains(
            fletchingTableMenu,
            "public void onTake(Player player, ItemStack stack)",
            "制箭台输出槽缺少 onTake 消耗输入材料，普通点击可能复制产物。"
        )

        val fletchingTableMixin = readProjectFile("src/main/java/com/spectrum_reclamation/spectrum_reclamation/mixin/FletchingTableMixin.java")
        requireContains(
            fletchingTableMixin,
            "buf -> buf.writeBlockPos(pos)",
            "制箭台打开菜单时缺少 BlockPos 写入，客户端无法完成距离校验。"
        )

        val trimEffectEventHandler = readProjectFile("src/main/java/com/spectrum_reclamation/spectrum_reclamation/trim/TrimEffectEventHandler.java")
        requireContains(
            trimEffectEventHandler,
            "private static Map<TrimEffectHandler, Integer> countHandlersFromArmor(LivingEntity entity)",
            "纹饰事件分发缺少统一计数入口，重复 HashMap 聚合逻辑可能再次扩散。"
        )
        requireContains(
            trimEffectEventHandler,
            "handler.getTickInterval()",
            "纹饰 tick 分发缺少处理器自定义间隔，低频持续效果会退回每 tick 执行。"
        )
        requireContains(
            trimEffectEventHandler,
            "Math.floorMod(entity.level().getGameTime() + phase, interval) == 0",
            "纹饰 tick 分发缺少错峰相位，低频效果可能集中在同一 tick 执行。"
        )
        requireContains(
            trimEffectEventHandler,
            "entry.getKey().enforceState(player, entry.getValue());",
            "回响碎片纹饰缺少每 tick 静音兜底恢复，重启后可能永久静音。"
        )

        val trimEffectHandler = readProjectFile("src/main/java/com/spectrum_reclamation/spectrum_reclamation/trim/TrimEffectHandler.java")
        requireContains(
            trimEffectHandler,
            "default int getTickInterval()",
            "纹饰处理器接口缺少 tick 间隔扩展点，事件分发无法做低频分配。"
        )

        val preciseWaypointCompassItem = readProjectFile("src/main/java/com/spectrum_reclamation/spectrum_reclamation/item/custom/PreciseWaypointCompassItem.java")
        requireContains(
            preciseWaypointCompassItem,
            "Component.translatable(\"spectrum_reclamation.waypoint.precise_mode\")",
            "精准追溯指针必须使用翻译键显示精准模式提示。"
        )
        requireNotContains(
            preciseWaypointCompassItem,
            "✦ 精准模式",
            "精准追溯指针不应在 Java 代码中硬编码中文提示文本。"
        )

        val zhCn = readProjectFile("src/main/resources/assets/spectrum_reclamation/lang/zh_cn.json")
        requireUniqueJsonKeys(
            zhCn,
            "中文语言文件不应包含重复翻译键。"
        )

        val srEventHandler = readProjectFile("src/main/java/com/spectrum_reclamation/spectrum_reclamation/event/SREventHandler.java")
        requireContains(
            srEventHandler,
            "spectrum_reclamation:silent_until_tick",
            "深灰涂装缺少 silent_until_tick 检查，重启后可能永久静音。"
        )
        requireContains(
            srEventHandler,
            "persistentData.remove(key);",
            "深灰涂装静音恢复后必须清理 persistentData 标记，避免重复触发。"
        )

        val meteorCrossbowItem = readProjectFile("src/main/java/com/spectrum_reclamation/spectrum_reclamation/item/custom/MeteorCrossbowItem.java")
        requireContains(
            meteorCrossbowItem,
            "this.performShooting(level, player, player.getUsedItemHand(), stack, 3.5F, 1.0F, null);",
            "陨星弩瞄准释放必须进入发射流程，确保松开右键实际射出沉重之矛。"
        )
        requireContains(
            meteorCrossbowItem,
            "super.performShooting(level, shooter, hand, weapon, 3.5F, inaccuracy, target);",
            "陨星弩发射流程必须委托 CrossbowItem.performShooting，确保原版流程清空弹药并处理耐久。"
        )
        requireContains(
            meteorCrossbowItem,
            "player.getAbilities().instabuild && player.getProjectile(crossbowStack).isEmpty()",
            "陨星弩创造模式缺少无弹药兜底，玩家没有沉重之矛时无法装填发射。"
        )

        val srItems = readProjectFile("src/main/java/com/spectrum_reclamation/spectrum_reclamation/registry/SRItems.java")
        requireContains(
            srItems,
            "new Item.Properties().stacksTo(1).durability(465)",
            "陨星弩缺少耐久属性，CrossbowItem 的发射耐久消耗无法生效。"
        )

        val thrownHeavySpear = readProjectFile("src/main/java/com/spectrum_reclamation/spectrum_reclamation/entity/ThrownHeavySpear.java")
        requireContains(
            thrownHeavySpear,
            "target.hurtMarked = true;",
            "沉重之矛紫色涂装传送后缺少目标运动同步标记，客户端可能短暂不同步。"
        )
        requireContains(
            thrownHeavySpear,
            "shooter.hurtMarked = true;",
            "沉重之矛紫色涂装传送后缺少射手运动同步标记，客户端可能短暂不同步。"
        )

        val livingTrapBlock = readProjectFile("src/main/java/com/spectrum_reclamation/spectrum_reclamation/block/LivingTrapBlock.java")
        requireContains(
            livingTrapBlock,
            "implements EntityBlock",
            "活体陷阱必须实现 EntityBlock，才能创建持久化方块实体。"
        )
        requireNotContains(
            livingTrapBlock,
            "TRAPPED_ENTITIES",
            "活体陷阱不应继续使用静态 Map 保存被困实体，重启后会丢失状态。"
        )

        val livingTrapBlockEntity = readProjectFile("src/main/java/com/spectrum_reclamation/spectrum_reclamation/block_entity/LivingTrapBlockEntity.java")
        requireContains(
            livingTrapBlockEntity,
            "tag.putUUID(\"TrappedEntityId\", trappedEntityId);",
            "活体陷阱方块实体必须持久化被困实体 UUID，避免重启后无法释放。"
        )
        requireContains(
            livingTrapBlockEntity,
            "tag.putInt(\"CooldownTicksRemaining\", cooldownTicksRemaining);",
            "活体陷阱方块实体必须持久化冷却剩余时间，避免释放后无法复位。"
        )
        requireContains(
            livingTrapBlockEntity,
            "private static final int MAX_TRAPPED_ENTITY_LOOKUP_RETRIES = 20;",
            "活体陷阱实体 UUID 短暂查不到时必须保留重试窗口，避免区块加载顺序导致误清空。"
        )

        val srBlockEntities = readProjectFile("src/main/java/com/spectrum_reclamation/spectrum_reclamation/registry/SRBlockEntities.java")
        requireContains(
            srBlockEntities,
            "LivingTrapBlockEntity::new",
            "活体陷阱缺少 BlockEntityType 注册，方块实体无法创建。"
        )

        val copperPipeBlockEntity = readProjectFile("src/main/java/com/spectrum_reclamation/spectrum_reclamation/block_entity/CopperPipeBlockEntity.java")
        requireContains(
            copperPipeBlockEntity,
            "revalidateNetworkId();",
            "铜管加载时必须重校验 networkId，避免旧存档或已合并旧网络造成幽灵网络。"
        )
        requireContains(
            copperPipeBlockEntity,
            "endpointBE.syncWithAdjacentPipeNetwork();",
            "铜管加载和合并后必须主动同步相邻端点，避免端点先加载后永久孤立。"
        )
        requireContains(
            copperPipeBlockEntity,
            "for (BlockPos node : oldNetwork.getNodesSnapshot())",
            "铜管网络合并更新 networkId 时必须只遍历旧网络快照，避免遍历合并后网络扩大影响面。"
        )

        val copperPipeEndpointBlockEntity = readProjectFile("src/main/java/com/spectrum_reclamation/spectrum_reclamation/block_entity/CopperPipeEndpointBlockEntity.java")
        requireContains(
            copperPipeEndpointBlockEntity,
            "public void syncWithAdjacentPipeNetwork()",
            "铜管端点必须提供相邻铜管同步入口，解决端点早于铜管加载的时序问题。"
        )
        requireContains(
            copperPipeEndpointBlockEntity,
            "unregisterFromCurrentNetwork();",
            "铜管端点切换网络前必须注销旧入口/出口，避免旧网络残留端点。"
        )

        val copperPipeNetwork = readProjectFile("src/main/java/com/spectrum_reclamation/spectrum_reclamation/util/CopperPipeNetwork.java")
        requireContains(
            copperPipeNetwork,
            "public Set<BlockPos> getNodesSnapshot()",
            "铜管网络必须提供节点快照，避免合并遍历时修改底层集合。"
        )
        requireContains(
            copperPipeNetwork,
            "return insertIntoEndpointContainer(level, itemStack, to).isEmpty();",
            "CopperPipeNetwork.transfer 必须遵守契约：只尝试插入目标端点容器，不再掉落或伪装提取入口。"
        )
        requireContains(
            copperPipeNetwork,
            "if (targetContainer == null || !canFullyInsertIntoContainer(targetContainer, itemStack))",
            "CopperPipeNetwork.transfer 必须先确认目标容器可全量插入，避免失败返回时已经部分写入。"
        )
        requireContains(
            copperPipeNetwork,
            "private static final int BFS_VISIT_LIMIT = 2048;",
            "铜管 BFS 必须有遍历压力守卫，避免异常大网络在单次 tick 中无限扩张成本。"
        )
        requireNotContains(
            copperPipeNetwork,
            "new ItemEntity(level",
            "CopperPipeNetwork.transfer 不应生成掉落物，溢出处理应由调用方决定。"
        )

        val copperPipeTickHandler = readProjectFile("src/main/java/com/spectrum_reclamation/spectrum_reclamation/event/CopperPipeTickHandler.java")
        requireContains(
            copperPipeTickHandler,
            "new ArrayList<>(network.getEntryPoints())",
            "铜管 tick 遍历入口时必须使用快照，避免传输过程中端点同步修改集合导致遍历异常。"
        )

        // insertIntoContainer 已从 CopperPipeTickHandler 迁移到 CopperPipeNetwork，
        // 以下检查合并到已有的 copperPipeNetwork 变量块中
        requireContains(
            copperPipeNetwork,
            "ItemStack toInsert = remaining.copyWithCount(canInsert);",
            "铜管插入空槽必须写入 ItemStack 副本，避免容器槽位与剩余返回值共享引用。"
        )
    }
}

tasks.named("check") {
    dependsOn(reviewFixGuards)
}

idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}
