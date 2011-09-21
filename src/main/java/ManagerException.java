/*
 * SGX Public License Notice This software is the intellectual property of SGX.
 * The program may be used only in accordance with the terms of the license
 * agreement you entered into with SGX. 2003 Singapore Exchange Pte Ltd (SGX).
 * All rights reserved. 2 Shenton Way / #22-00 SGX Centre 1 Singapore( 068804 )
 */
package com.sgx.fxfws.manager;

import com.sgx.clear.common.log.ClearException;

/**
 * This is a deal Exception class for manager
 * 
 * @author $Author: wcao@cn.ufinity.com $
 * @version $Revision: 1 $
 * @since $Date: 2011-06-8 13:30:41 +0800 (Wen, 8 Jun 2011) $
 */
public class ManagerException extends ClearException
{

    private static final long serialVersionUID = 75040000L;

    public static final String ERR_NULL_PARAM = "75040001";
    public static final String ERR_HIBERNATE = "75040002";
    public static final String ERR_DATA = "75040003";
    public static final String ERR_KEYSTORE = "75040004";
    public static final String ERR_CRYPTO = "75040005";

    //Alert code
    public static final String ERR_SKS_CONNECT = "75040006";
    public static final String ERR_FXFWS_DATABASE_CONNECT = "75040007";

    // stores the sqlCode returned by the DAO
    public int sqlCode = 0;

    /**
     * Constructs a new ManagerException.
     * 
     * @param errcode error code
     */
    public ManagerException( String errcode )
    {
        super( errcode );
    }

    /**
     * Constructs a new ManagerException.
     * 
     * @param errcode error code
     * @param s description
     */
    public ManagerException( String errcode, String s )
    {
        super( errcode, s );
    }

    /**
     * Constructs a new ManagerException.
     * 
     * @param errcode error code
     * @param t cause
     */
    public ManagerException( String errcode, Throwable t )
    {
        super( errcode, t );
    }

    /**
     * Constructs a new ManagerException.
     * 
     * @param errcode error code
     * @param sqlCode sql code from DAO
     * @param t cause
     */
    public ManagerException( String errcode, int sqlCode, Throwable t )
    {
        super( errcode, t );
        this.sqlCode = sqlCode;
    }

    /**
     * Constructs a new ManagerException.
     * 
     * @param errcode error code
     * @param s description
     * @param t cause
     */
    public ManagerException( String errcode, String s, Throwable t )
    {
        super( errcode, s, t );
    }

    /**
     * Constructs a new ManagerException.
     * 
     * @param errcode error code
     * @param s description
     * @param sqlCode sql code from DAO
     * @param t cause
     */
    public ManagerException( String errcode, String s, int sqlCode, Throwable t )
    {
        super( errcode, s, t );
        this.sqlCode = sqlCode;
    }

}
