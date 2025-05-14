package com.mw.oidc.commerce.order;

import com.liferay.account.manager.CurrentAccountEntryManager;
import com.liferay.account.model.AccountEntry;
import com.liferay.commerce.currency.model.CommerceCurrency;
import com.liferay.commerce.currency.service.CommerceCurrencyLocalService;
import com.liferay.commerce.model.CommerceOrder;
import com.liferay.commerce.order.CommerceOrderHttpHelper;
import com.liferay.commerce.service.CommerceOrderLocalService;
import com.liferay.portal.configuration.metatype.bnd.util.ConfigurableUtil;
import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.UserEmailAddressException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.HttpComponentsUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.security.sso.openid.connect.OpenIdConnect;
import com.liferay.portal.security.sso.openid.connect.OpenIdConnectAuthenticationHandler;
import com.liferay.portal.security.sso.openid.connect.constants.OpenIdConnectConstants;
import com.liferay.portal.security.sso.openid.connect.constants.OpenIdConnectWebKeys;
import com.liferay.portal.security.sso.openid.connect.internal.exception.StrangersNotAllowedException;
import com.liferay.portal.security.sso.openid.connect.internal.session.manager.OfflineOpenIdConnectSessionManager;
import com.liferay.portal.servlet.filters.autologin.AutoLoginFilter;
import com.mw.commerce.order.config.CustomCommerceOrderConfiguration;

import java.util.List;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(
	configurationPid = {"com.liferay.portal.security.sso.openid.connect.configuration.OpenIdConnectConfiguration", CustomCommerceOrderConfiguration.PID},
	property = {
		"after-filter=Virtual Host Filter", "servlet-context-name=",
		"servlet-filter-name=SSO OpenId Connect Auto Login Filter",
		"url-pattern=" + OpenIdConnectConstants.REDIRECT_URL_PATTERN
	},
	service = Filter.class
)
public class CustomOpenIdConnectAutoLoginFilter extends AutoLoginFilter {

	@Activate
	protected void activate(Map<String, Object> properties) {
		_log.info("Activating...");
		
		_customCommerceOrderConfiguration = ConfigurableUtil.createConfigurable(CustomCommerceOrderConfiguration.class, properties);
		
		_log.info("enabled: " + _customCommerceOrderConfiguration.enabled());
		
		_log.info("commerceChannelGroupId: " + _customCommerceOrderConfiguration.commerceChannelGroupId());
		
		_log.info("currencyCode: " + _customCommerceOrderConfiguration.currencyCode());
		
		_log.info("Activated...");
	}		

	@Override
	public boolean isFilterEnabled(
		HttpServletRequest httpServletRequest,
		HttpServletResponse httpServletResponse) {

		return _openIdConnect.isEnabled(_portal.getCompanyId(httpServletRequest));
	}

	@Override
	protected Log getLog() {
		return _log;
	}

	@Override
	protected void processFilter(
			HttpServletRequest httpServletRequest,
			HttpServletResponse httpServletResponse, FilterChain filterChain)
		throws Exception {
		
		_log.info("Calling processFilter...");

		HttpSession httpSession = httpServletRequest.getSession(false);

		if (httpSession == null) {
			return;
		}

		if (_offlineOpenIdConnectSessionManager.isOpenIdConnectSession(
				httpSession)) {

			if (_log.isDebugEnabled()) {
				_log.debug("User is already authenticated");
			}

			return;
		}

		String actionURL = (String)httpSession.getAttribute(
			OpenIdConnectWebKeys.OPEN_ID_CONNECT_ACTION_URL);

		try {
			_openIdConnectAuthenticationHandler.processAuthenticationResponse(
				httpServletRequest, httpServletResponse,
				userId -> _autoLoginUser(
					httpServletRequest, httpServletResponse, userId));
		}
		catch (StrangersNotAllowedException |
			   UserEmailAddressException.MustNotUseCompanyMx exception) {

			Class<?> clazz = exception.getClass();

			actionURL = HttpComponentsUtil.addParameter(
				actionURL, "error", clazz.getSimpleName());

			httpServletResponse.sendRedirect(actionURL);
		}
		catch (Exception exception) {
			_portal.sendError(
				exception, httpServletRequest, httpServletResponse);
		}

		if (httpServletResponse.isCommitted()) {
			return;
		}
		else if (actionURL != null) {
			httpServletResponse.sendRedirect(actionURL);

			return;
		}

		processFilter(
			CustomOpenIdConnectAutoLoginFilter.class.getName(), httpServletRequest,
			httpServletResponse, filterChain);
	}

	private void _autoLoginUser(
			HttpServletRequest httpServletRequest,
			HttpServletResponse httpServletResponse, Long userId)
		throws Exception {

		HttpSession httpSession = httpServletRequest.getSession();

		httpSession.setAttribute(
			OpenIdConnectWebKeys.OPEN_ID_CONNECT_AUTHENTICATING_USER_ID,
			userId);

		super.processFilter(
			httpServletRequest, httpServletResponse,
			(servletRequest, servletResponse) -> {
				long authenticatedUserId = _getRemoteUserId(servletRequest);

				if (authenticatedUserId == userId) {
					// Commerce specific customization starts here...
					
					if (!_customCommerceOrderConfiguration.enabled()) return;
				
					if (Validator.isNull(_customCommerceOrderConfiguration.commerceChannelGroupId())) {
						_log.info("commerceChannelGroupId is empty.");
						
						return;
					}
					
					if (Validator.isNull(_customCommerceOrderConfiguration.currencyCode())) {
						_log.info("CurrencyCode is empty / null.");
						
						return;
					}

					// Fetch the current Account Entry for the current user in the Commerce Enabled Site.
					AccountEntry currentAccountEntry = null;
					
					try {
						currentAccountEntry = _currentAccountEntryManager.getCurrentAccountEntry(_customCommerceOrderConfiguration.commerceChannelGroupId(), userId);
					} catch (PortalException e) {
						_log.error("Error retrieving currentAccountEntry for groupId: " + _customCommerceOrderConfiguration.commerceChannelGroupId() + ", userId: " + userId, e);
					}	
					
					if (currentAccountEntry == null) {
						_log.info("Unable to find curentAccountEntry for groupId: " + _customCommerceOrderConfiguration.commerceChannelGroupId() + ", userId: " + userId);
						
						return;			
					}						
					
					// Fetch Curreny for CurrencyCode USD
					CommerceCurrency commerceCurrency = _commerceCurrencyLocalService.fetchCommerceCurrency(currentAccountEntry.getCompanyId(), _customCommerceOrderConfiguration.currencyCode());
					
					if (commerceCurrency == null) {
						_log.info("Unable to find commerceCurrency for currencyCode: " + _customCommerceOrderConfiguration.currencyCode());
						
						return;
					}
					
					// Find an open order created by this user for this Account
					//CommerceOrder commerceOrder = _commerceOrderLocalService.fetchCommerceOrder(currentAccountEntry.getAccountEntryId(), _customCommerceOrderConfiguration.commerceChannelGroupId(), userId, 2);
					CommerceOrder commerceOrder = getOpenCommerceOrderByUserId(userId, currentAccountEntry.getCompanyId(), _customCommerceOrderConfiguration.commerceChannelGroupId(), currentAccountEntry.getAccountEntryId());
					
					if (commerceOrder == null) { // Create the order
						_log.info("Existing open commerce order not found for accountEntryId: " + currentAccountEntry.getAccountEntryId() + ", userId: " + userId);
						
						try {
							commerceOrder = _commerceOrderLocalService.addCommerceOrder(userId, _customCommerceOrderConfiguration.commerceChannelGroupId(), currentAccountEntry.getAccountEntryId(), commerceCurrency.getCode(), 0);
							
							_log.info("Created Commerce Order: UUID: " + commerceOrder.getUuid() + ", orderId: " + commerceOrder.getCommerceOrderId() + ", currencyCode: " + commerceCurrency.getCode());
						} catch (PortalException e) {
							_log.error("Error creating order for groupId: " + _customCommerceOrderConfiguration.commerceChannelGroupId() + ", accountEntryId: " + currentAccountEntry.getAccountEntryId() + ", userId: " + userId + ", currencyCode: " + commerceCurrency.getCode(), e);
						}						
					} else {
						_log.info("Existing open commerce order (commerceOrderId: " + commerceOrder.getCommerceOrderId() + " found for accountEntryId: " + currentAccountEntry.getAccountEntryId() + ", userId: " + userId);
					}
					
					if (commerceOrder != null) {
						// Set the Session Attribute
						httpSession.setAttribute(_commerceOrderHttpHelper.getCookieName(commerceOrder.getGroupId()), commerceOrder.getUuid());	
						
						_log.info("Session Attribute " + _commerceOrderHttpHelper.getCookieName(commerceOrder.getGroupId()) + " accountEntryId populated for: " + currentAccountEntry.getAccountEntryId() + ", userId: " + userId);
					} else {
						_log.info("Order not found or created for accountEntryId: " + currentAccountEntry.getAccountEntryId() + ", userId: " + userId);
					}
					
					// Commerce specific customization ends here...
					
					return;
				}

				throw new ServletException(
					"Expected user " + userId + " to be authenticated");
			});
	}
	
	/**
	 * @param userId
	 * @param companyId
	 * @param commerceChannelGroupId
	 * @param commerceAccountId
	 * @return
	 * 
	 * Get an open order based on the provided userId
	 */
	private CommerceOrder getOpenCommerceOrderByUserId(long userId, long companyId, long commerceChannelGroupId, long commerceAccountId) {
		long[] commerceAccountIds = {commerceAccountId};
		int[] orderStatuses= {2}; // Open orders only
		
		try {
			long start = System.currentTimeMillis();
			
			List<CommerceOrder> orders = _commerceOrderLocalService.getCommerceOrders(companyId, commerceChannelGroupId, commerceAccountIds, null, orderStatuses, false, QueryUtil.ALL_POS, QueryUtil.ALL_POS);
			
			_log.info(System.currentTimeMillis() - start + " ms.");
			
			for (CommerceOrder order: orders) {
				if (order.getUserId() == userId) {
					return order;
				}
			}
		} catch (PortalException e) {
			_log.error("Error getting orders", e);
		}
		
		
		return null;
	}	

	private long _getRemoteUserId(ServletRequest servletRequest) {
		HttpServletRequest httpServletRequest =
			(HttpServletRequest)servletRequest;

		return GetterUtil.getLong(httpServletRequest.getRemoteUser());
	}

	private static final Log _log = LogFactoryUtil.getLog(
		CustomOpenIdConnectAutoLoginFilter.class);

	@Reference
	private OfflineOpenIdConnectSessionManager
		_offlineOpenIdConnectSessionManager;

	@Reference
	private OpenIdConnect _openIdConnect;

	@Reference
	private OpenIdConnectAuthenticationHandler
		_openIdConnectAuthenticationHandler;

	@Reference
	private Portal _portal;
	
	@Reference
	private CurrentAccountEntryManager _currentAccountEntryManager;
	
	@Reference
	private CommerceOrderLocalService _commerceOrderLocalService;
	
	@Reference
	private CommerceCurrencyLocalService _commerceCurrencyLocalService;
	
	@Reference
	private CommerceOrderHttpHelper _commerceOrderHttpHelper;	
	
	private volatile CustomCommerceOrderConfiguration _customCommerceOrderConfiguration;
}