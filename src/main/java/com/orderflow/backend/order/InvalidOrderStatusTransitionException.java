
package com.orderflow.backend.order;

public class InvalidOrderStatusTransitionException
        extends RuntimeException {

    public InvalidOrderStatusTransitionException(
            Long orderId,
            String currentStatus,
            String requestedStatus) {

        super("Cannot change order " + orderId
                + " from " + currentStatus
                + " to " + requestedStatus);
    }
}
