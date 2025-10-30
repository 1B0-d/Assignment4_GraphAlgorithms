package graph.dagsp;

import java.util.*;

public class DAGShortestPaths {
    private int n;
    private List<List<int[]>> adj;

    public DAGShortestPaths(int n) {
        this.n = n;
        adj = new ArrayList<>();
        for (int i = 0; i < n; i++) adj.add(new ArrayList<>());
    }

    public void addEdge(int u, int v, int w) {
        adj.get(u).add(new int[]{v, w});
    }

    public int[] shortestPaths(int src, List<Integer> topo) {
        int INF = 1_000_000_000;
        int[] dist = new int[n];
        Arrays.fill(dist, INF);
        dist[src] = 0;

        for (int u : topo) {
            if (dist[u] != INF) {
                for (int[] e : adj.get(u)) {
                    int v = e[0], w = e[1];
                    dist[v] = Math.min(dist[v], dist[u] + w);
                }
            }
        }
        return dist;
    }

    public int[] longestPaths(int src, List<Integer> topo) {
        int[] dist = new int[n];
        Arrays.fill(dist, Integer.MIN_VALUE);
        dist[src] = 0;

        for (int u : topo) {
            if (dist[u] != Integer.MIN_VALUE) {
                for (int[] e : adj.get(u)) {
                    int v = e[0], w = e[1];
                    dist[v] = Math.max(dist[v], dist[u] + w);
                }
            }
        }
        return dist;
    }
}
