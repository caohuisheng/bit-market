package cn.bugstack.domain.strategy.service.armory;

/**
 * @Author: chs
 * @Description: 策略装配库，负责初始化策略计算
 * @CreateTime: 2024-07-06
 */
public interface IStrategyArmory {

    /**
     * 装配抽奖策略（触发的时机可以为活动审核通过后调用）
     * @param strategyId
     * @return 装配结果
     */
    boolean assembleLotteryStrategy(Long strategyId);

    /**
     * 根据活动id装配抽奖策略（触发的时机可以为活动审核通过后调用）
     * @param activityId 活动id
     * @return 装配结果
     */
    boolean assembleLotteryStrategyByActivityId(Long activityId);

}
