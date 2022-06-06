package com.salesmanager.shop.store.api.v1.customer;

import javax.inject.Inject;
import javax.validation.Valid;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.customer.PersistableCustomer;
import com.salesmanager.shop.store.api.exception.GenericRuntimeException;
import com.salesmanager.shop.store.controller.customer.facade.CustomerFacade;
import com.salesmanager.shop.store.security.AuthenticationResponse;
import com.salesmanager.shop.store.security.JWTTokenUtil;
import com.salesmanager.shop.store.security.user.JWTUser;
import com.salesmanager.shop.utils.AuthorizationUtils;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Tag;
import springfox.documentation.annotations.ApiIgnore;

@RestController
@RequestMapping("/api/v1")
@Api(tags = { "Customer authentication resource (Customer Authentication Api)" })
@SwaggerDefinition(tags = {
		@Tag(name = "Customer authentication resource", description = "Authenticates customer, register customer and reset customer password") })
public class AuthenticateCustomerCustomApi {

	private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticateCustomerCustomApi.class);

	@Value("${authToken.header}")
	private String tokenHeader;

	@Inject
	private AuthenticationManager jwtCustomerAuthenticationManager;

	@Inject
	private JWTTokenUtil jwtTokenUtil;

	@Inject
	private UserDetailsService jwtCustomerDetailsService;

	@Inject
	private CustomerFacade customerFacade;

	@Autowired
	AuthorizationUtils authorizationUtils;

	/**
	 * Create new customer for a given MerchantStore, then authenticate that
	 * customer
	 */
	@RequestMapping(value = { "/customer/custom-register" }, method = RequestMethod.POST, produces = {
			"application/json" })
	@ResponseStatus(HttpStatus.CREATED)
	@ApiOperation(httpMethod = "POST", value = "Registers a customer to the application", notes = "Used as self-served operation", response = AuthenticationResponse.class)
	@ApiImplicitParams({ @ApiImplicitParam(name = "store", dataType = "string", defaultValue = "DEFAULT"),
			@ApiImplicitParam(name = "lang", dataType = "string", defaultValue = "en") })
	@ResponseBody
	public ResponseEntity<?> register(@Valid @RequestBody PersistableCustomer customer,
			@ApiIgnore MerchantStore merchantStore, @ApiIgnore Language language) throws Exception {

		customer.setUserName(customer.getEmailAddress());
		customer.getBilling().setCountry("IN");
		if (customerFacade.checkIfUserExists(customer.getUserName(), merchantStore)) {
			// 409 Conflict
			throw new GenericRuntimeException("409",
					"Customer with email [" + customer.getEmailAddress() + "] is already registered");
		}

		Validate.notNull(customer.getUserName(), "Username cannot be null");
		Validate.notNull(customer.getBilling(), "Requires customer Country code");
		Validate.notNull(customer.getBilling().getCountry(), "Requires customer Country code");

		customerFacade.registerCustomer(customer, merchantStore, language);

		// Perform the security
		Authentication authentication = null;
		try {

			authentication = jwtCustomerAuthenticationManager.authenticate(
					new UsernamePasswordAuthenticationToken(customer.getUserName(), customer.getPassword()));

		} catch (final Exception e) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		if (authentication == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		SecurityContextHolder.getContext().setAuthentication(authentication);

		// Reload password post-security so we can generate token
		final JWTUser userDetails = (JWTUser) jwtCustomerDetailsService.loadUserByUsername(customer.getUserName());
		final String token = jwtTokenUtil.generateToken(userDetails);

		// Return the token
		return ResponseEntity.ok(new AuthenticationResponse(customer.getId(), token));

	}

}
