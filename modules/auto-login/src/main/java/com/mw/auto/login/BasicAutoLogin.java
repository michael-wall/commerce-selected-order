package com.mw.auto.login;

import com.liferay.account.manager.CurrentAccountEntryManager;
import com.liferay.account.model.AccountEntry;
import com.liferay.commerce.currency.model.CommerceCurrency;
import com.liferay.commerce.currency.service.CommerceCurrencyLocalService;
import com.liferay.commerce.model.CommerceOrder;
import com.liferay.commerce.order.CommerceOrderHttpHelper;
import com.liferay.commerce.service.CommerceOrderLocalService;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.security.auto.login.AutoLogin;
import com.liferay.portal.kernel.security.auto.login.BaseAutoLogin;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.Validator;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Michael Wall
 */
@Component(
	immediate = true,
	service = AutoLogin.class
)
public class BasicAutoLogin extends BaseAutoLogin {

	@Activate
	protected void activate(BundleContext bundleContext) {
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
		
		long groupId = 44904; // Hardcoded temporarially...
		String currencyCode = "USD";// Hardcoded temporarially...
		
		_log.info(CommerceOrder.class.getName() + StringPool.POUND + groupId);
		
		CommerceCurrency commerceCurrency = _commerceCurrencyLocalService.fetchCommerceCurrency(companyId, currencyCode);

		AccountEntry currentAccountEntry = _currentAccountEntryManager.getCurrentAccountEntry(groupId, user.getUserId());
		
		CommerceOrder commerceOrder = _commerceOrderLocalService.addCommerceOrder(user.getUserId(), groupId, currentAccountEntry.getAccountEntryId(), commerceCurrency.getCode(), 0);
		
		_log.info("Created Order: UUID: " + commerceOrder.getUuid() + ", orderId: " + commerceOrder.getCommerceOrderId() + ", currencyCode: " + commerceCurrency.getCode());
		
		HttpSession httpSession = httpServletRequest.getSession();

		httpSession.setAttribute(_commerceOrderHttpHelper.getCookieName(commerceOrder.getGroupId()), commerceOrder.getUuid());
		
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

	
	private static final Log _log = LogFactoryUtil.getLog(BasicAutoLogin.class);	
}