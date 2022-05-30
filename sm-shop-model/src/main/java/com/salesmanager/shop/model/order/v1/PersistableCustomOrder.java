package com.salesmanager.shop.model.order.v1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.salesmanager.shop.model.order.transaction.PersistableCustomPayment;

/**
 * This object is used when processing an order from the API It will be used for
 * processing the payment and as Order meta data
 *
 * @author c.samson
 *
 */
public class PersistableCustomOrder extends Order {

	/**
	 *
	 */
	private static final long serialVersionUID = 1L;

	private PersistableCustomPayment payment;
	private Long shippingQuote;
	@JsonIgnore
	private Long shoppingCartId;
	@JsonIgnore
	private Long customerId;

	public Long getShoppingCartId() {
		return shoppingCartId;
	}

	public void setShoppingCartId(Long shoppingCartId) {
		this.shoppingCartId = shoppingCartId;
	}

	public Long getCustomerId() {
		return customerId;
	}

	public void setCustomerId(Long customerId) {
		this.customerId = customerId;
	}

	public PersistableCustomPayment getPayment() {
		return payment;
	}

	public void setPayment(PersistableCustomPayment payment) {
		this.payment = payment;
	}

	public Long getShippingQuote() {
		return shippingQuote;
	}

	public void setShippingQuote(Long shippingQuote) {
		this.shippingQuote = shippingQuote;
	}

}
