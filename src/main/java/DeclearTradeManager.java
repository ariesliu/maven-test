/*
 * SGX Public License Notice This software is the intellectual property of SGX.
 * The program may be used only in accordance with the terms of the license
 * agreement you entered into with SGX. 2003 Singapore Exchange Pte Ltd (SGX).
 * All rights reserved. 2 Shenton Way / #22-00 SGX Centre 1 Singapore( 068804 )
 */
package com.sgx.fxfws.manager;

import java.util.Date;
import java.util.List;

import org.fpml.fpml_5.confirmation.Acknowledgement;
import org.fpml.fpml_5.confirmation.ClearingRefused;
import org.fpml.fpml_5.confirmation.MessageAddress;
import org.fpml.fpml_5.confirmation.MessageId;
import org.fpml.fpml_5.confirmation.NotificationMessageHeader;
import org.fpml.fpml_5.confirmation.Party;
import org.fpml.fpml_5.confirmation.PartyId;
import org.fpml.fpml_5.confirmation.PartyTradeIdentifier;
import org.fpml.fpml_5.confirmation.Reason;
import org.fpml.fpml_5.confirmation.RequestClearing;
import org.fpml.fpml_5.confirmation.ResponseMessageHeader;
import org.fpml.fpml_5.confirmation.TradeId;

import com.sgx.framework.web.AppContext;
import com.sgx.fxfws.dao.FXFWSDaoException;
import com.sgx.fxfws.dao.TransactionDeclearSpoolDao;
import com.sgx.fxfws.dao.TransactionDeclearStatusDao;
import com.sgx.fxfws.dao.TransactionNewStatusDao;
import com.sgx.fxfws.dao.domain.TransactionDeclearSpool;
import com.sgx.fxfws.dao.domain.TransactionNewStatus;
import com.sgx.fxfws.util.Constant;
import com.sgx.fxfws.util.FpMLConstant;
import com.sgx.fxfws.util.HibernateUtilFactory;
import com.sgx.fxfws.util.Util;
import com.sgx.tdc.ws.AuthenticationType;
import com.sgx.tdc.ws.fxfws.HashToken;
import com.sgx.tdc.ws.fxfws.ResponseBody;
import com.sgx.fxfws.manager.ManagerException;

/**
 * Manager for declear trade web service
 * 
 * @author $Author: twu@cn.ufinity.com $
 */
public class DeclearTradeManager extends AbstractManager
{
    private static DeclearTradeManager _manager;
    private TransactionNewStatusDao transactionNewStatusDao = null;
    private TransactionDeclearSpoolDao transactionDeclearSpoolDao = null;
    private TransactionDeclearStatusDao transactionDeclearStatusDao = null;

    /** Private constructor */
    private DeclearTradeManager()
    {
        super();
        transactionNewStatusDao = new TransactionNewStatusDao(
            HibernateUtilFactory.ConfigMode.HIBERNATE_WEBSERVICE );

        transactionDeclearSpoolDao = new TransactionDeclearSpoolDao(
            HibernateUtilFactory.ConfigMode.HIBERNATE_WEBSERVICE );

        transactionDeclearStatusDao = new TransactionDeclearStatusDao(
            HibernateUtilFactory.ConfigMode.HIBERNATE_WEBSERVICE );
    }

    /**
     * Gets the DeclearTradeManager instance
     * 
     * @return DeclearTradeManager instance
     */
    public static final synchronized DeclearTradeManager getInstance()
    {
        if( _manager == null )
        {
            _manager = new DeclearTradeManager();
        }
        return _manager;
    }

    /**
     * validate and processing
     * 
     * @param requestClearing
     * @param authentication
     * @param hashToken
     * @return ResponseBody
     * @throws ManagerException e
     * @Author: wu tao
     */
    public String validateAndProcessing( RequestClearing requestClearing,
        AuthenticationType authentication, HashToken hashToken )
        throws ManagerException
    {
        String msgCode = validateDeclearTradeInput( requestClearing,
            authentication );
        if( null != msgCode )
        {//validate not pass.
            return msgCode;
        }
        return processing( requestClearing, authentication, hashToken );
    }

    /**
     * validate all DeclearTrade method input.
     * 
     * @param requestClearing
     * @param authentication
     * @return Reason message code
     * @throws ManagerException
     * @Author: wu tao
     */
    private String validateDeclearTradeInput( RequestClearing requestClearing,
        AuthenticationType authentication ) throws ManagerException
    {
        String msgCode = null;

        //validateSentBy
        MessageAddress sentBy = requestClearing.getHeader().getSentBy();
        msgCode = FpMLManager.getInstance().validateSentBy( sentBy.getValue(),
            sentBy.getMessageAddressScheme(), authentication.getClientId() );
        if( !Constant.MessageCode.WEBSERVICESUCCESS.equals( msgCode ) )
        {
            return msgCode;
        }

        //validateSequenceNumber
        msgCode = validateSequenceNumber( requestClearing );
        if( null != msgCode )
        {
            return msgCode;
        }

        //validateIsCorrection
        msgCode = validateIsCorrection( requestClearing );
        if( null != msgCode )
        {
            return msgCode;
        }

        //validateDeClear
        msgCode = validateDeClear( requestClearing );
        if( null != msgCode )
        {
            return msgCode;
        }

        //validateTradeIdentifier
        msgCode = validateTradeIdentifier( requestClearing );
        if( null != msgCode )
        {
            return msgCode;
        }

        //validatePartyReferenceHref
        msgCode = validatePartyReferenceHref( requestClearing );
        if( null != msgCode )
        {
            return msgCode;
        }

        //validateTradeId
        msgCode = validateTradeId( requestClearing );
        if( null != msgCode )
        {
            return msgCode;
        }

        //validatePartyId
        msgCode = validatePartyId( requestClearing );
        if( null != msgCode )
        {
            return msgCode;
        }

        //validateCorrelationId
        msgCode = validateCorrelationId( requestClearing, authentication );
        if( null != msgCode )
        {
            return msgCode;
        }

        return null;
    }

    /**
     * processing, insert transactionNewSpool object and update clientRequest.
     * 
     * @param requestClearing
     * @param authentication
     * @param hashToken
     * @return Reason message code
     * @throws ManagerException
     * @Author: wu tao
     */
    private String processing( RequestClearing requestClearing,
        AuthenticationType authentication, HashToken hashToken )
        throws ManagerException
    {
        String method = "processing";

        //Insert entry into the transaction_declear_spool table based on the mapping
        TransactionDeclearSpool tds = new TransactionDeclearSpool();
        tds.setTransId( requestClearing.getHeader().getMessageId().getValue() );
        tds.setDealIdScheme( requestClearing.getCorrelationId()
            .getCorrelationIdScheme() );
        tds.setDealId( requestClearing.getCorrelationId().getValue() );

        Party party = ( Party ) ( requestClearing.getDeClear()
            .getTradeIdentifier().get( 0 ).getPartyReference().getHref() );
        List<PartyId> partyIdList = party.getPartyId();

        for ( PartyId partyId : partyIdList )
        {
            //set party BIC code to TransactionDeclearSpool object.
            if( ( AppContext.getInstance()
                .getProperty( FpMLConstant.FpMLScheme.PARTYIDSCHEMEFORBIC ) )
                .equals( partyId.getPartyIdScheme() ) )
            {
                debug( method, "param[requestClearing]: partyId with scheme \""
                    + FpMLConstant.FpMLScheme.PARTYIDSCHEMEFORBIC
                    + "\" value is: " + partyId.getValue() );
                tds.setPartyBic( partyId.getValue() );
            }

            //set partyType to to TransactionDeclearSpool object.
            if( ( AppContext.getInstance()
                .getProperty( FpMLConstant.FpMLScheme.PARTYIDSCHEMEFORACCOUNT ) )
                .equals( partyId.getPartyIdScheme() ) )
            {
                debug( method, "param[requestClearing]: partyId with scheme \""
                    + FpMLConstant.FpMLScheme.PARTYIDSCHEMEFORACCOUNT
                    + "\" value is: " + partyId.getValue() );
                if( !Util.isEmpty( partyId.getValue() ) )
                {
                    tds.setPartyAccount( partyId.getValue() );
                }
            }

        }

        //set trade id to to TransactionDeclearSpool object.
        TradeId tradeId = ( TradeId ) ( requestClearing.getDeClear()
            .getTradeIdentifier().get( 0 ).getTradeIdOrVersionedTradeId()
            .get( 0 ) );
        tds.setPartyTradeId( tradeId.getValue() );

        //set party name to to TransactionDeclearSpool object.
        if( !Util.isEmpty( party.getPartyName() )
            && !Util.isEmpty( party.getPartyName().getValue() ) )
        {
            tds.setPartyName( party.getPartyName().getValue() );
        }

        tds.setTimestamp( requestClearing.getHeader().getCreationTimestamp()
            .toGregorianCalendar().getTime() );
        tds.setDateCreated( new Date() );
        tds.setClientId( authentication.getClientId() );

        HashToken token = getHashToken();
        tds.setPushReference( token.getReference() );
        tds.setPushHashToken( token.getValue() );
        hashToken.setReference( token.getReference() );
        hashToken.setValue( token.getValue() );

        //insert transactionNewSpool.
        try
        {
            transactionDeclearSpoolDao.save( tds );
        }
        catch ( FXFWSDaoException e )
        {
            final String msg = "exception occured when insert TransactionDeclearSpool object.";
            error( method, msg, e );
            alert( e.getErrorCode(), msg );
            throw new ManagerException( e.getErrorCode(), msg, e.sqlCode, e );
        }

        return Constant.MessageCode.WEBSERVICESUCCESS;
    }

    /**
     * validate RequestClearing->SequenceNumber is 1
     * 
     * @param requestClearing
     * @return null if SequenceNumber is 1 or a Reason message code.
     * @Author: wu tao
     */
    private String validateSequenceNumber( RequestClearing requestClearing )
    {
        String method = "validateSequenceNumber";
        debug( method, "param[requestClearing]:"
            + requestClearing.getSequenceNumber() );
        if( !Constant.ValidateSequenceNumber.SEQUENCE_NUMBER
            .equals( requestClearing.getSequenceNumber() ) )
        {
            return Constant.MessageCode.SEQUENCINGNOTSUPPORTED;
        }
        return null;
    }

    /**
     * validate RequestClearing->isCorrection is false
     * 
     * @param requestClearing
     * @return null if isCorrection is false or a Reason message code
     * @Author: wu tao
     */
    private String validateIsCorrection( RequestClearing requestClearing )
    {
        String method = "validateIsCorrection";
        debug( method, "param[requestClearing]:"
            + requestClearing.isIsCorrection() );
        if( requestClearing.isIsCorrection() )
        {
            return Constant.MessageCode.CORRECTIONNOTSUPPORTED;
        }
        return null;
    }

    /**
     * validate RequestClearing->DeClear is null
     * 
     * @param requestClearing
     * @return null if DeClear is not null or a Reason message code if Declear
     *         is null.
     * @Author: wu tao
     */
    private String validateDeClear( RequestClearing requestClearing )
    {
        String method = "validateDeClear";
        debug( method,
            "param[requestClearing]: requestClearing.getDeClear() is null "
                + ( null == requestClearing.getDeClear() ) );
        if( null == requestClearing.getDeClear() )
        {
            return Constant.MessageCode.SYSTEM_ERROR;
        }
        return null;
    }

    /**
     * validate RequestClearing->DeClear->TradeIdentifier size is 1
     * 
     * @param requestClearing
     * @return null if TradeIdentifier size is 1 or a Reason message code if
     *         TradeIdentifier size is not 1.
     * @Author: wu tao
     */
    private String validateTradeIdentifier( RequestClearing requestClearing )
    {
        String method = "validateTradeIdentifier";
        List<PartyTradeIdentifier> tradeIdentifierList = requestClearing
            .getDeClear().getTradeIdentifier();
        if( null == tradeIdentifierList || 1 != tradeIdentifierList.size() )
        {
            debug( method,
                "param[requestClearing]: tradeIdentifier is null or not only have one element." );
            return Constant.MessageCode.INVALIDPARTYTYPE;
        }
        debug( method, "param[requestClearing]: tradeIdentifier size is "
            + tradeIdentifierList.size() );
        return null;
    }

    /**
     * validate
     * RequestClearing->DeClear->TradeIdentifier[0]->PartyReference->href is
     * null
     * 
     * @param requestClearing
     * @return null if href is present or a Reason message code if href is
     *         absent.
     * @Author: wu tao
     */
    private String validatePartyReferenceHref( RequestClearing requestClearing )
    {
        String method = "validatePartyReferenceHref";
        PartyTradeIdentifier partyTradeIdentifier = requestClearing
            .getDeClear().getTradeIdentifier().get( 0 );
        debug( method,
            "param[requestClearing]: tradeIdentifier first element 's  partyReferenct is "
                + partyTradeIdentifier.getPartyReference() );

        //only validate the first href.
        if( null == partyTradeIdentifier.getPartyReference()
            || null == partyTradeIdentifier.getPartyReference().getHref() )
        {
            return Constant.MessageCode.INVALIDFPMLMESSAGE;
        }
        return null;
    }

    /**
     * validate
     * requestClearing->deClear->tradeIdentifier[0]->tradeIdOrVersionedTradeId
     * [0] is null
     * 
     * @param requestClearing
     * @return null if tradeIdOrVersionedTradeId[0] is not null or a Reason
     *         message code if tradeIdOrVersionedTradeId[0] is null.
     * @Author: wu tao
     */
    private String validateTradeId( RequestClearing requestClearing )
    {
        String method = "validateTradeId";
        List<Object> list = requestClearing.getDeClear().getTradeIdentifier()
            .get( 0 ).getTradeIdOrVersionedTradeId();
        if( null == list || 1 != list.size()
            || Util.isEmpty( ( TradeId ) list.get( 0 ) )
            || Util.isEmpty( ( ( TradeId ) list.get( 0 ) ).getValue() ) )
        {
            debug( method,
                "param[requestClearing]: TradeId is blank or absence or multiple." );
            return Constant.MessageCode.MISSINGORMULTIPLETRADEIDS;
        }
        debug( method,
            "param[requestClearing]: TradeIdOrVersionedTradeId size is "
                + list.size() );
        return null;
    }

    /**
     * validate requestClearing->party->partyId
     * 
     * @param requestClearing
     * @return null if partyId contains BIC code and if contains partyType the
     *         value is "Party" or "CounterParty" or "Matcher" or return a
     *         reason message code.
     * @Author: wu tao
     */
    private String validatePartyId( RequestClearing requestClearing )
    {
        String method = "validatePartyId";
        List<Party> partyList = requestClearing.getParty();
        if( 0 == partyList.size() )
        {
            return Constant.MessageCode.MISSINGBICPARTYIDSCHEME;
        }
        Object object = requestClearing.getDeClear().getTradeIdentifier().get(
            0 ).getPartyReference().getHref();
        if( null == object || !( object instanceof Party ) )
        {
            return Constant.MessageCode.INVALIDFPMLMESSAGE;
        }

        int countOfBIC = 0;
        int countOfPartyType = 0;
        int countOfAccount = 0;

        Party party = ( Party ) object;

        List<PartyId> partyIdList = party.getPartyId();
        for ( PartyId partyId : partyIdList )
        {
            //validate party contains BIC code
            if( ( AppContext.getInstance()
                .getProperty( FpMLConstant.FpMLScheme.PARTYIDSCHEMEFORBIC ) )
                .equals( partyId.getPartyIdScheme() ) )
            {
                debug( method, "param[requestClearing]: partyId with scheme \""
                    + FpMLConstant.FpMLScheme.PARTYIDSCHEMEFORBIC
                    + "\" value is: " + partyId.getValue() );

                countOfBIC++;

                if( Util.isEmpty( partyId.getValue() )//missing bic (bic is blank)
                    || ( countOfBIC > 1 ) )
                {//duplicate BIC
                    return Constant.MessageCode.MISSINGORMULTIPLEBIC;
                }
            }

            //validate optional partyType value.
            if( ( AppContext.getInstance()
                .getProperty( FpMLConstant.FpMLScheme.PARTYIDSCHEMEFORPARTYTYPE ) )
                .equals( partyId.getPartyIdScheme() ) )
            {
                debug( method, "param[requestClearing]: partyId with scheme \""
                    + FpMLConstant.FpMLScheme.PARTYIDSCHEMEFORPARTYTYPE
                    + "\" value is: " + partyId.getValue() );

                countOfPartyType++;
                //validate if duplicate partyType.
                if( countOfPartyType > 1 )
                {
                    return Constant.MessageCode.DUPLICATEPARTYTYPE;
                }
                //validate if partyType value is "Party"
                if( !FpMLConstant.AddNewFXFTrade.PARTYTYPE[0].equals( partyId
                    .getValue() ) )
                {
                    return Constant.MessageCode.INVALIDPARTYTYPE;
                }
            }

            //validate party account.
            if( ( AppContext.getInstance()
                .getProperty( FpMLConstant.FpMLScheme.PARTYIDSCHEMEFORACCOUNT ) )
                .equals( partyId.getPartyIdScheme() ) )
            {
                debug( method, "param[requestClearing]: partyId with scheme \""
                    + FpMLConstant.FpMLScheme.PARTYIDSCHEMEFORACCOUNT
                    + "\" value is: " + partyId.getValue() );
                if( !Util.isEmpty( partyId.getValue() ) )
                {
                    countOfAccount++;
                    if( countOfAccount > 1 )
                    {
                        return Constant.MessageCode.DUPLICATEACCOUNTS;
                    }
                }
            }
        }

        //validate if missing BIC.        
        if( 0 == countOfBIC )
        {
            return Constant.MessageCode.MISSINGBICPARTYIDSCHEME;
        }

        //validate if missing partyType.
        if( 0 == countOfPartyType )
        {
            return Constant.MessageCode.DUPLICATEPARTYTYPE;
        }

        return null;
    }

    /**
     * validate RequestClearing->correlationId
     * 
     * @param requestClearing
     * @param authentication
     * @return null if correlationIdScheme based on the clientId and
     *         correlationId value in transaction_new_status table or return
     *         Reason message code if correlationIdScheme is not based on the
     *         clientId or correlationId value not in transaction_new_status
     *         table
     * @throws FXFWSDaoException
     * @Author: wu tao
     */
    private String validateCorrelationId( RequestClearing requestClearing,
        AuthenticationType authentication ) throws ManagerException
    {
        String method = "validateCorrelationId";
        debug( method, "param[requestClearing]: correlationId value is "
            + requestClearing.getCorrelationId().getValue()
            + " correlationScheme is "
            + requestClearing.getCorrelationId().getCorrelationIdScheme() );

        //validate scheme.
        if( !( AppContext.getInstance().getProperty(
            FpMLConstant.FpMLScheme.CORRELATIONIDSCHEME ) + authentication
            .getClientId() ).equals( requestClearing.getCorrelationId()
            .getCorrelationIdScheme() ) )
        {
            return Constant.MessageCode.INVALIDCORRELATIONIDSCHEME;
        }

        //Value of correlationId must not be blank
        if( Util.isEmpty( requestClearing.getCorrelationId().getValue() ) )
        {
            return Constant.MessageCode.MISSINGDEALID;

        }
        //Is correlationId value in transaction_new_status table
        TransactionNewStatus transactionNewStatus = null;
        try
        {
            transactionNewStatus = transactionNewStatusDao
                .findByDealIdAndScheme( requestClearing.getCorrelationId()
                    .getValue(), requestClearing.getCorrelationId()
                    .getCorrelationIdScheme() );
        }
        catch ( FXFWSDaoException e )
        {
            final String msg = "exception occured when query dealId in transaction_new_status table";
            error( method, msg, e );
            throw new ManagerException( e.getErrorCode(), msg, e.sqlCode, e );
        }
        //If the value is in transaction_new_status table, but the status of the deal is not 'DEAL_CLEARED'
        if( null == transactionNewStatus
            || !Constant.DealStatus.DEAL_CLEARED.equals( transactionNewStatus
                .getDealStatus() ) )
        {
            debug(method,"transactionNewStatus:"+transactionNewStatus);
            debug(method,"Deal Id is not in transaction_new_status or value is not DEAL_CLEARED");
            return Constant.MessageCode.INVALIDDEALID;
        }
        try
        {
            //If value is in transaction_declear_status or transaction_declear_spool already
            if( null != transactionDeclearSpoolDao.findByDealIdAndScheme(
                requestClearing.getCorrelationId().getValue(), requestClearing
                    .getCorrelationId().getCorrelationIdScheme() )
                || null != transactionDeclearStatusDao
                    .findByDealIdAndScheme( requestClearing.getCorrelationId()
                        .getValue(), requestClearing.getCorrelationId()
                        .getCorrelationIdScheme() ) ){
                debug(method,"Duplicate Deal Id");
                return Constant.MessageCode.DUPLICATEDEALID;
            }

        }
        catch ( FXFWSDaoException e )
        {
            final String msg = "exception occured when query dealId in transaction_declear_status or transaction_declear_spool table";
            error( method, msg, e );
            throw new ManagerException( e.getErrorCode(), msg, e.sqlCode, e );
        }
        return null;
    }

    /**
     * create ResponseBody.
     * 
     * @param requestClearing
     * @param authentication
     * @param reason
     * @return ResponseBody
     * @Author: wu tao
     */
    public ResponseBody createResponseBody( RequestClearing requestClearing,
        Reason reason )
    {
        ResponseBody body = objectFactory.createResponseBody(
            new ResponseBody() ).getValue();

        if( null == reason )
        {//validate pass
            ResponseMessageHeader messageHeader = new ResponseMessageHeader();
            MessageId replyMessageId = requestClearing.getHeader()
                .getMessageId();
            messageHeader.setInReplyTo( replyMessageId );

            MessageId messageId = new MessageId();
            messageId.setMessageIdScheme( replyMessageId.getMessageIdScheme() );
            messageId.setValue( replyMessageId.getValue()
                + FpMLConstant.Response.RESPONSEIDSUFFIX );

            messageHeader.setMessageId( messageId );
            messageHeader.setSentBy( getResponseSentBy() );
            messageHeader.setCreationTimestamp( Util
                .convertToXMLGregorianCalendar( new Date() ) );
            //Acknowledgement
            Acknowledgement clearingAcknowledgement = new Acknowledgement();
            clearingAcknowledgement.setHeader( messageHeader );
            clearingAcknowledgement.setCorrelationId( requestClearing
                .getCorrelationId() );
            clearingAcknowledgement.setFpmlVersion( requestClearing
                .getFpmlVersion() );
            body.setClearingAcknowledgement( clearingAcknowledgement );
        }
        else
        {//have validate error
            NotificationMessageHeader messageHeader = new NotificationMessageHeader();
            MessageId replyMessageId = requestClearing.getHeader()
                .getMessageId();
            messageHeader.setInReplyTo( replyMessageId );

            MessageId messageId = new MessageId();
            messageId.setMessageIdScheme( replyMessageId.getMessageIdScheme() );
            messageId.setValue( replyMessageId.getValue()
                + FpMLConstant.Response.RESPONSEIDSUFFIX );

            messageHeader.setMessageId( messageId );
            messageHeader.setSentBy( getResponseSentBy() );
            messageHeader.setCreationTimestamp( Util
                .convertToXMLGregorianCalendar( new Date() ) );
            //ClearingRefused
            ClearingRefused clearingRefused = new ClearingRefused();
            clearingRefused.setFpmlVersion( requestClearing.getFpmlVersion() );
            clearingRefused.setHeader( messageHeader );
            clearingRefused.setCorrelationId( requestClearing
                .getCorrelationId() );
            body.setClearingRefused( clearingRefused );

            //Reason
            body.getClearingRefused().getReason().add( reason );
        }

        return body;
    }

}
