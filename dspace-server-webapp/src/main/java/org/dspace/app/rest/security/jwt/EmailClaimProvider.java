/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.security.jwt;

import java.sql.SQLException;
import java.text.ParseException;
import javax.servlet.http.HttpServletRequest;

import com.nimbusds.jwt.JWTClaimsSet;
import org.apache.commons.lang3.StringUtils;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.springframework.stereotype.Component;

/**
 * Provides a claim for a JSON Web Token, this claim is responsible for adding
 * the EPerson email to it
 *
 * @author Your Name
 */
@Component
public class EmailClaimProvider implements JWTClaimProvider {

  public static final String EMAIL = "email";

  public String getKey() {
    return EMAIL;
  }

  public Object getValue(Context context, HttpServletRequest request) {
    EPerson currentUser = context.getCurrentUser();
    if (currentUser != null && StringUtils.isNotBlank(currentUser.getEmail())) {
      return currentUser.getEmail();
    }
    return null;
  }

  public void parseClaim(Context context, HttpServletRequest request, JWTClaimsSet jwtClaimsSet) throws SQLException {
    // Email is already available from the EPerson object when parsed from the token
    // This method can be left empty or used for validation if needed
    try {
      String email = jwtClaimsSet.getStringClaim(EMAIL);
      if (StringUtils.isNotBlank(email)) {
        // Email is now available in the token claims
        // The EPerson is already set in context by EPersonClaimProvider
        // so we don't need to do anything here, but we could validate if needed
      }
    } catch (ParseException e) {
      // Email claim not present or invalid - this is okay, it's optional
    }
  }
}
