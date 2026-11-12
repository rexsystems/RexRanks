package me.rexsystems.rexranks.engine;

import me.rexsystems.rexranks.api.context.ImmutableContextSet;
import me.rexsystems.rexranks.api.data.CachedMetaData;
import me.rexsystems.rexranks.api.node.Node;
import me.rexsystems.rexranks.api.query.QueryOptions;
import me.rexsystems.rexranks.api.query.Tristate;
import me.rexsystems.rexranks.model.GroupImpl;
import me.rexsystems.rexranks.model.NodeMapImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionCalculatorTest {
    private final Map<String, GroupImpl> groups = new HashMap<>();
    private PermissionCalculator calculator;

    @BeforeEach
    void setUp() {
        groups.clear();
        calculator = new PermissionCalculator(name -> Optional.ofNullable(groups.get(name.toLowerCase())), "default", true);
    }

    private GroupImpl group(String name, int weight) {
        GroupImpl group = new GroupImpl(name, calculator, QueryOptions::defaultContextual);
        group.setWeight(weight);
        groups.put(name.toLowerCase(), group);
        return group;
    }

    @Test
    void exactNegationBeatsWildcardGrant() {
        NodeMapImpl nodes = new NodeMapImpl();
        nodes.add(Node.permission("essentials.*", true));
        nodes.add(Node.permission("essentials.fly", false));
        assertEquals(Tristate.FALSE, calculator.check(nodes.toSet(), "default", "essentials.fly", QueryOptions.DEFAULT));
        assertEquals(Tristate.TRUE, calculator.check(nodes.toSet(), "default", "essentials.heal", QueryOptions.DEFAULT));
    }

    @Test
    void userNodeBeatsInherited() {
        GroupImpl staff = group("staff", 50);
        staff.data().add(Node.permission("essentials.fly", false));
        NodeMapImpl user = new NodeMapImpl();
        user.add(Node.inheritance("staff"));
        user.add(Node.permission("essentials.fly", true));
        assertEquals(Tristate.TRUE, calculator.check(user.toSet(), "staff", "essentials.fly", QueryOptions.DEFAULT));
    }

    @Test
    void higherWeightWinsBetweenGroups() {
        GroupImpl low = group("member", 10);
        low.data().add(Node.permission("essentials.fly", false));
        GroupImpl high = group("admin", 100);
        high.data().add(Node.permission("essentials.fly", true));
        NodeMapImpl user = new NodeMapImpl();
        user.add(Node.inheritance("member"));
        user.add(Node.inheritance("admin"));
        assertEquals(Tristate.TRUE, calculator.check(user.toSet(), "admin", "essentials.fly", QueryOptions.DEFAULT));
    }

    @Test
    void contextIsRespected() {
        NodeMapImpl nodes = new NodeMapImpl();
        nodes.add(Node.builder(me.rexsystems.rexranks.api.node.NodeType.PERMISSION, "essentials.fly")
                .value("true")
                .contexts(ImmutableContextSet.of("world", "nether"))
                .build());
        QueryOptions nether = QueryOptions.contextual(ImmutableContextSet.of("world", "nether"));
        QueryOptions overworld = QueryOptions.contextual(ImmutableContextSet.of("world", "world"));
        assertEquals(Tristate.TRUE, calculator.check(nodes.toSet(), "default", "essentials.fly", nether));
        assertEquals(Tristate.UNDEFINED, calculator.check(nodes.toSet(), "default", "essentials.fly", overworld));
    }

    @Test
    void expiredNodesAreIgnored() {
        NodeMapImpl nodes = new NodeMapImpl();
        nodes.add(Node.builder(me.rexsystems.rexranks.api.node.NodeType.PERMISSION, "essentials.fly")
                .value("true")
                .expiry(System.currentTimeMillis() - 1000)
                .build());
        assertEquals(Tristate.UNDEFINED, calculator.check(nodes.toSet(), "default", "essentials.fly", QueryOptions.DEFAULT));
    }

    @Test
    void inheritanceCyclesDoNotLoop() {
        GroupImpl a = group("a", 1);
        GroupImpl b = group("b", 1);
        a.data().add(Node.inheritance("b"));
        b.data().add(Node.inheritance("a"));
        a.data().add(Node.permission("test.node"));
        NodeMapImpl user = new NodeMapImpl();
        user.add(Node.inheritance("a"));
        assertEquals(Tristate.TRUE, calculator.check(user.toSet(), "a", "test.node", QueryOptions.DEFAULT));
    }

    @Test
    void defaultGroupIsAutoInheritedForUsers() {
        GroupImpl def = group("default", 0);
        def.data().add(Node.permission("spawn.use"));
        NodeMapImpl user = new NodeMapImpl();
        assertEquals(Tristate.TRUE, calculator.check(user.toSet(), "default", "spawn.use", QueryOptions.DEFAULT));
    }

    @Test
    void prefixUsesHighestPriority() {
        GroupImpl def = group("default", 0);
        def.data().add(Node.prefix(10, "&7[M] "));
        NodeMapImpl user = new NodeMapImpl();
        user.add(Node.prefix(100, "<#ffd36a>[A] "));
        CachedMetaData meta = calculator.metaData(user.toSet(), "default", QueryOptions.DEFAULT);
        assertEquals("<#ffd36a>[A] ", meta.getPrefix());
        assertEquals(2, meta.getPrefixes().size());
    }

    @Test
    void starGrantsEverythingWhenNothingMoreSpecific() {
        NodeMapImpl nodes = new NodeMapImpl();
        nodes.add(Node.permission("*"));
        assertEquals(Tristate.TRUE, calculator.check(nodes.toSet(), "default", "literally.anything", QueryOptions.DEFAULT));
        nodes.add(Node.permission("literally.anything", false));
        assertEquals(Tristate.FALSE, calculator.check(nodes.toSet(), "default", "literally.anything", QueryOptions.DEFAULT));
    }
}
