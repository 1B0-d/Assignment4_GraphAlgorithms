import graph.scc.TarjanSCC;
import graph.topo.TopologicalSort;
import graph.dagsp.DAGShortestPaths;

import java.util.*;

public class Main {
    public static void main(String[] args) {
        int n = 8;
        int[][] edges = {
                {0, 1, 3},
                {1, 2, 2},
                {2, 3, 4},
                {3, 1, 1},
                {4, 5, 2},
                {5, 6, 5},
                {6, 7, 1}
        };
        TarjanSCC tarjan = new TarjanSCC(n);
        for (int[] e : edges) tarjan.addEdge(e[0], e[1]);
        List<List<Integer>> comps = tarjan.run();

        System.out.println("SCC Components:");
        for (List<Integer> c : comps)
            System.out.println(c);

        List<List<Integer>> dag = new ArrayList<>();
        for (int i = 0; i < n; i++) dag.add(new ArrayList<>());
        for (int[] e : edges) dag.get(e[0]).add(e[1]);

        List<Integer> topo = TopologicalSort.kahnSort(n, dag);
        System.out.println("\nTopological Order: " + topo);

        DAGShortestPaths sp = new DAGShortestPaths(n);
        for (int[] e : edges) sp.addEdge(e[0], e[1], e[2]);

        int src = 4;
        int[] dist = sp.shortestPaths(src, topo);
        System.out.println("\nShortest distances from " + src + ": " + Arrays.toString(dist));

        int[] longDist = sp.longestPaths(src, topo);
        System.out.println("Longest distances from " + src + ": " + Arrays.toString(longDist));
    }
}
