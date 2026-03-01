package com.riskguard.service;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.*;

@Service
public class ClauseSegmentationService {

    // Türkçe ve İngilizce sözleşmelerde sık görülen madde başlık kalıpları
    // Örn:
    //  MADDE 1. TARAFLAR
    //  ARTICLE 2 - TERM
    //  SECTION 5 CONFIDENTIALITY
    //  BÖLÜM 3 GİZLİLİK
    //
    // (?im):
    //  i -> case-insensitive
    //  m -> multiline (^ ve $ her satır başı/sonu)
    //
    // Grup yapısı:
    //   ^\s*(MADDE|ARTICLE|SECTION|BÖLÜM|CLAUSE)?\s*\d+   => "MADDE 5", "ARTICLE 12", "SECTION 3" vs.
    //   [^\\n]*                                          => başlık satırının devamı (ör: " - CONFIDENTIALITY")
    //
    private static final Pattern HEADER_PATTERN = Pattern.compile(
        "(?im)^(\\s*(MADDE|ARTICLE|SECTION|BÖLÜM|CLAUSE)?\\s*\\d+[^\\n]*)$"
    );

    /**
     * ESKİ METOT:
     * Sadece clause metinlerini döndürür. (Legacy kullanım için bırakıyoruz.)
     */
    public List<String> splitIntoClauses(String fullText) {
        List<String> clauses = new ArrayList<>();

        if (fullText == null || fullText.isBlank()) {
            return clauses;
        }

        // Satır sonlarını normalize et
        String text = fullText.replaceAll("\\r", "");

        Matcher m = HEADER_PATTERN.matcher(text);
        List<Integer> starts = new ArrayList<>();

        while (m.find()) {
            starts.add(m.start());
        }

        // Hiç başlık bulunamazsa fallback:
        if (starts.isEmpty()) {
            String[] chunks = text.split("(\\n\\s*\\n)+");
            for (String c : chunks) {
                String trimmed = c.trim();
                if (!trimmed.isBlank()) {
                    clauses.add(trimmed);
                }
            }
            return clauses;
        }

        // sonu da ekle
        starts.add(text.length());

        for (int i = 0; i < starts.size() - 1; i++) {
            int from = starts.get(i);
            int to = starts.get(i + 1);
            String block = text.substring(from, to).trim();
            if (!block.isBlank()) {
                clauses.add(block);
            }
        }

        return clauses;
    }

    /**
     * YENİ METOT:
     * Her clause için:
     *   - text içeriği
     *   - orijinal metindeki başlangıç index'i (spanStart)
     *   - bitiş index'i (spanEnd)
     *
     * Bunu DB'ye ClauseEntity.spanStart / spanEnd olarak yazacağız.
     */
    public List<ClauseSegment> splitIntoClausesWithOffsets(String fullText) {
        List<ClauseSegment> segments = new ArrayList<>();

        if (fullText == null || fullText.isBlank()) {
            return segments;
        }

        // Satır sonlarını normalize et (aynı şey yukarıdaki metotla tutarlı olsun diye)
        String text = fullText.replaceAll("\\r", "");

        Matcher m = HEADER_PATTERN.matcher(text);
        List<Integer> starts = new ArrayList<>();

        while (m.find()) {
            starts.add(m.start());
        }

        // Hiç başlık bulunamazsa fallback:
        // - Tüm belgeyi tek blok olarak kabul et
        // - start = 0, end = text.length()
        if (starts.isEmpty()) {
            String trimmedWhole = text.trim();
            segments.add(new ClauseSegment(trimmedWhole, 0, text.length()));
            return segments;
        }

        // header'ların başladığı indexlerin yanına metin sonunu da koy
        starts.add(text.length());

        for (int i = 0; i < starts.size() - 1; i++) {
            int from = starts.get(i);
            int to = starts.get(i + 1);

            // substring ile blok metni çekiyoruz
            String block = text.substring(from, to).trim();
            if (!block.isBlank()) {
                // Önemli not:
                // burada biz trim() yaptığımız için block'ın başına/sonuna gelen boşlukları kırpıyoruz.
                // spanStart/spanEnd ise orijinal text bazlı from/to.
                // Bu bizim için kabul edilebilir çünkü highlight/geri gösterme için
                // asıl önemli olan yaklaşık aralık. Daha hassas istersen ileride
                // trim() öncesi offset düzeltmesi yaparız.
                segments.add(new ClauseSegment(block, from, to));
            }
        }

        return segments;
    }

    /**
     * ClauseSegment:
     * Controller bu yapıyı kullanacak, ClauseEntity'ye yazacak.
     */
    public static class ClauseSegment {
        public final String text;
        public final int start;
        public final int end;

        public ClauseSegment(String text, int start, int end) {
            this.text = text;
            this.start = start;
            this.end = end;
        }
    }
}
