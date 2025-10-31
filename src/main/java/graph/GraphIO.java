package graph;

import org.json.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;

public class GraphIO {
    public static class GraphSpec {
        public final String name; public final boolean directed; public final int n;
        public final List<int[]> edges; public final int source; public final String weightModel;
        public GraphSpec(String name, boolean directed, int n, List<int[]> edges, int source, String wm) {
            this.name = name; this.directed = directed; this.n = n;
            this.edges = edges; this.source = source; this.weightModel = wm;
        }
    }

    public static List<GraphSpec> loadGraphs(String path) throws IOException {
        String content = Files.readString(resolve(path));
        JSONObject root = new JSONObject(content);
        JSONArray arr = root.getJSONArray("graphs");
        List<GraphSpec> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject g = arr.getJSONObject(i);
            String name = g.optString("name", "graph_"+i);
            boolean directed = g.optBoolean("directed", true);
            int n = g.getInt("n");
            int source = g.optInt("source", 0);
            String wm = g.optString("weight_model", "edge");
            JSONArray es = g.getJSONArray("edges");
            List<int[]> edges = new ArrayList<>();
            for (int j = 0; j < es.length(); j++) {
                JSONObject e = es.getJSONObject(j);
                edges.add(new int[]{ e.getInt("u"), e.getInt("v"), e.optInt("w", 1)});
            }
            list.add(new GraphSpec(name, directed, n, edges, source, wm));
        }
        return list;
    }

    public static void writeGraphs(String outPath, List<JSONObject> graphs) throws IOException {
        JSONObject root = new JSONObject();
        root.put("graphs", graphs);
        Path p = Path.of(outPath);
        if (p.getParent() != null) Files.createDirectories(p.getParent());
        Files.writeString(p, root.toString(2));
    }

    private static Path resolve(String p) {
        Path[] candidates = new Path[]{
                Path.of(p),
                Path.of("data", p),
                Path.of("src","test","java", p),
                Path.of("src","test","java", Path.of(p).getFileName().toString())
        };
        for (Path c : candidates) if (Files.exists(c)) return c;
        return Path.of(p);
    }
}
