/*
 * SGX Public License Notice This software is the intellectual property of SGX.
 * The program may be used only in accordance with the terms of the license
 * agreement you entered into with SGX. 2003 Singapore Exchange Pte Ltd (SGX).
 * All rights reserved. 2 Shenton Way / #22-00 SGX Centre 1 Singapore( 068804 )
 */
package com.sgx.fxfws.manager;

import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.fpml.fpml_5.confirmation.ClearingStatusItem;
import org.fpml.fpml_5.confirmation.ClearingStatusValue;
import org.fpml.fpml_5.confirmation.MessageAddress;
import org.fpml.fpml_5.confirmation.MessageId;
import org.fpml.fpml_5.confirmation.Party;
import org.fpml.fpml_5.confirmation.PartyId;
import org.fpml.fpml_5.confirmation.ResponseMessageHeader;
import org.fpml.fpml_5.confirmation.TradeId;
import org.fpml.fpml_5.confirmation.TradeIdentifier;

import com.sgx.fxfws.dao.FXFWSDaoException;
import com.sgx.fxfws.dao.TransactionDeclearSpoolDao;
import com.sgx.fxfws.dao.TransactionDeclearStatusDao;
import com.sgx.fxfws.dao.TransactionNewSpoolDao;
import com.sgx.fxfws.dao.TransactionNewStatusDao;
import com.sgx.fxfws.dao.domain.TransactionDeclearSpool;
import com.sgx.fxfws.dao.domain.TransactionDeclearStatus;
import com.sgx.fxfws.dao.domain.TransactionNewSpool;
import com.sgx.fxfws.dao.domain.TransactionNewStatus;
import com.sgx.fxfws.util.Constant;
import com.sgx.fxfws.util.FpMLConstant;
import com.sgx.fxfws.util.HibernateUtilFactory;
import com.sgx.fxfws.util.Util;
import com.sgx.tdc.ws.fxfws.ClearingStatus;
import com.sgx.tdc.ws.fxfws.QueryClearing;
import com.sgx.tdc.ws.fxfws.ResponseBody;

/**
 * Manager for getting trade status web service
 * @author szf
 */
public class GetTradeStatusManager extends AbstractManager
{
    private static GetTradeStatusManager _manager;

    private TransactionDeclearStatusDao declearStatusDao;
    private TransactionDeclearSpoolDao declearSpoolDao;
    private TransactionNewStatusDao newStatusDao;
    private TransactionNewSpoolDao newSpoolDao;

    private final int DECLEAR_STATUS = 1;
    private final int DECLEAR_SPOOL = 2;
    private final int NEW_STATUS = 3;
    private final int NEW_SPOOL = 4;

    /**
     * Gets the GetTradeStatus instance
     * 
     * @return GetTradeStatus instance
     */
    public static final synchronized GetTradeStatusManager getInstance()
    {
        if( _manager == null )
        {
            _manager = new GetTradeStatusManager();
        }
        return _manager;
    }

    private GetTradeStatusManager()
    {
        super();
        declearStatusDao = new TransactionDeclearStatusDao(
            HibernateUtilFactory.ConfigMode.HIBERNATE_WEBSERVICE );
        declearSpoolDao = new TransactionDeclearSpoolDao(
            HibernateUtilFactory.ConfigMode.HIBERNATE_WEBSERVICE );
        newStatusDao = new TransactionNewStatusDao(
            HibernateUtilFactory.ConfigMode.HIBERNATE_WEBSERVICE );
        newSpoolDao = new TransactionNewSpoolDao(
            HibernateUtilFactory.ConfigMode.HIBERNATE_WEBSERVICE );

    }

    /**
     * get trade status
     * 
     * @param queryClearing
     * @param clientId
     * @return ResponseBody
     * @throws ManagerException 
     * @Author: szf
     */
    public ResponseBody getTradeStatus( QueryClearing queryClearing,
        String clientId ) throws ManagerException
    {
        String methodName = "getTradeStatus";
        debug( methodName, "queryClearing=" + Util.toMap( queryClearing )
            + ", clientId=" + clientId );
        ResponseBody responseBody = new ResponseBody();

        //input validate (may be any of clearingStatusItem validate fail  will be return)

        String result = FpMLManager.getInstance().validateSentBy(
            queryClearing.getHeader().getSentBy().getValue(),
            queryClearing.getHeader().getSentBy().getMessageAddressScheme(),
            clientId );
        // validate send by
        if( !Constant.MessageCode.WEBSERVICESUCCESS.equals( result ) )
        {
            debug( methodName, "validate send by fail" );
            responseBody.setReason( getReason( result ) );
            return responseBody;
        }
        if( queryClearing.getClearingStatusItem() == null )
        {
            mapResult( responseBody, queryClearing );
            return responseBody;
        }
        String msgCode = null;
        for ( ClearingStatusItem clearingStatusItem : queryClearing
            .getClearingStatusItem() )
        {
            if( ( msgCode = validateTradeIdentifier(clearingStatusItem.getTradeIdentifier()) ) != null )
            {
                responseBody.setReason( getReason( msgCode ) );
                return responseBody;
            }
            if( ( msgCode = validatePartyReference( clearingStatusItem
                .getTradeIdentifier().getPartyReference().getHref() ) ) != null )
            {
                responseBody.setReason( getReason( msgCode ) );
                return responseBody;
            }
            if( ( msgCode = validateTradeId( clearingStatusItem
                .getTradeIdentifier().getTradeIdOrVersionedTradeId(), clientId ) ) != null )
            {
                responseBody.setReason( getReason( msgCode ) );
                return responseBody;
            }
        }

        //check clearingStatusItem.size() more than the configured batch (100) of deals are requested
        if( queryClearing.getClearingStatusItem().size() > context
            .getIntegerProperty( Constant.ConfigKeys.MAX_PROCESS_TRADE_SIZE ) )
        {
            debug(methodName, "queryClearing.getClearingStatusItem().size() > MAX_PROCESS_TRADE_SIZE");
            responseBody.setReason( this
                .getReason( Constant.MessageCode.TOO_MANY_TRADE_IN_A_BATCH ) );
            return responseBody;
        }
        // get all trade id value
        Set<String> tradeIdValueSet = new HashSet<String>();
        // get a tradeId , all tradeId is same but value
        TradeId tradeId = ( TradeId ) queryClearing.getClearingStatusItem()
            .get( 0 ).getTradeIdentifier().getTradeIdOrVersionedTradeId().get(
                0 );
        for ( ClearingStatusItem clearingStatusItem : queryClearing
            .getClearingStatusItem() )
        {
            tradeIdValueSet.add( ( ( TradeId ) clearingStatusItem.getTradeIdentifier()
                .getTradeIdOrVersionedTradeId().get( 0 ) ).getValue() );
        }
        debug(methodName, "tradeIdValueSet=" +tradeIdValueSet);
        List<TransactionDeclearStatus> declearStatusList = null;
        List<TransactionDeclearSpool> declearSpoolList = null;
        List<TransactionNewStatus> newStatusList = null;
        List<TransactionNewSpool> newSpoolList = null;
        try
        {
            declearStatusList = declearStatusDao.findByDealIdAndSchemeAndClientId( tradeIdValueSet, tradeId.getTradeIdScheme(), clientId );
            declearSpoolList = declearSpoolDao.findByDealIdAndSchemeAndClientId( tradeIdValueSet, tradeId.getTradeIdScheme(), clientId );
            newStatusList = newStatusDao.findByDealIdAndSchemeAndClientId( tradeIdValueSet, tradeId.getTradeIdScheme(), clientId );
            newSpoolList = newSpoolDao.findByDealIdAndSchemeAndClientId( tradeIdValueSet, tradeId.getTradeIdScheme(), clientId );
            debug( methodName, "declearStatusList.size=" + declearStatusList.size()
                + ",declearSpoolList.size=" + declearSpoolList.size()
                + ",newStatusList.size=" + newStatusList.size() + ",newSpoolList.size="
                + newSpoolList.size() );
            
        }
        catch ( FXFWSDaoException e )
        {
            final String message = "Error when search for api4";
            error( methodName, message, e );
            throw new ManagerException( e.getErrorCode(), e.sqlCode, e );
        }
        // create map for search result of 4 tables
        Map<String, TransactionDeclearStatus> declearStatusMap = new HashMap<String, TransactionDeclearStatus>();
        Map<String, TransactionDeclearSpool> declearSpoolMap = new HashMap<String, TransactionDeclearSpool>();
        Map<String, TransactionNewStatus> newStatusMap = new HashMap<String, TransactionNewStatus>();
        Map<String, TransactionNewSpool> newSpoolMap = new HashMap<String, TransactionNewSpool>();
        // fill map
        if( declearStatusList != null )
        {
            for ( TransactionDeclearStatus item : declearStatusList )
            {
                declearStatusMap.put( item.getDealId(), item );
            }
        }
        if( declearSpoolList != null )
        {
            for ( TransactionDeclearSpool item : declearSpoolList )
            {
                declearSpoolMap.put( item.getDealId(), item );
            }
        }
        if( newStatusList != null )
        {
            for ( TransactionNewStatus item : newStatusList )
            {
                newStatusMap.put( item.getDealId(), item );
            }
        }
        if( newSpoolList != null )
        {
            for ( TransactionNewSpool item : newSpoolList )
            {
                newSpoolMap.put( item.getDealId(), item );
            }
        }
        debug( methodName, "declearStatusMap.size=" + declearStatusMap.size()
            + ",declearSpoolMap.size=" + declearSpoolMap.size()
            + ",newStatusMap.size=" + newStatusMap.size() + ",newSpoolMap.size="
            + newSpoolMap.size() );
        ClearingStatusValue clearingStatusValue = null;
        String tradeIdValue = null;
        // process batches
        for ( ClearingStatusItem clearingStatusItem : queryClearing
            .getClearingStatusItem() )
        {
            debug( methodName, "find By dealId from 4 map" );
            tradeIdValue = ( ( TradeId ) clearingStatusItem
                .getTradeIdentifier().getTradeIdOrVersionedTradeId().get( 0 ) )
                .getValue();
            if( declearStatusMap.containsKey( tradeIdValue ) )
            {
                debug( methodName, "declearStatusMap found data" );
                clearingStatusValue = new ClearingStatusValue();
                clearingStatusValue
                    .setValue( this.getStatus( declearStatusMap.get(
                        tradeIdValue ).getDealStatus(), DECLEAR_STATUS, null ) );
                clearingStatusItem.setClearingStatusValue( clearingStatusValue );
                continue;
            }
            if( declearSpoolMap.containsKey( tradeIdValue ) )
            {
                debug( methodName, "declearSpoolMap found data" );
                clearingStatusValue = new ClearingStatusValue();
                clearingStatusValue.setValue( this.getStatus( null,
                    DECLEAR_SPOOL, null ) );
                clearingStatusItem.setClearingStatusValue( clearingStatusValue );
                continue;
            }
            if( newStatusMap.containsKey( tradeIdValue ) )
            {
                debug( methodName, "newStatusMap found data" );
                clearingStatusValue = new ClearingStatusValue();
                clearingStatusValue.setValue( this.getStatus( newStatusMap.get(
                    tradeIdValue ).getDealStatus(), NEW_STATUS, newStatusMap
                    .get( tradeIdValue ).getClearerMessageCode() ) );
                clearingStatusItem.setClearingStatusValue( clearingStatusValue );
                continue;
            }
            if( newSpoolMap.containsKey( tradeIdValue ) )
            {
                debug( methodName, "newSpoolMap found data" );
                clearingStatusValue = new ClearingStatusValue();
                clearingStatusValue.setValue( this.getStatus( null, NEW_SPOOL,
                    null ) );
                clearingStatusItem.setClearingStatusValue( clearingStatusValue );
                continue;
            }
            debug( methodName, "4 map not found data" );
            clearingStatusValue = new ClearingStatusValue();
            clearingStatusValue.setValue( Constant.DealStatus.DEAL_NOT_FOUND );
            clearingStatusItem.setClearingStatusValue( clearingStatusValue );
        }
        mapResult( responseBody, queryClearing );
        return responseBody;
        
        /*
        try
        {
            // process batches
            ClearingStatusValue clearingStatusValue = null;
            TradeId tradeId = null;

            TransactionDeclearStatus declearStatus = null;
            TransactionDeclearSpool declearSpool = null;
            TransactionNewStatus newStatus = null;
            TransactionNewSpool newSpool = null;
            for ( ClearingStatusItem clearingStatusItem : queryClearing
                .getClearingStatusItem() )
            {
                debug(methodName, "findByDealIdAndSchemeAndClientId from 4 tables");
                tradeId = ( TradeId ) clearingStatusItem.getTradeIdentifier()
                    .getTradeIdOrVersionedTradeId().get( 0 );
                declearStatus = declearStatusDao
                    .findByDealIdAndSchemeAndClientId( tradeId.getValue(), tradeId
                        .getTradeIdScheme(), clientId );
                if( declearStatus != null )
                {
                    debug(methodName, "declearStatusDao found data");
                    clearingStatusValue = new ClearingStatusValue();
                    clearingStatusValue.setValue( this.getStatus( declearStatus
                        .getDealStatus(), DECLEAR_STATUS, null ) );
                    clearingStatusItem
                        .setClearingStatusValue( clearingStatusValue );
                    continue;
                }
                declearSpool = declearSpoolDao
                    .findByDealIdAndSchemeAndClientId( tradeId.getValue(), tradeId
                        .getTradeIdScheme(), clientId );
                if( declearSpool != null )
                {
                    debug(methodName, "declearSpoolDao found data");
                    clearingStatusValue = new ClearingStatusValue();
                    clearingStatusValue.setValue( this.getStatus( null,
                        DECLEAR_SPOOL, null ) );
                    clearingStatusItem
                        .setClearingStatusValue( clearingStatusValue );
                    continue;
                }
                newStatus = newStatusDao.findByDealIdAndSchemeAndClientId(
                    tradeId.getValue(), tradeId.getTradeIdScheme(), clientId );
                if( newStatus != null )
                {
                    debug(methodName, "newStatusDao found data");
                    clearingStatusValue = new ClearingStatusValue();
                    clearingStatusValue.setValue( this.getStatus( newStatus
                        .getDealStatus(), NEW_STATUS ,newStatus.getClearerMessageCode()) );
                    clearingStatusItem
                        .setClearingStatusValue( clearingStatusValue );
                    continue;
                }
                newSpool = newSpoolDao.findByDealIdAndSchemeAndClientId(
                    tradeId.getValue(), tradeId.getTradeIdScheme(), clientId );
                if( newSpool != null )
                {
                    debug(methodName, "newSpoolDao found data");
                    clearingStatusValue = new ClearingStatusValue();
                    clearingStatusValue.setValue( this.getStatus( null,
                        NEW_SPOOL, null ) );
                    clearingStatusItem
                        .setClearingStatusValue( clearingStatusValue );
                    continue;
                }
                debug(methodName, "4 table not found data");
                clearingStatusValue = new ClearingStatusValue();
                clearingStatusValue
                    .setValue( Constant.DealStatus.DEAL_NOT_FOUND );
                clearingStatusItem.setClearingStatusValue( clearingStatusValue );
            }
            mapResult( responseBody, queryClearing );
            return responseBody;
        }
        catch ( FXFWSDaoException e )
        {
            final String message = "Error when search for api4";
            error( methodName, message, e );
            throw new ManagerException( e.getErrorCode(), e.sqlCode, e );
        }*/
    }

    /**
     * mapping status and return
     * @return String
     * @param status
     * @Author: szf
     */
    public String getStatus( String status, int table, String clearerMessageCode )
    {
        String methodName = "getStatus";
        debug( methodName, "status=" + status + ", table=" + table
            + ", clearerMessageCode=" + clearerMessageCode );
        if( table == this.DECLEAR_STATUS )
        {
            debug(methodName, "table == DECLEAR_STATUS");
            if( Constant.DealStatus.PENDING_DECLEAR.equals( status ) )
            {
                return Constant.DealStatus.PENDING_DECLEAR;
            }
            else if( Constant.DealStatus.DEAL_DECLEARED.equals( status ) )
            {
                return Constant.DealStatus.DEAL_DECLEARED;
            }
            else if( Constant.DealStatus.REJECT_DECLEAR.equals( status ) )
            {
                return Constant.DealStatus.DEAL_CLEARED;
            }
        }
        else if( table == this.DECLEAR_SPOOL )
        {
            debug(methodName, "table == DECLEAR_SPOOL");
            return Constant.DealStatus.PARKED_DECLEAR;
        }
        else if( table == this.NEW_STATUS )
        {
            debug(methodName, "table == NEW_STATUS");
            if( Constant.DealStatus.DEAL_RECEIVED.equals( status ) )
            {
                return Constant.DealStatus.DEAL_RECEIVED;
            }
            else if( Constant.DealStatus.PENDING_CLEAR.equals( status ) )
            {
                return Constant.DealStatus.PENDING_CLEAR;
            }
            else if( Constant.DealStatus.REJECT_CLEAR.equals( status ) )
            {
                return Util.formatMessage( Constant.DealStatus.REJECT_CLEAR
                    + "({0})", Util.nullOrStringNullToEmpty( clearerMessageCode ) ); 
            }
            else if( Constant.DealStatus.DEAL_CLEARED.equals( status ) )
            {
                return Constant.DealStatus.DEAL_CLEARED;
            }
        }
        else if( table == this.NEW_SPOOL )
        {
            debug(methodName, "table == NEW_SPOOL");
            return Constant.DealStatus.DEAL_RECEIVED;
        }

        return Constant.DealStatus.DEAL_NOT_FOUND;
    }

    /**
     * map result for process success
     * 
     * @param responseBody
     * @param queryClearing
     * @Author: szf
     */
    public void mapResult( ResponseBody responseBody,
        QueryClearing queryClearing )
    {
        String methodName = "mapResult";
        info( methodName, "[responseBody=]" + responseBody
            + ",[queryClearing=]" + queryClearing );

        ResponseMessageHeader repHeader = new ResponseMessageHeader();
        MessageId messageId = new MessageId();
        repHeader.setMessageId( messageId );
        // set response id
        messageId.setValue( queryClearing.getHeader().getMessageId().getValue()
            + FpMLConstant.Response.RESPONSEIDSUFFIX );
        messageId.setMessageIdScheme( queryClearing.getHeader().getMessageId()
            .getMessageIdScheme() );
        // set inReplyTo
        repHeader.setInReplyTo( queryClearing.getHeader().getMessageId() );
        //set SentBy
        MessageAddress msgAddr = new MessageAddress();
        msgAddr
            .setMessageAddressScheme( context.getProperty( FpMLConstant.FpMLScheme.MESSAGEADDRESSSCHEMEFORPROVIDER ) );
        msgAddr.setValue( context.getProperty( FpMLConstant.FpMLField.SENTBY ) );
        repHeader.setSentBy( msgAddr );
        // set creationTimestamp
        repHeader.setCreationTimestamp( Util
            .convertToXMLGregorianCalendar( Calendar.getInstance().getTime() ) );

        ClearingStatus clearingStatus = new ClearingStatus();
        // set header
        clearingStatus.setHeader( repHeader );

        // set clearingStatusItem
        if( queryClearing.getClearingStatusItem() != null )
        {
            clearingStatus.getClearingStatusItem().addAll(
                queryClearing.getClearingStatusItem() );
        }
        // set party
        if( queryClearing.getParty() != null )
        {
            clearingStatus.getParty().addAll( queryClearing.getParty() );
        }

        responseBody.setClearingStatus( clearingStatus );

    }

    /**
     * Only 1 party to be provided - Party of the original request.
     * 
     * @return String
     * @Author: szf
     */
    public String validateTradeIdentifier(TradeIdentifier tradeIdentifier)
    {
        String methodName = "validateTradeIdentifier";
        info( methodName, "[tradeIdentifier=]" + tradeIdentifier );
        if(tradeIdentifier == null)
        {
            debug(methodName, "traderIdentifier == null");
            return Constant.MessageCode.INVALIDPARTYTYPE;
        }
        return null;
    }

    /**
     * check Party reference (href) present or not
     * 
     * @param href
     * @return String
     * @Author: szf
     */
    public String validatePartyReference( Object href )
    {
        String methodName = "validatePartyReference";
        info( methodName, "[href=]" + href );
        
        if( href == null || !(href instanceof Party) )
        {
            debug( methodName, " href == null or can not cast to Party " );
            return Constant.MessageCode.INVALIDFPMLMESSAGE;
        }

        Party party = (Party)href;
        
        return validatePartyId( party.getPartyId() );
        
        /*String resultOfValidatePartyIdBIC = validatePartyIdBIC( party
            .getPartyId() );
        if( resultOfValidatePartyIdBIC != null )
        {
            debug( methodName, " invalid partyId bic" );
            return resultOfValidatePartyIdBIC;
        }
        String resultOfValidatePartyIdBICValue = validatePartyIdBICValue( party
            .getPartyId() );
        if( resultOfValidatePartyIdBICValue != null )
        {
            debug( methodName, " invalid partyId bic value" );
            return resultOfValidatePartyIdBICValue;
        }

        String resultOfValidatePartyIdType = validatePartyIdType( party
            .getPartyId() );
        if( resultOfValidatePartyIdType != null )
        {
            debug( methodName, " invalid partyId type" );
            return resultOfValidatePartyIdType;
        }

        String resultOfValidatePartyIdTypeValue = validatePartyIdTypeValue( party
            .getPartyId() );
        if( resultOfValidatePartyIdTypeValue != null )
        {
            debug( methodName, " invalid partyId type value" );
            return resultOfValidatePartyIdTypeValue;
        }

        String resultOfValidatePartyIdAccount = validatePartyIdAccount( party
            .getPartyId() );
        if( resultOfValidatePartyIdAccount != null )
        {
            debug( methodName, " invalid partyId account" );
            return resultOfValidatePartyIdAccount;
        }
        return null;*/
    }

    /**
     * This tradeIdScheme must be a specifying the same scheme as the
     * correlationIdScheme
     * 
     * @param tradeId
     * @param clientId
     * @return String
     * @Author: szf
     */
    public String validateTradeId( List<Object> tradeIds, String clientId )
    {
        String methodName = "validateTradeId";
        info( methodName, "[tradeIds=]" + tradeIds + ",[clientId=]" + clientId );

        if( tradeIds == null || tradeIds.size() != 1 || Util.isEmpty( ( ( TradeId ) tradeIds.get( 0 ) ).getValue() ) )
        {
            debug(
                methodName,
                " tradeIds == null || tradeIds.size() !=1 || tradeIds.get(0).getValue() == empty" );
            return Constant.MessageCode.MISSINGORMULTIPLETRADEIDS;
        }

        if( !( context.getProperty( FpMLConstant.FpMLScheme.TRADEIDSCHEME ) + clientId )
            .equals( ( ( TradeId ) tradeIds.get( 0 ) ).getTradeIdScheme() ) )
        {
            debug( methodName, " invalide trade id scheme " );
            return Constant.MessageCode.INVALIDTRADEIDSCHEME;
        }
        return null;
    }
    
    /**
     * validate partyid   bic, type, account, bicValue, typeValue
     * 
     * @param partyIds
     * @return String
     * @Author: szf
     */
    public String validatePartyId( List<PartyId> partyIds )
    {
        String methodName = "validateAll";
        info( methodName, "[partyIds=]" + Util.toMap( partyIds ) );
        String typeSchema = context
            .getProperty( FpMLConstant.FpMLScheme.PARTYIDSCHEMEFORPARTYTYPE );
        String accountSchema = context
            .getProperty( FpMLConstant.FpMLScheme.PARTYIDSCHEMEFORACCOUNT );
        String bicSchema = context
            .getProperty( FpMLConstant.FpMLScheme.PARTYIDSCHEMEFORBIC );

        int bicSchemaNum = 0;
        int typeSchemaNum = 0;
        int accountSchemaNum = 0;
        for ( PartyId partyId : partyIds )
        {
            if( bicSchema.equals( partyId.getPartyIdScheme() ) )
            {
                if( Util.isEmpty( partyId.getValue() ) )
                {
                    debug( methodName, " partyId.getValue() is empty" );
                    return Constant.MessageCode.MISSINGORMULTIPLEBIC;
                }
                bicSchemaNum++;
            }
            else if( typeSchema.equals( partyId.getPartyIdScheme() ) )
            {
                if( !FpMLConstant.PartyId.PARTYTYPE.equals( partyId.getValue() ) )
                {
                    debug( methodName, "partyId.getValue()="
                        + partyId.getValue() + ",thisValue not equals "
                        + FpMLConstant.PartyId.PARTYTYPE );
                    return Constant.MessageCode.INVALIDPARTYTYPE;
                }
                typeSchemaNum++;
            }
            else if( accountSchema.equals( partyId.getPartyIdScheme() ) )
            {
                accountSchemaNum++;
            }
        }
        // miss bic scheme (zero node)
        if( bicSchemaNum == 0 )
        {
            debug( methodName, " bicSchemaNum == 0" );
            return Constant.MessageCode.MISSINGBICPARTYIDSCHEME;
        }
        // more than one node
        else if( bicSchemaNum > 1 )
        {
            debug( methodName, " bicSchemaNum > 1" );
            return Constant.MessageCode.MISSINGORMULTIPLEBIC;
        }
        else if( typeSchemaNum != 1 )
        {
            debug( methodName, "typeSchemaNum != 1" );
            return Constant.MessageCode.MISSINGORDUPLICATEPARTYTYPE;
        }
        else if( accountSchemaNum > 1 )
        {
            debug( methodName, " accountSchemaNum > 1" );
            return Constant.MessageCode.DUPLICATEACCOUNTS;
        }

        return null;
    }
}
