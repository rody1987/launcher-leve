package com.leve.launcher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Busca os jogos do dia na API pública da ESPN (sem chave). */
public class Jogos {

    static class Jogo {
        String liga, casa, fora, placar, detalhe;
        int estado; // 0 = ao vivo, 1 = a começar, 2 = encerrado
        long inicio;
    }

    // {código da ESPN, nome curto}. Adicione ou remova ligas à vontade.
    static final String[][] LIGAS = {
            // Brasil
            {"bra.1", "BRA"}, {"bra.2", "BRA B"}, {"bra.copa_do_brazil", "COPA BR"},
            // América do Sul
            {"conmebol.libertadores", "LIBERT"}, {"conmebol.sudamericana", "SUL-AM"},
            {"arg.1", "ARG"}, {"col.1", "COL"}, {"chi.1", "CHI"}, {"uru.1", "URU"},
            // Europa
            {"uefa.champions", "UCL"}, {"uefa.europa", "UEL"}, {"uefa.europa.conf", "UECL"},
            {"eng.1", "ING"}, {"esp.1", "ESP"}, {"ita.1", "ITA"}, {"ger.1", "ALE"},
            {"fra.1", "FRA"}, {"por.1", "POR"}, {"ned.1", "HOL"}
    };

    /** Retorna os jogos de hoje (horário local) ou null se não conseguiu baixar nada. */
    static List<Jogo> buscar() {
        ArrayList<Jogo> out = new ArrayList<>();
        SimpleDateFormat ymd = new SimpleDateFormat("yyyyMMdd", Locale.US); // fuso local
        SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.US);
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US);
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));

        Calendar c = Calendar.getInstance();
        String hoje = ymd.format(c.getTime());
        c.add(Calendar.DAY_OF_MONTH, -1);
        String faixa = ymd.format(c.getTime()) + "-" + hoje; // cobre diferença de fuso da ESPN

        int sucesso = 0;
        for (String[] l : LIGAS) {
            try {
                String body = baixar("https://site.api.espn.com/apis/site/v2/sports/soccer/"
                        + l[0] + "/scoreboard?dates=" + faixa);
                sucesso++;
                JSONArray ev = new JSONObject(body).optJSONArray("events");
                if (ev == null) continue;
                for (int i = 0; i < ev.length(); i++) {
                    JSONObject e = ev.getJSONObject(i);
                    Date d = iso.parse(e.getString("date"));
                    if (!ymd.format(d).equals(hoje)) continue;

                    JSONArray teams = e.getJSONArray("competitions")
                            .getJSONObject(0).getJSONArray("competitors");
                    Jogo j = new Jogo();
                    j.liga = l[1];
                    j.inicio = d.getTime();
                    String sh = "", sa = "";
                    for (int k = 0; k < teams.length(); k++) {
                        JSONObject t = teams.getJSONObject(k);
                        JSONObject tm = t.getJSONObject("team");
                        String nome = tm.optString("shortDisplayName", tm.optString("displayName", "?"));
                        String sc = t.optString("score", "0");
                        if ("home".equals(t.optString("homeAway"))) { j.casa = nome; sh = sc; }
                        else { j.fora = nome; sa = sc; }
                    }
                    JSONObject st = e.getJSONObject("status").getJSONObject("type");
                    String state = st.optString("state");
                    String nomeSt = st.optString("name");
                    if (nomeSt.contains("POSTPONED") || nomeSt.contains("CANCEL")) {
                        j.estado = 1; j.detalhe = "ADIADO"; j.placar = "x";
                    } else if ("in".equals(state)) {
                        j.estado = 0; j.detalhe = "AO VIVO " + st.optString("shortDetail");
                        j.placar = sh + " x " + sa;
                    } else if ("post".equals(state)) {
                        j.estado = 2; j.detalhe = "FIM"; j.placar = sh + " x " + sa;
                    } else {
                        j.estado = 1; j.detalhe = hm.format(d); j.placar = "x";
                    }
                    out.add(j);
                }
            } catch (Exception ignored) {
                // liga sem jogos, código inválido ou sem rede: segue para a próxima
            }
        }
        if (sucesso == 0) return null;
        Collections.sort(out, new Comparator<Jogo>() {
            @Override public int compare(Jogo a, Jogo b) {
                if (a.estado != b.estado) return a.estado - b.estado;
                return Long.compare(a.inicio, b.inicio);
            }
        });
        return out;
    }

    static String linha(Jogo j) {
        return "[" + j.liga + "] " + j.detalhe + " · " + j.casa + " " + j.placar + " " + j.fora;
    }

    private static String baixar(String u) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(6000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0");
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"), 8192);
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
            r.close();
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }
}
