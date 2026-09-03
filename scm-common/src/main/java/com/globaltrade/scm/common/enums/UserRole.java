package com.globaltrade.scm.common.enums;

/**
 * Application security roles. These names are duplicated (deliberately, as
 * literals) in {@code web.xml}, {@code glassfish-web.xml} and the
 * {@code @RolesAllowed} annotations across the EJB tier, because Jakarta EE
 * declarative security constraints must be resolvable from static
 * deployment descriptors / annotations and cannot reference a Java enum
 * directly. Keeping the enum as the single source of truth for the *set*
 * of valid roles still lets application code validate role names
 * consistently (see {@code SecurityUtil}).
 */
public enum UserRole {
    ADMIN,
    LOGISTICS_COORDINATOR,
    CUSTOMS_AGENT,
    WAREHOUSE_MANAGER,
    VENDOR_REPRESENTATIVE,
    CUSTOMER
}
