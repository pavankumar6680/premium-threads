package com.buildmybundle.orders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.buildmybundle.auth.UserDirectory;
import com.buildmybundle.auth.UserDirectory.UserContact;
import com.buildmybundle.inventory.InventoryService;
import com.buildmybundle.orders.OrderViews.OrderView;
import com.buildmybundle.orders.domain.CustomerOrder;
import com.buildmybundle.orders.domain.OrderItem;
import com.buildmybundle.orders.domain.OrderRepository;
import com.buildmybundle.orders.domain.OrderStatus;
import com.buildmybundle.payments.PaymentService;
import com.buildmybundle.shared.audit.AuditLog;
import com.buildmybundle.shared.error.ApiException;
import com.buildmybundle.shared.web.PageResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Order fulfilment for staff: list and inspect all orders, mark them shipped or delivered, or cancel an
 * unshipped order (stock is returned and the payment refunded, exactly as a customer cancellation).
 */
@Service
public class OrderAdminService {

	private static final Logger log = LoggerFactory.getLogger(OrderAdminService.class);

	public record AdminOrderSummary(Long id, String orderNumber, String status, Instant createdAt, BigDecimal total,
			String currency, int itemCount, UserContact customer) {
	}

	public record AdminOrderView(OrderView order, UserContact customer, List<String> allowedTransitions) {
	}

	private final OrderRepository orders;
	private final OrderService orderService;
	private final InventoryService inventory;
	private final PaymentService payments;
	private final UserDirectory users;
	private final AuditLog audit;

	public OrderAdminService(OrderRepository orders, OrderService orderService, InventoryService inventory,
			PaymentService payments, UserDirectory users, AuditLog audit) {
		this.orders = orders;
		this.orderService = orderService;
		this.inventory = inventory;
		this.payments = payments;
		this.users = users;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public PageResponse<AdminOrderSummary> list(OrderStatus status, String orderNumber, int page, int size) {
		PageRequest pageable = PageRequest.of(page, size);
		Page<CustomerOrder> result;
		if (orderNumber != null && !orderNumber.isBlank()) {
			List<CustomerOrder> match = orders.findByOrderNumber(orderNumber.trim().toUpperCase()).stream().toList();
			result = new org.springframework.data.domain.PageImpl<>(match, pageable, match.size());
		}
		else if (status != null) {
			result = orders.findByStatusOrderByCreatedAtDescIdDesc(status, pageable);
		}
		else {
			result = orders.findAllByOrderByCreatedAtDescIdDesc(pageable);
		}
		Map<Long, UserContact> contacts = users.contacts(result.getContent().stream().map(CustomerOrder::getUserId).toList());
		return PageResponse.from(result, o -> new AdminOrderSummary(o.getId(), o.getOrderNumber(), o.getStatus().name(),
				o.getCreatedAt(), o.getTotal(), o.getCurrency(), o.getItems().stream().mapToInt(OrderItem::getQuantity).sum(),
				contacts.get(o.getUserId())));
	}

	@Transactional(readOnly = true)
	public AdminOrderView get(Long id) {
		return view(orders.findById(id).orElseThrow(OrderAdminService::notFound));
	}

	@Transactional
	public AdminOrderView changeStatus(Long id, OrderStatus target) {
		CustomerOrder order = orders.findByIdForUpdate(id).orElseThrow(OrderAdminService::notFound);
		String from = order.getStatus().name();
		switch (target) {
			case SHIPPED -> order.ship();
			case DELIVERED -> order.deliver();
			case CANCELLED -> {
				order.cancel();
				inventory.release(order.quantities());
				payments.refund(order.getId());
			}
			case PAID -> throw ApiException.badRequest("INVALID_ORDER_TRANSITION", "Orders cannot be moved back to paid.");
		}
		orders.flush();
		audit.record("ORDER_" + target.name(), "ORDER", order.getId(),
				Map.of("orderNumber", order.getOrderNumber(), "from", from, "to", target.name()));
		log.info("Order {} changed from {} to {} by staff", order.getOrderNumber(), from, target);
		return view(order);
	}

	private AdminOrderView view(CustomerOrder order) {
		UserContact customer = users.contacts(List.of(order.getUserId())).get(order.getUserId());
		List<String> allowed = switch (order.getStatus()) {
			case PAID -> List.of(OrderStatus.SHIPPED.name(), OrderStatus.CANCELLED.name());
			case SHIPPED -> List.of(OrderStatus.DELIVERED.name());
			case DELIVERED, CANCELLED -> List.of();
		};
		return new AdminOrderView(orderService.view(order), customer, allowed);
	}

	private static ApiException notFound() {
		return ApiException.notFound("ORDER_NOT_FOUND", "Order not found.");
	}
}
