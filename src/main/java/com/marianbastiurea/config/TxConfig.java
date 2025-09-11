package com.marianbastiurea.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
public class TxConfig {

    @Bean("jarsTx")
    PlatformTransactionManager jarsTx(@Qualifier("jarsDs") DataSource ds) {
        return new DataSourceTransactionManager(ds);
    }
    @Bean("cratesTx")
    PlatformTransactionManager cratesTx(@Qualifier("cratesDs") DataSource ds) {
        return new DataSourceTransactionManager(ds);
    }
    @Bean("labelsTx")
    PlatformTransactionManager labelsTx(@Qualifier("labelsDs") DataSource ds) {
        return new DataSourceTransactionManager(ds);
    }

    @Bean("jarsTT")
    TransactionTemplate jarsTT(@Qualifier("jarsTx") PlatformTransactionManager tm) {
        var tt = new TransactionTemplate(tm);
        tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        return tt;
    }
    @Bean("cratesTT")
    TransactionTemplate cratesTT(@Qualifier("cratesTx") PlatformTransactionManager tm) {
        var tt = new TransactionTemplate(tm);
        tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        return tt;
    }
    @Bean("labelsTT")
    TransactionTemplate labelsTT(@Qualifier("labelsTx") PlatformTransactionManager tm) {
        var tt = new TransactionTemplate(tm);
        tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        return tt;
    }
}
