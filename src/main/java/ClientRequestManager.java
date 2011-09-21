/*
 * SGX Public License Notice This software is the intellectual property of SGX.
 * The program may be used only in accordance with the terms of the license
 * agreement you entered into with SGX. 2003 Singapore Exchange Pte Ltd (SGX).
 * All rights reserved. 2 Shenton Way / #22-00 SGX Centre 1 Singapore( 068804 )
 */

package com.sgx.fxfws.manager;

import java.sql.Timestamp;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.xml.ws.WebServiceContext;
import javax.xml.ws.handler.MessageContext;

import com.sgx.framework.web.AppContext;
import com.sgx.fxfws.dao.ClientRequestDao;
import com.sgx.fxfws.dao.FXFWSDaoException;
import com.sgx.fxfws.dao.domain.ClientRequest;
import com.sgx.fxfws.sks.FXFWSCacheException;
import com.sgx.fxfws.sks.RequestIDCache;
import com.sgx.fxfws.util.Constant;
import com.sgx.fxfws.util.HibernateUtilFactory;
import com.sgx.fxfws.util.Util;
import com.sgx.tdc.ws.AuthenticationType;

/**
 * This is a manager class that performs ClientRequest functions. It has no DB
 * interface This is a singleton class
 * 
 * @author $Author: wcao@cn.ufinity.com $
 * @version $Revision: 1 $
 * @since $Date: 2011-06-8 13:30:41 +0800 (Wen, 8 Jun 2011) $
 */
public class ClientRequestManager extends AbstractManager
{
    private static ClientRequestManager manager;
    private ClientRequestDao clientRequestDao = null;
    private RequestIDCache requestIDCache = null;
    private long lastClearCacheTime = System.currentTimeMillis();

    /** Private constructor */
    private ClientRequestManager()
    {
        clientRequestDao = new ClientRequestDao(
            HibernateUtilFactory.ConfigMode.HIBERNATE_WEBSERVICE );
        requestIDCache = new RequestIDCache();
    }

    /**
     * Gets the instance
     * 
     * @return requestIDManager instance
     */
    public static final synchronized ClientRequestManager getInstance()
    {
        if( manager == null )
        {
            manager = new ClientRequestManager();
        }

        return manager;
    }

    /**
     * clear request id cache
     * 
     * @Author: szf
     */
    public void clearCache()
    {
        if( System.currentTimeMillis() - this.lastClearCacheTime > AppContext
            .getInstance().getLongProperty(
                Constant.ConfigKeys.CLEAR_CACHE_TIME_DIFFERENCE ) * 1000 )
        {
            info( "clearCache",
                " exec clear requestId cache , clear cache time difference="
                    + AppContext.getInstance().getLongProperty(
                        Constant.ConfigKeys.CLEAR_CACHE_TIME_DIFFERENCE )
                    + " seconds" );
            clearExpiredRequestIDs();
            this.lastClearCacheTime = System.currentTimeMillis();
        }
    }

    /**
     * Get a requestID from cache
     * 
     * @param requestIDId
     * @return requestID info object
     * @throws FXFWSCacheException e
     */
    public String getRequestID( String requestID ) throws FXFWSCacheException
    {
        final String method = "getRequestID";
        clearCache();
        String reqId = requestIDCache.getRequestID( requestID );
        debug( method, "requestId retrieved from cache: " + reqId );
        return reqId;
    }

    /**
     * add a requestID to the cache
     * 
     * @param requestID info object
     * @throws FXFWSCacheException e
     */
    public void addRequestID( String requestID ) throws FXFWSCacheException
    {
        final String method = "addRequestID";
        requestIDCache.putRequestID( requestID );
        debug( method, "requestID added to cache: " + requestID );

    }

    /**
     * clear all expired requestID
     */
    public void clearExpiredRequestIDs()
    {
        final String method = "clearExpiredRequestIDs";

        info( method, "RequestId cleared: "
            + requestIDCache.clearExpiredRequestIDs() );
    }

    /**
     * remove required requestID
     * 
     * @param requestID
     */
    public void removeRequestID( String requestID )
    {
        final String method = "removeRequestID";
        requestIDCache.removeRequestID( requestID );
        debug( method, "requestID removed from cache: " + requestID );
    }

    /**
     * cache requestID and persistent clientRequest
     * 
     * @param requestID
     * @param header
     * @param webService
     * @param webOperation
     * @param ip
     * @throws ManagerException
     * @return String
     */
    public String cacheAndSaveRequestID( String requestID,
        AuthenticationType header, String webService, String webOperation,
        String ip )
    {
        String method = "cacheAndSaveRequestID";
        info( method, "param[requestID]:" + requestID + ",param[header]:"
            + header + ",param[webService]:" + webService
            + ",param[webOperation]:" + webOperation + ",param[ip]:" + ip );

        ClientRequest clientRequest = new ClientRequest();
        clientRequest.setClientId( header.getClientId() );
        clientRequest.setIpAddress( ip );
        clientRequest.setRequestId( requestID );
        clientRequest.setSessionId( header.getSessionId() );
        /*
         * clientRequest.setTimestamp( new Timestamp( header.getTimeStamp()
         * .toGregorianCalendar().getTime().getTime() ) );
         */
        clientRequest.setTimestamp( new Timestamp( new java.util.Date()
            .getTime() ) );
        clientRequest.setWebOperation( webOperation );
        clientRequest.setWebService( webService );

        // add requestID to cache
        try
        {
            this.addRequestID( requestID );
        }
        catch ( FXFWSCacheException e1 )
        {
            error( method, " requestID:" + requestID + " add to cache fail" );
            return Constant.MessageCode.SYSTEMUNAVAILABLE;
        }

        // add requestID to database
        try
        {
            boolean flag = clientRequestDao.save( clientRequest );
            if( !flag )
            {
                error( method, " requestID:" + requestID
                    + " add to database fail" );
                return Constant.MessageCode.SYSTEMUNAVAILABLE;
            }
        }
        catch ( FXFWSDaoException e )
        {
            // remove requestID from cache while exception occur
            removeRequestID( requestID );
            error( method, " exception occur and remove requestID from cache " );

            // if add requestID to cache success then remove it
            final String msg = "Error when Save clientRequest";
            error( method, msg, e );
            return Constant.MessageCode.SYSTEMUNAVAILABLE;
        }

        return Constant.MessageCode.WEBSERVICESUCCESS;
    }

    /**
     * get ip address
     * 
     * @param wsContext
     * @return String
     */
    public String getIPFromWSContext( WebServiceContext wsContext )
    {
        String method = "getIPFromWSContext";
        info( method, "param[wsContext]:" + wsContext );

        MessageContext msgContext = wsContext.getMessageContext();
        HttpServletRequest req = ( HttpServletRequest ) msgContext
            .get( MessageContext.SERVLET_REQUEST );
        String clientIP = Util.getClientIPAddress( req );
        debug( method, "clientIP:" + clientIP );
        return clientIP;
    }

    /**
     * update ClientRequest
     * 
     * @param requestId
     * @param messageCode
     * @return update count
     * @Author:hypan
     */
    public int updateClientRequest( String requestId, String messageCode )
    {
        String methodName = "updateClientRequest";
        debug( methodName, "param[requestId]:" + requestId
            + ",param[messageCode]:" + messageCode );
        int upCount = 0;
        Map<String, String> resMap = getMessageCodeAndMessage( messageCode );
        ClientRequest clientRequest = new ClientRequest();
        clientRequest.setRequestId( requestId );
        clientRequest.setReturnCode( resMap
            .get( Constant.MessageCodeAndMsg.MESSAGECODE ) );
        clientRequest.setReturnMessage( resMap
            .get( Constant.MessageCodeAndMsg.MESSAGE ) );
        try
        {
            upCount = clientRequestDao
                .updateClientRequestByRequestId( clientRequest );
        }
        catch ( FXFWSDaoException e )
        {
            final String message = "Error when Update ClientRequest table";
            error( methodName, message, e );
            alert( e.getErrorCode(), message );

        }
        return upCount;
    }

    /**
     * update client Request by real return code and message
     * 
     * @param requestId
     * @param returnCode
     * @param returnMsg
     * @return int
     * @Author: szf
     */
    public int updateClientRequest( String requestId, String returnCode,
        String returnMsg )
    {
        String methodName = "updateClientRequest";
        debug( methodName, "param[requestId]:" + requestId
            + ",param[returnCode]:" + returnCode + ",param[returnMsg]:"
            + returnMsg );
        int upCount = 0;
        ClientRequest clientRequest = new ClientRequest();
        clientRequest.setRequestId( requestId );
        clientRequest.setReturnCode( returnCode );
        clientRequest.setReturnMessage( returnMsg );
        try
        {
            upCount = clientRequestDao
                .updateClientRequestByRequestId( clientRequest );
        }
        catch ( FXFWSDaoException e )
        {
            final String message = "Error when Update ClientRequest table";
            error( methodName, message, e );
            alert( e.getErrorCode(), message );

        }
        return upCount;
    }
}
