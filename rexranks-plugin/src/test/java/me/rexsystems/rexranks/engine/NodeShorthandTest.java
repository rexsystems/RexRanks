package me.rexsystems.rexranks.engine;

import me.rexsystems.rexranks.api.node.Node;
import me.rexsystems.rexranks.api.node.NodeType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeShorthandTest {
    @Test
    void parsesLuckPermsShorthand() {
        Node prefix = NodeShorthand.parse("prefix.100.&c[Admin] ");
        assertEquals(NodeType.PREFIX, prefix.type());
        assertEquals("100", prefix.key());
        assertEquals("&c[Admin] ", prefix.value());

        Node group = NodeShorthand.parse("group.Admin");
        assertEquals(NodeType.INHERITANCE, group.type());
        assertEquals("admin", group.groupName());

        Node weight = NodeShorthand.parse("weight.50");
        assertEquals(50, weight.priority().orElse(-1));

        Node meta = NodeShorthand.parse("meta.rank.gold");
        assertEquals(NodeType.META, meta.type());
        assertEquals("rank", meta.key());
        assertEquals("gold", meta.value());

        Node denied = NodeShorthand.parse("-essentials.fly");
        assertTrue(denied.negated());
        assertEquals("essentials.fly", denied.key());
    }

    @Test
    void roundTrip() {
        Node original = Node.prefix(25, "<gradient:#ff0000:#ffaa00>[VIP] ");
        String shorthand = NodeShorthand.toShorthand(original);
        Node parsed = NodeShorthand.parse(shorthand);
        assertEquals(original.type(), parsed.type());
        assertEquals(original.key(), parsed.key());
        assertEquals(original.value(), parsed.value());
    }
}
