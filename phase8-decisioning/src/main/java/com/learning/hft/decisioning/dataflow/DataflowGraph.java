package com.learning.hft.decisioning.dataflow;

/**
 * Chapter 8 — a minimal reactive <b>DAG dataflow</b> engine: derived values recompute only when the
 * inputs they depend on change (<b>dirty propagation</b> / incremental computation). Enemy killed:
 * wasted work (recomputing everything on every tick).
 *
 * <p>This is the model behind a streaming quote-<b>skew</b> engine: when a price streams in (or a
 * "skew active" flag flips at a scheduled start time), only the affected downstream nodes recompute,
 * in dependency order. Topological order is computed once at {@link #build()}; the hot path
 * ({@link #recompute()}) walks a flat {@code int[]} order and touches only dirty nodes — zero
 * allocation.
 *
 * <p>Usage: add inputs and nodes, call {@link #build()} once, then {@link #setInput}/{@link #recompute}
 * on the hot path. Not thread-safe (single-writer, Ch.2).
 */
public final class DataflowGraph {

    /** Compute a node's value from the current {@code values} array and this node's {@code deps}. */
    @FunctionalInterface
    public interface NodeFn {
        double compute(double[] values, int[] deps);
    }

    private static final int MAX = 1024;

    private final double[] values = new double[MAX];
    private final boolean[] dirty = new boolean[MAX];
    private final int[][] deps = new int[MAX][];
    private final NodeFn[] fns = new NodeFn[MAX];
    private int count;

    private int[] topo;   // evaluation order, computed at build()
    private boolean built;

    /** Add a source node with an initial value. Returns its id. */
    public int addInput(double initial) {
        int id = count++;
        values[id] = initial;
        deps[id] = new int[0];
        fns[id] = null; // inputs are set externally
        return id;
    }

    /** Add a derived node computed from {@code dependencies}. Returns its id. */
    public int addNode(int[] dependencies, NodeFn fn) {
        int id = count++;
        deps[id] = dependencies;
        fns[id] = fn;
        return id;
    }

    /** Compute the topological evaluation order (Kahn's algorithm) once. */
    public void build() {
        int[] indegree = new int[count];
        for (int id = 0; id < count; id++) {
            indegree[id] = deps[id].length;
        }
        int[] queue = new int[count];
        int head = 0;
        int tail = 0;
        for (int id = 0; id < count; id++) {
            if (indegree[id] == 0) {
                queue[tail++] = id;
            }
        }
        topo = new int[count];
        int ordered = 0;
        while (head < tail) {
            int n = queue[head++];
            topo[ordered++] = n;
            // decrement indegree of nodes that depend on n
            for (int m = 0; m < count; m++) {
                for (int d : deps[m]) {
                    if (d == n && --indegree[m] == 0) {
                        queue[tail++] = m;
                    }
                }
            }
        }
        if (ordered != count) {
            throw new IllegalStateException("cycle detected — dataflow graph must be a DAG");
        }
        // initial full evaluation so all derived values are consistent
        for (int id : topo) {
            if (fns[id] != null) {
                values[id] = fns[id].compute(values, deps[id]);
            }
        }
        built = true;
    }

    /** Set a source value. Marks it dirty only if it actually changed (no-op recompute avoided). */
    public void setInput(int id, double value) {
        checkBuilt();
        if (values[id] != value) {
            values[id] = value;
            dirty[id] = true;
        }
    }

    public double value(int id) {
        return values[id];
    }

    /**
     * Recompute all nodes reachable from currently-dirty inputs, in topological order.
     *
     * @return the number of derived nodes that were recomputed (proves incrementality).
     */
    public int recompute() {
        checkBuilt();
        int recomputed = 0;
        for (int id : topo) {
            boolean need = dirty[id];
            if (!need) {
                for (int d : deps[id]) {
                    if (dirty[d]) {
                        need = true;
                        break;
                    }
                }
            }
            if (need) {
                if (fns[id] != null) {
                    values[id] = fns[id].compute(values, deps[id]);
                    recomputed++;
                }
                dirty[id] = true; // propagate to downstream nodes in this same pass
            }
        }
        // reset dirty flags for the next round
        for (int id = 0; id < count; id++) {
            dirty[id] = false;
        }
        return recomputed;
    }

    private void checkBuilt() {
        if (!built) {
            throw new IllegalStateException("call build() before using the graph");
        }
    }
}
