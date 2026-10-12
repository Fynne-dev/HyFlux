package dev.hyflux.api.node;

/** Creates node instances. Called once per placed block, and once per saved node on world load. */
@FunctionalInterface
public interface NodeFactory {

    EnergyNode create(NodeSpawnContext context);
}
