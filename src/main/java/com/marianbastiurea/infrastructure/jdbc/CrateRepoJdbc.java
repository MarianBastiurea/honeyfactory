package com.marianbastiurea.infrastructure.jdbc;

import com.marianbastiurea.domain.enums.CrateType;
import com.marianbastiurea.domain.enums.JarType;
import com.marianbastiurea.domain.repo.CrateRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.EnumMap;
import java.util.Map;

@Repository
public class CrateRepoJdbc implements CrateRepo {

    private static final Logger log = LoggerFactory.getLogger(CrateRepoJdbc.class);
    private final NamedParameterJdbcTemplate tpl;

    public CrateRepoJdbc(@Qualifier("cratesTpl") NamedParameterJdbcTemplate tpl) {
        this.tpl = tpl;
    }


    private static final String INC_ORDERED = """
                UPDATE public.crate_stock
                   SET ordered     = ordered + :req,
                       last_updated= now(),
                       row_version = row_version + 1
                 WHERE crate_type  = :ct
            """;


    private static final String DELIVER_CTE = """
                WITH s AS (
                    SELECT crate_type, initial_stock, delivered,
                           (initial_stock - delivered) AS available
                      FROM public.crate_stock
                     WHERE crate_type = :ct
                     FOR UPDATE
                ),
                d AS (
                    SELECT :req::int AS requested,
                           GREATEST(0, LEAST(:req::int, s.available)) AS will_deliver
                      FROM s
                )
                UPDATE public.crate_stock cs
                   SET delivered   = cs.delivered + d.will_deliver,
                       last_updated= now(),
                       row_version = cs.row_version + 1
                  FROM d
                 WHERE cs.crate_type = :ct
             RETURNING d.requested, d.will_deliver
            """;


    private static final String INSERT_LOG = """
                INSERT INTO public.processing_log(order_number, crate_type, requested_qty, delivered_qty, reason)
                VALUES (:orderNumber, :ct, :requested, :delivered, :reason)
            """;


    public void deliveredCrates(int orderNumber, Map<JarType, Integer> requestedJarsPlan) {
        if (requestedJarsPlan == null || requestedJarsPlan.isEmpty()) {
            log.info("[crates.delivered] empty jars plan → no-op");
            return;
        }

        EnumMap<CrateType, Integer> cratesReq = new EnumMap<>(CrateType.class);
        for (var e : requestedJarsPlan.entrySet()) {
            Integer jars = e.getValue();
            if (jars == null || jars <= 0) continue;

            CrateType ct = CrateType.forJarType(e.getKey());
            int neededCrates = ct.cratesNeededForJars(jars); // ceil(jars / jarsPerCrate)
            if (neededCrates > 0) {
                cratesReq.merge(ct, neededCrates, Integer::sum);
            }
        }

        for (var e : cratesReq.entrySet()) {
            String ct = e.getKey().name();
            int requested = e.getValue();
            int upd = tpl.update(INC_ORDERED, new MapSqlParameterSource()
                    .addValue("ct", ct)
                    .addValue("req", requested));
            if (upd == 0) {
                throw new IllegalStateException("Crate type not found: " + ct);
            }

            Map<String, Object> res = tpl.queryForMap(DELIVER_CTE, new MapSqlParameterSource()
                    .addValue("ct", ct)
                    .addValue("req", requested));
            int delivered = ((Number) res.get("will_deliver")).intValue();

            String reason = (delivered == requested) ? "FULL_DELIVERY" : "PARTIAL_DELIVERY";
            tpl.update(INSERT_LOG, new MapSqlParameterSource()
                    .addValue("orderNumber", orderNumber)
                    .addValue("ct", ct)
                    .addValue("requested", requested)
                    .addValue("delivered", delivered)
                    .addValue("reason", reason));

            log.info("[crates.delivered] order={} type={} requested={} delivered={} reason={}",
                    orderNumber, ct, requested, delivered, reason);
        }
    }


    @Override
    public void deliveredCrates(Map<JarType, Integer> plan) {
        deliveredCrates(1, plan);
    }

}
