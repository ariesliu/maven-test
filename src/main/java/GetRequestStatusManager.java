/*
 * SGX Public License Notice This software is the intellectual property of SGX.
 * The program may be used only in accordance with the terms of the license
 * agreement you entered into with SGX. 2003 Singapore Exchange Pte Ltd (SGX).
 * All rights reserved. 2 Shenton Way / #22-00 SGX Centre 1 Singapore( 068804 )
 */
package com.sgx.fxfws.manager;

import java.util.ArrayList;
import java.util.List;

import org.fpml.fpml_5.confirmation.MessageAddress;
import org.fpml.fpml_5.confirmation.MessageId;
import org.fpml.fpml_5.confirmation.RequestMessageHeader;

import com.sgx.fxfws.dao.ClientRequestDao;
import com.sgx.fxfws.dao.FXFWSDaoException;
import com.sgx.fxfws.dao.domain.ClientRequest;
import com.sgx.fxfws.util.Constant;
import com.sgx.fxfws.util.FpMLConstant;
import com.sgx.fxfws.util.HibernateUtilFactory;
import com.sgx.fxfws.util.Util;
import com.sgx.tdc.ws.fxfws.QueryRequest;
import com.sgx.tdc.ws.fxfws.Reason;
import com.sgx.tdc.ws.fxfws.RequestStatus;
import com.sgx.tdc.ws.fxfws.ResponseBody;

/**
 * Manager for getting request status web service
 * 
 * @author $Author: wcao@cn.ufinity.com $
 * @version $Revision: 1 $
 * @since $Date: 2011-06-28 14:14:17 +0800 (Tue, 28 Jun 2011) $
 */
public class GetRequestStatusManager extends AbstractManager
{
    /** GetRequestStatusManager instance */
    private static GetRequestStatusManager _manager;

    private ClientRequestDao clientRequestDao;

    /**
     * Gets the GetRequestStatusManager instance
     * 
     * @return GetRequestStatusManager instance
     */
    public static final synchronized GetRequestStatusManager getInstance()
    {
        if( _manager == null )
        {
            _manager = new GetRequestStatusManager();
        }
        return _manager;
    }

    /**
     * Constructor for GetRequestStatusManager
     */
    private GetRequestStatusManager()
    {
        super();
        clientRequestDao = new ClientRequestDao(
            HibernateUtilFactory.ConfigMode.HIBERNATE_WEBSERVICE );
    }

    /**
     * Deal with business process
     * 
     * @param parameters
     * @param clientId
     * @return ResponseBody
     * @throws ManagerException managerException
     * @Author: wcao
     */
    public ResponseBody getRequestStatus( QueryRequest parameters,
        String clientId ) throws ManagerException
    {
        String method = "getRequestStatus";
        info( method, "param[QueryRequest]:" + parameters + ",param[clientId]:"
            + clientId );
        ResponseBody responseBody = objectFactory.createResponseBody(
            objectFactory.createResponseBody() ).getValue();
        RequestStatus requestStatus = objectFactory.createRequestStatus(
            objectFactory.createRequestStatus() ).getValue();
        MessageId requestId = parameters.getMessageId();

        //3. Query clienTrequest returnCode and returnMessage 
        try
        {
            // query clientRequest by requestId and clientId and webOperation
            List<String> webOperation = new ArrayList<String>();
            webOperation.add( Constant.WebserviceName.ADDNEWFXFTRADE );
            webOperation.add( Constant.WebserviceName.DECLEARTRADE );

            ClientRequest queryClientRequest = clientRequestDao
                .findClientRequestByReqIdCntIdAndOpt( requestId.getValue(),
                    clientId, webOperation );
            if( Util.isEmpty( queryClientRequest ) )
            {
                debug( method, "Requested messageId not found in database" );
                Reason reason = getReason( Constant.MessageCode.REQUESTEDMESSAGEIDNOTFOUND );
                responseBody.setReason( reason );
            }
            else
            {
                requestStatus.setRequestStatusValue( queryClientRequest
                    .getReturnCode()
                    + FpMLConstant.Response.CONNECTOR_SIGN
                    + queryClientRequest.getReturnMessage() );
                responseBody.setRequestStatus( requestStatus );
            }
        }
        catch ( FXFWSDaoException e )
        {
            error( method, "Error when findClientRequestByReqIdCntIdAndOpt", e );
            throw new ManagerException( e.getErrorCode(), e.sqlCode, e );
        }

        return responseBody;
    }

    /**
     * input validate
     * 
     * @param parameters
     * @param clientId
     * @return String
     * @Author: wcao
     */
    public String validateInput( QueryRequest parameters, String clientId )
    {
        String method = "validateInput";
        info( method, "param[QueryRequest]:" + parameters + ",param[clientId]:"
            + clientId );
        String messageCode = Constant.MessageCode.WEBSERVICESUCCESS;
        RequestMessageHeader requestMessageHeader = parameters.getHeader();
        MessageAddress messageAddress = requestMessageHeader.getSentBy();
        MessageId requestId = parameters.getMessageId();

        FpMLManager fpMLManager = FpMLManager.getInstance();
        //1. validate sentBy
        messageCode = fpMLManager.validateSentBy( messageAddress.getValue(),
            messageAddress.getMessageAddressScheme(), clientId );
        // if validate failure, return response and end
        if( !Constant.MessageCode.WEBSERVICESUCCESS.equals( messageCode ) )
        {
            debug( method, "ValidateSentBy failure" );
            return messageCode;
        }
        //2. validate messageIdScheme
        if( !context.getProperty( FpMLConstant.FpMLScheme.MESSAGEIDSCHEME )
            .equals( requestId.getMessageIdScheme() ) )
        {
            debug( method, "Invalid messageIdScheme" );
            return Constant.MessageCode.INVALIDMESSAGEIDSCHEME;
        }
        debug( method, "ValidateInput is success" );
        return messageCode;
    }
}
