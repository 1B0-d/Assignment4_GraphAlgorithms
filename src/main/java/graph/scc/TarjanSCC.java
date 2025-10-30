package graph.scc;

import java.util.*;

public class TarjanSCC {
    private int n;
    private List<List<Integer>> adj;
    private int time;
    private int[] index, lowlink;
    private boolean[] onStack;
    private Deque<Integer> stack;
    private List<List<Integer>> components;

    public TarjanSCC(int n) {
        this.n = n;
        adj = new ArrayList<>();
        for (int i = 0; i < n; i++) adj.add(new ArrayList<>());
    }

    public void addEdge(int u, int v) {
        adj.get(u).add(v);
    }

    public List<List<Integer>> run() {
        time = 0;
        index = new int[n];
        lowlink = new int[n];
        onStack = new boolean[n];
        stack = new ArrayDeque<>();
        components = new ArrayList<>();

        Arrays.fill(index, -1);
        for (int v = 0; v < n; v++) {
            if (index[v] == -1)
                dfs(v);
        }
        return components;
    }

    private void dfs(int v) {
        index[v] = lowlink[v] = time++;
        stack.push(v);
        onStack[v] = true;

        for (int to : adj.get(v)) {
            if (index[to] == -1) {
                dfs(to);
                lowlink[v] = Math.min(lowlink[v], lowlink[to]);
            } else if (onStack[to]) {
                lowlink[v] = Math.min(lowlink[v], index[to]);
            }
        }

        if (lowlink[v] == index[v]) {
            List<Integer> comp = new ArrayList<>();
            int w;
            do {
                w = stack.pop();
                onStack[w] = false;
                comp.add(w);
            } while (w != v);
            components.add(comp);
        }
    }
}
