package org.upyog.sv.util;

import static com.jayway.jsonpath.Criteria.where;
import static com.jayway.jsonpath.Filter.filter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.mdms.model.MasterDetail;
import org.egov.mdms.model.MdmsCriteria;
import org.egov.mdms.model.MdmsCriteriaReq;
import org.egov.mdms.model.ModuleDetail;
import org.egov.tracer.model.CustomException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;
import org.upyog.sv.config.StreetVendingConfiguration;
import org.upyog.sv.constants.StreetVendingConstants;
import org.upyog.sv.kafka.producer.Producer;
import org.upyog.sv.repository.ServiceRequestRepository;
import org.upyog.sv.service.StreetVendingEncryptionService;
import org.upyog.sv.service.impl.StreetyVendingNotificationServiceImpl;
import org.upyog.sv.web.models.StreetVendingDetail;
import org.upyog.sv.web.models.StreetVendingRequest;
import org.upyog.sv.web.models.VendorDetail;
import org.upyog.sv.web.models.events.EventRequest;
import org.upyog.sv.web.models.notification.Email;
import org.upyog.sv.web.models.notification.EmailRequest;
import org.upyog.sv.web.models.notification.SMSRequest;

import com.jayway.jsonpath.Filter;
import com.jayway.jsonpath.JsonPath;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class NotificationUtil {

	private ServiceRequestRepository serviceRequestRepository;

	private StreetVendingConfiguration config;

	private Producer producer;

	private RestTemplate restTemplate;
	
	@Autowired
	private StreetVendingEncryptionService decrypt;

	@Autowired
	public NotificationUtil(ServiceRequestRepository serviceRequestRepository, StreetVendingConfiguration config,
			Producer producer, RestTemplate restTemplate) {
		this.serviceRequestRepository = serviceRequestRepository;
		this.config = config;
		this.producer = producer;
		this.restTemplate = restTemplate;
	}

	/**
	 * Extracts message for the specific code
	 *
	 * @param notificationCode    The code for which message is required
	 * @param localizationMessage The localization messages
	 * @return message for the specific code
	 */
	public String getMessageTemplate(String notificationCode, String localizationMessage) {

		String path = "$..messages[?(@.code==\"{}\")].message";
		path = path.replace("{}", notificationCode);
		String message = "";
		try {
			Object messageObj = JsonPath.parse(localizationMessage).read(path);
			message = ((ArrayList<String>) messageObj).get(0);
		} catch (Exception e) {
			log.warn("Fetching from localization failed", e);
		}
		return message;
	}

	/**
	 * Fetches messages from localization service
	 *
	 * @param tenantId    tenantId of the PTR
	 * @param requestInfo The requestInfo of the request
	 * @return Localization messages for the module
	 */
	public String getLocalizationMessages(String tenantId, RequestInfo requestInfo) {

		String locale = StreetVendingConstants.NOTIFICATION_LOCALE;
		Boolean isRetryNeeded = false;
		String jsonString = null;
		LinkedHashMap responseMap = null;

		if (!StringUtils.isEmpty(requestInfo.getMsgId()) && requestInfo.getMsgId().split("\\|").length >= 2) {
			locale = requestInfo.getMsgId().split("\\|")[1];
			isRetryNeeded = true;
		}

		responseMap = (LinkedHashMap) serviceRequestRepository.fetchResult(getUri(tenantId, requestInfo, locale),
				requestInfo);
		jsonString = new JSONObject(responseMap).toString();

		if (StringUtils.isEmpty(jsonString) && isRetryNeeded) {

			responseMap = (LinkedHashMap) serviceRequestRepository.fetchResult(
					getUri(tenantId, requestInfo, StreetVendingConstants.NOTIFICATION_LOCALE), requestInfo);
			jsonString = new JSONObject(responseMap).toString();
			if (StringUtils.isEmpty(jsonString))
				throw new CustomException("EG_EW_LOCALE_ERROR",
						"Localisation values not found for Ewaste notifications");
		}
		return jsonString;
	}

	/**
	 * Returns the uri for the localization call
	 *
	 * @return The uri for localization search call
	 */
	public StringBuilder getUri(String tenantId, RequestInfo requestInfo, String locale) {

		if (config.getIsLocalizationStateLevel())
			tenantId = tenantId.split("\\.")[0];

		StringBuilder uri = new StringBuilder();
		uri.append(config.getLocalizationHost()).append(config.getLocalizationContextPath())
				.append(config.getLocalizationSearchEndpoint()).append("?").append("locale=").append(locale)
				.append("&tenantId=").append(tenantId).append("&module=")
				.append(StreetVendingConstants.NOTIFICATION_MODULE_NAME);

		return uri;
	}

	/**
	 * Enriches the smsRequest with the customized messages
	 *
	 * @param request
	 *            The request from kafka topic
	 * @param smsRequests
	 *            List of SMSRequets
	 */
	public void enrichSMSRequest(StreetVendingRequest request, List<SMSRequest> smsRequests) {
	    String tenantId = request.getStreetVendingDetail().getTenantId();
	    String localizationMessages = getLocalizationMessages(tenantId, request.getRequestInfo());
	    String messageMap = getCustomizedMsg(request.getRequestInfo(), request.getStreetVendingDetail(), localizationMessages);
	    List<VendorDetail> vendorDetails = request.getStreetVendingDetail().getVendorDetail();
	    String mobileNumber = null;
	    
	    if (vendorDetails != null && !vendorDetails.isEmpty()) {
	        mobileNumber = vendorDetails.get(0).getMobileNo();  
	    }
	    
	    if (mobileNumber != null) {
	        Map<String, String> mobileNumberToOwner = fetchUserUUIDs(mobileNumber, request.getRequestInfo(), tenantId, request.getStreetVendingDetail());
	        smsRequests.addAll(createSMSRequest(messageMap, mobileNumberToOwner));
	    }
	}

	
	/**
	 * Fetches UUIDs of CITIZEN based on the phone number.
	 *
	 * @param mobileNumber - Mobile Numbers
	 * @param requestInfo  - Request Information
	 * @param tenantId     - Tenant Id
	 * @return Returns List of MobileNumbers and UUIDs
	 */
	public Map<String, String> fetchUserUUIDs(String mobileNumber, RequestInfo requestInfo, String tenantId, StreetVendingDetail requestDetail) {
		Map<String, String> mapOfPhoneNoAndUUIDs = new HashMap<>();
		StringBuilder uri = new StringBuilder();
		uri.append(config.getUserHost()).append(config.getUserSearchEndpoint());
	    StreetVendingDetail	decryptedDetail = decrypt.decryptObject(requestDetail, requestInfo);
	    mobileNumber =  decryptedDetail.getVendorDetail().get(0).getMobileNo();
		Map<String, Object> userSearchRequest = new HashMap<>();
		userSearchRequest.put("RequestInfo", requestInfo);
		userSearchRequest.put("tenantId", tenantId);
		userSearchRequest.put("userType", "CITIZEN");
		userSearchRequest.put("userName", mobileNumber);
		try {

			Object user = serviceRequestRepository.fetchResult(uri, userSearchRequest);
			log.info("User fetched in fetUserUUID method of StreetVending notfication consumer" + user.toString());
			if (user != null) {
				String uuid = JsonPath.read(user, "$.user[0].uuid");
				mapOfPhoneNoAndUUIDs.put(mobileNumber, uuid);
				log.info("mapOfPhoneNoAndUUIDs : " + mapOfPhoneNoAndUUIDs);
			}
		} catch (Exception e) {
			log.error("Exception while fetching user for username - " + mobileNumber);
			log.error("Exception trace: ", e);
		}

		return mapOfPhoneNoAndUUIDs;
	}
	


	/**
	 * Creates sms request for the each owners
	 * 
	 * @param mobileNumberToOwnerName Map of mobileNumber to OwnerName
	 * @return List of SMSRequest
	 */
	public List<SMSRequest> createSMSRequest(String message, Map<String, String> mobileNumberToOwnerName) {

		List<SMSRequest> smsRequest = new LinkedList<>();
		for (Map.Entry<String, String> entryset : mobileNumberToOwnerName.entrySet()) {
			String customizedMsg = message.replace(StreetVendingConstants.NOTIFICATION_OWNERNAME, entryset.getValue());
			smsRequest.add(new SMSRequest(entryset.getKey(), customizedMsg));
		}
		return smsRequest;
	}

	/**
	 * Send the SMSRequest on the SMSNotification kafka topic
	 *
	 * @param smsRequestList The list of SMSRequest to be sent
	 */
	public void sendSMS(List<SMSRequest> smsRequestList) {

		if (config.getIsSMSNotificationEnabled()) {
			if (CollectionUtils.isEmpty(smsRequestList))
				log.info("Messages from localization couldn't be fetched!");
			for (SMSRequest smsRequest : smsRequestList) {
				producer.push(config.getSmsNotifTopic(), smsRequest);
				log.info("Sending SMS notification: ");
				log.info("MobileNumber: " + smsRequest.getMobileNumber() + " Messages: " + smsRequest.getMessage());
			}
		}
	}

	/**
	 * Fetches UUIDs of CITIZENs based on the phone number.
	 *
	 * @param mobileNumbers
	 * @param requestInfo
	 * @param tenantId
	 * @return
	 */
	public Map<String, String> fetchUserUUIDs(Set<String> mobileNumbers, RequestInfo requestInfo, String tenantId) {

		Map<String, String> mapOfPhnoAndUUIDs = new HashMap<>();
		StringBuilder uri = new StringBuilder();
		uri.append(config.getUserHost()).append(config.getUserSearchEndpoint());
		Map<String, Object> userSearchRequest = new HashMap<>();
		userSearchRequest.put("RequestInfo", requestInfo);
		userSearchRequest.put("tenantId", tenantId);
		userSearchRequest.put("userType", "CITIZEN");
		for (String mobileNo : mobileNumbers) {
			userSearchRequest.put("userName", mobileNo);
			try {
				Object user = serviceRequestRepository.fetchResult(uri, userSearchRequest);
				if (null != user) {
					String uuid = JsonPath.read(user, "$.user[0].uuid");
					mapOfPhnoAndUUIDs.put(mobileNo, uuid);
				} else {
					log.error("Service returned null while fetching user for username - " + mobileNo);
				}
			} catch (Exception e) {
				log.error("Exception while fetching user for username - " + mobileNo);
				log.error("Exception trace: ", e);
				continue;
			}
		}
		return mapOfPhnoAndUUIDs;
	}

	/**
	 * Pushes the event request to Kafka Queue.
	 *
	 * @param request
	 */
	public void sendEventNotification(EventRequest request) {
		log.info("EVENT notification sent!" + request);
		producer.push(config.getSaveUserEventsTopic(), request);
	}

	/**
	 * Creates email request for the each owners
	 *
	 * @param mobileNumberToEmailId Map of mobileNumber to emailIds
	 * @return List of EmailRequest
	 */

	public List<EmailRequest> createEmailRequest(RequestInfo requestInfo, String message,
			Map<String, String> mobileNumberToEmailId) {

		List<EmailRequest> emailRequest = new LinkedList<>();
		for (Map.Entry<String, String> entryset : mobileNumberToEmailId.entrySet()) {
			String customizedMsg = "";
			if (message.contains(StreetVendingConstants.NOTIFICATION_EMAIL))
				customizedMsg = message.replace(StreetVendingConstants.NOTIFICATION_EMAIL, entryset.getValue());

			String subject = "";
			String body = customizedMsg;
			Email emailobj = Email.builder().emailTo(Collections.singleton(entryset.getValue())).isHTML(false)
					.body(body).subject(subject).build();
			EmailRequest email = new EmailRequest(requestInfo, emailobj);
			emailRequest.add(email);
		}
		return emailRequest;
	}

	/**
	 * Send the EmailRequest on the EmailNotification kafka topic
	 *
	 * @param emailRequestList The list of EmailRequest to be sent
	 */
	public void sendEmail(List<EmailRequest> emailRequestList) {

		if (config.getIsEmailNotificationEnabled()) {
			if (CollectionUtils.isEmpty(emailRequestList))
				log.info("Messages from localization couldn't be fetched!");
			for (EmailRequest emailRequest : emailRequestList) {
				if (!StringUtils.isEmpty(emailRequest.getEmail().getBody())) {
					producer.push(config.getEmailNotifTopic(), emailRequest);
					log.info("Sending EMAIL notification! ");
					log.info("Email Id: " + emailRequest.getEmail().toString());
				} else {
					log.info("Email body is empty, hence no email notification will be sent.");
				}
			}

		}
	}

	/**
	 * Fetches email ids of CITIZENs based on the phone number.
	 *
	 * @param mobileNumbers
	 * @param requestInfo
	 * @param tenantId
	 * @return
	 */

	public Map<String, String> fetchUserEmailIds(Set<String> mobileNumbers, RequestInfo requestInfo, String tenantId) {
		Map<String, String> mapOfPhnoAndEmailIds = new HashMap<>();
		StringBuilder uri = new StringBuilder();
		uri.append(config.getUserHost()).append(config.getUserSearchEndpoint());
		Map<String, Object> userSearchRequest = new HashMap<>();
		userSearchRequest.put("RequestInfo", requestInfo);
		userSearchRequest.put("tenantId", tenantId);
		userSearchRequest.put("userType", "CITIZEN");
		for (String mobileNo : mobileNumbers) {
			userSearchRequest.put("userName", mobileNo);
			try {
				Object user = serviceRequestRepository.fetchResult(uri, userSearchRequest);
				if (null != user) {
					if (JsonPath.read(user, "$.user[0].emailId") != null) {
						String email = JsonPath.read(user, "$.user[0].emailId");
						mapOfPhnoAndEmailIds.put(mobileNo, email);
					}
				} else {
					log.error("Service returned null while fetching user for username - " + mobileNo);
				}
			} catch (Exception e) {
				log.error("Exception while fetching user for username - " + mobileNo);
				log.error("Exception trace: ", e);
				continue;
			}
		}
		return mapOfPhnoAndEmailIds;
	}

	/**
	 * Method to fetch the list of channels for a particular action from mdms
	 * configd from mdms configs returns the message minus some lines to match In
	 * App Templates
	 * 
	 * @param requestInfo
	 * @param tenantId
	 * @param moduleName
	 * @param action
	 */
	public List<String> fetchChannelList(RequestInfo requestInfo, String tenantId, String moduleName, String action) {
		List<String> masterData = new ArrayList<>();
		StringBuilder uri = new StringBuilder();
		uri.append(config.getMdmsHost()).append(config.getMdmsPath());
		if (StringUtils.isEmpty(tenantId))
			return masterData;
		MdmsCriteriaReq mdmsCriteriaReq = getMdmsRequestForChannelList(requestInfo, tenantId);

		Filter masterDataFilter = filter(
				where(StreetVendingConstants.MODULE).is(moduleName).and(StreetVendingConstants.ACTION).is(action));

		try {
			Object response = serviceRequestRepository.fetchResult(uri, mdmsCriteriaReq);
			masterData = JsonPath.parse(response).read("$.MdmsRes.Channel.channelList[?].channelNames[*]",
					masterDataFilter);
		} catch (Exception e) {
			log.error("Exception while fetching workflow states to ignore: ", e);
		}

		return masterData;
	}

	
	/**
	 * Builds an MDMS request for retrieving the channel list.
	 *
	 * @param requestInfo the request information containing metadata
	 * @param tenantId    the tenant ID for which the data is requested
	 * @return an {@link MdmsCriteriaReq} object containing the request criteria for fetching the channel list
	 */
	
	private MdmsCriteriaReq getMdmsRequestForChannelList(RequestInfo requestInfo, String tenantId) {
		MasterDetail masterDetail = new MasterDetail();
		masterDetail.setName(StreetVendingConstants.CHANNEL_LIST);
		List<MasterDetail> masterDetailList = new ArrayList<>();
		masterDetailList.add(masterDetail);

		ModuleDetail moduleDetail = new ModuleDetail();
		moduleDetail.setMasterDetails(masterDetailList);
		moduleDetail.setModuleName(StreetVendingConstants.CHANNEL);
		List<ModuleDetail> moduleDetailList = new ArrayList<>();
		moduleDetailList.add(moduleDetail);

		MdmsCriteria mdmsCriteria = new MdmsCriteria();
		mdmsCriteria.setTenantId(tenantId);
		mdmsCriteria.setModuleDetails(moduleDetailList);

		MdmsCriteriaReq mdmsCriteriaReq = new MdmsCriteriaReq();
		mdmsCriteriaReq.setMdmsCriteria(mdmsCriteria);
		mdmsCriteriaReq.setRequestInfo(requestInfo);

		return mdmsCriteriaReq;
	}
	
	/**
	 * Retrieves a customized notification message based on the application status and workflow action.
	 *
	 * @param requestInfo         the request information containing metadata
	 * @param streetVendingDetail the details of the street vending application
	 * @param localizationMessage the localized message template
	 * @return a customized notification message for the user
	 */

	public String getCustomizedMsg(RequestInfo requestInfo, StreetVendingDetail streetVendingDetail,
			String localizationMessage) {
		String message = null, messageTemplate;
		String ACTION_STATUS = streetVendingDetail.getWorkflow().getAction();
		switch (ACTION_STATUS) {

		case StreetVendingConstants.ACTION_STATUS_APPLY:
			messageTemplate = getMessageTemplate(StreetVendingConstants.NOTIFICATION_SUBMIT, localizationMessage);
			message = getMessageWithNumber(streetVendingDetail, messageTemplate);
			break;

		case StreetVendingConstants.ACTION_STATUS_FORWARD:
			if (streetVendingDetail.getApplicationStatus().equals(StreetVendingConstants.INSPECTION_PENDING_STATUS)) {
				messageTemplate = getMessageTemplate(StreetVendingConstants.NOTIFICATION_INSPECTION,
						localizationMessage);
			} else {
				messageTemplate = getMessageTemplate(StreetVendingConstants.NOTIFICATION_INSPECTION_COMPLETE,
						localizationMessage);
			}
			message = getMessageWithNumber(streetVendingDetail, messageTemplate);
			break;

		case StreetVendingConstants.ACTION_STATUS_SENDBACKTOCITIZEN:
			messageTemplate = getMessageTemplate(StreetVendingConstants.NOTIFICATION_SENTBACK, localizationMessage);
			message = getMessageWithNumber(streetVendingDetail, messageTemplate);
			break;

		case StreetVendingConstants.ACTION_STATUS_REJECT:
			messageTemplate = getMessageTemplate(StreetVendingConstants.NOTIFICATION_REJECT, localizationMessage);
			message = getMessageWithNumber(streetVendingDetail, messageTemplate);
			break;

		case StreetVendingConstants.ACTION_STATUS_PAY:
			messageTemplate = getMessageTemplate(StreetVendingConstants.NOTIFICATION_REGISTRATIONCOMPLETED,
					localizationMessage);
			message = getMessageWithNumberAndFinalDetails(streetVendingDetail, messageTemplate);
			break;
		case StreetVendingConstants.ACTION_STATUS_ELIGIBLE_TO_RENEW:
			messageTemplate = getMessageTemplate(StreetVendingConstants.NOTIFICATION_ELIGIBLETORENEW,
					localizationMessage);
			message = getMessageWithNumberAndFinalDetails(streetVendingDetail, messageTemplate);
			break;
		case StreetVendingConstants.ACTION_STATUS_APPLICATION_EXPIRED:
			messageTemplate = getMessageTemplate(StreetVendingConstants.NOTIFICATION_APPLICATIONEXPIRED,
					localizationMessage);
			message = getMessageWithNumberAndFinalDetails(streetVendingDetail, messageTemplate);
			break;
		}

		return message;
	}

	/**
	 * Formats a notification message by replacing placeholders with the vendor's name and application number.
	 *
	 * @param streetVendingDetail the details of the street vending application
	 * @param message             the message template with placeholders
	 * @return a formatted message with the vendor's details
	 */
	
	private String getMessageWithNumber(StreetVendingDetail streetVendingDetail, String message) {
		message = message.replace("{1}", streetVendingDetail.getVendorDetail().get(0).getName());
		message = message.replace("{2}", streetVendingDetail.getApplicationNo());
		return message;
	}

	/**
	 * Formats a notification message by replacing placeholders with the vendor's name, application number,
	 * and certificate number.
	 *
	 * @param streetVendingDetail the details of the street vending application
	 * @param message             the message template with placeholders
	 * @return a formatted message with the vendor's details and certificate number
	 */
	
	private String getMessageWithNumberAndFinalDetails(StreetVendingDetail streetVendingDetail, String message) {
		message = message.replace("{1}", streetVendingDetail.getVendorDetail().get(0).getName());
		message = message.replace("{2}", streetVendingDetail.getApplicationNo());
		message = message.replace("{3}", streetVendingDetail.getCertificateNo());
		return message;
	}

}
