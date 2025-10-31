import org.json.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

public class BatchJsonTests {
    @Test
    public void process_small_medium_large() throws Exception {
        runOne("small.json");
        runOne("medium.json");
        runOne("large.json");
    }

    private void runOne(String inName) throws Exception {
        Path inPath = resolve(inName);
        List<GraphSpec> graphs = loadGraphs(inPath);
        List<JSONObject> outGraphs = new ArrayList<>();
        for (GraphSpec g : graphs) outGraphs.add(processGraph(g));
        String base = inPath.getFileName().toString().replace(".json", "");
        Path out = (inPath.getParent() != null) ? inPath.getParent().resolve(base + "_results.json")
                : Paths.get("outputs", base + "_results.json");
        writeGraphs(out, outGraphs);
        assertTrue(Files.exists(out));
        JSONObject root = new JSONObject(Files.readString(out));
        assertEquals(graphs.size(), root.getJSONArray("graphs").length());
    }

    private JSONObject processGraph(GraphSpec g) {
        long t0 = System.nanoTime();
        TarjanSCC tarjan = new TarjanSCC(g.n);
        for (int[] e : g.edges) tarjan.addEdge(e[0], e[1]);
        List<List<Integer>> sccs = tarjan.run();
        long t1 = System.nanoTime();

        int C = sccs.size();
        int[] compOf = new int[g.n];
        for (int cid = 0; cid < C; cid++) for (int v : sccs.get(cid)) compOf[v] = cid;

        Map<Integer, Map<Integer,Integer>> mm = new HashMap<>();
        for (int i = 0; i < C; i++) mm.put(i, new HashMap<>());
        for (int[] e : g.edges) {
            int cu = compOf[e[0]], cv = compOf[e[1]], w = e[2];
            if (cu != cv) mm.get(cu).merge(cv, w, Math::min);
        }
        List<List<Integer>> dag = new ArrayList<>();
        List<List<int[]>> dagW = new ArrayList<>();
        for (int i = 0; i < C; i++) { dag.add(new ArrayList<>()); dagW.add(new ArrayList<>()); }
        int dagM = 0;
        for (int u = 0; u < C; u++) for (var ent : mm.get(u).entrySet()) {
            dag.get(u).add(ent.getKey());
            dagW.get(u).add(new int[]{ent.getKey(), ent.getValue()});
            dagM++;
        }

        long t2s = System.nanoTime();
        TopoRes topoRes = topoKahn(dag);
        long t2e = System.nanoTime();

        int srcComp = compOf[Math.max(0, Math.min(g.source, g.n - 1))];

        long t3s = System.nanoTime();
        SPRes sp = shortestOnDag(dagW, topoRes.order, srcComp);
        long t3e = System.nanoTime();

        long t4s = System.nanoTime();
        SPRes lp = longestOnDag(dagW, topoRes.order, srcComp);
        long t4e = System.nanoTime();

        List<Integer> longCompPath = restorePath(lp.parent, lp.bestEnd);
        List<Integer> shortCompPath = restorePath(sp.parent, sp.bestEnd);

        List<Integer> topoVertices = new ArrayList<>();
        for (int c : topoRes.order) {
            List<Integer> comp = new ArrayList<>(sccs.get(c));
            Collections.sort(comp);
            topoVertices.addAll(comp);
        }

        int[] distVShort = new int[g.n];
        Integer[] distVShortBox = new Integer[g.n];
        for (int v = 0; v < g.n; v++) {
            int d = sp.dist[compOf[v]];
            distVShort[v] = d;
            distVShortBox[v] = (d >= INF) ? null : d;
        }

        Integer[] distVLongBox = new Integer[g.n];
        for (int v = 0; v < g.n; v++) {
            int d = lp.dist[compOf[v]];
            distVLongBox[v] = (d == MINF) ? null : d;
        }

        List<Integer> shortVertexPath = componentPathToVertexPath(shortCompPath, compOf, g.source, g.edges);
        List<Integer> longVertexPath = componentPathToVertexPath(longCompPath, compOf, g.source, g.edges);

        JSONObject J = new JSONObject();
        J.put("name", g.name);
        J.put("directed", g.directed);
        J.put("n", g.n);
        J.put("source", g.source);
        J.put("weight_model", g.weightModel);

        JSONArray E = new JSONArray();
        for (int[] e : g.edges) E.put(new JSONObject().put("u", e[0]).put("v", e[1]).put("w", e[2]));
        J.put("edges", E);

        JSONObject R = new JSONObject();

        JSONArray sccArr = new JSONArray();
        JSONArray sccSizes = new JSONArray();
        for (var comp : sccs) {
            JSONArray a = new JSONArray();
            for (int v : comp) a.put(v);
            sccArr.put(a);
            sccSizes.put(comp.size());
        }
        R.put("sccs", sccArr);
        R.put("scc_sizes", sccSizes);

        JSONArray compOfArr = new JSONArray();
        for (int v = 0; v < g.n; v++) compOfArr.put(compOf[v]);
        R.put("comp_of", compOfArr);

        JSONArray dagEdges = new JSONArray();
        for (int u = 0; u < C; u++)
            for (int[] e : dagW.get(u))
                dagEdges.put(new JSONObject().put("from", u).put("to", e[0]).put("w", e[1]));
        R.put("condensation_nodes", C);
        R.put("condensation_edges", dagEdges);

        JSONArray topoComp = new JSONArray();
        for (int t : topoRes.order) topoComp.put(t);
        R.put("topo_order_components", topoComp);

        JSONArray topoVerts = new JSONArray();
        for (int v : topoVertices) topoVerts.put(v);
        R.put("topo_order_vertices", topoVerts);

        JSONObject SJ = new JSONObject();
        SJ.put("source_component", srcComp);
        JSONArray Sd = new JSONArray();
        for (int d : sp.dist) Sd.put(d >= INF ? JSONObject.NULL : d);
        SJ.put("dist", Sd);
        JSONArray SpathC = new JSONArray();
        for (int v : shortCompPath) SpathC.put(v);
        SJ.put("path_components", SpathC);
        JSONArray SpathV = new JSONArray();
        for (int v : shortVertexPath) SpathV.put(v);
        SJ.put("path_vertices", SpathV);
        R.put("shortest_on_condensation", SJ);

        JSONObject LJ = new JSONObject();
        LJ.put("source_component", srcComp);
        JSONArray Ld = new JSONArray();
        for (int d : lp.dist) Ld.put(d == MINF ? JSONObject.NULL : d);
        LJ.put("dist", Ld);
        LJ.put("max_distance", lp.bestVal == MINF ? JSONObject.NULL : lp.bestVal);
        JSONArray LpathC = new JSONArray();
        for (int v : longCompPath) LpathC.put(v);
        LJ.put("path_components", LpathC);
        JSONArray LpathV = new JSONArray();
        for (int v : longVertexPath) LpathV.put(v);
        LJ.put("path_vertices", LpathV);
        R.put("longest_on_condensation", LJ);

        JSONObject SV = new JSONObject();
        JSONArray SVd = new JSONArray();
        for (Integer d : distVShortBox) SVd.put(d == null ? JSONObject.NULL : d);
        SV.put("dist", SVd);
        JSONArray SVpath = new JSONArray();
        for (int v : shortVertexPath) SVpath.put(v);
        SV.put("path_vertices", SVpath);
        R.put("shortest_on_vertices", SV);

        JSONObject LV = new JSONObject();
        JSONArray LVd = new JSONArray();
        for (Integer d : distVLongBox) LVd.put(d == null ? JSONObject.NULL : d);
        LV.put("dist", LVd);
        JSONArray LVpath = new JSONArray();
        for (int v : longVertexPath) LVpath.put(v);
        LV.put("path_vertices", LVpath);
        LV.put("max_distance", lp.bestVal == MINF ? JSONObject.NULL : lp.bestVal);
        R.put("longest_on_vertices", LV);

        JSONObject metrics = new JSONObject();
        JSONObject mSCC = new JSONObject();
        mSCC.put("time_ns", t1 - t0);
        mSCC.put("dfs_calls", g.n);
        mSCC.put("edges_scanned", g.edges.size());
        metrics.put("scc", mSCC);

        JSONObject mTopo = new JSONObject();
        mTopo.put("time_ns", t2e - t2s);
        mTopo.put("nodes", C);
        mTopo.put("edges", dagM);
        mTopo.put("pops", topoRes.order.size());
        metrics.put("toposort", mTopo);

        JSONObject mSP = new JSONObject();
        mSP.put("time_ns", t3e - t3s);
        mSP.put("relaxations", sp.relaxations);
        mSP.put("updated", sp.updated);
        metrics.put("dag_shortest", mSP);

        JSONObject mLP = new JSONObject();
        mLP.put("time_ns", t4e - t4s);
        mLP.put("relaxations", lp.relaxations);
        mLP.put("updated", lp.updated);
        metrics.put("dag_longest", mLP);

        JSONObject summary = new JSONObject();
        summary.put("n", g.n);
        summary.put("m", g.edges.size());
        double dens = g.directed ? (g.edges.size() / Math.max(1.0, (double) g.n * (g.n - 1))) :
                (g.edges.size() / Math.max(1.0, (double) g.n * (g.n - 1) / 2.0));
        summary.put("density", dens);
        summary.put("is_dag", C == g.n);
        summary.put("graph_type", C == g.n ? "DAG" : "Cyclic");
        R.put("metrics", metrics);
        R.put("summary", summary);

        J.put("results", R);
        return J;
    }

    private static class GraphSpec {
        final String name; final boolean directed; final int n; final List<int[]> edges; final int source; final String weightModel;
        GraphSpec(String name, boolean directed, int n, List<int[]> edges, int source, String wm) {
            this.name = name; this.directed = directed; this.n = n; this.edges = edges; this.source = source; this.weightModel = wm;
        }
    }

    private static class TopoRes { List<Integer> order; int indegSum; }
    private static class SPRes { int[] dist; int[] parent; int bestEnd; int bestVal; int relaxations; int updated; }

    private static final int INF = 1_000_000_000;
    private static final int MINF = Integer.MIN_VALUE;

    private static TopoRes topoKahn(List<List<Integer>> dag) {
        int n = dag.size();
        int[] indeg = new int[n];
        for (int u = 0; u < n; u++) for (int v : dag.get(u)) indeg[v]++;
        ArrayDeque<Integer> q = new ArrayDeque<>();
        for (int i = 0; i < n; i++) if (indeg[i] == 0) q.add(i);
        List<Integer> ord = new ArrayList<>();
        while (!q.isEmpty()) {
            int u = q.poll();
            ord.add(u);
            for (int v : dag.get(u)) if (--indeg[v] == 0) q.add(v);
        }
        TopoRes r = new TopoRes();
        r.order = ord;
        int s = 0; for (int x : indeg) s += Math.max(0, x);
        r.indegSum = s;
        return r;
    }

    private static SPRes shortestOnDag(List<List<int[]>> dagW, List<Integer> topo, int src) {
        int n = dagW.size();
        int[] d = new int[n]; Arrays.fill(d, INF); d[src] = 0;
        int[] p = new int[n]; Arrays.fill(p, -1);
        int relax = 0, upd = 0;
        for (int u : topo) {
            if (d[u] >= INF) continue;
            for (int[] e : dagW.get(u)) {
                int v = e[0], w = e[1]; relax++;
                if (d[v] > d[u] + w) { d[v] = d[u] + w; p[v] = u; upd++; }
            }
        }
        int bestEnd = -1, bestVal = INF;
        for (int i = 0; i < n; i++) if (d[i] < bestVal) { bestVal = d[i]; bestEnd = i; }
        SPRes r = new SPRes(); r.dist = d; r.parent = p; r.bestEnd = bestEnd; r.bestVal = bestVal; r.relaxations = relax; r.updated = upd; return r;
    }

    private static SPRes longestOnDag(List<List<int[]>> dagW, List<Integer> topo, int src) {
        int n = dagW.size();
        int[] d = new int[n]; Arrays.fill(d, MINF); d[src] = 0;
        int[] p = new int[n]; Arrays.fill(p, -1);
        int relax = 0, upd = 0;
        for (int u : topo) {
            if (d[u] == MINF) continue;
            for (int[] e : dagW.get(u)) {
                int v = e[0], w = e[1]; relax++;
                if (d[v] < d[u] + w) { d[v] = d[u] + w; p[v] = u; upd++; }
            }
        }
        int bestEnd = -1, bestVal = MINF;
        for (int i = 0; i < n; i++) if (d[i] > bestVal) { bestVal = d[i]; bestEnd = i; }
        SPRes r = new SPRes(); r.dist = d; r.parent = p; r.bestEnd = bestEnd; r.bestVal = bestVal; r.relaxations = relax; r.updated = upd; return r;
    }

    private static List<Integer> restorePath(int[] parent, int end) {
        if (end < 0) return List.of();
        ArrayDeque<Integer> st = new ArrayDeque<>();
        for (int v = end; v != -1; v = parent[v]) st.push(v);
        return new ArrayList<>(st);
    }

    private static List<Integer> componentPathToVertexPath(List<Integer> compPath, int[] compOf, int src, List<int[]> edges) {
        if (compPath.isEmpty()) return List.of();
        List<Integer> out = new ArrayList<>();
        int cur = src;
        out.add(cur);
        for (int i = 0; i + 1 < compPath.size(); i++) {
            int c1 = compPath.get(i), c2 = compPath.get(i + 1);
            int bestU = -1, bestV = -1;
            for (int[] e : edges) if (compOf[e[0]] == c1 && compOf[e[1]] == c2) {
                if (bestU == -1 || e[0] < bestU || (e[0] == bestU && e[1] < bestV)) { bestU = e[0]; bestV = e[1]; }
            }
            if (bestU == -1) continue;
            if (out.isEmpty() || out.get(out.size() - 1) != bestU) out.add(bestU);
            out.add(bestV);
            cur = bestV;
        }
        return out;
    }

    private static List<GraphSpec> loadGraphs(Path path) throws java.io.IOException {
        String content = Files.readString(path);
        JSONObject root = new JSONObject(content);
        JSONArray arr = root.getJSONArray("graphs");
        List<GraphSpec> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject g = arr.getJSONObject(i);
            String name = g.optString("name", "graph_" + i);
            boolean directed = g.optBoolean("directed", true);
            int n = g.getInt("n");
            int source = g.optInt("source", 0);
            String wm = g.optString("weight_model", "edge");
            JSONArray es = g.getJSONArray("edges");
            List<int[]> edges = new ArrayList<>();
            for (int j = 0; j < es.length(); j++) {
                JSONObject e = es.getJSONObject(j);
                edges.add(new int[]{ e.getInt("u"), e.getInt("v"), e.optInt("w", 1) });
            }
            list.add(new GraphSpec(name, directed, n, edges, source, wm));
        }
        return list;
    }

    private static void writeGraphs(Path out, List<JSONObject> graphs) throws java.io.IOException {
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        JSONObject root = new JSONObject().put("graphs", graphs);
        Files.writeString(out, root.toString(2));
    }

    private static Path resolve(String name) throws java.io.IOException {
        List<Path> candidates = List.of(
                Paths.get(name),
                Paths.get("src","test","java", name),
                Paths.get("src","test","resources", name),
                Paths.get("data", name)
        );
        for (Path p : candidates) if (Files.exists(p)) return p;
        Path start = Paths.get(System.getProperty("user.dir"));
        try (Stream<Path> s = Files.walk(start, 8)) {
            Optional<Path> found = s.filter(p -> p.getFileName().toString().equals(name)).findFirst();
            if (found.isPresent()) return found.get();
        }
        throw new java.nio.file.NoSuchFileException(name);
    }
}

class TarjanSCC {
    final int n;
    final List<List<Integer>> g;
    int time = 0;
    int[] disc, low;
    boolean[] inStack;
    Deque<Integer> st = new ArrayDeque<>();
    List<List<Integer>> comps = new ArrayList<>();
    TarjanSCC(int n) { this.n = n; g = new ArrayList<>(); for (int i = 0; i < n; i++) g.add(new ArrayList<>()); }
    void addEdge(int u, int v) { g.get(u).add(v); }
    List<List<Integer>> run() {
        disc = new int[n]; low = new int[n]; Arrays.fill(disc, -1); inStack = new boolean[n];
        for (int i = 0; i < n; i++) if (disc[i] == -1) dfs(i);
        return comps;
    }
    void dfs(int u) {
        disc[u] = low[u] = time++;
        st.push(u); inStack[u] = true;
        for (int v : g.get(u)) {
            if (disc[v] == -1) { dfs(v); low[u] = Math.min(low[u], low[v]); }
            else if (inStack[v]) low[u] = Math.min(low[u], disc[v]);
        }
        if (low[u] == disc[u]) {
            List<Integer> comp = new ArrayList<>();
            while (true) {
                int x = st.pop(); inStack[x] = false; comp.add(x);
                if (x == u) break;
            }
            comps.add(comp);
        }
    }
}
