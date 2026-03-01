package com.riskguard.ai;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;

@Component
public class SimilarityStore {

    public static record Snippet(
        String id, String category, String text, String saferWording, String note
    ) {}

    private final EmbeddingService embeddingService;

    private final List<Snippet> corpus = new ArrayList<>();
    private final List<float[]> vectors = new ArrayList<>();

    @Value("${riskguard.ai.similarity.log:true}")
    private boolean similarityLogEnabled; // true: rationale’a ve UI’a log düş

    // 🔧 Varsayılan minimum benzerlik eşiği (konfigüratif)
    @Value("${riskguard.ai.similarity.min:0.55}")
    private double defaultMinSim;

    public SimilarityStore(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    @PostConstruct
    @SuppressWarnings("unchecked")
    public void load() throws Exception {
        try (InputStream in = new ClassPathResource("best-practices.yml").getInputStream()) {
            List<Map<String, Object>> items = new Yaml().load(in);
            if (items != null) {
                for (Map<String, Object> m : items) {
                    String id = String.valueOf(m.getOrDefault("id", UUID.randomUUID().toString()));
                    String cat = (String) m.getOrDefault("category", "OTHER");
                    String txt = (String) m.getOrDefault("text", "");
                    String safer = (String) m.getOrDefault("safer_wording", "");
                    String note = (String) m.getOrDefault("note", "");
                    Snippet s = new Snippet(id, cat, txt, safer, note);
                    corpus.add(s);
                    vectors.add(embeddingService.embed(txt));
                }
            }
            if (similarityLogEnabled) {
                System.out.println("[SimilarityStore] Yüklendi: " + corpus.size() + " snippet, vektör=" + vectors.size());
            }
        }
    }

    public Optional<Match> nearest(String query, int topK, double minSim) throws Exception {
        float[] qv = embeddingService.embed(query);
        double best = -1.0;
        int bestIdx = -1;
        for (int i = 0; i < vectors.size(); i++) {
            double sim = embeddingService.cosine(qv, vectors.get(i));
            if (sim > best) { best = sim; bestIdx = i; }
        }
        if (bestIdx >= 0 && best >= minSim) {
            Snippet s = corpus.get(bestIdx);
            return Optional.of(new Match(s, best));
        }
        return Optional.empty();
    }

    // ✅ EK: En benzer K kaydı döndür (azalan kosinüs benzerliğe göre)
    public List<Match> topK(String query, int k) throws Exception {
        return topK(query, k, null, null);
    }

    // ✅ EK: Kategori filtresi ve minSim ile top-K
    public List<Match> topK(String query, int k, String categoryFilter, Double minSimOpt) throws Exception {
        if (k <= 0) k = 1;
        if (k > 50) k = 50; // güvenli üst sınır

        float[] qv = embeddingService.embed(query);
        double minSim = (minSimOpt != null) ? minSimOpt : defaultMinSim;

        // küçükten büyüğe tutan kuyruk → kapasite aşınca en küçük olanı at
        PriorityQueue<Match> pq = new PriorityQueue<>(Comparator.comparingDouble(Match::similarity));
        for (int i = 0; i < vectors.size(); i++) {
            Snippet s = corpus.get(i);
            if (categoryFilter != null && !categoryFilter.isBlank()) {
                if (!categoryFilter.equalsIgnoreCase(s.category())) {
                    continue;
                }
            }
            double sim = embeddingService.cosine(qv, vectors.get(i));
            if (sim >= minSim) {
                pq.offer(new Match(s, sim));
                if (pq.size() > k) {
                    pq.poll(); // en düşük benzerliği at
                }
            }
        }
        ArrayList<Match> out = new ArrayList<>(pq.size());
        while (!pq.isEmpty()) out.add(0, pq.poll()); // büyükten küçüğe çevir
        return out;
    }

    public record Match(Snippet snippet, double similarity) {}

    // (Opsiyonel) küçük yardımcılar
    public int size() { return corpus.size(); }
    public boolean isEmpty() { return corpus.isEmpty(); }
}
