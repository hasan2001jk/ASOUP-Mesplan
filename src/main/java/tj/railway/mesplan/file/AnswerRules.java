package tj.railway.mesplan.file;

import tj.railway.mesplan.entity.Order;
import tj.railway.mesplan.entity.Orderdetail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Правила ответа нашей администрации (66) на строки заявок — по документации АС «МЕСПЛАН»
 * (docs/АС МЕСПЛАН: Приложения В и Г к Доп. ТЗ, «Порядок заполнения полей» с изм. 08.2025)
 * и по квитанциям сети на 10 782 ответа старой программы (docs/АНАЛИЗ-И-ПЛАН.md, §2г).
 *
 * <ul>
 *   <li>Ответ — с «Видом строки» 2 → 2s, 7 → 7s, 8 → 8s, 10 → 10s. Строки, отправленные
 *       с видом «2» без «s», сеть отклонила 901 раз из 925 (0133 «Неизвестный вид строки»).</li>
 *   <li>Остальные виды (3, 4, 5, 6 — итог согласования; 11 — отзыв; 17 — без результата;
 *       2s/7s/… — чужие ответы) — только для сведения: в ответ не идут.</li>
 *   <li>Результат: для 2s — 1, 2 или 3; для 7s, 8s, 10s — только 1 или 2.
 *       При 2 и 3 причина обязательна. Если 66 в маршруте несколько раз — результат одинаковый.</li>
 *   <li>Менять можно только стык своей администрации и количество тонн, вагонов, контейнеров —
 *       и только в сторону уменьшения (такая правка — это результат 3). В повторном согласовании
 *       (7s, 8s, 10s) менять нельзя ничего.</li>
 * </ul>
 * В скобках у каждой проблемы — код ошибки сети, которой она обернулась бы в квитанции.
 */
public final class AnswerRules {

    /** Наша ж/д администрация в нумерации МЕСПЛАН — Таджикская железная дорога. */
    public static final int OUR_ADM = 66;

    /**
     * Поля, которые ответ не может менять: коды, станции, даты. Текстовые поля (наименования,
     * примечание, экспедитор) не сравниваются: старая программа портила их при записи (обрезка
     * пробелов, кавычки при DOS↔Windows), и сеть такие ответы принимала — 1 900 раз из 1 900
     * (ArchiveAnswerRulesTest). Наш ответ их не трогает, пока оператор сам не изменит.
     */
    private static final List<String> IDENTITY = Arrays.asList(
            "adm", "ftype", "datepogr", "transid", "gosotpr", "orderno", "strorderno", "istrorderno", "codengr",
            "stotpr", "stnazn", "etsng", "gng", "strnazn", "codeport", "rodvag", "prinadl", "rodotpr",
            "conttr", "contpr");

    private static final Set<String> VOLUMES = new HashSet<>(Arrays.asList("netto", "vags", "containers"));

    private AnswerRules() {
    }

    /**
     * «Вид строки» ответа на пришедшую строку; null — на такую строку не отвечают.
     * Ведущие нули отбрасываются: «002» — то же, что «2».
     */
    public static String answerKind(String incoming) {
        if (incoming == null) {
            return null;
        }
        String v = incoming.trim().toLowerCase();
        while (v.length() > 1 && v.charAt(0) == '0') {
            v = v.substring(1);
        }
        switch (v) {
            case "2": return "2s";
            case "7": return "7s";
            case "8": return "8s";
            case "10": return "10s";
            default: return null;
        }
    }

    public static Set<Short> allowedResults(String answerKind) {
        return "2s".equals(answerKind)
                ? new HashSet<>(Arrays.asList((short) 1, (short) 2, (short) 3))
                : new HashSet<>(Arrays.asList((short) 1, (short) 2));
    }

    /** Наши элементы маршрута (администрация 66). */
    public static List<Orderdetail> ours(Order o) {
        List<Orderdetail> out = new ArrayList<>();
        for (Orderdetail d : o.getOrderdetails()) {
            if (d.getAdm() != null && d.getAdm() == OUR_ADM) {
                out.add(d);
            }
        }
        return out;
    }

    /** Есть ли у строки наш ответ (результат не пуст и не 0 хотя бы в одном нашем элементе). */
    public static boolean answered(Order o) {
        for (Orderdetail d : ours(o)) {
            if (d.getResult() != null && d.getResult() != 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Проблемы, из-за которых сеть отклонила бы ответ на эту строку. Пусто — можно отправлять.
     *
     * @param kind     «Вид строки» ответа (answerKind)
     * @param original строка в том виде, как пришла (разобранная), или null — тогда правки
     *                 не с чем сравнить и проверяются только результат и причина
     */
    public static List<String> problems(Order o, String kind, Order original) {
        List<String> out = new ArrayList<>();
        List<Orderdetail> ours = ours(o);
        if (ours.isEmpty()) {
            out.add("нашей администрации (66) нет в маршруте (0119)");
            return out;
        }
        Set<Short> results = new HashSet<>();
        Set<String> reasons = new HashSet<>();
        for (Orderdetail d : ours) {
            results.add(d.getResult() == null ? 0 : d.getResult());
            reasons.add(trim(d.getDenyreason()));
        }
        if (results.size() > 1) {
            out.add("администрация 66 стоит в маршруте " + ours.size() + " раза, а результаты разные " + results
                    + " — должен быть одинаковый (0145)");
            return out;
        }
        short result = results.iterator().next();
        if (!allowedResults(kind).contains(result)) {
            out.add("результат " + result + " для вида строки " + kind + " недопустим — можно "
                    + sorted(allowedResults(kind)) + ("2s".equals(kind) ? "" : " (повторное согласование)") + " (0104)");
        }
        if ((result == 2 || result == 3) && reasons.contains("")) {
            out.add("при результате " + result + " нужна причина (" + (result == 2 ? "0105" : "0106") + ")");
        }
        for (String r : reasons) {
            if (r.length() > 100) {
                out.add("причина длиннее 100 знаков (0136)");
            }
        }
        if (o.getNetto() != null && o.getVags() != null && o.getNetto() < o.getVags()) {
            out.add("тонн (" + o.getNetto() + ") меньше, чем вагонов (" + o.getVags() + ") (0073)");
        }
        if (o.getNetto() != null && o.getNetto() == 0) {
            out.add("ноль тонн (0071)");
        }
        if (o.getVags() != null && o.getVags() == 0) {
            out.add("ноль вагонов (0023)");
        }
        if (original == null) {
            return out;
        }

        boolean corrected = false;
        for (OrderFileParser.Field f : OrderFileParser.FIELDS) {
            if (!IDENTITY.contains(f.name)) {
                continue;
            }
            if (!same(OrderFileParser.read(o, f.name), OrderFileParser.read(original, f.name))) {
                out.add("изменено поле «" + f.label + "» — ответ может менять только стык своей администрации "
                        + "и тонны, вагоны, контейнеры (" + codeFor(f.name) + ")");
            }
        }
        for (String v : VOLUMES) {
            Integer now = (Integer) OrderFileParser.read(o, v);
            Integer was = (Integer) OrderFileParser.read(original, v);
            if (Objects.equals(nz(now), nz(was))) {
                continue;
            }
            corrected = true;
            if (nz(now) > nz(was)) {
                out.add("«" + label(v) + "» увеличено с " + nz(was) + " до " + nz(now)
                        + " — можно только уменьшить (" + ("netto".equals(v) ? "0112" : "vags".equals(v) ? "0113" : "0143") + ")");
            }
        }
        List<Orderdetail> now = o.getOrderdetails();
        List<Orderdetail> was = original.getOrderdetails();
        if (now.size() != was.size()) {
            out.add("изменено число администраций в маршруте: было " + was.size() + ", стало " + now.size() + " (0086)");
            return out;
        }
        for (int i = 0; i < now.size(); i++) {
            Orderdetail a = now.get(i);
            Orderdetail b = was.get(i);
            String where = "администрация " + (i + 1) + " (" + b.getAdm() + ")";
            if (!Objects.equals(a.getAdm(), b.getAdm())) {
                out.add(where + ": изменён код или порядок администраций (0108)");
                continue;
            }
            boolean mine = a.getAdm() != null && a.getAdm() == OUR_ADM;
            if (!same(a.getGate(), b.getGate())) {
                if (mine) {
                    corrected = true;
                } else {
                    out.add(where + ": изменён стык чужой администрации (0102)");
                }
            }
        }
        if (corrected && !"2s".equals(kind)) {
            out.add("в повторном согласовании (" + kind + ") ничего менять нельзя — только ответить 1 или 2 (0118)");
        }
        return out;
    }

    private static String codeFor(String field) {
        switch (field) {
            case "etsng": return "0080";
            case "strnazn": return "0081";
            case "poluchname": return "0082";
            case "rodvag": return "0083";
            case "prinadl": return "0084";
            case "codeport": return "0085";
            case "codegrpolport": return "0088";
            case "namegrpolport": return "0089";
            case "adm": return "0091";
            case "gosotpr": return "0092";
            case "stotpr": return "0093";
            case "codengr": return "0094";
            case "transid": return "0095";
            case "otprname": return "0096";
            case "stnazn": return "0099";
            case "datepogr": return "0144";
            default: return "0137";
        }
    }

    private static String label(String field) {
        for (OrderFileParser.Field f : OrderFileParser.FIELDS) {
            if (f.name.equals(field)) {
                return f.label;
            }
        }
        return field;
    }

    /** Пустое число, 0 и пустой текст — одно и то же «ничего»: так их пишет и читает файл. */
    private static boolean same(Object a, Object b) {
        return Objects.equals(norm(a), norm(b));
    }

    private static Object norm(Object v) {
        if (v == null) {
            return "";
        }
        if (v instanceof String) {
            return ((String) v).trim();
        }
        if (v instanceof Number) {
            long n = ((Number) v).longValue();
            return n == 0 ? "" : n;
        }
        return v;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static String trim(String v) {
        return v == null ? "" : v.trim();
    }

    private static List<Short> sorted(Set<Short> s) {
        List<Short> l = new ArrayList<>(s);
        Collections.sort(l);
        return l;
    }
}
