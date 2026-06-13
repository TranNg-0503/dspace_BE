/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.vnpay.dao.impl;

import java.sql.SQLException;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import org.dspace.app.rest.vnpay.VnpayTransaction;
import org.dspace.app.rest.vnpay.dao.VnpayTransactionDAO;
import org.dspace.core.AbstractHibernateDAO;
import org.dspace.core.Context;

/**
 * Hibernate implementation of {@link VnpayTransactionDAO}.
 */
public class VnpayTransactionDAOImpl extends AbstractHibernateDAO<VnpayTransaction>
    implements VnpayTransactionDAO {

    @Override
    public VnpayTransaction findByTransactionId(Context context, String transactionId) throws SQLException {
        if (transactionId == null || transactionId.isEmpty()) {
            return null;
        }

        CriteriaBuilder criteriaBuilder = getCriteriaBuilder(context);
        CriteriaQuery<VnpayTransaction> criteriaQuery =
            getCriteriaQuery(criteriaBuilder, VnpayTransaction.class);
        Root<VnpayTransaction> transactionRoot = criteriaQuery.from(VnpayTransaction.class);
        criteriaQuery.select(transactionRoot);
        criteriaQuery.where(criteriaBuilder.equal(transactionRoot.get("transactionId"), transactionId));

        return singleResult(context, criteriaQuery);
    }
}
