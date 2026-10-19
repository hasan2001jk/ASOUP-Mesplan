package tj.railway.mesplan.file;

import tj.railway.mesplan.entity.Order;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Сборка ответного файла: каждой заявке из базы — её строка исходного файла.
 *
 * Строка находится по ключу «администрация · № заявки · № строки · порядковый №»
 * (в архиве 2018–2026 он уникален в 93 419 файлах из 93 422; при повторе —
 * по порядку появления). Нет исходного файла или строки — заявка пишется
 * в стандартной ширине.
 */
public final class AnswerFile {

    private AnswerFile() {
    }

    /** Текст ответного файла (без кодирования): идентификатор и строки, CRLF. */
    public static String build(String identifier, List<Order> orders, String originalText) {
        Map<String, List<String>> byKey = new HashMap<>();
        if (originalText != null) {
            for (String l : originalText.split("\n")) {
                l = l.endsWith("\r") ? l.substring(0, l.length() - 1) : l;
                if (l.trim().isEmpty() || "PR".indexOf(Character.toUpperCase(l.charAt(0))) >= 0) {
                    continue;
                }
                String[] t = l.split("\\^", -1);
                if (t.length >= 9) {
                    byKey.computeIfAbsent(key(t[0], t[6], t[7], t[8]), k -> new ArrayList<>()).add(l);
                }
            }
        }
        Map<String, Integer> used = new HashMap<>();
        StringBuilder sb = new StringBuilder(identifier).append("\r\n");
        for (Order o : orders) {
            String k = key(str(o.getAdm()), o.getOrderno(), str(o.getStrorderno()), str(o.getIstrorderno()));
            List<String> candidates = byKey.get(k);
            int n = used.merge(k, 1, Integer::sum) - 1;
            String line = candidates != null && n < candidates.size()
                    ? AnswerFileWriter.line(o, candidates.get(n))
                    : AnswerFileWriter.line(o);
            sb.append(line).append("\r\n");
        }
        return sb.toString();
    }

    private static String key(String adm, String orderno, String str, String istr) {
        return trimZero(adm) + "|" + (orderno == null ? "" : orderno.trim()) + "|" + trimZero(str) + "|" + trimZero(istr);
    }

    /** Числовую часть ключа сравниваем как число: «01» и «1» — одно и то же. */
    private static String trimZero(String v) {
        if (v == null) {
            return "0";
        }
        String t = v.trim();
        if (t.isEmpty()) {
            return "0";
        }
        try {
            return String.valueOf(Long.parseLong(t));
        } catch (NumberFormatException e) {
            return t;
        }
    }

    private static String str(Integer v) {
        return v == null ? "" : String.valueOf(v);
    }
}
