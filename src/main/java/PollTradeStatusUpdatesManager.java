/*
 * SGX Public License Notice This software is the intellectual property of SGX.
 * The program may be used only in accordance with the terms of the license
 * agreement you entered into with SGX. 2003 Singapore Exchange Pte Ltd (SGX).
 * All rights reserved. 2 Shenton Way / #22-00 SGX Centre 1 Singapore( 068804 )
 */
package com.sgx.fxfws.manager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.fpml.fpml_5.confirmation.ClearingStatusValue;
import org.fpml.fpml_5.confirmation.MessageAddress;
import org.fpml.fpml_5.confirmation.MessageId;
import org.fpml.fpml_5.confirmation.Party;
import org.fpml.fpml_5.confirmation.PartyId;
import org.fpml.fpml_5.confirmation.PartyName;
import org.fpml.fpml_5.confirmation.PartyReference;
import org.fpml.fpml_5.confirmation.ResponseMessageHeader;
import org.fpml.fpml_5.confirmation.TradeId;
import org.fpml.fpml_5.confirmation.TradeIdentifier;

import com.sgx.fxfws.bean.TradeAndTransactionStatus;
import com.sgx.fxfws.dao.FXFWSDaoException;
import com.sgx.fxfws.dao.TradeStatusCacheDao;
import com.sgx.fxfws.util.Constant;
import com.sgx.fxfws.util.FpMLConstant;
import com.sgx.fxfws.util.HibernateUtilFactory;
import com.sgx.fxfws.util.Util;
import com.sgx.tdc.ws.fxfws.ClearingStatus;
import com.sgx.tdc.ws.fxfws.ClearingStatusUpdateItem;
import com.sgx.tdc.ws.fxfws.QueryClearing;
import com.sgx.tdc.ws.fxfws.ResponseBody;

/**
 * Manager for getting trade status web service
 * @author SZF
 */
public class PollTradeStatusUpdatesManager extends AbstractManager
{
    private static PollTradeStatusUpdatesManager _manager;
    private TradeStatusCacheDao tradeStatusCacheDao;
    /**
     * Gets the GetTradeStatus instance
     * 
     * @return GetTradeStatus instance
     */
    public static final synchronized PollTradeStatusUpdatesManager getInstance()
    {
        if( _manager == null )
        {
            _manager = new PollTradeStatusUpdatesManager();
        }
        return _manager;
    }

    private PollTradeStatusUpdatesManager()
    {
        super();
        tradeStatusCacheDao = new TradeStatusCacheDao(HibernateUtilFactory.ConfigMode.HIBERNATE_WEBSERVICE);
        
    }

    /**
     * get trade status
     * 
     * @param queryClearing
     * @param clientId
     * @return ResponseBody
     * @throws ManagerException 
     * @Author: SZF
     */
    public ResponseBody pollTradeStatusUpdates( QueryClearing queryClearing,
        String clientId ) throws ManagerException
    {
        String methodName = "pollTradeStatusUpdates";
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
        try
        {
            // process 

            //dao invoke
            List<TradeAndTransactionStatus> tradeAndTransactionStatus = tradeStatusCacheDao
                .findTradeStatusCachesByClientId( clientId );

            if( tradeAndTransactionStatus == null || tradeAndTransactionStatus.size() == 0)
            {
                debug(methodName, " tradeAndTransactionStatus == null || tradeAndTransactionStatus.size() == 0 ");
                return null;
            }
            // 2 output list  List<ClearingStatusUpdateItem>  and List<Party>
            List<ClearingStatusUpdateItem> clearingStatusUpdateItems = new ArrayList<ClearingStatusUpdateItem>();
            List<Party> parties = new ArrayList<Party>();
            debug(methodName, "define var reference;");
            //reference for Party
            Party party = null;
            PartyName partyName = null;
            PartyId partyIdBic = null;
            PartyId partyIdPartyType = null;
            //reference for ClearingStatusUpdateItem
            ClearingStatusUpdateItem clearingStatusUpdateItem = null;
            ClearingStatusValue clearingStatusValue = null;
            TradeIdentifier tradeIdentifier = null;
            PartyReference partyReference = null;
            TradeId tradeId = null;
            //currentTime
            Date currentDate = new Date();
            //id list
            List<Long> idList = new ArrayList<Long>();
            Map<String,Party> existsParties = new HashMap<String,Party>();
            debug(methodName, "new an existsParties");
            for( TradeAndTransactionStatus status : tradeAndTransactionStatus )
            {
                debug(methodName, " for loop inner ");
                idList.add( status.getId() );
                if( !existsParties.containsKey( status.getPartyBic() )  )
                {
                    debug( methodName, "partyBic not exists " );
                    // create Party
                    party = new Party();
                    //bic code
                    party.setId( status.getPartyBic() );
                    //party name
                    partyName = new PartyName();
                    partyName.setValue( status.getPartyName() );
                    party.setPartyName( partyName );
                    //party id for bic
                    partyIdBic = new PartyId();
                    partyIdBic
                        .setPartyIdScheme( context
                            .getProperty( FpMLConstant.FpMLScheme.PARTYIDSCHEMEFORBIC ) );
                    partyIdBic.setValue( status.getPartyBic() );
                    // party id for partytype
                    partyIdPartyType = new PartyId();
                    partyIdPartyType
                        .setPartyIdScheme( context
                            .getProperty( FpMLConstant.FpMLScheme.PARTYIDSCHEMEFORPARTYTYPE ) );
                    partyIdPartyType.setValue( FpMLConstant.PartyId.PARTYTYPE );
                    // add party id
                    party.getPartyId().add( partyIdBic );
                    party.getPartyId().add( partyIdPartyType );
                    // add party
                    parties.add( party );
                    
                    debug( methodName, "add partyBic to map" );
                    existsParties.put( status.getPartyBic(), party );
                }
                else
                {
                    debug( methodName, "partyBic exists " );
                    party = existsParties.get( status.getPartyBic() );
                }

                // create ClearingStatusUpdateItem
                clearingStatusUpdateItem = new ClearingStatusUpdateItem();
                // ClearingStatusValue
                clearingStatusValue = new ClearingStatusValue();
                clearingStatusValue.setValue( getStatus(
                    status.getDealStatus(), status.getClearerMessageCode() ) );
                // tradeIdentifier->party reference
                partyReference = new PartyReference();
                partyReference.setHref( party );
                // tradeIdentifier->tradeId
                tradeId = new TradeId();
                tradeId.setTradeIdScheme( context
                    .getProperty( FpMLConstant.FpMLScheme.TRADEIDSCHEME )
                    + clientId );
                tradeId.setValue( status.getDealId() );
                
                tradeIdentifier = new TradeIdentifier();
                // set tradeIdentifier value
                tradeIdentifier.setPartyReference( partyReference );
                tradeIdentifier.getTradeIdOrVersionedTradeId().add( tradeId );
                // set clearingStatusUpdateItem value
                clearingStatusUpdateItem.setTradeIdentifier( tradeIdentifier );
                clearingStatusUpdateItem
                    .setClearingStatusValue( clearingStatusValue );
                
                clearingStatusUpdateItems.add( clearingStatusUpdateItem );
                //clearingStatusUpdateItem.setTimeStamp(Util.convertToXMLGregorianCalendar( currentDate )  );
                clearingStatusUpdateItem.setTimeStamp(Util.convertToXMLGregorianCalendar( status.getTimestamp() )  );

            }
            // update 
            debug(methodName, "will updateTradeStatusCacheById ");
            tradeStatusCacheDao.updateTradeStatusCacheById( idList, currentDate );
            
            mapResult( responseBody, queryClearing, clearingStatusUpdateItems, parties );
            return responseBody;
        }
        catch ( FXFWSDaoException e )
        {
            final String message = "Error when search for api5";
            error( methodName, message, e );
            throw new ManagerException( e.getErrorCode(), e.sqlCode, e );
        }
    }

    /**
     * 
     * map result for process success
     * @param responseBody
     * @param queryClearing
     * @param clearingStatusUpdateItems
     * @param parties
     * @Author: SZF
     */
    public void mapResult( ResponseBody responseBody,
        QueryClearing queryClearing,
        List<ClearingStatusUpdateItem> clearingStatusUpdateItems,
        List<Party> parties )
    {
        String methodName = "mapResult";
        info( methodName, "[responseBody=]" + responseBody
            + ",[queryClearing=]" + queryClearing
            + "[clearingStatusUpdateItems=]" + clearingStatusUpdateItems
            + ",[parties=]" + parties );

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

        // set clearingStatusUpdateItem
        clearingStatus.getClearingStatusUpdateItem().addAll( clearingStatusUpdateItems );
        // set party
        clearingStatus.getParty().addAll( parties );

        responseBody.setClearingStatus( clearingStatus );
    }

    /**
     * mapping deal status
     * 
     * @param status
     * @param clearerMessageCode
     * @return String
     * @Author: SZF
     */
    public String getStatus( String status, String clearerMessageCode )
    {
        String methodName = "getStatus";
        debug( methodName, "status=" + status + ", clearerMessageCode="
            + clearerMessageCode );
        if( Constant.DealStatus.REJECT_CLEAR.equals( status )
            || Constant.DealStatus.REJECT_DECLEAR.equals( status ) )
        {
            return Util.formatMessage( status + "({0})",
                Util.nullOrStringNullToEmpty( clearerMessageCode ) );
        }
        return status;
    }

}
