/*
 * SGX Public License Notice This software is the intellectual property of SGX.
 * The program may be used only in accordance with the terms of the license
 * agreement you entered into with SGX. 2003 Singapore Exchange Pte Ltd (SGX).
 * All rights reserved. 2 Shenton Way / #22-00 SGX Centre 1 Singapore( 068804 )
 */
package com.sgx.fxfws.manager;

import java.util.HashMap;
import java.util.Map;

import org.fpml.fpml_5.confirmation.MessageAddress;

import com.sgx.clear.common.manager.ManagerBase;
import com.sgx.framework.web.AppContext;
import com.sgx.framework.web.IAppContext;
import com.sgx.fxfws.util.Constant;
import com.sgx.fxfws.util.CryptoUtil;
import com.sgx.fxfws.util.FpMLConstant;
import com.sgx.fxfws.util.Util;
import com.sgx.tdc.ws.fxfws.HashToken;
import com.sgx.tdc.ws.fxfws.ObjectFactory;
import com.sgx.tdc.ws.fxfws.Reason;


public abstract class AbstractManager extends ManagerBase
{
    /** the object for getting message from property */
    protected IAppContext context = AppContext.getInstance();

    protected ObjectFactory objectFactory = new ObjectFactory();

    /**
     * get reason by messsage code.
     * 
     * @param messageCode
     * @return Reason
     * @Author: wcao
     */
    public Reason getReason( String messageCode )
    {
        String method = "getReason";
        info( method, "param[messageCode]:" + messageCode );
        String description = getMessage( messageCode );
        Reason reason = objectFactory.createReason();
        reason.setReasonCode( getMessageCode( messageCode ) );
        reason.setDescription( description );
        return reason;
    }

    /**
     * get MessageCode And Message from resource
     * 
     * @param messageCode
     * @return map contains messageCode and Message
     * @Author: hypan
     */
    public Map<String, String> getMessageCodeAndMessage( String messageCode )
    {
        String methodName = "getMessageCodeAndMessage";
        debug( methodName, "messageCode:" + messageCode );
        Map<String, String> messageCodeAndMsg = new HashMap<String, String>();
        messageCodeAndMsg.put( Constant.MessageCodeAndMsg.MESSAGECODE,
            getMessageCode( messageCode ) );
        messageCodeAndMsg.put( Constant.MessageCodeAndMsg.MESSAGE,
            getMessage( messageCode ) );
        return messageCodeAndMsg;
    }

    /**
     * get MessageCode from resource
     * 
     * @param messageCode
     * @return map contains messageCode and Message
     * @Author: wcao
     */
    public String getMessageCode( String messageCode )
    {
        String methodName = "getMessageCode";
        debug( methodName, "messageCode:" + messageCode );
        return context.getProperty( messageCode );
    }

    /**
     * get Message from resource
     * 
     * @param messageCode
     * @return message
     * @Author: wcao
     */
    public String getMessage( String messageCode )
    {
        String methodName = "getMessage";
        debug( methodName, "messageCode:" + messageCode );
        return context.getProperty( Constant.RETURN_CODE_MESSAGE_MAP
            .get( messageCode ) );
    }

    /**
     * get HashToken
     * 
     * @return HashToken
     * @Author: hypan
     */
    public HashToken getHashToken()
    {
        String methodName = "getHashToken";
        HashToken hashToken = new HashToken();
        String reference = Util.generateResponseId();
        CryptoUtil crypto = new CryptoUtil( CryptoUtil.AES_ALGORITHM );
        String hash = null;
        try
        {
            hash = crypto.generateSecretKeyForSha256();
            debug( methodName, "reference=" + reference + ",hash:" + hash );
        }
        catch ( Exception e )
        {
            final String msg = "Error when getHashToken";
            error( methodName, msg, e );
        }
        hashToken.setReference( reference );
        hashToken.setValue( hash );
        return hashToken;
    }

    /**
     * get Reason from messageCode
     * 
     * @param messageCode
     * @return Reason
     * @Author: hypan
     */
    public org.fpml.fpml_5.confirmation.Reason getFpMLReason( String messageCode )
    {
        org.fpml.fpml_5.confirmation.Reason reason = new org.fpml.fpml_5.confirmation.Reason();
        org.fpml.fpml_5.confirmation.ReasonCode reasonCode = new org.fpml.fpml_5.confirmation.ReasonCode();
        reasonCode.setValue( getMessageCodeAndMessage( messageCode ).get(
            Constant.MessageCodeAndMsg.MESSAGECODE ) );
        reason.setReasonCode( reasonCode );
        reason.setDescription( getMessageCodeAndMessage( messageCode ).get(
            Constant.MessageCodeAndMsg.MESSAGE ) );
        return reason;
    }

    /**
     * get SentBy Bean for response
     * 
     * @return MessageAddress
     * @Author: hypan
     */
    public MessageAddress getResponseSentBy()
    {
        MessageAddress sentBy = new MessageAddress();
        sentBy
            .setMessageAddressScheme( context
                .getProperty( FpMLConstant.FpMLScheme.MESSAGEADDRESSSCHEMEFORPROVIDER ) );
        sentBy.setValue( context.getProperty( FpMLConstant.FpMLField.SENTBY ) );
        return sentBy;
    }

}
