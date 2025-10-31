import org.json.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

public class ResultsCsvExportTest {

    @Test
    public void generate_csv_summary() throws Exception {
        List<Path> inputs = findResultFiles();
        assertFalse(inputs.isEmpty());
        Path outDir = Files.exists(Paths.get("outputs")) ? Paths.get("outputs") : Paths.get(".");
        Files.createDirectories(outDir);
        Path csv = outDir.resolve("summary.csv");

        String header = String.join(",",
                "file","name","n","m","density","is_dag","graph_type",
                "scc_count","avg_scc_size","max_scc_size","scc_sizes",
                "topo_len_components","topo_len_vertices","topo_order_vertices",
                "src_comp","short_reachable_components","short_path_components","short_path_vertices",
                "long_max_distance","long_path_components","long_path_vertices",
                "scc_time_ns","topo_time_ns","short_time_ns","long_time_ns",
                "scc_dfs_calls","scc_edges_scanned","topo_nodes","topo_edges","topo_pops",
                "short_relax","short_updated","long_relax","long_updated"
        ) + "\n";

        StringBuilder sb = new StringBuilder(header);

        for (Path p : inputs) {
            JSONObject root = new JSONObject(Files.readString(p));
            JSONArray graphs = root.getJSONArray("graphs");
            for (int i = 0; i < graphs.length(); i++) {
                JSONObject g = graphs.getJSONObject(i);
                String name = g.optString("name", "graph_"+i);
                int n = g.getInt("n");
                int m = g.getJSONArray("edges").length();
                boolean directed = g.optBoolean("directed", true);

                JSONObject r = g.getJSONObject("results");

                double density = r.has("summary")
                        ? r.getJSONObject("summary").optDouble("density", computeDensity(directed, n, m))
                        : computeDensity(directed, n, m);
                boolean isDag = r.has("summary")
                        ? r.getJSONObject("summary").optBoolean("is_dag", false)
                        : r.getJSONArray("sccs").length() == n;
                String graphType = r.has("summary")
                        ? r.getJSONObject("summary").optString("graph_type", isDag ? "DAG" : "Cyclic")
                        : (isDag ? "DAG" : "Cyclic");

                JSONArray sccs = r.getJSONArray("sccs");
                int sccCount = sccs.length();
                int maxScc = 0, sumScc = 0;
                for (int k = 0; k < sccs.length(); k++) {
                    int sz = sccs.getJSONArray(k).length();
                    sumScc += sz; if (sz > maxScc) maxScc = sz;
                }
                double avgScc = sccCount == 0 ? 0.0 : (sumScc * 1.0 / sccCount);
                String sccSizesStr = r.has("scc_sizes") ? joinInts(r.getJSONArray("scc_sizes"), "|") : deriveSccSizes(sccs, "|");

                JSONArray topoComp = r.optJSONArray("topo_order_components");
                int topoLenComp = topoComp == null ? 0 : topoComp.length();

                JSONArray topoVerts = r.optJSONArray("topo_order_vertices");
                int topoLenVerts = topoVerts == null ? 0 : topoVerts.length();
                String topoVertsStr = topoVerts == null ? "" : joinInts(topoVerts, ";");

                JSONObject sh = r.optJSONObject("shortest_on_condensation");
                int srcComp = sh == null ? -1 : sh.optInt("source_component", -1);
                JSONArray shDist = sh == null ? null : sh.optJSONArray("dist");
                int shortReach = countNonNull(shDist);
                String spathC = (sh != null && sh.has("path_components")) ? joinInts(sh.getJSONArray("path_components"), "->") : "";
                String spathV = (sh != null && sh.has("path_vertices")) ? joinInts(sh.getJSONArray("path_vertices"), "->") : "";

                JSONObject lg = r.optJSONObject("longest_on_condensation");
                Integer longMax = (lg == null || lg.isNull("max_distance")) ? null : lg.getInt("max_distance");
                String lpathC = (lg != null && lg.has("path_components")) ? joinInts(lg.getJSONArray("path_components"), "->") : "";
                String lpathV = (lg != null && lg.has("path_vertices")) ? joinInts(lg.getJSONArray("path_vertices"), "->") : "";

                JSONObject mscc = r.optJSONObject("metrics") != null ? r.getJSONObject("metrics").optJSONObject("scc") : null;
                JSONObject mtop = r.optJSONObject("metrics") != null ? r.getJSONObject("metrics").optJSONObject("toposort") : null;
                JSONObject mshort = r.optJSONObject("metrics") != null ? r.getJSONObject("metrics").optJSONObject("dag_shortest") : null;
                JSONObject mlong = r.optJSONObject("metrics") != null ? r.getJSONObject("metrics").optJSONObject("dag_longest") : null;

                long sccTime = mscc == null ? -1 : mscc.optLong("time_ns", -1);
                long topoTime = mtop == null ? -1 : mtop.optLong("time_ns", -1);
                long shortTime = mshort == null ? -1 : mshort.optLong("time_ns", -1);
                long longTime = mlong == null ? -1 : mlong.optLong("time_ns", -1);

                int sccDfs = mscc == null ? -1 : mscc.optInt("dfs_calls", -1);
                int sccScan = mscc == null ? -1 : mscc.optInt("edges_scanned", -1);
                int topoNodes = mtop == null ? -1 : mtop.optInt("nodes", -1);
                int topoEdges = mtop == null ? -1 : mtop.optInt("edges", -1);
                int topoPops = mtop == null ? -1 : mtop.optInt("pops", -1);
                int shortRelax = mshort == null ? -1 : mshort.optInt("relaxations", -1);
                int shortUpd = mshort == null ? -1 : mshort.optInt("updated", -1);
                int longRelax = mlong == null ? -1 : mlong.optInt("relaxations", -1);
                int longUpd = mlong == null ? -1 : mlong.optInt("updated", -1);

                sb.append(csv(p.getFileName().toString())).append(",")
                        .append(csv(name)).append(",")
                        .append(n).append(",")
                        .append(m).append(",")
                        .append(formatDouble(density)).append(",")
                        .append(isDag).append(",")
                        .append(csv(graphType)).append(",")
                        .append(sccCount).append(",")
                        .append(formatDouble(avgScc)).append(",")
                        .append(maxScc).append(",")
                        .append(csv(sccSizesStr)).append(",")
                        .append(topoLenComp).append(",")
                        .append(topoLenVerts).append(",")
                        .append(csv(topoVertsStr)).append(",")
                        .append(srcComp).append(",")
                        .append(shortReach).append(",")
                        .append(csv(spathC)).append(",")
                        .append(csv(spathV)).append(",")
                        .append(longMax == null ? "" : longMax).append(",")
                        .append(csv(lpathC)).append(",")
                        .append(csv(lpathV)).append(",")
                        .append(sccTime).append(",")
                        .append(topoTime).append(",")
                        .append(shortTime).append(",")
                        .append(longTime).append(",")
                        .append(sccDfs).append(",")
                        .append(sccScan).append(",")
                        .append(topoNodes).append(",")
                        .append(topoEdges).append(",")
                        .append(topoPops).append(",")
                        .append(shortRelax).append(",")
                        .append(shortUpd).append(",")
                        .append(longRelax).append(",")
                        .append(longUpd)
                        .append("\n");
            }
        }

        Files.writeString(csv, sb.toString());
        assertTrue(Files.exists(csv));
    }

    private static List<Path> findResultFiles() throws Exception {
        List<Path> bases = List.of(
                Paths.get("outputs"),
                Paths.get("src","test","java"),
                Paths.get("src","test","resources"),
                Paths.get(".")
        );
        List<Path> found = new ArrayList<>();
        for (Path b : bases) {
            if (!Files.exists(b)) continue;
            try (Stream<Path> s = Files.walk(b, 4)) {
                s.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith("_results.json"))
                        .forEach(found::add);
            }
        }
        if (found.isEmpty()) {
            try (Stream<Path> s = Files.walk(Paths.get(System.getProperty("user.dir")), 8)) {
                s.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith("_results.json"))
                        .forEach(found::add);
            }
        }
        return found;
    }

    private static String joinInts(JSONArray arr, String sep) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < arr.length(); i++) {
            if (i > 0) b.append(sep);
            Object v = arr.get(i);
            b.append(v == JSONObject.NULL ? "" : String.valueOf(((Number)v).intValue()));
        }
        return b.toString();
    }

    private static String deriveSccSizes(JSONArray sccs, String sep) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < sccs.length(); i++) {
            if (i > 0) b.append(sep);
            b.append(sccs.getJSONArray(i).length());
        }
        return b.toString();
    }

    private static String csv(String s) {
        if (s == null) return "";
        String q = s.replace("\"","\"\"");
        if (q.indexOf(',') >= 0 || q.indexOf('"') >= 0 || q.indexOf('\n') >= 0) return "\""+q+"\"";
        return q;
    }

    private static String formatDouble(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) return "";
        return String.format(java.util.Locale.US, "%.6f", d);
    }

    private static double computeDensity(boolean directed, int n, int m) {
        if (n <= 1) return 0.0;
        double denom = directed ? (double)n*(n-1) : (double)n*(n-1)/2.0;
        return m/denom;
    }

    private static int countNonNull(JSONArray arr) {
        if (arr == null) return 0;
        int c = 0;
        for (int i = 0; i < arr.length(); i++) if (!(arr.get(i) == JSONObject.NULL)) c++;
        return c;
    }
}
