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
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

@Service
public class ReportRenderService {
    
    @Autowired
    private SuggestionService suggestionService;

    // Tema default'unu application.yml'dan okuyacağız
    @Autowired
    private Environment env;

    @Value("${riskguard.ai.similarity.log:true}")
    private boolean similarityLogEnabled; // UI’da “Benzerlik: …” göstergesini kontrol eder

    // --- TR sayı biçimlendirme yardımcıları ---
    private static final java.util.Locale TR = new java.util.Locale("tr","TR");
    private static final java.util.Locale US = java.util.Locale.US;
    private String fmt1(double v) { return String.format(TR, "%.1f", v); }   // 1 ondalık (TR)
    private String fmt2(double v) { return String.format(TR, "%.2f", v); }   // 2 ondalık (TR)
    private String us1(double v) { return String.format(US, "%.1f", v); }    // CSS yüzde için (US)

    /**
     * MEVCUT oluşturduğun HTML renderer — DOKUNMADIM
     */
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
        // STYLES
        // =======================
        
        html.append("<style>");
        // --- Print (PDF) optimizasyonu ---
        html.append("@page{size:A4;margin:12mm 12mm 14mm 12mm;}");
        html.append(".print-header{display:none;}");               // ekran için kapalı
        html.append(".screen-only{display:block;}");              // ekran sınıfı
        html.append("@media print{")
            // Ekranda işe yarayan ama yazdırmada gereksiz unsurları gizle
            .append(".ui-inline-pdf-btn,.pdf-btn,.ai-suggestion{display:none!important;}")
            // Genel sadeleştirme
            .append("*{background:transparent!important;box-shadow:none!important;}")
            // Sayfa düzeni
            .append(".page-wrapper{max-width:100%!important;margin:0!important;}")
            .append(".card{page-break-inside:avoid;break-inside:avoid;}")
            .append("tr,td,th,thead,tbody,table{page-break-inside:avoid;break-inside:avoid;}")
            .append("h1,h2{page-break-after:avoid;}")
            // Link sonlarına otomatik URL eklemesini kapat
            .append("a[href]:after{content:\"\"!important;}")
            // Print başlığı göster
            .append(".print-header{display:block!important;margin:0 0 8px 0;font-size:12px;color:#555;}")
            // Renk kontrastı (rozetler yazıcıda okunaklı kalsın)
            .append(".badge{filter:grayscale(20%);}")
            .append("}");
        html.append(".snippet{break-inside:avoid;page-break-inside:avoid;}");

        /* ============================
           KURUMSAL TEMA – EK/OVERRIDE
           (mevcut stilleri silmeden üzerine yazar)
           ============================ */
        html.append(":root{")
            .append("--bg-page:#f3f6f9;")
            .append("--bg-card:#ffffff;")
            .append("--border-card:#e5e7eb;")
            .append("--text-main:#0f172a;")
            .append("--text-dim:#6b7280;")
            .append("--text-heading:#111827;")
            .append("--brand-1:#1f3a8a;")
            .append("--brand-2:#2563eb;")
            .append("--ok:#10b981;")
            .append("--warn:#f59e0b;")
            .append("--danger:#ef4444;")
            .append("--radius-lg:14px;")
            .append("--radius-md:10px;")
            .append("}");

        // Sayfa genel stil (mevcudu koruyup override ediyoruz)
        html.append("body{background:var(--bg-page);color:var(--text-main);")
            .append("font:14px/1.6 'Inter','DejaVu Sans',Arial,sans-serif;")
            .append("-webkit-font-smoothing:antialiased;-moz-osx-font-smoothing:grayscale;}");

        // İçerik sarmalayıcı
        html.append(".page-wrapper{max-width:1100px;margin:0 auto;}");

        // Kartlar
        html.append(".card{background:var(--bg-card);border:1px solid var(--border-card);")
            .append("border-radius:var(--radius-lg);padding:20px;margin-bottom:24px;")
            .append("box-shadow:0 6px 18px rgba(2,6,23,.06);}");

        // Başlıklar
        html.append("h1{font-size:20px;margin:0 0 8px 0;color:var(--text-heading);display:flex;")
            .append("align-items:center;justify-content:space-between;flex-wrap:wrap;row-gap:8px;}")
            .append("h2{font-size:16px;margin:16px 0 8px 0;color:#333;}");

        html.append(".meta-row{font-size:14px;color:#555;margin-bottom:4px;}");
        html.append(".score{font-weight:800;font-size:32px;color:var(--brand-1);margin-top:4px;}");
        html.append(".score .unit{font-size:12px;color:var(--text-dim);}"); // istersen kullan

        // Risk rozet stili
        html.append(".badge{display:inline-block;font-size:12px;font-weight:700;color:#fff;")
            .append("border-radius:999px;padding:6px 12px;line-height:1;")
            .append("box-shadow:0 2px 6px rgba(0,0,0,.10);}"); // hafif yumuşak gölge

        // PDF butonu
        html.append(".pdf-btn{display:inline-block;background:#1f2937;color:#fff;font-size:12px;")
            .append("font-weight:600;padding:6px 10px;border-radius:10px;text-decoration:none;")
            .append("border:1px solid #111;font-family:system-ui,-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Helvetica Neue',Arial,sans-serif;line-height:1;}")
            .append(".pdf-btn:hover{background:#000;}");

        // Kritik riskler bölümü
        html.append(".top-risk-wrapper{display:flex;flex-wrap:wrap;gap:12px;margin:0;padding:0;list-style:none;}")
            .append(".top-risk-card{flex:1 1 250px;border:1px solid var(--border-card);border-radius:12px;")
            .append("padding:12px 16px;background:#ffffff;box-shadow:0 4px 12px rgba(2,6,23,.05);")
            .append("page-break-inside:avoid;}")
            .append(".top-risk-head{font-size:13px;font-weight:700;color:var(--text-heading);margin-bottom:4px;display:flex;flex-wrap:wrap;gap:6px;align-items:center;}")
            .append(".top-risk-chip{font-size:11px;font-weight:700;line-height:1;border-radius:8px;padding:4px 8px;color:#fff;background:var(--danger);} ")
            .append(".top-risk-body{font-size:12px;color:#374151;line-height:1.45;}");

        // Bulgular tablosu
        html.append("table{width:100%;border-collapse:collapse;margin-top:12px;font-size:14px;}")
            .append("th{text-align:left;background:#f8fafc;color:#111;padding:8px;font-weight:700;font-size:13px;border:1px solid var(--border-card);}")
            .append("td{background:#fff;border:1px solid var(--border-card);vertical-align:top;padding:8px;}")
            .append(".cat{font-weight:700;color:var(--text-heading);}")
            .append(".rule{color:#64748b;font-size:12px;}");

        // Metinden alınan snippet
        html.append(".snippet{font-family:monospace;font-size:12px;background:#f9fafb;border:1px dashed #e5e7eb;")
            .append("border-radius:8px;padding:8px;display:block;white-space:pre-wrap;word-break:break-word;}");

        // Tavsiye kutuları
        html.append(".advice-box{font-size:12px;color:#111;background:#fff8e6;border:1px solid #fde68a;")
            .append("border-radius:10px;padding:10px;line-height:1.45;margin-top:6px;}");

        html.append(".empty{color:#777;font-style:italic;padding:16px 0;}");

        // Dipnot
        html.append(".footer-note{font-size:11px;color:#888;text-align:center;margin-top:24px;line-height:1.4;}");

        // Analiz Özeti
        html.append(".summary-card{background:#fffbe6;border-left:4px solid #ffcc00;padding:12px 16px;")
            .append("border-radius:10px;margin-top:16px;font-size:13px;color:#333;line-height:1.6;}")
            .append(".summary-card strong{color:#000;}");

        // Yumuşak gradient başlık şeridi (ilk kartın başlığını yumuşatır; HTML’i değiştirmeden)
        html.append(".card:first-of-type h1{")
            .append("background:linear-gradient(135deg,var(--brand-1),var(--brand-2));")
            .append("-webkit-background-clip:text;background-clip:text;color:transparent;")
            .append("}");

        html.append("</style>");

        html.append("</head><body>");

        // =======================
        // PAGE WRAPPER BAŞLANGICI
        // =======================
        html.append("<div class='page-wrapper'>");
        // --- Satır vurgulama (anchor hedefi) ---
        // ⬇⬇⬇ BODY’deki serbest CSS’i bir <style> bloğuna alıyoruz (silmeden)
        html.append("<style>")
            .append("tr[id^=\\\"row-\\\"]{scroll-margin-top:16px;}")
            .append(".flash-highlight{animation: rgFlash 1.6s ease-out 0s 1;}")
            .append("@keyframes rgFlash{0%{background:#fff3cd;}60%{background:#fffbe6;}100%{background:transparent;}}")
            .append("</style>");
        // --- Print çıktısı için üst bilgi ---
        String _printDate = java.time.LocalDate.now(java.time.ZoneId.systemDefault()).toString();
        html.append("<div class='print-header'>")
            .append("<div><b>Rapor:</b> Sözleşme Risk Raporu</div>")
            .append("<div><b>Belge ID:</b> ").append(safe(toSafeString(report.documentId))).append("</div>")
            .append("<div><b>Dosya adı:</b> ").append(safe(toSafeString(report.filename))).append("</div>")
            .append("<div><b>Oluşturma tarihi:</b> ").append(_printDate).append("</div>")
            .append("</div>");

        // =========================================================
        // ÜST KART
        // =========================================================
        html.append("<div class='card'>");

        html.append("<h1>");

        // Sol taraf (başlık)
        html.append("<span style='display:flex; flex-direction:column;'>");
        html.append("<span>Sözleşme Risk Raporu</span>");
        html.append("</span>");

        // Sağ taraf (rozet + PDF butonu)
        html.append("<span style='display:flex; flex-wrap:wrap; align-items:center; gap:8px;'>");

        // risk rozeti
        html.append("<span class='badge' style='background:")
            .append(riskColor)
            .append(";'>")
            .append(riskLabel)
            .append("</span>");

        // PDF indir butonu (iframe'de kaldırılacak)
        html.append("<a class='pdf-btn ui-inline-pdf-btn' href='/ui/report/")
            .append(safe(toSafeString(report.documentId)))
            .append("/pdf' download>")
            .append("⬇ PDF indir")
            .append("</a>");

        html.append("</span>"); // sağ taraf

        html.append("</h1>");

        // meta bilgiler
        html.append("<div class='meta-row'><strong>Belge ID:</strong> ")
            .append(safe(toSafeString(report.documentId)))
            .append("</div>");
        html.append("<div class='meta-row'><strong>Dosya adı:</strong> ")
            .append(safe(toSafeString(report.filename)))
            .append("</div>");
        html.append("<div class='meta-row'><strong>Dil:</strong> ")
            .append(safe(toSafeString(report.language)))
            .append("</div>");

        html.append("<div class='meta-row' style='margin-top:12px;'><strong>Toplam Risk Skoru:</strong></div>");
        html.append("<div class='score'>")
            .append(fmt1(report.totalScore))
            .append(" <span class='unit'>/ 100</span></div>");

        // ANALİZ ÖZETİ KARTI
        html.append("<div class='summary-card'>");
        html.append("<strong>📊 Analiz Özeti</strong><br>");
        html.append("Madde Sayısı: ")
            .append(report.clauseCount != null ? report.clauseCount : 0)
            .append("<br>");
        html.append("Bulgu Sayısı: ")
            .append(report.findings != null ? report.findings.size() : 0)
            .append("<br>");
        html.append("Metin Uzunluğu: ")
            .append(report.textLength != null ? report.textLength : 0)
            .append(" karakter<br>");
        html.append("Toplam Risk Skoru: ")
            .append(fmt1(report.totalScore))
            .append("<br>");
        html.append("</div>");

        // En Kritik Riskler
        html.append("<div style='margin-top:16px;'>");
        html.append("<h2 style='margin:0 0 6px 0;'>En Kritik 3 Risk</h2>");
        html.append(buildTopRisksSummary(report.findings));
        html.append("</div>");

        html.append("</div>"); // card kapat

        // =========================================================
        // BULGULAR KARTI
        // =========================================================
        html.append("<div class='card'>");
        html.append("<h2>Tespit Edilen Riskli Maddeler</h2>");

        if (report.findings == null || report.findings.isEmpty()) {
            html.append("<div class='empty'>Hiç riskli madde bulunamadı veya kurallar bu metne uymadı.</div>");
        } else {
            html.append("<table>");
            html.append("<thead>");
            html.append("<tr>");
            html.append("<th style='width:20%;'>Başlık / Kurallar</th>");
            html.append("<th style='width:12%;'>Skor</th>");
            html.append("<th style='width:12%;'>Güven</th>");
            html.append("<th style='width:26%;'>Açıklama</th>");
            html.append("<th style='width:30%;'>Tavsiye</th>");
            html.append("</tr>");
            html.append("</thead>");
            html.append("<tbody>");

            // --- Dedup edilmiş liste ---
            java.util.List<FindingGroup> groups = groupFindings(report.findings);

            for (FindingGroup g : groups) {
                String rowDomId = Integer.toHexString(g.key.hashCode());
                html.append("<tr id='row-").append(rowDomId).append("'>");

                // Başlık / Kurallar
                html.append("<td>");
                String titleToShow = firstNonEmpty(
                        g.humanTitle,
                        prettyCategory(g.category)
                );
                html.append("<div class='cat'>")
                    .append(safe(titleToShow))
                    .append("</div>");

                if (!g.ruleIds.isEmpty()) {
                    html.append("<div class='rule'>Eşleşen Kurallar: ")
                        .append(safe(String.join(", ", g.ruleIds)))
                        .append("</div>");
                }
                html.append("</td>");

                // Skor (TR format)
                html.append("<td>")
                    .append(fmt1(g.scoreMax))
                    .append("</td>");

                // Güven (TR format)
                html.append("<td>")
                    .append(fmt2(g.confMax))
                    .append("</td>");

                // Açıklama + Snippet (toggle)
                html.append("<td>");
                if (isNonEmpty(g.explanation)) {
                    html.append("<div>")
                        .append(safe(g.explanation))
                        .append("</div>");
                } else {
                    html.append("<div>-</div>");
                }
                if (isNonEmpty(g.snippet)) {
                    // Yukarıda zaten rowDomId tanımlıysa, onu kullan
                    // Eğer değilse, bu satırda üret:
                    String domId = Integer.toHexString(g.key.hashCode());

                    // snippet'i toggle’lı şekilde ekle (kısalt / devamını göster)
                    html.append(snippetWithToggle(g.snippet, domId));

                }

                html.append("</td>");

                // Tavsiye
                html.append("<td>");
                boolean wroteAdvice = false;

                if (isNonEmpty(g.advice)) {
                    html.append("<div class='advice-box'>");
                    html.append("<b>Tavsiye:</b> ");
                    html.append(safe(g.advice));
                    html.append("</div>");
                    wroteAdvice = true;
                }

                if (isNonEmpty(g.mitigation)) {
                    html.append("<div class='advice-box' style='background:#e8f5e9; border-color:#c8e6c9;'>");
                    html.append("<b>Önerilen Aksiyon:</b> ");
                    html.append(safe(g.mitigation));
                    html.append("</div>");
                    wroteAdvice = true;
                }

                if (!wroteAdvice) {
                    html.append("<div class='advice-box' style='color:#777; background:#fafafa; border-color:#eee;'>");
                    html.append("Belirli bir aksiyon önerisi yok. Hukuk ekibiyle gözden geçirilmesi önerilir.");
                    html.append("</div>");
                }

                // === AI Öneri (MVP) — mevcut içeriğe EK olarak ===
                try {
                    String clauseText =
                        (g.snippet != null && !String.valueOf(g.snippet).isBlank())
                            ? g.snippet
                            : (g.explanation != null && !String.valueOf(g.explanation).isBlank()
                                ? g.explanation
                                : g.category);
                    SmartSuggestion s = suggestionService.suggest(clauseText);
                    appendAiSuggestionHtml(html, s);
                } catch (Exception ex) {
                    html.append("<div class='advice-box' style='color:#b00020;background:#fff5f5;border-color:#ffcdd2;'>")
                        .append("AI öneri üretiminde bir hata oluştu: ")
                        .append(safe(ex.getMessage()))
                        .append("</div>");
                }

                html.append("</td>");

                html.append("</tr>");
            }

            html.append("</tbody>");
            html.append("</table>");
        }

        html.append("</div>"); // card
        
        // =========================================================
        // KATEGORİ BAZLI ORTALAMA SKOR KARTI
        // =========================================================
        html.append("<div class='card'>");
        html.append("<h2>Kategori Bazlı Ortalama Risk Skoru</h2>");

        if (report.avgScorePerCategory == null || report.avgScorePerCategory.isEmpty()) {
            html.append("<div class='empty'>Kategori bazlı skor hesaplanamadı.</div>");
        } else {
            html.append("<div style='font-size:13px; color:#555; margin-bottom:12px;'>");
            html.append("Aşağıdaki çubuklar her risk kategorisi için ortalama skor değerini gösterir. ");
            html.append("Yüksek skor = daha kritik risk alanı.</div>");

            html.append("<div style='display:flex; flex-direction:column; gap:8px; max-width:600px;'>");

            for (java.util.Map.Entry<String, Double> e : report.avgScorePerCategory.entrySet()) {
                String catRaw = e.getKey();
                Double avgVal = e.getValue();
                if (avgVal == null) {
                    avgVal = 0.0;
                }

                String prettyName = prettyCategory(catRaw);
                if (prettyName == null || prettyName.isBlank()) {
                    prettyName = catRaw;
                }

                double pct = avgVal;
                if (pct < 0) pct = 0;
                if (pct > 100) pct = 100;

                html.append("<div style='font-size:13px; font-weight:600; color:#222;'>")
                    .append(safe(prettyName))
                    .append(" <span style='font-size:12px; font-weight:400; color:#555;'>(avg ")
                    .append(fmt1(avgVal))
                    .append(")</span>")
                    .append("</div>");

                html.append("<div style='height:10px; background:#eee; border-radius:999px; overflow:hidden;'>");

                String barColor;
                if (avgVal >= 70.0) {
                    barColor = "#b00020";
                } else if (avgVal >= 30.0) {
                    barColor = "#f57c00";
                } else {
                    barColor = "#2e7d32";
                }

                html.append("<div style='height:100%; width:")
                    .append(us1(pct))   // CSS yüzdede noktalı ondalık gerekir
                    .append("%; background:")
                    .append(barColor)
                    .append("'></div>");

                html.append("</div>");
            }

            html.append("</div>");
        }

        html.append("</div>"); // card

        // =========================================================
        // FOOTER
        // =========================================================
        html.append("<div class='footer-note'>");
        html.append("Bu rapor otomatik metin analizi ile üretilmiştir. Nihai hukuki değerlendirme için lütfen hukuk birimiyle doğrulayın.");
        html.append("</div>");

        // page-wrapper kapanış
        html.append("</div>");

        // Inline/iframe görünümünde PDF butonunu kaldır
        html.append("<script>(function(){try{")
            .append("var inIframe = window.self !== window.top;")
            .append("if(inIframe){ var btn=document.querySelector('.ui-inline-pdf-btn'); if(btn) btn.remove(); }")
            .append("}catch(e){}})();</script>");


        // --- Anchor'a inince hedef satırı kısa süreli vurgula ---
        html.append("<script>(function(){")
            .append("function flash(id){")
            .append("  try{")
            .append("    if(!id) return;")
            .append("    var el=document.getElementById(id.replace('#',''));")
            .append("    if(!el) return;")
            .append("    if(el.scrollIntoView) el.scrollIntoView({behavior:'smooth',block:'center'});")
            .append("    el.classList.remove('flash-highlight');")
            .append("    void el.offsetWidth;") // reflow ile animasyonu resetle
            .append("    el.classList.add('flash-highlight');")
            .append("    setTimeout(function(){ el.classList.remove('flash-highlight'); }, 1700);")
            .append("  }catch(e){}")
            .append("}")
            .append("function handleHash(){ if(window.location.hash){ var id=window.location.hash.substring(1); flash(id); } }")
            .append("document.addEventListener('click',function(e){")
            .append("  var a=e.target.closest('a[href^=\"#row-\"]');")
            .append("  if(a){ e.preventDefault(); var id=a.getAttribute('href').substring(1); flash(id); }")
            .append("},true);")
            .append("window.addEventListener('hashchange',handleHash);")
            .append("handleHash();") // sayfa hash ile açıldıysa
            .append("})();</script>");
        html.append("</body></html>");

        return html.toString();
    }

    // Risk rozet metni
    private String riskLevelLabel(double score) {
        if (score >= 70.0) return "Yüksek Risk";
        if (score >= 30.0) return "Orta Risk";
        return "Düşük Risk";
    }

    // Risk rozet rengi
    private String riskLevelColor(double score) {
        if (score >= 70.0) return "#b00020"; // kırmızı
        if (score >= 30.0) return "#f57c00"; // turuncu
        return "#2e7d32"; // yeşil
    }

    private String prettyCategory(String raw) {
        if (raw == null) return "";
        return switch (raw) {
            case "INDEMNITY" -> "Geniş Tazminat Yükümlülüğü";
            case "LIMITATION_OF_LIABILITY" -> "Sorumluluk Sınırı / Limitsiz Sorumluluk";
            case "TERMINATION" -> "Tek Taraflı Fesih Hakkı";
            case "INSURANCE_OBLIGATION" -> "Yüksek Şirket Sigorta Yükümlülüğü";
            case "INTELLECTUAL_PROPERTY_TRANSFER" -> "Fikri Hakların Tam Devri";
            case "GOVERNING_LAW" -> "Yabancı Hukuk / Yetki Riski";
            case "CONFIDENTIALITY" -> "Gizlilik / Veri Koruma";
            case "PAYMENT_TERMS" -> "Ödeme Şartları";
            case "SLA" -> "Hizmet Seviyesi Taahhüdü (SLA)";
            case "DATA_PROTECTION" -> "Kişisel Veri / KVKK Riski";
            default -> raw;
        };
    }

    private String buildTopRisksSummary(List<RiskReportResponse.FindingSummary> findings) {
        if (findings == null || findings.isEmpty()) {
            return "<div class='empty'>Bu sözleşmede tanımlı ciddi risk bulunamadı.</div>";
        }
        List<RiskReportResponse.FindingSummary> sorted = new ArrayList<>(findings);
        sorted.sort((a,b) -> Double.compare(b.score, a.score));

        List<RiskReportResponse.FindingSummary> topDistinctByCategory = new ArrayList<>();
        java.util.Set<String> seenCategories = new java.util.HashSet<>();
        for (RiskReportResponse.FindingSummary f : sorted) {
            String catKey = f.category != null ? f.category : "_";
            if (!seenCategories.contains(catKey)) {
                topDistinctByCategory.add(f);
                seenCategories.add(catKey);
            }
            if (topDistinctByCategory.size() >= 3) break;
        }

        int limit = Math.min(3, topDistinctByCategory.size());
        StringBuilder sb = new StringBuilder();

        sb.append("<ul class='top-risk-wrapper'>");
        for (int i = 0; i < limit; i++) {
            RiskReportResponse.FindingSummary f = topDistinctByCategory.get(i);

            sb.append("<li class='top-risk-card'>");

            sb.append("<div class='top-risk-head'>");

            // tabloda oluşacak satır id’sini hesapla (snippet+category ile aynı anahtar)
            String _sn = toSafeString(f.snippet);
            String _cat = toSafeString(f.category);
            String _key = _sn + "||" + _cat;
            String _rowDomId = Integer.toHexString(_key.hashCode());

            // başlığı tabloda ilgili satıra götüren linke sar
            String _title = safe(firstNonEmpty(
                    f.humanTitle,
                    prettyCategory(toSafeString(f.category))
            ));

            sb.append("<a href='#row-").append(_rowDomId).append("' ")
            .append("style='text-decoration:none;color:inherit;border-bottom:1px dashed #94a3b8;'>")
            .append(_title)
            .append("</a>");

            sb.append("<span class='top-risk-chip' style='margin-left:8px;'>Skor: ")
            .append(String.valueOf(f.score))
            .append("</span>");

            sb.append("</div>");

            sb.append("<div class='top-risk-body'>");
            if (isNonEmpty(f.explanation)) {
                sb.append(safe(toSafeString(f.explanation)));
            } else {
                sb.append("Riskli ifade tespit edildi.");
            }
            sb.append("</div>");

            if (isNonEmpty(f.snippet)) {
                sb.append("<div class='top-risk-body' style='margin-top:8px; font-family:monospace;background:#fff;border:1px solid #eee;border-radius:6px;padding:8px;white-space:pre-wrap;word-break:break-word;'>");
                sb.append(safe(toSafeString(f.snippet)));
                sb.append("</div>");
            }

            if (isNonEmpty(f.advice)) {
                sb.append("<div class='top-risk-body' style='margin-top:8px; font-size:12px; background:#fff8e1; border:1px solid #ffecb3; border-radius:6px; padding:8px; line-height:1.4;'>");
                sb.append("<b>Tavsiye:</b> ");
                sb.append(safe(toSafeString(f.advice)));
                sb.append("</div>");
            }

            if (isNonEmpty(f.mitigation)) {
                sb.append("<div class='top-risk-body' style='margin-top:8px; font-size:12px; background:#e8f5e9; border:1px solid #c8e6c9; border-radius:6px; padding:8px; line-height:1.4;'>");
                sb.append("<b>Önerilen Aksiyon:</b> ");
                sb.append(safe(toSafeString(f.mitigation)));
                sb.append("</div>");
            }

            sb.append("</li>");
        }
        sb.append("</ul>");

        return sb.toString();
    }

    // --- Dedup için grup modeli ve yardımcıları ---
    private static class FindingGroup {
        String key;                    // snippet + category
        String category;               // ham category
        String humanTitle;             // varsa
        java.util.Set<String> ruleIds = new java.util.LinkedHashSet<>();
        double scoreMax = 0.0;
        double confMax = 0.0;
        String explanation;            // ilk/dolu olan
        String snippet;                // ortak snippet
        String advice;                 // ilk/dolu olan
        String mitigation;             // ilk/dolu olan
    }

    private String fgKey(RiskReportResponse.FindingSummary f) {
        String sn = toSafeString(f.snippet);
        String cat = toSafeString(f.category);
        return sn + "||" + cat;
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

            // ⬇️ primitive double'lar için null kontrolü yok
            g.scoreMax = f.score;
            g.confMax  = f.confidence;

            if (f.ruleId != null) g.ruleIds.add(toSafeString(f.ruleId));
            map.put(key, g);
        } else {
            if (f.ruleId != null) g.ruleIds.add(toSafeString(f.ruleId));

            // ⬇️ primitive double
            double s = f.score;
            if (s > g.scoreMax) g.scoreMax = s;

            double c = f.confidence;
            if (c > g.confMax) g.confMax = c;

            if (!isNonEmpty(g.explanation) && isNonEmpty(f.explanation)) g.explanation = toSafeString(f.explanation);
            if (!isNonEmpty(g.advice) && isNonEmpty(f.advice)) g.advice = toSafeString(f.advice);
            if (!isNonEmpty(g.mitigation) && isNonEmpty(f.mitigation)) g.mitigation = toSafeString(f.mitigation);
        }
    }
    return new java.util.ArrayList<>(map.values());
    }


    private String safe(String s) {
        if (s == null) return "";
        return s
            .replace("&","&amp;")
            .replace("<","&lt;")
            .replace(">","&gt;")
            .replace("\"","&quot;")
            .replace("'","&#39;");
    }

    private String toSafeString(Object o) {
        if (o == null) return "";
        return String.valueOf(o);
    }

    private boolean isNonEmpty(Object o) {
        if (o == null) return false;
        String s = String.valueOf(o).trim();
        return !s.isEmpty();
    }

    private String firstNonEmpty(String... vals) {
        if (vals == null) return "";
        for (String v : vals) {
            if (v != null && !v.trim().isEmpty()) {
                return v;
            }
        }
        return "";
    }

    // --- Snippet kısaltma ve "devamını göster" HTML'i ---
    private String snippetWithToggle(String raw, String domId) {
        if (raw == null) raw = "";
        String safeText = safe(toSafeString(raw));
        int limit = 450; // karakter sınırı (isteğe göre 300/500)
        if (safeText.length() <= limit) {
            return "<div class='snippet'>" + safeText + "</div>";
        }
        String shortPart = safeText.substring(0, limit) + "…";
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='snippet' id='sn-short-").append(domId).append("' style='display:block;'>")
          .append(shortPart)
          .append("</div>");
        sb.append("<div class='snippet' id='sn-full-").append(domId).append("' style='display:none;'>")
          .append(safeText)
          .append("</div>");
        sb.append("<a href='#' class='pdf-btn' style='margin-top:6px; display:inline-block; background:#e5e7eb; color:#111; border-color:#cbd5e1;' ")
          .append("onclick=\"var a=document.getElementById('sn-short-").append(domId).append("');")
          .append("var b=document.getElementById('sn-full-").append(domId).append("');")
          .append("if(a.style.display==='none'){a.style.display='block';b.style.display='none';this.innerText='Devamını göster';}")
          .append("else{a.style.display='none';b.style.display='block';this.innerText='Kısalt';}")
          .append("return false;\">Devamını göster</a>");
        return sb.toString();
    }

        private void appendAiSuggestionHtml(StringBuilder html, SmartSuggestion s) {
        html.append("<div class='ai-suggestion' ")
            .append("style='margin-top:10px;padding:12px;border:1px dashed #cbd5e1;")
            .append("border-radius:10px;background:#f8fafc;'>");

        // Başlık
        html.append("<div style='font-weight:700;margin-bottom:6px;'>")
            .append("AI Öneri (").append(s.category()).append(")")
            .append("</div>");

        // Neden risk?
        html.append("<div><b>Neden risk?</b> ")
            .append(s.rationale())
            .append("</div>");

        // Müzakere ipuçları
        if (s.levers() != null && !s.levers().isEmpty()) {
            html.append("<div style='margin-top:6px;'><b>Müzakere İpuçları:</b><ul style='margin:6px 0 0 18px;'>");
            for (String tip : s.levers()) {
                html.append("<li>").append(tip).append("</li>");
            }
            html.append("</ul></div>");
        }

        // Daha güvenli ifade
        if (s.saferWording() != null && !s.saferWording().isBlank()) {
            html.append("<div style='margin-top:6px;'><b>Daha Güvenli İfade:</b> ")
                .append(s.saferWording())
                .append("</div>");
        }

        // --- BENZERLİK ROZETİ + UYARILAR ---
        if (similarityLogEnabled && (s.matchId() != null || s.similarity() != null)) {

            Double sim = s.similarity();           // null olabilir
            String simText = (sim == null) ? "—" : fmt2(sim);  // TR biçimi
            // Renk eşikleri:
            //  >= 0.90  → koyu yeşil
            //  0.85–0.89 → turuncu (sınırda)
            //  < 0.85 veya null → gri (düşük güven)
            String badgeBg = "#e5e7eb";  // gri varsayılan
            String badgeText = "#111827";
            String badgeBorder = "#cbd5e1";
            String badgeEmoji = "🔍";

            if (sim != null) {
                if (sim >= 0.90) {
                    badgeBg = "#dcfce7";  // yeşilimsi
                    badgeText = "#065f46";
                    badgeBorder = "#bbf7d0";
                } else if (sim >= 0.85) {
                    badgeBg = "#fff7ed";  // turuncumsu (sınırda)
                    badgeText = "#9a3412";
                    badgeBorder = "#fed7aa";
                } else {
                    // 0.85 altı zaten gri kalacak
                }
            }

            // Düşük güven bayrağı varsa griye zorlansın
            if (s.lowConfidence()) {
                badgeBg = "#f1f5f9";
                badgeText = "#0f172a";
                badgeBorder = "#cbd5e1";
                badgeEmoji = "ℹ️";
            }

            html.append("<div style='margin-top:8px;display:flex;gap:8px;align-items:center;flex-wrap:wrap'>");

            // Ana benzerlik rozeti
            html.append("<span title='")
                .append(s.matchId() != null ? s.matchId() : "benzerlik")
                .append(s.matchCategory() != null ? (" • " + s.matchCategory()) : "")
                .append(sim != null ? (" • cosine " + simText) : "")
                .append("' ")
                .append("style='display:inline-block;font-size:11px;font-weight:600;")
                .append("padding:4px 9px;border-radius:999px;")
                .append("border:1px solid ").append(badgeBorder).append(";")
                .append("color:").append(badgeText).append(";")
                .append("background:").append(badgeBg).append(";")
                .append("box-shadow:0 1px 2px rgba(0,0,0,0.04);'>")
                .append(badgeEmoji).append(" Benzerlik ").append(simText);

            // (id • kategori) bilgisini kısa tut
            if (s.matchId() != null) {
                html.append(" (").append(s.matchId());
                if (s.matchCategory() != null && !s.matchCategory().isBlank()) {
                    html.append(" · ").append(s.matchCategory());
                }
                html.append(")");
            }
            html.append("</span>");

            // DÜŞÜK GÜVEN etiketi
            if (s.lowConfidence()) {
                html.append("<span style='display:inline-block;font-size:11px;font-weight:600;")
                    .append("padding:4px 8px;border-radius:999px;")
                    .append("border:1px solid #fde68a;color:#92400e;background:#fef3c7;'>")
                    .append("Düşük güven")
                    .append("</span>");
            }

            // KATEGORİ UYUŞMAZLIĞI etiketi
            if (s.categoryMismatch()) {
                html.append("<span style='display:inline-block;font-size:11px;font-weight:600;")
                    .append("padding:4px 8px;border-radius:999px;")
                    .append("border:1px solid #fde68a;color:#92400e;background:#fff7ed;'>")
                    .append("⚠ Kategori uyuşmazlığı")
                    .append("</span>");
            }

            html.append("</div>");

            // Küçük açıklama satırı
            if (s.lowConfidence() || s.categoryMismatch()) {
                html.append("<div style='margin-top:4px;font-size:11px;color:#64748b;'>");
                if (s.lowConfidence()) {
                    html.append("Benzerlik eşik değerin altında; heuristik kategori korunuyor. ");
                }
                if (s.categoryMismatch()) {
                    html.append("AI kategorisi ile kural kategorisi uyuşmuyor; elle doğrulayın.");
                }
                html.append("</div>");
            }
        }

        html.append("</div>"); // .ai-suggestion kapat
        }   

}
