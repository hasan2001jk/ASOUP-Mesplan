package tj.railway.mesplan.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tj.railway.mesplan.entity.Order;
import tj.railway.mesplan.entity.Orderdetail;
import tj.railway.mesplan.entity.dictionaries.Conttypesize;
import tj.railway.mesplan.entity.dictionaries.Przsob;
import tj.railway.mesplan.entity.dictionaries.Rodvag;
import tj.railway.mesplan.file.OrderChecks;
import tj.railway.mesplan.file.OrderFileParser;
import tj.railway.mesplan.repository.OrderRepository;
import tj.railway.mesplan.repository.OrderdetailRepository;
import tj.railway.mesplan.repository.dictionaries.ConttypesizeRepository;
import tj.railway.mesplan.repository.dictionaries.PrzsobRepository;
import tj.railway.mesplan.repository.dictionaries.RodvagRepository;
import tj.railway.mesplan.web.ApiException;

import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MesplanService {

    /** Файлы заявок — в DOS-кодировке, как читал исходник («Cp866»). */
    public static final Charset FILE_CHARSET = Charset.forName("IBM866");

    /** «Просмотр» берёт не больше квартала за раз. */
    static final int MAX_PERIOD_DAYS = 92;

    private final RodvagRepository rodvagRepository;
    private final PrzsobRepository przsobRepository;
    private final ConttypesizeRepository conttypesizeRepository;
    private final OrderRepository orderRepository;
    private final OrderdetailRepository orderdetailRepository;

    public List<Rodvag> getRodvags() {
        return rodvagRepository.findAll();
    }

    public Rodvag getRodvag(String code) {
        return rodvagRepository.findById(code)
                .orElseThrow(() -> ApiException.notFound("Нет рода вагона " + code));
    }

    public List<Przsob> getPrzsobs() {
        return przsobRepository.findAll();
    }

    public Przsob getPrzsob(Integer code) {
        return przsobRepository.findById(code)
                .orElseThrow(() -> ApiException.notFound("Нет признака собственности " + code));
    }

    public List<Conttypesize> getConttypesizes() {
        return conttypesizeRepository.findAll();
    }

    public Conttypesize getConttypesize(Integer code) {
        return conttypesizeRepository.findById(code)
                .orElseThrow(() -> ApiException.notFound("Нет типоразмера контейнера " + code));
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersByDateoper(LocalDate day) {
        return withDetails(orderRepository.findAllByDateoper(day.atStartOfDay()));
    }

    /** Заявки с датой загрузки/ввода в [from, to] включительно. */
    @Transactional(readOnly = true)
    public List<Order> getOrdersByPeriod(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw ApiException.badRequest("Дата окончания раньше даты начала");
        }
        if (from.plusDays(MAX_PERIOD_DAYS).isBefore(to)) {
            throw ApiException.badRequest("Период больше " + MAX_PERIOD_DAYS + " дней — сузьте его");
        }
        return withDetails(orderRepository.findAllByDateoperGreaterThanEqualAndDateoperLessThanOrderByIdDesc(
                from.atStartOfDay(), to.plusDays(1).atStartOfDay()));
    }

    /**
     * Администрации загружаются внутри транзакции: open-in-view выключен, и после
     * выхода из сервиса ленивый список уже не прочитать (ответ был бы 500).
     * FetchMode.SUBSELECT на связи — одним запросом на весь список.
     */
    private static List<Order> withDetails(List<Order> orders) {
        for (Order o : orders) {
            o.getOrderdetails().size();
        }
        return orders;
    }

    @Transactional(readOnly = true)
    public Order getOrder(Integer id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Нет заявки с номером записи " + id));
        order.getOrderdetails().size(); // загрузить до закрытия транзакции (open-in-view выключен)
        return order;
    }

    /**
     * Новая заявка (id = null) или изменение существующей. Администрации
     * приходят в orderdetails целиком: которых нет в списке — удаляются.
     */
    @Transactional
    public Order saveOrder(Order order) {
        List<String> problems = OrderChecks.problems(order);
        if (!problems.isEmpty()) {
            throw ApiException.badRequest("Заявка не сохранена: " + String.join("; ", problems));
        }
        if (order.getId() != null && !orderRepository.existsById(order.getId())) {
            throw ApiException.notFound("Нет заявки с номером записи " + order.getId() + " — возможно, её уже удалили");
        }
        if (order.getDateoper() == null) {
            order.setDateoper(LocalDate.now().atStartOfDay());
        }
        if (order.getHeader() == null) {
            order.setHeader("");
        }
        List<Orderdetail> details = order.getOrderdetails() == null ? new ArrayList<>() : order.getOrderdetails();
        order.setOrderdetails(new ArrayList<>());
        for (Orderdetail od : details) {
            order.addOrderdetail(od);
        }
        Order saved = orderRepository.save(order);
        // orderid только читается из базы — у только что добавленных строк в ответе
        // его ещё нет. Проставляем, чтобы фронт получил то, что теперь лежит в базе.
        for (Orderdetail od : saved.getOrderdetails()) {
            od.setOrderid(saved.getId());
        }
        return saved;
    }

    @Transactional
    public Order deleteOrder(Integer id) {
        Order order = getOrder(id);
        orderRepository.delete(order);
        return order;
    }

    @Transactional
    public Orderdetail saveOrderdetail(Orderdetail orderdetail) {
        List<String> problems = OrderChecks.problems(orderdetail);
        if (orderdetail != null && orderdetail.getOrderid() == null) {
            problems.add("не указана заявка (orderid)");
        }
        if (!problems.isEmpty()) {
            throw ApiException.badRequest("Администрация не сохранена: " + String.join("; ", problems));
        }
        Order order = orderRepository.findById(orderdetail.getOrderid())
                .orElseThrow(() -> ApiException.notFound("Нет заявки с номером записи " + orderdetail.getOrderid()));
        orderdetail.setOrder(order);
        return orderdetailRepository.save(orderdetail);
    }

    @Transactional
    public Orderdetail deleteOrderdetail(Integer id) {
        Orderdetail orderdetail = orderdetailRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Нет строки администрации " + id));
        orderdetailRepository.delete(orderdetail);
        return orderdetail;
    }

    /** Загрузка файла заявок. Ошибка формата — OrderFormatException (400), ошибка базы — 502. */
    @Transactional
    public Order uploadOrder(byte[] content, String fileName) {
        Order order = OrderFileParser.parse(new String(content, FILE_CHARSET), LocalDate.now());
        Order saved = orderRepository.save(order);
        for (Orderdetail od : saved.getOrderdetails()) {
            od.setOrderid(saved.getId());
        }
        log.info("Загружен файл заявок {}: запись {}, заявка {} строка {}, администраций {}",
                fileName, saved.getId(), saved.getOrderno(), saved.getStrorderno(), saved.getOrderdetails().size());
        return saved;
    }
}
