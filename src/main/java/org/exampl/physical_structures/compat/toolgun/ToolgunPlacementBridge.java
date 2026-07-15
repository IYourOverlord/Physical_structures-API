package org.exampl.physical_structures.compat.toolgun;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

/**
 * Мост к Toolgun (create_aeronautics_toolgun) для размещения {@code .excraft} блупринтов.
 *
 * <h3>Почему НЕ рефлексия в package-private методы Toolgun</h3>
 * <p>Декомпиляция create_aeronautics_toolgun-0.2.0.jar показала, что <b>весь</b> стек
 * размещения физических структур ({@code CreatePhysicalSchematicSupport},
 * {@code PortableStructurePreviewData}, {@code SubLevelFileStore}) объявлен
 * package-private. Ни одного {@code public interface}, NeoForge-события или
 * capability для сторонних модов в этой версии нет — мод не проектировался для
 * внешней интеграции. Прежняя реализация моста вызывала package-private
 * {@code CreatePhysicalSchematicSupport.loadToolgunBlueprint(...)} через рефлексию
 * с сигнатурой, угаданной по декомпилированному байткоду, включая enum
 * {@code PlacementSnapMode} — класс, который декомпилятор не смог восстановить
 * (см. {@code PlacementSnapMode.java} → {@code // INTERNAL ERROR //} в исходном
 * архиве). Любое изменение этой сигнатуры в новой версии Toolgun молча ломает
 * рефлексию без compile-time предупреждения.</p>
 *
 * <h3>Почему именно команда {@code /aerotoolgun print_blueprint}</h3>
 * <p>Toolgun сам регистрирует серверную Brigadier-команду
 * {@code PortableStructurePrinterCommands} (permission level 4):</p>
 * <pre>{@code
 * /aerotoolgun print_blueprint <file> <pos>
 * }</pre>
 * <p>Это единственная точка входа в Toolgun 0.2.0, которая:</p>
 * <ul>
 *   <li>является публичным, заявленным самим модом интерфейсом (а не деталью реализации);</li>
 *   <li>не требует {@code ServerPlayer} — работает от {@link CommandSourceStack}
 *       консоли/блока-командоблока (см. ниже про физические Create-схемы);</li>
 *   <li>сама разбирает оба формата блупринтов и сама выбирает правильный внутренний
 *       путь — нам не нужно знать ни {@code SubLevelFileStore}, ни {@code PlacementSnapMode}.</li>
 * </ul>
 *
 * <h3>Важное ограничение, унаследованное от самого Toolgun (не от моста)</h3>
 * <p>Команда обрабатывает {@code .excraft} файлы (формат {@code SubLevelFileStore})
 * полностью корректно без игрока. Но если файл — это Create-физическая схема
 * (тег {@code sub_levels}, путь через {@code CreatePhysicalSchematicSupport}), её
 * безыгроковая ветка в Toolgun 0.2.0 жёстко бросает
 * {@code IOException("server-side physical schematic placement requires player context")}
 * — это ограничение самого Toolgun, видимое в декомпилированном байткоде
 * {@code CreatePhysicalSchematicSupport.placeCreatePhysicalSchematic(ServerLevel, ...)}.
 * Обхода нет ни через эту команду, ни через рефлексию, ни через блок-принтер
 * Toolgun — все три пути ведут в один и тот же internal-метод. Для обычных
 * {@code .excraft}-блупринтов (то, что хранит {@code <gamedir>/blueprints/*.excraft},
 * формат {@code SubLevelFileStore}) это ограничение не действует.</p>
 *
 * <p>Если игрок в момент вызова доступен, передайте его в
 * {@link #place(ServerLevel, BlockPos, String, ServerPlayer)} — команда выполнится
 * от его {@link CommandSourceStack}, и Create-физические схемы (требующие
 * игрового контекста) разместятся корректно. Без игрока (перегрузка с тремя
 * параметрами, или {@code player == null}) команда всё равно отрабатывает: для
 * {@code .excraft} полностью успешно, для Create-физических схем — с ошибкой,
 * которую мост перехватывает и логирует как предупреждение (не падает).</p>
 */
public final class ToolgunPlacementBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger("PhysicalStructures/ToolgunBridge");

    private ToolgunPlacementBridge() {}

    /**
     * Делегирует размещение {@code .excraft} блупринта в Toolgun через его
     * собственную команду {@code /aerotoolgun print_blueprint <file> <pos>},
     * выполняемую от имени консоли сервера (permission level 4).
     *
     * <p>Используйте эту перегрузку, когда инициатор размещения — не конкретный
     * игрок (например, программный вызов из другого мода, генератор структур,
     * сервер-сайд событие). Для Create-физических схем ({@code sub_levels})
     * без игрока команда вернёт {@code false} — см. ограничение в классовом javadoc.</p>
     *
     * @param level         серверный уровень
     * @param origin        позиция для размещения (origin структуры)
     * @param blueprintName имя блупринта без расширения (как лежит в
     *                      {@code <gamedir>/blueprints/<имя>.excraft})
     * @return {@code true} если команда выполнилась успешно (Brigadier-результат &gt; 0)
     */
    public static boolean place(ServerLevel level, BlockPos origin, String blueprintName) {
        return place(level, origin, blueprintName, null);
    }

    /**
     * Делегирует размещение {@code .excraft} блупринта в Toolgun через его
     * собственную команду {@code /aerotoolgun print_blueprint <file> <pos>}.
     *
     * <p>Если {@code player} передан — команда выполняется от его
     * {@link CommandSourceStack} (через {@link ServerPlayer#createCommandSourceStack()}),
     * что даёт Toolgun реальный игровой контекст. Это единственный способ корректно
     * разместить Create-физическую схему (тег {@code sub_levels}) — без игрока та
     * ветка в Toolgun 0.2.0 гарантированно завершается ошибкой
     * (см. классовый javadoc, раздел про ограничение).</p>
     *
     * <p>Игрок не обязан физически находиться рядом с {@code origin} — координаты
     * передаются в саму команду явно, Toolgun не использует позицию игрока для
     * размещения, только для прав доступа и (в общем случае Create-схем) для
     * владельца файла схематики, который он создаёт на лету.</p>
     *
     * @param level         серверный уровень
     * @param origin        позиция для размещения (origin структуры)
     * @param blueprintName имя блупринта без расширения (как лежит в
     *                      {@code <gamedir>/blueprints/<имя>.excraft})
     * @param player        игрок, от чьего permission-контекста выполнять команду,
     *                      или {@code null} для выполнения от имени консоли сервера
     * @return {@code true} если команда выполнилась успешно (Brigadier-результат &gt; 0)
     */
    public static boolean place(ServerLevel level, BlockPos origin, String blueprintName,
                                 @Nullable ServerPlayer player) {
        var server = level.getServer();

        // С игроком: его собственный CommandSourceStack, но с правом level 4
        // принудительно поднятым для этого конкретного объекта — Toolgun-команда
        // (PortableStructurePrinterCommands) требует source.hasPermission(4), и мы
        // не хотим требовать от игрока-инициатора реальных прав оператора только
        // для того, чтобы спавнить структуру программно. withPermission(4) действует
        // исключительно на этот один CommandSourceStack-объект — не выдаёт игроку op
        // и не меняет его права в мире.
        // Без игрока: консоль сервера — уже permission level 4 по умолчанию.
        CommandSourceStack source = (player != null
                ? player.createCommandSourceStack().withPermission(4)
                : server.createCommandSourceStack())
                .withLevel(level)
                .withSuppressedOutput(); // не засорять чат/консоль системными "Success" сообщениями

        String command = "aerotoolgun print_blueprint " + quote(blueprintName)
                + " " + origin.getX() + " " + origin.getY() + " " + origin.getZ();

        try {
            int result = server.getCommands().getDispatcher().execute(command, source);
            if (result > 0) {
                LOGGER.info("[ToolgunBridge] '{}' placed via /aerotoolgun at {} ({}).",
                        blueprintName, origin, player != null ? "player " + player.getGameProfile().getName() : "console");
                return true;
            }
            LOGGER.warn("[ToolgunBridge] '{}' command returned 0 (not placed) at {}.", blueprintName, origin);
            return false;
        } catch (CommandSyntaxException e) {
            // Toolgun command не нашёл аргументы / file not found / unsupported schematic —
            // включая описанное выше ограничение для физических Create-схем без игрока.
            LOGGER.warn("[ToolgunBridge] Toolgun rejected '{}': {}", blueprintName, e.getMessage());
            return false;
        } catch (Exception e) {
            LOGGER.error("[ToolgunBridge] Unexpected error placing '{}' via Toolgun command", blueprintName, e);
            return false;
        }
    }

    /** Brigadier StringArgumentType.string() требует кавычки для имён с пробелами/спецсимволами. */
    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
