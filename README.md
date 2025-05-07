## Introduction ##
This project consists of 4 custom OSGi modules: 
- commerce-order-config: Configuration to enable the custom logic and store the commerceChannelGroupId and the default currencyCode used when creating the Commerce Order.
- auto-login-commerce-order: Contains a sample AutoLogin OSGi component that creates a Commerce Order and populates the Session Attribute so the Commerce Order is pre-selected in the GUI.
- oidc-fragment: An OSGi fragment module to expose 2 internal Liferay packages from the com.liferay.portal.security.sso.openid.connect.impl module that are referenced in oidc-commerce-order module.
- oidc-commerce-order: An OSGI component that overrides the OpenIdConnectAutoLoginFilter Liferay class with a custom implementation. This checks for an existing open Commerce Order for the current user and if not found it creates one. In both cases it populates the Session Attribute so the Commerce Order is pre-selected in the GUI.

## Environment ##
- The modules are built for 2025.Q1.7 (Liferay Workspace gradle.properties > liferay.workspace.product = dxp-2025.q1.7-lts)
- JDK 21 is expected for compile time and runtime.
- The original source for CustomOpenIdConnectAutoLoginFilter.java came from 2025.Q1.7 Quarterly Release: https://github.com/liferay/liferay-dxp/blob/2025.q1.7/modules/apps/portal-security-sso/portal-security-sso-openid-connect-impl/src/main/java/com/liferay/portal/security/sso/openid/connect/internal/servlet/filter/auto/login/OpenIdConnectAutoLoginFilter.java
- Ensure the Liferay source code for this file is checked for updates when the custom modules are updated to a newer QR version.s

## Configuration ##
- Go to System Settings > Commerce > Custom Commerce Order. Check the 'Enabled' checkbox, populate 'Commerce Channel Group ID' with the commerceChannelGroupId from the Commerce enabled Site (e.g. visit the Site in browser, view Source Code and search for commerceChannelGroupId) and populate the Currency Code to use e.g. USD.
- Go to System Settings > Platform > Module Container > Component Blacklist and add the following to the list of Blacklisted Components: com.liferay.portal.security.sso.openid.connect.internal.servlet.filter.auto.login.OpenIdConnectAutoLoginFilter
- Note: If already using a ComponentBlacklistConfiguration.config file in the Liferay PaaS repository then update that file instead of configuring through the GUI.
- If the custom modules are removed ensure that the OpenIdConnectAutoLoginFilter class is un-blacklisted, otherwise OIDC SSO users will not be able to login.
- The session.phishing.protected.attributes portal property must be updated to include com.liferay.commerce.model.CommerceOrder#XXXXXXXXXX (where XXXXXXXXXX is the commerceChannelGroupId from above) to allow the session attribute that is set in the unauthenticated user session in BasicAutoLoginCommerceOrder.java class to be passed to the new authenticated user session created after the user is logged in.

## BasicAutoLoginCommerceOrder.java ##
- Invoke with syntax http://localhost:8080/?emailAddress=test@liferay.com or http://localhost:8080/web/minium/catalog/?emailAddress=test@liferay.com etc.

## Sample test Scenario for BasicAutoLoginCommerceOrder.java ##
1. Auto Login as test@liferay.com in Browser A, note the new commerce order ID in the logging but don't visit the Commerce Site.
2. Auto Login as the same user in Browser B, note the new commerce order ID in the logging and visit the Commerce Site.
3. In Browser B confirm the correct commerce order ID from step 2 is applied.
4. In Browser A visit the Commerce Site and confirm the correct commerce order ID from step 1 is applied.

## HTTP Session Usage ##
- In the Liferay DXP source code the currently selected order is managed with a java HTTP session attribute in class CommerceOrderHttpHelperImpl, method setCurrentCommerceOrder:
```
HttpSession httpSession = httpServletRequest.getSession();
httpSession.setAttribute(getCookieName(commerceOrder.getGroupId()), commerceOrder.getUuid());
```
- where getCookieName is in syntax CommerceOrder.class.getName() + StringPool.POUND + commerceChannelGroupId e.g. com.liferay.commerce.model.CommerceOrder#44904
- Liferay PaaS uses sticky session by default, so if a user gets switched to another node e.g. due to a node failure or due to a 'blue green' build deployment then for the PunchOut Customer scenario they would end up with a new java HTTP session and as a result a new order.

## Notes ##
- The currencyCode is stored in configuration as it is needed when creating a new Commerce Order and I tested locally with USD as it was the default. Additional logic may be required to determine the correct currency dynamically to avoid storing it as a Configuration setting.
