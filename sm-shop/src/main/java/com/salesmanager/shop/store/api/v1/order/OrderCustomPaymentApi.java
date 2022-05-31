package com.salesmanager.shop.store.api.v1.order;

import java.security.Principal;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.salesmanager.core.business.services.catalog.product.PricingService;
import com.salesmanager.core.business.services.customer.CustomerService;
import com.salesmanager.core.business.services.order.OrderService;
import com.salesmanager.core.business.services.payments.PaymentService;
import com.salesmanager.core.business.services.shoppingcart.ShoppingCartService;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.payments.Payment;
import com.salesmanager.core.model.payments.Transaction;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.shoppingcart.ShoppingCart;
import com.salesmanager.shop.model.order.transaction.PersistableCustomPayment;
import com.salesmanager.shop.model.order.transaction.ReadableTransaction;
import com.salesmanager.shop.populator.order.transaction.PersistableCustomPaymentPopulator;
import com.salesmanager.shop.populator.order.transaction.ReadableTransactionPopulator;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Tag;
import springfox.documentation.annotations.ApiIgnore;

@Controller
@RequestMapping("/api/v1")
@Api(tags = { "Order payment resource (Order custom payment Api)" })
@SwaggerDefinition(tags = { @Tag(name = "Order payment resource", description = "Manage order payments") })
public class OrderCustomPaymentApi {

	private static final Logger LOGGER = LoggerFactory.getLogger(OrderCustomPaymentApi.class);

	@Inject
	private CustomerService customerService;

	@Inject
	private OrderService orderService;

	@Inject
	private ShoppingCartService shoppingCartService;

	@Inject
	private PricingService pricingService;

	@Inject
	private PaymentService paymentService;

	@RequestMapping(value = { "/cart/{code}/payment/custom-init" }, method = RequestMethod.POST)
	@ResponseBody
	@ApiImplicitParams({ @ApiImplicitParam(name = "store", dataType = "String", defaultValue = "DEFAULT"),
			@ApiImplicitParam(name = "lang", dataType = "String", defaultValue = "en") })
	public ReadableTransaction init(@Valid @RequestBody PersistableCustomPayment payment, @PathVariable String code,
			@ApiIgnore MerchantStore merchantStore, @ApiIgnore Language language) throws Exception {

		final ShoppingCart cart = shoppingCartService.getByCode(code, merchantStore);
		if (cart == null) {
			throw new ResourceNotFoundException("Cart code " + code + " does not exist");
		}

		final PersistableCustomPaymentPopulator populator = new PersistableCustomPaymentPopulator();
		populator.setPricingService(pricingService);

		final Payment paymentModel = new Payment();

		populator.populate(payment, paymentModel, merchantStore, language);

		final Transaction transactionModel = paymentService.initTransaction(null, paymentModel, merchantStore);

		final ReadableTransaction transaction = new ReadableTransaction();
		final ReadableTransactionPopulator trxPopulator = new ReadableTransactionPopulator();
		trxPopulator.setOrderService(orderService);
		trxPopulator.setPricingService(pricingService);

		trxPopulator.populate(transactionModel, transaction, merchantStore, language);

		return transaction;

	}

	@RequestMapping(value = { "/auth/cart/{code}/payment/custom-init" }, method = RequestMethod.POST)
	@ResponseBody
	@ApiImplicitParams({ @ApiImplicitParam(name = "store", dataType = "String", defaultValue = "DEFAULT"),
			@ApiImplicitParam(name = "lang", dataType = "String", defaultValue = "en") })
	public ReadableTransaction init(@Valid @RequestBody PersistableCustomPayment payment, @PathVariable String code,
			@ApiIgnore MerchantStore merchantStore, @ApiIgnore Language language, HttpServletRequest request,
			HttpServletResponse response) throws Exception {

		try {
			final Principal principal = request.getUserPrincipal();
			final String userName = principal.getName();

			final Customer customer = customerService.getByNick(userName);

			if (customer == null) {
				response.sendError(401, "Error while initializing the payment customer not authorized");
				return null;
			}

			final ShoppingCart cart = shoppingCartService.getByCode(code, merchantStore);
			if (cart == null) {

				throw new ResourceNotFoundException("Cart code " + code + " does not exist");
			}

			if (cart.getCustomerId() == null) {
				response.sendError(404, "Cart code " + code + " does not exist for exist for user " + userName);
				return null;
			}

			if (cart.getCustomerId().longValue() != customer.getId().longValue()) {
				response.sendError(404, "Cart code " + code + " does not exist for exist for user " + userName);
				return null;
			}

			final PersistableCustomPaymentPopulator populator = new PersistableCustomPaymentPopulator();
			populator.setPricingService(pricingService);

			final Payment paymentModel = new Payment();

			populator.populate(payment, paymentModel, merchantStore, language);

			final Transaction transactionModel = paymentService.initTransaction(customer, paymentModel, merchantStore);

			final ReadableTransaction transaction = new ReadableTransaction();
			final ReadableTransactionPopulator trxPopulator = new ReadableTransactionPopulator();
			trxPopulator.setOrderService(orderService);
			trxPopulator.setPricingService(pricingService);

			trxPopulator.populate(transactionModel, transaction, merchantStore, language);

			return transaction;

		} catch (final Exception e) {
			LOGGER.error("Error while initializing the payment", e);
			try {
				response.sendError(503, "Error while initializing the payment " + e.getMessage());
			} catch (final Exception ignore) {
			}
			return null;
		}
	}

}
