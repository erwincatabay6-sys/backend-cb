package com.cellbank.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "cellbank.bootstrap",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log =
            LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final AdminBootstrapService adminBootstrapService;
    private final Environment environment;

    public AdminBootstrapRunner(
            AdminBootstrapService adminBootstrapService,
            Environment environment) {

        this.adminBootstrapService = adminBootstrapService;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {

        boolean created = adminBootstrapService.createInitialAdmin(
                environment.getProperty("CELLBANK_ADMIN_NAME"),
                environment.getProperty("CELLBANK_ADMIN_USERNAME"),
                environment.getProperty("CELLBANK_ADMIN_EMAIL"),
                environment.getProperty("CELLBANK_ADMIN_PASSWORD")
        );

        if (created) {
            log.info("Initial administrator account created.");
        } else {
            log.info("An administrator account already exists. Bootstrap skipped.");
        }
    }
}
