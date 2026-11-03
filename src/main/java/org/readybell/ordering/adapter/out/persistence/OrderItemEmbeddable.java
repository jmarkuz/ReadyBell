package org.readybell.ordering.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.readybell.ordering.domain.OrderItem;

/** Persistent form of {@link OrderItem} (one row in {@code order_item}). */
@Embeddable
public class OrderItemEmbeddable {

    @Column(name = "name", nullable = false, length = OrderItem.MAX_NAME_LENGTH)
    private String name;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    protected OrderItemEmbeddable() {}

    static OrderItemEmbeddable from(OrderItem item) {
        var e = new OrderItemEmbeddable();
        e.name = item.name();
        e.quantity = item.quantity();
        return e;
    }

    OrderItem toDomain() {
        return new OrderItem(name, quantity);
    }

    public String getName() { return name; }
    public int getQuantity() { return quantity; }
}
