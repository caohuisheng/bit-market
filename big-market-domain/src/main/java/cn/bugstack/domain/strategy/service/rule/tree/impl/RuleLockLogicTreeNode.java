package cn.bugstack.domain.strategy.service.rule.tree.impl;

import cn.bugstack.domain.strategy.model.valobj.RuleLogicCheckTypeVO;
import cn.bugstack.domain.strategy.repository.IStrategyRepository;
import cn.bugstack.domain.strategy.service.rule.ILogicTreeNode;
import cn.bugstack.domain.strategy.service.rule.tree.factory.DefaultTreeFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Date;

/**
 * Author: chs
 * Description: 次数锁节点
 * CreateTime: 2024-07-14
 */
@Slf4j
@Component("rule_lock")
public class RuleLockLogicTreeNode implements ILogicTreeNode {

    @Resource
    private IStrategyRepository strategyRepository;

    @Override
    public DefaultTreeFactory.TreeActionEntity logic(String userId, Long strategyId, Integer awardId, String ruleValue, Date endDatetime) {
        log.info("规则过滤-次数锁 userId:{}, strategyId:{}, awardId:{}", userId, strategyId, awardId);

        // 限定的抽奖次数
        long targetRaffleCount = 0L;
        try {
            targetRaffleCount = Long.parseLong(ruleValue);
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }

        // 查询用户今日抽奖次数
        Integer todayUserRaffleCount = strategyRepository.queryTodayUserRaffleCount(userId, strategyId);
        // 如果用户抽奖次数大于规则限定值，规则放行
        if(todayUserRaffleCount >= targetRaffleCount){
            return DefaultTreeFactory.TreeActionEntity.builder()
                    .ruleLogicCheckTypeVO(RuleLogicCheckTypeVO.ALLOW)
                    .build();
        }

        // 如果用户抽奖次数小于规则限定值，规则拦截
        return DefaultTreeFactory.TreeActionEntity.builder()
                .ruleLogicCheckTypeVO(RuleLogicCheckTypeVO.TAKE_OVER)
                .build();
    }

}
