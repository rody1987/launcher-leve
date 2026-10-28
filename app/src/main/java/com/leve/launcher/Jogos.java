 package com.leve.launcher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
            {"bra.1", "BRA"}, {"bra.2", "BRA B"}, {"bra.copa_do_brazil", "COPA BR"},
            {"conmebol.libertadores", "LIBERT"}, {"conmebol.sudamericana", "SUL-AM"},
            {"arg.1", "ARG"}, {"col.1", "COL"}, {"chi.1", "CHI"}, {"uru.1", "URU"},
            {"uefa.champions", "UCL"}, {"uefa.europa", "UEL"}, {"uefa.europa.conf", "UECL"},
            {"eng.1", "ING"}, {"esp.1", "ESP"}, {"ita.1", "ITA"}, {"ger.1", "ALE"},
            {"fra.1", "FRA"}, {"por.1", "POR"}, {"ned.1", "HOL"}
    };

    /** Sempre retorna uma lista. Se algo falhar, vem um item de diagnóstico (liga == null). */
    static List<Jogo> buscar() {
        ArrayList<Jogo> out = new ArrayList<>();
        SimpleDateFormat ymd = new SimpleDateFormat("yyyyMMdd", Locale.US); // fuso local
        SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.US);
        SimpleDateFormat iso1 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US);
        SimpleDateFormat iso2 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        iso1.setTimeZone(TimeZone.getTimeZone("UTC"));
        iso2.setTimeZone(TimeZone.getTimeZone("UTC"));
        String hoje = ymd.format(new Date());

        int ok = 0, recebidos = 0;
        String erro = null;

        for (String[] l : LIGAS) {
            String body;
            try {
                body = baixar("https://site.api.espn.com/apis/site/v2/sports/soccer/"
                        + l[0] + "/scoreboard");
                ok++;
            } catch (Exception e) {
                if (erro == null) erro = l[0] + " " + e.getClass().getSimpleName() + ": " + e.getMessage();
                continue;
            }
            try {
                JSONArray ev = new JSONObject(body).optJSONArray("events");
                if (ev == null) continue;
                for (int i = 0; i < ev.length(); i++) {
                    recebidos++;
                    try {
                        JSONObject e = ev.getJSONObject(i);
                        String ds = e.getString("date");
                        Date d;
                        try { d = iso1.parse(ds); } catch (ParseException pe) { d = iso2.parse(ds); }
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
                    } catch (Exception ex) {
                        if (erro == null) erro = l[0] + " leitura " + ex.getClass().getSimpleName()
                                + ": " + ex.getMessage();
                    }
                }
            } catch (Exception ex) {
                if (erro == null) erro = l[0] + " json " + ex.getClass().getSimpleName()
                        + ": " + ex.getMessage();
            }
        }

        if (ok == 0) {
            return info("Sem resposta da ESPN (0/" + LIGAS.length + " ligas). "
                    + (erro == null ? "" : erro));
        }
        Collections.sort(out, new Comparator<Jogo>() {
            @Override public int compare(Jogo a, Jogo b) {
                if (a.estado != b.estado) return a.estado - b.estado;
                return Long.compare(a.inicio, b.inicio);
            }
        });
        if (out.isEmpty()) {
            return info("Sem jogos hoje (ligas ok " + ok + "/" + LIGAS.length
                    + ", eventos lidos " + recebidos + ")"
                    + (erro == null ? "" : ". Aviso: " + erro));
        }
        return out;
    }

    private static List<Jogo> info(String texto) {
        ArrayList<Jogo> l = new ArrayList<>();
        Jogo j = new Jogo();
        j.detalhe = texto; // liga == null indica item de diagnóstico
        j.estado = 1;
        l.add(j);
        return l;
    }

    static String linha(Jogo j) {
        if (j.liga == null) return j.detalhe;
        return "[" + j.liga + "] " + j.detalhe + " · " + j.casa + " " + j.placar + " " + j.fora;
    }

    private static String baixar(String u) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(6000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0");
        c.setRequestProperty("Accept", "application/json");
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
