## BasicAutoLogin.java ##
- Invoke with syntax http://localhost:8080/?emailAddress=test@liferay.com
- Note the groupId for the Commerce enabled Site and the currency Code are temporarily hardcoded in BasicAutoLogin.java.
- Note that the the session.phishing.protected.attributes portal property must be updated to include com.liferay.commerce.model.CommerceOrder#44904 (where 44904 is the Commerce enabled Site groupId) to allow the session attribute that is set in the BasicAutoLogin class to be passed to the new session created after the user is logged in.
