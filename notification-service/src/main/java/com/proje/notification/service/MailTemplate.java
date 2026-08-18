package com.proje.notification.service;

/**
 * Mail govdesi icin HTML kabugu.
 *
 * NEDEN ELLE HTML: iki mail turu icin sablon motoru eklemek, ogrenilecek
 * ikinci bir API ve surdurulecek ikinci bir bagimlilik demekti. Projenin
 * kurali burada da uygulandi: karsiligi varsa yeni bagimlilik eklenmez.
 *
 * NEDEN TABLO YERLESIMI: mail istemcileri (ozellikle Outlook) flexbox ve grid
 * desteklemez, cogu `<style>` blogunu da siler. Bu bir gerileme degil, ortamin
 * kendisi: bicim satir ici (inline) yazilir ve yerlesim tabloyla kurulur.
 * Yerlesim tablolari `role="presentation"` tasir, yoksa ekran okuyucu bunlari
 * VERI tablosu sanip satir sutun okur.
 *
 * NEDEN UZAK GORSEL YOK: istemcilerin cogu uzak gorselleri varsayilan olarak
 * engeller, dolayisiyla logo cogu kullanicida hic gorunmezdi; ustelik uzak bir
 * gorsel "bu mail acildi" sinyali sizdirir. Marka isareti bu yuzden renkli bir
 * hucre ve iki harf. Ayni gerekce yazi tipini kendi sunucumuzdan servis etme
 * kararinin kardesi.
 *
 * RENKLER OLCULDU (beyaz kart uzerinde): baslik 15,25:1, govde 9,44:1, sonuk
 * 5,33:1, buton zemini 6,46:1. Acik kil (#C8937E) BILEREK kullanilmadi --
 * beyaz uzerinde 2,65:1 veriyor ve grafik ogeler icin gereken 3:1'i tutmuyor.
 */
final class MailTemplate {

    private static final String CLAY = "#8A4F35";
    private static final String INK = "#1E2631";
    private static final String BODY = "#3D4753";
    private static final String MUTED = "#626C79";
    private static final String PAGE = "#F4F1EE";
    private static final String CARD = "#FFFFFF";
    private static final String LINE = "#E4DED8";

    private MailTemplate() {
    }

    /**
     * HTML'e giren her degeri kacirir.
     *
     * Arayuzde React bunu kendiliginden yapiyordu; burada isaretlemeyi ELLE
     * kurdugumuz icin sorumluluk bizde. Kacirilmazsa `&` iceren bir departman
     * adi ya da `<` iceren bir unvan duzeni bozar.
     */
    static String escape(String raw) {
        if (raw == null) {
            return "";
        }

        return raw.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /** Bir paragraf. Metin kacirilir. */
    static String paragraph(String text) {
        return """
                <p style="margin:0 0 16px;font-size:15px;line-height:1.6;color:%s;">%s</p>
                """.formatted(BODY, escape(text));
    }

    /** Kucuk punto, sonuk not. */
    static String note(String text) {
        return """
                <p style="margin:0 0 16px;font-size:13px;line-height:1.6;color:%s;">%s</p>
                """.formatted(MUTED, escape(text));
    }

    /**
     * Etiket/deger satirlari.
     *
     * Bu GERCEK bir veri tablosudur, yerlesim tablosu degil; o yuzden
     * `role="presentation"` YOK ve etiket sutunu `<th scope="row">`.
     */
    static String facts(String... labelsAndValues) {
        if (labelsAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("facts() expects label/value pairs");
        }

        StringBuilder rows = new StringBuilder();

        for (int i = 0; i < labelsAndValues.length; i += 2) {
            rows.append("""
                    <tr>
                      <th scope="row" align="left" style="padding:6px 16px 6px 0;font-size:13px;
                          font-weight:400;color:%s;white-space:nowrap;vertical-align:top;">%s</th>
                      <td style="padding:6px 0;font-size:14px;color:%s;vertical-align:top;">%s</td>
                    </tr>
                    """.formatted(MUTED, escape(labelsAndValues[i]), INK, escape(labelsAndValues[i + 1])));
        }

        return """
                <table cellpadding="0" cellspacing="0" border="0"
                       style="margin:0 0 20px;border-top:1px solid %s;border-bottom:1px solid %s;
                              padding:6px 0;width:100%%;">
                  %s
                </table>
                """.formatted(LINE, LINE, rows);
    }

    /**
     * Birincil eylem dugmesi.
     *
     * Baglanti METNI de yazilir (`fallback`): bazi istemciler dugmeyi
     * bicimlendirmeden duz bir baglantiya indirger ve kullanici nereye
     * gittigini gormelidir. Kimlik avina karsi da dogru davranis budur.
     */
    static String button(String url, String label) {
        String safeUrl = escape(url);

        return """
                <table role="presentation" cellpadding="0" cellspacing="0" border="0" style="margin:0 0 20px;">
                  <tr>
                    <td bgcolor="%s" style="border-radius:8px;">
                      <a href="%s" style="display:inline-block;padding:12px 22px;font-size:15px;
                         font-weight:600;color:#FFFFFF;text-decoration:none;border-radius:8px;">%s</a>
                    </td>
                  </tr>
                </table>
                <p style="margin:0 0 16px;font-size:12px;line-height:1.5;color:%s;word-break:break-all;">
                  If the button does not work, copy this address:<br>%s
                </p>
                """.formatted(CLAY, safeUrl, escape(label), MUTED, safeUrl);
    }

    /** Icerigi kabuga yerlestirir ve tam bir HTML belgesi dondurur. */
    static String page(String heading, String content) {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width,initial-scale=1">
                  <title>%s</title>
                </head>
                <body style="margin:0;padding:0;background:%s;">
                  <table role="presentation" cellpadding="0" cellspacing="0" border="0"
                         style="width:100%%;background:%s;">
                    <tr>
                      <td align="center" style="padding:28px 12px;">

                        <table role="presentation" cellpadding="0" cellspacing="0" border="0"
                               style="width:100%%;max-width:560px;background:%s;border:1px solid %s;
                                      border-radius:12px;font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;">
                          <tr>
                            <td style="padding:22px 28px 0;">
                              <table role="presentation" cellpadding="0" cellspacing="0" border="0">
                                <tr>
                                  <td bgcolor="%s" width="30" height="30"
                                      style="border-radius:7px;color:#FFFFFF;font-size:13px;
                                             font-weight:700;text-align:center;">HR</td>
                                  <td style="padding-left:10px;font-size:13px;font-weight:600;color:%s;">
                                    HR System
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>

                          <tr>
                            <td style="padding:18px 28px 0;">
                              <h1 style="margin:0 0 14px;font-size:20px;line-height:1.35;color:%s;">%s</h1>
                            </td>
                          </tr>

                          <tr>
                            <td style="padding:0 28px 6px;">%s</td>
                          </tr>

                          <tr>
                            <td style="padding:6px 28px 24px;border-top:1px solid %s;">
                              <p style="margin:14px 0 0;font-size:12px;line-height:1.5;color:%s;">
                                This message was sent automatically. Please do not reply.
                              </p>
                            </td>
                          </tr>
                        </table>

                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(escape(heading), PAGE, PAGE, CARD, LINE, CLAY, INK, INK,
                escape(heading), content, LINE, MUTED);
    }
}
