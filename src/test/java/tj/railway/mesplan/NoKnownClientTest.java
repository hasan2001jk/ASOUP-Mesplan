package tj.railway.mesplan;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;
import tj.railway.mesplan.configuration.NoKnownClient;
import tj.railway.mesplan.configuration.NoKnownClientInterceptor;
import tj.railway.mesplan.controller.DictionariesController;
import tj.railway.mesplan.controller.OrderController;
import tj.railway.mesplan.controller.OrderDetailController;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/** Точки без вызова во фронте отмечены; их первый вызов попадает в журнал, но не запрещается. */
class NoKnownClientTest {

    /** Сверено с oper-dev (MesplanService фронта) 2026-10-16. Меняется — сверить заново. */
    static final String[] MARKED = {
            "DictionariesController.getConttypesize", "DictionariesController.getPrzsob(Integer)",
            "DictionariesController.getRodvag", "OrderController.getOrdersByDate",
            "OrderDetailController.deleteOrderDetails", "OrderDetailController.saveOrderDetails"};

    @Test
    void exactlyTheCheckedEndpointsAreMarked() {
        Set<String> found = new TreeSet<>();
        for (Class<?> c : new Class<?>[]{DictionariesController.class, OrderController.class, OrderDetailController.class}) {
            assertNotNull(c.getAnnotation(RequestMapping.class));
            for (Method m : c.getDeclaredMethods()) {
                NoKnownClient mark = m.getAnnotation(NoKnownClient.class);
                if (mark != null) {
                    assertFalse(mark.value().trim().isEmpty());
                    String overload = m.getName().equals("getPrzsob") ? "(Integer)" : "";
                    found.add(c.getSimpleName() + "." + m.getName() + overload);
                }
            }
        }
        assertEquals(new TreeSet<>(Arrays.asList(MARKED)), found);
    }

    @Test
    void firstCallReportedOnceAndNeverBlocked() throws Exception {
        NoKnownClientInterceptor interceptor = new NoKnownClientInterceptor();
        HandlerMethod marked = new HandlerMethod(new OrderDetailController(null),
                OrderDetailController.class.getDeclaredMethod("deleteOrderDetails", Integer.class));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orderdetails/1/delete");
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), marked));
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), marked));
        HandlerMethod used = new HandlerMethod(new OrderController(null),
                OrderController.class.getDeclaredMethod("getOrderById", Integer.class));
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), used));
    }
}
