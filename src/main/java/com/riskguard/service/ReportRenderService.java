package com.riskguard.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.riskguard.dto.RiskReportResponse;
import org.springframework.beans.factory.annotation.Autowired;

import com.riskguard.ai.SuggestionService;
import com.riskguard.ai.SuggestionService.SmartSuggestion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;

@Service
public class ReportRenderService {
    
    @Autowired(required = false)
    private SuggestionService suggestionService;

    @Autowired
    private Environment env;

    @Value("${riskguard.ai.similarity.log:true}")
    private boolean similarityLogEnabled;

    // --- TR sayı biçimlendirme yardımcıları ---
    private static final java.util.Locale TR = new java.util.Locale("tr","TR");
    private static final java.util.Locale US = java.util.Locale.US;
    private String fmt1(double v) { return String.format(TR, "%.1f", v); }
    private String fmt2(double v) { return String.format(TR, "%.2f", v); }
    private String us1(double v) { return String.format(US, "%.1f", v); }

    public String renderHtml(RiskReportResponse report) {

        String riskLabel = riskLevelLabel(report.totalScore);
        String riskColor = riskLevelColor(report.totalScore);

        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>");
        html.append("<html lang='tr'><head>");
        html.append("<meta charset='UTF-8'/>");
        html.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'/>");
        html.append("<title>Sözleşme Risk Raporu</title>");

        // =======================
        // STYLES (PDF VE EKRAN İÇİN OPTİMİZE EDİLDİ)
        // =======================
        
        html.append("<style>");
        
        // --- PRINT (PDF) CSS ---
        html.append("@page { size: A4; margin: 0; }");
        html.append(".print-header { display: none; }");
        html.append(".screen-only { display: block; }");
        
        html.append("@media print {")
            // Renkleri PDF'e geçmeye zorlar (En kritik ayar)
            .append("  * { -webkit-print-color-adjust: exact !important; print-color-adjust: exact !important; }")
            .append("  body { background-color: #f3f6f9 !important; font-size: 11px !important; }")
            .append("  .page-wrapper { max-width: 100% !important; margin: 0 !important; padding: 0 !important; }")
            // Kartları şık beyaz kutular yapar ve sayfada bölünmelerini engeller
            .append("  .card { background: #ffffff !important; border: 1px solid #d1d5db !important; border-radius: 8px !important; padding: 20px !important; margin-bottom: 20px !important; page-break-inside: avoid; break-inside: avoid; box-shadow: 0 1px 3px rgba(0,0,0,0.1) !important; }")
            // Tablo düzeltmeleri (Taşmaları ve ezilmeleri önler)
            .append("  table { width: 100% !important; border-collapse: collapse !important; table-layout: fixed !important; }")
            .append("  th, td { word-wrap: break-word !important; border: 1px solid #e5e7eb !important; padding: 8px !important; vertical-align: top; }")
            .append("  th { background-color: #f8fafc !important; font-weight: bold !important; color: #111827 !important; }")
            .append("  tr { page-break-inside: avoid !important; break-inside: avoid !important; }")
            .append("  h1, h2, h3 { page-break-after: avoid !important; }")
            // PDF'te görünmemesi gereken butonlar
            .append("  .ui-inline-pdf-btn, .pdf-btn, .ai-suggestion { display: none !important; }")
            .append("  a[href]:after { content: none !important; }")
            // Sadece PDF'te çıkacak Kurumsal Antet başlığı
            .append("  .print-header { display: block !important; margin-bottom: 20px; padding-bottom: 10px; border-bottom: 2px solid #1f3a8a; font-size: 13px; color: #333; }")
            // Rozetlerin arka plan rengini zorla basar
            .append("  .badge { border: 1px solid rgba(0,0,0,0.1); color: #fff !important; }")
            .append("}");

        html.append(".snippet{break-inside:avoid;page-break-inside:avoid;}");

        /* ============================
           KURUMSAL TEMA (Web için)
           ============================ */
        html.append(":root{")
            .append("--bg-page:#f3f6f9; --bg-card:#ffffff; --border-card:#e5e7eb;")
            .append("--text-main:#0f172a; --text-dim:#6b7280; --text-heading:#111827;")
            .append("--brand-1:#1f3a8a; --brand-2:#2563eb; --danger:#ef4444;")
            .append("--radius-lg:14px; --radius-md:10px;")
            .append("}");

        html.append("body{background:var(--bg-page);color:var(--text-main); font:14px/1.6 'Inter',Arial,sans-serif;}");
        html.append(".page-wrapper{max-width:1100px;margin:0 auto;}");
        html.append(".card{background:var(--bg-card);border:1px solid var(--border-card); border-radius:var(--radius-lg);padding:20px;margin-bottom:24px; box-shadow:0 6px 18px rgba(2,6,23,.06);}");
        html.append("h1{font-size:20px;margin:0 0 8px 0;color:var(--text-heading);display:flex; align-items:center;justify-content:space-between;flex-wrap:wrap;}");
        html.append("h2{font-size:16px;margin:16px 0 8px 0;color:#333;}");
        html.append(".meta-row{font-size:14px;color:#555;margin-bottom:4px;}");
        html.append(".score{font-weight:800;font-size:32px;color:var(--brand-1);margin-top:4px;}");
        html.append(".score .unit{font-size:12px;color:var(--text-dim);}"); 

        // Rozet Stili
        html.append(".badge{display:inline-block;font-size:12px;font-weight:700;color:#fff; border-radius:999px;padding:6px 12px; line-height:1;}");

        html.append(".pdf-btn{display:inline-block;background:#1f2937;color:#fff;font-size:12px; font-weight:600;padding:6px 10px;border-radius:10px;text-decoration:none; border:1px solid #111;line-height:1;}");
        html.append(".pdf-btn:hover{background:#000;}");

        html.append(".top-risk-wrapper{display:flex;flex-wrap:wrap;gap:12px;margin:0;padding:0;list-style:none;}");
        html.append(".top-risk-card{flex:1 1 250px;border:1px solid var(--border-card);border-radius:12px; padding:12px 16px;background:#ffffff;box-shadow:0 4px 12px rgba(2,6,23,.05); page-break-inside:avoid;}");
        html.append(".top-risk-head{font-size:13px;font-weight:700;color:var(--text-heading);margin-bottom:4px;}");
        html.append(".top-risk-chip{font-size:11px;font-weight:700;border-radius:8px;padding:4px 8px;color:#fff;background:var(--danger);}");
        html.append(".top-risk-body{font-size:12px;color:#374151;}");

        html.append("table{width:100%;border-collapse:collapse;margin-top:12px;font-size:13px;}");
        html.append("th{text-align:left;background:#f8fafc;color:#111;padding:8px;border:1px solid var(--border-card);}");
        html.append("td{background:#fff;border:1px solid var(--border-card);padding:8px;}");
        html.append(".cat{font-weight:700;color:var(--text-heading);}");
        html.append(".rule{color:#64748b;font-size:11px;}");

        html.append(".snippet{font-family:monospace;font-size:11px;background:#f9fafb;border:1px dashed #e5e7eb; border-radius:6px;padding:8px;display:block;white-space:pre-wrap;word-break:break-word;}");
        html.append(".advice-box{font-size:12px;color:#111;background:#fff8e6;border:1px solid #fde68a; border-radius:8px;padding:10px;margin-top:6px;}");
        html.append(".summary-card{background:#fffbe6;border-left:4px solid #ffcc00;padding:12px 16px; border-radius:10px;margin-top:16px;font-size:13px;}");
        html.append(".empty{color:#777;font-style:italic;padding:16px 0;}");
        html.append(".footer-note{font-size:11px;color:#888;text-align:center;margin-top:24px;line-height:1.4;}");

        html.append("</style></head><body>");

        html.append("<div class='page-wrapper'>");

        // =======================
        // PRINT HEADER (Sadece PDF'te Görünür)
        // =======================
        String _printDate = java.time.LocalDate.now(java.time.ZoneId.systemDefault()).toString();
        html.append("<div class='print-header'>")
            .append("<div style='font-size: 18px; font-weight: bold; color: #1f3a8a; margin-bottom: 8px;'>Sözleşme Risk Analiz Raporu</div>")
            .append("<div><b>Belge ID:</b> ").append(safe(toSafeString(report.documentId))).append("</div>")
            .append("<div><b>Dosya adı:</b> ").append(safe(toSafeString(report.filename))).append("</div>")
            .append("<div><b>Oluşturma tarihi:</b> ").append(_printDate).append("</div>")
            .append("</div>");

        // =========================================================
        // 1. KART: Özet Bilgiler
        // =========================================================
        html.append("<div class='card'>");
        
        html.append("<h1>");
        html.append("<span>Sözleşme Risk Raporu</span>");
        html.append("<span style='display:flex; align-items:center; gap:8px;'>");
        // Rozet rengi inline olarak veriliyor, PDF CSS bunu ezecek (print-color-adjust sayesinde renk korunur)
        html.append("<span class='badge' style='background-color:").append(riskColor).append(";'>").append(riskLabel).append("</span>");
        html.append("<a class='pdf-btn ui-inline-pdf-btn' href='/ui/report/").append(safe(toSafeString(report.documentId))).append("/pdf' download>⬇ PDF indir</a>");
        html.append("</span>"); 
        html.append("</h1>");

        html.append("<div class='meta-row'><strong>Belge ID:</strong> ").append(safe(toSafeString(report.documentId))).append("</div>");
        html.append("<div class='meta-row'><strong>Dosya adı:</strong> ").append(safe(toSafeString(report.filename))).append("</div>");
        html.append("<div class='meta-row'><strong>Dil:</strong> ").append(safe(toSafeString(report.language))).append("</div>");

        html.append("<div class='meta-row' style='margin-top:12px;'><strong>Toplam Risk Skoru:</strong></div>");
        html.append("<div class='score'>").append(fmt1(report.totalScore)).append(" <span class='unit'>/ 100</span></div>");

        html.append("<div class='summary-card'>");
        html.append("<strong>📊 Analiz Özeti</strong><br>");
        html.append("İncelenen Madde Sayısı: ").append(report.clauseCount != null ? report.clauseCount : 0).append("<br>");
        html.append("Riskli Bulgu Sayısı: ").append(report.findings != null ? report.findings.size() : 0).append("<br>");
        html.append("Metin Uzunluğu: ").append(report.textLength != null ? report.textLength : 0).append(" karakter<br>");
        html.append("</div>");
        html.append("</div>"); 

        // =========================================================
        // 2. KART: En Kritik Riskler Özeti
        // =========================================================
        html.append("<div class='card'>");
        html.append("<h2>En Kritik Riskler</h2>");
        html.append(buildTopRisksSummary(report.findings));
        html.append("</div>");

        // =========================================================
        // 3. KART: Tespit Edilen Riskli Maddeler (Detay Tablosu)
        // =========================================================
        html.append("<div class='card'>");
        html.append("<h2>Tespit Edilen Riskli Maddeler (Detay)</h2>");

        if (report.findings == null || report.findings.isEmpty()) {
            html.append("<div class='empty'>Hiç riskli madde bulunamadı. Sözleşmeniz güvenli görünüyor.</div>");
        } else {
            html.append("<table><thead><tr>");
            // Sütun genişlikleri sabitlendi, PDF'te sıkışma önlendi
            html.append("<th style='width:20%;'>Kategori</th>");
            html.append("<th style='width:10%;'>Skor</th>");
            html.append("<th style='width:35%;'>Açıklama & Snippet</th>");
            html.append("<th style='width:35%;'>Tavsiye & Aksiyon</th>");
            html.append("</tr></thead><tbody>");

            java.util.List<FindingGroup> groups = groupFindings(report.findings);

            for (FindingGroup g : groups) {
                html.append("<tr>");

                // Kategori & Kurallar
                html.append("<td><div class='cat'>").append(safe(firstNonEmpty(g.humanTitle, prettyCategory(g.category)))).append("</div>");
                if (!g.ruleIds.isEmpty()) {
                    html.append("<div class='rule'>").append(safe(String.join(", ", g.ruleIds))).append("</div>");
                }
                html.append("</td>");

                // Skor
                html.append("<td><strong>").append(fmt1(g.scoreMax)).append("</strong></td>");

                // Açıklama & Metin Parçası
                html.append("<td>");
                if (isNonEmpty(g.explanation)) html.append("<div style='margin-bottom:8px;'>").append(safe(g.explanation)).append("</div>");
                if (isNonEmpty(g.snippet)) html.append("<div class='snippet'>").append(safe(g.snippet)).append("</div>");
                html.append("</td>");

                // Tavsiye & Aksiyon (Mitigation)
                html.append("<td>");
                boolean wroteAdvice = false;
                if (isNonEmpty(g.advice)) {
                    html.append("<div class='advice-box'><b>Tavsiye:</b> ").append(safe(g.advice)).append("</div>");
                    wroteAdvice = true;
                }
                if (isNonEmpty(g.mitigation)) {
                    html.append("<div class='advice-box' style='background:#e8f5e9; border-color:#c8e6c9;'><b>Önerilen Aksiyon:</b> ").append(safe(g.mitigation)).append("</div>");
                    wroteAdvice = true;
                }
                if (!wroteAdvice) {
                    html.append("<div class='advice-box' style='color:#777; background:#fafafa; border-color:#eee;'>Özel bir aksiyon tanımlanmamış.</div>");
                }

                // AI Yorum Eklentisi (Sadece Ekran İçin)
                if (suggestionService != null) {
                    try {
                        String clauseText = (g.snippet != null && !String.valueOf(g.snippet).isBlank()) ? g.snippet : (g.explanation != null && !String.valueOf(g.explanation).isBlank() ? g.explanation : g.category);
                        SmartSuggestion s = suggestionService.suggest(clauseText);
                        appendAiSuggestionHtml(html, s);
                    } catch (Exception ex) {
                        html.append("<div class='ai-suggestion' style='display:none;'></div>");
                    }
                }
                html.append("</td></tr>");
            }
            html.append("</tbody></table>");
        }
        html.append("</div>"); 
        
        // =========================================================
        // 4. KART: Kategori Bazlı Ortalama Skor
        // =========================================================
        html.append("<div class='card'>");
        html.append("<h2>Kategori Bazlı Ortalama Risk Skoru</h2>");

        if (report.avgScorePerCategory == null || report.avgScorePerCategory.isEmpty()) {
            html.append("<div class='empty'>Kategori bazlı skor hesaplanamadı.</div>");
        } else {
            html.append("<div style='display:flex; flex-direction:column; gap:8px; max-width:600px;'>");
            for (java.util.Map.Entry<String, Double> e : report.avgScorePerCategory.entrySet()) {
                String catRaw = e.getKey();
                Double avgVal = e.getValue() == null ? 0.0 : e.getValue();
                String prettyName = prettyCategory(catRaw);
                if (prettyName.isBlank()) prettyName = catRaw;

                double pct = Math.max(0, Math.min(100, avgVal));
                String barColor = (avgVal >= 70.0) ? "#b00020" : ((avgVal >= 30.0) ? "#f57c00" : "#2e7d32");

                html.append("<div style='font-size:13px; font-weight:600; color:#222;'>")
                    .append(safe(prettyName)).append(" <span style='font-size:12px; font-weight:400; color:#555;'>(avg ").append(fmt1(avgVal)).append(")</span></div>");
                html.append("<div style='height:10px; background:#eee; border-radius:999px; overflow:hidden;'>");
                html.append("<div style='height:100%; width:").append(us1(pct)).append("%; background:").append(barColor).append("'></div>");
                html.append("</div>");
            }
            html.append("</div>");
        }
        html.append("</div>"); 

        html.append("<div class='footer-note'>Bu rapor otomatik metin analizi ile üretilmiştir. Nihai hukuki değerlendirme için lütfen hukuk birimiyle doğrulayın.</div>");
        html.append("</div></body></html>");

        return html.toString();
    }

    private String riskLevelLabel(double score) {
        if (score >= 70.0) return "Yüksek Risk";
        if (score >= 30.0) return "Orta Risk";
        return "Düşük Risk";
    }

    private String riskLevelColor(double score) {
        if (score >= 70.0) return "#b00020"; 
        if (score >= 30.0) return "#f57c00"; 
        return "#2e7d32"; 
    }

    private String prettyCategory(String raw) {
        if (raw == null) return "";
        return switch (raw) {
            case "INDEMNITY" -> "Geniş Tazminat Yükümlülüğü";
            case "LIMITATION_OF_LIABILITY" -> "Sorumluluk Sınırı";
            case "TERMINATION" -> "Tek Taraflı Fesih Hakkı";
            case "INSURANCE_OBLIGATION" -> "Sigorta Yükümlülüğü";
            case "INTELLECTUAL_PROPERTY_TRANSFER" -> "Fikri Hakların Devri";
            case "GOVERNING_LAW" -> "Yabancı Hukuk Riski";
            case "CONFIDENTIALITY" -> "Gizlilik / KVKK";
            case "PAYMENT_TERMS" -> "Ödeme Şartları";
            case "DATA_PROTECTION" -> "Kişisel Veri";
            default -> raw;
        };
    }

    private String buildTopRisksSummary(List<RiskReportResponse.FindingSummary> findings) {
        if (findings == null || findings.isEmpty()) return "<div class='empty'>Bu sözleşmede tanımlı ciddi risk bulunamadı.</div>";
        List<RiskReportResponse.FindingSummary> sorted = new ArrayList<>(findings);
        sorted.sort((a,b) -> Double.compare(b.score, a.score));

        StringBuilder sb = new StringBuilder("<ul class='top-risk-wrapper'>");
        int limit = Math.min(3, sorted.size());
        for (int i = 0; i < limit; i++) {
            RiskReportResponse.FindingSummary f = sorted.get(i);
            sb.append("<li class='top-risk-card'>");
            sb.append("<div class='top-risk-head'>").append(safe(firstNonEmpty(f.humanTitle, prettyCategory(f.category)))).append(" <span class='top-risk-chip'>").append(f.score).append("</span></div>");
            sb.append("<div class='top-risk-body'>").append(safe(toSafeString(f.explanation))).append("</div>");
            sb.append("</li>");
        }
        sb.append("</ul>");
        return sb.toString();
    }

    private static class FindingGroup {
        String key;                    
        String category;               
        String humanTitle;             
        java.util.Set<String> ruleIds = new java.util.LinkedHashSet<>();
        double scoreMax = 0.0;
        double confMax = 0.0;
        String explanation;            
        String snippet;                
        String advice;                 
        String mitigation;             
    }

    private String fgKey(RiskReportResponse.FindingSummary f) {
        return toSafeString(f.snippet) + "||" + toSafeString(f.category);
    }

    private java.util.List<FindingGroup> groupFindings(java.util.List<RiskReportResponse.FindingSummary> list) {
        java.util.LinkedHashMap<String, FindingGroup> map = new java.util.LinkedHashMap<>();
        if (list == null) return new java.util.ArrayList<>();

        for (RiskReportResponse.FindingSummary f : list) {
            String key = fgKey(f);
            FindingGroup g = map.get(key);
            if (g == null) {
                g = new FindingGroup();
                g.key = key;
                g.category = toSafeString(f.category);
                g.humanTitle = firstNonEmpty(f.humanTitle, prettyCategory(toSafeString(f.category)));
                g.snippet = toSafeString(f.snippet);
                g.explanation = toSafeString(f.explanation);
                g.advice = toSafeString(f.advice);
                g.mitigation = toSafeString(f.mitigation);
                g.scoreMax = f.score;
                g.confMax  = f.confidence;
                if (f.ruleId != null) g.ruleIds.add(toSafeString(f.ruleId));
                map.put(key, g);
            } else {
                if (f.ruleId != null) g.ruleIds.add(toSafeString(f.ruleId));
                if (f.score > g.scoreMax) g.scoreMax = f.score;
                if (f.confidence > g.confMax) g.confMax = f.confidence;
            }
        }
        return new java.util.ArrayList<>(map.values());
    }

    private String safe(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");
    }

    private String toSafeString(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private boolean isNonEmpty(Object o) {
        return o != null && !String.valueOf(o).trim().isEmpty();
    }

    private String firstNonEmpty(String... vals) {
        for (String v : vals) if (v != null && !v.trim().isEmpty()) return v;
        return "";
    }

    private void appendAiSuggestionHtml(StringBuilder html, SmartSuggestion s) {
        html.append("<div class='ai-suggestion' style='margin-top:10px;padding:12px;border:1px dashed #cbd5e1;border-radius:10px;background:#f8fafc;'>");
        html.append("<div style='font-weight:700;margin-bottom:6px;'>AI Öneri (").append(safe(s.category())).append(")</div>");
        html.append("<div><b>Neden risk?</b> ").append(safe(s.rationale())).append("</div>");
        if (s.saferWording() != null && !s.saferWording().isBlank()) {
            html.append("<div style='margin-top:6px;'><b>Daha Güvenli İfade:</b> ").append(safe(s.saferWording())).append("</div>");
        }
        html.append("</div>");
    }   
}