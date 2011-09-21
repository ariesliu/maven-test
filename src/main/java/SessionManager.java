/*
 * SGX Public License Notice This software is the intellectual property of SGX.
 * The program may be used only in accordance with the terms of the license
 * agreement you entered into with SGX. 2003 Singapore Exchange Pte Ltd (SGX).
 * All rights reserved. 2 Shenton Way / #22-00 SGX Centre 1 Singapore( 068804 )
 */

package com.sgx.fxfws.manager;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.List;

import com.sgx.clear.common.crypto.Crypto;
import com.sgx.framework.web.AppContext;
import com.sgx.framework.web.IAppContext;
import com.sgx.fxfws.sks.SessionCache;
import com.sgx.fxfws.sks.SessionInfo;
import com.sgx.fxfws.sks.FXFWSCacheException;
import com.sgx.fxfws.util.Constant;
import com.sgx.fxfws.util.CryptoException;
import com.sgx.fxfws.util.CryptoUtil;
import com.sgx.fxfws.util.PKIUtil;
import com.sgx.fxfws.util.PasswordUtil;

/**
 * This is a manager class that performs session functions. It has no DB
 * interface This is a singleton class
 * 
 * @author $Author: zfshao@cn.ufinity.com $
 * @version $Revision: 1073 $
 * @since $Date: 2011-03-22 15:54:47 +0800 (Tue, 22 Mar 2011) $
 */
public class SessionManager extends AbstractManager
{
    private static SessionManager manager;
    private SessionCache sessionCache;
    private String keystorePass;
    private String keystoreFile;
    private String sksKeyAlias;

    /** Private constructor */
    private SessionManager()
    {
        super();
        sessionCache = new SessionCache();

        // gets the keystore and password
        IAppContext context = AppContext.getInstance();
        String encKeystorePass = context
            .getProperty( Constant.ConfigKeys.KEYSTORE_PASSWORD );
        keystoreFile = context.getProperty( Constant.ConfigKeys.KEYSTORE_FILE );
        sksKeyAlias = context.getProperty( Constant.ConfigKeys.KEYSTORE_ALIAS );

        PasswordUtil passUtil = new PasswordUtil();
        keystorePass = passUtil.decrypt( encKeystorePass );
    }

    /**
     * Gets the instance
     * 
     * @return SessionManager instance
     */
    public static final synchronized SessionManager getInstance()
    {
        if( manager == null )
        {
            manager = new SessionManager();
        }

        return manager;
    }

    /**
     * Get a session from cache
     * 
     * @param sessionId
     * @return session info object
     * @throws FXFWSCacheException e
     */
    public SessionInfo getSession( String sessionId )
        throws FXFWSCacheException
    {
        final String method = "getSession";
        info( method, "param[sesisonId]=" + sessionId);
        SessionInfo session = sessionCache.getSession( sessionId );
        debug( method, "Session retrieved from cache: " + session );
        return session;
    }

    /**
     * add a session to the cache
     * 
     * @param session info object
     * @throws FXFWSCacheException e
     */
    public void addSession( SessionInfo session ) throws FXFWSCacheException
    {
        final String method = "addSession";
        info( method, "param[session]=" + session);
        sessionCache.putSession( session );
        debug( method, "Session added to cache: " + session );
    }

    /**
     * clear all expired sessions
     * 
     * @throws FXFWSCacheException e
     */
    public void clearExpiredSessions() throws FXFWSCacheException
    {
        final String method = "clearExpiredSessions";

        info( method, "Sessions cleared: "
            + sessionCache.clearExpiredSessions() );
    }

    /**
     * decrypt session from SKS with my own private key and cipher secretKey
     * return the constructed session object
     * 
     * @param encSession containing encrypted session from SKS
     * @param secretKey cipherText of secretKey
     * @return SessionInfo object containing the session
     * @throws ManagerException e
     */
    public SessionInfo decryptSession( String encSession, String secretKey )
        throws ManagerException
    {
        final String method = "decryptSession";
        info( method, "param[encSession]=" + encSession + ",param[secretKey]="
            + secretKey );
        PrivateKey privateKey = null;
        try
        {
            PKIUtil util = PKIUtil.getInstance( keystoreFile, keystorePass );
            privateKey = util.getPrivateKey( sksKeyAlias, keystorePass );
        }
        catch ( CryptoException ce )
        {
            final String msg = "Error getting private key for alias: "
                + sksKeyAlias + ", keystore: " + keystoreFile;

            error( method, msg, ce );
            throw new ManagerException( ManagerException.ERR_KEYSTORE, msg, ce );
        }

        // decrypt with private key
        String plainText = null;
        String plainKey = null;
        CryptoUtil cryptoUtilRSA = new CryptoUtil( CryptoUtil.RSA_ALGORITHM );
        CryptoUtil cryptoUtilAES = new CryptoUtil( CryptoUtil.AES_ALGORITHM );
        try
        {
            plainKey = cryptoUtilRSA.decryptRSA( privateKey, secretKey );

            List<byte[]> keys = cryptoUtilAES.splitRawKey( Crypto
                .asByte( plainKey ) );

            //System.out.println( "aes key:" + Crypto.asHex(keys.get(0)) + "," + keys.get(0).length);
            //System.out.println( "aes  iv:" + Crypto.asHex(keys.get(1)) + "," + keys.get(0).length);

            // encrypt plaintext with the split session key
            //String encText = cryptoUtilAES.encrypt( Crypto.asHex(keys.get(0)), Crypto.asHex(keys.get(1)), plainText);
            plainText = cryptoUtilAES.decrypt( Crypto.asHex( keys.get( 0 ) ),
                Crypto.asHex( keys.get( 1 ) ), encSession );
        }
        catch ( CryptoException ce )
        {
            final String msg = "Error crypto ";
            error( method, msg, ce );
            throw new ManagerException( ManagerException.ERR_CRYPTO, msg, ce );
        }
        //System.out.println(plainText);
        return new SessionInfo( plainText );
    }

    /**
     * decrypt secretKey to plainKey
     * 
     * @param secretKey
     * @return byte[]
     * @throws ManagerException e
     */
    public byte[] decryptKey( String secretKey ) throws ManagerException
    {
        final String method = "decryptKey";
        info( method, "param[secretKey]=" + secretKey);
        PrivateKey privateKey = null;
        try
        {
            PKIUtil util = PKIUtil.getInstance( keystoreFile, keystorePass );
            privateKey = util.getPrivateKey( sksKeyAlias, keystorePass );
        }
        catch ( CryptoException ce )
        {
            final String msg = "Error getting private key for alias: "
                + sksKeyAlias + ", keystore: " + keystoreFile;

            error( method, msg, ce );
            throw new ManagerException( ManagerException.ERR_KEYSTORE, msg, ce );
        }

        // decrypt with private key
        String plainKey = null;
        CryptoUtil cryptoUtilRSA = new CryptoUtil( CryptoUtil.RSA_ALGORITHM );
        try
        {
            plainKey = cryptoUtilRSA.decryptRSA( privateKey, secretKey );

        }
        catch ( CryptoException ce )
        {
            final String msg = "Error crypto ";
            error( method, msg, ce );
            throw new ManagerException( ManagerException.ERR_CRYPTO, msg, ce );
        }
        return Crypto.asByte( plainKey );
    }

    /**
     * encrypt session with my own public key and return the constructed session
     * object
     * 
     * @param session
     * @param subjectDn the DN for the cert
     * @return hex-encoded encrypted session of this object
     * @throws ManagerException e
     */
    public String encryptSession( SessionInfo session, String subjectDn )
        throws ManagerException
    {
        final String method = "encryptSession";
        info( method, "param[session]=" + session + ",param[subjectDn]="
            + subjectDn );
        PublicKey publicKey = null;
        try
        {
            PKIUtil util = PKIUtil.getInstance( keystoreFile, keystorePass );
            publicKey = util.getPublicKeyFromCert( sksKeyAlias, subjectDn,
                keystorePass );
        }
        catch ( CryptoException ce )
        {
            final String msg = "Error getting public key for alias/dn: "
                + sksKeyAlias + "/" + subjectDn + ", keystore: " + keystoreFile;

            error( method, msg, ce );
            throw new ManagerException( ManagerException.ERR_KEYSTORE, msg, ce );
        }

        // encrypt with public key
        String encSession = null;
        CryptoUtil cryptoUtil = new CryptoUtil( CryptoUtil.RSA_ALGORITHM );

        try
        {
            encSession = cryptoUtil.encryptRSA( publicKey, session.dumpAsXML()
                .getBytes() );
        }
        catch ( CryptoException ce )
        {
            final String msg = "Error encrypting for alias/dn: " + sksKeyAlias
                + "/" + subjectDn;

            error( method, msg, ce );
            throw new ManagerException( ManagerException.ERR_CRYPTO, msg, ce );
        }

        return encSession;
    }
}
