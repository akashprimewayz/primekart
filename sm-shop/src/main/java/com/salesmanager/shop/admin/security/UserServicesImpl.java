package com.salesmanager.shop.admin.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.user.GroupService;
import com.salesmanager.core.business.services.user.PermissionService;
import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.user.Group;
import com.salesmanager.core.model.user.GroupType;
import com.salesmanager.core.model.user.Permission;
import com.salesmanager.shop.constants.Constants;

/**
 *
 * @author casams1
 *         http://stackoverflow.com/questions/5105776/spring-security-with
 *         -custom-user-details
 */
@Service("userDetailsService")
public class UserServicesImpl implements WebUserServices {

	private static final Logger LOGGER = LoggerFactory.getLogger(UserServicesImpl.class);

	private static final String DEFAULT_INITIAL_PASSWORD = "password";

	@Inject
	private UserService userService;

	@Inject
	private MerchantStoreService merchantStoreService;

	@Inject
	@Named("passwordEncoder")
	private PasswordEncoder passwordEncoder;

	@Inject
	protected PermissionService permissionService;

	@Inject
	protected GroupService groupService;

	public final static String ROLE_PREFIX = "ROLE_";

	public UserDetails loadUserByUsername(String userName) throws UsernameNotFoundException, DataAccessException {

		com.salesmanager.core.model.user.User user = null;
		final Collection<GrantedAuthority> authorities = new ArrayList<>();

		try {

			user = userService.getByUserName(userName);

			if (user == null) {
				return null;
			}

			final GrantedAuthority role = new SimpleGrantedAuthority(ROLE_PREFIX + Constants.PERMISSION_AUTHENTICATED);// required
																														// to
																														// login
			authorities.add(role);

			final List<Integer> groupsId = new ArrayList<>();
			final List<Group> groups = user.getGroups();
			for (final Group group : groups) {

				groupsId.add(group.getId());

			}

			final List<Permission> permissions = permissionService.getPermissions(groupsId);
			for (final Permission permission : permissions) {
				final GrantedAuthority auth = new SimpleGrantedAuthority(ROLE_PREFIX + permission.getPermissionName());
				authorities.add(auth);
			}

		} catch (final Exception e) {
			LOGGER.error("Exception while querrying user", e);
			throw new SecurityDataAccessException("Exception while querrying user", e);
		}

		final User secUser = new User(userName, user.getAdminPassword(), user.isActive(), true, true, true,
				authorities);
		return secUser;
	}

	public void createDefaultAdmin() throws Exception {

		final MerchantStore store = merchantStoreService.getByCode(MerchantStore.DEFAULT_STORE);

		final String password = passwordEncoder.encode(DEFAULT_INITIAL_PASSWORD);

		final List<Group> groups = groupService.listGroup(GroupType.ADMIN);

		// creation of the super admin admin:password)
		final com.salesmanager.core.model.user.User user = new com.salesmanager.core.model.user.User(
				"admin@shopizer.com", password, "admin@shopizer.com");
		user.setFirstName("Administrator");
		user.setLastName("User");

		for (final Group group : groups) {
			if (group.getGroupName().equals(Constants.GROUP_SUPERADMIN)
					|| group.getGroupName().equals(Constants.GROUP_ADMIN)) {
				user.getGroups().add(group);
			}
		}

		user.setMerchantStore(store);
		userService.create(user);

	}

}
