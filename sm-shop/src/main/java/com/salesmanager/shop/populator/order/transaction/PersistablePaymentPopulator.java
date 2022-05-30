package com.salesmanager.shop.populator.order.transaction;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.Validate;

import com.salesmanager.core.business.exception.ConversionException;
import com.salesmanager.core.business.services.catalog.product.PricingService;
import com.salesmanager.core.business.utils.AbstractDataPopulator;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.payments.Payment;
import com.salesmanager.core.model.payments.PaymentType;
import com.salesmanager.core.model.payments.TransactionType;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;

public class PersistablePaymentPopulator extends AbstractDataPopulator<PersistablePayment, Payment> {

	private PricingService pricingService;

	@Override
	protected Payment createTarget() {
		// TODO Auto-generated method stub
		return null;
	}

	public PricingService getPricingService() {
		return pricingService;
	}

	public void setPricingService(PricingService pricingService) {
		this.pricingService = pricingService;
	}

	@Override
	public Payment populate(PersistablePayment persistablePayment, Payment target, MerchantStore store,
			Language language) throws ConversionException {

		Validate.notNull(persistablePayment, "PersistablePayment cannot be null");
		Validate.notNull(pricingService, "pricingService must be set");
		if (target == null) {
			target = new Payment();
		}

		try {

			target.setAmount(pricingService.getAmount(persistablePayment.getAmount()));
			target.setModuleName(persistablePayment.getPaymentModule());
			target.setPaymentType(PaymentType.valueOf(persistablePayment.getPaymentType()));
			target.setTransactionType(TransactionType.valueOf(persistablePayment.getTransactionType()));

			final Map<String, String> metadata = new HashMap<>();
			metadata.put("paymentToken", persistablePayment.getPaymentToken());
			target.setPaymentMetaData(metadata);

			return target;

		} catch (final Exception e) {
			throw new ConversionException(e);
		}
	}

}
