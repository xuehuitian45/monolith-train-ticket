package edu.fudanselab.trainticket.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fudanselab.trainticket.entity.*;
import edu.fudanselab.trainticket.util.JsonUtils;
import edu.fudanselab.trainticket.util.Response;
import edu.fudanselab.trainticket.service.BasicService;
import edu.fudanselab.trainticket.service.ServiceResolver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * @author fdse
 */
@Service
public class BasicServiceImpl implements BasicService {

    @Autowired
    private RestTemplate restTemplate;

    /*@Autowired
    private DiscoveryClient discoveryClient;*/

    private static final Logger LOGGER = LoggerFactory.getLogger(BasicServiceImpl.class);

    @Autowired
    private ServiceResolver serviceResolver;

    @Override
    public Response queryForTravel(Travel info, HttpHeaders headers) {

        Response response = new Response<>();
        TravelResult result = new TravelResult();
        result.setStatus(true);
        response.setStatus(1);
        response.setMsg("Success");

        // 验证必填字段
        if (info == null) {
            LOGGER.error("[queryForTravel][Travel info is null]");
            result.setStatus(false);
            response.setStatus(0);
            response.setMsg("Travel info is null");
            response.setData(result);
            return response;
        }

        if (info.getTrip() == null) {
            LOGGER.error("[queryForTravel][Trip is null]");
            result.setStatus(false);
            response.setStatus(0);
            response.setMsg("Trip is null");
            response.setData(result);
            return response;
        }

        String start = info.getStartPlace();
        String end = info.getEndPlace();
        boolean startingPlaceExist = checkStationExists(start, headers);
        boolean endPlaceExist = checkStationExists(end, headers);
        if (!startingPlaceExist || !endPlaceExist) {
            result.setStatus(false);
            response.setStatus(0);
            response.setMsg("Start place or end place not exist!");
            if (!startingPlaceExist)
                BasicServiceImpl.LOGGER.warn("[queryForTravel][Start place not exist][start place: {}]", info.getStartPlace());
            if (!endPlaceExist)
                BasicServiceImpl.LOGGER.warn("[queryForTravel][End place not exist][end place: {}]", info.getEndPlace());
        }

        TrainType trainType = queryTrainTypeByName(info.getTrip().getTrainTypeName(), headers);
        if (trainType == null) {
            BasicServiceImpl.LOGGER.warn("[queryForTravel][traintype doesn't exist][trainTypeName: {}]", info.getTrip().getTrainTypeName());
            result.setStatus(false);
            response.setStatus(0);
            response.setMsg("Train type doesn't exist");
            return response;
        } else {
            result.setTrainType(trainType);
        }

        String routeId = info.getTrip().getRouteId();
        Route route = getRouteByRouteId(routeId, headers);
        if(route == null){
            result.setStatus(false);
            response.setStatus(0);
            response.setMsg("Route doesn't exist");
            return response;
        }

        //Check the route list for this train. Check that the required start and arrival stations are in the list of stops that are not on the route, and check that the location of the start station is before the stop
        //Trains that meet the above criteria are added to the return list
        int indexStart = 0;
        int indexEnd = 0;
        if (route.getStations().contains(start) &&
                route.getStations().contains(end) &&
                route.getStations().indexOf(start) < route.getStations().indexOf(end)){
            indexStart = route.getStations().indexOf(start);
            indexEnd = route.getStations().indexOf(end);
            LOGGER.info("[queryForTravel][query start index and end index][indexStart: {} indexEnd: {}]", indexStart, indexEnd);
            LOGGER.info("[queryForTravel][query stations and distances][stations: {} distances: {}]", route.getStations(), route.getDistances());
        }else {
            result.setStatus(false);
            response.setStatus(0);
            response.setMsg("Station not correct in Route");
            return response;
        }
        PriceConfig priceConfig = queryPriceConfigByRouteIdAndTrainType(routeId, trainType.getName(), headers);
        HashMap<String, String> prices = new HashMap<>();
        try {
            int distance = 0;
            distance = route.getDistances().get(indexEnd) - route.getDistances().get(indexStart);
            /**
             * We need the price Rate and distance (starting station).
             */
            double priceForEconomyClass = distance * priceConfig.getBasicPriceRate();
            double priceForConfortClass = distance * priceConfig.getFirstClassPriceRate();
            prices.put("economyClass", "" + priceForEconomyClass);
            prices.put("confortClass", "" + priceForConfortClass);
        }catch (Exception e){
            prices.put("economyClass", "95.0");
            prices.put("confortClass", "120.0");
        }
        result.setRoute(route);
        result.setPrices(prices);
        result.setPercent(1.0);
        response.setData(result);
        BasicServiceImpl.LOGGER.info("[queryForTravel][all done][result: {}]", result);

        return response;
    }

    @Override
    public Response queryForTravels(List<Travel> infos, HttpHeaders headers) {
        Response response = new Response<>();
        response.setStatus(1);
        response.setMsg("Success");

        HashMap<String, Travel> tripInfos = new HashMap<>();
        HashMap<String, List<String>> startTrips = new HashMap<>();
        HashMap<String, List<String>> endTrips = new HashMap<>();
        HashMap<String, List<String>> routeTrips = new HashMap<>();
        HashMap<String, List<String>> typeTrips = new HashMap<>();
        Set<String> stationNames = new HashSet<>();
        Set<String> trainTypeNames = new HashSet<>();
        Set<String> routeIds = new HashSet<>();
        Set<String> avaTrips = new HashSet<>();

        // ========== 第一步：遍历行程数据，过滤无效数据+初始化映射 ==========
        for (Travel info : infos) {
            // 全局异常捕获：单个行程数据异常不影响整体流程
            try {
                // 1. 层层判空：过滤无效的Travel/Trip/TripId
                if (info == null) {
                    LOGGER.warn("[queryForTravels][过滤无效数据][info为null]");
                    continue;
                }
                Trip trip = info.getTrip();
                if (trip == null) {
                    LOGGER.warn("[queryForTravels][过滤无效数据][trip为null][startPlace: {}, endPlace: {}]",
                            info.getStartPlace(), info.getEndPlace());
                    continue;
                }
                TripId tripId = trip.getTripId();
                if (tripId == null) {
                    LOGGER.warn("[queryForTravels][过滤无效数据][tripId为null][trainTypeName: {}, routeId: {}]",
                            trip.getTrainTypeName(), trip.getRouteId());
                    continue;
                }

                // 2. 安全获取tripNumber（避免TripId.toString()空指针）
                String tripNumber;
                try {
                    tripNumber = tripId.toString();
                } catch (NullPointerException e) {
                    LOGGER.error("[queryForTravels][TripId.toString()空指针][tripId: {}]", tripId, e);
                    continue;
                }

                // 3. 判空：起止地点
                if (info.getStartPlace() == null || info.getEndPlace() == null ||
                        info.getStartPlace().isEmpty() || info.getEndPlace().isEmpty()) {
                    LOGGER.warn("[queryForTravels][过滤无效数据][起止地点为空][tripNumber: {}]", tripNumber);
                    continue;
                }
                stationNames.add(info.getStartPlace());
                stationNames.add(info.getEndPlace());

                // 4. 判空：列车类型
                if (trip.getTrainTypeName() == null || trip.getTrainTypeName().isEmpty()) {
                    LOGGER.warn("[queryForTravels][过滤无效数据][列车类型为空][tripNumber: {}]", tripNumber);
                    continue;
                }
                trainTypeNames.add(trip.getTrainTypeName());

                // 5. 判空：路线ID
                if (trip.getRouteId() == null || trip.getRouteId().isEmpty()) {
                    LOGGER.warn("[queryForTravels][过滤无效数据][路线ID为空][tripNumber: {}]", tripNumber);
                    continue;
                }
                routeIds.add(trip.getRouteId());

                // 6. 初始化各类映射
                avaTrips.add(tripNumber);
                tripInfos.put(tripNumber, info);

                // 初始化startTrips
                String start = info.getStartPlace();
                List<String> trips = startTrips.getOrDefault(start, new ArrayList<>());
                trips.add(tripNumber);
                startTrips.put(start, trips);

                // 初始化endTrips
                String end = info.getEndPlace();
                trips = endTrips.getOrDefault(end, new ArrayList<>());
                trips.add(tripNumber);
                endTrips.put(end, trips);

                // 初始化routeTrips
                String routeId = trip.getRouteId();
                trips = routeTrips.getOrDefault(routeId, new ArrayList<>());
                trips.add(tripNumber);
                routeTrips.put(routeId, trips);

                // 初始化typeTrips
                String trainTypeName = trip.getTrainTypeName();
                trips = typeTrips.getOrDefault(trainTypeName, new ArrayList<>());
                trips.add(tripNumber);
                typeTrips.put(trainTypeName, trips);

            } catch (Exception e) {
                LOGGER.error("[queryForTravels][处理行程数据异常][info: {}]", info, e);
                continue;
            }
        }

        // ========== 第二步：校验站点是否存在 ==========
        Map<String, String> stationMap = checkStationsExists(new ArrayList<>(stationNames), headers);
        if (stationMap == null || stationMap.isEmpty()) {
            response.setStatus(0);
            response.setMsg("all stations don't exist");
            return response;
        }
        for (Map.Entry<String, String> s : stationMap.entrySet()) {
            if (s.getValue() == null) {
                // 站点不存在，移除关联行程
                if (startTrips.containsKey(s.getKey())) {
                    avaTrips.removeAll(startTrips.get(s.getKey()));
                }
                if (endTrips.containsKey(s.getKey())) {
                    avaTrips.removeAll(endTrips.get(s.getKey()));
                }
            }
        }
        if (avaTrips.isEmpty()) {
            response.setStatus(0);
            response.setMsg("no travel info available");
            return response;
        }

        // ========== 第三步：校验列车类型是否存在 ==========
        List<TrainType> tts = queryTrainTypeByNames(new ArrayList<>(trainTypeNames), headers);
        if (tts == null || tts.isEmpty()) {
            response.setStatus(0);
            response.setMsg("all train_type don't exist");
            return response;
        }
        Map<String, TrainType> trainTypeMap = new HashMap<>();
        for (TrainType t : tts) {
            trainTypeMap.put(t.getName(), t);
        }
        for (Map.Entry<String, List<String>> typeTrip : typeTrips.entrySet()) {
            String ttype = typeTrip.getKey();
            if (!trainTypeMap.containsKey(ttype)) {
                avaTrips.removeAll(typeTrip.getValue());
            }
        }
        if (avaTrips.isEmpty()) {
            response.setStatus(0);
            response.setMsg("no travel info available");
            return response;
        }

        // ========== 第四步：校验路线是否存在+站点逻辑 ==========
        List<Route> routes = getRoutesByRouteIds(new ArrayList<>(routeIds), headers);
        if (routes == null || routes.isEmpty()) {
            response.setStatus(0);
            response.setMsg("all routes don't exist");
            return response;
        }
        Map<String, Route> routeMap = new HashMap<>();
        for (Route r : routes) {
            routeMap.put(r.getId(), r);
        }
        for (Map.Entry<String, List<String>> routeTrip : routeTrips.entrySet()) {
            String routeId = routeTrip.getKey();
            if (!routeMap.containsKey(routeId)) {
                avaTrips.removeAll(routeTrip.getValue());
                continue;
            }

            // 路线存在，校验站点逻辑
            Route route = routeMap.get(routeId);
            List<String> trips = routeTrip.getValue();
            // 判空：路线的站点列表
            if (route.getStations() == null || route.getStations().isEmpty()) {
                LOGGER.warn("[queryForTravels][路线站点为空][routeId: {}]", routeId);
                avaTrips.removeAll(trips);
                continue;
            }

            for (String t : trips) {
                // 判空：tripInfos中的行程数据
                if (!tripInfos.containsKey(t)) {
                    LOGGER.warn("[queryForTravels][行程数据不存在][tripNumber: {}]", t);
                    avaTrips.remove(t);
                    continue;
                }
                Travel tripInfo = tripInfos.get(t);
                String start = tripInfo.getStartPlace();
                String end = tripInfo.getEndPlace();

                // 校验站点是否在路线中 + 起点索引 < 终点索引
                int startIndex = route.getStations().indexOf(start);
                int endIndex = route.getStations().indexOf(end);
                if (startIndex == -1 || endIndex == -1 || startIndex >= endIndex) {
                    avaTrips.remove(t);
                }
            }
        }
        if (avaTrips.isEmpty()) {
            response.setStatus(0);
            response.setMsg("no travel info available");
            return response;
        }

        // ========== 第五步：查询价格配置 ==========
        List<String> routeIdAndTypes = new ArrayList<>();
        for (String tripNumber : avaTrips) {
            if (!tripInfos.containsKey(tripNumber)) {
                continue;
            }
            Travel info = tripInfos.get(tripNumber);
            Trip trip = info.getTrip();
            if (trip == null) {
                continue;
            }
            String routeId = trip.getRouteId();
            String trainType = trip.getTrainTypeName();
            if (routeId == null || trainType == null) {
                continue;
            }
            routeIdAndTypes.add(routeId + ":" + trainType);
        }
        Map<String, PriceConfig> pcMap = queryPriceConfigByRouteIdsAndTrainTypes(routeIdAndTypes, headers);

        // ========== 第六步：计算价格+组装返回结果 ==========
        Map<String, TravelResult> trMap = new HashMap<>();
        for (String tripNumber : avaTrips) {
            try {
                if (!tripInfos.containsKey(tripNumber)) {
                    continue;
                }
                Travel info = tripInfos.get(tripNumber);
                Trip trip = info.getTrip();
                if (trip == null) {
                    continue;
                }

                String trainType = trip.getTrainTypeName();
                String routeId = trip.getRouteId();
                if (!routeMap.containsKey(routeId) || !trainTypeMap.containsKey(trainType)) {
                    continue;
                }

                Route route = routeMap.get(routeId);
                // 判空：路线的距离列表
                if (route.getDistances() == null || route.getDistances().isEmpty()) {
                    LOGGER.warn("[queryForTravels][路线距离为空][routeId: {}]", routeId);
                    continue;
                }

                // 计算站点索引
                int indexStart = route.getStations().indexOf(info.getStartPlace());
                int indexEnd = route.getStations().indexOf(info.getEndPlace());
                // 校验索引有效性
                if (indexStart < 0 || indexEnd < 0 || indexStart >= indexEnd ||
                        indexEnd >= route.getDistances().size() || indexStart >= route.getDistances().size()) {
                    LOGGER.warn("[queryForTravels][站点索引无效][tripNumber: {}, start: {}, end: {}]",
                            tripNumber, indexStart, indexEnd);
                    continue;
                }

                // 价格计算
                double basicPriceRate = 0.75;
                double firstPriceRate = 1;
                String priceKey = routeId + ":" + trainType;
                if (pcMap.containsKey(priceKey)) {
                    PriceConfig priceConfig = pcMap.get(priceKey);
                    basicPriceRate = priceConfig.getBasicPriceRate();
                    firstPriceRate = priceConfig.getFirstClassPriceRate();
                }

                HashMap<String, String> prices = new HashMap<>();
                try {
                    int distance = route.getDistances().get(indexEnd) - route.getDistances().get(indexStart);
                    double priceForEconomyClass = distance * basicPriceRate;
                    double priceForConfortClass = distance * firstPriceRate;
                    prices.put("economyClass", String.format("%.2f", priceForEconomyClass)); // 格式化保留2位小数
                    prices.put("confortClass", String.format("%.2f", priceForConfortClass));
                } catch (Exception e) {
                    LOGGER.warn("[queryForTravels][价格计算异常][tripNumber: {}]", tripNumber, e);
                    prices.put("economyClass", "95.00");
                    prices.put("confortClass", "120.00");
                }

                // 组装返回结果
                TravelResult result = new TravelResult();
                result.setStatus(true);
                result.setTrainType(trainTypeMap.get(trainType));
                result.setRoute(route);
                result.setPrices(prices);
                result.setPercent(1.0);

                trMap.put(tripNumber, result);
            } catch (Exception e) {
                LOGGER.error("[queryForTravels][组装行程结果异常][tripNumber: {}]", tripNumber, e);
                continue;
            }
        }

        // ========== 第七步：返回最终结果 ==========
        response.setData(trMap);
        LOGGER.info("[queryForTravels][all done][有效行程数: {}, 结果映射: {}]", avaTrips.size(), trMap);
        return response;
    }

    @Override
    public Response queryForStationId(String stationName, HttpHeaders headers) {
        BasicServiceImpl.LOGGER.info("[queryForStationId][Query For Station Id][stationName: {}]", stationName);
        HttpEntity requestEntity = new HttpEntity(null);
        String station_service_url=serviceResolver.getServiceUrl("ts-station-service");
        ResponseEntity<Response> re = restTemplate.exchange(
                station_service_url + "/api/v1/stationservice/stations/id/" + stationName,
                HttpMethod.GET,
                requestEntity,
                Response.class);
        if (re.getBody().getStatus() != 1) {
            String msg = re.getBody().getMsg();
            BasicServiceImpl.LOGGER.warn("[queryForStationId][Query for stationId error][stationName: {}, message: {}]", stationName, msg);
            return new Response<>(0, msg, null);
        }
        return  re.getBody();
    }

    public Map<String,String> checkStationsExists(List<String> stationNames, HttpHeaders headers) {
        BasicServiceImpl.LOGGER.info("[checkStationsExists][Check Stations Exists][stationNames: {}]", stationNames);
        HttpEntity requestEntity = new HttpEntity(stationNames, null);
        String station_service_url=serviceResolver.getServiceUrl("ts-station-service");
        ResponseEntity<Response> re = restTemplate.exchange(
                station_service_url + "/api/v1/stationservice/stations/idlist",
                HttpMethod.POST,
                requestEntity,
                Response.class);
        Response<Map<String, String>> r = re.getBody();
        if(r.getStatus() == 0) {
            return null;
        }
        Map<String, String> stationMap = r.getData();
        return stationMap;
    }

    public boolean checkStationExists(String stationName, HttpHeaders headers) {
        BasicServiceImpl.LOGGER.info("[checkStationExists][Check Station Exists][stationName: {}]", stationName);
        HttpEntity requestEntity = new HttpEntity(null);
        String station_service_url=serviceResolver.getServiceUrl("ts-station-service");
        ResponseEntity<Response> re = restTemplate.exchange(
                station_service_url + "/api/v1/stationservice/stations/id/" + stationName,
                HttpMethod.GET,
                requestEntity,
                Response.class);
        Response exist = re.getBody();

        return exist.getStatus() == 1;
    }

    public List<TrainType> queryTrainTypeByNames(List<String> trainTypeNames, HttpHeaders headers) {
        BasicServiceImpl.LOGGER.info("[queryTrainTypeByNames][Query Train Type][Train Type names: {}]", trainTypeNames);
        HttpEntity requestEntity = new HttpEntity(trainTypeNames, null);
        String train_service_url=serviceResolver.getServiceUrl("ts-train-service");
        ResponseEntity<Response> re = restTemplate.exchange(
                train_service_url + "/api/v1/trainservice/trains/byNames",
                HttpMethod.POST,
                requestEntity,
                Response.class);
        Response<List<TrainType>>  response = re.getBody();
        if(response.getStatus() == 0){
            return null;
        }
        List<TrainType> tts = Arrays.asList(JsonUtils.conveterObject(response.getData(), TrainType[].class));
        return tts;
    }

    public TrainType queryTrainTypeByName(String trainTypeName, HttpHeaders headers) {
        BasicServiceImpl.LOGGER.info("[queryTrainTypeByName][Query Train Type][Train Type name: {}]", trainTypeName);
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

    private List<Route> getRoutesByRouteIds(List<String> routeIds, HttpHeaders headers) {
        BasicServiceImpl.LOGGER.info("[getRoutesByRouteIds][Get Route By Ids][Route IDs：{}]", routeIds);
        HttpEntity requestEntity = new HttpEntity(routeIds, null);
        String route_service_url=serviceResolver.getServiceUrl("ts-route-service");
        ResponseEntity<Response> re = restTemplate.exchange(
                route_service_url + "/api/v1/routeservice/routes/byIds/",
                HttpMethod.POST,
                requestEntity,
                Response.class);
        Response<List<Route>> result = re.getBody();
        if ( result.getStatus() == 0) {
            BasicServiceImpl.LOGGER.warn("[getRoutesByRouteIds][Get Route By Ids Failed][Fail msg: {}]", result.getMsg());
            return null;
        } else {
            BasicServiceImpl.LOGGER.info("[getRoutesByRouteIds][Get Route By Ids][Success]");
            List<Route> routes = Arrays.asList(JsonUtils.conveterObject(result.getData(), Route[].class));;
            return routes;
        }
    }

    private Route getRouteByRouteId(String routeId, HttpHeaders headers) {
        BasicServiceImpl.LOGGER.info("[getRouteByRouteId][Get Route By Id][Route ID：{}]", routeId);
        HttpEntity requestEntity = new HttpEntity(null);
        String route_service_url=serviceResolver.getServiceUrl("ts-route-service");
        ResponseEntity<Response> re = restTemplate.exchange(
                route_service_url + "/api/v1/routeservice/routes/" + routeId,
                HttpMethod.GET,
                requestEntity,
                Response.class);
        Response result = re.getBody();
        if ( result.getStatus() == 0) {
            BasicServiceImpl.LOGGER.warn("[getRouteByRouteId][Get Route By Id Failed][Fail msg: {}]", result.getMsg());
            return null;
        } else {
            BasicServiceImpl.LOGGER.info("[getRouteByRouteId][Get Route By Id][Success]");
            return JsonUtils.conveterObject(result.getData(), Route.class);
        }
    }

    private PriceConfig queryPriceConfigByRouteIdAndTrainType(String routeId, String trainType, HttpHeaders headers) {
        BasicServiceImpl.LOGGER.info("[queryPriceConfigByRouteIdAndTrainType][Query For Price Config][RouteId: {} ,TrainType: {}]", routeId, trainType);
        HttpEntity requestEntity = new HttpEntity(null, null);
        String price_service_url=serviceResolver.getServiceUrl("ts-price-service");
        ResponseEntity<Response> re = restTemplate.exchange(
                price_service_url + "/api/v1/priceservice/prices/" + routeId + "/" + trainType,
                HttpMethod.GET,
                requestEntity,
                Response.class);
        Response result = re.getBody();

        BasicServiceImpl.LOGGER.info("[queryPriceConfigByRouteIdAndTrainType][Response Resutl to String][result: {}]", result.toString());
        return  JsonUtils.conveterObject(result.getData(), PriceConfig.class);
    }

    private Map<String, PriceConfig> queryPriceConfigByRouteIdsAndTrainTypes(List<String> routeIdsTypes, HttpHeaders headers) {
        BasicServiceImpl.LOGGER.info("[queryPriceConfigByRouteIdsAndTrainTypes][Query For Price Config][RouteId and TrainType: {}]", routeIdsTypes);
        HttpEntity requestEntity = new HttpEntity(routeIdsTypes, null);
        String price_service_url=serviceResolver.getServiceUrl("ts-price-service");
        ResponseEntity<Response> re = restTemplate.exchange(
                price_service_url + "/api/v1/priceservice/prices/byRouteIdsAndTrainTypes",
                HttpMethod.POST,
                requestEntity,
                Response.class);
        Response<Map<String, PriceConfig>> result = re.getBody();

        Map<String, PriceConfig> pcMap;
        if ( result.getStatus() == 0) {
            BasicServiceImpl.LOGGER.warn("[queryPriceConfigByRouteIdsAndTrainTypes][Get Price Config by routeId and trainType Failed][Fail msg: {}]", result.getMsg());
            return null;
        } else {
            ObjectMapper mapper = new ObjectMapper();
            try{
                pcMap = mapper.readValue(JsonUtils.object2Json(result.getData()), new TypeReference<Map<String, PriceConfig>>(){});
            }catch(Exception e) {
                BasicServiceImpl.LOGGER.warn("[queryPriceConfigByRouteIdsAndTrainTypes][Get Price Config by routeId and trainType Failed][Fail msg: {}]", e.getMessage());
                return null;
            }
            BasicServiceImpl.LOGGER.info("[queryPriceConfigByRouteIdsAndTrainTypes][Get Price Config by routeId and trainType][Success][priceConfigs: {}]", result.getData());
            return pcMap;
        }
    }

}
