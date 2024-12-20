package cn.bugstack.infrastructure.persistent.repository;

import cn.bugstack.domain.rebate.model.aggregate.BehaviorRebateAggregate;
import cn.bugstack.domain.rebate.model.entity.BehaviorRebateOrderEntity;
import cn.bugstack.domain.rebate.model.entity.TaskEntity;
import cn.bugstack.domain.rebate.model.vo.BehaviorTypeVO;
import cn.bugstack.domain.rebate.model.vo.DailyBehaviorRebateVO;
import cn.bugstack.domain.rebate.repository.IBehaviorRebateRepository;
import cn.bugstack.infrastructure.event.EventPublisher;
import cn.bugstack.infrastructure.persistent.dao.DailyBehaviorRebateDao;
import cn.bugstack.infrastructure.persistent.dao.TaskDao;
import cn.bugstack.infrastructure.persistent.dao.UserBehaviorRebateOrderDao;
import cn.bugstack.infrastructure.persistent.po.DailyBehaviorRebate;
import cn.bugstack.infrastructure.persistent.po.Task;
import cn.bugstack.infrastructure.persistent.po.UserBehaviorRebateOrder;
import cn.bugstack.middleware.db.router.strategy.IDBRouterStrategy;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Author: chs
 * Description:
 * CreateTime: 2024-08-13
 */
@Slf4j
@Component
public class BehaviorRebateRepository implements IBehaviorRebateRepository {

    @Resource
    private DailyBehaviorRebateDao dailyBehaviorRebateDao;
    @Resource
    private UserBehaviorRebateOrderDao userBehaviorRebateOrderDao;
    @Resource
    private TaskDao taskDao;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private IDBRouterStrategy dbRouter;
    @Resource
    private EventPublisher eventPublisher;

    @Override
    public List<DailyBehaviorRebateVO> queryDailyBehaviorRebateConfig(BehaviorTypeVO behaviorTypeVO) {
        List<DailyBehaviorRebate> dailyBehaviorRebates = dailyBehaviorRebateDao.queryDailyBehaviorRebateByBehaviorType(behaviorTypeVO.getCode());
        return dailyBehaviorRebates.stream().map(dailyBehaviorRebate -> {
            DailyBehaviorRebateVO dailyBehaviorRebateVO = new DailyBehaviorRebateVO();
            BeanUtils.copyProperties(dailyBehaviorRebate, dailyBehaviorRebateVO);
            return dailyBehaviorRebateVO;
        }).collect(Collectors.toList());
    }

    @Override
    public void saveUserRebateRecord(String userId, List<BehaviorRebateAggregate> behaviorRebateAggregates) {
        try {
            dbRouter.doRouter(userId);
            transactionTemplate.execute(status -> {
                try {
                    for (BehaviorRebateAggregate behaviorRebateAggregate : behaviorRebateAggregates) {
                        //用户行为返利订单对象
                        BehaviorRebateOrderEntity behaviorRebateOrderEntity = behaviorRebateAggregate.getBehaviorRebateOrderEntity();
                        UserBehaviorRebateOrder userBehaviorRebateOrder = new UserBehaviorRebateOrder();
                        BeanUtils.copyProperties(behaviorRebateOrderEntity, userBehaviorRebateOrder);
                        userBehaviorRebateOrderDao.insert(userBehaviorRebateOrder);

                        //任务对象
                        TaskEntity taskEntity = behaviorRebateAggregate.getTaskEntity();
                        Task task = new Task();
                        task.setUserId(userId);
                        task.setTopic(taskEntity.getTopic());
                        task.setMessageId(taskEntity.getMessageId());
                        task.setMessage(JSON.toJSONString(taskEntity.getMessage()));
                        task.setState(taskEntity.getState().getCode());
                        taskDao.insert(task);
                    }
                    return 1;
                }catch(DuplicateKeyException e){
                    log.error("写入返利记录唯一索引异常 userId:{}",userId, e);
                    status.setRollbackOnly();
                    throw new AppException(ResponseCode.INDEX_DUP.getCode());
                }catch(Exception e){
                    log.error("写入返利记录异常 userId:{}",userId, e);
                    status.setRollbackOnly();
                    throw e;
                }
            });
        } finally {
            dbRouter.clear();
        }

        //同步发送MQ消息
        for (BehaviorRebateAggregate behaviorRebateAggregate : behaviorRebateAggregates) {
            TaskEntity taskEntity = behaviorRebateAggregate.getTaskEntity();
            Task taskReq = new Task();
            taskReq.setUserId(taskEntity.getUserId());
            taskReq.setMessageId(taskEntity.getMessageId());
            try {
                eventPublisher.publish(taskEntity.getTopic(), taskEntity.getMessage());
                taskDao.updateTaskSendMessageCompleted(taskReq);
            } catch (Exception e) {
                log.info("写入返利记录，发送MQ消息失败 userId:{} topic:{}", userId, taskEntity.getTopic());
            }
        }
    }

    @Override
    public List<BehaviorRebateOrderEntity> queryOrderByOutBusinessNo(String userId, String outBusinessNo) {
        //查询用户行为返利订单
        UserBehaviorRebateOrder request = new UserBehaviorRebateOrder();
        request.setUserId(userId);
        request.setOutBusinessNo(outBusinessNo);
        List<UserBehaviorRebateOrder> userBehaviorRebateOrder = userBehaviorRebateOrderDao.queryOrderByOutBusinessNo(request);
        //转换为实体对象
        return userBehaviorRebateOrder.stream().map(item -> {
            BehaviorRebateOrderEntity behaviorRebateOrderEntity = new BehaviorRebateOrderEntity();
            BeanUtils.copyProperties(item, behaviorRebateOrderEntity);
            return behaviorRebateOrderEntity;
        }).collect(Collectors.toList());
    }
}
