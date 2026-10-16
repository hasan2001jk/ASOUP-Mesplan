package tj.railway.mesplan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tj.railway.mesplan.entity.Order;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Integer> {
    List<Order> findAllByDateoper(LocalDateTime dateoper);

    /** Заявки, загруженные/введённые в [from, to) — для «Просмотра», новые сверху. */
    List<Order> findAllByDateoperGreaterThanEqualAndDateoperLessThanOrderByIdDesc(LocalDateTime from, LocalDateTime to);
}
