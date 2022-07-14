package com.salesmanager.shop.store.api.v1.order;

import java.io.IOException;
import java.security.Principal;
import java.util.Locale;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.services.customer.CustomerService;
import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.order.OrderService;
import com.salesmanager.core.business.services.payments.TransactionService;
import com.salesmanager.core.business.services.shoppingcart.ShoppingCartService;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.payments.Transaction;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.core.model.shoppingcart.ShoppingCart;
import com.salesmanager.shop.model.order.v1.PaytmPaymentResponse;
import com.salesmanager.shop.model.order.v1.PersistableCustomOrder;
import com.salesmanager.shop.model.order.v1.ReadableOrderConfirmation;
import com.salesmanager.shop.model.shoppingcart.ReadableShoppingCart;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.controller.order.facade.OrderFacadeCustom;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Tag;
import springfox.documentation.annotations.ApiIgnore;

@RestController
@RequestMapping("/api/v1")
@Api(tags = { "Ordering api (Order Flow Api)" })
@SwaggerDefinition(tags = { @Tag(name = "Order flow resource", description = "Manage orders (create, list, get)") })
public class OrderCustomApi {

	private static final Logger LOGGER = LoggerFactory.getLogger(OrderCustomApi.class);

	@Value("$(custom.url)")
	private String url;

	@Inject
	private CustomerService customerService;

	@Inject
	private OrderFacadeCustom orderFacadeCustom;

	@Inject
	private OrderService orderService;

	@Inject
	@Qualifier("transactionServiceCustom")
	private TransactionService transactionService;

	@Inject
	private com.salesmanager.shop.store.controller.order.facade.v1.OrderFacade orderFacadeV1;

	@Inject
	private ShoppingCartService shoppingCartService;

	@Autowired
	private MerchantStoreService merchantStoreService;

	@RequestMapping(value = { "/auth/cart/{code}/paytmCode" }, method = RequestMethod.POST)
	@ResponseStatus(HttpStatus.OK)
	@ResponseBody
	@ApiImplicitParams({ @ApiImplicitParam(name = "store", dataType = "string", defaultValue = "DEFAULT"),
			@ApiImplicitParam(name = "lang", dataType = "string", defaultValue = "en") })
	public ReadableShoppingCart getPaytmTransactionCode(@PathVariable final String code, // shopping cart
			@Valid @RequestBody PersistableCustomOrder order, // order
			@ApiIgnore MerchantStore merchantStore, @ApiIgnore Language language, HttpServletRequest request,
			HttpServletResponse response, Locale locale) throws Exception {

		try {

			final Principal principal = request.getUserPrincipal();
			final String userName = principal.getName();

			final Customer customer = customerService.getByNick(userName);

			if (customer == null) {
				response.sendError(401, "Error while performing checkout customer not authorized");
				return null;
			}

			final ShoppingCart cart = shoppingCartService.getByCode(code, merchantStore);
			if (cart == null) {
				throw new ResourceNotFoundException("Cart code " + code + " does not exist");
			}

			order.setShoppingCartId(cart.getId());
			order.setCustomerId(customer.getId());// That is an existing customer purchasing

			final Order modelOrder = orderFacadeCustom.processOrder(order, customer, merchantStore, language, locale);
			final Long orderId = modelOrder.getId();
			modelOrder.setId(orderId);

			final Transaction transaction = transactionService.getCapturableTransaction(modelOrder);

			final ReadableShoppingCart readableShoppingCart = new ReadableShoppingCart();
			readableShoppingCart.setTxnId(transaction.getTransactionDetails().get("INITIATETRANSACTIONID"));
			readableShoppingCart.setOrder(modelOrder.getId());

			return readableShoppingCart;

		} catch (final Exception e) {
			LOGGER.error("Error while processing checkout", e);
			try {
				response.sendError(503, "Error while processing checkout " + e.getMessage());
			} catch (final Exception ignore) {
			}
			return null;
		}
	}

	@PostMapping(value = "/paytm/processOrderAfterPayment")
	public void getResponseRedirect(HttpServletRequest request, HttpServletResponse response) {
		final PaytmPaymentResponse paytmPaymentResponse = new PaytmPaymentResponse();

		paytmPaymentResponse.setBANKNAME(request.getParameter("BANKNAME"));
		paytmPaymentResponse.setBANKTXNID(request.getParameter("BANKTXNID"));
		paytmPaymentResponse.setCHECKSUMHASH(request.getParameter("CHECKSUMHASH"));
		paytmPaymentResponse.setCURRENCY(request.getParameter("CURRENCY"));
		paytmPaymentResponse.setGATEWAYNAME(request.getParameter("GATEWAYNAME"));
		paytmPaymentResponse.setMID(request.getParameter("MID"));
		paytmPaymentResponse.setORDERID(request.getParameter("ORDERID"));
		paytmPaymentResponse.setPAYMENTMODE(request.getParameter("PAYMENTMODE"));
		paytmPaymentResponse.setRESPCODE(request.getParameter("RESPCODE"));
		paytmPaymentResponse.setRESPMSG(request.getParameter("RESPMSG"));
		paytmPaymentResponse.setSTATUS(request.getParameter("STATUS"));
		paytmPaymentResponse.setTXNAMOUNT(request.getParameter("TXNAMOUNT"));
		paytmPaymentResponse.setTXNDATE(request.getParameter("TXNDATE"));
		paytmPaymentResponse.setTXNID(request.getParameter("TXNID"));
		paytmPaymentResponse.setStoreCode(request.getParameter("UDF_1"));
		try {
			final MerchantStore store = merchantStoreService.getByCode(paytmPaymentResponse.getStoreCode());
			final Long orderId = Long.parseLong(paytmPaymentResponse.getORDERID());
			final Order order = orderService.getOrder(orderId, store);
			final Customer customer = customerService.getById(order.getCustomerId());
			final Order modelOrder = orderFacadeCustom.processOrderAfterpayment(order, customer, store);

			final Transaction transaction = transactionService.getCapturableTransaction(modelOrder);

			transaction.getTransactionDetails().put("TRANSACTIONID", paytmPaymentResponse.getTXNID());
			transaction.getTransactionDetails().put("TRNAPPROVED", paytmPaymentResponse.getSTATUS());
			transaction.getTransactionDetails().put("BANKTXNID", paytmPaymentResponse.getBANKTXNID());
			transaction.getTransactionDetails().put("BANKNAME", paytmPaymentResponse.getBANKNAME());
			transaction.getTransactionDetails().put("TXNAMOUNT", paytmPaymentResponse.getBANKNAME());
			transaction.getTransactionDetails().put("PAYMENTMODE", paytmPaymentResponse.getBANKNAME());
			transaction.getTransactionDetails().put("CURRENCY", paytmPaymentResponse.getCURRENCY());
			transaction.getTransactionDetails().put("TXNDATE", paytmPaymentResponse.getTXNDATE());
			transaction.getTransactionDetails().put("RESPMSG", paytmPaymentResponse.getRESPMSG());
			transaction.getTransactionDetails().put("GATEWAYNAME", paytmPaymentResponse.getGATEWAYNAME());
			transaction.getTransactionDetails().put("CHECKSUMHASH", paytmPaymentResponse.getCHECKSUMHASH());
			transaction.getTransactionDetails().put("RESPONSE_ORDERID", paytmPaymentResponse.getORDERID());

			transactionService.create(transaction);

			orderFacadeV1.orderConfirmation(modelOrder, customer, store, customer.getDefaultLanguage());

			if (paytmPaymentResponse.getSTATUS().equalsIgnoreCase("TXN_FAILURE")) {
				response.sendRedirect(url); // "http://localhost:3000/order-confirm"
			}
			response.sendRedirect(url); // "http://localhost:3000/order-confirm"
		} catch (final IOException | ServiceException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	@RequestMapping(value = { "/auth/cart/{code}/processOrderAfterPayment" }, method = RequestMethod.POST)
	@ResponseStatus(HttpStatus.OK)
	@ResponseBody
	@ApiImplicitParams({ @ApiImplicitParam(name = "store", dataType = "string", defaultValue = "DEFAULT"),
			@ApiImplicitParam(name = "lang", dataType = "string", defaultValue = "en") })
	public ReadableOrderConfirmation processOrderAfterPayment(@PathVariable final String code, // shopping cart
			@Valid @RequestBody PersistableCustomOrder order, // order
			@ApiIgnore MerchantStore merchantStore, @ApiIgnore Language language, HttpServletRequest request,
			HttpServletResponse response, Locale locale) throws Exception {

		try {
			final Principal principal = request.getUserPrincipal();
			final String userName = principal.getName();

			final Customer customer = customerService.getByNick(userName);

			if (customer == null) {
				response.sendError(401, "Error while performing checkout customer not authorized");
				return null;
			}

			final ShoppingCart cart = shoppingCartService.getByCode(code, merchantStore);
			if (cart == null) {
				throw new ResourceNotFoundException("Cart code " + code + " does not exist");
			}

			order.setShoppingCartId(cart.getId());
			order.setCustomerId(customer.getId());// That is an existing customer purchasing

			final Order modelOrder = orderFacadeCustom.processOrder(order, customer, merchantStore, language, locale);
			final Long orderId = modelOrder.getId();
			modelOrder.setId(orderId);

			return orderFacadeV1.orderConfirmation(modelOrder, customer, merchantStore, language);
		} catch (final Exception e) {
			LOGGER.error("Error while processing checkout", e);
			try {
				response.sendError(503, "Error while processing checkout " + e.getMessage());
			} catch (final Exception ignore) {
			}
			return null;
		}
	}
}
