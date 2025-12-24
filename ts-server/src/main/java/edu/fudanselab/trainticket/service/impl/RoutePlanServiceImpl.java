package edu.fudanselab.trainticket.service.impl;

import edu.fudanselab.trainticket.entity.Route;
import edu.fudanselab.trainticket.entity.RoutePlanResultUnit;
import edu.fudanselab.trainticket.entity.TripResponse;
import edu.fudanselab.trainticket.entity.Trip;
import edu.fudanselab.trainticket.entity.TripInfo;
import edu.fudanselab.trainticket.entity.RoutePlanInfo;
import edu.fudanselab.trainticket.entity.TripAllDetail;
import edu.fudanselab.trainticket.entity.TripAllDetailInfo;
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
import edu.fudanselab.trainticket.service.RoutePlanService;
import edu.fudanselab.trainticket.service.ServiceResolver;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author fdse
 */
@Service
public class RoutePlanServiceImpl implements RoutePlanService {

    @Autowired
    private RestTemplate restTemplate;
    /*@Autowired
    private DiscoveryClient discoveryClient;*/
    private static final Logger LOGGER = LoggerFactory.getLogger(RoutePlanServiceImpl.class);

    @Autowired
    private ServiceResolver serviceResolver;

    @Override
    public Response searchCheapestResult(RoutePlanInfo info, HttpHeaders headers) {

        //1.Violence pulls out all the results of travel-service and travle2-service
        TripInfo queryInfo = new TripInfo();
        queryInfo.setStartPlace(info.getStartStation());
        queryInfo.setEndPlace(info.getEndStation());
        queryInfo.setDepartureTime(info.getTravelDate());

        ArrayList<TripResponse> highSpeed = getTripFromHighSpeedTravelServive(queryInfo, headers);
        ArrayList<TripResponse> normalTrain = getTripFromNormalTrainTravelService(queryInfo, headers);

        //2.Sort by second-class seats
        ArrayList<TripResponse> finalResult = new ArrayList<>();
        finalResult.addAll(highSpeed);
        finalResult.addAll(normalTrain);

        float minPrice;
        int minIndex = -1;
        int size = Math.min(5, finalResult.size());
        ArrayList<TripResponse> returnResult = new ArrayList<>();
        for (int i = 0; i < size; i++) {

            minPrice = Float.MAX_VALUE;
            for (int j = 0; j < finalResult.size(); j++) {
                TripResponse thisRes = finalResult.get(j);
                if (Float.parseFloat(thisRes.getPriceForEconomyClass()) < minPrice) {
                    minPrice = Float.parseFloat(finalResult.get(j).getPriceForEconomyClass());
                    minIndex = j;
                }
            }
            returnResult.add(finalResult.get(minIndex));
            finalResult.remove(minIndex);
        }


        ArrayList<RoutePlanResultUnit> units = new ArrayList<>();
        for (int i = 0; i < returnResult.size(); i++) {
            TripResponse tempResponse = returnResult.get(i);

            RoutePlanResultUnit tempUnit = new RoutePlanResultUnit();
            tempUnit.setTripId(tempResponse.getTripId().toString());
            tempUnit.setTrainTypeName(tempResponse.getTrainTypeName());
            tempUnit.setStartStation(tempResponse.getStartStation());
            tempUnit.setEndStation(tempResponse.getTerminalStation());
            tempUnit.setStopStations(getStationList(tempResponse.getTripId().toString(), headers));
            tempUnit.setPriceForSecondClassSeat(tempResponse.getPriceForEconomyClass());
            tempUnit.setPriceForFirstClassSeat(tempResponse.getPriceForConfortClass());
            tempUnit.setEndTime(tempResponse.getEndTime());
            tempUnit.setStartTime(tempResponse.getStartTime());

            units.add(tempUnit);
        }

        return new Response<>(1, "Success", units);
    }

    @Override
    public Response searchQuickestResult(RoutePlanInfo info, HttpHeaders headers) {

        //1.Violence pulls out all the results of travel-service and travle2-service
        TripInfo queryInfo = new TripInfo();
        queryInfo.setStartPlace(info.getStartStation());
        queryInfo.setEndPlace(info.getEndStation());
        queryInfo.setDepartureTime(info.getTravelDate());

        ArrayList<TripResponse> highSpeed = getTripFromHighSpeedTravelServive(queryInfo, headers);
        ArrayList<TripResponse> normalTrain = getTripFromNormalTrainTravelService(queryInfo, headers);

        //2.Sort by time
        ArrayList<TripResponse> finalResult = new ArrayList<>();

        for (TripResponse tr : highSpeed) {
            finalResult.add(tr);
        }
        for (TripResponse tr : normalTrain) {
            finalResult.add(tr);
        }

        long minTime;
        int minIndex = -1;
        int size = Math.min(finalResult.size(), 5);
        ArrayList<TripResponse> returnResult = new ArrayList<>();
        for (int i = 0; i < size; i++) {

            minTime = Long.MAX_VALUE;
            for (int j = 0; j < finalResult.size(); j++) {
                TripResponse thisRes = finalResult.get(j);
                Date endTime = StringUtils.String2Date(thisRes.getEndTime());
                Date startTime = StringUtils.String2Date(thisRes.getStartTime());
                if (endTime.getTime() - startTime.getTime() < minTime) {
                    minTime = endTime.getTime() - startTime.getTime();
                    minIndex = j;
                }
            }
            returnResult.add(finalResult.get(minIndex));
            finalResult.remove(minIndex);

        }


        ArrayList<RoutePlanResultUnit> units = new ArrayList<>();
        for (int i = 0; i < returnResult.size(); i++) {
            TripResponse tempResponse = returnResult.get(i);

            RoutePlanResultUnit tempUnit = new RoutePlanResultUnit();
            tempUnit.setTripId(tempResponse.getTripId().toString());
            tempUnit.setTrainTypeName(tempResponse.getTrainTypeName());
            tempUnit.setStartStation(tempResponse.getStartStation());
            tempUnit.setEndStation(tempResponse.getTerminalStation());

            tempUnit.setStopStations(getStationList(tempResponse.getTripId().toString(), headers));

            tempUnit.setPriceForSecondClassSeat(tempResponse.getPriceForEconomyClass());
            tempUnit.setPriceForFirstClassSeat(tempResponse.getPriceForConfortClass());
            tempUnit.setStartTime(tempResponse.getStartTime());
            tempUnit.setEndTime(tempResponse.getEndTime());
            units.add(tempUnit);
        }
        return new Response<>(1, "Success", units);
    }

    @Override
    public Response searchMinStopStations(RoutePlanInfo info, HttpHeaders headers) {
        // 前置参数校验（替换StringUtils.isEmpty为原生判断）
        if (info == null || info.getStartStation() == null || info.getStartStation().trim().isEmpty()
                || info.getEndStation() == null || info.getEndStation().trim().isEmpty()) {
            LOGGER.error("[searchMinStopStations] Invalid request info: startStation or endStation is empty");
            return new Response<>(0, "Invalid request: start/end station cannot be empty", null);
        }

        String fromStationId = info.getStartStation();
        String toStationId = info.getEndStation();
        RoutePlanServiceImpl.LOGGER.info("[searchMinStopStations][Start and Finish][From Id: {} To: {}]", fromStationId, toStationId);

        // 1. 获取两站之间的路线（核心空值修复）
        ArrayList<Route> routeList = new ArrayList<>();
        try {
            HttpEntity requestEntity = new HttpEntity(null);
            String route_service_url = serviceResolver.getServiceUrl("ts-route-service");
            // 校验服务地址是否有效（替换StringUtils.isEmpty）
            if (route_service_url == null || route_service_url.trim().isEmpty()) {
                LOGGER.error("[searchMinStopStations] ts-route-service url is empty");
                return new Response<>(0, "Route service is unavailable", null);
            }

            ResponseEntity<Response<ArrayList<Route>>> re = restTemplate.exchange(
                    route_service_url + "/api/v1/routeservice/routes/" + fromStationId + "/" + toStationId,
                    HttpMethod.GET,
                    requestEntity,
                    new ParameterizedTypeReference<Response<ArrayList<Route>>>() {
                    });

            // 多层空值校验：响应体 -> Response对象 -> data字段
            if (re != null && re.getBody() != null) {
                Response<ArrayList<Route>> routeResponse = re.getBody();
                if (routeResponse.getData() != null) {
                    routeList = routeResponse.getData();
                } else {
                    LOGGER.warn("[searchMinStopStations] Route service return empty data");
                }
            } else {
                LOGGER.warn("[searchMinStopStations] Route service return null response");
            }
        } catch (Exception e) {
            LOGGER.error("[searchMinStopStations] Failed to get routes from ts-route-service", e);
            return new Response<>(0, "Failed to query route information: " + e.getMessage(), null);
        }

        // 无路线时直接返回，避免后续空指针
        if (routeList.isEmpty()) {
            LOGGER.warn("[searchMinStopStations][Get the route][Candidate Route Number: 0]");
            return new Response<>(0, "No routes found between " + fromStationId + " and " + toStationId, null);
        }
        RoutePlanServiceImpl.LOGGER.info("[searchMinStopStations][Get the route][Candidate Route Number: {}]", routeList.size());

        // 2. 计算两站之间的停靠站数（修复indexOf返回-1的问题）
        ArrayList<Integer> gapList = new ArrayList<>();
        for (int i = 0; i < routeList.size(); i++) {
            Route route = routeList.get(i);
            // 校验stations是否为空
            if (route == null || route.getStations() == null || route.getStations().isEmpty()) {
                LOGGER.warn("[searchMinStopStations] Route {} has empty stations, skip", i);
                gapList.add(Integer.MAX_VALUE); // 设为极大值，避免被选中
                continue;
            }

            int indexStart = route.getStations().indexOf(fromStationId);
            int indexEnd = route.getStations().indexOf(toStationId);

            // 校验站点是否存在于路线中
            if (indexStart == -1 || indexEnd == -1 || indexEnd < indexStart) {
                LOGGER.warn("[searchMinStopStations] Station not found in route {}: start={}, end={}",
                        i, indexStart, indexEnd);
                gapList.add(Integer.MAX_VALUE); // 设为极大值，避免被选中
                continue;
            }
            gapList.add(indexEnd - indexStart);
        }

        // 3. 挑选停靠站最少的路线（修复gapList为空的问题）
        ArrayList<String> resultRoutes = new ArrayList<>();
        if (!gapList.isEmpty()) {
            int size = Math.min(5, routeList.size());
            for (int i = 0; i < size && !routeList.isEmpty() && !gapList.isEmpty(); i++) {
                int minIndex = 0;
                int tempMinGap = Integer.MAX_VALUE;
                // 找到最小gap的下标
                for (int j = 0; j < gapList.size(); j++) {
                    Integer gap = gapList.get(j);
                    if (gap != null && gap < tempMinGap) {
                        tempMinGap = gap;
                        minIndex = j;
                    }
                }
                // 校验下标是否有效
                if (minIndex >= 0 && minIndex < routeList.size()) {
                    resultRoutes.add(routeList.get(minIndex).getId());
                    // 移除已选中的路线和gap
                    routeList.remove(minIndex);
                    gapList.remove(minIndex);
                } else {
                    break; // 下标无效时终止循环
                }
            }
        }

        // 无候选路线时返回
        if (resultRoutes.isEmpty()) {
            LOGGER.warn("[searchMinStopStations] No valid routes with minimal stops");
            return new Response<>(0, "No valid routes with minimal stops", null);
        }

        // 4. 获取列车信息（修复travelTrips/travel2Trips空值问题）
        ArrayList<ArrayList<Trip>> travelTrips = new ArrayList<>();
        ArrayList<ArrayList<Trip>> travel2Trips = new ArrayList<>();
        try {
            HttpEntity requestEntity = new HttpEntity(resultRoutes, null);

            // 调用ts-travel-service
            String travel_service_url = serviceResolver.getServiceUrl("ts-travel-service");
            // 替换StringUtils.isEmpty
            if (travel_service_url != null && !travel_service_url.trim().isEmpty()) {
                ResponseEntity<Response<ArrayList<ArrayList<Trip>>>> re2 = restTemplate.exchange(
                        travel_service_url + "/api/v1/travelservice/trips/routes",
                        HttpMethod.POST,
                        requestEntity,
                        new ParameterizedTypeReference<Response<ArrayList<ArrayList<Trip>>>>() {
                        });
                if (re2 != null && re2.getBody() != null && re2.getBody().getData() != null) {
                    travelTrips = re2.getBody().getData();
                }
            } else {
                LOGGER.warn("[searchMinStopStations] ts-travel-service url is empty");
            }

            // 调用ts-travel2-service
            String travel2_service_url = serviceResolver.getServiceUrl("ts-travel2-service");
            // 替换StringUtils.isEmpty
            if (travel2_service_url != null && !travel2_service_url.trim().isEmpty()) {
                ResponseEntity<Response<ArrayList<ArrayList<Trip>>>> re2 = restTemplate.exchange(
                        travel2_service_url + "/api/v1/travel2service/trips/routes",
                        HttpMethod.POST,
                        requestEntity,
                        new ParameterizedTypeReference<Response<ArrayList<ArrayList<Trip>>>>() {
                        });
                if (re2 != null && re2.getBody() != null && re2.getBody().getData() != null) {
                    travel2Trips = re2.getBody().getData();
                }
            } else {
                LOGGER.warn("[searchMinStopStations] ts-travel2-service url is empty");
            }
        } catch (Exception e) {
            LOGGER.error("[searchMinStopStations] Failed to get trips from travel service", e);
            return new Response<>(0, "Failed to query train information: " + e.getMessage(), null);
        }

        // 合并查询结果（修复下标越界和空值问题）
        ArrayList<ArrayList<Trip>> finalTripResult = new ArrayList<>();
        // 取两个列表的最小长度，避免下标越界
        int mergeSize = Math.min(travel2Trips.size(), travelTrips.size());
        for (int i = 0; i < mergeSize; i++) {
            ArrayList<Trip> tempList = new ArrayList<>(travel2Trips.get(i)); // 新建列表避免空指针
            tempList.addAll(travelTrips.get(i));
            finalTripResult.add(tempList);
        }
        // 补充剩余的结果
        if (travel2Trips.size() > mergeSize) {
            finalTripResult.addAll(travel2Trips.subList(mergeSize, travel2Trips.size()));
        } else if (travelTrips.size() > mergeSize) {
            finalTripResult.addAll(travelTrips.subList(mergeSize, travelTrips.size()));
        }

        RoutePlanServiceImpl.LOGGER.info("[searchMinStopStations][Get train Information][Trips Num: {}]", finalTripResult.size());

        // 5. 获取价格和站点信息（修复tripAllDetail空值问题）
        ArrayList<Trip> trips = new ArrayList<>();
        for (ArrayList<Trip> tempTrips : finalTripResult) {
            if (tempTrips != null) {
                trips.addAll(tempTrips);
            }
        }

        ArrayList<RoutePlanResultUnit> tripResponses = new ArrayList<>();
        for (Trip trip : trips) {
            if (trip == null || trip.getTripId() == null) {
                LOGGER.warn("[searchMinStopStations] Skip null trip");
                continue;
            }

            try {
                TripResponse tripResponse = null;
                TripAllDetailInfo allDetailInfo = new TripAllDetailInfo();
                allDetailInfo.setTripId(trip.getTripId().toString());
                allDetailInfo.setTravelDate(info.getTravelDate());
                allDetailInfo.setFrom(fromStationId);
                allDetailInfo.setTo(toStationId);

                HttpEntity requestEntity = new HttpEntity(allDetailInfo, null);
                String requestUrl = "";
                String tripIdStr = trip.getTripId().toString();

                // 确定请求地址
                if (tripIdStr.startsWith("D") || tripIdStr.startsWith("G")) {
                    requestUrl = serviceResolver.getServiceUrl("ts-travel-service") + "/api/v1/travelservice/trip_detail";
                } else {
                    requestUrl = serviceResolver.getServiceUrl("ts-travel2-service") + "/api/v1/travel2service/trip_detail";
                }

                // 替换StringUtils.isEmpty
                if (requestUrl != null && !requestUrl.trim().isEmpty()) {
                    ResponseEntity<Response<TripAllDetail>> re3 = restTemplate.exchange(
                            requestUrl,
                            HttpMethod.POST,
                            requestEntity,
                            new ParameterizedTypeReference<Response<TripAllDetail>>() {
                            });

                    // 空值校验
                    if (re3 != null && re3.getBody() != null && re3.getBody().getData() != null) {
                        TripAllDetail tripAllDetail = re3.getBody().getData();
                        tripResponse = tripAllDetail.getTripResponse();
                    }
                }

                // 只有tripResponse非空时才构建返回对象
                if (tripResponse != null) {
                    RoutePlanResultUnit unit = new RoutePlanResultUnit();
                    unit.setTripId(tripIdStr);
                    unit.setTrainTypeName(tripResponse.getTrainTypeName());
                    unit.setStartStation(tripResponse.getStartStation());
                    unit.setEndStation(tripResponse.getTerminalStation());
                    unit.setStartTime(tripResponse.getStartTime());
                    unit.setEndTime(tripResponse.getEndTime());
                    unit.setPriceForFirstClassSeat(tripResponse.getPriceForConfortClass());
                    unit.setPriceForSecondClassSeat(tripResponse.getPriceForEconomyClass());

                    // 获取路线站点信息
                    String routeId = trip.getRouteId();
                    Route tripRoute = getRouteByRouteId(routeId, headers);
                    if (tripRoute != null && tripRoute.getStations() != null) {
                        unit.setStopStations(tripRoute.getStations());
                    } else {
                        unit.setStopStations(new ArrayList<>()); // 兜底空列表
                    }

                    tripResponses.add(unit);
                }
            } catch (Exception e) {
                LOGGER.error("[searchMinStopStations] Failed to get trip detail for tripId: {}", trip.getTripId(), e);
                continue; // 单个车次失败不影响整体，继续处理下一个
            }
        }

        RoutePlanServiceImpl.LOGGER.info("[searchMinStopStations][Trips Response Unit Num: {}]", tripResponses.size());
        return new Response<>(1, "Success.", tripResponses);
    }


    private Route getRouteByRouteId(String routeId, HttpHeaders headers) {
        HttpEntity requestEntity = new HttpEntity(null);
        String route_service_url = serviceResolver.getServiceUrl("ts-route-service");
        ResponseEntity<Response<Route>> re = restTemplate.exchange(
                route_service_url + "/api/v1/routeservice/routes/" + routeId,
                HttpMethod.GET,
                requestEntity,
                new ParameterizedTypeReference<Response<Route>>() {
                });
        Response<Route> result = re.getBody();

        if (result.getStatus() == 0) {
            RoutePlanServiceImpl.LOGGER.error("[getRouteByRouteId][Get Route By Id Fail][RouteId: {}]", routeId);
            return null;
        } else {
            RoutePlanServiceImpl.LOGGER.info("[getRouteByRouteId][Get Route By Id Success]");
            return result.getData();
        }
    }

    private ArrayList<TripResponse> getTripFromHighSpeedTravelServive(TripInfo info, HttpHeaders headers) {
        RoutePlanServiceImpl.LOGGER.info("[getTripFromHighSpeedTravelServive][trip info: {}]", info);
        HttpEntity requestEntity = new HttpEntity(info, null);
        String travel_service_url=serviceResolver.getServiceUrl("ts-travel-service");
        ResponseEntity<Response<ArrayList<TripResponse>>> re = restTemplate.exchange(
                travel_service_url + "/api/v1/travelservice/trips/left",
                HttpMethod.POST,
                requestEntity,
                new ParameterizedTypeReference<Response<ArrayList<TripResponse>>>() {
                });

        ArrayList<TripResponse> tripResponses = re.getBody().getData();
        RoutePlanServiceImpl.LOGGER.info("[getTripFromHighSpeedTravelServive][Route Plan Get Trip][Size:{}]", tripResponses.size());
        return tripResponses;
    }

    private ArrayList<TripResponse> getTripFromNormalTrainTravelService(TripInfo info, HttpHeaders headers) {
        HttpEntity requestEntity = new HttpEntity(info, null);
        String travel2_service_url=serviceResolver.getServiceUrl("ts-travel2-service");
        ResponseEntity<Response<ArrayList<TripResponse>>> re = restTemplate.exchange(
                travel2_service_url + "/api/v1/travel2service/trips/left",
                HttpMethod.POST,
                requestEntity,
                new ParameterizedTypeReference<Response<ArrayList<TripResponse>>>() {
                });
        ArrayList<TripResponse> list = re.getBody().getData();
        RoutePlanServiceImpl.LOGGER.info("[getTripFromNormalTrainTravelService][Route Plan Get TripOther][Size:{}]", list.size());
        return list;
    }

    private List<String> getStationList(String tripId, HttpHeaders headers) {

        String path;
        String travel_service_url=serviceResolver.getServiceUrl("ts-travel-service");
        String travel2_service_url=serviceResolver.getServiceUrl("ts-travel2-service");
        if (tripId.charAt(0) == 'G' || tripId.charAt(0) == 'D') {
            path = travel_service_url + "/api/v1/travelservice/routes/" + tripId;
        } else {
            path = travel2_service_url + "/api/v1/travel2service/routes/" + tripId;
        }
        HttpEntity requestEntity = new HttpEntity(null);
        ResponseEntity<Response<Route>> re = restTemplate.exchange(
                path,
                HttpMethod.GET,
                requestEntity,
                new ParameterizedTypeReference<Response<Route>>() {
                });
        Route route = re.getBody().getData();
        return route.getStations();
    }
}
