"""Swift grammar check on Windows. Does not type-check Apple framework calls."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / ".validation"))
from tree_sitter import Language, Parser
import tree_sitter_swift

parser = Parser(Language(tree_sitter_swift.language()))
failures = []
files = sorted((ROOT / "Sources").rglob("*.swift")) + sorted((ROOT / "Tests").rglob("*.swift")) + sorted((ROOT / "ShareExtension").rglob("*.swift")) + sorted((ROOT / "tools/xcode_spm_setup/Sources").rglob("*.swift")) + [ROOT / "Package.swift", ROOT / "tools/xcode_spm_setup/Package.swift"]
for path in files:
    source = path.read_bytes()
    tree = parser.parse(source)
    stack = [tree.root_node]
    while stack:
        node = stack.pop()
        if node.type == "ERROR" or node.is_missing:
            failures.append(f"{path.relative_to(ROOT)}:{node.start_point.row + 1}:{node.start_point.column + 1}: {node.type}: {source[node.start_byte:node.end_byte][:120]!r}")
        stack.extend(reversed(node.children))
if failures:
    print("\n".join(failures)); sys.exit(1)
print(f"Swift syntax grammar passed for {len(files)} files; compiler/type checking still requires Xcode.")
