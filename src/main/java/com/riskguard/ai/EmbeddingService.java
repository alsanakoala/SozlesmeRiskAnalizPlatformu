// src/main/java/com/riskguard/ai/EmbeddingService.java
package com.riskguard.ai;

import ai.djl.Application;
import ai.djl.ModelException;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelZoo;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;
import ai.djl.training.util.ProgressBar;
import ai.djl.util.Utils;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

// HuggingFace çevirici (embedding için gerekli)
import ai.djl.huggingface.translator.TextEmbeddingTranslatorFactory;

import java.io.IOException;
import java.util.Arrays;

@Service
public class EmbeddingService implements AutoCloseable {

    private ZooModel<String, float[]> model;
    private Predictor<String, float[]> predictor;

    // Eğer model yüklenemezse servis “degrade” modda çalışır
    private volatile boolean degraded = false;

    @PostConstruct
    public void init() {
        try {
            // Çok dilli cümle embedding (Sentence-Transformers)
            // Örn: paraphrase-multilingual-MiniLM-L12-v2 (Türkçe için uygun)
            Criteria<String, float[]> criteria = Criteria.builder()
                    .optApplication(Application.NLP.TEXT_EMBEDDING)
                    .setTypes(String.class, float[].class)
                    .optEngine("PyTorch")
                    // 🔑 DOĞRU MODEL ZOO ŞEMASI:
                    .optModelUrls("djl://ai.djl.huggingface.pytorch/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2")
                    // Gerekli çevirici:
                    .optTranslatorFactory(new TextEmbeddingTranslatorFactory())
                    .optProgress(new ProgressBar())
                    .build();

            model = ModelZoo.loadModel(criteria);
            predictor = model.newPredictor();

            // Basit bir dry-run (loglamak için):
            float[] probe = predictor.predict("Merhaba dünya");
            System.out.println("[EmbeddingService] Model yüklendi. Vektör boyutu=" + probe.length);

        } catch (Exception e) {
            // Uygulamayı düşürmeyelim; degrade moda geç
            degraded = true;
            System.err.println("[EmbeddingService] UYARI: Embedding modeli yüklenemedi, degrade moda geçiliyor: " + e);
            System.err.println(" - DJL/pytorch yerelleri veya ağ erişimi eksik olabilir.");
            System.err.println(" - Uygulama çalışmaya devam edecek; benzerlik kalitesi düşer.");
        }
    }

    public float[] embed(String text) throws TranslateException {
        if (!degraded && predictor != null) {
            try {
                return predictor.predict(text == null ? "" : text);
            } catch (Exception e) {
                // çalışma zamanında da sorun çıkarsa degrade et
                degraded = true;
                System.err.println("[EmbeddingService] Çalışma zamanında hata; degrade moda geçiliyor: " + e);
            }
        }
        // ---- DEGRADE Fallback: küçük bir pseudo-embedding ----
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
        // Ağ/yerel kütüphane engeli varsa servis çalışsın diye basit bir hash vektörü
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
