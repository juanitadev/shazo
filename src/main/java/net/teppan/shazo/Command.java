package net.teppan.shazo;

/**
 * Marker interface for storage directives produced by a {@link Describer}.
 *
 * <p>{@code Command} is an open interface — any package can define new
 * implementations without modifying the framework.  Repository implementations
 * dispatch on the command types they support and throw {@link ShazoException}
 * for unrecognised types:
 *
 * <pre>{@code
 * switch (command) {
 *     case NoOpCommand   ()   -> { }
 *     case SqlCommand    sql  -> executeSql(conn, sql);
 *     case ShellCommand  sh   -> executeShell(sh);
 *     default -> throw new ShazoException(
 *         "Unsupported command type: " + command.getClass().getName());
 * }
 * }</pre>
 *
 * <p>The framework ships three built-in implementations:
 * <ul>
 *   <li>{@link NoOpCommand} — skip; universal no-op</li>
 *   <li>{@link net.teppan.shazo.jdbc.SqlCommand} — JDBC SQL statement</li>
 *   <li>{@link net.teppan.shazo.shell.ShellCommand} — external process</li>
 * </ul>
 *
 * <p>Storage-specific extensions live in their own packages and implement
 * this interface directly — for example,
 * {@link net.teppan.shazo.file.FileCommand} for file-system operations.
 *
 * @see Describer
 * @see NoOpCommand
 * @see net.teppan.shazo.jdbc.SqlCommand
 * @see net.teppan.shazo.shell.ShellCommand
 * @see net.teppan.shazo.file.FileCommand
 */
public interface Command {
}
