package com.osmshop.order;

/** Published inside the order transaction; emails are sent only after it commits. */
public record OrderEvent(long orderId, Kind kind) {
    public enum Kind { PLACED, CONFIRMED, PAID, CANCELLED }
}
