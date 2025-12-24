package edu.fudanselab.trainticket.service.impl;

import edu.fudanselab.trainticket.entity.TrainType;
import edu.fudanselab.trainticket.entity.TripInfo;
import edu.fudanselab.trainticket.entity.TripResponse;
import edu.fudanselab.trainticket.entity.RoutePlanInfo;
import edu.fudanselab.trainticket.entity.RoutePlanResultUnit;
import edu.fudanselab.trainticket.entity.Seat;
import edu.fudanselab.trainticket.entity.SeatClass;
import edu.fudanselab.trainticket.util.JsonUtils;
import edu.fudanselab.trainticket.util.Response;
import edu.fudanselab.trainticket.util.StringUtils;
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
import org.springframework.web.client.RestTemplate;
import edu.fudanselab.trainticket.entity.TransferTravelInfo;
import edu.fudanselab.trainticket.entity.TransferTravelResult;
import edu.fudanselab.trainticket.entity.TravelAdvanceResultUnit;
import edu.fudanselab.trainticket.service.ServiceResolver;
import edu.fudanselab.trainticket.service.TravelPlanService;

import java.util.ArrayList;
import java.util.List;

/**
 * @author fdse
 */
@Service
public class TravelPlanServiceImpl implements TravelPlanService {

    @Autowired
    private RestTemplate restTemplate;
    /*@Autowired
    private DiscoveryClient discoveryClient;*/

    private static final Logger LOGGER = LoggerFactory.getLogger(TravelPlanServiceImpl.class);

    String success = "Success";
    String cannotFind = "Cannot Find";

    @Autowired
    private ServiceResolver serviceResolver;

    @Override
    public Response getTransferSearch(TransferTravelInfo info, HttpHeaders headers) {
        // 1. 前置入参校验（源头避免无效请求）
        if (info == null) {
            LOGGER.error("[getTransferSearch] TransferTravelInfo is null");
            return new Response<>(0, "Invalid request: TransferTravelInfo cannot be null", null);
        }
        // 校验核心字段非空
        if (info.getTravelDate() == null
                || info.getStartStation() == null || info.getStartStation().trim().isEmpty()
                || info.getViaStation() == null || info.getViaStation().trim().isEmpty()
                || info.getEndStation() == null || info.getEndStation().trim().isEmpty()) {
            LOGGER.error("[getTransferSearch] Invalid TransferTravelInfo: travelDate/startStation/viaStation/endStation cannot be empty");
            return new Response<>(0, "Invalid request: travelDate/startStation/viaStation/endStation cannot be empty", null);
        }

        // 2. 构建第一段行程查询参数（安全处理日期转换）
        TripInfo queryInfoFirstSection = new TripInfo();
        try {
            // 安全转换日期：避免Date2String处理null
            String departureTime = StringUtils.Date2String(info.getTravelDate());
            queryInfoFirstSection.setDepartureTime(departureTime == null ? "" : departureTime);
        } catch (Exception e) {
            LOGGER.error("[getTransferSearch] Failed to convert travelDate to string", e);
            queryInfoFirstSection.setDepartureTime(""); // 兜底空字符串
        }
        queryInfoFirstSection.setStartPlace(info.getStartStation());
        queryInfoFirstSection.setEndPlace(info.getViaStation());

        // 3. 查询第一段行程（空值兜底+异常捕获）
        List<TripResponse> firstSectionFromHighSpeed = new ArrayList<>();
        List<TripResponse> firstSectionFromNormal = new ArrayList<>();
        try {
            List<TripResponse> tempHigh = tripsFromHighSpeed(queryInfoFirstSection, headers);
            if (tempHigh != null) {
                firstSectionFromHighSpeed = tempHigh;
            }
        } catch (Exception e) {
            LOGGER.error("[getTransferSearch] Failed to get first section high speed trips", e);
            // 异常时仍返回空列表，不终止逻辑
        }
        try {
            List<TripResponse> tempNormal = tripsFromNormal(queryInfoFirstSection, headers);
            if (tempNormal != null) {
                firstSectionFromNormal = tempNormal;
            }
        } catch (Exception e) {
            LOGGER.error("[getTransferSearch] Failed to get first section normal trips", e);
        }

        // 4. 构建第二段行程查询参数
        TripInfo queryInfoSecondSection = new TripInfo();
        try {
            String departureTime = StringUtils.Date2String(info.getTravelDate());
            queryInfoSecondSection.setDepartureTime(departureTime == null ? "" : departureTime);
        } catch (Exception e) {
            LOGGER.error("[getTransferSearch] Failed to convert travelDate to string for second section", e);
            queryInfoSecondSection.setDepartureTime("");
        }
        queryInfoSecondSection.setStartPlace(info.getViaStation());
        queryInfoSecondSection.setEndPlace(info.getEndStation());

        // 5. 查询第二段行程（空值兜底+异常捕获）
        List<TripResponse> secondSectionFromHighSpeed = new ArrayList<>();
        List<TripResponse> secondSectionFromNormal = new ArrayList<>();
        try {
            List<TripResponse> tempHigh = tripsFromHighSpeed(queryInfoSecondSection, headers);
            if (tempHigh != null) {
                secondSectionFromHighSpeed = tempHigh;
            }
        } catch (Exception e) {
            LOGGER.error("[getTransferSearch] Failed to get second section high speed trips", e);
        }
        try {
            List<TripResponse> tempNormal = tripsFromNormal(queryInfoSecondSection, headers);
            if (tempNormal != null) {
                secondSectionFromNormal = tempNormal;
            }
        } catch (Exception e) {
            LOGGER.error("[getTransferSearch] Failed to get second section normal trips", e);
        }

        // 6. 合并行程结果（确保addAll的集合非null）
        List<TripResponse> firstSection = new ArrayList<>();
        firstSection.addAll(firstSectionFromHighSpeed); // 已兜底为空列表，不会抛NPE
        firstSection.addAll(firstSectionFromNormal);

        List<TripResponse> secondSection = new ArrayList<>();
        secondSection.addAll(secondSectionFromHighSpeed);
        secondSection.addAll(secondSectionFromNormal);

        // 7. 构建返回结果（无有效换乘时返回友好提示）
        TransferTravelResult result = new TransferTravelResult();
        result.setFirstSectionResult(firstSection);
        result.setSecondSectionResult(secondSection);

        // 校验是否有有效换乘结果
        boolean hasValidResult = !firstSection.isEmpty() && !secondSection.isEmpty();
        if (hasValidResult) {
            LOGGER.info("[getTransferSearch] Transfer search success: first section size={}, second section size={}",
                    firstSection.size(), secondSection.size());
            return new Response<>(1, "Success.", result);
        } else {
            LOGGER.warn("[getTransferSearch] No transfer trips found: start={}, via={}, end={}",
                    info.getStartStation(), info.getViaStation(), info.getEndStation());
            return new Response<>(0, "No transfer trips found", result); // 返回空结果，便于前端展示
        }
    }

    @Override
    public Response getCheapest(TripInfo info, HttpHeaders headers) {
        RoutePlanInfo routePlanInfo = new RoutePlanInfo();
        routePlanInfo.setNum(5);
        routePlanInfo.setStartStation(info.getStartPlace());
        routePlanInfo.setEndStation(info.getEndPlace());
        routePlanInfo.setTravelDate(info.getDepartureTime());
        ArrayList<RoutePlanResultUnit> routePlanResultUnits = getRoutePlanResultCheapest(routePlanInfo, headers);

        if (!routePlanResultUnits.isEmpty()) {
            ArrayList<TravelAdvanceResultUnit> lists = new ArrayList<>();
            for (int i = 0; i < routePlanResultUnits.size(); i++) {
                RoutePlanResultUnit tempUnit = routePlanResultUnits.get(i);
                TravelAdvanceResultUnit newUnit = new TravelAdvanceResultUnit();
                newUnit.setTripId(tempUnit.getTripId());
                newUnit.setEndStation(tempUnit.getEndStation());
                newUnit.setTrainTypeId(tempUnit.getTrainTypeName());
                newUnit.setStartStation(tempUnit.getStartStation());

                List<String> stops = tempUnit.getStopStations();
                newUnit.setStopStations(stops);
                newUnit.setPriceForFirstClassSeat(tempUnit.getPriceForFirstClassSeat());
                newUnit.setPriceForSecondClassSeat(tempUnit.getPriceForSecondClassSeat());
                newUnit.setStartTime(tempUnit.getStartTime());
                newUnit.setEndTime(tempUnit.getEndTime());

                TrainType trainType = queryTrainTypeByName(tempUnit.getTrainTypeName(), headers);
                int firstClassTotalNum = trainType.getConfortClass();
                int secondClassTotalNum = trainType.getEconomyClass();

                int first = getRestTicketNumber(info.getDepartureTime(), tempUnit.getTripId(),
                        tempUnit.getStartStation(), tempUnit.getEndStation(), SeatClass.FIRSTCLASS.getCode(), firstClassTotalNum, tempUnit.getStopStations(), headers);

                int second = getRestTicketNumber(info.getDepartureTime(), tempUnit.getTripId(),
                        tempUnit.getStartStation(), tempUnit.getEndStation(), SeatClass.SECONDCLASS.getCode(), secondClassTotalNum, tempUnit.getStopStations(), headers);
                newUnit.setNumberOfRestTicketFirstClass(first);
                newUnit.setNumberOfRestTicketSecondClass(second);
                lists.add(newUnit);
            }

            return new Response<>(1, success, lists);
        } else {
            TravelPlanServiceImpl.LOGGER.warn("[getCheapest][Get cheapest trip warn][Route Plan Result Units: {}]","No Content");
            return new Response<>(0, cannotFind, null);
        }
    }

    @Override
    public Response getQuickest(TripInfo info, HttpHeaders headers) {
        // 1. 前置入参校验（源头避免无效请求）
        if (info == null) {
            TravelPlanServiceImpl.LOGGER.error("[getQuickest] TripInfo is null");
            return new Response<>(0, cannotFind, null);
        }
        if (info.getStartPlace() == null || info.getStartPlace().trim().isEmpty()
                || info.getEndPlace() == null || info.getEndPlace().trim().isEmpty()
                || info.getDepartureTime() == null || info.getDepartureTime().trim().isEmpty()) {
            TravelPlanServiceImpl.LOGGER.error("[getQuickest] Invalid TripInfo: start/end place or departure time is empty");
            return new Response<>(0, "Invalid request: start/end place or departure time cannot be empty", null);
        }

        // 2. 构建RoutePlanInfo
        RoutePlanInfo routePlanInfo = new RoutePlanInfo();
        routePlanInfo.setNum(5);
        routePlanInfo.setStartStation(info.getStartPlace());
        routePlanInfo.setEndStation(info.getEndPlace());
        routePlanInfo.setTravelDate(info.getDepartureTime());

        // 3. 获取最快路线规划结果（核心空值修复）
        ArrayList<RoutePlanResultUnit> routePlanResultUnits = new ArrayList<>();
        try {
            ArrayList<RoutePlanResultUnit> tempResult = getRoutePlanResultQuickest(routePlanInfo, headers);
            if (tempResult != null) {
                routePlanResultUnits = tempResult;
            }
        } catch (Exception e) {
            TravelPlanServiceImpl.LOGGER.error("[getQuickest] Failed to get quickest route plan", e);
            return new Response<>(0, "Failed to query quickest trip: " + e.getMessage(), null);
        }

        // 4. 无结果时返回友好提示
        if (routePlanResultUnits.isEmpty()) {
            TravelPlanServiceImpl.LOGGER.warn("[getQuickest][Get quickest trip warn][Route Plan Result Units: No Content]");
            return new Response<>(0, cannotFind, null);
        }

        // 5. 转换结果并计算余票（增加全链路异常处理）
        ArrayList<TravelAdvanceResultUnit> lists = new ArrayList<>();
        for (int i = 0; i < routePlanResultUnits.size(); i++) {
            RoutePlanResultUnit tempUnit = routePlanResultUnits.get(i);
            // 跳过null的tempUnit
            if (tempUnit == null) {
                TravelPlanServiceImpl.LOGGER.warn("[getQuickest] Skip null RoutePlanResultUnit at index: {}", i);
                continue;
            }

            try {
                TravelAdvanceResultUnit newUnit = new TravelAdvanceResultUnit();
                newUnit.setTripId(tempUnit.getTripId());
                newUnit.setTrainTypeId(tempUnit.getTrainTypeName());
                newUnit.setEndStation(tempUnit.getEndStation());
                newUnit.setStartStation(tempUnit.getStartStation());

                // 修复：StopStations空值兜底
                List<String> stops = tempUnit.getStopStations();
                newUnit.setStopStations(stops == null ? new ArrayList<>() : stops);

                newUnit.setPriceForFirstClassSeat(tempUnit.getPriceForFirstClassSeat());
                newUnit.setPriceForSecondClassSeat(tempUnit.getPriceForSecondClassSeat());
                newUnit.setStartTime(tempUnit.getStartTime());
                newUnit.setEndTime(tempUnit.getEndTime());

                // 修复：TrainType空值处理
                TrainType trainType = queryTrainTypeByName(tempUnit.getTrainTypeName(), headers);
                int firstClassTotalNum = 0;
                int secondClassTotalNum = 0;
                if (trainType != null) {
                    firstClassTotalNum = trainType.getConfortClass();
                    secondClassTotalNum = trainType.getEconomyClass();
                } else {
                    TravelPlanServiceImpl.LOGGER.warn("[getQuickest] No TrainType found for name: {}", tempUnit.getTrainTypeName());
                    // 兜底默认值，避免后续计算异常
                    firstClassTotalNum = 0;
                    secondClassTotalNum = 0;
                }

                // 修复：getRestTicketNumber增加异常捕获+参数校验
                int first = 0;
                int second = 0;
                try {
                    // 确保stopStations非null（已兜底）
                    first = getRestTicketNumber(
                            info.getDepartureTime(),
                            tempUnit.getTripId(),
                            tempUnit.getStartStation(),
                            tempUnit.getEndStation(),
                            SeatClass.FIRSTCLASS.getCode(),
                            firstClassTotalNum,
                            newUnit.getStopStations(), // 使用已兜底的stopStations
                            headers
                    );

                    second = getRestTicketNumber(
                            info.getDepartureTime(),
                            tempUnit.getTripId(),
                            tempUnit.getStartStation(),
                            tempUnit.getEndStation(),
                            SeatClass.SECONDCLASS.getCode(),
                            secondClassTotalNum,
                            newUnit.getStopStations(),
                            headers
                    );
                } catch (Exception e) {
                    TravelPlanServiceImpl.LOGGER.error("[getQuickest] Failed to get rest tickets for tripId: {}", tempUnit.getTripId(), e);
                    // 单个车次余票查询失败，设为0而非终止循环
                    first = 0;
                    second = 0;
                }

                newUnit.setNumberOfRestTicketFirstClass(first);
                newUnit.setNumberOfRestTicketSecondClass(second);
                lists.add(newUnit);
            } catch (Exception e) {
                TravelPlanServiceImpl.LOGGER.error("[getQuickest] Failed to process RoutePlanResultUnit at index: {}", i, e);
                // 单个车次处理失败，跳过继续处理下一个
                continue;
            }
        }

        // 最终结果兜底（所有车次都处理失败时）
        if (lists.isEmpty()) {
            TravelPlanServiceImpl.LOGGER.warn("[getQuickest] No valid TravelAdvanceResultUnit after processing");
            return new Response<>(0, cannotFind, null);
        }

        return new Response<>(1, success, lists);
    }

    @Override
    public Response getMinStation(TripInfo info, HttpHeaders headers) {
        // 1. 前置入参校验（源头避免无效请求，核心修复第一步）
        if (info == null) {
            TravelPlanServiceImpl.LOGGER.error("[getMinStation] TripInfo is null");
            return new Response<>(0, cannotFind, null);
        }
        if (info.getStartPlace() == null || info.getStartPlace().trim().isEmpty()
                || info.getEndPlace() == null || info.getEndPlace().trim().isEmpty()
                || info.getDepartureTime() == null || info.getDepartureTime().trim().isEmpty()) {
            TravelPlanServiceImpl.LOGGER.error("[getMinStation] Invalid TripInfo: startPlace/endPlace/departureTime is empty");
            return new Response<>(0, "Invalid request: start/end place or departure time cannot be empty", null);
        }

        // 2. 构建RoutePlanInfo
        RoutePlanInfo routePlanInfo = new RoutePlanInfo();
        routePlanInfo.setNum(5);
        routePlanInfo.setStartStation(info.getStartPlace());
        routePlanInfo.setEndStation(info.getEndPlace());
        routePlanInfo.setTravelDate(info.getDepartureTime());

        // 3. 核心修复：初始化空列表兜底，避免getRoutePlanResultMinStation返回null触发NPE
        ArrayList<RoutePlanResultUnit> routePlanResultUnits = new ArrayList<>();
        try {
            ArrayList<RoutePlanResultUnit> tempResult = getRoutePlanResultMinStation(routePlanInfo, headers);
            // 只有返回值非null时才替换，否则保留空列表
            if (tempResult != null) {
                routePlanResultUnits = tempResult;
            } else {
                TravelPlanServiceImpl.LOGGER.warn("[getMinStation] getRoutePlanResultMinStation return null");
            }
        } catch (Exception e) {
            // 捕获getRoutePlanResultMinStation的所有异常（如远程调用失败）
            TravelPlanServiceImpl.LOGGER.error("[getMinStation] Failed to get route plan result for min station", e);
            return new Response<>(0, "Failed to query min station trips: " + e.getMessage(), null);
        }

        // 4. 无结果时返回友好提示（此时routePlanResultUnits一定非null，只是可能为空）
        if (routePlanResultUnits.isEmpty()) {
            TravelPlanServiceImpl.LOGGER.warn("[getMinStation][Get min stations trip warn][Route Plan Result Units: No Content]");
            return new Response<>(0, cannotFind, null);
        }

        // 5. 遍历处理结果（全链路空值校验+异常捕获，核心修复第二步）
        ArrayList<TravelAdvanceResultUnit> lists = new ArrayList<>();
        for (int i = 0; i < routePlanResultUnits.size(); i++) {
            RoutePlanResultUnit tempUnit = routePlanResultUnits.get(i);
            // 跳过null的tempUnit，避免空指针
            if (tempUnit == null) {
                TravelPlanServiceImpl.LOGGER.warn("[getMinStation] Skip null RoutePlanResultUnit at index: {}", i);
                continue;
            }

            try {
                TravelAdvanceResultUnit newUnit = new TravelAdvanceResultUnit();
                newUnit.setTripId(tempUnit.getTripId());
                newUnit.setTrainTypeId(tempUnit.getTrainTypeName());
                newUnit.setStartStation(tempUnit.getStartStation());
                newUnit.setEndStation(tempUnit.getEndStation());

                // 修复：StopStations空值兜底（避免传入null）
                List<String> stops = tempUnit.getStopStations();
                newUnit.setStopStations(stops == null ? new ArrayList<>() : stops);

                newUnit.setPriceForFirstClassSeat(tempUnit.getPriceForFirstClassSeat());
                newUnit.setPriceForSecondClassSeat(tempUnit.getPriceForSecondClassSeat());
                newUnit.setEndTime(tempUnit.getEndTime());
                newUnit.setStartTime(tempUnit.getStartTime());

                // 修复：TrainType空值处理（避免调用getConfortClass()抛NPE）
                TrainType trainType = queryTrainTypeByName(tempUnit.getTrainTypeName(), headers);
                int firstClassTotalNum = 0;
                int secondClassTotalNum = 0;
                if (trainType != null) {
                    firstClassTotalNum = trainType.getConfortClass();
                    secondClassTotalNum = trainType.getEconomyClass();
                } else {
                    TravelPlanServiceImpl.LOGGER.warn("[getMinStation] No TrainType found for name: {}", tempUnit.getTrainTypeName());
                }

                // 修复：getRestTicketNumber异常捕获+参数兜底
                int first = 0;
                int second = 0;
                try {
                    first = getRestTicketNumber(
                            info.getDepartureTime(),
                            tempUnit.getTripId(),
                            tempUnit.getStartStation(),
                            tempUnit.getEndStation(),
                            SeatClass.FIRSTCLASS.getCode(),
                            firstClassTotalNum,
                            newUnit.getStopStations(), // 使用已兜底的非null列表
                            headers
                    );

                    second = getRestTicketNumber(
                            info.getDepartureTime(),
                            tempUnit.getTripId(),
                            tempUnit.getStartStation(),
                            tempUnit.getEndStation(),
                            SeatClass.SECONDCLASS.getCode(),
                            secondClassTotalNum,
                            newUnit.getStopStations(),
                            headers
                    );
                } catch (Exception e) {
                    TravelPlanServiceImpl.LOGGER.error("[getMinStation] Failed to get rest tickets for tripId: {}", tempUnit.getTripId(), e);
                    // 单个车次余票查询失败，设为0而非终止循环
                    first = 0;
                    second = 0;
                }

                newUnit.setNumberOfRestTicketFirstClass(first);
                newUnit.setNumberOfRestTicketSecondClass(second);
                lists.add(newUnit);
            } catch (Exception e) {
                // 单个车次处理失败，跳过继续处理下一个
                TravelPlanServiceImpl.LOGGER.error("[getMinStation] Failed to process RoutePlanResultUnit at index: {}", i, e);
                continue;
            }
        }

        // 最终兜底：所有车次处理失败时返回无结果
        if (lists.isEmpty()) {
            TravelPlanServiceImpl.LOGGER.warn("[getMinStation] No valid TravelAdvanceResultUnit after processing");
            return new Response<>(0, cannotFind, null);
        }

        return new Response<>(1, success, lists);
    }


    private int getRestTicketNumber(String travelDate, String trainNumber, String startStationName, String endStationName, int seatType, int totalNum, List<String> stations, HttpHeaders headers) {
        Seat seatRequest = new Seat();

        seatRequest.setDestStation(startStationName);
        seatRequest.setStartStation(endStationName);
        seatRequest.setTrainNumber(trainNumber);
        seatRequest.setTravelDate(travelDate);
        seatRequest.setSeatType(seatType);
        seatRequest.setStations(stations);
        seatRequest.setTotalNum(totalNum);

        TravelPlanServiceImpl.LOGGER.info("[getRestTicketNumber][Seat Request][Seat Request is: {}]", seatRequest.toString());
        HttpEntity requestEntity = new HttpEntity(seatRequest, null);
        String seat_service_url = serviceResolver.getServiceUrl("ts-seat-service");
        ResponseEntity<Response<Integer>> re = restTemplate.exchange(
                seat_service_url + "/api/v1/seatservice/seats/left_tickets",
                HttpMethod.POST,
                requestEntity,
                new ParameterizedTypeReference<Response<Integer>>() {
                });

        return re.getBody().getData();
    }

    private ArrayList<RoutePlanResultUnit> getRoutePlanResultCheapest(RoutePlanInfo info, HttpHeaders headers) {
        HttpEntity requestEntity = new HttpEntity(info, null);
        String route_plan_service_url = serviceResolver.getServiceUrl("ts-route-plan-service");
        ResponseEntity<Response<ArrayList<RoutePlanResultUnit>>> re = restTemplate.exchange(
                route_plan_service_url + "/api/v1/routeplanservice/routePlan/cheapestRoute",
                HttpMethod.POST,
                requestEntity,
                new ParameterizedTypeReference<Response<ArrayList<RoutePlanResultUnit>>>() {
                });
        return re.getBody().getData();
    }

    private ArrayList<RoutePlanResultUnit> getRoutePlanResultQuickest(RoutePlanInfo info, HttpHeaders headers) {
        HttpEntity requestEntity = new HttpEntity(info, null);
        String route_plan_service_url = serviceResolver.getServiceUrl("ts-route-plan-service");
        ResponseEntity<Response<ArrayList<RoutePlanResultUnit>>> re = restTemplate.exchange(
                route_plan_service_url + "/api/v1/routeplanservice/routePlan/quickestRoute",
                HttpMethod.POST,
                requestEntity,
                new ParameterizedTypeReference<Response<ArrayList<RoutePlanResultUnit>>>() {
                });

        return re.getBody().getData();
    }

    private ArrayList<RoutePlanResultUnit> getRoutePlanResultMinStation(RoutePlanInfo info, HttpHeaders headers) {
        HttpEntity requestEntity = new HttpEntity(info, null);
        String route_plan_service_url = serviceResolver.getServiceUrl("ts-route-plan-service");
        ResponseEntity<Response<ArrayList<RoutePlanResultUnit>>> re = restTemplate.exchange(
                route_plan_service_url + "/api/v1/routeplanservice/routePlan/minStopStations",
                HttpMethod.POST,
                requestEntity,
                new ParameterizedTypeReference<Response<ArrayList<RoutePlanResultUnit>>>() {
                });
        return re.getBody().getData();
    }

    private List<TripResponse> tripsFromHighSpeed(TripInfo info, HttpHeaders headers) {
        HttpEntity requestEntity = new HttpEntity(info, null);
        String travel_service_url=serviceResolver.getServiceUrl("ts-travel-service");
        ResponseEntity<Response<List<TripResponse>>> re = restTemplate.exchange(
                travel_service_url + "/api/v1/travelservice/trips/left",
                HttpMethod.POST,
                requestEntity,
                new ParameterizedTypeReference<Response<List<TripResponse>>>() {
                });
        return re.getBody().getData();
    }

    private ArrayList<TripResponse> tripsFromNormal(TripInfo info, HttpHeaders headers) {

        HttpEntity requestEntity = new HttpEntity(info, null);
        String travel2_service_url=serviceResolver.getServiceUrl("ts-travel2-service");
        ResponseEntity<Response<ArrayList<TripResponse>>> re = restTemplate.exchange(
                travel2_service_url + "/api/v1/travel2service/trips/left",
                HttpMethod.POST,
                requestEntity,
                new ParameterizedTypeReference<Response<ArrayList<TripResponse>>>() {
                });

        return re.getBody().getData();
    }

    public TrainType queryTrainTypeByName(String trainTypeName, HttpHeaders headers) {
        HttpEntity requestEntity = new HttpEntity(null);
        String train_service_url=serviceResolver.getServiceUrl("ts-train-service");
        ResponseEntity<Response> re = restTemplate.exchange(
                train_service_url + "/api/v1/trainservice/trains/byName/" + trainTypeName,
                HttpMethod.GET,
                requestEntity,
                Response.class);
        Response  response = re.getBody();

        return JsonUtils.conveterObject(response.getData(), TrainType.class);
    }

}
