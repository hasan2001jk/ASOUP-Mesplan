package tj.railway.mesplan.file;

import tj.railway.mesplan.entity.Order;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
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

    /** Что получилось: текст, сколько строк ушло в ответ, сколько пропущено и почему, что мешает. */
    public static final class Built {
        public final String text;
        public final int lines;
        /** Строки только для сведения (вид 3, 4, 5, 6, 11, 17, чужие 2s…) — в ответ не идут. */
        public final int informational;
        /** Строки, на которые наша администрация ещё не ответила (результат 0), — в ответ не идут. */
        public final int unanswered;
        /** Проблемы по строкам; не пусто — файл отправлять нельзя (сеть отклонила бы эти строки). */
        public final List<String> problems;

        Built(String text, int lines, int informational, int unanswered, List<String> problems) {
            this.text = text;
            this.lines = lines;
            this.informational = informational;
            this.unanswered = unanswered;
            this.problems = problems;
        }
    }

    /**
     * Все заявки строками исходного файла, без правил ответа: вид строки и состав — как пришли,
     * меняются только поля, изменённые в базе. Это проверка верности записи (ArchiveRoundTripTest:
     * без правок файл выходит байт в байт); оператору идёт answer(…).
     */
    public static String build(String identifier, List<Order> orders, String originalText) {
        Map<String, List<String>> byKey = index(originalText);
        Map<String, Integer> used = new HashMap<>();
        StringBuilder sb = new StringBuilder(identifier).append("\r\n");
        for (Order o : orders) {
            String source = sourceOf(o, byKey, used);
            sb.append(source != null ? AnswerFileWriter.line(o, source) : AnswerFileWriter.line(o)).append("\r\n");
        }
        return sb.toString();
    }

    private static Map<String, List<String>> index(String originalText) {
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
        return byKey;
    }

    /** Строка исходного файла для заявки; при повторе ключа — по порядку появления. */
    private static String sourceOf(Order o, Map<String, List<String>> byKey, Map<String, Integer> used) {
        String k = key(str(o.getAdm()), o.getOrderno(), str(o.getStrorderno()), str(o.getIstrorderno()));
        List<String> candidates = byKey.get(k);
        int n = used.merge(k, 1, Integer::sum) - 1;
        return candidates != null && n < candidates.size() ? candidates.get(n) : null;
    }

    /**
     * Ответный файл по правилам сети (AnswerRules): в него идут только строки, на которые
     * отвечают (вид 2, 7, 8, 10) и на которые наша администрация уже ответила; «Вид строки»
     * пишется ответный — 2s, 7s, 8s, 10s. Вид определяется по ИСХОДНОЙ строке: если оператор
     * сам поставил в форме «2s», это всё равно ответ на «2».
     */
    public static Built answer(String identifier, List<Order> orders, String originalText, LocalDate today) {
        Map<String, List<String>> byKey = index(originalText);
        Map<String, Integer> used = new HashMap<>();
        StringBuilder sb = new StringBuilder(identifier).append("\r\n");
        int lines = 0;
        int informational = 0;
        int unanswered = 0;
        List<String> problems = new ArrayList<>();
        for (Order o : orders) {
            String source = sourceOf(o, byKey, used);
            String[] tokens = source == null ? null : source.split("\\^", -1);
            String kind = AnswerRules.answerKind(tokens != null && tokens.length > 2 ? tokens[2] : o.getStrview());
            if (kind == null && source == null) {
                // Исходной строки нет, а оператор уже поставил ответный вид («2s») — принимаем его.
                String v = o.getStrview() == null ? "" : o.getStrview().trim().toLowerCase();
                kind = Arrays.asList("2s", "7s", "8s", "10s").contains(v) ? v : null;
            }
            if (kind == null) {
                informational++;
                continue;
            }
            if (!AnswerRules.answered(o)) {
                unanswered++;
                continue;
            }
            Order original = null;
            if (source != null) {
                try {
                    original = OrderFileParser.parseLine(source, 0, today);
                } catch (OrderFormatException e) {
                    original = null;
                }
            }
            String where = "заявка " + o.getOrderno() + ", строка " + o.getStrorderno() + ": ";
            for (String p : AnswerRules.problems(o, kind, original)) {
                problems.add(where + p);
            }
            String line = source != null ? AnswerFileWriter.line(o, source) : AnswerFileWriter.line(o);
            String[] out = line.split("\\^", -1);
            out[2] = kind;
            sb.append(String.join("^", out)).append("\r\n");
            lines++;
        }
        return new Built(sb.toString(), lines, informational, unanswered, problems);
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
