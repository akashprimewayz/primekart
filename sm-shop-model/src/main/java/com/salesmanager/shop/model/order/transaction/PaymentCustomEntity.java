package com.salesmanager.shop.model.order.transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class PaymentCustomEntity extends PaymentEntity {

	/**
	 *
	 */
	private static final long serialVersionUID = 1L;
	/**
	 *
	 */

	private String amount;

	public String getAmount() {
		return amount;
	}

	public void setAmount(String amount) {
		if (isDecimal(amount)) {

			final BigDecimal submitedAmountDecimal = new BigDecimal(amount).setScale(2, RoundingMode.CEILING);

			this.amount = submitedAmountDecimal.toPlainString();
		}
	}

	boolean isDecimal(String str) {
		try {
			Double.parseDouble(str);
		} catch (final NumberFormatException e) {
			return false;
		}
		return true;
	}

	// BigDecimal submitedAmountDecimal=new BigDecimal(
	// order.getPayment().getAmount());

	// submitedAmountDecimal = submitedAmountDecimal.setScale(2,
	// RoundingMode.CEILING);

	// String submitedAmount= submitedAmountDecimal.toPlainString();

}
