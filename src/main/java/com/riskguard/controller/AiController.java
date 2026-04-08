package com.riskguard.controller;

import com.riskguard.ai.SuggestionService;
import com.riskguard.ai.SuggestionService.SmartSuggestion;

// 🔹 EK: SimilarityStore ve koleksiyonlar
import com.riskguard.ai.SimilarityStore;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final SuggestionService suggestionService;

    // 🔹 EK: SimilarityStore enjeksiyonu
    private final SimilarityStore similarityStore;

    // 🔄 GÜNCEL: ctor iki bağımlılık alıyor
    public AiController(SuggestionService suggestionService,
                        SimilarityStore similarityStore) {
        this.suggestionService = suggestionService;
        this.similarityStore = similarityStore;
    }

    // ✅ Mevcut DTO'lar (korundu)
    public static record SuggestRequest(String clause) {}
    public static record SuggestResponse(SmartSuggestion suggestion) {}

    @PostMapping("/suggest")
    public SuggestResponse suggest(@RequestBody SuggestRequest req) {
        SmartSuggestion s = suggestionService.suggest(req.clause());
        return new SuggestResponse(s);
    }

    // ==============================
    // 🔎 EK: Top-K benzerlik endpoint’i
    // ==============================

    /**
     * Örnek kullanım:
     * curl -s -H "Content-Type: application/json" \
     *   -d '{"clause":"Taraf, dolaylı ve sonuçsal zararlardan sorumlu olmayacaktır.","k":3}' \
     *   http://localhost:8080/api/v1/ai/similar | jq .
     *
     * İsteğe bağlı alanlar:
     * - k: kaç sonuç (1..10 arası önerilir, default=3)
     * - minSim: minimum cosine benzerlik eşiği (örn. 0.55). Boş ise store’daki varsayılan kullanılır.
     * - category: yalnızca verilen kategori içinden arama (örn. "LIABILITY"). Boş ise tüm korpus.
     */
    @PostMapping("/similar")
    public SimilarResponse similar(@RequestBody SimilarRequest req) throws Exception {
        int k = (req.k() != null && req.k() > 0 && req.k() <= 10) ? req.k() : 3;
        String category = (req.category() != null && !req.category().isBlank()) ? req.category().trim() : null;

        List<SimilarityStore.Match> list = similarityStore.topK(
                req.clause(),
                k,
                category,
                req.minSim() // null ise store default kullanılır
        );

        List<SimilarItem> items = new ArrayList<>(list.size());
        for (var m : list) {
            var sn = m.snippet();
            items.add(new SimilarItem(
                    sn.id(),
                    sn.category(),
                    sn.text(),
                    sn.saferWording(),
                    sn.note(),
                    m.similarity()
            ));
        }
        return new SimilarResponse(items);
    }

    // 🔹 EK: /similar endpoint’i için DTO’lar
    public static record SimilarRequest(
            String clause,
            Integer k,
            Double minSim,
            String category
    ) {}

    public static record SimilarItem(
            String id,
            String category,
            String text,
            String saferWording,
            String note,
            Double similarity
    ) {}

    public static record SimilarResponse(List<SimilarItem> matches) {}
}
