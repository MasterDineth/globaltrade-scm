package com.globaltrade.scm.timer;

import com.globaltrade.scm.common.enums.CustomsDocumentStatus;
import com.globaltrade.scm.entity.AuditLogEntry;
import com.globaltrade.scm.entity.CustomsDocument;
import jakarta.annotation.Resource;
import jakarta.ejb.Stateless;
import jakarta.ejb.Timeout;
import jakarta.ejb.Timer;
import jakarta.ejb.TimerConfig;
import jakarta.ejb.TimerService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.logging.Logger;

/**
 * PROGRAMMATIC, per-instance single-action timer. Unlike the recurring
 * timers elsewhere in this package, there is no fixed schedule at all
 * here: each {@link CustomsDocument} carries its own
 * {@code submissionDeadline}, set per shipment based on the destination
 * country's customs rules. A single {@code @Schedule} annotation cannot
 * express "one timer per row, at a time taken from that row's data" --
 * only the {@code TimerService} API can, via
 * {@code createSingleActionTimer}. This is intentionally placed on a
 * {@code @Stateless} bean: because {@link #scheduleDeadlineReminder}
 * is an ordinary business method invoked exactly once per document
 * (from {@code CustomsDocumentationServiceBean} when the document is
 * created), not from a lifecycle callback, there is no risk of the
 * duplicate-registration problem described on
 * {@link VendorPerformanceAssessmentTimerBean}.
 */
@Stateless
public class CustomsDeadlineTimerBean {

    private static final Logger LOGGER = Logger.getLogger(CustomsDeadlineTimerBean.class.getName());
    private static final long REMINDER_LEAD_HOURS = 24L;

    @Resource
    private TimerService timerService;

    @PersistenceContext(unitName = "scmPU")
    private EntityManager em;

    /**
     * Schedules a single, one-off reminder/escalation timer to fire
     * {@value #REMINDER_LEAD_HOURS} hours before the document's submission
     * deadline. The document's primary key is passed as the (Serializable)
     * timer info so the {@code @Timeout} callback can re-load current state
     * -- never pass the entity itself as timer info, since persistent
     * timers are serialized into the timer store and a detached entity
     * graph is exactly the kind of thing that breaks across a redeploy.
     */
    public void scheduleDeadlineReminder(CustomsDocument document) {
        if (document.getSubmissionDeadline() == null) {
            LOGGER.warning(() -> "Customs document " + document.getId() + " has no submission deadline; no reminder scheduled.");
            return;
        }
        LocalDateTime reminderTime = document.getSubmissionDeadline().minusHours(REMINDER_LEAD_HOURS);
        Date reminderDate = Date.from(reminderTime.atZone(ZoneId.systemDefault()).toInstant());

        TimerConfig config = new TimerConfig(document.getId(), true);
        timerService.createSingleActionTimer(reminderDate, config);
        LOGGER.info(() -> String.format("Scheduled customs deadline reminder for document %d at %s",
                document.getId(), reminderTime));
    }

    @Timeout
    public void onDeadlineApproaching(Timer timer) {
        Long documentId = (Long) timer.getInfo();
        CustomsDocument document = em.find(CustomsDocument.class, documentId);
        if (document == null) {
            LOGGER.warning(() -> "Customs deadline timer fired for missing document id=" + documentId);
            return;
        }
        if (document.getStatus() == CustomsDocumentStatus.SUBMITTED
                || document.getStatus() == CustomsDocumentStatus.APPROVED) {
            return; // already handled -- nothing to escalate
        }

        LOGGER.severe(() -> "CUSTOMS DEADLINE ESCALATION: document " + documentId
                + " still " + document.getStatus() + " with deadline " + document.getSubmissionDeadline());

        AuditLogEntry escalation = new AuditLogEntry();
        escalation.setEntityName("CustomsDocument");
        escalation.setEntityId(String.valueOf(documentId));
        escalation.setAction("CUSTOMS_DEADLINE_ESCALATION");
        escalation.setPerformedBy("SYSTEM_TIMER");
        escalation.setTimestamp(LocalDateTime.now());
        escalation.setDetails("status=" + document.getStatus() + " deadline=" + document.getSubmissionDeadline());
        em.persist(escalation);
    }
}
