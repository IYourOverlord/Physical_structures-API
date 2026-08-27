package org.exampl.physical_structures.compat.sable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Интеграция physical_structures с sable-schematic-api.
 *
 * <p>Позволяет вызывать структуры из {@code .nbt} файлов, сохранённых
 * sable-schematic-api (командой {@code /sablebp save}, инструментом
 * {@code blueprint_tool} или предметом {@code camera} — все три пишут один и
 * тот же формат). Когда блок-спаунер или предмет-спаунер physical_structures
 * настроен на id вида {@code sable:<имя_файла>}, этот класс:</p>
 * <ol>
 *   <li>Проверяет, что файл {@code <world>/sable_blueprints/<имя>.nbt} существует
 *       (понятная ошибка игроку заранее, а не после неудачного выполнения команды).</li>
 *   <li>Делегирует размещение в sable-schematic-api через
 *       {@link SablePlacementBridge}, который выполняет встроенную команду
 *       {@code /sablebp load <имя>}.</li>
 * </ol>
 *
 * <p><b>Важно:</b> файл должен лежать именно в серверной папке мира
 * {@code sable_blueprints}, а не в клиентской {@code Sable-Schematics}, которую
 * использует инструмент {@code blueprint_tool}/камера на клиенте. Это осознанное
 * ограничение: цель интеграции — разместить блупринт средствами physical_structures
 * ("только используя инструменты моего проекта"), не трогая клиентские инструменты
 * sable-schematic-api вообще. Экспорт из клиентской папки на сервер — разовое
 * действие сборки контента, а не часть игрового процесса.</p>
 *
 * <p><b>Опциональная зависимость:</b> весь класс безопасно загружается даже без
 * sable-schematic-api — {@link SableCompat#initIfPresent()} проверяет это заранее
 * и просто не регистрирует провайдера, если мод отсутствует.</p>
 */
public final class SableStructureHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("PhysicalStructures/SableCompat");

    /** Namespace, который physical_structures использует для Sable-блупринтов. */
    public static final String SABLE_NAMESPACE = "sable";

    /** Директория с .nbt файлами в папке мира — совпадает с SableBlueprintFiles.DIRECTORY. */
    private static final String BLUEPRINTS_DIR = "sable_blueprints";
    private static final String EXTENSION = ".nbt";

    private SableStructureHandler() {}

    // ---------------------------------------------------------------- public API

    /**
     * Возвращает true если {@code id} — это ссылка на Sable-блупринт
     * (т.е. namespace == {@value #SABLE_NAMESPACE}).
     */
    public static boolean isSableId(ResourceLocation id) {
        return SABLE_NAMESPACE.equals(id.getNamespace());
    }

    /**
     * Главная точка входа: обрабатывает активацию блок-спаунера/предмета для
     * Sable-блупринта.
     *
     * @param level    серверный уровень
     * @param spawnPos позиция, куда нужно разместить структуру (origin)
     * @param id       ResourceLocation вида {@code sable:<имя>}
     * @param player   игрок-инициатор (может быть null — размещение работает
     *                 одинаково в обоих случаях, sable-schematic-api не требует
     *                 игрового контекста для {@code /sablebp load})
     * @return true если размещение делегировано успешно
     */
    public static boolean triggerSable(ServerLevel level,
                                        BlockPos spawnPos,
                                        ResourceLocation id,
                                        @Nullable Player player) {
        String fileName = id.getPath(); // "my_ship" -> ищем my_ship.nbt

        Path file = resolveBlueprintFile(level.getServer(), fileName);
        if (file == null) {
            msg(player, "§c[PhysicalStructures] Invalid Sable blueprint name: " + fileName);
            return false;
        }

        if (!Files.isRegularFile(file)) {
            msg(player, "§c[PhysicalStructures] Sable blueprint file not found: " + file
                    + ". Copy the .nbt into the world's '" + BLUEPRINTS_DIR + "' folder first.");
            LOGGER.error("[SableCompat] File not found: {}", file);
            return false;
        }

        boolean ok = SablePlacementBridge.place(level, spawnPos, fileName);
        if (!ok) {
            msg(player, "§c[PhysicalStructures] sable-schematic-api could not place '" + fileName + "'. "
                    + "Check the server log for diagnostics from /sablebp load.");
        }
        return ok;
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Резолвит путь к файлу так же, как это делает {@code SableBlueprintFiles.path}
     * внутри sable-schematic-api (та же нормализация имени и та же директория),
     * чтобы проверка "файл существует" точно соответствовала тому, что реально
     * прочитает {@code /sablebp load}.
     */
    @Nullable
    private static Path resolveBlueprintFile(MinecraftServer server, String rawName) {
        String normalized = rawName.trim().toLowerCase(Locale.ROOT);
        if (normalized.endsWith(EXTENSION)) {
            normalized = normalized.substring(0, normalized.length() - EXTENSION.length());
        }
        if (normalized.isEmpty()
                || normalized.contains("\\")
                || normalized.contains("/")
                || normalized.contains("..")) {
            return null;
        }

        Path directory = server.getWorldPath(LevelResource.ROOT).resolve(BLUEPRINTS_DIR).toAbsolutePath().normalize();
        Path path = directory.resolve(normalized + EXTENSION).normalize();
        if (!path.startsWith(directory)) {
            return null;
        }
        return path;
    }

    private static void msg(@Nullable Player player, String text) {
        if (player != null) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(text));
        }
    }
}
