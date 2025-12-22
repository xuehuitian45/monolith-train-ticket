package edu.fudanselab.trainticket.service.impl;

import edu.fudanselab.trainticket.entity.ConsignRecord;
import edu.fudanselab.trainticket.entity.Consign;
import edu.fudanselab.trainticket.repository.ConsignRepository;
import edu.fudanselab.trainticket.service.ConsignService;
import edu.fudanselab.trainticket.service.ServiceResolver;
import edu.fudanselab.trainticket.util.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
//import org.springframework.cloud.client.ServiceInstance;
//import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @author fdse
 */
@Service
public class ConsignServiceImpl implements ConsignService {
    @Autowired
    ConsignRepository repository;

    @Autowired
    RestTemplate restTemplate;

    /*@Autowired
    private DiscoveryClient discoveryClient;*/

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsignServiceImpl.class);

    @Autowired
    private ServiceResolver serviceResolver;


    @Override
    public Response insertConsignRecord(Consign consignRequest, HttpHeaders headers) {
        ConsignServiceImpl.LOGGER.info("[insertConsignRecord][Insert Start][consignRequest.getOrderId: {}]", consignRequest.getOrderId());

        // 验证必填字段
        if (consignRequest.getOrderId() == null || consignRequest.getOrderId().isEmpty()) {
            LOGGER.error("[insertConsignRecord][OrderId is null or empty]");
            return new Response<>(0, "OrderId is required", null);
        }
        if (consignRequest.getAccountId() == null || consignRequest.getAccountId().isEmpty()) {
            LOGGER.error("[insertConsignRecord][AccountId is null or empty]");
            return new Response<>(0, "AccountId is required", null);
        }

        ConsignRecord consignRecord = new ConsignRecord();
        //Set the record attribute
        consignRecord.setId(UUID.randomUUID().toString());
        consignRecord.setOrderId(consignRequest.getOrderId());
        consignRecord.setAccountId(consignRequest.getAccountId());
        ConsignServiceImpl.LOGGER.info("[insertConsignRecord][Insert Info][handle date: {}, target date: {}]", consignRequest.getHandleDate(), consignRequest.getTargetDate());
        consignRecord.setHandleDate(consignRequest.getHandleDate());
        consignRecord.setTargetDate(consignRequest.getTargetDate());
        consignRecord.setFrom(consignRequest.getFrom());
        consignRecord.setTo(consignRequest.getTo());
        consignRecord.setConsignee(consignRequest.getConsignee());
        consignRecord.setPhone(consignRequest.getPhone());
        consignRecord.setWeight(consignRequest.getWeight());

        //get the price
        try {
            HttpEntity requestEntity = new HttpEntity(null, headers);
            String consign_price_service_url = serviceResolver.getServiceUrl("ts-consign-price-service");
            ResponseEntity<Response<Double>> re = restTemplate.exchange(
                    consign_price_service_url + "/api/v1/consignpriceservice/consignprice/" + consignRequest.getWeight() + "/" + consignRequest.isWithin(),
                    HttpMethod.GET,
                    requestEntity,
                    new ParameterizedTypeReference<Response<Double>>() {
                    });
            if (re.getBody() != null && re.getBody().getData() != null) {
                consignRecord.setPrice(re.getBody().getData());
            } else {
                LOGGER.error("[insertConsignRecord][Get price failed][Response body is null]");
                return new Response<>(0, "Failed to get consign price", null);
            }
        } catch (Exception e) {
            LOGGER.error("[insertConsignRecord][Get price error][error: {}]", e.getMessage(), e);
            return new Response<>(0, "Failed to get consign price: " + e.getMessage(), null);
        }

        LOGGER.info("[insertConsignRecord][SAVE consign info][consignRecord : {}]", consignRecord.toString());
        ConsignRecord result = repository.save(consignRecord);
        LOGGER.info("[insertConsignRecord][SAVE consign result][result: {}]", result.toString());
        return new Response<>(1, "You have consigned successfully! The price is " + result.getPrice(), result);
    }

    @Override
    public Response updateConsignRecord(Consign consignRequest, HttpHeaders headers) {
        ConsignServiceImpl.LOGGER.info("[updateConsignRecord][Update Start]");

        if (consignRequest.getId() == null || consignRequest.getId().isEmpty()) {
            LOGGER.error("[updateConsignRecord][Id is null or empty]");
            return new Response<>(0, "Consign id is required", null);
        }

        if (!repository.findById(consignRequest.getId()).isPresent()) {
            return insertConsignRecord(consignRequest, headers);
        }
        ConsignRecord originalRecord = repository.findById(consignRequest.getId()).get();

        if (consignRequest.getAccountId() != null && !consignRequest.getAccountId().isEmpty()) {
            originalRecord.setAccountId(consignRequest.getAccountId());
        }
        originalRecord.setHandleDate(consignRequest.getHandleDate());
        originalRecord.setTargetDate(consignRequest.getTargetDate());
        originalRecord.setFrom(consignRequest.getFrom());
        originalRecord.setTo(consignRequest.getTo());
        originalRecord.setConsignee(consignRequest.getConsignee());
        originalRecord.setPhone(consignRequest.getPhone());
        //Recalculate price
        if (originalRecord.getWeight() != consignRequest.getWeight()) {
            try {
                HttpEntity requestEntity = new HttpEntity<>(null, headers);
                String consign_price_service_url = serviceResolver.getServiceUrl("ts-consign-price-service");
                ResponseEntity<Response<Double>> re = restTemplate.exchange(
                        consign_price_service_url + "/api/v1/consignpriceservice/consignprice/" + consignRequest.getWeight() + "/" + consignRequest.isWithin(),
                        HttpMethod.GET,
                        requestEntity,
                        new ParameterizedTypeReference<Response<Double>>() {
                        });

                if (re.getBody() != null && re.getBody().getData() != null) {
                    originalRecord.setPrice(re.getBody().getData());
                } else {
                    LOGGER.warn("[updateConsignRecord][Get price failed][Response body is null, keeping original price]");
                }
            } catch (Exception e) {
                LOGGER.error("[updateConsignRecord][Get price error][error: {}]", e.getMessage(), e);
                // 如果获取价格失败，保持原价格
            }
        }
        originalRecord.setConsignee(consignRequest.getConsignee());
        originalRecord.setPhone(consignRequest.getPhone());
        originalRecord.setWeight(consignRequest.getWeight());
        repository.save(originalRecord);
        return new Response<>(1, "Update consign success", originalRecord);
    }

    @Override
    public Response queryByAccountId(UUID accountId, HttpHeaders headers) {
        List<ConsignRecord> consignRecords = repository.findByAccountId(accountId.toString());
        if (consignRecords != null && !consignRecords.isEmpty()) {
            return new Response<>(1, "Find consign by account id success", consignRecords);
        }else {
            LOGGER.warn("[queryByAccountId][No Content according to accountId][accountId: {}]", accountId);
            return new Response<>(0, "No Content according to accountId", null);
        }
    }

    @Override
    public Response queryByOrderId(UUID orderId, HttpHeaders headers) {
        ConsignRecord consignRecords = repository.findByOrderId(orderId.toString());
        if (consignRecords != null ) {
            return new Response<>(1, "Find consign by order id success", consignRecords);
        }else {
            LOGGER.warn("[queryByOrderId][No Content according to orderId][orderId: {}]", orderId);
            return new Response<>(0, "No Content according to order id", null);
        }
    }

    @Override
    public Response queryByConsignee(String consignee, HttpHeaders headers) {
        List<ConsignRecord> consignRecords = repository.findByConsignee(consignee);
        if (consignRecords != null && !consignRecords.isEmpty()) {
            return new Response<>(1, "Find consign by consignee success", consignRecords);
        }else {
            LOGGER.warn("[queryByConsignee][No Content according to consignee][consignee: {}]", consignee);
            return new Response<>(0, "No Content according to consignee", null);
        }
    }
}
