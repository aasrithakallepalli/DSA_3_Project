import java.util.*;

/** String algorithms behind the Plagiarism Checker for Short Documents. Text is converted to int word-ids first. */
public class Algorithms {

    /** Rabin-Karp: rolling hash of every k-word window -> hash -> start positions. */
    static Map<Long, List<Integer>> rabinKarp(int[] a, int k) {
        Map<Long, List<Integer>> m = new HashMap<>();
        if (a.length < k) return m;
        final long M = 1_000_000_007L, B = 131;
        long h = 0, pw = 1;
        for (int i = 0; i < k - 1; i++) pw = pw * B % M;
        for (int i = 0; i < a.length; i++) {
            if (i >= k) h = ((h - a[i - k] * pw % M) % M + M) % M; // drop the oldest word
            h = (h * B + a[i]) % M;                                // add the newest word
            if (i >= k - 1) m.computeIfAbsent(h, x -> new ArrayList<>()).add(i - k + 1);
        }
        return m;
    }

    /** KMP: index of first occurrence of p in t, or -1. */
    static int kmp(int[] t, int[] p) {
        int[] f = new int[p.length];
        for (int i = 1, k = 0; i < p.length; i++) {
            while (k > 0 && p[i] != p[k]) k = f[k - 1];
            if (p[i] == p[k]) k++;
            f[i] = k;
        }
        for (int i = 0, k = 0; i < t.length; i++) {
            while (k > 0 && t[i] != p[k]) k = f[k - 1];
            if (t[i] == p[k]) k++;
            if (k == p.length) return i - k + 1;
        }
        return -1;
    }

    /** Z-function: z[i] = length of the longest common prefix of s and s[i..]. */
    static int[] zFunction(int[] s) {
        int n = s.length;
        int[] z = new int[n];
        for (int i = 1, l = 0, r = 0; i < n; i++) {
            if (i < r) z[i] = Math.min(r - i, z[i - l]);
            while (i + z[i] < n && s[z[i]] == s[i + z[i]]) z[i]++;
            if (i + z[i] > r) { l = i; r = i + z[i]; }
        }
        return z;
    }

    /** Does p occur in t? (Z-function on p + separator + t) */
    static boolean zContains(int[] t, int[] p) {
        int[] s = new int[p.length + 1 + t.length];
        System.arraycopy(p, 0, s, 0, p.length);
        s[p.length] = -1;
        System.arraycopy(t, 0, s, p.length + 1, t.length);
        int[] z = zFunction(s);
        for (int i = p.length + 1; i < s.length; i++) if (z[i] >= p.length) return true;
        return false;
    }

    /** Suffix array by prefix doubling, O(n log^2 n). */
    static int[] suffixArray(int[] s) {
        int n = s.length;
        Integer[] sa = new Integer[n];
        int[] r = s.clone(), t = new int[n];
        for (int i = 0; i < n; i++) sa[i] = i;
        for (int k = 1; ; k <<= 1) {
            final int K = k;
            final int[] R = r;
            Comparator<Integer> c = (a, b) -> {
                if (R[a] != R[b]) return Integer.compare(R[a], R[b]);
                int x = a + K < n ? R[a + K] : -1, y = b + K < n ? R[b + K] : -1;
                return Integer.compare(x, y);
            };
            Arrays.sort(sa, c);
            t[sa[0]] = 0;
            for (int i = 1; i < n; i++) t[sa[i]] = t[sa[i - 1]] + (c.compare(sa[i - 1], sa[i]) < 0 ? 1 : 0);
            r = t.clone();
            if (r[sa[n - 1]] == n - 1) break;
        }
        int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = sa[i];
        return out;
    }

    /** Kasai LCP: lcp[i] = common prefix length of suffixes sa[i-1] and sa[i]. */
    static int[] lcp(int[] s, int[] sa) {
        int n = s.length;
        int[] rank = new int[n], l = new int[n];
        for (int i = 0; i < n; i++) rank[sa[i]] = i;
        for (int i = 0, h = 0; i < n; i++) {
            if (rank[i] > 0) {
                int j = sa[rank[i] - 1];
                while (i + h < n && j + h < n && s[i + h] == s[j + h]) h++;
                l[rank[i]] = h;
                if (h > 0) h--;
            } else h = 0;
        }
        return l;
    }

    /** Finds shared passages (>= K words) between a and b. Returns {marksOfA, marksOfB}. */
    static boolean[][] match(int[] a, int[] b, int K) {
        int n = a.length;
        int[] s = new int[n + 1 + b.length + 1];
        System.arraycopy(a, 0, s, 0, n);
        s[n] = 1;                                   // separators never equal a word id (ids >= 3)
        System.arraycopy(b, 0, s, n + 1, b.length);
        s[s.length - 1] = 2;
        int[] sa = suffixArray(s), l = lcp(s, sa);
        boolean[] A = new boolean[n], B = new boolean[b.length];
        for (int i = 1; i < s.length; i++) {
            if (l[i] < K) continue;
            int p = sa[i - 1], q = sa[i];
            if (p > q) { int tmp = p; p = q; q = tmp; }
            if (p < n && q > n)
                for (int j = 0; j < l[i]; j++) { A[p + j] = true; B[q - n - 1 + j] = true; }
        }
        return new boolean[][]{A, B};
    }
}
