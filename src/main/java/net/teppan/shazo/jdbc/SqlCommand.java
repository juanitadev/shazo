package net.teppan.shazo.jdbc;

import net.teppan.shazo.Command;

import java.util.List;
import java.util.Objects;

/**
 * A parameterized SQL statement for use with {@link JdbcRepository}.
 *
 * <pre>{@code
 * List.of(SqlCommand.of("SELECT 1 FROM person WHERE id = ?", person.id()))
 * List.of(new SqlCommand("SELECT ...", paramList))
 * }</pre>
 *
 * @param statement  the SQL text with {@code ?} as positional placeholders
 * @param parameters the bind values in placeholder order; never {@code null}
 * @see JdbcRepository
 */
public record SqlCommand(String statement, List<Object> parameters) implements Command {

    /** Compact constructor — defensively copies the parameter list. */
    public SqlCommand {
        Objects.requireNonNull(statement, "statement");
        parameters = List.copyOf(parameters);
    }

    /**
     * Creates a {@code SqlCommand} with positional bind values.
     *
     * @param statement the SQL text
     * @param params    the bind values
     * @return a new {@code SqlCommand}
     */
    public static SqlCommand of(String statement, Object... params) {
        return new SqlCommand(statement, List.of(params));
    }

    /**
     * Creates a {@code SqlCommand} with no bind parameters.
     *
     * @param statement the SQL text
     * @return a new {@code SqlCommand}
     */
    public static SqlCommand of(String statement) {
        return new SqlCommand(statement, List.of());
    }
}
