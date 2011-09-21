/*
 * SGX Public License Notice This software is the intellectual property of SGX.
 * The program may be used only in accordance with the terms of the license
 * agreement you entered into with SGX. 2003 Singapore Exchange Pte Ltd (SGX).
 * All rights reserved. 2 Shenton Way / #22-00 SGX Centre 1 Singapore( 068804 )
 */
package com.sgx.fxfws.manager;

import com.sgx.fxfws.dao.ClientRequestDao;
import com.sgx.fxfws.dao.FXFWSDaoException;
import com.sgx.fxfws.sks.FXFWSCacheException;
import com.sgx.fxfws.util.Constant;
import com.sgx.fxfws.util.FpMLConstant;
import com.sgx.fxfws.util.HibernateUtilFactory;
import com.sgx.fxfws.util.Util;
import com.sgx.tdc.ws.AuthenticationType;

/**
 * FpML manager for validate FpML header
 * 
 * @author $Author: wcao@cn.ufinity.com $
 * @version $Revision: 1 $
 * @since $Date: 2011-06-15 10:30:41 +0800 (Wen, 15 Jun 2011) $
 */
public class FpMLManager extends AbstractManager
{
    /** FpMLManager instance */
    private static FpMLManager _manager;

    private ClientRequestDao clientRequestDao;

    /**
     * Gets the GetRequestStatusManager instance
     * 
     * @return GetRequestStatusManager instance
     */
    public static final synchronized FpMLManager getInstance()
    {
        if( _manager == null )
        {
            _manager = new FpMLManager();
        }
        return _manager;
    }

    /**
     * Constructor for GetRequestStatusManager
     */
    private FpMLManager()
    {
        super();
        clientRequestDao = new ClientRequestDao(
            HibernateUtilFactory.ConfigMode.HIBERNATE_WEBSERVICE );
    }

    /**
     * validate messageId, sentBy and add requestId into cache and database
     * 
     * @param requestId
     * @param messageIdScheme
     * @param header
     * @param webService
     * @param webOperation
     * @param ip
     * @return String
     * @Author: wcao
     */
    public String commonValidateAndSaveRequestId( String requestId,
        String messageIdScheme, AuthenticationType header, String webService,
        String webOperation, String ip )
    {
        String method = "commonValidateAndSaveRequestId";
        String messageCode = Constant.MessageCode.WEBSERVICESUCCESS;

        info( method, "param[requestId]:" + requestId
            + ",param[messageIdScheme]:" + messageIdScheme + ",param[header]:"
            + header + ",param[webService]:" + webService
            + ",param[webOperation]:" + webOperation + ",param[ip]:" + ip );

        //1. validate FpML Request ID
        messageCode = validateReqId( requestId, messageIdScheme, header
            .getSessionId() );
        if( !Constant.MessageCode.WEBSERVICESUCCESS.equals( messageCode ) )
        {
            debug( method, "Validate Request ID is false" );
            return messageCode;
        }

        //2. add requestID to cache and database
        ClientRequestManager clientRequestManager = ClientRequestManager
            .getInstance();
        messageCode = clientRequestManager.cacheAndSaveRequestID( requestId,
            header, webService, webOperation, ip );
        if( !Constant.MessageCode.WEBSERVICESUCCESS.equals( messageCode ) )
        {
            debug( method, "Add requestID to cache and database error" );
            return messageCode;
        }
        debug( method, "CommonValidateAndSaveRequestId success" );
        return messageCode;
    }

    /**
     * validate FpML Root Element Attributes
     * 
     * @param version
     * @return String
     */
    public String validateFpMLRootElement( String version )
    {
        String method = "validateFpMLRootElement";
        info( method, "param[version]:" + version );

        //If version != '5-1'
        if( !FpMLConstant.Version.VERSION.equals( version ) )
        {
            debug( method, "Validate Version is false" );
            return Constant.MessageCode.UNSUPPORTEDFPMLVERSION;
        }
        debug( method, "Validate FpML Root Element is true" );
        return Constant.MessageCode.WEBSERVICESUCCESS;
    }

    /**
     * validate FpML Message ID for api5
     * 
     * @param requestId
     * @param messageIdScheme
     * @param headSessionId
     * @return String
     */
    public String validateMessageId( String requestId, String messageIdScheme,
        String headSessionId )
    {
        String method = "validateMessageId";
        info( method, "param[requestId]:" + requestId
            + ",param[messageIdScheme]:" + messageIdScheme
            + ",param[headSessionId]:" + headSessionId );

        //Validate messageIdScheme
        if( !context.getProperty( FpMLConstant.FpMLScheme.MESSAGEIDSCHEME )
            .equals( messageIdScheme ) )
        {
            debug( method, "Validate messageIdScheme is false" );
            return Constant.MessageCode.INVALIDMESSAGEIDSCHEME;
        }

        //validate RptID Format And SessionId 
        if( !validateRptIDOrReqIDFormatAndSessionId( requestId, headSessionId ) )
        {
            debug( method, "Validate RptID Format And SessionId is false" );
            return Constant.MessageCode.INVALIDMESSAGEIDFORMAT;
        }
        return Constant.MessageCode.WEBSERVICESUCCESS;
    }

    /**
     * validate FpML Request ID
     * 
     * @param requestId
     * @param messageIdScheme
     * @param headSessionId
     * @return String
     */
    private String validateReqId( String requestId, String messageIdScheme,
        String headSessionId )
    {
        String method = "validateReqId";
        info( method, "param[requestId]:" + requestId
            + ",param[messageIdScheme]:" + messageIdScheme
            + ",param[headSessionId]:" + headSessionId );

        //Validate messageIdScheme
        if( !context.getProperty( FpMLConstant.FpMLScheme.MESSAGEIDSCHEME )
            .equals( messageIdScheme ) )
        {
            debug( method, "Validate messageIdScheme is false" );
            return Constant.MessageCode.INVALIDMESSAGEIDSCHEME;
        }

        //validate RptID Format And SessionId 
        if( !validateRptIDOrReqIDFormatAndSessionId( requestId, headSessionId ) )
        {
            debug( method, "Validate RptID Format And SessionId is false" );
            return Constant.MessageCode.INVALIDMESSAGEIDFORMAT;
        }

        //Check requestId from the internal cache and database
        //a. Check the internal cache for duplicated request id, if its not found
        ClientRequestManager clientRequestManager = ClientRequestManager
            .getInstance();
        try
        {
            String reqId = ClientRequestManager.getInstance().getRequestID(
                requestId );
            if( requestId.equals( reqId ) )
            {
                debug( method, "request id is exist in the internal cache" );
                return Constant.MessageCode.DUPLICATEMESSAGEID;
            }
        }
        catch ( FXFWSCacheException e )
        {
            if( FXFWSCacheException.ERR_CACHE_MISS.equals( e.getErrorCode() ) )
            {
                debug( method, "request id is not exist in the internal cache" );
            }
            if( FXFWSCacheException.ERR_CACHE_EXPIRED.equals( e.getErrorCode() ) )
            {
                debug( method, "request id is exprired in the internal cache" );
            }
        }

        //b. search the client_request table
        try
        {
            Integer count = 0;
            count = clientRequestDao
                .getCountClientRequestByRequestId( requestId );
            if( count > 0 )
            {
                debug( method,
                    "Request id is exist in the client_request table, count="
                        + count );
                try
                {
                    clientRequestManager.addRequestID( requestId );
                }
                catch ( FXFWSCacheException e )
                {
                    debug( method, " requestID:" + requestId
                        + " add to cache fail" );
                }
                return Constant.MessageCode.DUPLICATEMESSAGEID;
            }
        }
        catch ( FXFWSDaoException e )
        {
            final String msg = "Error when getCountClientRequestByRequestId from database";
            error( method, msg, e );
            return Constant.MessageCode.SYSTEMUNAVAILABLE;
        }
        debug( method, "Validate FpML Request ID is true" );
        return Constant.MessageCode.WEBSERVICESUCCESS;
    }

    /**
     * validate FpML SentBy
     * 
     * @param requestId
     * @param messageAddressScheme
     * @param clientId
     * @return String
     */
    public String validateSentBy( String sentBy, String messageAddressScheme,
        String clientId )
    {
        String method = "validateSentBy";
        info( method, "param[sentBy]:" + sentBy
            + ",param[messageAddressScheme ]:" + messageAddressScheme
            + ",param[clientId]:" + clientId );

        //Validate messageAddressScheme
        if( !context.getProperty(
            FpMLConstant.FpMLScheme.MESSAGEADDRESSSCHEMEFORCLIENT ).equals(
            messageAddressScheme ) )
        {
            debug( method, "Validate messageAddressScheme is false" );
            return Constant.MessageCode.INVALIDMESSAGEADDRESSSCHEME;
        }

        //If sentBy != clientID (in the SOAP header)
        if( !sentBy.equals( clientId ) )
        {
            debug( method, "Validate sentBy is not clientId" );
            return Constant.MessageCode.INVALIDSENTBYSENDER;
        }

        debug( method, "Validate FpML Sender is true" );
        return Constant.MessageCode.WEBSERVICESUCCESS;
    }

    /**
     * validate RptID Or ReqID Format requestId is not following the format, ie.
     * <sessionid>-<number> If <sessionid> part is not match with the session ID
     * given in the header
     * 
     * @param requestId
     * @param headSessionId
     * @return boolean
     */
    private boolean validateRptIDOrReqIDFormatAndSessionId( String requestId,
        String headSessionId )

    {
        String method = "validateRptIDOrReqIDFormatAndSessionId";
        info( method, "param[requestId]:" + requestId
            + ",param[headSessionId]:" + headSessionId );
        int sepIndex = requestId
            .lastIndexOf( Constant.ValidateReqID.SEPARATORSTR );
        if( Constant.ValidateReqID.SEPINDEX == sepIndex )
        {
            debug( method, "Request ID does not contains '-'" );
            return false;
        }

        String sessionId = requestId.substring( 0, sepIndex );
        String number = requestId.substring( sepIndex + 1, requestId.length() );
        if( Util.isEmpty( sessionId ) || Util.isEmpty( number )
            || !Util.isNumber( number ) || !headSessionId.equals( sessionId ) )
        {
            debug( method, "Request ID format error" );
            return false;
        }
        else
        {
            debug( method, "ValidateRptIDOrReqIDFormatAndSessionId is true" );
            return true;
        }
    }
}
