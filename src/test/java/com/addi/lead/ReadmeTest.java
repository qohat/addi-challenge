package com.addi.lead;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.addi.lead.cli.Cli;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The README is prose and no test reviews prose. These two check the parts of it that can silently
 * stop being true: a command it teaches that the CLI would reject, and a pointer to a file that
 * moved. Both are what a reader cannot see and a rename does.
 */
class ReadmeTest {

    private static final Path README = Path.of("README.md");

    /** The invocations the README teaches, written as a reader would type them, bash or PowerShell. */
    private static final Pattern COMMAND = Pattern.compile("(?m)^\\s*(?:\\$|PS>) lead-validation (.+)$");

    /** Repository paths, by their roots, so runtime state and build output are not candidates. */
    private static final Pattern REPOSITORY_PATH =
            Pattern.compile("(docs|specs|src|fixtures|prompts|scripts|\\.claude)/[\\w./-]*");

    private static String readme() throws IOException {
        return Files.readString(README);
    }

    @Test
    void teachesCommandsTheCliAccepts() throws IOException {
        var commands = COMMAND.matcher(readme()).results().map(match -> match.group(1)).toList();

        assertTrue(commands.size() >= 3, "The README shows fewer commands than the CLI has: " + commands);
        for (var command : commands) {
            var args = command.trim().split("\\s+");
            assertFalse(
                    Cli.parse(args) instanceof Cli.Invocation.InputError,
                    "The README teaches a command the CLI rejects: " + command);
        }
    }

    @Test
    void pointsOnlyAtFilesThatExist() throws IOException {
        var paths = REPOSITORY_PATH
                .matcher(readme())
                .results()
                .map(match -> match.group().replaceAll("[./]+$", ""))
                .distinct()
                .toList();

        assertTrue(paths.size() >= 5, "The README has stopped pointing at the repository: " + paths);
        for (var path : paths) {
            assertTrue(Files.exists(Path.of(path)), "The README points at " + path + ", which does not exist.");
        }
    }

    @Test
    void keepsEverySectionTheBriefAsksFor() throws IOException {
        var headings = List.of(
                "## What it is",
                "## Run it",
                "## How it works",
                "## Decisions and assumptions",
                "## How it was built",
                "## Working with AI",
                "## What it cost",
                "## Pending improvements",
                "## Repository map");

        var readme = readme();
        var previous = -1;
        for (var heading : headings) {
            var at = readme.indexOf(heading);
            assertTrue(at > previous, "The README is missing " + heading + " or has it out of order.");
            previous = at;
        }
    }
}
