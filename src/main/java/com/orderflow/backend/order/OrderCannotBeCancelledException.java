
package com.orderflow.backend.order;

public class OrderCannotBeCancelledException extends RuntimeException {

    public OrderCannotBeCancelledException(Long id, String status) {
        super("Order " + id
                + " cannot be cancelled because its status is " + status);
    }
}
