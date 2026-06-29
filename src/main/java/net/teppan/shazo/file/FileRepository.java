package net.teppan.shazo.file;

import net.teppan.shazo.AbstractRepository;
import net.teppan.shazo.Command;
import net.teppan.shazo.NoOpCommand;
import net.teppan.shazo.Describer;
import net.teppan.shazo.RawResult;
import net.teppan.shazo.ShazoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A {@link net.teppan.shazo.Repository} implementation backed by the local
 * file system, executing {@link FileCommand} directives produced by a
 * {@link Describer}.
 *
 * <p>All file operations are relative to the {@code baseDirectory} supplied at
 * construction. The directory is created automatically if it does not exist.
 *
 * <h2>Creating a repository</h2>
 * <pre>{@code
 * FileRepository<Memo> repo = new FileRepository<>(
 *     Path.of("./memos"),
 *     new FileMemoDescriber());
 * }</pre>
 *
 * <h2>Command semantics</h2>
 * <table class="striped">
 * <caption>FileCommand variants</caption>
 * <thead><tr><th>Command</th><th>Effect</th><th>Result rows</th></tr></thead>
 * <tbody>
 *   <tr><td>{@link FileCommand.Write}</td>
 *       <td>Writes (or overwrites) a file</td><td>none</td></tr>
 *   <tr><td>{@link FileCommand.Delete}</td>
 *       <td>Deletes a file; no-op if absent</td><td>none</td></tr>
 *   <tr><td>{@link FileCommand.Read}</td>
 *       <td>Reads a file</td>
 *       <td>one row {@code {"_content": text}} or empty if absent</td></tr>
 *   <tr><td>{@link FileCommand.List}</td>
 *       <td>Lists matching files, parses each, filters by predicate</td>
 *       <td>one row per matching file (keys from {@code rowParser})</td></tr>
 * </tbody>
 * </table>
 *
 * <p>{@link net.teppan.shazo.jdbc.SqlCommand}, {@link net.teppan.shazo.shell.ShellCommand}, and any other unrecognised
 * command types are rejected with {@link ShazoException}.
 * {@link NoOpCommand} is skipped silently.
 *
 * @param <T> the domain type managed by this repository
 * @see FileCommand
 * @see Describer
 */
public final class FileRepository<T> extends AbstractRepository<T> {

    private static final Logger log = LoggerFactory.getLogger(FileRepository.class);

    private final Path baseDirectory;

    /**
     * Constructs a {@code FileRepository} rooted at {@code baseDirectory}.
     *
     * @param baseDirectory the directory where files are stored; created
     *                      automatically if absent; never {@code null}
     * @param describer     the describer for domain type {@code T}; never {@code null}
     * @throws UncheckedIOException if the directory cannot be created
     */
    public FileRepository(Path baseDirectory, Describer<T> describer) {
        super(describer);
        this.baseDirectory = Objects.requireNonNull(baseDirectory, "baseDirectory");
        try {
            Files.createDirectories(baseDirectory);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create base directory: " + baseDirectory, e);
        }
    }

    @Override
    protected RawResult execute(List<Command> commands) throws ShazoException {
        var rows = new ArrayList<Map<String, Object>>();
        for (var command : commands) {
            switch (command) {
                case NoOpCommand()           -> { /* skip */ }
                case FileCommand.Write  w    -> executeWrite(w);
                case FileCommand.Delete d    -> executeDelete(d);
                case FileCommand.Read   r    -> rows.addAll(executeRead(r));
                case FileCommand.List   l    -> rows.addAll(executeList(l));
                default -> throw new ShazoException(
                    "FileRepository: unsupported command type: " + command.getClass().getName());
            }
        }
        return RawResult.of(rows);
    }

    // ── File operations ───────────────────────────────────────────────────────

    private void executeWrite(FileCommand.Write cmd) throws ShazoException {
        var path = resolve(cmd.name());
        log.debug("File.Write {}", path);
        try {
            Files.writeString(path, cmd.content());
        } catch (IOException e) {
            throw new ShazoException("Failed to write file: " + path, e);
        }
    }

    private void executeDelete(FileCommand.Delete cmd) throws ShazoException {
        var path = resolve(cmd.name());
        log.debug("File.Delete {}", path);
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new ShazoException("Failed to delete file: " + path, e);
        }
    }

    private List<Map<String, Object>> executeRead(FileCommand.Read cmd) throws ShazoException {
        var path = resolve(cmd.name());
        log.debug("File.Read {}", path);
        try {
            var content = Files.readString(path);
            return List.of(Map.of("_content", content));
        } catch (NoSuchFileException e) {
            return List.of();
        } catch (IOException e) {
            throw new ShazoException("Failed to read file: " + path, e);
        }
    }

    private List<Map<String, Object>> executeList(FileCommand.List cmd) throws ShazoException {
        log.debug("File.List {}  glob={}", baseDirectory, cmd.glob());
        try (var stream = Files.newDirectoryStream(baseDirectory, cmd.glob())) {
            var rows = new ArrayList<Map<String, Object>>();
            for (var path : stream) {
                if (!Files.isRegularFile(path)) continue;
                try {
                    var content = Files.readString(path);
                    var row     = cmd.rowParser().apply(content);
                    if (cmd.predicate().test(row)) {
                        rows.add(row);
                    }
                } catch (UncheckedIOException e) {
                    throw new ShazoException("Failed to read file during listing: " + path, e.getCause());
                }
            }
            return rows;
        } catch (IOException e) {
            throw new ShazoException("Failed to list directory: " + baseDirectory, e);
        }
    }

    // ── Path helper ───────────────────────────────────────────────────────────

    private Path resolve(String name) {
        return baseDirectory.resolve(name);
    }
}
