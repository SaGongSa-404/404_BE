// Compile first: ./gradlew classes. Reports compiled project-class cycles, not runtime bean cycles.
const { execFileSync } = require('node:child_process');
const output = execFileSync('jdeps', [
  '--ignore-missing-deps', '-verbose:class', '-filter:none', 'build/classes/java/main'
], { encoding: 'utf8', maxBuffer: 16 * 1024 * 1024 });
const graph = new Map();
for (const line of output.split('\n')) {
  const match = line.match(/^\s+(com\.sagongsa\.backend\.[\w.$]+)\s+->\s+(com\.sagongsa\.backend\.[\w.$]+)/);
  if (!match) continue;
  const [from, to] = match.slice(1).map(name => name.split('$')[0]);
  if (!graph.has(from)) graph.set(from, new Set());
  if (!graph.has(to)) graph.set(to, new Set());
  if (from !== to) graph.get(from).add(to);
}
let next = 0;
const indexes = new Map(), low = new Map(), stack = [], active = new Set(), cycles = [];
function visit(node) {
  indexes.set(node, next); low.set(node, next++); stack.push(node); active.add(node);
  for (const target of graph.get(node)) {
    if (!indexes.has(target)) { visit(target); low.set(node, Math.min(low.get(node), low.get(target))); }
    else if (active.has(target)) low.set(node, Math.min(low.get(node), indexes.get(target)));
  }
  if (low.get(node) !== indexes.get(node)) return;
  const component = []; let current;
  do { current = stack.pop(); active.delete(current); component.push(current); } while (current !== node);
  if (component.length > 1) cycles.push(component.sort());
}
for (const node of graph.keys()) if (!indexes.has(node)) visit(node);
console.log(JSON.stringify({
  scope: 'Compiled project types; nested classes collapsed; includes DTO and entity dependencies, not only Spring beans',
  classes: graph.size,
  edges: [...graph.values()].reduce((sum, edges) => sum + edges.size, 0),
  cycles: cycles.sort((a, b) => b.length - a.length || a[0].localeCompare(b[0]))
}, null, 2));
