package tj.railway.mesplan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tj.railway.mesplan.entity.Orderdetail;

import java.util.List;

public interface OrderdetailRepository extends JpaRepository<Orderdetail, Integer> {
    //List<Orderdetail> findByOrderid(Integer orderid);
}
