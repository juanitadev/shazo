package net.teppan.shazo;

/**
 * Marker interface for storage directives produced by a {@link Describer}.
 *
 * <p>{@code Command} is an open interface — any package can define new
 * implementations without modifying the framework.  A {@link Describer} and its
 * {@link AbstractRepository} are parameterized by a single concrete command type
 * {@code C extends Command}, so each repository handles exactly one command type
 * and the binding is checked by the compiler.  A repository whose command type is
 * itself a sealed hierarchy (such as {@link net.teppan.shazo.file.FileCommand})
 * can dispatch with an exhaustive {@code switch} that needs no default branch:
 *
 * <pre>{@code
 * switch (command) {
 *     case FileCommand.Write  w -> executeWrite(w);
 *     case FileCommand.Delete d -> executeDelete(d);
 *     case FileCommand.Read   r -> executeRead(r);
 *     case FileCommand.List   l -> executeList(l);
 * }
 * }</pre>
 *
 * <p>The framework ships two built-in command types:
 * <ul>
 *   <li>{@link net.teppan.shazo.jdbc.SqlCommand} — JDBC SQL statement</li>
 *   <li>{@link net.teppan.shazo.shell.ShellCommand} — external process</li>
 * </ul>
 *
 * <p>Storage-specific extensions live in their own packages and implement
 * this interface directly — for example,
 * {@link net.teppan.shazo.file.FileCommand} for file-system operations.
 * An operation that requires no storage action returns an empty command list.
 *
 * @see Describer
 * @see net.teppan.shazo.jdbc.SqlCommand
 * @see net.teppan.shazo.shell.ShellCommand
 * @see net.teppan.shazo.file.FileCommand
 */
public interface Command {
}
