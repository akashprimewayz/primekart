package com.salesmanager.shop.populator.order;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.salesmanager.core.business.exception.ConversionException;
import com.salesmanager.core.business.services.customer.CustomerService;
import com.salesmanager.core.business.services.reference.currency.CurrencyService;
import com.salesmanager.core.business.utils.AbstractDataPopulator;
import com.salesmanager.core.model.common.Billing;
import com.salesmanager.core.model.common.Delivery;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.order.OrderChannel;
import com.salesmanager.core.model.order.attributes.OrderAttribute;
import com.salesmanager.core.model.order.orderstatus.OrderStatus;
import com.salesmanager.core.model.order.orderstatus.OrderStatusHistory;
import com.salesmanager.core.model.payments.PaymentType;
import com.salesmanager.core.model.reference.currency.Currency;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.model.order.v1.PersistableCustomAnonymousOrder;
import com.salesmanager.shop.model.order.v1.PersistableCustomOrder;
import com.salesmanager.shop.populator.customer.CustomerPopulator;
import com.salesmanager.shop.utils.LocaleUtils;

@Component
public class PersistableCustomOrderApiPopulator extends AbstractDataPopulator<PersistableCustomOrder, Order> {

	@Autowired
	private CurrencyService currencyService;
	@Autowired
	private CustomerService customerService;
	/*
	 * @Autowired private ShoppingCartService shoppingCartService;
	 *
	 * @Autowired private ProductService productService;
	 *
	 * @Autowired private ProductAttributeService productAttributeService;
	 *
	 * @Autowired private DigitalProductService digitalProductService;
	 */
	@Autowired
	private CustomerPopulator customerPopulator;

	@Override
	public Order populate(PersistableCustomOrder order, Order target, MerchantStore store, Language language)
			throws ConversionException {

		/*
		 * Validate.notNull(currencyService,"currencyService must be set");
		 * Validate.notNull(customerService,"customerService must be set");
		 * Validate.notNull(shoppingCartService,"shoppingCartService must be set");
		 * Validate.notNull(productService,"productService must be set");
		 * Validate.notNull(
		 * productAttributeService,"productAttributeService must be set");
		 * Validate.notNull(digitalProductService,"digitalProductService must be set");
		 */
		Validate.notNull(order.getPayment(), "Payment cannot be null");

		try {

			if (target == null) {
				target = new Order();
			}

			// target.setLocale(LocaleUtils.getLocale(store));

			target.setLocale(LocaleUtils.getLocale(store));

			Currency currency = null;
			try {
				currency = currencyService.getByCode(order.getCurrency());
			} catch (final Exception e) {
				throw new ConversionException("Currency not found for code " + order.getCurrency());
			}

			if (currency == null) {
				throw new ConversionException("Currency not found for code " + order.getCurrency());
			}

			// Customer
			Customer customer = null;
			if (order.getCustomerId() != null && order.getCustomerId().longValue() > 0) {
				final Long customerId = order.getCustomerId();
				customer = customerService.getById(customerId);

				if (customer == null) {
					throw new ConversionException("Curstomer with id " + order.getCustomerId() + " does not exist");
				}
				target.setCustomerId(customerId);

			} else if (order instanceof PersistableCustomAnonymousOrder) {
				final PersistableCustomer persistableCustomer = ((PersistableCustomAnonymousOrder) order).getCustomer();
				customer = new Customer();
				customer = customerPopulator.populate(persistableCustomer, customer, store, language);
			} else {
				throw new ConversionException("Curstomer details or id not set in request");
			}

			target.setCustomerEmailAddress(customer.getEmailAddress());

			final Delivery delivery = customer.getDelivery();
			target.setDelivery(delivery);

			final Billing billing = customer.getBilling();
			target.setBilling(billing);

			if (order.getAttributes() != null && order.getAttributes().size() > 0) {
				final Set<OrderAttribute> attrs = new HashSet<>();
				for (final com.salesmanager.shop.model.order.OrderAttribute attribute : order.getAttributes()) {
					final OrderAttribute attr = new OrderAttribute();
					attr.setKey(attribute.getKey());
					attr.setValue(attribute.getValue());
					attr.setOrder(target);
					attrs.add(attr);
				}
				target.setOrderAttributes(attrs);
			}

			target.setDatePurchased(new Date());
			target.setCurrency(currency);
			target.setCurrencyValue(new BigDecimal(0));
			target.setMerchant(store);
			target.setChannel(OrderChannel.API);
			// need this
			target.setStatus(OrderStatus.ORDERED);
			target.setPaymentModuleCode(order.getPayment().getPaymentModule());
			target.setPaymentType(PaymentType.valueOf(order.getPayment().getPaymentType()));

			target.setCustomerAgreement(order.isCustomerAgreement());
			target.setConfirmedAddress(true);// force this to true, cannot perform this activity from the API

			if (!StringUtils.isBlank(order.getComments())) {
				final OrderStatusHistory statusHistory = new OrderStatusHistory();
				statusHistory.setStatus(null);
				statusHistory.setOrder(target);
				statusHistory.setComments(order.getComments());
				target.getOrderHistory().add(statusHistory);
			}

			return target;

		} catch (final Exception e) {
			throw new ConversionException(e);
		}
	}

	@Override
	protected Order createTarget() {
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * public CurrencyService getCurrencyService() { return currencyService; }
	 *
	 * public void setCurrencyService(CurrencyService currencyService) {
	 * this.currencyService = currencyService; }
	 *
	 * public CustomerService getCustomerService() { return customerService; }
	 *
	 * public void setCustomerService(CustomerService customerService) {
	 * this.customerService = customerService; }
	 *
	 * public ShoppingCartService getShoppingCartService() { return
	 * shoppingCartService; }
	 *
	 * public void setShoppingCartService(ShoppingCartService shoppingCartService) {
	 * this.shoppingCartService = shoppingCartService; }
	 *
	 * public ProductService getProductService() { return productService; }
	 *
	 * public void setProductService(ProductService productService) {
	 * this.productService = productService; }
	 *
	 * public ProductAttributeService getProductAttributeService() { return
	 * productAttributeService; }
	 *
	 * public void setProductAttributeService(ProductAttributeService
	 * productAttributeService) { this.productAttributeService =
	 * productAttributeService; }
	 *
	 * public DigitalProductService getDigitalProductService() { return
	 * digitalProductService; }
	 *
	 * public void setDigitalProductService(DigitalProductService
	 * digitalProductService) { this.digitalProductService = digitalProductService;
	 * }
	 */

}
