package net.teppan.shazo.shell;

import net.teppan.shazo.Command;
import net.teppan.shazo.NoOpCommand;
import net.teppan.shazo.jdbc.SqlCommand;
import net.teppan.shazo.shell.ShellCommand;
import net.teppan.shazo.Describer;
import net.teppan.shazo.Producer;
import net.teppan.shazo.ShazoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ShellRepository} and {@link LineParser}.
 *
 * <p>Tests use {@code sh -c "..."} to stay portable across Unix-like systems
 * without relying on the PATH containing any specific binary beyond {@code sh}.
 */
class ShellRepositoryTest {

    // ── Domain type under test ───────────────────────────────────────────────

    record Line(String text) {}

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private ShellRepository<Line> repo;

    @BeforeEach
    void setUp() {
        Describer<Line> describer = Describer.<Line>builder()
            .contains(l -> List.of(ShellCommand.of("sh", "-c",
                "echo " + l.text() + " | wc -l")))
            .store  (l -> List.of(NoOpCommand.INSTANCE))
            .delete (l -> List.of(NoOpCommand.INSTANCE))
            .retrieve(l -> List.of(ShellCommand.of("sh", "-c",
                "printf '%s' '" + l.text() + "'")))
            .catalog (l -> List.of(ShellCommand.of("sh", "-c",
                "printf '%s\\n' " + l.text())))
            // infuser: first non-blank stdout line → Line
            .infuser(result -> result.firstValue("line", Producer.asString())
                .map(Line::new).orElseThrow())
            // cataloger: each line → Line
            .cataloger(result -> result.rows().stream()
                .map(row -> new Line((String) row.get("line")))
                .toList())
            .build();

        repo = new ShellRepository<>(describer);
    }

    // ── retrieve ─────────────────────────────────────────────────────────────

    @Test
    void retrieveReturnsStdoutAsLine() throws ShazoException {
        var result = repo.retrieve(new Line("hello"));
        assertThat(result).contains(new Line("hello"));
    }

    @Test
    void retrieveEmptyOutputReturnsEmpty() throws ShazoException {
        // "true" exits 0 with no output; result has no rows → verifier returns false → Optional.empty()
        Describer<Line> noOutputDescriber = Describer.<Line>builder()
            .contains(l -> List.of(NoOpCommand.INSTANCE))
            .store   (l -> List.of(NoOpCommand.INSTANCE))
            .delete  (l -> List.of(NoOpCommand.INSTANCE))
            .retrieve(l -> List.of(ShellCommand.of("sh", "-c", "true")))
            .catalog (l -> List.of(ShellCommand.of("sh", "-c", "true")))
            .infuser(result -> new Line(""))
            .cataloger(result -> List.of())
            .build();

        var emptyRepo = new ShellRepository<>(noOutputDescriber);
        assertThat(emptyRepo.retrieve(new Line("anything"))).isEmpty();
    }

    // ── catalog ───────────────────────────────────────────────────────────────

    @Test
    void catalogReturnsMultipleLines() throws ShazoException {
        // sh -c "printf '%s\n' a b c" prints a, b, c on separate lines
        Describer<Line> multiDescriber = Describer.<Line>builder()
            .contains(l -> List.of(NoOpCommand.INSTANCE))
            .store   (l -> List.of(NoOpCommand.INSTANCE))
            .delete  (l -> List.of(NoOpCommand.INSTANCE))
            .retrieve(l -> List.of(NoOpCommand.INSTANCE))
            .catalog (l -> List.of(ShellCommand.of("sh", "-c", "printf '%s\\n' a b c")))
            .infuser (result -> new Line(""))
            .cataloger(result -> result.rows().stream()
                .map(row -> new Line((String) row.get("line")))
                .toList())
            .build();

        var lines = new ShellRepository<>(multiDescriber).catalog(new Line("ignored"));
        assertThat(lines).containsExactly(new Line("a"), new Line("b"), new Line("c"));
    }

    // ── LineParser.tabDelimited ───────────────────────────────────────────────

    @Test
    void tabDelimitedParserSplitsColumns() throws ShazoException {
        record Row(String id, String name, String score) {}

        Describer<Row> describer = Describer.<Row>builder()
            .contains(r -> List.of(NoOpCommand.INSTANCE))
            .store   (r -> List.of(NoOpCommand.INSTANCE))
            .delete  (r -> List.of(NoOpCommand.INSTANCE))
            .retrieve(r -> List.of(ShellCommand.of("sh", "-c",
                "printf '42\\tAlice\\t99.5'")))
            .catalog (r -> List.of(NoOpCommand.INSTANCE))
            .infuser(result -> result.first().map(row -> new Row(
                (String) row.get("id"),
                (String) row.get("name"),
                (String) row.get("score"))).orElseThrow())
            .cataloger(result -> List.of())
            .build();

        var tabRepo = new ShellRepository<>(describer,
            LineParser.tabDelimited("id", "name", "score"));

        assertThat(tabRepo.retrieve(new Row("", "", "")))
            .contains(new Row("42", "Alice", "99.5"));
    }

    @Test
    void tabDelimitedFillsMissingColumnsWithEmptyString() throws ShazoException {
        record Pair(String a, String b, String c) {}

        Describer<Pair> describer = Describer.<Pair>builder()
            .contains(p -> List.of(NoOpCommand.INSTANCE))
            .store   (p -> List.of(NoOpCommand.INSTANCE))
            .delete  (p -> List.of(NoOpCommand.INSTANCE))
            .retrieve(p -> List.of(ShellCommand.of("sh", "-c",
                "printf 'x\\ty'")))    // only 2 fields, 3 columns
            .catalog (p -> List.of(NoOpCommand.INSTANCE))
            .infuser(result -> result.first().map(row -> new Pair(
                (String) row.get("a"),
                (String) row.get("b"),
                (String) row.get("c"))).orElseThrow())
            .cataloger(result -> List.of())
            .build();

        var tabRepo = new ShellRepository<>(describer,
            LineParser.tabDelimited("a", "b", "c"));

        assertThat(tabRepo.retrieve(new Pair("", "", "")))
            .contains(new Pair("x", "y", ""));
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @Test
    void nonZeroExitCodeThrowsShazoException() {
        Describer<Line> describer = Describer.<Line>builder()
            .contains(l -> List.of(NoOpCommand.INSTANCE))
            .store   (l -> List.of(NoOpCommand.INSTANCE))
            .delete  (l -> List.of(NoOpCommand.INSTANCE))
            .retrieve(l -> List.of(ShellCommand.of("sh", "-c", "exit 42")))
            .catalog (l -> List.of(NoOpCommand.INSTANCE))
            .infuser (result -> new Line(""))
            .cataloger(result -> List.of())
            .build();

        var failingRepo = new ShellRepository<>(describer);
        assertThatThrownBy(() -> failingRepo.retrieve(new Line("x")))
            .isInstanceOf(ShazoException.class)
            .hasMessageContaining("42");
    }

    @Test
    void nonExistentCommandThrowsShazoException() {
        Describer<Line> describer = Describer.<Line>builder()
            .contains(l -> List.of(NoOpCommand.INSTANCE))
            .store   (l -> List.of(NoOpCommand.INSTANCE))
            .delete  (l -> List.of(NoOpCommand.INSTANCE))
            .retrieve(l -> List.of(ShellCommand.of(
                "__no_such_command_shazo_test__")))
            .catalog (l -> List.of(NoOpCommand.INSTANCE))
            .infuser (result -> new Line(""))
            .cataloger(result -> List.of())
            .build();

        var badRepo = new ShellRepository<>(describer);
        assertThatThrownBy(() -> badRepo.retrieve(new Line("x")))
            .isInstanceOf(ShazoException.class);
    }

    @Test
    void sqlCommandThrowsShazoException() {
        Describer<Line> describer = Describer.<Line>builder()
            .contains(l -> List.of(NoOpCommand.INSTANCE))
            .store   (l -> List.of(NoOpCommand.INSTANCE))
            .delete  (l -> List.of(NoOpCommand.INSTANCE))
            .retrieve(l -> List.of(SqlCommand.of("SELECT 1")))
            .catalog (l -> List.of(NoOpCommand.INSTANCE))
            .infuser (result -> new Line(""))
            .cataloger(result -> List.of())
            .build();

        var sqlRepo = new ShellRepository<>(describer);
        assertThatThrownBy(() -> sqlRepo.retrieve(new Line("x")))
            .isInstanceOf(ShazoException.class)
            .hasMessageContaining("SqlCommand");
    }

    // ── LineParser factory ────────────────────────────────────────────────────

    @Test
    void byLineParserMapsLineColumn() {
        var parser = LineParser.byLine();
        var row    = parser.parse("hello world");
        assertThat(row).containsEntry("line", "hello world");
    }

    @Test
    void tabDelimitedThrowsOnEmptyColumns() {
        assertThatThrownBy(() -> LineParser.tabDelimited())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void delimitedThrowsOnEmptyColumns() {
        assertThatThrownBy(() -> LineParser.delimited(","))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void delimitedParserSplitsOnCustomDelimiter() {
        var parser = LineParser.delimited(",", "x", "y");
        var row    = parser.parse("foo,bar");
        assertThat(row)
            .containsEntry("x", "foo")
            .containsEntry("y", "bar");
    }
}
