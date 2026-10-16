package tj.railway.mesplan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tj.railway.mesplan.entity.Orderdetail;

public interface OrderdetailRepository extends JpaRepository<Orderdetail, Integer> {
}
