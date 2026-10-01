#!/usr/bin/env python3
"""Second pass: fix PlayerFilter assignments and getPlayer calls."""

import re
from pathlib import Path

path = Path(r"D:\nnn\Spraute_Engine\src\main\java\org\zonarstudio\spraute_engine\script\ScriptExecutor.java")
text = path.read_text(encoding="utf-8")

text = re.sub(
    r"(\s+)(?:task\.)?(wait[A-Za-z]*Players) = sp\.getUUID\(\);",
    r"\1\2 = buildPlayerFilterFromPlayer(sp);",
    text,
)

text = text.replace(
    "getPlayerList().getPlayer(waitPositionPlayers)",
    "findOnlinePlayerForFilter(waitPositionPlayers)",
)
text = text.replace(
    "getPlayerList().getPlayer(task.waitPositionPlayers)",
    "findOnlinePlayerForFilter(task.waitPositionPlayers)",
)
text = text.replace(
    "getPlayerList().getPlayer(waitInventoryPlayers)",
    "findOnlinePlayerForFilter(waitInventoryPlayers)",
)
text = text.replace(
    "getPlayerList().getPlayer(task.waitInventoryPlayers)",
    "findOnlinePlayerForFilter(task.waitInventoryPlayers)",
)

pattern = re.compile(
    r"net\.minecraft\.server\.level\.ServerPlayer sp = resolveServerPlayer\(evaluateExpression\((pNode|args\.get\(0\)|call\.getArgs\(\)\.get\(0\)|\(ScriptNode\) instr\.getArg\(0\))\)\);\s*\n"
    r"(\s+)if \(sp != null\) \{\s*\n"
    r"\2(?:task\.)?(wait[A-Za-z]*Players) = buildPlayerFilterFromPlayer\(sp\);",
    re.MULTILINE,
)

def repl(m):
    expr = m.group(1)
    indent = m.group(2)
    field = m.group(3)
    prefix = "task." if "task." in m.group(0) else ""
    return (
        f"Object playerArg = evaluateExpression({expr});\n"
        f"{indent}PlayerFilter playerFilter = buildPlayerFilter(playerArg);\n"
        f"{indent}if (playerFilter != null) {{\n"
        f"{indent}    {prefix}{field} = playerFilter;"
    )

text = pattern.sub(repl, text)

text = text.replace(
    "if (targetPlayer == null || !player.getUUID().equals(targetPlayer.getUUID())) continue;",
    "if (!matchesEventPlayerArg(handler.eventArgs.get(0), player.getUUID())) continue;",
)
text = text.replace(
    "if (playerEnt == null || !playerEnt.getUUID().equals(player.getUUID())) continue;",
    "if (!matchesEventPlayerArg(handler.eventArgs.get(0), player.getUUID())) continue;",
)
text = text.replace(
    "matched = playerEnt.getUUID().equals(player.getUUID());",
    "matched = matchesEventPlayerArg(arg0, player.getUUID());",
)
text = text.replace(
    "if (playerEnt != null && playerEnt.getUUID().equals(player.getUUID())) {",
    "if (matchesEventPlayerArg(handler.eventArgs.get(0), player.getUUID())) {",
)
text = text.replace(
    "if (targetEntity == null || !player.getUUID().equals(targetEntity.getUUID())) continue;",
    "if (!matchesEventPlayerArg(handler.eventArgs.get(0), player.getUUID())) continue;",
)

path.write_text(text, encoding="utf-8")
print("Second pass done")
