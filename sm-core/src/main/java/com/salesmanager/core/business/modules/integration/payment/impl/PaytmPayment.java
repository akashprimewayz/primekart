package com.salesmanager.core.business.modules.integration.payment.impl;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.SimpleFormatter;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.paytm.merchant.models.PaymentDetail;
import com.paytm.merchant.models.SDKResponse;
import com.paytm.merchant.models.Time;
import com.paytm.pg.constants.LibraryConstants;
import com.paytm.pg.constants.MerchantProperties;
import com.paytm.pg.merchant.PaytmChecksum;
import com.paytm.pg.utils.CommonUtil;
import com.paytm.pgplus.enums.EChannelId;
import com.paytm.pgplus.enums.EnumCurrency;
import com.paytm.pgplus.models.Money;
import com.paytm.pgplus.models.UserInfo;
import com.paytm.pgplus.response.InitiateTransactionResponse;
import com.salesmanager.core.business.utils.ProductPriceUtils;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.payments.Payment;
import com.salesmanager.core.model.payments.PaymentType;
import com.salesmanager.core.model.payments.Transaction;
import com.salesmanager.core.model.payments.TransactionType;
import com.salesmanager.core.model.shoppingcart.ShoppingCartItem;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.core.model.system.IntegrationModule;
import com.salesmanager.core.modules.integration.IntegrationException;
import com.salesmanager.core.modules.integration.payment.model.PaymentModule;
import com.salesmanager.core.modules.integration.payment.model.PaymentModuleCustom;
import com.stripe.Stripe;
// import com.stripe.exception.APIConnectionException;
import com.stripe.exception.AuthenticationException;
import com.stripe.exception.CardException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.Refund;

public class PaytmPayment implements PaymentModuleCustom {

	@Inject
	private ProductPriceUtils productPriceUtils;

	
	private String str;

	private final static String AUTHORIZATION = "Authorization";
	private final static String TRANSACTION = "Transaction";

	private static final Logger LOGGER = LoggerFactory.getLogger(StripePayment.class);

	@Override
	public void validateModuleConfiguration(
			IntegrationConfiguration integrationConfiguration,
			MerchantStore store) throws IntegrationException {


		List<String> errorFields = null;


		Map<String,String> keys = integrationConfiguration.getIntegrationKeys();

		//validate integrationKeys['secretKey']
		if(keys==null || StringUtils.isBlank(keys.get("secretKey"))) {
			errorFields = new ArrayList<String>();
			errorFields.add("secretKey");
		}

		//validate integrationKeys['publishableKey']
		if(keys==null || StringUtils.isBlank(keys.get("publishableKey"))) {
			if(errorFields==null) {
				errorFields = new ArrayList<String>();
			}
			errorFields.add("publishableKey");
		}


		if(errorFields!=null) {
			IntegrationException ex = new IntegrationException(IntegrationException.ERROR_VALIDATION_SAVE);
			ex.setErrorFields(errorFields);
			throw ex;

		}



	}


	@Override
	public Transaction initTransaction(MerchantStore store, Customer customer,
			BigDecimal amount, Payment payment,
			IntegrationConfiguration configuration, IntegrationModule module)
					throws IntegrationException {
	      Validate.notNull(configuration,"Configuration cannot be null");
	      String publicKey = configuration.getIntegrationKeys().get("publishableKey");
	      Validate.notNull(publicKey,"Publishable key not found in configuration");

	      Transaction transaction = new Transaction();
	      transaction.setAmount(amount);
	      transaction.setDetails(publicKey);
	      transaction.setPaymentType(payment.getPaymentType());
	      transaction.setTransactionDate(new Date());
	      transaction.setTransactionType(payment.getTransactionType());
	      
	      return transaction;
		}

	@Override
	public Transaction authorize(MerchantStore store, Customer customer,
			List<ShoppingCartItem> items, BigDecimal amount, Payment payment,
			IntegrationConfiguration configuration, IntegrationModule module)
					throws IntegrationException {

		Transaction transaction = new Transaction();
		try {


			String apiKey = configuration.getIntegrationKeys().get("secretKey");

			if(payment.getPaymentMetaData()==null || StringUtils.isBlank(apiKey)) {
				IntegrationException te = new IntegrationException(
						"Can't process Stripe, missing payment.metaData");
				te.setExceptionType(IntegrationException.TRANSACTION_EXCEPTION);
				te.setMessageCode("message.payment.error");
				te.setErrorCode(IntegrationException.TRANSACTION_EXCEPTION);
				throw te;
			}

			/**
			 * this is send by stripe from tokenization ui
			 */
			String token = payment.getPaymentMetaData().get("stripe_token");

			if(StringUtils.isBlank(token)) {
				IntegrationException te = new IntegrationException(
						"Can't process Stripe, missing stripe token");
				te.setExceptionType(IntegrationException.TRANSACTION_EXCEPTION);
				te.setMessageCode("message.payment.error");
				te.setErrorCode(IntegrationException.TRANSACTION_EXCEPTION);
				throw te;
			}


			String amnt = productPriceUtils.getAdminFormatedAmount(store, amount);

			//stripe does not support floating point
			//so amnt * 100 or remove floating point
			//553.47 = 55347

			String strAmount = String.valueOf(amnt);
			strAmount = strAmount.replace(".","");

			Map<String, Object> chargeParams = new HashMap<String, Object>();
			chargeParams.put("amount", strAmount);
			chargeParams.put("capture", false);
			chargeParams.put("currency", store.getCurrency().getCode());
			chargeParams.put("source", token); // obtained with Stripe.js
			chargeParams.put("description", new StringBuilder().append(TRANSACTION).append(" - ").append(store.getStorename()).toString());

			Stripe.apiKey = apiKey;


			Charge ch = Charge.create(chargeParams);

			//Map<String,String> metadata = ch.getMetadata();


			transaction.setAmount(amount);
			//transaction.setOrder(order);
			transaction.setTransactionDate(new Date());
			transaction.setTransactionType(TransactionType.AUTHORIZE);
			transaction.setPaymentType(PaymentType.CREDITCARD);
			transaction.getTransactionDetails().put("TRANSACTIONID", token);
			transaction.getTransactionDetails().put("TRNAPPROVED", ch.getStatus());
			transaction.getTransactionDetails().put("TRNORDERNUMBER", ch.getId());
			transaction.getTransactionDetails().put("MESSAGETEXT", null);

		} catch (Exception e) {

			throw buildException(e);

		} 

		return transaction;


	}

	@Override
	public Transaction capture(MerchantStore store, Customer customer,
			Order order, Transaction capturableTransaction,
			IntegrationConfiguration configuration, IntegrationModule module)
					throws IntegrationException {


		Transaction transaction = new Transaction();
		try {


			String apiKey = configuration.getIntegrationKeys().get("secretKey");

			if(StringUtils.isBlank(apiKey)) {
				IntegrationException te = new IntegrationException(
						"Can't process Stripe, missing payment.metaData");
				te.setExceptionType(IntegrationException.TRANSACTION_EXCEPTION);
				te.setMessageCode("message.payment.error");
				te.setErrorCode(IntegrationException.TRANSACTION_EXCEPTION);
				throw te;
			}

			String chargeId = capturableTransaction.getTransactionDetails().get("TRNORDERNUMBER");

			if(StringUtils.isBlank(chargeId)) {
				IntegrationException te = new IntegrationException(
						"Can't process Stripe capture, missing TRNORDERNUMBER");
				te.setExceptionType(IntegrationException.TRANSACTION_EXCEPTION);
				te.setMessageCode("message.payment.error");
				te.setErrorCode(IntegrationException.TRANSACTION_EXCEPTION);
				throw te;
			}


			Stripe.apiKey = apiKey;

			Charge ch = Charge.retrieve(chargeId);
			ch.capture();


			transaction.setAmount(order.getTotal());
			transaction.setOrder(order);
			transaction.setTransactionDate(new Date());
			transaction.setTransactionType(TransactionType.CAPTURE);
			transaction.setPaymentType(PaymentType.CREDITCARD);
			transaction.getTransactionDetails().put("TRANSACTIONID", capturableTransaction.getTransactionDetails().get("TRANSACTIONID"));
			transaction.getTransactionDetails().put("TRNAPPROVED", ch.getStatus());
			transaction.getTransactionDetails().put("TRNORDERNUMBER", ch.getId());
			transaction.getTransactionDetails().put("MESSAGETEXT", null);

			//authorize a preauth 


			return transaction;

		} catch (Exception e) {

			throw buildException(e);

		}  

	}

	@Override
	public Transaction authorizeAndCapture(Order order,MerchantStore store, Customer customer,
			List<ShoppingCartItem> items, BigDecimal amount, Payment payment,
			IntegrationConfiguration configuration, IntegrationModule module)
					throws IntegrationException {
		//setInitialParameters();
		String token=createTxnTokenwithRequiredParams(order,store, customer, amount, payment, configuration, module);

		Transaction transaction = new Transaction();
		
		
		transaction.setAmount(amount);
		//transaction.setOrder(order);
		transaction.setTransactionDate(new Date());
		transaction.setTransactionType(TransactionType.AUTHORIZECAPTURE);
		transaction.setPaymentType(PaymentType.CREDITCARD);
		JSONObject paytmResponseParams = new JSONObject(token);
		JSONObject paytmResponseBody = (JSONObject) paytmResponseParams.get("body");
		transaction.getTransactionDetails().put("INITIATETRANSACTIONID", paytmResponseBody.getString("txnToken"));
		transaction.getTransactionDetails().put("TRNAPPROVED", "PENDING");
		return transaction;  

	}

	@Override
	public Transaction refund(boolean partial, MerchantStore store, Transaction transaction,
			Order order, BigDecimal amount,
			IntegrationConfiguration configuration, IntegrationModule module)
					throws IntegrationException {



		String apiKey = configuration.getIntegrationKeys().get("secretKey");

		if(StringUtils.isBlank(apiKey)) {
			IntegrationException te = new IntegrationException(
					"Can't process Stripe, missing payment.metaData");
			te.setExceptionType(IntegrationException.TRANSACTION_EXCEPTION);
			te.setMessageCode("message.payment.error");
			te.setErrorCode(IntegrationException.TRANSACTION_EXCEPTION);
			throw te;
		}

		try {


			String trnID = transaction.getTransactionDetails().get("TRNORDERNUMBER");

			String amnt = productPriceUtils.getAdminFormatedAmount(store, amount);

			Stripe.apiKey = apiKey;

			//stripe does not support floating point
			//so amnt * 100 or remove floating point
			//553.47 = 55347

			String strAmount = String.valueOf(amnt);
			strAmount = strAmount.replace(".","");

			Charge ch = Charge.retrieve(trnID);

			Map<String, Object> params = new HashMap<>();
			params.put("charge", ch.getId());
			params.put("amount", strAmount);
			Refund re = Refund.create(params);

			transaction = new Transaction();
			transaction.setAmount(order.getTotal());
			transaction.setOrder(order);
			transaction.setTransactionDate(new Date());
			transaction.setTransactionType(TransactionType.CAPTURE);
			transaction.setPaymentType(PaymentType.CREDITCARD);
			transaction.getTransactionDetails().put("TRANSACTIONID", transaction.getTransactionDetails().get("TRANSACTIONID"));
			transaction.getTransactionDetails().put("TRNAPPROVED", re.getReason());
			transaction.getTransactionDetails().put("TRNORDERNUMBER", re.getId());
			transaction.getTransactionDetails().put("MESSAGETEXT", null);

			return transaction;


		} catch(Exception e) {

			throw buildException(e);

		} 



	}

	private IntegrationException buildException(Exception ex) {


		if(ex instanceof CardException) {
			CardException e = (CardException)ex;
			// Since it's a decline, CardException will be caught
			//System.out.println("Status is: " + e.getCode());
			//System.out.println("Message is: " + e.getMessage());


			/**
			 * 
				invalid_number 	The card number is not a valid credit card number.
				invalid_expiry_month 	The card's expiration month is invalid.
				invalid_expiry_year 	The card's expiration year is invalid.
				invalid_cvc 	The card's security code is invalid.
				incorrect_number 	The card number is incorrect.
				expired_card 	The card has expired.
				incorrect_cvc 	The card's security code is incorrect.
				incorrect_zip 	The card's zip code failed validation.
				card_declined 	The card was declined.
				missing 	There is no card on a customer that is being charged.
				processing_error 	An error occurred while processing the card.
				rate_limit 	An error occurred due to requests hitting the API too quickly. Please let us know if you're consistently running into this error.
			 */


			String declineCode = e.getDeclineCode();

			if("card_declined".equals(declineCode)) {
				IntegrationException te = new IntegrationException(
						"Can't process stripe message " + e.getMessage());
				te.setExceptionType(IntegrationException.EXCEPTION_PAYMENT_DECLINED);
				te.setMessageCode("message.payment.declined");
				te.setErrorCode(IntegrationException.TRANSACTION_EXCEPTION);
				return te;
			}

			if("invalid_number".equals(declineCode)) {
				IntegrationException te = new IntegrationException(
						"Can't process stripe message " + e.getMessage());
				te.setExceptionType(IntegrationException.EXCEPTION_VALIDATION);
				te.setMessageCode("messages.error.creditcard.number");
				te.setErrorCode(IntegrationException.EXCEPTION_VALIDATION);
				return te;
			}

			if("invalid_expiry_month".equals(declineCode)) {
				IntegrationException te = new IntegrationException(
						"Can't process stripe message " + e.getMessage());
				te.setExceptionType(IntegrationException.EXCEPTION_VALIDATION);
				te.setMessageCode("messages.error.creditcard.dateformat");
				te.setErrorCode(IntegrationException.EXCEPTION_VALIDATION);
				return te;
			}

			if("invalid_expiry_year".equals(declineCode)) {
				IntegrationException te = new IntegrationException(
						"Can't process stripe message " + e.getMessage());
				te.setExceptionType(IntegrationException.EXCEPTION_VALIDATION);
				te.setMessageCode("messages.error.creditcard.dateformat");
				te.setErrorCode(IntegrationException.EXCEPTION_VALIDATION);
				return te;
			}

			if("invalid_cvc".equals(declineCode)) {
				IntegrationException te = new IntegrationException(
						"Can't process stripe message " + e.getMessage());
				te.setExceptionType(IntegrationException.EXCEPTION_VALIDATION);
				te.setMessageCode("messages.error.creditcard.cvc");
				te.setErrorCode(IntegrationException.EXCEPTION_VALIDATION);
				return te;
			}

			if("incorrect_number".equals(declineCode)) {
				IntegrationException te = new IntegrationException(
						"Can't process stripe message " + e.getMessage());
				te.setExceptionType(IntegrationException.EXCEPTION_VALIDATION);
				te.setMessageCode("messages.error.creditcard.number");
				te.setErrorCode(IntegrationException.EXCEPTION_VALIDATION);
				return te;
			}

			if("incorrect_cvc".equals(declineCode)) {
				IntegrationException te = new IntegrationException(
						"Can't process stripe message " + e.getMessage());
				te.setExceptionType(IntegrationException.EXCEPTION_VALIDATION);
				te.setMessageCode("messages.error.creditcard.cvc");
				te.setErrorCode(IntegrationException.EXCEPTION_VALIDATION);
				return te;
			}

			//nothing good so create generic error
			IntegrationException te = new IntegrationException(
					"Can't process stripe card  " + e.getMessage());
			te.setExceptionType(IntegrationException.EXCEPTION_VALIDATION);
			te.setMessageCode("messages.error.creditcard.number");
			te.setErrorCode(IntegrationException.EXCEPTION_VALIDATION);
			return te;



		} else if (ex instanceof InvalidRequestException) {
			LOGGER.error("InvalidRequest error with stripe", ex.getMessage());
			InvalidRequestException e =(InvalidRequestException)ex;
			IntegrationException te = new IntegrationException(
					"Can't process Stripe, missing invalid payment parameters");
			te.setExceptionType(IntegrationException.TRANSACTION_EXCEPTION);
			te.setMessageCode("messages.error.creditcard.number");
			te.setErrorCode(IntegrationException.TRANSACTION_EXCEPTION);
			return te;

		} else if (ex instanceof AuthenticationException) {
			LOGGER.error("Authentication error with stripe", ex.getMessage());
			AuthenticationException e = (AuthenticationException)ex;
			// Authentication with Stripe's API failed
			// (maybe you changed API keys recently)
			IntegrationException te = new IntegrationException(
					"Can't process Stripe, missing invalid payment parameters");
			te.setExceptionType(IntegrationException.TRANSACTION_EXCEPTION);
			te.setMessageCode("message.payment.error");
			te.setErrorCode(IntegrationException.TRANSACTION_EXCEPTION);
			return te;

		} /*else if (ex instanceof APIConnectionException) { // DEPRECATED THIS EXCEPTION TYPE
		LOGGER.error("API connection error with stripe", ex.getMessage());
		APIConnectionException e = (APIConnectionException)ex;
		  // Network communication with Stripe failed
		IntegrationException te = new IntegrationException(
				"Can't process Stripe, missing invalid payment parameters");
		te.setExceptionType(IntegrationException.TRANSACTION_EXCEPTION);
		te.setMessageCode("message.payment.error");
		te.setErrorCode(IntegrationException.TRANSACTION_EXCEPTION);
		return te;
	} */else if (ex instanceof StripeException) {
		LOGGER.error("Error with stripe", ex.getMessage());
		StripeException e = (StripeException)ex;
		// Display a very generic error to the user, and maybe send
		// yourself an email
		IntegrationException te = new IntegrationException(
				"Can't process Stripe authorize, missing invalid payment parameters");
		te.setExceptionType(IntegrationException.TRANSACTION_EXCEPTION);
		te.setMessageCode("message.payment.error");
		te.setErrorCode(IntegrationException.TRANSACTION_EXCEPTION);
		return te;



	} else if (ex instanceof Exception) {
		LOGGER.error("Stripe module error", ex.getMessage());
		if(ex instanceof IntegrationException) {
			return (IntegrationException)ex;
		} else {
			IntegrationException te = new IntegrationException(
					"Can't process Stripe authorize, exception", ex);
			te.setExceptionType(IntegrationException.TRANSACTION_EXCEPTION);
			te.setMessageCode("message.payment.error");
			te.setErrorCode(IntegrationException.TRANSACTION_EXCEPTION);
			return te;
		}


	} else {
		LOGGER.error("Stripe module error", ex.getMessage());
		IntegrationException te = new IntegrationException(
				"Can't process Stripe authorize, exception", ex);
		te.setExceptionType(IntegrationException.TRANSACTION_EXCEPTION);
		te.setMessageCode("message.payment.error");
		te.setErrorCode(IntegrationException.TRANSACTION_EXCEPTION);
		return te;
	}

	}

	public static void setInitialParameters() {
		/** Initialize mandatory Parameters */
		String env = LibraryConstants.STAGING_ENVIRONMENT;
		// Find your Merchant ID and Merchant Key in your Paytm Dashboard at https://dashboard.paytm.com/next/apikeys
		String mid = "LHMWXe52738162569788";
		String key = "hWuDk3sjWKk9D2&5";
		/* Website: For Staging - WEBSTAGING, For Production - DEFAULT */
		String website = "WEBSTAGING";
		/* Client Id e.g C11 */
		String clientid = "abc001";

		/** Setting Callback URL */
		String callbackUrl = "http://localhost:8080/api/v1/pgresponse";
		MerchantProperties.setCallbackUrl(callbackUrl);

		/** Setting Initial Parameters */
		MerchantProperties.initialize(env, mid, key, clientid, website);

		/** Setting timeout for connection i.e. Connection Timeout */
		MerchantProperties.setConnectionTimeout(new Time(5, TimeUnit.MINUTES));
	}

	/**
	 * Merchant can use createTxnTokenwithRequiredParams method to get token with
	 * required parameters.
	 * 
	 * This method create a PaymentDetail object having all the required parameters
	 * (Merchant can change these values according to his requirements) and calls
	 * SDK's createTxnToken method to get the
	 * {@link SDKResponse}(InitiateTransactionResponse) object having token which
	 * will be used in future transactions such as getting payment options.
	 * @return 
	 */
	public  String createTxnTokenwithRequiredParams(Order order,MerchantStore store, Customer customer, BigDecimal amount, Payment payment,
			IntegrationConfiguration configuration, IntegrationModule module) {
		 
			String responseData = "";
			String env = LibraryConstants.STAGING_ENVIRONMENT;
			// Find your Merchant ID and Merchant Key in your Paytm Dashboard at https://dashboard.paytm.com/next/apikeys
			String mid = "LHMWXe52738162569788";
			String key = "hWuDk3sjWKk9D2&5";
			/* Website: For Staging - WEBSTAGING, For Production - DEFAULT */
			String website = "WEBSTAGING";
			/* Client Id e.g C11 */
			
			JSONObject paytmParams = new JSONObject();

			JSONObject body = new JSONObject();
			body.put("requestType", "Payment");
			body.put("mid", mid);
			body.put("websiteName", website);
			body.put("orderId", order.getId());
			body.put("callbackUrl", "http://localhost:8080/api/v1/paytm/processOrderAfterPayment");
			JSONObject txnAmount = new JSONObject();
			txnAmount.put("value", amount);
			txnAmount.put("currency", "INR");

			JSONObject userInfo = new JSONObject();
			userInfo.put("custId", customer.getId());
			body.put("txnAmount", txnAmount);
			body.put("userInfo", userInfo);

			JSONObject extendInfo = new JSONObject();
			extendInfo.put("udf1", order.getMerchant().getCode());
			extendInfo.put("udf2", order.getMerchant().getCode());

			body.put("extendInfo", extendInfo);
			/*
			 * Generate checksum by parameters we have in body
			 * You can get Checksum JAR from https://developer.paytm.com/docs/checksum/
			 * Find your Merchant Key in your Paytm Dashboard at https://dashboard.paytm.com/next/apikeys
			 */

		
			/* for Production */
			// URL url = new URL("https://securegw.paytm.in/theia/api/v1/initiateTransaction?mid=YOUR_MID_HERE&orderId=ORDERID_98765");

			try {
				
				String checksum = PaytmChecksum.generateSignature(body.toString(), key);

				JSONObject head = new JSONObject();
				head.put("signature", checksum);

				paytmParams.put("body", body);
				paytmParams.put("head", head);

				String post_data = paytmParams.toString();
				System.out.println("post_dataRequest"+post_data);
				/* for Staging */
				URL url = new URL("https://securegw-stage.paytm.in/theia/api/v1/initiateTransaction?mid="+mid+"&orderId="+order.getId());

				HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		
				
				connection.setRequestMethod("POST");
				connection.setRequestProperty("Content-Type", "application/json");
				connection.setDoOutput(true);

				DataOutputStream requestWriter = new DataOutputStream(connection.getOutputStream());
				requestWriter.writeBytes(post_data);
				requestWriter.close();
				InputStream is = connection.getInputStream();
				BufferedReader responseReader = new BufferedReader(new InputStreamReader(is));
				if ((responseData = responseReader.readLine()) != null) {
					System.out.append("Response: " + responseData);
				}

				responseReader.close();
			} catch (Exception exception) {
				exception.printStackTrace();
			}

		 return responseData;
	 }

	public static String generateRandomString(int count) {

		StringBuilder builder = new StringBuilder();
		while (count-- != 0) {
			int character = new Random().nextInt(9);
			builder.append(character);
		}
		return builder.toString();
	}


	public String getStr() {
		return str;
	}


	public void setStr(String str) {
		this.str = str;
	}
	
	
}
