package net.teppan.shazo;

/**
 * A no-operation placeholder that all {@link net.teppan.shazo.AbstractRepository}
 * implementations must skip silently.
 *
 * <p>Return {@link #INSTANCE} from a {@link Describer} operation method when
 * no storage action is required for that operation:
 *
 * <pre>{@code
 * .store(entity -> List.of(NoOpCommand.INSTANCE))   // store is a no-op
 * }</pre>
 */
public record NoOpCommand() implements Command {

    /** The canonical singleton instance. */
    public static final NoOpCommand INSTANCE = new NoOpCommand();
}
