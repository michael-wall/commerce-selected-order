package com.mw.auto.login;

import com.liferay.account.manager.CurrentAccountEntryManager;
import com.liferay.account.model.AccountEntry;
import com.liferay.commerce.currency.model.CommerceCurrency;
import com.liferay.commerce.currency.service.CommerceCurrencyLocalService;
import com.liferay.commerce.model.CommerceOrder;
import com.liferay.commerce.order.CommerceOrderHttpHelper;
import com.liferay.commerce.service.CommerceOrderLocalService;
import com.liferay.portal.configuration.metatype.bnd.util.ConfigurableUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.security.auto.login.AutoLogin;
import com.liferay.portal.kernel.security.auto.login.BaseAutoLogin;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.Validator;
import com.mw.commerce.order.config.CustomCommerceOrderConfiguration;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Michael Wall
 */
@Component(
	immediate = true,
	service = AutoLogin.class,
	configurationPid = CustomCommerceOrderConfiguration.PID
)
public class BasicAutoLoginCommerceOrder extends BaseAutoLogin {

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
	protected String[] doLogin(
			HttpServletRequest httpServletRequest,
			HttpServletResponse httpServletResponse)
		throws Exception {

		long companyId = _portal.getCompanyId(httpServletRequest);
		
		String emailAddress = ParamUtil.get(httpServletRequest, "emailAddress", "");
		
		if (Validator.isNull(emailAddress)) return null;
		
		User user = _userLocalService.fetchUserByEmailAddress(companyId, emailAddress);
		
		if (Validator.isNull(user)) return null;

		String[] credentials = new String[3];

		credentials[0] = String.valueOf(user.getUserId());
		credentials[1] = user.getPassword();
		credentials[2] = Boolean.TRUE.toString();
		
		// Commerce specific customization starts here...
		
		if (!_customCommerceOrderConfiguration.enabled()) return credentials;
		
		if (Validator.isNull(_customCommerceOrderConfiguration.commerceChannelGroupId())) {
			_log.info("commerceChannelGroupId is empty.");
			
			return credentials;
		}
		
		if (Validator.isNull(_customCommerceOrderConfiguration.currencyCode())) {
			_log.info("CurrencyCode is empty / null.");
			
			return credentials;
		}

		// Fetch the current Account Entry for the current user in the Commerce Enabled Site.
		AccountEntry currentAccountEntry = null;
		
		try {
			currentAccountEntry = _currentAccountEntryManager.getCurrentAccountEntry(_customCommerceOrderConfiguration.commerceChannelGroupId(), user.getUserId());
		} catch (PortalException e) {
			_log.error("Error retrieving currentAccountEntry for commerceChannelGroupId: " + _customCommerceOrderConfiguration.commerceChannelGroupId() + ", userId: " + user.getUserId(), e);
		}			
		
		if (currentAccountEntry == null) {
			_log.info("Unable to find curentAccountEntry for commerceChannelGroupId: " + _customCommerceOrderConfiguration.commerceChannelGroupId() + ", userId: " + user.getUserId());
			
			return credentials;			
		}		
		
		// Fetch Curreny for CurrencyCode USD
		CommerceCurrency commerceCurrency = _commerceCurrencyLocalService.fetchCommerceCurrency(companyId, _customCommerceOrderConfiguration.currencyCode());
		
		if (commerceCurrency == null) {
			_log.info("Unable to find commerceCurrency for currencyCode: " + _customCommerceOrderConfiguration.currencyCode());
			
			return credentials;
		}
		
		// Create the Commerce Order
		CommerceOrder commerceOrder = null;
		try {
			commerceOrder = _commerceOrderLocalService.addCommerceOrder(user.getUserId(), _customCommerceOrderConfiguration.commerceChannelGroupId(), currentAccountEntry.getAccountEntryId(), commerceCurrency.getCode(), 0);
		} catch (PortalException e) {
			_log.error("Error creating order for commerceChannelGroupId: " + _customCommerceOrderConfiguration.commerceChannelGroupId() + ", accountEntryId: " + currentAccountEntry.getAccountEntryId() + ", userId: " + user.getUserId() + ", currencyCode: " + commerceCurrency.getCode(), e);
		}
		
		if (commerceOrder != null) {
			_log.info("Created Commerce Order: UUID: " + commerceOrder.getUuid() + ", orderId: " + commerceOrder.getCommerceOrderId() + ", currencyCode: " + commerceCurrency.getCode());
			
			HttpSession httpSession = httpServletRequest.getSession();

			// Set the Session Attribute
			httpSession.setAttribute(_commerceOrderHttpHelper.getCookieName(commerceOrder.getGroupId()), commerceOrder.getUuid());	
			
			_log.info("Session Attribute " + _commerceOrderHttpHelper.getCookieName(commerceOrder.getGroupId()) + " populated for accountEntryId: " + currentAccountEntry.getAccountEntryId() + ", userId: " + user.getUserId());
		} else {
			_log.info("Order not created for accountEntryId: " + currentAccountEntry.getAccountEntryId() + ", userId: " + user.getUserId());
		}
		
		// Commerce specific customization ends here...
		
		return credentials;
	}	
	
	
	@Reference
	private Portal _portal;	
	
	@Reference
	private UserLocalService _userLocalService;	
	
	@Reference
	private CurrentAccountEntryManager _currentAccountEntryManager;
	
	@Reference
	private CommerceOrderLocalService _commerceOrderLocalService;
	
	@Reference
	private CommerceCurrencyLocalService _commerceCurrencyLocalService;
	
	@Reference
	private CommerceOrderHttpHelper _commerceOrderHttpHelper;

	private volatile CustomCommerceOrderConfiguration _customCommerceOrderConfiguration;	
	
	private static final Log _log = LogFactoryUtil.getLog(BasicAutoLoginCommerceOrder.class);	
}