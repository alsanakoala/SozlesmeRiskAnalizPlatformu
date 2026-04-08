package com.riskguard.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * NLP + benzerlik (SimilarityStore) tabanlı öneri üretimi.
 * - minSim altında eşleşme bulunursa "düşük güven" olarak işaretler.
 * - Heuristik kategori ile benzerlikten gelen kategori uyuşmazlığında uyarı verir.
 */
@Service
public class SuggestionService {

    private final SimilarityStore similarityStore;

    @Value("${riskguard.ai.similarity.minSim:0.85}")
    private double minSim; // Eşik (0.85 varsayılan)

    @Value("${riskguard.ai.similarity.log:true}")
    private boolean similarityLogEnabled;

    public SuggestionService(SimilarityStore similarityStore) {
        this.similarityStore = similarityStore;
    }

    // ---- Dış API ----
    public SmartSuggestion suggest(String clause) {
        if (clause == null) clause = "";

        // 1) Heuristik kategori + temel mesajlar
        Heuristic h = analyzeHeuristic(clause);

        // 2) Embedding tabanlı en yakın "best-practice" araması (opsiyonel)
        SimilarityStore.Match match = null;
        try {
            var opt = similarityStore.nearest(clause, 3, 0.0); // minSim=0 burada sadece "bul" demek
            if (opt.isPresent()) {
                match = opt.get();
            }
        } catch (Exception e) {
            // embedding/Model yüklenemese de sistem çalışmaya devam etsin
        }

        String category = h.category; // varsayılan: heuristik kategori
        String rationale = h.rationale;
        List<String> levers = new ArrayList<>(h.levers);
        String saferWording = h.saferWording;

        String matchId = null;
        String matchCategory = null;
        Double similarity = null;
        boolean lowConfidence = false;
        boolean categoryMismatch = false;

        if (match != null) {
            matchId = match.snippet().id();
            matchCategory = match.snippet().category();
            similarity = match.similarity();

            // safer wording/best-practice notlarını varsa üstüne yaz/ekle
            if (match.snippet().saferWording() != null && !match.snippet().saferWording().isBlank()) {
                // Heuristikten gelen varsa üzerine yazmıyoruz; ekleme yapıyoruz:
                if (saferWording == null || saferWording.isBlank()) {
                    saferWording = match.snippet().saferWording();
                } else {
                    saferWording = saferWording + " " + match.snippet().saferWording();
                }
            }
            if (match.snippet().note() != null && !match.snippet().note().isBlank()) {
                levers.add(match.snippet().note());
            }

            // 3) Eşik kontrolü
            if (similarity < minSim) {
                lowConfidence = true; // düşük güven rozetine zemin
                // minSim altında kategori değiştirmiyoruz (heuristik kalsın)
                // sadece rasyonale bilgi notu düşelim:
                rationale = appendNote(rationale,
                        String.format("Benzerlik eşiğin altında (cosine≈%.2f < %.2f). Heuristik kategori korunuyor.", similarity, minSim));
            } else {
                // minSim üstünde: kategoriyi matchCategory ile hizalayabiliriz
                // ancak uyuşmazlık varsa uyarı verelim:
                if (matchCategory != null && !matchCategory.isBlank() && !matchCategory.equalsIgnoreCase(h.category)) {
                    categoryMismatch = true;
                    // kategoriyi heuristikte bırakıp uyarı eklemek genelde daha güvenli
                    rationale = appendNote(rationale,
                            "AI uyarısı: Benzerlikten gelen kategori ile heuristik kategori uyuşmuyor. Dikkatle değerlendirin.");
                    // İstersen burada category = matchCategory; diyerek AI kategorisine geçebilirsin.
                }
            }
        }

        // Görsel/rozet tarafına yardımcı olması için levers içine küçük ipuçları ekleyelim:
        if (lowConfidence) {
            levers.add("Eşik altı benzerlik: metni daraltıp daha net hale getirerek yeniden deneyin.");
        }
        if (categoryMismatch) {
            levers.add("Kategori uyuşmazlığı: ilgili maddeyi elle gözden geçirin, kural etiketini teyit edin.");
        }

        return new SmartSuggestion(
                category,
                rationale,
                List.copyOf(levers),
                saferWording,
                matchId,
                matchCategory,
                similarity,
                lowConfidence,
                categoryMismatch
        );
    }

    private String appendNote(String base, String extra) {
        if (base == null || base.isBlank()) return extra;
        return base + " " + extra;
    }

    // ---- Basit heuristik: metne göre kabaca kategori seçimi ve mesajlar ----
    private Heuristic analyzeHeuristic(String clause) {
        String text = clause.toLowerCase();
        String category = "OTHER";
        String rationale = "Madde kategorize edilemedi; manuel gözden geçirme önerilir.";
        String safer = "";
        List<String> levers = new ArrayList<>();

        if (containsAny(text, "indemnity", "tazmin", "tazminat", "hold harmless")) {
            category = "INDEMNITY";
            rationale = "Geniş tazminat yükümlülüğü izlenimi veriyor; karşı tarafın kapsamı ve sınırları netleştirilmeli.";
            safer = "Tarafların tazmin sorumluluğu kusur oranı ve makul zarar kalemleriyle sınırlandırılır.";
            levers.add("İstisnaları (dolaylı/sonuçsal zararlar) net tanımlayın.");
        } else if (containsAny(text, "limitation of liability", "sorumluluk", "limitsiz", "no cap")) {
            category = "LIMITATION_OF_LIABILITY";
            rationale = "Sorumluluk sınırı belirsiz veya limitsiz görünüyor; ticari risk yüksek olabilir.";
            safer = "Genel sorumluluk, sözleşme bedeli veya makul bir tavanla sınırlandırılır.";
            levers.add("Doğrudan/dolaylı zarar ayrımını net yazın.");
        } else if (containsAny(text, "terminate", "fesih", "convenience")) {
            category = "TERMINATION";
            rationale = "Tek taraflı veya kolay fesih hakkı izlenimi var; gelir sürekliliği etkilenebilir.";
            safer = "Fesih hakları karşılıklılık, makul bildirim süresi ve sebeplilikle dengelenir.";
            levers.add("Bildirim süresi ve telafi mekanizmasını ekleyin.");
        } else if (containsAny(text, "insurance", "sigorta", "poliçe")) {
            category = "INSURANCE_OBLIGATION";
            rationale = "Yüksek veya tek tarafı zorlayan sigorta yükümlülüğü olabilir.";
            safer = "Poliçe limitleri makul seviyede ve karşılıklıdır.";
            levers.add("Sigorta teminat kapsamını ve istisnaları maddeleştirin.");
        } else if (containsAny(text, "governing law", "yürürlük", "yetkili mahkeme", "hukuk")) {
            category = "GOVERNING_LAW";
            rationale = "Yabancı hukuk/yetki riski olabilir; uygulanacak hukuk ve yetki dengeli seçilmeli.";
            safer = "Taraf merkezleri ve ifa yerleri dikkate alınarak nötr bir yetki belirlenir.";
            levers.add("Tahkim/mahkeme seçimini iş modeline göre tartın.");
        } else if (containsAny(text, "confidential", "gizli", "veri", "kvkk", "gdpr")) {
            category = "DATA_PROTECTION";
            rationale = "Gizlilik/KVKK yükümlülükleri olabilir; uyumsuzluk riski var.";
            safer = "Kişisel veri işleme şartları ve amaç sınırlaması açıkça yazılır.";
            levers.add("İhlal bildirimi ve sızma yönetimini ekleyin.");
        }

        return new Heuristic(category, rationale, levers, safer);
    }

    private boolean containsAny(String text, String... kws) {
        for (String k : kws) {
            if (text.contains(k)) return true;
        }
        return false;
    }

    // ---- Basit taşıyıcılar ----
    private static class Heuristic {
        final String category;
        final String rationale;
        final List<String> levers;
        final String saferWording;

        Heuristic(String category, String rationale, List<String> levers, String saferWording) {
            this.category = category;
            this.rationale = rationale;
            this.levers = levers;
            this.saferWording = saferWording;
        }
    }

    // UI/Renderer tarafından kullanılan DTO (record)
    public static record SmartSuggestion(
            String category,
            String rationale,
            List<String> levers,
            String saferWording,
            String matchId,
            String matchCategory,
            Double similarity,
            boolean lowConfidence,
            boolean categoryMismatch
    ) {}
}
