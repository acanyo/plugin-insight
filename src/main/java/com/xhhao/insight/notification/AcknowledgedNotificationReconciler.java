package com.xhhao.insight.notification;

import com.xhhao.insight.alert.AlertEngine;
import com.xhhao.insight.alert.AlertStatus;
import com.xhhao.insight.storage.model.AlertIncident;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Reconciles durable alert acknowledgements with Halo notification read state at startup. */
@Component
@Slf4j
public class AcknowledgedNotificationReconciler {

    private final AlertEngine alertEngine;
    private final HaloNotificationReadService notificationReadService;

    public AcknowledgedNotificationReconciler(
        AlertEngine alertEngine,
        HaloNotificationReadService notificationReadService
    ) {
        this.alertEngine = alertEngine;
        this.notificationReadService = notificationReadService;
    }

    public int reconcile() {
        try {
            List<AlertIncident> acknowledged = alertEngine.activeIncidents().stream()
                .filter(incident -> AlertStatus.ACKNOWLEDGED.name()
                    .equalsIgnoreCase(incident.status()))
                .toList();
            return notificationReadService.markIncidentsRead(acknowledged);
        } catch (RuntimeException error) {
            log.warn("Failed to reconcile acknowledged alert notifications ({})",
                error.getClass().getSimpleName());
            return 0;
        }
    }
}
