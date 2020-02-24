package com.changhong.sei.task.dto;

import com.changhong.sei.annotation.Remark;

/**
 * *************************************************************************************************
 * <p/>
 * 实现功能：作业的执行状态
 * <p>
 * ------------------------------------------------------------------------------------------------
 * 版本          变更时间             变更人                     变更原因
 * ------------------------------------------------------------------------------------------------
 * 1.0.00      2017-08-02 13:50      王锦光(wangj)                新建
 * <p/>
 * *************************************************************************************************
 */
public enum JobState {
    /**
     * 无状态
     */
    @Remark("无状态")
    NONE,
    /**
     * 正常
     */
    @Remark("正常")
    NORMAL,
    /**
     * 暂停
     */
    @Remark("暂停")
    PAUSED,
    /**
     * 完成
     */
    @Remark("完成")
    COMPLETE,
    /**
     * 错误
     */
    @Remark("错误")
    ERROR,
    /**
     *阻塞
     */
    @Remark("阻塞")
    BLOCKED
}
