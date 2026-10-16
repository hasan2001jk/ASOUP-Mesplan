package tj.railway.mesplan.file;

import tj.railway.mesplan.entity.Order;
import tj.railway.mesplan.entity.Orderdetail;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Разбор файла заявок МЕСПЛАН.
 *
 * Формат — тот, что читал исходный MesplanService.getOrder (он же — таблица
 * WORK/WORK1 старой программы mesplan2006):
 *
 *   строка 1        служебная строка, сохраняется в order.header как есть;
 *   строка 2        поля заявки через «^», каждое поле кончается «^»:
 *                   35 полей заявки (FIELDS), затем группы по 8 полей — по одной
 *                   на каждую ж/д администрацию (DETAIL_FIELDS).
 *
 * Отличия от исходного разбора — только там, где он падал или терял данные:
 *  - ошибка формата называет строку, поле и полученное значение (было: ответ «2»);
 *  - длина текста и диапазон tinyint проверяются здесь, а не базой;
 *  - «Причина отказа» последней администрации без завершающего «^»
 *    сохраняется (исходник её молча обнулял);
 *  - конец строки \r\n или \n в конце файла не ломает разбор (исходник уходил
 *    в цикл по «\r\n» и падал).
 * На файлах, которые исходник разбирал без ошибки, результат тот же —
 * это проверяет OrderFileParserTest на замороженной копии исходного кода.
 */
public final class OrderFileParser {

    enum Kind { INT, TINY, TEXT, DATE }

    static final class Field {
        final String name;
        final String label;
        final Kind kind;
        final int max;

        Field(String name, String label, Kind kind, int max) {
            this.name = name;
            this.label = label;
            this.kind = kind;
            this.max = max;
        }
    }

    private static Field f(String name, String label, Kind kind) {
        return new Field(name, label, kind, kind == Kind.TEXT ? 255 : 0);
    }

    private static Field t(String name, String label, int max) {
        return new Field(name, label, Kind.TEXT, max);
    }

    /** Поля заявки в порядке файла. Длины — по колонкам таблицы orders. */
    static final List<Field> FIELDS = Arrays.asList(
            f("adm", "Ж/д администрация", Kind.INT),
            t("ftype", "Тип файла", 2),
            t("strview", "Вид строки", 3),
            f("datepogr", "Дата погрузки", Kind.DATE),
            f("transid", "Вид сообщения", Kind.INT),
            f("gosotpr", "Государство отправления", Kind.INT),
            t("orderno", "№ заявки", 10),
            f("strorderno", "№ строки заявки", Kind.INT),
            f("istrorderno", "Порядковый № строки", Kind.INT),
            f("codengr", "Код номенклатурной группы", Kind.INT),
            t("otprname", "Отправитель", 50),
            t("poluchname", "Получатель", 50),
            f("stotpr", "Станция отправления", Kind.INT),
            f("stnazn", "Станция назначения", Kind.INT),
            f("etsng", "ЕТСНГ", Kind.INT),
            t("gng", "ГНГ", 12),
            f("strnazn", "Государство назначения", Kind.INT),
            t("codegrpolport", "Код получателя в порту", 20),
            t("namegrpolport", "Получатель в порту", 50),
            t("codeport", "Порт", 50),
            f("rodvag", "Род вагона", Kind.INT),
            f("prinadl", "Принадлежность", Kind.TINY),
            f("netto", "Нетто", Kind.INT),
            f("vags", "Вагонов", Kind.INT),
            f("prim", "Примечание", Kind.TEXT),
            f("rodotpr", "Род отправки", Kind.TINY),
            f("otprcode", "Код отправителя", Kind.INT),
            f("poluchcode", "Код получателя", Kind.INT),
            t("rezerv1", "Резерв 1", 20),
            t("rezerv2", "Резерв 2", 20),
            f("rezerv3", "Резерв 3", Kind.TEXT),
            f("containers", "Контейнеров", Kind.INT),
            f("conttr", "Типоразмер контейнера", Kind.INT),
            f("contpr", "Принадлежность контейнера", Kind.TINY),
            f("admcount", "Количество администраций", Kind.TINY));

    /** Поля одной администрации. Длины — по колонкам таблицы orderdetails. */
    static final List<Field> DETAIL_FIELDS = Arrays.asList(
            f("adm", "Ж/д администрация", Kind.INT),
            f("gate", "Стык", Kind.INT),
            t("expname", "Экспедитор", 50),
            f("expcode", "Код экспедитора", Kind.INT),
            t("info1", "Доп. информация 1", 4),
            t("info2", "Доп. информация 2", 50),
            f("result", "Результат", Kind.TINY),
            t("denyreason", "Причина отказа", 100));

    private OrderFileParser() {
    }

    /**
     * @param text  содержимое файла, уже раскодированное (файлы — в CP866)
     * @param today дата загрузки — пишется в dateoper
     */
    public static Order parse(String text, LocalDate today) {
        if (text == null || text.trim().isEmpty()) {
            throw new OrderFormatException("Файл пустой");
        }
        int eol = text.indexOf('\n');
        if (eol < 0) {
            throw new OrderFormatException("В файле одна строка: нет строки с данными заявки после служебной строки");
        }
        String header = stripCr(text.substring(0, eol));
        String rest = text.substring(eol + 1);
        int eol2 = rest.indexOf('\n');
        String line = stripCr(eol2 < 0 ? rest : rest.substring(0, eol2));
        if (eol2 >= 0 && !rest.substring(eol2 + 1).trim().isEmpty()) {
            throw new OrderFormatException("В файле больше одной строки с данными. Сейчас поддерживается одна заявка "
                    + "на файл — пришлите этот файл разработчику, чтобы добавить разбор нескольких.");
        }

        List<String> values = split(line);
        // Исходник принимал строку, где у последней администрации нет даже пустой
        // «Причины отказа» (ни текста, ни завершающего «^»), — принимаем и мы.
        if (values.size() > FIELDS.size()
                && (values.size() - FIELDS.size()) % DETAIL_FIELDS.size() == DETAIL_FIELDS.size() - 1) {
            values.add("");
        }
        int detailValues = values.size() - FIELDS.size();
        if (detailValues < 0) {
            throw new OrderFormatException("Строка 2: полей " + values.size() + ", а у заявки их " + FIELDS.size()
                    + " (последнее — «" + FIELDS.get(FIELDS.size() - 1).label + "»)");
        }
        if (detailValues % DETAIL_FIELDS.size() != 0) {
            throw new OrderFormatException("Строка 2: после " + FIELDS.size() + " полей заявки осталось "
                    + detailValues + " полей — не делится на группы по " + DETAIL_FIELDS.size()
                    + " (данные ж/д администраций)");
        }

        Order order = new Order();
        order.setDateoper(today.atStartOfDay());
        order.setHeader(header);
        for (int i = 0; i < FIELDS.size(); i++) {
            set(order, FIELDS.get(i), values.get(i), i + 1, null);
        }
        int groups = detailValues / DETAIL_FIELDS.size();
        for (int g = 0; g < groups; g++) {
            Orderdetail od = new Orderdetail();
            for (int j = 0; j < DETAIL_FIELDS.size(); j++) {
                int pos = FIELDS.size() + g * DETAIL_FIELDS.size() + j;
                set(od, DETAIL_FIELDS.get(j), values.get(pos), pos + 1, g + 1);
            }
            order.addOrderdetail(od);
        }
        return order;
    }

    /**
     * Поля через «^». Завершающий «^» у последнего поля — как в файле; если его
     * нет, последнее поле всё равно берётся (это была «Причина отказа»).
     */
    static List<String> split(String line) {
        List<String> out = new ArrayList<>();
        int from = 0;
        int at;
        while ((at = line.indexOf('^', from)) >= 0) {
            out.add(line.substring(from, at));
            from = at + 1;
        }
        String tail = line.substring(from);
        if (!tail.trim().isEmpty()) {
            out.add(tail);
        }
        return out;
    }

    private static String stripCr(String s) {
        return s.endsWith("\r") ? s.substring(0, s.length() - 1) : s;
    }

    private static void set(Object target, Field field, String raw, int position, Integer group) {
        Object value = convert(field, raw, position, group);
        try {
            String setter = "set" + Character.toUpperCase(field.name.charAt(0)) + field.name.substring(1);
            Class<?> type = field.kind == Kind.INT ? Integer.class
                    : field.kind == Kind.TINY ? Short.class
                    : field.kind == Kind.DATE ? LocalDateTime.class : String.class;
            target.getClass().getMethod(setter, type).invoke(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Нет поля " + field.name + " в " + target.getClass().getSimpleName(), e);
        }
    }

    static Object convert(Field field, String raw, int position, Integer group) {
        String where = "поле " + position + " «" + field.label + "»"
                + (group == null ? "" : " (администрация " + group + ")");
        String v = raw.trim();
        switch (field.kind) {
            case INT:
                if (v.isEmpty()) {
                    return 0;
                }
                try {
                    return Integer.parseInt(v);
                } catch (NumberFormatException e) {
                    throw new OrderFormatException(where + ": ожидалось число, получено «" + raw + "»");
                }
            case TINY:
                if (v.isEmpty()) {
                    return (short) 0;
                }
                int n;
                try {
                    n = Integer.parseInt(v);
                } catch (NumberFormatException e) {
                    throw new OrderFormatException(where + ": ожидалось число, получено «" + raw + "»");
                }
                if (n < 0 || n > 255) {
                    throw new OrderFormatException(where + ": число " + n + " вне 0…255");
                }
                return (short) n;
            case DATE:
                if (!v.matches("\\d{8}")) {
                    throw new OrderFormatException(where + ": ожидалась дата ДДММГГГГ, получено «" + raw + "»");
                }
                try {
                    return LocalDate.of(Integer.parseInt(v.substring(4, 8)), Integer.parseInt(v.substring(2, 4)),
                            Integer.parseInt(v.substring(0, 2))).atStartOfDay();
                } catch (java.time.DateTimeException e) {
                    throw new OrderFormatException(where + ": такой даты нет — «" + raw + "»");
                }
            default:
                // Текст — как в файле (исходник тоже не обрезал пробелы).
                if (raw.length() > field.max) {
                    throw new OrderFormatException(where + ": длина " + raw.length() + ", допустимо " + field.max);
                }
                return raw;
        }
    }
}
