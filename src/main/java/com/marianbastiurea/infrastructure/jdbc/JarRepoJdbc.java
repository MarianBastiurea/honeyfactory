package com.marianbastiurea.infrastructure.jdbc;

import com.marianbastiurea.domain.enums.JarType;
import com.marianbastiurea.domain.repo.JarRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.EnumMap;
import java.util.Map;

@Repository
public class JarRepoJdbc implements JarRepo {
    private static final Logger log = LoggerFactory.getLogger(JarRepoJdbc.class);

    private final NamedParameterJdbcTemplate tpl;

    public JarRepoJdbc(@Qualifier("jarsTpl") NamedParameterJdbcTemplate tpl) {
        this.tpl = tpl;
    }

    private static final String INC_ORDERED = """
                UPDATE public.jar_stock
                   SET ordered     = ordered + :req,
                       last_updated= now(),
                       row_version = row_version + 1
                 WHERE jar_type    = :jt
            """;


    private static final String DELIVER_CTE = """
                WITH s AS (
                    SELECT jar_type, initial_stock, delivered,
                           (initial_stock - delivered) AS available
                      FROM public.jar_stock
                     WHERE jar_type = :jt
                     FOR UPDATE
                ),
                d AS (
                    SELECT :req::int AS requested,
                           GREATEST(0, LEAST(:req::int, s.available)) AS will_deliver
                      FROM s
                )
                UPDATE public.jar_stock js
                   SET delivered   = js.delivered + d.will_deliver,
                       last_updated= now(),
                       row_version = js.row_version + 1
                  FROM d
                 WHERE js.jar_type = :jt
             RETURNING d.requested, d.will_deliver
            """;


    private static final String INSERT_LOG = """
                INSERT INTO public.processing_log(order_number, jar_type, requested_qty, delivered_qty, reason)
                VALUES (:orderNumber, :jt, :requested, :delivered, :reason)
            """;

    public void deliveredJars(int orderNumber, Map<JarType, Integer> requestedPlan) {
        if (requestedPlan == null || requestedPlan.isEmpty()) {
            log.info("[jars.delivered] empty plan → no-op");
            return;
        }


        EnumMap<JarType, Integer> plan = new EnumMap<>(JarType.class);
        for (var e : requestedPlan.entrySet()) {
            Integer qty = e.getValue();
            if (qty == null || qty <= 0) continue;
            plan.merge(e.getKey(), qty, Integer::sum);
        }
        for (var e : plan.entrySet()) {
            String jt = e.getKey().name();
            int requested = e.getValue();

            int upd = tpl.update(INC_ORDERED, new MapSqlParameterSource()
                    .addValue("jt", jt)
                    .addValue("req", requested));
            if (upd == 0) {
                throw new IllegalStateException("Jar type not found: " + jt);
            }


            Map<String, Object> res = tpl.queryForMap(DELIVER_CTE, new MapSqlParameterSource()
                    .addValue("jt", jt)
                    .addValue("req", requested));
            int delivered = ((Number) res.get("will_deliver")).intValue();


            String reason = (delivered == requested) ? "FULL_DELIVERY" : "PARTIAL_DELIVERY";
            tpl.update(INSERT_LOG, new MapSqlParameterSource()
                    .addValue("orderNumber", orderNumber)
                    .addValue("jt", jt)
                    .addValue("requested", requested)
                    .addValue("delivered", delivered)
                    .addValue("reason", reason));

            log.info("[jars.delivered] order={} type={} requested={} delivered={} reason={}",
                    orderNumber, jt, requested, delivered, reason);
        }
    }


    @Override
    public void deliveredJars(Map<JarType, Integer> plan) {
        deliveredJars(1, plan);
    }
}
