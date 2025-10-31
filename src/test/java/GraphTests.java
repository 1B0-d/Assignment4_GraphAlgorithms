import graph.scc.TarjanSCC;
import graph.topo.TopologicalSort;
import graph.dagsp.DAGShortestPaths;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

public class GraphTests {

    @Test
    public void testSCC() {
        TarjanSCC scc = new TarjanSCC(8);
        int[][] edges = {
                {0, 1}, {1, 2}, {2, 3}, {3, 1},
                {4, 5}, {5, 6}, {6, 7}
        };
        for (int[] e : edges) scc.addEdge(e[0], e[1]);

        List<List<Integer>> comps = scc.run();
        assertEquals(6, comps.size()); // 1 большой цикл (1,2,3) + 5 одиночных

        boolean foundCycle = comps.stream().anyMatch(c -> c.containsAll(List.of(1, 2, 3)));
        assertTrue(foundCycle, "SCC containing 1,2,3 should exist");
    }

    @Test
    public void testTopoSort() {
        int n = 4;
        List<List<Integer>> adj = new ArrayList<>();
        for (int i = 0; i < n; i++) adj.add(new ArrayList<>());
        adj.get(0).add(1);
        adj.get(1).add(2);
        adj.get(2).add(3);

        List<Integer> topo = TopologicalSort.kahnSort(n, adj);
        assertEquals(List.of(0,1,2,3), topo);
    }

    @Test
    public void testShortestPaths() {
        int n = 8;
        DAGShortestPaths sp = new DAGShortestPaths(n);
        int[][] edges = {
                {4, 5, 2}, {5, 6, 5}, {6, 7, 1}
        };
        for (int[] e : edges) sp.addEdge(e[0], e[1], e[2]);

        // topo order for this subgraph
        List<Integer> topo = List.of(4,5,6,7);
        int[] dist = sp.shortestPaths(4, topo);
        assertEquals(0, dist[4]);
        assertEquals(2, dist[5]);
        assertEquals(7, dist[6]);
        assertEquals(8, dist[7]);
    }
}
