package com.sagongsa.backend.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.sagongsa.backend.decision.DecisionService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BackendDependencyCycleTest {
	@Test
	void compiledTopLevelProjectTypesHaveNoDependencyCycle(@TempDir Path temp) throws Exception {
		Path classes = Path.of(DecisionService.class.getProtectionDomain().getCodeSource().getLocation().toURI());
		Path output = temp.resolve("jdeps.txt");
		Path executable = Path.of(System.getProperty("java.home"), "bin", "jdeps");
		Process process = new ProcessBuilder(executable.toString(), "--ignore-missing-deps", "-verbose:class", "-filter:none", classes.toString())
			.redirectErrorStream(true).redirectOutput(output.toFile()).start();
		if (!process.waitFor(30, TimeUnit.SECONDS)) {
			process.destroyForcibly();
			throw new AssertionError("jdeps timed out");
		}
		String report = Files.readString(output);
		assertThat(process.exitValue()).as(report).isZero();
		Map<String, Set<String>> graph = new TreeMap<>();
		Pattern edge = Pattern.compile("^\\s+(com\\.sagongsa\\.backend\\.[\\w.$]+)\\s+->\\s+(com\\.sagongsa\\.backend\\.[\\w.$]+)");
		for (String line : report.lines().toList()) {
			var match = edge.matcher(line);
			if (!match.find()) continue;
			String from = match.group(1).split("\\$")[0];
			String to = match.group(2).split("\\$")[0];
			graph.computeIfAbsent(from, ignored -> new TreeSet<>());
			graph.computeIfAbsent(to, ignored -> new TreeSet<>());
			if (!from.equals(to)) graph.get(from).add(to);
		}
		assertThat(graph.size()).isGreaterThan(100);
		Set<String> visited = new HashSet<>();
		for (String node : graph.keySet()) visit(node, graph, visited, new LinkedHashSet<>());
	}

	private void visit(String node, Map<String, Set<String>> graph, Set<String> visited, LinkedHashSet<String> path) {
		if (path.contains(node)) {
			var cycle = new ArrayList<>(path);
			cycle.add(node);
			throw new AssertionError("Project type dependency cycle: " + String.join(" -> ", cycle));
		}
		if (!visited.add(node)) return;
		path.add(node);
		for (String target : graph.get(node)) visit(target, graph, visited, path);
		path.remove(node);
	}
}
