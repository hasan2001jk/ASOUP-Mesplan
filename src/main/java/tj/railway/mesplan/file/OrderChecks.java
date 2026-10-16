package tj.railway.mesplan.file;

import tj.railway.mesplan.entity.Order;
import tj.railway.mesplan.entity.Orderdetail;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Проверка заявки перед записью в базу — для сохранения из формы «Ввод»
 * (файл проверяет OrderFileParser).
 *
 * Обязательные поля — колонки NOT NULL таблиц orders / orderdetails. Без этой
 * проверки пустое поле доходило до SQL Server, и тот отвечал ошибкой 515,
 * которую оператор видел как «ошибка базы» без указания поля.
 */
public final class OrderChecks {

    static final Set<String> REQUIRED = new HashSet<>(Arrays.asList(
            "adm", "ftype", "strview", "datepogr", "transid", "gosotpr", "orderno", "strorderno", "codengr",
            "otprname", "poluchname", "stotpr", "stnazn", "etsng", "gng", "strnazn", "prinadl", "netto", "vags",
            "rodotpr", "admcount"));

    static final Set<String> DETAIL_REQUIRED = new HashSet<>(Arrays.asList("adm", "gate", "expname", "expcode"));

    private OrderChecks() {
    }

    /** Список проблем по-русски; пустой — можно сохранять. */
    public static List<String> problems(Order order) {
        List<String> out = new ArrayList<>();
        if (order == null) {
            out.add("Нет данных заявки");
            return out;
        }
        check(order, OrderFileParser.FIELDS, REQUIRED, "", out);
        if (order.getOrderdetails() != null) {
            int n = 0;
            for (Orderdetail od : order.getOrderdetails()) {
                n++;
                check(od, OrderFileParser.DETAIL_FIELDS, DETAIL_REQUIRED, "администрация " + n + ": ", out);
            }
        }
        return out;
    }

    public static List<String> problems(Orderdetail od) {
        List<String> out = new ArrayList<>();
        if (od == null) {
            out.add("Нет данных администрации");
            return out;
        }
        check(od, OrderFileParser.DETAIL_FIELDS, DETAIL_REQUIRED, "", out);
        return out;
    }

    private static void check(Object target, List<OrderFileParser.Field> fields, Set<String> required,
                              String prefix, List<String> out) {
        for (OrderFileParser.Field field : fields) {
            Object value = read(target, field.name);
            if (value == null) {
                if (required.contains(field.name)) {
                    out.add(prefix + "не заполнено «" + field.label + "»");
                }
                continue;
            }
            if (field.kind == OrderFileParser.Kind.TEXT && ((String) value).length() > field.max) {
                out.add(prefix + "«" + field.label + "» длиннее " + field.max + " знаков");
            }
            if (field.kind == OrderFileParser.Kind.TINY) {
                short s = (Short) value;
                if (s < 0 || s > 255) {
                    out.add(prefix + "«" + field.label + "» вне 0…255");
                }
            }
        }
    }

    private static Object read(Object target, String name) {
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
