package com.salesmanager.shop.store.api.v1.store;

import java.security.Principal;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.collect.ImmutableMap;
import com.salesmanager.shop.model.store.PersistableCustomMerchantStore;
import com.salesmanager.shop.model.store.ReadableMerchantStore;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;
import com.salesmanager.shop.store.controller.store.facade.StoreCustomFacade;
import com.salesmanager.shop.store.controller.user.facade.UserFacade;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Tag;

@RestController
@RequestMapping("/api/v1")
@Api(tags = { "Merchant and store management resource (Merchant - Store Management Api)" })
@SwaggerDefinition(tags = {
		@Tag(name = "Merchant and store management", description = "Edit merchants (retailers) and stores") })
public class MerchantStoreCustomApi {

	private static final Logger LOGGER = LoggerFactory.getLogger(MerchantStoreCustomApi.class);

	private static final Map<String, String> MAPPING_FIELDS = ImmutableMap.<String, String>builder().put("name", "name")
			.put("readableAudit.user", "auditSection.modifiedBy").build();

	@Inject
	private StoreCustomFacade storeCustomFacade;

	@Inject
	private UserFacade userFacade;

	@ResponseStatus(HttpStatus.OK)
	@PostMapping(value = { "/private/custom-store" }, produces = MediaType.APPLICATION_JSON_VALUE)
	@ApiOperation(httpMethod = "POST", value = "Creates a new store", notes = "", response = ReadableMerchantStore.class)
	public void create(@Valid @RequestBody PersistableCustomMerchantStore store) {

		final String authenticatedUser = userFacade.authenticatedUser();
		if (authenticatedUser == null) {
			throw new UnauthorizedException();
		}

		userFacade.authorizedGroup(authenticatedUser,
				Stream.of("SUPERADMIN", "ADMIN_RETAILER").collect(Collectors.toList()));

		storeCustomFacade.create(store);
	}

	@ResponseStatus(HttpStatus.OK)
	@PutMapping(value = { "/private/custom-store/{code}" }, produces = MediaType.APPLICATION_JSON_VALUE)
	@ApiOperation(httpMethod = "PUT", value = "Updates a store", notes = "", response = ReadableMerchantStore.class)
	public void update(@PathVariable String code, @Valid @RequestBody PersistableCustomMerchantStore store,
			HttpServletRequest request) {

		final String userName = getUserFromRequest(request);
		validateUserPermission(userName, code);
		store.setCode(code);
		storeCustomFacade.update(store);
	}

	private String getUserFromRequest(HttpServletRequest request) {
		// user doing action must be attached to the store being modified
		final Principal principal = request.getUserPrincipal();
		return principal.getName();
	}

	private void validateUserPermission(String userName, String code) {
		// TODO reviewed Spring Security should be used
		if (!userFacade.authorizedStore(userName, code)) {
			throw new UnauthorizedException("User " + userName + " not authorized");
		}
	}
}
