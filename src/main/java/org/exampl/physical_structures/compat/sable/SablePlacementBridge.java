package org.exampl.physical_structures.compat.sable;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Мост к sable-schematic-api для размещения его собственного формата блупринтов
 * (произвольный {@code .nbt}, сохранённый через {@code /sablebp save}, инструмент
 * {@code blueprint_tool} или предмет {@code camera}).
 *
 * <h3>Почему командой, а не прямой Java-зависимостью</h3>
 * <p>sable-schematic-api сам предупреждает в README, что его публичный API
 * (пакет {@code dev.rew1nd.sableschematicapi.api.*}) "ещё развивается". Классы,
 * которые реально нужны для декодирования и размещения блупринта
 * ({@code SableBlueprint}, {@code SableBlueprintPlacer}), лежат вне {@code api}
 * пакета — то есть формально не гарантированы как стабильный контракт. Если
 * physical_structures скомпилируется напрямую против его jar, любое изменение
 * сигнатуры в новой версии тихо сломает интеграцию рантайм-ошибкой
 * ({@code NoSuchMethodError}), которую пользователь увидит только в проде.</p>
 *
 * <p>Вместо этого используется ровно тот же приём, что уже применяется для
 * {@code create_aeronautics_toolgun} в {@link org.exampl.physical_structures.compat.toolgun.ToolgunPlacementBridge}:
 * делегирование через встроенную серверную Brigadier-команду мода,
 * {@code /sablebp load <name>}. Это:</p>
 * <ul>
 *   <li>публичный, заявленный самим модом интерфейс, а не деталь реализации;</li>
 *   <li>не требует {@code ServerPlayer} — сам {@code SableBlueprintCommands.loadSchematic}
 *       использует только позицию {@link CommandSourceStack}, без специфичной для
 *       игрока логики (в отличие от Toolgun, тут нет разделения на "с игроком" /
 *       "без игрока" веток);</li>
 *   <li>не требует compileOnly-зависимости на jar sable-schematic-api вообще —
 *       весь мост работает через vanilla/NeoForge API (Brigadier, CommandSourceStack).</li>
 * </ul>
 *
 * <h3>Откуда команда берёт файл</h3>
 * <p>{@code /sablebp load <name>} читает {@code <world>/sable_blueprints/<name>.nbt}
 * (см. {@code SableBlueprintFiles.path}). Это тот же файл и тот же формат, что
 * создают {@code /sablebp save}, инструмент {@code blueprint_tool} (сохранение
 * через диалог) и предмет {@code camera} — все они в конечном счёте пишут
 * {@code NbtIo.writeCompressed(blueprint.save(), ...)}. Если у игрока файл лежит
 * в клиентской папке {@code Sable-Schematics} (сохранён через camera/blueprint_tool
 * локально), его нужно один раз скопировать на сервер в
 * {@code <world>/sable_blueprints/<name>.nbt} — дальше он доступен через
 * physical_structures без дальнейшего участия инструментов sable-schematic-api.</p>
 */
public final class SablePlacementBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger("PhysicalStructures/SableBridge");

    private SablePlacementBridge() {}

    /**
     * Делегирует размещение Sable-блупринта в sable-schematic-api через его
     * собственную команду {@code /sablebp load <name>}, выполняемую от имени
     * консоли сервера (permission level 4 по умолчанию — команде достаточно 2),
     * с позицией, принудительно установленной на {@code origin}.
     *
     * @param level         серверный уровень
     * @param origin        позиция размещения (передаётся как позиция
     *                      {@link CommandSourceStack}, так как сама команда
     *                      {@code /sablebp load} не принимает координаты явным
     *                      аргументом и всегда использует позицию источника)
     * @param blueprintName имя блупринта без расширения, как лежит в
     *                      {@code <world>/sable_blueprints/<имя>.nbt}
     * @return {@code true} если команда выполнилась успешно (Brigadier-результат &gt; 0)
     */
    public static boolean place(ServerLevel level, BlockPos origin, String blueprintName) {
        MinecraftServer server = level.getServer();

        CommandSourceStack source = server.createCommandSourceStack()
                .withLevel(level)
                .withPosition(new Vec3(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5))
                .withSuppressedOutput(); // не засорять консоль системными "Success" сообщениями

        String command = "sablebp load " + quote(blueprintName);

        try {
            int result = server.getCommands().getDispatcher().execute(command, source);
            if (result > 0) {
                LOGGER.info("[SableBridge] '{}' placed via /sablebp load at {}.", blueprintName, origin);
                return true;
            }
            LOGGER.warn("[SableBridge] '{}' command returned 0 (not placed) at {}.", blueprintName, origin);
            return false;
        } catch (CommandSyntaxException e) {
            // Файл не найден / повреждён / пустой sub-level и т.д. — sable-schematic-api
            // сам разбирается в деталях через свою диагностику; здесь мы получаем только
            // код результата команды.
            LOGGER.warn("[SableBridge] sable-schematic-api rejected '{}': {}", blueprintName, e.getMessage());
            return false;
        } catch (Exception e) {
            LOGGER.error("[SableBridge] Unexpected error placing '{}' via /sablebp load", blueprintName, e);
            return false;
        }
    }

    /** Brigadier StringArgumentType.string() требует кавычки для имён с пробелами/спецсимволами. */
    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
