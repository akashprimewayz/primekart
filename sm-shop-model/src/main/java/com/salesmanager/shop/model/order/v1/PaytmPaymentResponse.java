package com.salesmanager.shop.model.order.v1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.salesmanager.shop.model.order.transaction.PersistablePayment;

/**
 * This object is used when processing an order from the API
 * It will be used for processing the payment and as Order meta data
 * @author c.samson
 *
 */
public class PaytmPaymentResponse {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private String ORDERID;
	private String MID;
	private String TXNID;
	private String TXNAMOUNT;
	private String PAYMENTMODE;
	private String CURRENCY;
	private String TXNDATE;
	private String STATUS;
	private String RESPCODE;
	private String RESPMSG;
	private String GATEWAYNAME;
	private String BANKTXNID;
	private String BANKNAME;
	private String CHECKSUMHASH;
	private String storeCode;

	public String getORDERID() {
		return ORDERID;
	}
	public void setORDERID(String oRDERID) {
		ORDERID = oRDERID;
	}
	public String getMID() {
		return MID;
	}
	public void setMID(String mID) {
		MID = mID;
	}
	public String getTXNID() {
		return TXNID;
	}
	public void setTXNID(String tXNID) {
		TXNID = tXNID;
	}
	public String getTXNAMOUNT() {
		return TXNAMOUNT;
	}
	public void setTXNAMOUNT(String tXNAMOUNT) {
		TXNAMOUNT = tXNAMOUNT;
	}
	public String getPAYMENTMODE() {
		return PAYMENTMODE;
	}
	public void setPAYMENTMODE(String pAYMENTMODE) {
		PAYMENTMODE = pAYMENTMODE;
	}
	public String getCURRENCY() {
		return CURRENCY;
	}
	public void setCURRENCY(String cURRENCY) {
		CURRENCY = cURRENCY;
	}
	public String getTXNDATE() {
		return TXNDATE;
	}
	public void setTXNDATE(String tXNDATE) {
		TXNDATE = tXNDATE;
	}
	public String getSTATUS() {
		return STATUS;
	}
	public void setSTATUS(String sTATUS) {
		STATUS = sTATUS;
	}
	public String getRESPCODE() {
		return RESPCODE;
	}
	public void setRESPCODE(String rESPCODE) {
		RESPCODE = rESPCODE;
	}
	public String getRESPMSG() {
		return RESPMSG;
	}
	public void setRESPMSG(String rESPMSG) {
		RESPMSG = rESPMSG;
	}
	public String getGATEWAYNAME() {
		return GATEWAYNAME;
	}
	public void setGATEWAYNAME(String gATEWAYNAME) {
		GATEWAYNAME = gATEWAYNAME;
	}
	public String getBANKTXNID() {
		return BANKTXNID;
	}
	public void setBANKTXNID(String bANKTXNID) {
		BANKTXNID = bANKTXNID;
	}
	public String getBANKNAME() {
		return BANKNAME;
	}
	public void setBANKNAME(String bANKNAME) {
		BANKNAME = bANKNAME;
	}
	public String getCHECKSUMHASH() {
		return CHECKSUMHASH;
	}
	public void setCHECKSUMHASH(String cHECKSUMHASH) {
		CHECKSUMHASH = cHECKSUMHASH;
	}
	public String getStoreCode() {
		return storeCode;
	}
	public void setStoreCode(String storeCode) {
		this.storeCode = storeCode;
	}
	
}
