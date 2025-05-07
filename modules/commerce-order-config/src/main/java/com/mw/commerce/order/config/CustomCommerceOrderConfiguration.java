package com.mw.commerce.order.config;

import com.liferay.portal.configuration.metatype.annotations.ExtendedObjectClassDefinition;

import aQute.bnd.annotation.metatype.Meta;
import aQute.bnd.annotation.metatype.Meta.Type;

@ExtendedObjectClassDefinition(category = "custom-commerce-order", scope = ExtendedObjectClassDefinition.Scope.SYSTEM)
@Meta.OCD(id = CustomCommerceOrderConfiguration.PID, localization = "content/Language", name = "configuration.customCommerceOrder.name", description="configuration.customCommerceOrder.desc")
public interface CustomCommerceOrderConfiguration {
	public static final String PID = "com.mw.commerce.order.config.CustomCommerceOrderConfiguration";

	@Meta.AD(deflt = "false", required = false, type = Type.Boolean, name = "field.enabled.name", description = "field.enabled.desc")
	public boolean enabled();
	
	@Meta.AD(deflt = "0", required = false, type = Type.Long, name = "field.commerceChannelGroupId.name", description = "field.commerceChannelGroupId.desc")
	public long commerceChannelGroupId();
	
	@Meta.AD(deflt = "USD", required = false, type = Type.String, name = "field.currencyCode.name", description = "field.currencyCode.desc")
	public String currencyCode();
}