package tj.railway.mesplan.file;

import tj.railway.mesplan.entity.Order;
import tj.railway.mesplan.entity.Orderdetail;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Ответный файл заявок — как его пишет старая программа (mesplan2016, order.pas,
 * Torders_form.FormClose): первая строка — идентификатор файла, затем по строке на
 * заявку, кодировка CP866, конец строки CRLF.
 *
 * Строка заявки — те же поля и в том же порядке, что во входящем файле; у каждой
 * ж/д администрации после кода экспедитора и двух полей доп. информации — результат
 * и причина (при «Виде строки» 001 — без них). Последнего «^» нет — как у order.pas.
 *
 * Старая программа хранила каждое поле текстом и писала его как пришло. У нас
 * числа лежат в числовых колонках, и ведущие нули теряются — поэтому ширина каждого
 * поля взята по архиву 2018–2026 (193 тыс. заявок): та, что встречается почти
 * всегда (нетто — 5 знаков с нулями, вагоны — 3, код экспедитора — 20 с пробелами
 * справа, …). AnswerFileWriterTest считает, сколько строк архива выходят байт
 * в байт после разбора и записи; ArchiveRoundTripTest — то же на всём архиве.
 */
public final class AnswerFileWriter {

    private static final DateTimeFormatter DDMMYYYY = DateTimeFormatter.ofPattern("ddMMyyyy");

    private AnswerFileWriter() {
    }

    /** Весь файл: идентификатор и заявки (в том порядке, в каком переданы). */
    public static String file(String identifier, List<Order> orders) {
        StringBuilder sb = new StringBuilder(identifier).append("\r\n");
        for (Order o : orders) {
            sb.append(line(o)).append("\r\n");
        }
        return sb.toString();
    }

    /** Строка одной заявки — все поля в стандартной ширине (оригинала нет). */
    public static String line(Order o) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < OrderFileParser.FIELDS.size(); i++) {
            if (i > 0) {
                s.append('^');
            }
            s.append(orderField(o, i));
        }
        boolean withResult = OrderFileParser.groupSize(o.getStrview()) == OrderFileParser.DETAIL_FIELDS.size();
        for (Orderdetail d : o.getOrderdetails()) {
            for (int j = 0; j < (withResult ? 8 : 6); j++) {
                s.append('^').append(detailField(d, j));
            }
        }
        return s.toString();
    }

    /**
     * Строка заявки на основе строки исходного файла (как order.pas: поле пишется
     * так, как пришло). Поле меняется, только если значение в базе другое — обычно
     * это результат и причина отказа нашей администрации. Изменённое поле — в
     * стандартной ширине (orderField / detailField).
     */
    public static String line(Order o, String original) {
        String[] t = original.split("\\^", -1);
        int n = OrderFileParser.FIELDS.size();
        int group = OrderFileParser.groupSize(o.getStrview());
        if (t.length < n) {
            return line(o);
        }
        List<String> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            OrderFileParser.Field f = OrderFileParser.FIELDS.get(i);
            Object db = OrderFileParser.read(o, f.name);
            if (f.name.equals("admcount")) {
                db = (short) o.getOrderdetails().size();
            }
            out.add(same(f, t[i], db) ? t[i] : orderField(o, i));
        }
        List<Orderdetail> details = o.getOrderdetails();
        int origValues = t.length - n;
        int origGroups = (origValues + (group == 8 ? 1 : 0)) / group;   // последняя причина может отсутствовать
        boolean sameShape = origGroups == details.size();
        for (int g = 0; sameShape && g < details.size(); g++) {
            Object adm = details.get(g).getAdm();
            sameShape = same(OrderFileParser.DETAIL_FIELDS.get(0), t[n + g * group], adm);
        }
        for (int g = 0; g < details.size(); g++) {
            Orderdetail d = details.get(g);
            for (int j = 0; j < group; j++) {
                int k = n + g * group + j;
                String fresh = detailField(d, j);
                if (sameShape && k < t.length) {
                    OrderFileParser.Field f = OrderFileParser.DETAIL_FIELDS.get(j);
                    out.add(same(f, t[k], OrderFileParser.read(d, f.name)) ? t[k] : fresh);
                } else if (sameShape && j == group - 1 && fresh.isEmpty()) {
                    // В исходной строке не было даже пустой последней причины — не добавляем.
                    continue;
                } else {
                    out.add(fresh);
                }
            }
        }
        return String.join("^", out);
    }

    /** Значение поля в исходном тексте равно значению в базе? */
    private static boolean same(OrderFileParser.Field f, String raw, Object db) {
        Object parsed;
        try {
            parsed = OrderFileParser.convert(f, raw, 0, null);
        } catch (OrderFormatException e) {
            return false;
        }
        if (db == null) {
            // Пустое число разбирается в 0, пустой текст — в "": null в базе — то же самое.
            return parsed == null || "".equals(parsed) || Integer.valueOf(0).equals(parsed)
                    || Short.valueOf((short) 0).equals(parsed);
        }
        return db.equals(parsed);
    }

    /** Поле заявки с номером i (0…34) в стандартной ширине. */
    static String orderField(Order o, int i) {
        switch (i) {
            case 0: return plain(o.getAdm());
            case 1: return str(o.getFtype());
            case 2: return str(o.getStrview());
            case 3: return o.getDatepogr() == null ? "" : o.getDatepogr().format(DDMMYYYY);
            case 4: return plain(o.getTransid());
            case 5: return zeros(o.getGosotpr(), 3);
            case 6: return str(o.getOrderno());
            case 7: return plain(o.getStrorderno());
            case 8: return plain(o.getIstrorderno());
            case 9: return zeros(o.getCodengr(), 2);
            case 10: return str(o.getOtprname());
            case 11: return str(o.getPoluchname());
            case 12: return zeros(o.getStotpr(), 6);
            case 13: return zeros(o.getStnazn(), 6);
            case 14: return zeros(o.getEtsng(), 6);          // order.pas: fillstrto(etsng, 6)
            case 15: return str(o.getGng());
            case 16: return zeros(o.getStrnazn(), 3);
            case 17: return str(o.getCodegrpolport());
            case 18: return str(o.getNamegrpolport());
            case 19: return str(o.getCodeport());
            case 20: return plain(o.getRodvag());
            case 21: return plain(o.getPrinadl());
            case 22: return zeros(o.getNetto(), 5);
            case 23: return zeros(o.getVags(), 3);
            case 24: return str(o.getPrim());
            case 25: return plain(o.getRodotpr());
            case 26: return zeros(o.getOtprcode(), 4);
            case 27: return zeros(o.getPoluchcode(), 4);
            case 28: return str(o.getRezerv1());
            case 29: return str(o.getRezerv2());
            case 30: return str(o.getRezerv3());
            case 31: return zeros(o.getContainers(), 7);
            // Типоразмер контейнера: в 99 % строк — четыре пробела (контейнеров нет).
            case 32: return o.getConttr() == null || o.getConttr() == 0 ? "    " : zeros(o.getConttr(), 4);
            case 33: return o.getContpr() == null || o.getContpr() == 0 ? "" : plain(o.getContpr());
            case 34: return String.valueOf(o.getOrderdetails().size());   // «Кол-во адм» — по числу строк
            default: throw new IllegalArgumentException("поле " + i);
        }
    }

    /** Поле администрации с номером j (0…7) в стандартной ширине. */
    static String detailField(Orderdetail d, int j) {
        switch (j) {
            case 0: return plain(d.getAdm());
            case 1: return zeros(d.getGate(), 6);
            case 2: return str(d.getExpname());
            case 3: return rightPad(plain(d.getExpcode()), 20);
            case 4: return str(d.getInfo1());
            case 5: return str(d.getInfo2());
            case 6: return plain(d.getResult());
            case 7: return str(d.getDenyreason());
            default: throw new IllegalArgumentException("поле администрации " + j);
        }
    }

    /**
     * Идентификатор ответного файла — как кнопка «Сформировать» в order.pas:
     * буквы входящего («PL_K») + «66» + месяц + «-ГГГГ-ММ-ДД-чч.мм.сс.» + миллисекунды
     * (не меньше двух цифр) + «123», дополнено нулями до 31 знака после букв,
     * + «_NV_2013.1». Время — местное минус сдвиг (в order.pas — 1 ч по умолчанию).
     *
     * @param incoming идентификатор входящего файла («PL_K6610-…»), может быть null
     * @param month    месяц плана, 1–12 (в order.pas — выбор оператора)
     */
    public static String identifier(String incoming, int month, LocalDateTime localNow, int hourShift) {
        String prefix = "PL_K";
        if (incoming != null) {
            int i = 0;
            while (i < incoming.length() && !Character.isDigit(incoming.charAt(i))) {
                i++;
            }
            if (i > 0) {
                prefix = incoming.substring(0, i);
            }
        }
        LocalDateTime t = localNow.minusHours(hourShift);
        String s = "66" + two(month) + "-" + t.getYear() + "-" + two(t.getMonthValue()) + "-" + two(t.getDayOfMonth())
                + "-" + two(t.getHour()) + "." + two(t.getMinute()) + "." + two(t.getSecond()) + "."
                + two(t.getNano() / 1_000_000) + "123";
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < 31) {
            sb.append('0');
        }
        return prefix + sb + "_NV_2013.1";
    }

    private static String two(int v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }

    private static String str(String v) {
        return v == null ? "" : v;
    }

    private static String plain(Number v) {
        return v == null ? "" : String.valueOf(v);
    }

    static String zeros(Number v, int width) {
        if (v == null) {
            return "";
        }
        StringBuilder s = new StringBuilder(String.valueOf(v));
        while (s.length() < width) {
            s.insert(0, '0');
        }
        return s.toString();
    }

    private static String rightPad(String v, int width) {
        StringBuilder s = new StringBuilder(v);
        while (s.length() < width) {
            s.append(' ');
        }
        return s.toString();
    }
}
