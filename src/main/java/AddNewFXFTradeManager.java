/*
 * SGX Public License Notice This software is the intellectual property of SGX.
 * The program may be used only in accordance with the terms of the license
 * agreement you entered into with SGX. 2003 Singapore Exchange Pte Ltd (SGX).
 * All rights reserved. 2 Shenton Way / #22-00 SGX Centre 1 Singapore( 068804 )
 */
package com.sgx.fxfws.manager;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.datatype.XMLGregorianCalendar;
import org.fpml.fpml_5.confirmation.CorrelationId;
import org.fpml.fpml_5.confirmation.FxCashSettlement;
import org.fpml.fpml_5.confirmation.FxFixing;
import org.fpml.fpml_5.confirmation.FxSingleLeg;
import org.fpml.fpml_5.confirmation.IdentifiedDate;
import org.fpml.fpml_5.confirmation.Party;
import org.fpml.fpml_5.confirmation.PartyId;
import org.fpml.fpml_5.confirmation.PartyReference;
import org.fpml.fpml_5.confirmation.PartyTradeIdentifier;
import org.fpml.fpml_5.confirmation.Payment;
import org.fpml.fpml_5.confirmation.QuoteBasisEnum;
import org.fpml.fpml_5.confirmation.RequestClearing;
import org.fpml.fpml_5.confirmation.TradeId;
import com.sgx.fxfws.dao.FXFWSDaoException;
import com.sgx.fxfws.dao.TransactionNewSpoolDao;
import com.sgx.fxfws.util.Constant;
import com.sgx.fxfws.util.FpMLConstant;
import com.sgx.fxfws.util.HibernateUtilFactory;
import com.sgx.fxfws.util.Util;
import com.sgx.fxfws.dao.TransactionNewStatusDao;
import com.sgx.fxfws.dao.domain.TransactionNewSpool;
import com.sgx.tdc.ws.fxfws.HashToken;

import org.fpml.fpml_5.confirmation.ProductType;
import org.fpml.fpml_5.confirmation.Currency;

/**
 * This is a manager class that adding new FXF trade web service.
 * 
 * @author $Author: hypan@cn.ufinity.com $
 */
public class AddNewFXFTradeManager extends AbstractManager
{

    private static AddNewFXFTradeManager _manager;
    private TransactionNewSpoolDao transactionNewSpoolDao;
    private TransactionNewStatusDao transactionNewStatusDao;

    /**
     * Gets the AddNewFXFTradeManager instance
     * 
     * @return AddNewFXFTradeManager instance
     */
    public static final synchronized AddNewFXFTradeManager getInstance()
    {
        if( _manager == null )
        {
            _manager = new AddNewFXFTradeManager();
        }
        return _manager;
    }

    /**
     * Constructor for AddNewFXFTradeManager
     */
    private AddNewFXFTradeManager()
    {
        super();
        transactionNewSpoolDao = new TransactionNewSpoolDao(
            HibernateUtilFactory.ConfigMode.HIBERNATE_WEBSERVICE );
        transactionNewStatusDao = new TransactionNewStatusDao(
            HibernateUtilFactory.ConfigMode.HIBERNATE_WEBSERVICE );

    }

    /**
     * validate Input Params
     * 
     * @param request
     * @param clientId
     * @return String messageCode
     * @throws ManagerException e
     * @Author: hypan
     */
    public String validateInputParams( RequestClearing request, String clientId )
        throws ManagerException
    {

        final String methodName = "validateInputParams";
        debug( methodName, "param[request]:" + request + ",param[clientId]:"
            + clientId );
        // contains PartyReference of partyTradeIdentifier,exchangedCurrency1,exchangedCurrency2
        Set<Party> partys = new HashSet<Party>();

        String messageCode = Constant.MessageCode.WEBSERVICESUCCESS;

        //validate sendBy
        messageCode = FpMLManager.getInstance()
            .validateSentBy( request.getHeader().getSentBy().getValue(),
                request.getHeader().getSentBy().getMessageAddressScheme(),
                clientId );

        if( !Constant.MessageCode.WEBSERVICESUCCESS.equals( messageCode ) )
        {
            debug( methodName, "validate sendBy is false" );
            return messageCode;
        }
        //validate isCorrection is not false
        messageCode = validateIsCorrection( request.isIsCorrection() );
        if( !Constant.MessageCode.WEBSERVICESUCCESS.equals( messageCode ) )
        {
            debug( methodName, "validate IsCorrection is false" );
            return messageCode;
        }

        //Validate correlationId
        messageCode = validateCorrelationId( request.getCorrelationId(),
            clientId );
        if( !Constant.MessageCode.WEBSERVICESUCCESS.equals( messageCode ) )
        {
            debug( methodName, "validate CorrelationId is false" );
            return messageCode;
        }

        //Validate sequenceNumber
        messageCode = validateSequenceNumber( request.getSequenceNumber() );
        if( !Constant.MessageCode.WEBSERVICESUCCESS.equals( messageCode ) )
        {
            debug( methodName, "validate SequenceNumber is false" );
            return messageCode;
        }

        //Validate partyTradeIdentifier
        //Validate tradeId

        List<PartyTradeIdentifier> partyTradeIdentifier = request.getTrade()
            .getTradeHeader().getPartyTradeIdentifier();
        messageCode = validatePartyTradeIdentifierAndTradeId(
            partyTradeIdentifier, partys );
        if( !Constant.MessageCode.WEBSERVICESUCCESS.equals( messageCode ) )
        {
            debug( methodName,
                "validate partyTradeIdentifier or tradeId is false" );
            return messageCode;
        }

        //Validate tradeDate
        messageCode = validateTradeDate( request.getTrade().getTradeHeader()
            .getTradeDate() );
        if( !Constant.MessageCode.WEBSERVICESUCCESS.equals( messageCode ) )
        {
            debug( methodName, "validate TradeDate is false" );
            return messageCode;
        }

        //Validate productType
        FxSingleLeg fxSingleLeg = ( FxSingleLeg ) request.getTrade()
            .getProduct().getValue();
        messageCode = validateProductType( fxSingleLeg.getProductType() );
        if( !Constant.MessageCode.WEBSERVICESUCCESS.equals( messageCode ) )
        {
            debug( methodName, "validate ProductType is false" );
            return messageCode;
        }

        //Validate Seller of Currency 1
        messageCode = validatePartyReference( fxSingleLeg
            .getExchangedCurrency1().getPayerPartyReference(), partys );
        if( !Constant.MessageCode.WEBSERVICESUCCESS.equals( messageCode ) )
        {
            debug( methodName, "validate Seller of Currency 1 is false" );
            return messageCode;
        }

        //Validate Buyer of Currency 1
        messageCode = validatePartyReference( fxSingleLeg
            .getExchangedCurrency1().getReceiverPartyReference(), partys );
        if( !Constant.MessageCode.WEBSERVICESUCCESS.equals( messageCode ) )
        {
            debug( methodName, "validate Buyer of Currency 1 is false" );
            return messageCode;
        }

        //Validate Currency 1
        messageCode = this.validateCurrencyforCurrency1( fxSingleLeg
            .getExchangedCurrency1().getPaymentAmount().getCurrency() );
        if( !Constant.MessageCode.WEBSERVICESUCCESS.equals( messageCode ) )
        {
            debug( methodName,
                "validate currency for exchangedCurrency1 is false" );
            return messageCode;
        }

        //Validate Amount for Currency 1
        messageCode = validateAmountforCurrency1( fxSingleLeg
            .getExchangedCurrency1().getPaymentAmount().getAmount() );
        if( !Constant.MessageCode.WEBSERVICESUCCESS.equals( messageCode ) )
        {
            debug( methodName, "validate Amount for Currency 1 is false" );
            return messageCode;
        }

        //Validate Seller of Currency 2

        messageCode = validatePartyReference( fxSingleLeg
            .getExchangedCurrency2().getPayerPartyReference(), partys );
        if( !Constant.MessageCode.WEBSERVICESUCCESS.equals( messageCode ) )
        {
            debug( methodName, "validate Seller of Currency 2 is false" );
            return messageCode;
        }

        //Validate Buyer of Currency 2
        messageCode = validatePartyReference( fxSingleLeg
            .getExchangedCurrency2().getReceiverPartyReference(), partys );
        if( !Constant.MessageCode.WEBSERVICESUCCESS.equals( messageCode ) )
        {
            debug( methodName, "validate Buyer of Currency 2 is false" );
            return messageCode;
        }

        //Validate Currency 2
        messageCode = validateCurrencyforCurrency2( fxSingleLeg
            .getExchangedCurrency2().getPaymentAmount().getCurrency() );
        if( !Constant.MessageCode.WEBSERVICESUCCESS.equals( messageCode ) )
        {
            debug( methodName,
                "validate currency for exchangedCurrency2 is false" );
            return messageCode;
        }

        //Validate Amount for Currency 2
        messageCode = validateAmountforCurrency2( fxSingleLeg
            .getExchangedCurrency2().getPaymentAmount().getAmount() );
        if( !Constant.MessageCode.WEBSERVICESUCCESS.equals( messageCode ) )
        {
            debug( methodName, "validate Amount for Currency 1 is false" );
            return messageCode;
        }

        //Validate Settlement Date
        messageCode = validateSettlementDate( fxSingleLeg.getValueDate() );
        if( !Constant.MessageCode.WEBSERVICESUCCESS.equals( messageCode ) )
        {
            debug( methodName, "validate Settlement Date is false" );
            return messageCode;
        }

        //Validate Basis for exchange rate
        messageCode = validateQuotedBasis( fxSingleLeg.getExchangeRate()
            .getQuotedCurrencyPair().getQuoteBasis() );
        if( !Constant.MessageCode.WEBSERVICESUCCESS.equals( messageCode ) )
        {
            debug( methodName, "validate Basis for exchange rate is false" );
            return messageCode;
        }

        //Validate Currency 1
        messageCode = validateCurrency1( fxSingleLeg.getExchangeRate()
            .getQuotedCurrencyPair().getCurrency1(), fxSingleLeg );
        if( !Constant.MessageCode.WEBSERVICESUCCESS.equals( messageCode ) )
        {
            debug( methodName, "validate Currency 1  is false" );
            return messageCode;
        }

        //Validate Currency 2
        messageCode = validateCurrency2( fxSingleLeg.getExchangeRate()
            .getQuotedCurrencyPair().getCurrency2(), fxSingleLeg
            .getExchangeRate().getQuotedCurrencyPair().getCurrency1(),
            fxSingleLeg );
        if( !Constant.MessageCode.WEBSERVICESUCCESS.equals( messageCode ) )
        {
            debug( methodName, "validate Currency 2  is false" );
            return messageCode;
        }

        //Validate Exchange rate
        messageCode = validateRate( fxSingleLeg.getExchangeRate().getRate() );
        if( !Constant.MessageCode.WEBSERVICESUCCESS.equals( messageCode ) )
        {
            debug( methodName, "validate Exchange rate  is false" );
            return messageCode;
        }

        //Validate Settlement Currency
        messageCode = validateSettlementCurrency( fxSingleLeg
            .getNonDeliverableSettlement() );

        if( !Constant.MessageCode.WEBSERVICESUCCESS.equals( messageCode ) )
        {
            debug( methodName, "validate Settlement Currency  is false" );
            return messageCode;
        }

        //Validate Fixing Date
        List<FxFixing> fxFixings = fxSingleLeg.getNonDeliverableSettlement()
            .getFixing();
        debug( methodName, "fxFixings size:" + fxFixings.size() );
        messageCode = validateFixingDate( fxFixings );
        if( !Constant.MessageCode.WEBSERVICESUCCESS.equals( messageCode ) )
        {
            debug( methodName, "validate Fixing Date is false" );
            return messageCode;
        }
        //Validate Party information
        return validatePartyInformation( partys );
    }

    /**
     * process of AddNewTrade
     * 
     * @param request
     * @param clientId
     * @param systemName
     * @param hashToken
     * @return messageCode
     * @throws ManagerException e
     * @Author: hypan
     */
    public String processAddNewTrade( RequestClearing request, String clientId,
        String systemName, HashToken hashToken ) throws ManagerException
    {
        final String methodName = "processAddNewTrade";
        debug( methodName, "param[request]:" + request + ",param[clientId]:"
            + clientId + ",param[systemName]:" + systemName
            + ",param[hashToken]:" + hashToken );
        TransactionNewSpool transactionNewSpool = createTransactionNewSpool(
            request, clientId, systemName );
        //Insert entry into the transaction_new_spool table
        try
        {
            transactionNewSpoolDao.save( transactionNewSpool );
        }
        catch ( FXFWSDaoException e )
        {
            final String msg = "Error when Save TransactionNewSpool";
            error( methodName, msg, e );
            alert( e.getErrorCode(), msg );
            throw new ManagerException( e.getErrorCode(), e.sqlCode, e );
        }
        //if save success set hashToken for success response
        hashToken.setReference( transactionNewSpool.getPushReference() );
        hashToken.setValue( transactionNewSpool.getPushHashToken() );
        debug( methodName, "hashToken:" + hashToken.getValue() + ",refrence:"
            + hashToken.getReference() );
        return Constant.MessageCode.WEBSERVICESUCCESS;
    }

    /**
     * validate IsCorrection
     * 
     * @param isCorrection
     * @return String return message code
     * @Author: hypan
     */
    public String validateIsCorrection( boolean isCorrection )
    {
        final String methodName = "validateIsCorrection";
        debug( methodName, "param[isCorrection]:" + isCorrection );
        //If value is not false return error code
        if( isCorrection )
        {

            return Constant.MessageCode.CORRECTIONNOTSUPPORTED;
        }
        return Constant.MessageCode.WEBSERVICESUCCESS;
    }

    /**
     * validate CorrelationId
     * 
     * @param correlationId
     * @param clientId
     * @return String message code
     * @throws ManagerException e
     * @Author: hypan
     */
    public String validateCorrelationId( CorrelationId correlationId,
        String clientId ) throws ManagerException
    {
        final String methodName = "validateCorrelationId";
        debug( methodName, "param[CorrelationIdScheme]:"
            + correlationId.getCorrelationIdScheme() + ",param[clientId]:"
            + clientId );
        String correlationIdScheme = context
            .getProperty( FpMLConstant.FpMLScheme.CORRELATIONIDSCHEME )
            + clientId;
        //Validate correlationIdScheme based on the clientId
        if( !correlationIdScheme
            .equals( correlationId.getCorrelationIdScheme() ) )
        {
            debug( methodName, "validate correlationIdScheme is false " );
            return Constant.MessageCode.INVALIDCORRELATIONIDSCHEME;
        }

        //Value of correlationId must not be blank
        if( Util.isEmpty( correlationId.getValue() ) )
        {
            return Constant.MessageCode.MISSINGDEALID;

        }
        //if value is already in transaction_new_spool or transaction_new_status tables

        try
        {
            if( transactionNewSpoolDao.findByDealIdAndScheme( correlationId
                .getValue(), correlationId.getCorrelationIdScheme() ) != null
                || transactionNewStatusDao.findByDealIdAndScheme( correlationId
                    .getValue(), correlationId.getCorrelationIdScheme() ) != null )
            {
                debug( methodName, "duplicate correlationId " );
                return Constant.MessageCode.DUPLICATEDEALID;
            }

        }
        catch ( FXFWSDaoException e )
        {
            final String message = "Error when vlidateCorrelationId";
            error( methodName, message, e );
            throw new ManagerException( e.getErrorCode(), e.sqlCode, e );
        }

        return Constant.MessageCode.WEBSERVICESUCCESS;

    }

    /**
     * validate SequenceNumber
     * 
     * @param sequenceNumber
     * @return String messageCode
     * @Author: hypan
     */
    public String validateSequenceNumber( BigInteger sequenceNumber )
    {
        final String methodName = "validateSequenceNumber";
        debug( methodName, "param[sequenceNumber]:" + sequenceNumber );
        //This sequenceNumber not be 1 ,return error code
        if( !FpMLConstant.AddNewFXFTrade.SEQUENCENUMBER.equals( sequenceNumber ) )
        {
            return Constant.MessageCode.SEQUENCINGNOTSUPPORTED;
        }

        return Constant.MessageCode.WEBSERVICESUCCESS;
    }

    /**
     * validate PartyTradeIdentifier And TradeId
     * 
     * @param partyTradeIdentifier
     * @param partys contains PartyReference of
     *            partyTradeIdentifier,exchangedCurrency1,exchangedCurrency2
     * @return String messageCode
     * @Author: hypan
     */
    public String validatePartyTradeIdentifierAndTradeId(
        List<PartyTradeIdentifier> partyTradeIdentifier, Set<Party> partys )
    {
        final String methodName = "validatePartyTradeIdentifierAndTradeId";
        debug( methodName, "param[partyTradeIdentifier size]:"
            + partyTradeIdentifier.size() + ",param[partys.size]:"
            + partys.size() );
        List<Party> hrefList = new ArrayList<Party>();
        String partyReferenceCode = Constant.MessageCode.WEBSERVICESUCCESS;
        String tradeIdMessageCode = Constant.MessageCode.WEBSERVICESUCCESS;
        //Party reference (href) must be present,if not return error
        for ( PartyTradeIdentifier ptIdentifier : partyTradeIdentifier )
        {

            partyReferenceCode = validatePartyReference( ptIdentifier
                .getPartyReference(), partys );
            if( !Constant.MessageCode.WEBSERVICESUCCESS
                .equals( partyReferenceCode ) )
            {
                debug( methodName, "Party reference (href) does not present " );
                return partyReferenceCode;
            }
            //validate tradeId
            tradeIdMessageCode = validateTradeId( ptIdentifier
                .getTradeIdOrVersionedTradeId() );

            if( !Constant.MessageCode.WEBSERVICESUCCESS
                .equals( tradeIdMessageCode ) )
            {
                debug( methodName, "validate tradeId is false " );
                return tradeIdMessageCode;
            }

            hrefList.add( ( Party ) ptIdentifier.getPartyReference().getHref() );
        }

        //must not be any duplicated href,if not return error
        if( new HashSet<Party>( hrefList ).size() < hrefList.size() )
        {
            debug( methodName, "duplicated href in PartyTradeIdentifier" );
            return Constant.MessageCode.INVALIDFPMLMESSAGE;
        }
        return Constant.MessageCode.WEBSERVICESUCCESS;
    }

    /**
     * validate TradeId
     * 
     * @param tradeId
     * @return String message Code
     * @Author: hypan
     */
    public String validateTradeId( List<Object> tradeIds )
    {
        final String methodName = "validateTradeId";
        debug( methodName, "param[tradeIds size]:" + tradeIds.size() );
        //missing/multiple tradeIds
        if( tradeIds.size() != 1
            || Util.isEmpty( ( TradeId ) tradeIds.get( 0 ) )
            || Util.isEmpty( ( ( TradeId ) tradeIds.get( 0 ) ).getValue() ) )
        {
            debug( methodName, "missing or multiple tradeIds" );
            return Constant.MessageCode.MISSINGORMULTIPLETRADEIDS;
        }

        return Constant.MessageCode.WEBSERVICESUCCESS;
    }

    /**
     * validate TradeDate
     * 
     * @param tradeDate
     * @return String message code
     * @Author: hypan
     */
    public String validateTradeDate( IdentifiedDate tradeDate )
    {
        final String methodName = "validateTradeDate";
        debug( methodName, "param[tradeDate]:" + tradeDate );
        //This date not present
        if( Util.isEmpty( tradeDate ) || Util.isEmpty( tradeDate.getValue() ) )
        {
            return Constant.MessageCode.INVALIDTRADEDATE;
        }
        debug( methodName, "tradeDate value:" + tradeDate.getValue() );
        return Constant.MessageCode.WEBSERVICESUCCESS;

    }

    /**
     * validate ProductType
     * 
     * @param productTypes
     * @return String message code
     * @Author: hypan
     */
    public String validateProductType( List<ProductType> productTypes )
    {
        final String methodName = "validateProductType";
        debug( methodName, "param[productTypes size]:" + productTypes.size() );

        //There must be only 1 productType field
        if( productTypes.size() != 1 || Util.isEmpty( productTypes.get( 0 ) ) )
        {
            debug( methodName, "productType missing or duplicate" );
            return Constant.MessageCode.INVALIDORDUPLICATEPRODUCTTYPE;
        }

        //Validate productTypeScheme not be http://www.fpml.org/coding-scheme/product-type-simple
        if( !context.getProperty( FpMLConstant.FpMLScheme.PRODUCTTYPESCHEME )
            .equals( productTypes.get( 0 ).getProductTypeScheme() ) )
        {
            debug( methodName, "productTypeScheme does not :"
                + context
                    .getProperty( FpMLConstant.FpMLScheme.PRODUCTTYPESCHEME ) );
            return Constant.MessageCode.INVALIDPRODUCTTYPESCHEME;
        }

        //Value not be 'FxForward' return error code
        if( !FpMLConstant.AddNewFXFTrade.PRODUCTTYPE.equals( productTypes.get(
            0 ).getValue() ) )
        {
            debug( methodName, "productType value does not :"
                + FpMLConstant.AddNewFXFTrade.PRODUCTTYPE );
            return Constant.MessageCode.UNSUPPORTEDPRODUCTORASSET;
        }

        return Constant.MessageCode.WEBSERVICESUCCESS;
    }

    /**
     * validate PartyReference
     * 
     * @param partyReference
     * @param partys
     * @return String messageCode
     * @Author: hypan
     */
    public String validatePartyReference( PartyReference partyReference,
        Set<Party> partys )
    {
        final String methodName = "validatePartyReference";
        debug( methodName, "param[partyReference]:" + partyReference
            + ",param[partys]:" + partys );
        //partyReference (href) not  present
        if( Util.isEmpty( partyReference.getHref() )
            || ( !( ( partyReference.getHref() ) instanceof Party ) ) )
        {
            return Constant.MessageCode.INVALIDFPMLMESSAGE;
        }
        partys.add( ( Party ) partyReference.getHref() );
        return Constant.MessageCode.WEBSERVICESUCCESS;
    }

    /**
     * validate Currency for Currency1
     * 
     * @param currency
     * @return String message code
     * @Author: hypan
     */
    public String validateCurrencyforCurrency1( Currency currency )
    {
        final String methodName = "validateCurrencyforCurrency1";
        debug( methodName, "param[currency.value]:" + currency.getValue() );
        //if currency value not present
        if( Util.isEmpty( currency.getValue() ) )
        {

            return Constant.MessageCode.INVALIDCURRENCYFOREXCHANGEDCURRENCY1;
        }

        return Constant.MessageCode.WEBSERVICESUCCESS;
    }

    /**
     * validate Amount for Currency1
     * 
     * @param amount
     * @return String message code
     * @Author: hypan
     */
    public String validateAmountforCurrency1( BigDecimal amount )
    {
        final String methodName = "validateAmountforCurrency1";
        debug( methodName, "param[amount]:" + amount );
        // if amount is negative
        if( amount.compareTo( BigDecimal.ZERO ) == -1 )
        {
            return Constant.MessageCode.INVALIDAMOUNTFOREXCHANGEDCURRENCY1;
        }
        return Constant.MessageCode.WEBSERVICESUCCESS;

    }

    /**
     * validate Currency for Currency2
     * 
     * @param currency
     * @return String message code
     * @Author: hypan
     */
    public String validateCurrencyforCurrency2( Currency currency )
    {
        final String methodName = "validateCurrencyforCurrency2";
        debug( methodName, "param[currency.value]:" + currency.getValue() );
        //if currency value not present
        if( Util.isEmpty( currency.getValue() ) )
        {

            return Constant.MessageCode.INVALIDCURRENCYFOREXCHANGEDCURRENCY2;
        }

        return Constant.MessageCode.WEBSERVICESUCCESS;
    }

    /**
     * validate Amount for Currency2
     * 
     * @param amount
     * @return String message code
     * @Author: hypan
     */
    public String validateAmountforCurrency2( BigDecimal amount )
    {
        final String methodName = "validateAmountforCurrency2";
        debug( methodName, "param[amount]:" + amount );
        // if amount is negative
        if( amount.compareTo( BigDecimal.ZERO ) == -1 )
        {
            return Constant.MessageCode.INVALIDAMOUNTFOREXCHANGEDCURRENCY2;
        }
        return Constant.MessageCode.WEBSERVICESUCCESS;

    }

    /**
     * validate Settlement Date
     * 
     * @param settlementDate
     * @return String message code
     * @Author: hypan
     */
    public String validateSettlementDate( XMLGregorianCalendar settlementDate )
    {
        final String methodName = "validateSettlementDate";
        debug( methodName, "param[settlementDate]:" + settlementDate );
        //SettlementDate not present 
        if( Util.isEmpty( settlementDate ) )
        {
            return Constant.MessageCode.INVALIDVALUEDATE;
        }
        return Constant.MessageCode.WEBSERVICESUCCESS;
    }

    /**
     * validate QuotedBasis
     * 
     * @param quotedBasis
     * @return String message code
     * @Author: hypan
     */
    public String validateQuotedBasis( QuoteBasisEnum quotedBasis )
    {
        final String methodName = "validateQuotedBasis";
        debug( methodName, "param[quotedBasis]:" + quotedBasis );
        //Value not be Currency2perCurrency1 or Currency1perCurrency2
        if( Util.isEmpty( quotedBasis )
            || Util.isEmpty( QuoteBasisEnum.fromValue( quotedBasis.value() ) ) )
        {
            return Constant.MessageCode.INVALIDQUOTEDBASIS;
        }

        return Constant.MessageCode.WEBSERVICESUCCESS;
    }

    /**
     * validate Currency1
     * 
     * @param currency1
     * @param fxSingleLeg
     * @return String message code
     * @Author: hypan
     */
    public String validateCurrency1( Currency currency1, FxSingleLeg fxSingleLeg )
    {
        final String methodName = "validateCurrency1";
        debug( methodName, "param[currency1]:" + currency1
            + ",param[fxSingleLeg]:" + fxSingleLeg );
        //currency1 value not present
        if( Util.isEmpty( currency1.getValue() ) )
        {
            return Constant.MessageCode.INVALIDCURRENCYFORCURRENCY1;
        }
        //currency1  value not the same as the currency specified under exchangedCurrency1 or exchangedCurrency2
        String exchangedCurrency1 = fxSingleLeg.getExchangedCurrency1()
            .getPaymentAmount().getCurrency().getValue();
        String exchangedCurrency2 = fxSingleLeg.getExchangedCurrency2()
            .getPaymentAmount().getCurrency().getValue();
        debug( methodName, "exchangedCurrency1 currency:" + exchangedCurrency1
            + ",exchangedCurrency2 currency:" + exchangedCurrency2 );

        if( !( currency1.getValue().equals( exchangedCurrency1 ) || currency1
            .getValue().equals( exchangedCurrency2 ) ) )
        {
            return Constant.MessageCode.EXCHANGERATEFORCURRENCY1NOTFOUND;
        }

        return Constant.MessageCode.WEBSERVICESUCCESS;
    }

    /**
     * validate Currency2
     * 
     * @param currency2
     * @param currency1
     * @param fxSingleLeg
     * @return String message
     * @Author: hypan
     */
    public String validateCurrency2( Currency currency2, Currency currency1,
        FxSingleLeg fxSingleLeg )
    {
        final String methodName = "validateCurrency2";
        debug( methodName, "param[currency2]:" + currency2
            + ",param[currency1]:" + currency1 + ",param[fxSingleLeg]:"
            + fxSingleLeg );
        //currency2 value not present
        if( Util.isEmpty( currency2.getValue() ) )
        {
            debug( methodName, "currency2 value does not present" );
            return Constant.MessageCode.INVALIDCURRENCYFORCURRENCY2;
        }

        String exchangedCurrency1 = fxSingleLeg.getExchangedCurrency1()
            .getPaymentAmount().getCurrency().getValue();
        String exchangedCurrency2 = fxSingleLeg.getExchangedCurrency2()
            .getPaymentAmount().getCurrency().getValue();
        debug( methodName, "exchangedCurrency1 currency:" + exchangedCurrency1
            + ",exchangedCurrency2 currency:" + exchangedCurrency2
            + ",currency1:" + currency1.getValue() + ",currency2:"
            + currency2.getValue() );

        //if not( currency1 == exchangedCurrency1 && currency2 == exchangedCurrency2 or currency2 == exchangedCurrency1 && currency1 == exchangedCurrency2)
        if( !( ( currency1.getValue().equals( exchangedCurrency1 ) && currency2
            .getValue().equals( exchangedCurrency2 ) ) || ( currency2
            .getValue().equals( exchangedCurrency1 ) && currency1.getValue()
            .equals( exchangedCurrency2 ) ) ) )

        {
            debug( methodName, "currency2 value is not right" );
            return Constant.MessageCode.EXCHANGERATEFORCURRENCY2NOTFOUND;
        }

        return Constant.MessageCode.WEBSERVICESUCCESS;
    }

    /**
     * validate Rate
     * 
     * @param rate
     * @return String message code
     * @Author: hypan
     */
    public String validateRate( BigDecimal rate )
    {
        final String methodName = "validateRate";
        debug( methodName, "param[rate]:" + rate );
        //if rate <= 0
        if( !( rate.compareTo( BigDecimal.ZERO ) == 1 ) )
        {
            return Constant.MessageCode.INVALIDEXCHANGERATE;
        }
        return Constant.MessageCode.WEBSERVICESUCCESS;

    }

    /**
     * validateSettlementCurrency
     * 
     * @param nonDeliverableSettlement
     * @return messageCode
     * @Author: hypan
     */
    public String validateSettlementCurrency(
        FxCashSettlement nonDeliverableSettlement )
    {
        final String methodName = "validateSettlementCurrency";
        debug( methodName, "param[nonDeliverableSettlement]:"
            + nonDeliverableSettlement );
        //If blank/absent
        if( Util.isEmpty( nonDeliverableSettlement )
            || Util.isEmpty( nonDeliverableSettlement.getSettlementCurrency()
                .getValue() ) )
        {
            return Constant.MessageCode.MISSINGSETTLEMENTCURRENCY;
        }
        return Constant.MessageCode.WEBSERVICESUCCESS;
    }

    /**
     * validate Fixing Date
     * 
     * @param fxFixings
     * @return String message code
     * @Author: hypan
     */
    public String validateFixingDate( List<FxFixing> fxFixings )
    {
        final String methodName = "validateFixingDate";
        debug( methodName, "param[fxFixings size]:" + fxFixings.size() );
        if( fxFixings.size() != 1 || Util.isEmpty( fxFixings.get( 0 ) )
            || Util.isEmpty( fxFixings.get( 0 ).getFixingDate() ) )
        {
            debug( methodName, "fixingDate is blank or absent" );
            return Constant.MessageCode.MISSINGORDUPLICATEFIXINGDATE;
        }

        return Constant.MessageCode.WEBSERVICESUCCESS;
    }

    /**
     * validatePartyInformation
     * 
     * @param partys
     * @return String message code
     * @Author: hypan
     */
    public String validatePartyInformation( Set<Party> partys )
    {
        final String methodName = "validatePartyInformation";
        debug( methodName, "param[partys size]:" + partys.size() );
        //globe parameter contains partyType value of a party
        List<String> partyType = new ArrayList<String>();
        List<PartyId> partyIds = null;
        int partyIdBICNum;//BIC Code num of partyId List
        int partyIdTypeNum;//PartyType num of partyId List
        int partyIdAccountNum;//Account of partyId List
        String[] partyTypes = FpMLConstant.AddNewFXFTrade.PARTYTYPE;//get partyType arrays
        debug( methodName, "partyType of arrays:" + partyTypes );
        for ( Party party : partys )
        {

            //validate partyName,If blank/absent
            if( Util.isEmpty( party.getPartyName() )
                || Util.isEmpty( party.getPartyName().getValue() ) )
            {
                return Constant.MessageCode.MISSINGPARTYNAME;
            }
            //initial count for partyIdBICNum,partyIdTypeNum,partyIdAccountNum
            partyIdBICNum = 0;
            partyIdTypeNum = 0;
            partyIdAccountNum = 0;
            partyIds = party.getPartyId();
            debug( methodName, "party:" + party.getPartyName()
                + ",partyId list size:" + partyIds.size() );
            for ( PartyId partyId : partyIds )
            {
                debug( methodName, "PartyIdScheme:"
                    + partyId.getPartyIdScheme() + ",value:"
                    + partyId.getValue() );
                //if BIC Code
                if( context.getProperty(
                    FpMLConstant.FpMLScheme.PARTYIDSCHEMEFORBIC ).equals(
                    partyId.getPartyIdScheme() ) )
                {
                    //if partyId value is null
                    if( Util.isEmpty( partyId.getValue() ) )
                    {
                        debug( methodName, "partyId BIC Code is blank" );
                        return Constant.MessageCode.MISSINGORMULTIPLEBIC;
                    }

                    partyIdBICNum++;

                }//if PartyType 
                else if( context.getProperty(
                    FpMLConstant.FpMLScheme.PARTYIDSCHEMEFORPARTYTYPE ).equals(
                    partyId.getPartyIdScheme() ) )
                {

                    //The party type value not be Party, CounterParty or Matcher
                    if( !partyTypes[0].equals( partyId.getValue() )
                        && !partyTypes[1].equals( partyId.getValue() )
                        && !partyTypes[2].equals( partyId.getValue() ) )
                    {
                        debug(
                            methodName,
                            partyId.getValue()
                                + ":party type value not be Party, CounterParty or Matcher" );
                        return Constant.MessageCode.INVALIDPARTYTYPE;
                    }
                    partyIdTypeNum++;
                    partyType.add( partyId.getValue() );

                }
                else if( context.getProperty(
                    FpMLConstant.FpMLScheme.PARTYIDSCHEMEFORACCOUNT ).equals(
                    partyId.getPartyIdScheme() ) )
                {
                    partyIdAccountNum++;
                }

            }
            debug( methodName, "partyIdBICNum:" + partyIdBICNum
                + ",partyIdBICNum:" + partyIdBICNum + ",partyIdTypeNum:"
                + partyIdTypeNum );

            //if missing BIC
            if( partyIdBICNum < 1 )
            {
                debug( methodName, "Missing BIC partyIdScheme" );
                return Constant.MessageCode.MISSINGBICPARTYIDSCHEME;
            }//if multiple BIC 
            else if( partyIdBICNum > 1 )
            {
                debug( methodName, "Has too many BIC partyIdScheme" );
                return Constant.MessageCode.MISSINGORMULTIPLEBIC;
            }

            //if missing or Duplicate PartyType
            if( partyIdTypeNum != 1 )
            {
                debug( methodName, "Duplicate PartyType of PartyId" );
                return Constant.MessageCode.DUPLICATEPARTYTYPE;
            }

            //if multiple accounts in a party
            if( partyIdAccountNum > 1 )
            {
                debug( methodName, "Duplicate Account of PartyId" );
                return Constant.MessageCode.DUPLICATEACCOUNTS;
            }

        }
        //if partyType is missing Party,CounterParty
        if( !partyType.contains( partyTypes[0] )
            || !partyType.contains( partyTypes[1] ) )
        {
            debug( methodName,
                "Missing Party,CounterParty of PartyType in Party" );
            return Constant.MessageCode.INVALIDPARTYTYPE;
        }
        //if partyType, since they are optional in party
        if( partyType.size() > ( new HashSet<String>( partyType ) ).size() )
        {
            debug( methodName, "Duplicate PartyType of Party" );
            return Constant.MessageCode.DUPLICATEPARTYTYPE;
        }

        return Constant.MessageCode.WEBSERVICESUCCESS;

    }

    /**
     * get PartyIds From partyType
     * 
     * @param partyAndTradeId
     * @return Map of partyId type and value
     * @Author:hypan
     */
    private Map<String, String> getPartyIdsFromType(
        Map<Party, TradeId> partyAndTradeId )
    {
        final String methodName = "getPartyIdsFromType";
        debug( methodName, "param[partyAndTradeId size]:"
            + partyAndTradeId.size() );

        Map<String, String> partyIdMap = new HashMap<String, String>();
        String partyType = null;
        for ( Party party : partyAndTradeId.keySet() )
        {
            debug( methodName, "partyId:" + party.getId() );
            for ( PartyId partyId : party.getPartyId() )
            {
                debug( methodName, "partyId:" + partyId.getPartyIdScheme() );
                //if current partyId is partyType
                if( context.getProperty(
                    FpMLConstant.FpMLScheme.PARTYIDSCHEMEFORPARTYTYPE ).equals(
                    partyId.getPartyIdScheme() ) )
                {
                    debug( methodName, "partyId value:" + partyId.getValue() );
                    //partyType=Party
                    if( FpMLConstant.AddNewFXFTrade.PARTYTYPE[0]
                        .equals( partyId.getValue() ) )
                    {
                        partyType = FpMLConstant.AddNewFXFTrade.PARTYTYPE[0];
                        break;
                    }
                    //partyType=CounterParty
                    else if( FpMLConstant.AddNewFXFTrade.PARTYTYPE[1]
                        .equals( partyId.getValue() ) )
                    {
                        partyType = FpMLConstant.AddNewFXFTrade.PARTYTYPE[1];
                        break;
                    }
                    //partyType=Matcher
                    else if( FpMLConstant.AddNewFXFTrade.PARTYTYPE[2]
                        .equals( partyId.getValue() ) )
                    {
                        partyType = FpMLConstant.AddNewFXFTrade.PARTYTYPE[2];
                        break;
                    }
                }
            }
            if( !Util.isEmpty( partyType ) )
            {
                debug( methodName, "partyType:" + partyType );
                setMap( partyIdMap, party, partyAndTradeId.get( party ),
                    partyType );
                partyType = null;
            }

        }

        return partyIdMap;
    }

    /**
     * Set partyId partyType and value to Map
     * 
     * @param partyIdMap Map of PartyId partyType and value
     * @param party
     * @param tradeId
     * @param partyType
     * @Author: hypan
     */
    private void setMap( Map<String, String> partyIdMap, Party party,
        TradeId tradeId, String partyType )
    {

        final String methodName = "setMap";
        debug( methodName, "param[party Id]:" + party.getId()
            + ",param[tradeId]:" + tradeId.getId() + ",param[partyType]:"
            + partyType );

        partyIdMap.put( partyType + FpMLConstant.AddNewFXFTrade.CONNECTOR
            + FpMLConstant.AddNewFXFTrade.TRADE_ID, tradeId.getValue() );//set PARTY_TRADE_ID
        partyIdMap.put( partyType + FpMLConstant.AddNewFXFTrade.CONNECTOR
            + FpMLConstant.AddNewFXFTrade.NAME, party.getPartyName()//set PARTY_NAME
            .getValue() );
        for ( PartyId pd : party.getPartyId() )
        {
            //if BIC partyId
            if( context.getProperty(
                FpMLConstant.FpMLScheme.PARTYIDSCHEMEFORBIC ).equals(
                pd.getPartyIdScheme() ) )
            {
                partyIdMap.put( partyType
                    + FpMLConstant.AddNewFXFTrade.CONNECTOR
                    + FpMLConstant.AddNewFXFTrade.BIC, pd.getValue() );//set PARTY_BIC
            }
            //if Account partyId
            else if( context.getProperty(
                FpMLConstant.FpMLScheme.PARTYIDSCHEMEFORACCOUNT ).equals(
                pd.getPartyIdScheme() ) )
            {
                partyIdMap.put( partyType
                    + FpMLConstant.AddNewFXFTrade.CONNECTOR
                    + FpMLConstant.AddNewFXFTrade.ACCOUNT, pd.getValue() );//set PARTY_ACCOUNT
            }
        }

    }

    /**
     * Get BIC code from party
     * 
     * @param party
     * @return BIC code
     * @Author: hypan
     */
    private String getBIC( Party party )
    {
        for ( PartyId partyId : party.getPartyId() )
        {
            if( context.getProperty(
                FpMLConstant.FpMLScheme.PARTYIDSCHEMEFORBIC ).equals(
                partyId.getPartyIdScheme() ) )
            {
                return partyId.getValue();
            }
        }
        return null;
    }

    /**
     * set BaseCurrency Or ReferenceCurrency
     * 
     * @param baseCurrency
     * @param referenceCurrency
     * @param transactionNewSpool
     * @Author: hypan
     */
    private void setBaseOrReferenceCurrency( Payment baseCurrency,
        Payment referenceCurrency, TransactionNewSpool transactionNewSpool )
    {
        String methodName = "setBaseOrReferenceCurrency";
        debug( methodName, "baseCurrency:" + baseCurrency
            + ",referenceCurrency:" + referenceCurrency
            + ",param[transactionNewSpool]:" + transactionNewSpool );
        //BASE_CURRENCY_SELLER
        transactionNewSpool
            .setBaseCurrencySeller( getBIC( ( Party ) baseCurrency
                .getPayerPartyReference().getHref() ) );
        //BASE _CURRENCY_BUYER
        transactionNewSpool
            .setBaseCurrencyBuyer( getBIC( ( Party ) baseCurrency
                .getReceiverPartyReference().getHref() ) );
        //BASE _CURRENCY
        transactionNewSpool.setBaseCurrency( baseCurrency.getPaymentAmount()
            .getCurrency().getValue() );
        //BASE _CURRENCY_AMOUNT
        transactionNewSpool.setBaseCurrencyAmount( baseCurrency
            .getPaymentAmount().getAmount().toString() );

        //REFERENCE_CURRENCY_SELLER
        transactionNewSpool
            .setReferenceCurrencySeller( getBIC( ( Party ) referenceCurrency
                .getPayerPartyReference().getHref() ) );
        //REFERENCE _CURRENCY_BUYER
        transactionNewSpool
            .setReferenceCurrencyBuyer( getBIC( ( Party ) referenceCurrency
                .getReceiverPartyReference().getHref() ) );
        //REFERENCE _CURRENCY
        transactionNewSpool.setReferenceCurrency( referenceCurrency
            .getPaymentAmount().getCurrency().getValue() );
        //REFERENCE _CURRENCY_AMOUNT
        transactionNewSpool.setReferenceCurrencyAmount( referenceCurrency
            .getPaymentAmount().getAmount().toString() );
    }

    /**
     * create TransactionNewSpool bean
     * 
     * @param request
     * @param clientId
     * @param systemName
     * @return TransactionNewSpool
     * @Author: hypan
     */
    private TransactionNewSpool createTransactionNewSpool(
        RequestClearing request, String clientId, String systemName )
    {
        String methodName = "createTransactionNewSpool";
        debug( methodName, "param[RequestClearing]:" + request
            + ",param[clientId]:" + clientId + ",param[systemName]:"
            + systemName );
        TransactionNewSpool transactionNewSpool = new TransactionNewSpool();
        transactionNewSpool.setTransId( request.getHeader().getMessageId()
            .getValue() );//messageId.value
        transactionNewSpool.setDealIdScheme( request.getCorrelationId()
            .getCorrelationIdScheme() );//correlationId.correlationIdSchema
        transactionNewSpool.setDealId( request.getCorrelationId().getValue() );
        //transactionNewSpool.setPartyBic( partyBic )
        //Map contains Party and TradeId  of PartyTradeIdentifier
        Map<Party, TradeId> partyAndTradeId = new HashMap<Party, TradeId>();
        List<PartyTradeIdentifier> partyTradeIdentifier = request.getTrade()
            .getTradeHeader().getPartyTradeIdentifier();
        for ( PartyTradeIdentifier pt : partyTradeIdentifier )
        {
            partyAndTradeId.put( ( Party ) pt.getPartyReference().getHref(),
                ( TradeId ) pt.getTradeIdOrVersionedTradeId().get( 0 ) );

        }
        //get PartyId partyType and value
        Map<String, String> partyIdMap = getPartyIdsFromType( partyAndTradeId );
        debug( methodName, "partyIdMap size:" + partyIdMap.size() );
        //PARTY
        transactionNewSpool.setPartyBic( partyIdMap
            .get( FpMLConstant.AddNewFXFTrade.PARTYTYPE[0]
                + FpMLConstant.AddNewFXFTrade.CONNECTOR
                + FpMLConstant.AddNewFXFTrade.BIC ) );
        transactionNewSpool.setPartyAccount( partyIdMap
            .get( FpMLConstant.AddNewFXFTrade.PARTYTYPE[0]
                + FpMLConstant.AddNewFXFTrade.CONNECTOR
                + FpMLConstant.AddNewFXFTrade.ACCOUNT ) );
        transactionNewSpool.setPartyName( partyIdMap
            .get( FpMLConstant.AddNewFXFTrade.PARTYTYPE[0]
                + FpMLConstant.AddNewFXFTrade.CONNECTOR
                + FpMLConstant.AddNewFXFTrade.NAME ) );
        transactionNewSpool.setPartyTradeId( partyIdMap
            .get( FpMLConstant.AddNewFXFTrade.PARTYTYPE[0]
                + FpMLConstant.AddNewFXFTrade.CONNECTOR
                + FpMLConstant.AddNewFXFTrade.TRADE_ID ) );
        //COUNTERPARTY
        transactionNewSpool.setCounterpartyBic( partyIdMap
            .get( FpMLConstant.AddNewFXFTrade.PARTYTYPE[1]
                + FpMLConstant.AddNewFXFTrade.CONNECTOR
                + FpMLConstant.AddNewFXFTrade.BIC ) );
        transactionNewSpool.setCounterpartyAccount( partyIdMap
            .get( FpMLConstant.AddNewFXFTrade.PARTYTYPE[1]
                + FpMLConstant.AddNewFXFTrade.CONNECTOR
                + FpMLConstant.AddNewFXFTrade.ACCOUNT ) );
        transactionNewSpool.setCounterpartyName( partyIdMap
            .get( FpMLConstant.AddNewFXFTrade.PARTYTYPE[1]
                + FpMLConstant.AddNewFXFTrade.CONNECTOR
                + FpMLConstant.AddNewFXFTrade.NAME ) );
        transactionNewSpool.setCounterpartyTradeId( partyIdMap
            .get( FpMLConstant.AddNewFXFTrade.PARTYTYPE[1]
                + FpMLConstant.AddNewFXFTrade.CONNECTOR
                + FpMLConstant.AddNewFXFTrade.TRADE_ID ) );
        //MATCHER
        transactionNewSpool.setMatcherBic( partyIdMap
            .get( FpMLConstant.AddNewFXFTrade.PARTYTYPE[2]
                + FpMLConstant.AddNewFXFTrade.CONNECTOR
                + FpMLConstant.AddNewFXFTrade.BIC ) );
        transactionNewSpool.setMatcherAccount( partyIdMap
            .get( FpMLConstant.AddNewFXFTrade.PARTYTYPE[2]
                + FpMLConstant.AddNewFXFTrade.CONNECTOR
                + FpMLConstant.AddNewFXFTrade.ACCOUNT ) );
        transactionNewSpool.setMatcherName( partyIdMap
            .get( FpMLConstant.AddNewFXFTrade.PARTYTYPE[2]
                + FpMLConstant.AddNewFXFTrade.CONNECTOR
                + FpMLConstant.AddNewFXFTrade.NAME ) );
        transactionNewSpool.setMatcherTradeId( partyIdMap
            .get( FpMLConstant.AddNewFXFTrade.PARTYTYPE[2]
                + FpMLConstant.AddNewFXFTrade.CONNECTOR
                + FpMLConstant.AddNewFXFTrade.TRADE_ID ) );
        transactionNewSpool.setTradeDate( Util
            .convertXMLGregorianCalendarToDate( request.getTrade()
                .getTradeHeader().getTradeDate().getValue() ) );
        //PRODUCT_TYPE
        FxSingleLeg fxSingleLeg = ( FxSingleLeg ) request.getTrade()
            .getProduct().getValue();
        transactionNewSpool.setProductType( fxSingleLeg.getProductType()
            .get( 0 ).getValue() );

        String quoteBasis = fxSingleLeg.getExchangeRate()
            .getQuotedCurrencyPair().getQuoteBasis().value();
        //If quoteBasis is Currency2perCurrency1,
        //The currency specified as Currency2 is the reference currency
        //The currency specified as Currency1 is the base currency
        Payment baseCurrency = null;
        Payment referenceCurrency = null;
        //If quoteBasis is Currency2perCurrency1,
        //The currency specified as Currency2 is the reference currency
        //The currency specified as Currency1 is the base currency
        debug( methodName, "quoteBasis:" + quoteBasis );
        Payment exchangedCurrency1 = fxSingleLeg.getExchangedCurrency1();
        Payment exchangedCurrency2 = fxSingleLeg.getExchangedCurrency2();

        if( quoteBasis
            .equals( QuoteBasisEnum.CURRENCY_2_PER_CURRENCY_1.value() ) )
        {

            debug( methodName,
                "Currency2 is the reference currency,Currency1 is the base currency" );
            if( fxSingleLeg.getExchangeRate().getQuotedCurrencyPair()
                .getCurrency1().getValue().equals(
                    exchangedCurrency1.getPaymentAmount().getCurrency()
                        .getValue() ) )
            {
                baseCurrency = exchangedCurrency1;
                referenceCurrency = exchangedCurrency2;
            }
            else
            {
                baseCurrency = exchangedCurrency2;
                referenceCurrency = exchangedCurrency1;
            }

        }//If quoteBasis is Currency1perCurrency2,
        //The currency specified as Currency1 is the reference currency
        //The currency specified as Currency2 is the base currency
        else
        {
            debug( methodName,
                "Currency1 is the reference currency,Currency2 is the base currency" );
            if( fxSingleLeg.getExchangeRate().getQuotedCurrencyPair()
                .getCurrency1().getValue().equals(
                    exchangedCurrency1.getPaymentAmount().getCurrency()
                        .getValue() ) )
            {
                baseCurrency = exchangedCurrency2;
                referenceCurrency = exchangedCurrency1;
            }
            else
            {
                baseCurrency = exchangedCurrency1;
                referenceCurrency = exchangedCurrency2;
            }

        }
        setBaseOrReferenceCurrency( baseCurrency, referenceCurrency,
            transactionNewSpool );

        //SETTLEMENT_DATE
        transactionNewSpool.setSettlementDate( Util
            .convertXMLGregorianCalendarToDate( fxSingleLeg.getValueDate() ) );
        //FIXING_DATE
        transactionNewSpool.setFixingDate( Util
            .convertXMLGregorianCalendarToDate( fxSingleLeg
                .getNonDeliverableSettlement().getFixing().get( 0 )
                .getFixingDate() ) );
        //SETTLEMENT_CURRENCY
        transactionNewSpool.setSettlementCurrency( fxSingleLeg
            .getNonDeliverableSettlement().getSettlementCurrency().getValue() );
        //FORWARD_RATE
        transactionNewSpool.setForwardRate( fxSingleLeg.getExchangeRate()
            .getRate().toString() );
        //TIMESTAMP
        transactionNewSpool.setTimestamp( Util
            .convertXMLGregorianCalendarToDate( request.getHeader()
                .getCreationTimestamp() ) );
        //DATE_CREATED
        transactionNewSpool.setDateCreated( new Date() );
        //CLIENT_ID
        transactionNewSpool.setClientId( clientId );
        //SYSTEM_NAME
        transactionNewSpool.setSystemName( systemName );
        //get hashToken from abstract manager
        HashToken hashToken = getHashToken();
        debug( methodName, "reference:" + hashToken.getReference()
            + ",hashToken:" + hashToken.getValue() );
        //PUSH_REFERENCE
        transactionNewSpool.setPushReference( hashToken.getReference() );
        //PUSH_HASH_TOKEN
        transactionNewSpool.setPushHashToken( hashToken.getValue() );

        return transactionNewSpool;
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

}
