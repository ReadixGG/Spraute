#!/usr/bin/env python3
"""Patch ScriptExecutor.java: UUID wait*PlayerUuid -> PlayerFilter wait*Players."""

from pathlib import Path

path = Path(r"D:\nnn\Spraute_Engine\src\main\java\org\zonarstudio\spraute_engine\script\ScriptExecutor.java")
text = path.read_text(encoding="utf-8")

replacements = [
    ("UUID waitOrbPickupPlayerUuid", "PlayerFilter waitOrbPickupPlayers"),
    ("UUID waitTradePlayerUuid", "PlayerFilter waitTradePlayers"),
    ("UUID waitPositionPlayerUuid", "PlayerFilter waitPositionPlayers"),
    ("UUID waitInventoryPlayerUuid", "PlayerFilter waitInventoryPlayers"),
    ("UUID waitBlockPlayerUuid", "PlayerFilter waitBlockPlayers"),
    ("UUID waitUiOverlapPlayerUuid", "PlayerFilter waitUiOverlapPlayers"),
    ("UUID waitChatPlayerUuid", "PlayerFilter waitChatPlayers"),
    ("UUID waitPlayerActionPlayerUuid", "PlayerFilter waitPlayerActionPlayers"),
    ("UUID waitDimensionPlayerUuid", "PlayerFilter waitDimensionPlayers"),
    ("UUID waitUiPlayerUuid", "PlayerFilter waitUiPlayers"),
    ("waitOrbPickupPlayerUuid", "waitOrbPickupPlayers"),
    ("waitTradePlayerUuid", "waitTradePlayers"),
    ("waitPositionPlayerUuid", "waitPositionPlayers"),
    ("waitInventoryPlayerUuid", "waitInventoryPlayers"),
    ("waitBlockPlayerUuid", "waitBlockPlayers"),
    ("waitUiOverlapPlayerUuid", "waitUiOverlapPlayers"),
    ("waitChatPlayerUuid", "waitChatPlayers"),
    ("waitPlayerActionPlayerUuid", "waitPlayerActionPlayers"),
    ("waitDimensionPlayerUuid", "waitDimensionPlayers"),
    ("waitUiPlayerUuid", "waitUiPlayers"),
]

for old, new in replacements:
    text = text.replace(old, new)

# match patterns
text = text.replace(
    ".waitUiPlayers != null && waitUiPlayers.equals(player.getUUID())",
    ".waitUiPlayers != null && waitUiPlayers.matches(player.getUUID())",
)
text = text.replace(
    "waitUiPlayers != null && waitUiPlayers.equals(player.getUUID())",
    "waitUiPlayers != null && waitUiPlayers.matches(player.getUUID())",
)
text = text.replace(
    "waitDimensionPlayers != null\n                    && waitDimensionPlayers.equals(player.getUUID())",
    "waitDimensionPlayers != null\n                    && waitDimensionPlayers.matches(player.getUUID())",
)
text = text.replace(
    "task.waitDimensionPlayers != null\n                        && task.waitDimensionPlayers.equals(player.getUUID())",
    "task.waitDimensionPlayers != null\n                        && task.waitDimensionPlayers.matches(player.getUUID())",
)
text = text.replace(
    "waitUiOverlapPlayers != null && waitUiOverlapPlayers.equals(player.getUUID())",
    "waitUiOverlapPlayers != null && waitUiOverlapPlayers.matches(player.getUUID())",
)
text = text.replace(
    "t.waitUiOverlapPlayers != null && t.waitUiOverlapPlayers.equals(player.getUUID())",
    "t.waitUiOverlapPlayers != null && t.waitUiOverlapPlayers.matches(player.getUUID())",
)
text = text.replace(
    "waitBlockPlayers != null && waitBlockPlayers.equals(player.getUUID())",
    "waitBlockPlayers != null && waitBlockPlayers.matches(player.getUUID())",
)
text = text.replace(
    "task.waitBlockPlayers != null && task.waitBlockPlayers.equals(player.getUUID())",
    "task.waitBlockPlayers != null && task.waitBlockPlayers.matches(player.getUUID())",
)
text = text.replace(
    "waitChatPlayers != null && waitChatPlayers.equals(player.getUUID())",
    "waitChatPlayers != null && waitChatPlayers.matches(player.getUUID())",
)
text = text.replace(
    "task.waitChatPlayers != null && task.waitChatPlayers.equals(player.getUUID())",
    "task.waitChatPlayers != null && task.waitChatPlayers.matches(player.getUUID())",
)
text = text.replace(
    "waitOrbPickupPlayers != null && waitOrbPickupPlayers.equals(player.getUUID())",
    "waitOrbPickupPlayers != null && waitOrbPickupPlayers.matches(player.getUUID())",
)
text = text.replace(
    "task.waitOrbPickupPlayers != null && task.waitOrbPickupPlayers.equals(player.getUUID())",
    "task.waitOrbPickupPlayers != null && task.waitOrbPickupPlayers.matches(player.getUUID())",
)
text = text.replace(
    "waitTradePlayers != null && waitTradePlayers.equals(player.getUUID())",
    "waitTradePlayers != null && waitTradePlayers.matches(player.getUUID())",
)
text = text.replace(
    "task.waitTradePlayers == null\n                        || !task.waitTradePlayers.equals(player.getUUID())",
    "task.waitTradePlayers == null\n                        || !task.waitTradePlayers.matches(player.getUUID())",
)
text = text.replace(
    "player.getUUID().equals(waitPlayerActionPlayers)",
    "waitPlayerActionPlayers != null && waitPlayerActionPlayers.matches(player.getUUID())",
)
text = text.replace(
    "player.getUUID().equals(task.waitPlayerActionPlayers)",
    "task.waitPlayerActionPlayers != null && task.waitPlayerActionPlayers.matches(player.getUUID())",
)

path.write_text(text, encoding="utf-8")
print("Patched", path)
