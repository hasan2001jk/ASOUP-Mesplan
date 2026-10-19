package tj.railway.mesplan.file;

import tj.railway.mesplan.entity.Order;
import tj.railway.mesplan.entity.Orderdetail;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Разбор файла заявок МЕСПЛАН (PL_K — корректировка, PLAN — основной план, PL_E).
 *
 * Формат — как у старой программы mesplan2016 (main.pas) и исходного
 * MesplanService.getOrder; сверен с архивом 2018–2026 (docs/mesplan_messages,
 * 93 665 файлов, 193 470 заявок — ArchiveParseTest):
 *
 *   строка «P…»/«R…»   идентификатор файла → order.header;
 *   прочие строки       по заявке на строку: поля через «^», 35 полей заявки
 *                       (FIELDS), затем по группе на ж/д администрацию
 *                       (DETAIL_FIELDS: 8 полей, при «Виде строки» 001 — 6).
 *
 * Отличия от исходного разбора — только там, где он падал или терял данные:
 *  - несколько заявок в файле (исходник брал первую строку, остальные терял);
 *  - ошибка называет строку, поле и значение (было: ответ «2»);
 *  - длина текста и диапазон tinyint проверяются здесь, а не базой;
 *  - «Причина отказа» последней администрации без завершающего «^» сохраняется;
 *  - перевод строки в конце файла не ломает разбор.
 * На однострочных файлах, которые исходник разбирал без ошибки, результат тот
 * же — OrderFileParserTest сверяет с замороженной копией исходного кода.
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
            t("ftype", "Тип файла", 4),
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

    /** Длина колонки orders.header. */
    static final int HEADER_MAX = 50;

    /**
     * Разбор файла заявок. Один файл — сколько угодно заявок, по одной на строку
     * (в архиве 2018–2026: у 20 % файлов их больше одной, у PLAN — сотни).
     *
     * Правила — как в старой программе (mesplan2016, main.pas, «Просмотр и
     * корректировка заявок (2016)»):
     *  - строка, начинающаяся с «P» или «R», — идентификатор файла (обычно первая,
     *    например «PL_K6610-2026-10-15-14.59.56.666397_NV_2013.1»); он пишется в
     *    header каждой заявки и нужен для ответного файла;
     *  - любая другая непустая строка — заявка: 35 полей, затем группы по 8 полей
     *    на каждую ж/д администрацию; при «Виде строки» 001 — по 6 (без результата
     *    и причины отказа).
     *
     * @param text  содержимое файла, уже раскодированное (файлы — в CP866)
     * @param today дата загрузки — пишется в dateoper
     */
    public static List<Order> parseFile(String text, LocalDate today) {
        if (text == null || text.trim().isEmpty()) {
            throw new OrderFormatException("Файл пустой");
        }
        String header = null;
        List<Order> orders = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        for (int n = 0; n < lines.length; n++) {
            String line = stripCr(lines[n]);
            if (line.trim().isEmpty()) {
                continue;
            }
            char first = Character.toUpperCase(line.charAt(0));
            if (first == 'P' || first == 'R') {
                header = line.trim();
                if (header.length() > HEADER_MAX) {
                    throw new OrderFormatException("строка " + (n + 1) + ": идентификатор файла длиннее "
                            + HEADER_MAX + " знаков");
                }
                continue;
            }
            Order order = parseLine(line, n + 1, today);
            order.setHeader(header);
            orders.add(order);
        }
        if (orders.isEmpty()) {
            throw new OrderFormatException("В файле нет ни одной заявки");
        }
        return orders;
    }

    /** Размер группы полей одной администрации: 6 при «Виде строки» 001, иначе 8. */
    static int groupSize(String strview) {
        return "001".equals(strview == null ? null : strview.trim()) ? 6 : DETAIL_FIELDS.size();
    }

    static Order parseLine(String line, int lineNo, LocalDate today) {
        String at = "строка " + lineNo + ", ";
        List<String> values = split(line);
        if (values.size() < FIELDS.size()) {
            throw new OrderFormatException(at.substring(0, at.length() - 2) + ": полей " + values.size()
                    + ", а у заявки их не меньше " + FIELDS.size() + " (последнее — «"
                    + FIELDS.get(FIELDS.size() - 1).label + "»)");
        }
        int group = groupSize(values.get(2));
        // Старая программа принимала строку, где у последней администрации нет даже
        // пустой «Причины отказа» (ни текста, ни завершающего «^»), — принимаем и мы.
        if (group == DETAIL_FIELDS.size() && values.size() > FIELDS.size()
                && (values.size() - FIELDS.size()) % group == group - 1) {
            values.add("");
        }
        int detailValues = values.size() - FIELDS.size();
        if (detailValues % group != 0) {
            throw new OrderFormatException(at.substring(0, at.length() - 2) + ": после " + FIELDS.size()
                    + " полей заявки осталось " + detailValues + " — не делится на группы по " + group
                    + " (данные ж/д администраций)");
        }

        Order order = new Order();
        order.setDateoper(today.atStartOfDay());
        for (int i = 0; i < FIELDS.size(); i++) {
            set(order, FIELDS.get(i), values.get(i), i + 1, null, at);
        }
        int groups = detailValues / group;
        for (int g = 0; g < groups; g++) {
            Orderdetail od = new Orderdetail();
            for (int j = 0; j < group; j++) {
                int pos = FIELDS.size() + g * group + j;
                set(od, DETAIL_FIELDS.get(j), values.get(pos), pos + 1, g + 1, at);
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
        // Хвост после последнего «^» — последнее поле, даже если это одни пробелы
        // (в архиве последняя «Причина отказа» бывает из 12 пробелов: без этого
        // ответный файл терял бы их). Пустой хвост — это просто завершающий «^».
        String tail = line.substring(from);
        if (!tail.isEmpty()) {
            out.add(tail);
        }
        return out;
    }

    private static String stripCr(String s) {
        return s.endsWith("\r") ? s.substring(0, s.length() - 1) : s;
    }

    private static void set(Object target, Field field, String raw, int position, Integer group, String at) {
        Object value;
        try {
            value = convert(field, raw, position, group);
        } catch (OrderFormatException e) {
            throw new OrderFormatException(at + e.getMessage());
        }
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
    /** Значение поля сущности по имени (геттер). */
    static Object read(Object target, String name) {
        try {
            for (PropertyDescriptor pd : Introspector.getBeanInfo(target.getClass()).getPropertyDescriptors()) {
                if (pd.getName().equals(name) && pd.getReadMethod() != null) {
                    return pd.getReadMethod().invoke(target);
                }
            }
        } catch (IntrospectionException | ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        throw new IllegalStateException("Нет поля " + name + " в " + target.getClass().getSimpleName());
    }
}
