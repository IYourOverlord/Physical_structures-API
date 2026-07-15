package org.exampl.physical_structures.compat.toolgun;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Интеграция physical_structures с create_aeronautics_toolgun.
 *
 * <p>Позволяет вызывать структуры из файлов {@code .excraft}, которые
 * создаёт мод Toolgun. Когда блок-спаунер настроен на id вида
 * {@code excraft:<имя_файла>}, этот класс:</p>
 * <ol>
 *   <li>Проверяет, что файл {@code <gamedir>/blueprints/<имя>.excraft} существует
 *       (без парсинга NBT — Toolgun сам валидирует формат при размещении).</li>
 *   <li>Делегирует размещение в Toolgun через {@link ToolgunPlacementBridge},
 *       который выполняет встроенную команду Toolgun
 *       {@code /aerotoolgun print_blueprint}.</li>
 * </ol>
 *
 * <p><b>Изменение по сравнению с предыдущей версией:</b> раньше этот класс сам
 * читал и парсил NBT (включая ручную проверку тега {@code sub_levels}), кэшировал
 * его как {@code .nbt} в {@code config/physical_structures/excraft_cache/} и
 * регистрировал в {@link org.exampl.physical_structures.api.PhysicalStructureRegistry},
 * чтобы затем передать сырые байты в {@code ToolgunPlacementBridge}, который вызывал
 * package-private {@code CreatePhysicalSchematicSupport.loadToolgunBlueprint(...)}
 * через рефлексию. Этот путь требовал: знания внутреннего NBT-формата Toolgun,
 * точного угадывания сигнатуры приватного метода и enum {@code PlacementSnapMode}.</p>
 *
 * <p>Теперь файл не парсится и не кэшируется вообще — Toolgun делает это сам внутри
 * своей же команды. Наш реестр {@code PhysicalStructureRegistry} не используется
 * для {@code excraft:}-структур, так как размещением полностью владеет Toolgun;
 * мы только проверяем наличие файла, чтобы дать понятную ошибку игроку/логу
 * до вызова команды, а не после.</p>
 *
 * <p><b>Опциональная зависимость:</b> весь класс безопасно загружается даже без
 * Toolgun — {@link ExcraftCompat#isAvailable()} проверяет это заранее.</p>
 */
public final class ExcraftStructureHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("PhysicalStructures/ExcraftCompat");

    /** Namespace, который physical_structures использует для .excraft структур */
    public static final String EXCRAFT_NAMESPACE = "excraft";

    /** Директория с .excraft файлами (рядом с игрой) — совпадает с Toolgun'овским SubLevelFileStore.getSaveDirectory(). */
    private static final String BLUEPRINTS_DIR = "blueprints";

    private ExcraftStructureHandler() {}

    // ---------------------------------------------------------------- public API

    /**
     * Возвращает true если {@code id} — это ссылка на .excraft файл
     * (т.е. namespace == {@value #EXCRAFT_NAMESPACE}).
     */
    public static boolean isExcraftId(ResourceLocation id) {
        return EXCRAFT_NAMESPACE.equals(id.getNamespace());
    }

    /**
     * Главная точка входа: обрабатывает активацию блок-спаунера для .excraft структуры.
     *
     * <p>Алгоритм:</p>
     * <ol>
     *   <li>Проверяет наличие {@code .excraft} файла по имени из {@code id.getPath()}
     *       (понятная ошибка игроку, если файла нет — Toolgun-команда в этом случае
     *       просто молча вернёт 0).</li>
     *   <li>Выполняет {@code /aerotoolgun print_blueprint} через {@link ToolgunPlacementBridge}.</li>
     * </ol>
     *
     * @param level    серверный уровень
     * @param spawnPos позиция, куда нужно разместить структуру (origin)
     * @param id       ResourceLocation вида {@code excraft:<имя>}
     * @param player   игрок-инициатор (может быть null или не {@link ServerPlayer} —
     *                 в этом случае команда выполнится от имени консоли сервера;
     *                 если это реальный {@link ServerPlayer}, команда выполняется
     *                 от его {@code CommandSourceStack}, что нужно для размещения
     *                 Create-физических схем — см. {@link ToolgunPlacementBridge})
     * @return true если размещение делегировано успешно
     */
    public static boolean triggerExcraft(ServerLevel level,
                                          BlockPos spawnPos,
                                          ResourceLocation id,
                                          @Nullable Player player) {
        String fileName = id.getPath(); // "my_ship" -> ищем my_ship.excraft
        Path excraftFile = FMLPaths.GAMEDIR.get().resolve(BLUEPRINTS_DIR).resolve(fileName + ".excraft");

        if (!Files.isRegularFile(excraftFile)) {
            msg(player, "§c[PhysicalStructures] .excraft file not found: " + excraftFile);
            LOGGER.error("[ExcraftCompat] File not found: {}", excraftFile);
            return false;
        }

        ServerPlayer serverPlayer = (player instanceof ServerPlayer sp) ? sp : null;
        boolean ok = ToolgunPlacementBridge.place(level, spawnPos, fileName, serverPlayer);
        if (!ok) {
            msg(player, "§c[PhysicalStructures] Toolgun could not place '" + fileName + "'. " +
                    "If this is a physical (sub_levels) schematic and no player context was " +
                    "available, Toolgun 0.2.0 requires one for that format — see ToolgunPlacementBridge javadoc.");
        }
        return ok;
    }

    // ---------------------------------------------------------------- helpers

    private static void msg(@Nullable Player player, String text) {
        if (player != null) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(text));
        }
    }
}
