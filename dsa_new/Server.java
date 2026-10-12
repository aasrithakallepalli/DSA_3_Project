import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Plagiarism Checker for Short Documents - run: javac *.java && java Server  then open http://localhost:8080 */
public class Server {

    // Source index searched by "Web check" (name, site, text)
    static final String[][] SRC = {
        {"Wikipedia", "en.wikipedia.org", "Photosynthesis is the process by which green plants and some bacteria convert light energy into chemical energy. During this process carbon dioxide and water are transformed into glucose and oxygen inside the chloroplasts of the cell."},
        {"Britannica", "britannica.com", "Climate change refers to long term shifts in temperatures and weather patterns. Since the nineteenth century human activities have been the main driver of climate change, primarily due to the burning of fossil fuels like coal, oil and gas."},
        {"Khan Academy", "khanacademy.org", "The water cycle describes how water evaporates from the surface of the earth, rises into the atmosphere, cools and condenses into clouds, and falls again to the surface as precipitation."},
        {"NASA", "climate.nasa.gov", "Global warming is the long term heating of earth's climate system observed since the pre industrial period due to human activities, primarily fossil fuel burning, which increases heat trapping greenhouse gas levels in earth's atmosphere."},
        {"GeeksforGeeks", "geeksforgeeks.org", "Merge sort is a divide and conquer algorithm that divides the input array into two halves, recursively sorts each half, and then merges the two sorted halves to produce the final sorted array in n log n time."},
        {"Medium", "medium.com", "Artificial intelligence is the simulation of human intelligence by machines, especially computer systems. Applications include expert systems, natural language processing, speech recognition and machine vision used across many industries."},
        {"ScienceDirect", "sciencedirect.com", "Renewable energy comes from natural sources that are replenished constantly such as sunlight, wind, rain and geothermal heat, and it offers a sustainable alternative to fossil fuels for electricity generation."},
        {"Coursera", "coursera.org", "Machine learning is a branch of artificial intelligence that enables computers to learn from data and improve their performance on tasks without being explicitly programmed for every step."}
    };

    static class Doc { List<String> raw = new ArrayList<>(); int[] ids; }
    static class Res { String name, url; int pct; }

    public static void main(String[] args) throws Exception {
        HttpServer s = HttpServer.create(new InetSocketAddress(8080), 0);
        s.createContext("/", e -> {
            String p = e.getRequestURI().getPath();
            if (p.equals("/") || p.equals("/index.html")) send(e, 200, "text/html; charset=utf-8", new String(Files.readAllBytes(Paths.get("index.html")), StandardCharsets.UTF_8));
            else send(e, 404, "text/plain", "Not found");
        });
        s.createContext("/api/web", e -> send(e, 200, "application/json; charset=utf-8", web(form(e))));
        s.createContext("/api/compare", e -> send(e, 200, "application/json; charset=utf-8", compare(form(e))));
        s.start();
        System.out.println("Plagiarism Checker running at http://localhost:8080");
    }

    static Doc prep(String t, Map<String, Integer> dict) {
        Doc d = new Doc();
        for (String w : t.trim().split("\\s+")) if (w.matches(".*[A-Za-z0-9].*")) d.raw.add(w);
        d.ids = new int[d.raw.size()];
        for (int i = 0; i < d.ids.length; i++) {
            String key = d.raw.get(i).toLowerCase().replaceAll("[^a-z0-9]", "");
            Integer id = dict.get(key);
            if (id == null) { id = dict.size() + 3; dict.put(key, id); }
            d.ids[i] = id;
        }
        return d;
    }

    static int count(boolean[] m) { int c = 0; for (boolean b : m) if (b) c++; return c; }

    static String web(Map<String, String> f) {
        Map<String, Integer> dict = new HashMap<>();
        Doc d = prep(f.getOrDefault("text", ""), dict);
        int n = d.ids.length;
        if (n < 8) return "{\"error\":\"Please add at least 8 words.\"}";
        Map<Long, List<Integer>> dh = Algorithms.rabinKarp(d.ids, 3);
        boolean[] union = new boolean[n];
        List<Res> res = new ArrayList<>();
        for (String[] s : SRC) {
            Doc x = prep(s[2], dict);
            Map<Long, List<Integer>> sh = Algorithms.rabinKarp(x.ids, 3);
            int ok = 0;
            for (Map.Entry<Long, List<Integer>> en : dh.entrySet()) {          // Rabin-Karp finds candidates
                if (!sh.containsKey(en.getKey())) continue;
                int p = en.getValue().get(0);
                if (Algorithms.kmp(x.ids, Arrays.copyOfRange(d.ids, p, p + 3)) >= 0) ok++; // KMP verifies
            }
            Res r = new Res(); r.name = s[0]; r.url = s[1];
            if (ok > 0) {
                boolean[] a = Algorithms.match(d.ids, x.ids, 4)[0];             // suffix array + LCP
                for (int i = 0; i < n; i++) if (a[i]) union[i] = true;
                r.pct = Math.round(100f * count(a) / n);
            }
            res.add(r);
        }
        res.sort((p, q) -> q.pct - p.pct);
        StringBuilder b = new StringBuilder("{\"total\":" + Math.round(100f * count(union) / n) + ",\"sources\":[");
        for (int i = 0; i < res.size(); i++) {
            if (i > 0) b.append(',');
            b.append("{\"name\":" + q(res.get(i).name) + ",\"url\":" + q(res.get(i).url) + ",\"pct\":" + res.get(i).pct + "}");
        }
        return b.append("],\"words\":").append(words(d.raw)).append(",\"marks\":").append(marks(union)).append('}').toString();
    }

    static String compare(Map<String, String> f) {
        Map<String, Integer> dict = new HashMap<>();
        Doc a = prep(f.getOrDefault("a", ""), dict), b = prep(f.getOrDefault("b", ""), dict);
        if (a.ids.length < 8 || b.ids.length < 8) return "{\"error\":\"Add at least 8 words to both documents.\"}";
        Map<Long, List<Integer>> ha = Algorithms.rabinKarp(a.ids, 3), hb = Algorithms.rabinKarp(b.ids, 3);
        boolean any = false;
        for (Map.Entry<Long, List<Integer>> en : ha.entrySet()) {
            if (!hb.containsKey(en.getKey())) continue;
            int p = en.getValue().get(0);
            if (Algorithms.zContains(b.ids, Arrays.copyOfRange(a.ids, p, p + 3))) { any = true; break; } // Z-function verifies
        }
        boolean[][] m = any ? Algorithms.match(a.ids, b.ids, 4) : new boolean[][]{new boolean[a.ids.length], new boolean[b.ids.length]};
        int ca = count(m[0]), cb = count(m[1]);
        return "{\"overall\":" + Math.round(100f * (ca + cb) / (a.ids.length + b.ids.length))
            + ",\"pa\":" + Math.round(100f * ca / a.ids.length) + ",\"pb\":" + Math.round(100f * cb / b.ids.length)
            + ",\"aw\":" + words(a.raw) + ",\"am\":" + marks(m[0]) + ",\"bw\":" + words(b.raw) + ",\"bm\":" + marks(m[1]) + "}";
    }

    static String words(List<String> w) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < w.size(); i++) { if (i > 0) b.append(','); b.append(q(w.get(i))); }
        return b.append(']').toString();
    }
    static String marks(boolean[] m) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < m.length; i++) { if (i > 0) b.append(','); b.append(m[i] ? 1 : 0); }
        return b.append(']').toString();
    }
    static String q(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c < 32) b.append(' ');
            else b.append(c);
        }
        return b.append('"').toString();
    }

    static Map<String, String> form(HttpExchange e) throws IOException {
        String body = new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> m = new HashMap<>();
        for (String kv : body.split("&")) {
            int i = kv.indexOf('=');
            if (i > 0) m.put(kv.substring(0, i), URLDecoder.decode(kv.substring(i + 1), StandardCharsets.UTF_8));
        }
        return m;
    }

    static void send(HttpExchange e, int code, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        e.getResponseHeaders().set("Content-Type", type);
        e.sendResponseHeaders(code, bytes.length);
        e.getResponseBody().write(bytes);
        e.close();
    }
}
