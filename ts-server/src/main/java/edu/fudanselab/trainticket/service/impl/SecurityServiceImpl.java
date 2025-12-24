package edu.fudanselab.trainticket.service.impl;

import edu.fudanselab.trainticket.entity.OrderSecurity;
import edu.fudanselab.trainticket.util.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import edu.fudanselab.trainticket.entity.SecurityConfig;
import edu.fudanselab.trainticket.repository.SecurityRepository;
import edu.fudanselab.trainticket.service.SecurityService;
import edu.fudanselab.trainticket.service.ServiceResolver;

import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;

/**
 * @author fdse
 */
@Service
public class SecurityServiceImpl implements SecurityService {

    @Autowired
    private SecurityRepository securityRepository;

    @Autowired
    RestTemplate restTemplate;

    /*@Autowired
    private DiscoveryClient discoveryClient;*/

    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityServiceImpl.class);

    @Autowired
    private ServiceResolver serviceResolver;

    String success = "Success";

    @Override
    public Response findAllSecurityConfig(HttpHeaders headers) {
        ArrayList<SecurityConfig> securityConfigs = securityRepository.findAll();
        if (securityConfigs != null && !securityConfigs.isEmpty()) {
            return new Response<>(1, success, securityConfigs);
        }
        SecurityServiceImpl.LOGGER.warn("[findAllSecurityConfig][Find all security config warn][{}]","No content");
        return new Response<>(0, "No Content", null);
    }

    @Override
    public Response addNewSecurityConfig(SecurityConfig info, HttpHeaders headers) {
        SecurityConfig sc = securityRepository.findByName(info.getName());
        if (sc != null) {
            SecurityServiceImpl.LOGGER.warn("[addNewSecurityConfig][Add new Security config warn][Security config already exist][SecurityConfigId: {},Name: {}]",sc.getId(),info.getName());
            return new Response<>(0, "Security Config Already Exist", null);
        } else {
            SecurityConfig config = new SecurityConfig();
            config.setId(UUID.randomUUID().toString());
            config.setName(info.getName());
            config.setValue(info.getValue());
            config.setDescription(info.getDescription());
            securityRepository.save(config);
            return new Response<>(1, success, config);
        }
    }

    @Override
    public Response modifySecurityConfig(SecurityConfig info, HttpHeaders headers) {
        SecurityConfig sc = securityRepository.findById(info.getId()).orElse(null);
        if (sc == null) {
            SecurityServiceImpl.LOGGER.error("[modifySecurityConfig][Modify Security config error][Security config not found][SecurityConfigId: {},Name: {}]",info.getId(),info.getName());
            return new Response<>(0, "Security Config Not Exist", null);
        } else {
            sc.setName(info.getName());
            sc.setValue(info.getValue());
            sc.setDescription(info.getDescription());
            securityRepository.save(sc);
            return new Response<>(1, success, sc);
        }
    }

    @Transactional
    @Override
    public Response deleteSecurityConfig(String id, HttpHeaders headers) {
        securityRepository.deleteById(id);
        SecurityConfig sc = securityRepository.findById(id).orElse(null);
        if (sc == null) {
            return new Response<>(1, success, id);
        } else {
            SecurityServiceImpl.LOGGER.error("[deleteSecurityConfig][Delete Security config error][Reason not clear][SecurityConfigId: {}]",id);
            return new Response<>(0, "Reason Not clear", id);
        }
    }

    @Override
    public Response check(String accountId, HttpHeaders headers) {
        // 1. 获取订单安全信息（先判空）
        SecurityServiceImpl.LOGGER.debug("[check][Get Order Num Info]");
        OrderSecurity orderResult = getSecurityOrderInfoFromOrder(new Date(), accountId, headers);
        OrderSecurity orderOtherResult = getSecurityOrderOtherInfoFromOrder(new Date(), accountId, headers);

        // 修复：orderResult/orderOtherResult判空，避免NPE
        int orderInOneHour = 0;
        int totalValidOrder = 0;
        if (orderResult != null) {
            orderInOneHour += orderResult.getOrderNumInLastOneHour();
            totalValidOrder += orderResult.getOrderNumOfValidOrder();
        }
        if (orderOtherResult != null) {
            orderInOneHour += orderOtherResult.getOrderNumInLastOneHour();
            totalValidOrder += orderOtherResult.getOrderNumOfValidOrder();
        }

        // 2. 获取关键配置信息（核心修复：判空 + 处理配置不存在的情况）
        SecurityServiceImpl.LOGGER.debug("[check][Get Security Config Info]");
        SecurityConfig configMaxInHour = securityRepository.findByName("max_order_1_hour");
        SecurityConfig configMaxNotUse = securityRepository.findByName("max_order_not_use");

        // 修复：配置不存在时抛友好异常/使用默认值
        if (configMaxInHour == null) {
            String errorMsg = "安全配置不存在：max_order_1_hour";
            SecurityServiceImpl.LOGGER.error("[check][{}][AccountId: {}]", errorMsg, accountId);
            return new Response<>(0, errorMsg, accountId);
        }
        if (configMaxNotUse == null) {
            String errorMsg = "安全配置不存在：max_order_not_use";
            SecurityServiceImpl.LOGGER.error("[check][{}][AccountId: {}]", errorMsg, accountId);
            return new Response<>(0, errorMsg, accountId);
        }

        // 此时配置非null，可安全调用getValue()
        SecurityServiceImpl.LOGGER.info("[check][Max][Max In One Hour: {}  Max Not Use: {}]", configMaxInHour.getValue(), configMaxNotUse.getValue());

        // 3. 解析配置值（增加格式校验，避免NumberFormatException）
        int oneHourLine;
        int totalValidLine;
        try {
            oneHourLine = Integer.parseInt(configMaxInHour.getValue());
            totalValidLine = Integer.parseInt(configMaxNotUse.getValue());
        } catch (NumberFormatException e) {
            String errorMsg = "安全配置值格式错误（非整数）";
            SecurityServiceImpl.LOGGER.error("[check][{}][max_order_1_hour: {}, max_order_not_use: {}]", errorMsg, configMaxInHour.getValue(), configMaxNotUse.getValue(), e);
            return new Response<>(0, errorMsg, accountId);
        }

        // 4. 业务逻辑判断
        if (orderInOneHour > oneHourLine || totalValidOrder > totalValidLine) {
            SecurityServiceImpl.LOGGER.warn("[check][Check Security config warn][Too much order in last one hour or too much valid order][AccountId: {}]", accountId);
            return new Response<>(0, "Too much order in last one hour or too much valid order", accountId);
        } else {
            return new Response<>(1, "Success.", accountId);
        }
    }

    private OrderSecurity getSecurityOrderInfoFromOrder(Date checkDate, String accountId, HttpHeaders headers) {
        HttpEntity requestEntity = new HttpEntity(null);
        String order_service_url = serviceResolver.getServiceUrl("ts-order-service");
        ResponseEntity<Response<OrderSecurity>> re = restTemplate.exchange(
                order_service_url + "/api/v1/orderservice/order/security/" + checkDate + "/" + accountId,
                HttpMethod.GET,
                requestEntity,
                new ParameterizedTypeReference<Response<OrderSecurity>>() {
                });
        Response<OrderSecurity> response = re.getBody();
        OrderSecurity result =  response.getData();
        SecurityServiceImpl.LOGGER.info("[getSecurityOrderInfoFromOrder][Get Order Info For Security][Last One Hour: {}  Total Valid Order: {}]", result.getOrderNumInLastOneHour(), result.getOrderNumOfValidOrder());
        return result;
    }

    private OrderSecurity getSecurityOrderOtherInfoFromOrder(Date checkDate, String accountId, HttpHeaders headers) {
        HttpEntity requestEntity = new HttpEntity(null);
        String order_other_service_url = serviceResolver.getServiceUrl("ts-order-other-service");
        ResponseEntity<Response<OrderSecurity>> re = restTemplate.exchange(
                order_other_service_url + "/api/v1/orderOtherService/orderOther/security/" + checkDate + "/" + accountId,
                HttpMethod.GET,
                requestEntity,
                new ParameterizedTypeReference<Response<OrderSecurity>>() {
                });
        Response<OrderSecurity> response = re.getBody();
        OrderSecurity result =  response.getData();
        SecurityServiceImpl.LOGGER.info("[getSecurityOrderOtherInfoFromOrder][Get Order Other Info For Security][Last One Hour: {}  Total Valid Order: {}]", result.getOrderNumInLastOneHour(), result.getOrderNumOfValidOrder());
        return result;
    }

}
