package edu.fudanselab.trainticket.service.impl;

import edu.fudanselab.trainticket.entity.Assurance;
import edu.fudanselab.trainticket.entity.AssuranceType;
import edu.fudanselab.trainticket.entity.AssuranceTypeBean;
import edu.fudanselab.trainticket.entity.PlainAssurance;
import edu.fudanselab.trainticket.repository.AssuranceRepository;
import edu.fudanselab.trainticket.service.AssuranceService;
import edu.fudanselab.trainticket.util.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @author fdse
 */
@Service
public class AssuranceServiceImpl implements AssuranceService {

    @Autowired
    private AssuranceRepository assuranceRepository;

    private static int insurence_id = 1;

    private static final Logger LOGGER = LoggerFactory.getLogger(AssuranceServiceImpl.class);

    @Override
    public Response findAssuranceById(String id, HttpHeaders headers) {
        Optional<Assurance> assurance = assuranceRepository.findById(id.toString());
        if (assurance == null) {
            AssuranceServiceImpl.LOGGER.warn("[findAssuranceById][find assurance][No content][assurance id: {}]", id);
            return new Response<>(0, "No Content by this id", null);
        } else {
            AssuranceServiceImpl.LOGGER.info("[findAssuranceById][Find Assurance][assurance id: {}]", id);
            return new Response<>(1, "Find Assurance Success", assurance);
        }
    }

    @Override
    public Response findAssuranceByOrderId(UUID orderId, HttpHeaders headers) {
        Assurance assurance = assuranceRepository.findByOrderId(orderId.toString());
        if (assurance == null) {
            AssuranceServiceImpl.LOGGER.warn("[findAssuranceByOrderId][find assurance][No content][orderId: {}]", orderId);
            return new Response<>(0, "No Content by this orderId", null);
        } else {
            AssuranceServiceImpl.LOGGER.info("[findAssuranceByOrderId][find assurance success][orderId: {}]", orderId);
            return new Response<>(1, "Find Assurance Success", assurance);
        }
    }

    @Override
    public Response create(int typeIndex, String orderId, HttpHeaders headers) {
        Assurance a = assuranceRepository.findByOrderId(orderId);
        AssuranceType at = AssuranceType.getTypeByIndex(typeIndex);
        if (a != null) {
            AssuranceServiceImpl.LOGGER.error("[create][AddAssurance Fail][Assurance already exists][typeIndex: {}, orderId: {}]", typeIndex, orderId);
            return new Response<>(0, "Fail.Assurance already exists", null);
        } else if (at == null) {
            AssuranceServiceImpl.LOGGER.warn("[create][AddAssurance Fail][Assurance type doesn't exist][typeIndex: {}, orderId: {}]", typeIndex, orderId);
            return new Response<>(0, "Fail.Assurance type doesn't exist", null);
        } else {
            Assurance assurance = new Assurance(String.valueOf(insurence_id), UUID.fromString(orderId).toString(), at);
//            insurence_id += 1;
            assuranceRepository.save(assurance);
            AssuranceServiceImpl.LOGGER.info("[create][AddAssurance][Success]");
            return new Response<>(1, "Success", assurance);
        }
    }

    @Override
    public Response deleteById(String assuranceId, HttpHeaders headers) {
        // 1. 前置校验：先检查要删除的assuranceId是否存在，避免JPA抛异常
        Optional<Assurance> existAssurance = assuranceRepository.findById(assuranceId);
        if (!existAssurance.isPresent()) {
            // ID不存在，直接返回失败提示，不执行删除
            AssuranceServiceImpl.LOGGER.error("[deleteById][DeleteAssurance Fail][Assurance not exist][assuranceId: {}]", assuranceId);
            return new Response<>(0, "Fail.Assurance not exist with id: " + assuranceId, assuranceId);
        }

        // 2. 执行删除操作（此时ID存在，不会触发EmptyResultDataAccessException）
        try {
            assuranceRepository.deleteById(assuranceId);
        } catch (Exception e) {
            // 捕获数据库层面的异常（如连接失败、锁冲突等）
            AssuranceServiceImpl.LOGGER.error("[deleteById][DeleteAssurance Fail][Database error][assuranceId: {}]", assuranceId, e);
            return new Response<>(0, "Fail.Delete assurance failed due to database error", assuranceId);
        }

        // 3. 验证删除结果
        Optional<Assurance> deletedAssurance = assuranceRepository.findById(assuranceId);
        if (!deletedAssurance.isPresent()) {
            AssuranceServiceImpl.LOGGER.info("[deleteById][DeleteAssurance success][assuranceId: {}]", assuranceId);
            return new Response<>(1, "Delete Success with Assurance id", null);
        } else {
            AssuranceServiceImpl.LOGGER.error("[deleteById][DeleteAssurance Fail][Assurance not clear][assuranceId: {}]", assuranceId);
            // 修复原代码错误：失败时应返回0，而非1
            return new Response<>(0, "Fail.Assurance not clear", assuranceId);
        }
    }

    @Override
    public Response deleteByOrderId(UUID orderId, HttpHeaders headers) {
        assuranceRepository.removeAssuranceByOrderId(orderId.toString());
        Assurance isExistAssurace = assuranceRepository.findByOrderId(orderId.toString());
        if (isExistAssurace == null) {
            AssuranceServiceImpl.LOGGER.info("[deleteByOrderId][DeleteAssurance Success][orderId: {}]", orderId);
            return new Response<>(1, "Delete Success with Order Id", null);
        } else {
            AssuranceServiceImpl.LOGGER.error("[deleteByOrderId][DeleteAssurance Fail][Assurance not clear][orderId: {}]", orderId);
            return new Response<>(0, "Fail.Assurance not clear", orderId);
        }
    }

    @Override
    public Response modify(String assuranceId, String orderId, int typeIndex, HttpHeaders headers) {
        // 1. 先调用查询接口获取保险信息
        Response oldAssuranceResponse = findAssuranceById(assuranceId, headers);
        Object data = oldAssuranceResponse.getData();

        // 2. 校验返回数据是否为 Optional 且有值（核心修复）
        Optional<Assurance> assuranceOpt = null;
        if (data instanceof Optional) {
            assuranceOpt = (Optional<Assurance>) data;
        }

        // 3. 先判断 Optional 是否为空，避免 get() 抛异常
        if (assuranceOpt == null || !assuranceOpt.isPresent()) {
            AssuranceServiceImpl.LOGGER.error("[modify][ModifyAssurance Fail][Assurance not found][assuranceId: {}, orderId: {}, typeIndex: {}]", assuranceId, orderId, typeIndex);
            return new Response<>(0, "Fail.Assurance not found.", null);
        }

        // 4. 有值时再安全取值
        Assurance oldAssurance = assuranceOpt.get();

        // 5. 校验保险类型是否存在
        AssuranceType at = AssuranceType.getTypeByIndex(typeIndex);
        if (at != null) {
            // 可选：增加 orderId 的 UUID 格式校验（避免后续报错）
            try {
                UUID.fromString(orderId); // 校验 orderId 格式
            } catch (IllegalArgumentException e) {
                AssuranceServiceImpl.LOGGER.error("[modify][ModifyAssurance Fail][orderId format error][orderId: {}]", orderId, e);
                return new Response<>(0, "Fail.orderId format error, must be UUID", null);
            }

            oldAssurance.setType(at);
            assuranceRepository.save(oldAssurance);
            AssuranceServiceImpl.LOGGER.info("[modify][ModifyAssurance Success][assuranceId: {}, orderId: {}, typeIndex: {}]", assuranceId, orderId, typeIndex);
            return new Response<>(1, "Modify Success", oldAssurance);
        } else {
            AssuranceServiceImpl.LOGGER.error("[modify][ModifyAssurance Fail][Assurance Type not exist][assuranceId: {}, orderId: {}, typeIndex: {}]", assuranceId, orderId, typeIndex);
            return new Response<>(0, "Assurance Type not exist", null);
        }
    }

    @Override
    public Response getAllAssurances(HttpHeaders headers) {
        List<Assurance> as = assuranceRepository.findAll();
        if (as != null && !as.isEmpty()) {
            ArrayList<PlainAssurance> result = new ArrayList<>();
            for (Assurance a : as) {
                PlainAssurance pa = new PlainAssurance();
                pa.setId(String.valueOf(a.getId()));
                pa.setOrderId(a.getOrderId());
                pa.setTypeIndex(a.getType().getIndex());
                pa.setTypeName(a.getType().getName());
                pa.setTypePrice(a.getType().getPrice());
                result.add(pa);
            }
            AssuranceServiceImpl.LOGGER.info("[getAllAssurances][find all assurance success][list size: {}]", as.size());
            return new Response<>(1, "Success", result);
        } else {
            AssuranceServiceImpl.LOGGER.warn("[getAllAssurances][find all assurance][No content]");
            return new Response<>(0, "No Content, Assurance is empty", null);
        }
    }

    @Override
    public Response getAllAssuranceTypes(HttpHeaders headers) {

        List<AssuranceTypeBean> atlist = new ArrayList<>();
        for (AssuranceType at : AssuranceType.values()) {
            AssuranceTypeBean atb = new AssuranceTypeBean();
            atb.setIndex(at.getIndex());
            atb.setName(at.getName());
            atb.setPrice(at.getPrice());
            atlist.add(atb);
        }
        if (!atlist.isEmpty()) {
            AssuranceServiceImpl.LOGGER.info("[getAllAssuranceTypes][find all assurance type success][list size: {}]", atlist.size());
            return new Response<>(1, "Find All Assurance", atlist);
        } else {
            AssuranceServiceImpl.LOGGER.warn("[getAllAssuranceTypes][find all assurance type][No content]");
            return new Response<>(0, "Assurance is Empty", null);
        }
    }
}
