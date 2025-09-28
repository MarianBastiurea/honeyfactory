package com.marianbastiurea.infrastructure.jdbc;

import com.marianbastiurea.domain.enums.JarType;
import com.marianbastiurea.domain.enums.LabelType;
import com.marianbastiurea.domain.repo.LabelRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;


import java.util.EnumMap;
import java.util.Map;

@Repository
public class LabelRepoJdbc implements LabelRepo {

    private static final Logger log = LoggerFactory.getLogger(LabelRepoJdbc.class);
    private final NamedParameterJdbcTemplate tpl;

    public LabelRepoJdbc(@Qualifier("labelsTpl") NamedParameterJdbcTemplate tpl) {
        this.tpl = tpl;
    }

    private static final String INC_ORDERED = """
        UPDATE public.label_stock
           SET ordered     = ordered + :req,
               last_updated= now(),
               row_version = row_version + 1
         WHERE label_type  = :lt
    """;

    private static final String DELIVER_CTE = """
        WITH s AS (
            SELECT label_type, initial_stock, delivered,
                   (initial_stock - delivered) AS available
              FROM public.label_stock
             WHERE label_type = :lt
             FOR UPDATE
        ),
        d AS (
            SELECT :req::int AS requested,
                   GREATEST(0, LEAST(:req::int, s.available)) AS will_deliver
              FROM s
        )
        UPDATE public.label_stock ls
           SET delivered   = ls.delivered + d.will_deliver,
               last_updated= now(),
               row_version = ls.row_version + 1
          FROM d
         WHERE ls.label_type = :lt
     RETURNING d.requested, d.will_deliver
    """;


    private static final String INSERT_LOG = """
        INSERT INTO public.processing_log(order_number, label_type, requested_qty, delivered_qty, reason)
        VALUES (:orderNumber, :lt, :requested, :delivered, :reason)
    """;


    public void deliveredLabels(int orderNumber, Map<JarType, Integer> requestedJarsPlan) {

        EnumMap<LabelType, Integer> labelsReq = new EnumMap<>(LabelType.class);
        for (var e : requestedJarsPlan.entrySet()) {
            Integer qty = e.getValue();
            if (qty == null || qty <= 0) continue;
            LabelType lt = labelTypeFor(e.getKey());
            labelsReq.merge(lt, qty, Integer::sum);
        }

        for (var e : labelsReq.entrySet()) {
            String lt = e.getKey().name();
            int requested = e.getValue();

            int upd = tpl.update(INC_ORDERED, new MapSqlParameterSource()
                    .addValue("lt", lt)
                    .addValue("req", requested));
            if (upd == 0) {
                throw new IllegalStateException("Label type not found: " + lt);
            }

            Map<String, Object> res = tpl.queryForMap(DELIVER_CTE, new MapSqlParameterSource()
                    .addValue("lt", lt)
                    .addValue("req", requested));
            int delivered = ((Number) res.get("will_deliver")).intValue();


            String reason = (delivered == requested) ? "FULL_DELIVERY" : "PARTIAL_DELIVERY";
            tpl.update(INSERT_LOG, new MapSqlParameterSource()
                    .addValue("orderNumber", orderNumber)
                    .addValue("lt", lt)
                    .addValue("requested", requested)
                    .addValue("delivered", delivered)
                    .addValue("reason", reason));

            log.info("[labels.delivered] order={} type={} requested={} delivered={} reason={}",
                    orderNumber, lt, requested, delivered, reason);
        }
    }

    @Override
    public void deliveredLabels(Map<JarType, Integer> plan) {
        deliveredLabels(-1, plan); // fallback (order necunoscut)
    }


    private static LabelType labelTypeFor(JarType jt) {
        return switch (jt) {
            case JAR200 -> LabelType.LABEL200;
            case JAR400 -> LabelType.LABEL400;
            case JAR800 -> LabelType.LABEL800;
        };
    }
}
