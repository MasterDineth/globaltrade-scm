package com.globaltrade.scm.common.enums;

/**
 * Lifecycle states of a {@code Shipment}. Ordering follows the normal
 * happy-path progression; {@link #CUSTOMS_HOLD}, {@link #DELAYED} and
 * {@link #EXCEPTION} are the branch points the exception-handling and
 * alerting subsystems key off.
 */
public enum ShipmentStatus {
    CREATED,
    PICKED_UP,
    IN_TRANSIT,
    CUSTOMS_HOLD,
    OUT_FOR_DELIVERY,
    DELAYED,
    DELIVERED,
    EXCEPTION
}
