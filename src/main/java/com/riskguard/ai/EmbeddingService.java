// src/main/java/com/riskguard/ai/EmbeddingService.java
package com.riskguard.ai;

import ai.djl.Application;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelZoo;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;
import ai.djl.training.util.ProgressBar;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import ai.djl.huggingface.translator.TextEmbeddingTranslatorFactory;

@Service
public class EmbeddingService implements AutoCloseable {

    private ZooModel<String, float[]> model;
    private Predictor<String, float[]> predictor;

    // 🔥 HIZLANDIRMA: Başlangıçta doğrudan "degrade" (hafif) modda başlatıyoruz.
    private volatile boolean degraded = true; 

    @PostConstruct
    public void init() {
        // Ağır PyTorch modelini internetten indirmeye çalışma işlemini devre dışı bıraktık.
        // Sistem artık saniyelerce/dakikalarca beklemeyecek!
        System.out.println("======================================================");
        System.out.println("🚀 [EmbeddingService] DİKKAT: Ağır AI modeli devre dışı!");
        System.out.println("🚀 [EmbeddingService] Pseudo-embedding (Şimşek Hızı Modu) aktif.");
        System.out.println("======================================================");

        /* İleride projeyi güçlü bir sunucuya taşıdığında gerçek yapay zeka
         vektörlerini (Semantic Search) açmak istersen bu bloğu tekrar aktifleştirebilirsin:
         
        try {
            Criteria<String, float[]> criteria = Criteria.builder()
                    .optApplication(Application.NLP.TEXT_EMBEDDING)
                    .setTypes(String.class, float[].class)
                    .optEngine("PyTorch")
                    .optModelUrls("djl://ai.djl.huggingface.pytorch/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2")
                    .optTranslatorFactory(new TextEmbeddingTranslatorFactory())
                    .optProgress(new ProgressBar())
                    .build();

            model = ModelZoo.loadModel(criteria);
            predictor = model.newPredictor();
            degraded = false;
        } catch (Exception e) {
            degraded = true;
            System.err.println("[EmbeddingService] UYARI: Model yüklenemedi: " + e);
        }
        */
    }

    public float[] embed(String text) throws TranslateException {
        // Her zaman en hızlı mod olan pseudoEmbed çalışacak
        return pseudoEmbed(text);
    }

    public double cosine(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb) + 1e-9);
    }

    @Override
    public void close() throws Exception {
        if (predictor != null) predictor.close();
        if (model != null) model.close();
    }

    private float[] pseudoEmbed(String text) {
        // İşlemleri kilitlemeyen, saniyenin binde biri süren pseudo vektörleme
        int dim = 64;
        float[] v = new float[dim];
        if (text == null) return v;
        int i = 0;
        for (char c : text.toCharArray()) v[i++ % dim] += (c % 13);
        
        // normalize
        double n = 0;
        for (float x : v) n += x * x;
        n = Math.sqrt(n) + 1e-9;
        for (int k = 0; k < v.length; k++) v[k] /= n;
        return v;
    }
}